package com.rupio.app.data.repo

import androidx.room.withTransaction
import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.core.searchKey
import com.rupio.app.data.local.*
import com.rupio.app.data.prefs.SettingsStore
import com.rupio.app.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/** Reordena para que los miembros de una superserie queden juntos a partir del primero. */
object SupersetOps {
    fun <T> contiguous(items: List<T>, members: Set<String>, id: (T) -> String): List<T> {
        val inGroup = items.filter { id(it) in members }
        if (inGroup.isEmpty()) return items
        val out = mutableListOf<T>()
        var placed = false
        for (it in items) {
            if (id(it) in members) { if (!placed) { out += inGroup; placed = true } }
            else out += it
        }
        return out
    }
}

data class ToggleResult(val completed: Boolean, val newRecords: Set<RecordKind>)

/** Cómo estaban los ejercicios y las series antes de quitar algo, para "Deshacer". */
data class WorkoutSnapshot(val exercises: List<WorkoutExerciseEntity>, val sets: List<WorkoutSetEntity>)

class WorkoutRepository(
    private val db: RupioDatabase,
    private val settings: SettingsStore,
    private val userId: () -> String?,
    private val pendingDeletes: com.rupio.app.data.remote.PendingDeletes? = null,
) {
    private val dao = db.workouts()
    private fun now() = System.currentTimeMillis()
    private fun uid() = userId() ?: error("Sin sesión")

    val active: Flow<WorkoutEntity?> = dao.observeActive()
    fun observe(id: String) = dao.observe(id)
    fun observeExercises(id: String) = dao.observeExercises(id)
    fun observeSets(id: String) = dao.observeSets(id)
    fun observeRecent(limit: Int) = dao.observeRecent(limit)
    suspend fun historyPage(beforeDay: Int, beforeStart: Long, limit: Int) = dao.historyPage(beforeDay, beforeStart, limit)
    suspend fun historySearch(query: String, beforeDay: Int, beforeStart: Long, limit: Int) =
        dao.historySearchPage(query, query.searchKey(), beforeDay, beforeStart, limit)
    suspend fun months() = dao.months()
    fun observeCount() = dao.observeCount()
    suspend fun exerciseSummaries(ids: List<String>) = if (ids.isEmpty()) emptyList() else dao.exerciseSummaries(ids)
    suspend fun get(id: String) = dao.get(id)

    private fun defaultsFor(tracking: Tracking) = when (tracking) {
        Tracking.REPS_ONLY -> 8 to 15
        Tracking.DURATION -> 1 to 1
        else -> 8 to 12
    }

    // ------------------------------------------------------------------ iniciar
    suspend fun startEmpty(name: String = "Entrenamiento libre"): String {
        dao.active()?.let { return it.id }
        val zone = ZoneId.systemDefault(); val t = now()
        val w = WorkoutEntity(Ids.uuidV7(t), uid(), null, name, t, null, zone.id, Dates.localEpochDay(t, zone), updatedAt = t)
        dao.upsertWorkout(w)
        return w.id
    }

    suspend fun startFromRoutine(routineId: String): String {
        dao.active()?.let { return it.id }
        val r = db.routines().get(routineId) ?: return startEmpty()
        val items = db.routines().exercises(routineId)
        val zone = ZoneId.systemDefault(); val t = now(); val user = uid()
        val day = Dates.localEpochDay(t, zone)
        val w = WorkoutEntity(Ids.uuidV7(t), user, routineId, r.name, t, null, zone.id, day, updatedAt = t)
        val ex = items.mapIndexed { i, it ->
            WorkoutExerciseEntity(Ids.uuidV7(t), w.id, user, it.exerciseId, i, it.supersetId, it.supersetRestMode, it.supersetRestSec,
                it.restSec, it.repMin, it.repMax, it.notes, t, setPlan = it.setPlan)
        }
        // Con plan: una serie por cada serie planeada (los calentamientos ya marcados). Sin plan: target_sets normales.
        val sets = ex.zip(items).flatMap { (we, re) ->
            val plan = SetPlans.decode(re.setPlan)
            val n = if (plan.isNotEmpty()) plan.size else re.targetSets
            (0 until n).map { k ->
                WorkoutSetEntity(Ids.uuidV7(t), we.id, w.id, user, we.exerciseId, k, setType = plan.getOrNull(k)?.t ?: 0, performedOn = day, updatedAt = t)
            }
        }
        dao.insertFull(w, ex, sets)
        return w.id
    }

    // ------------------------------------------------------------------ ejercicios del entrenamiento
    suspend fun addExercises(workoutId: String, exerciseIds: List<String>) {
        val w = dao.get(workoutId) ?: return
        val existing = dao.exercises(workoutId)
        val s = settings.current(); val t = now(); val user = uid()
        var pos = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val catalog = db.exercises().getMany(exerciseIds).associateBy { it.id }
        db.withTransaction {
            for (id in exerciseIds) {
                val tr = Tracking.of(catalog[id]?.tracking ?: "weight_reps")
                val (mn, mx) = defaultsFor(tr)
                val we = WorkoutExerciseEntity(Ids.uuidV7(), workoutId, user, id, pos++, restSec = s.defaultRestSec, repMin = mn, repMax = mx, updatedAt = t)
                dao.upsertExercise(we)
                dao.upsertSets((0 until 3).map { k -> WorkoutSetEntity(Ids.uuidV7(), we.id, workoutId, user, id, k, performedOn = w.localDate, updatedAt = t) })
            }
        }
    }

    suspend fun removeExercise(workoutExerciseId: String, workoutId: String) {
        val list = dao.exercises(workoutId)
        val target = list.firstOrNull { it.id == workoutExerciseId } ?: return
        val t = now()
        db.withTransaction {
            dao.upsertExercise(target.copy(deleted = true, dirty = true, updatedAt = t))
            dao.sets(workoutId).filter { it.workoutExerciseId == workoutExerciseId }.forEach { dao.upsertSet(it.copy(deleted = true, dirty = true, updatedAt = t)) }
            target.supersetId?.let { ss -> dissolveIfSingle(list.filter { it.id != workoutExerciseId }, ss, t) }
        }
    }

    /** Foto de los ejercicios (con sus superseries) y de las series de [workoutExerciseId] antes de quitarlo. */
    suspend fun snapshot(workoutId: String, workoutExerciseId: String?): WorkoutSnapshot =
        WorkoutSnapshot(dao.exercises(workoutId), dao.sets(workoutId).filter { workoutExerciseId == null || it.workoutExerciseId == workoutExerciseId })

    /** "Deshacer": vuelve a dejar todo como en la foto (el borrado era suave, deleted = 1). */
    suspend fun restore(s: WorkoutSnapshot) {
        val t = now()
        db.withTransaction {
            dao.upsertExercises(s.exercises.map { it.copy(deleted = false, updatedAt = t, dirty = true) })
            dao.upsertSets(s.sets.map { it.copy(deleted = false, updatedAt = t, dirty = true) })
        }
    }

    suspend fun move(workoutId: String, workoutExerciseId: String, delta: Int) {
        val list = dao.exercises(workoutId).toMutableList()
        val i = list.indexOfFirst { it.id == workoutExerciseId }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        java.util.Collections.swap(list, i, j)
        val t = now()
        dao.upsertExercises(list.mapIndexed { k, e -> e.copy(position = k, updatedAt = t, dirty = true) })
    }

    suspend fun updateExerciseRest(workoutExerciseId: String, workoutId: String, restSec: Int) {
        val e = dao.exercises(workoutId).firstOrNull { it.id == workoutExerciseId } ?: return
        dao.upsertExercise(e.copy(restSec = restSec, updatedAt = now(), dirty = true))
    }

    suspend fun updateNotes(workoutExerciseId: String, workoutId: String, notes: String) {
        val e = dao.exercises(workoutId).firstOrNull { it.id == workoutExerciseId } ?: return
        dao.upsertExercise(e.copy(notes = notes.ifBlank { null }, updatedAt = now(), dirty = true))
    }

    // ------------------------------------------------------------------ superseries
    suspend fun createSuperset(workoutId: String, memberIds: List<String>, mode: SupersetRestMode, sharedRestSec: Int?) {
        if (memberIds.size < 2) return
        val list = dao.exercises(workoutId)
        val ssId = Ids.uuidV7(); val t = now()
        val oldGroups = list.filter { it.id in memberIds }.mapNotNull { it.supersetId }.toSet()
        val updated = list.map {
            if (it.id in memberIds) it.copy(supersetId = ssId, supersetRestMode = mode.code, supersetRestSec = sharedRestSec) else it
        }
        val ordered = SupersetOps.contiguous(updated, memberIds.toSet()) { it.id }
        db.withTransaction {
            dao.upsertExercises(ordered.mapIndexed { k, e -> e.copy(position = k, updatedAt = t, dirty = true) })
            oldGroups.forEach { dissolveIfSingle(dao.exercises(workoutId), it, t) }
        }
    }

    suspend fun updateSupersetRest(workoutId: String, supersetId: String, mode: SupersetRestMode, sharedRestSec: Int?) {
        val t = now()
        dao.upsertExercises(dao.exercises(workoutId).filter { it.supersetId == supersetId }
            .map { it.copy(supersetRestMode = mode.code, supersetRestSec = sharedRestSec, updatedAt = t, dirty = true) })
    }

    /** Saca un ejercicio de la superserie y lo deja justo después del grupo (el grupo sigue contiguo). */
    suspend fun removeFromSuperset(workoutId: String, workoutExerciseId: String) {
        val list = dao.exercises(workoutId).toMutableList()
        val e = list.firstOrNull { it.id == workoutExerciseId } ?: return
        val ss = e.supersetId ?: return
        val t = now()
        list.remove(e)
        val lastIdx = list.indexOfLast { it.supersetId == ss }
        list.add(if (lastIdx < 0) list.size else lastIdx + 1, e.copy(supersetId = null))
        db.withTransaction {
            dao.upsertExercises(list.mapIndexed { k, x -> x.copy(position = k, updatedAt = t, dirty = true) })
            dissolveIfSingle(dao.exercises(workoutId), ss, t)
        }
    }

    suspend fun getSet(id: String) = dao.getSet(id)

    suspend fun ungroup(workoutId: String, supersetId: String) {
        val t = now()
        dao.upsertExercises(dao.exercises(workoutId).filter { it.supersetId == supersetId }.map { it.copy(supersetId = null, updatedAt = t, dirty = true) })
    }

    private suspend fun dissolveIfSingle(list: List<WorkoutExerciseEntity>, ss: String, t: Long) {
        val left = list.filter { it.supersetId == ss && !it.deleted }
        if (left.size == 1) dao.upsertExercise(left[0].copy(supersetId = null, updatedAt = t, dirty = true))
    }

    // ------------------------------------------------------------------ series
    suspend fun addSet(workoutExerciseId: String) {
        val all = dao.exercises(dao.workoutIdOf(workoutExerciseId) ?: return)
        val we = all.firstOrNull { it.id == workoutExerciseId } ?: return
        val w = dao.get(we.workoutId) ?: return
        val sets = dao.sets(we.workoutId).filter { it.workoutExerciseId == workoutExerciseId }
        val last = sets.lastOrNull()
        dao.upsertSet(WorkoutSetEntity(Ids.uuidV7(), workoutExerciseId, we.workoutId, uid(), we.exerciseId, (last?.position ?: -1) + 1,
            weightKg = last?.weightKg, reps = null, performedOn = w.localDate, updatedAt = now()))
    }

    suspend fun removeSet(setId: String) {
        val s = dao.getSet(setId) ?: return
        dao.upsertSet(s.copy(deleted = true, dirty = true, updatedAt = now()))
    }

    suspend fun updateSet(setId: String, weightKg: Double?, reps: Int?, durationSec: Int?, rpe: Double?) {
        val s = dao.getSet(setId) ?: return
        dao.upsertSet(s.copy(weightKg = weightKg, reps = reps, durationSec = durationSec, rpe = rpe, updatedAt = now(), dirty = true))
    }

    /** Edición puntual de una serie (peso, reps, tiempo, RPE, tipo). */
    suspend fun editSet(setId: String, f: (WorkoutSetEntity) -> WorkoutSetEntity) {
        val s = dao.getSet(setId) ?: return
        dao.upsertSet(f(s).copy(updatedAt = now(), dirty = true))
    }

    suspend fun updateRepRange(workoutExerciseId: String, workoutId: String, repMin: Int, repMax: Int) {
        val e = dao.exercises(workoutId).firstOrNull { it.id == workoutExerciseId } ?: return
        dao.upsertExercise(e.copy(repMin = repMin, repMax = maxOf(repMin, repMax), updatedAt = now(), dirty = true))
    }

    suspend fun cycleSetType(setId: String) {
        val s = dao.getSet(setId) ?: return
        dao.upsertSet(s.copy(setType = SetType.of(s.setType).next().code, updatedAt = now(), dirty = true))
    }

    /**
     * Marca / desmarca una serie. Si está vacía usa los valores sugeridos (como Hevy).
     * Devuelve los récords que supera en el momento (contra récords guardados y el resto de la sesión).
     */
    suspend fun toggleComplete(setId: String, tracking: Tracking, fallbackWeightKg: Double?, fallbackReps: Int?, fallbackDuration: Int?): ToggleResult {
        val s = dao.getSet(setId) ?: return ToggleResult(false, emptySet())
        val t = now()
        if (s.completed) {
            dao.upsertSet(s.copy(completed = false, completedAt = null, isPr = false, updatedAt = t, dirty = true))
            return ToggleResult(false, emptySet())
        }
        val filled = s.copy(
            weightKg = s.weightKg ?: if (tracking.usesWeight) fallbackWeightKg else null,
            reps = s.reps ?: if (tracking.usesReps) fallbackReps else null,
            durationSec = s.durationSec ?: if (tracking == Tracking.DURATION) fallbackDuration else null,
            completed = true, completedAt = t, updatedAt = t, dirty = true,
        )
        val current = currentBest(filled.exerciseId, filled.workoutId, excludeSet = filled.id)
        val beaten = Records.beaten(tracking, filled.toData(), current)
        val headline = beaten.intersect(setOf(RecordKind.MAX_WEIGHT, RecordKind.E1RM, RecordKind.MAX_REPS, RecordKind.MAX_DURATION))
        dao.upsertSet(filled.copy(isPr = headline.isNotEmpty()))
        return ToggleResult(true, headline)
    }

    private suspend fun currentBest(exerciseId: String, workoutId: String, excludeSet: String): Map<RecordKind, Double> {
        val stored = db.records().forExercise(exerciseId).associate { RecordKind.of(it.kind) to it.value }.toMutableMap()
        val tracking = Tracking.of(db.exercises().get(exerciseId)?.tracking ?: "weight_reps")
        dao.sets(workoutId).filter { it.exerciseId == exerciseId && it.completed && it.id != excludeSet }.forEach { st ->
            Records.candidates(tracking, st.toData()).forEach { (k, v) -> if (v > (stored[k] ?: 0.0)) stored[k] = v }
        }
        return stored
    }

    // ------------------------------------------------------------------ terminar / descartar
    suspend fun finish(workoutId: String, name: String?, notes: String?): Int {
        val w = dao.get(workoutId) ?: return 0
        val t = now()
        var prCount = 0
        db.withTransaction {
            val sets = dao.sets(workoutId)
            val exercises = dao.exercises(workoutId)
            // Se descartan series sin completar y ejercicios sin ninguna serie completada.
            sets.filter { !it.completed }.forEach { dao.upsertSet(it.copy(deleted = true, dirty = true, updatedAt = t)) }
            val done = sets.filter { it.completed }
            val withSets = done.map { it.workoutExerciseId }.toSet()
            exercises.filter { it.id !in withSets }.forEach { dao.upsertExercise(it.copy(deleted = true, dirty = true, updatedAt = t)) }

            val catalog = db.exercises().getMany(done.map { it.exerciseId }.distinct()).associateBy { it.id }
            // Récords definitivos
            val newRecords = mutableListOf<PersonalRecordEntity>()
            for ((exId, exSets) in done.groupBy { it.exerciseId }) {
                val tracking = Tracking.of(catalog[exId]?.tracking ?: "weight_reps")
                val best = db.records().forExercise(exId).associateBy { it.kind }.toMutableMap()
                for (st in exSets) {
                    for ((kind, v) in Records.candidates(tracking, st.toData())) {
                        if (v > (best[kind.code]?.value ?: 0.0) + 1e-6) {
                            best[kind.code] = PersonalRecordEntity(exId, kind.code, uid(), v, st.id, st.completedAt ?: t, t, dirty = true)
                        }
                    }
                }
                newRecords += best.values.filter { it.updatedAt == t }
            }
            db.records().upsert(newRecords)
            prCount = newRecords.count { it.kind in listOf(RecordKind.MAX_WEIGHT.code, RecordKind.E1RM.code, RecordKind.MAX_REPS.code, RecordKind.MAX_DURATION.code) }
            val prSetIds = newRecords.mapNotNull { it.setId }.toSet()
            done.forEach { dao.upsertSet(it.copy(isPr = it.id in prSetIds, updatedAt = t, dirty = true)) }

            val working = done.filter { it.setType != SetType.WARMUP.code }
            val volume = working.sumOf { (it.weightKg ?: 0.0).coerceAtLeast(0.0) * (it.reps ?: 0) }
            dao.upsertWorkout(w.copy(
                name = name?.takeIf { it.isNotBlank() } ?: w.name, notes = notes?.ifBlank { null } ?: w.notes,
                // Si el reloj del teléfono se atrasó a media sesión, nunca termina antes de empezar.
                endedAt = maxOf(t, w.startedAt), durationSec = ((t - w.startedAt) / 1000).coerceIn(0, 86_400 * 2).toInt(),
                totalVolumeKg = volume, totalSets = working.size, totalReps = working.sumOf { it.reps ?: 0 },
                prCount = prCount, updatedAt = t, dirty = true,
            ))
        }
        return prCount
    }

    /** Un entrenamiento activo nunca se subió → se borra de verdad. */
    suspend fun discard(workoutId: String) = dao.hardDelete(workoutId)

    /** Borra (suave) un entreno terminado y recalcula los récords. Devuelve la acción para "Deshacer". */
    suspend fun deleteWorkout(workoutId: String): suspend () -> Unit {
        val t = now()
        val w = dao.get(workoutId)
        val exercises = dao.exercises(workoutId)
        val sets = dao.sets(workoutId)
        val exerciseIds = sets.map { it.exerciseId }.toSet()
        db.withTransaction {
            dao.softDelete(workoutId, t)
            exercises.forEach { dao.upsertExercise(it.copy(deleted = true, dirty = true, updatedAt = t)) }
            sets.forEach { dao.upsertSet(it.copy(deleted = true, dirty = true, updatedAt = t)) }
        }
        rebuildRecords(exerciseIds)
        return {
            val t2 = now()
            db.withTransaction {
                w?.let { dao.upsertWorkout(it.copy(deleted = false, dirty = true, updatedAt = t2)) }
                dao.upsertExercises(exercises.map { it.copy(deleted = false, dirty = true, updatedAt = t2) })
                dao.upsertSets(sets.map { it.copy(deleted = false, dirty = true, updatedAt = t2) })
            }
            rebuildRecords(exerciseIds)
        }
    }

    /**
     * Recalcula los récords de esos ejercicios con las series que siguen existiendo.
     * Si un récord ya no tiene respaldo (borraste el entreno), se elimina aquí y en el servidor.
     */
    suspend fun rebuildRecords(exerciseIds: Collection<String>) {
        val uid = userId() ?: return
        val t = now()
        for (ex in exerciseIds.toSet()) {
            val tracking = Tracking.of(db.exercises().get(ex)?.tracking ?: "weight_reps")
            val m = dao.recordMaxes(ex)
            val kinds = when (tracking) {
                Tracking.WEIGHT_REPS, Tracking.WEIGHTED_BODYWEIGHT -> listOf(RecordKind.MAX_WEIGHT to m.maxWeight, RecordKind.E1RM to m.e1rm,
                    RecordKind.SET_VOLUME to m.setVolume, RecordKind.MAX_REPS to m.maxReps)
                Tracking.REPS_ONLY, Tracking.ASSISTED_BODYWEIGHT -> listOf(RecordKind.MAX_REPS to m.maxReps)
                Tracking.DURATION -> listOf(RecordKind.MAX_DURATION to m.maxDuration)
            }
            val best = kinds.mapNotNull { (k, v) -> v?.takeIf { it > 0 }?.let { k to it } }.toMap()
            val existing = db.records().forExercise(ex).associateBy { RecordKind.of(it.kind) }
            db.records().upsert(best.mapNotNull { (k, v) ->
                val e = existing[k]
                if (e != null && kotlin.math.abs(e.value - v) < 1e-9) null   // sigue igual: no se toca
                else {
                    val holder = dao.recordHolder(ex, k.code, v)
                    PersonalRecordEntity(ex, k.code, uid, v, holder?.id, holder?.completedAt ?: t, t, dirty = true)
                }
            })
            (existing.keys - best.keys).forEach { k -> db.records().delete(ex, k.code); pendingDeletes?.add(ex, k.code) }
        }
    }


    /** Una sola vez tras actualizar la app: deja los récords consistentes con el historial. */
    suspend fun rebuildAllRecords() = rebuildRecords(db.records().allExerciseIds())

    suspend fun renameActive(workoutId: String, name: String) {
        val w = dao.get(workoutId) ?: return
        dao.upsertWorkout(w.copy(name = name, updatedAt = now()))
    }

    // ------------------------------------------------------------------ historial del ejercicio
    suspend fun previousSessions(exerciseId: String, excludeWorkout: String, max: Int = 6): List<SessionData> =
        dao.previousSets(exerciseId, excludeWorkout).groupBy { it.workoutId }.values.take(max).map { rows ->
            val sorted = rows.sortedBy { it.position }
            SessionData(sorted.first().performedOn, sorted.map { SetData(it.weightKg, it.reps, it.durationSec, it.rpe, SetType.of(it.setType)) })
        }

    suspend fun recommendation(exercise: ExerciseEntity, repMin: Int, repMax: Int, excludeWorkout: String): Recommendation {
        val s = settings.current()
        return Progression.recommend(ProgressionInput(
            Tracking.of(exercise.tracking), repMin, repMax, exercise.isLowerBody, exercise.equipment, s.unit,
            previousSessions(exercise.id, excludeWorkout),
        ))
    }
}

private fun WorkoutSetEntity.toData() = SetData(weightKg, reps, durationSec, rpe, SetType.of(setType))
