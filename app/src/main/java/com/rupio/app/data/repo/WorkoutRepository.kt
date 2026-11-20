package com.rupio.app.data.repo

import com.rupio.app.core.tr

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

/** Una serie del editor de un entreno terminado ([id] null = serie nueva). Pesos en kg (la ayuda va en negativo). */
data class EditedSet(val id: String?, val workoutExerciseId: String, val exerciseId: String, val setType: Int,
                     val weightKg: Double?, val reps: Int?, val durationSec: Int?, val rpe: Double? = null)

/** Qué cambió al guardar el editor: series cambiadas o nuevas y series quitadas. */
data class EditResult(val touched: Set<String>, val removed: Set<String>)

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
    /** v1.9: última vez que terminaste cada rutina. */
    val routineLastDone: Flow<List<RoutineLastDone>> = dao.observeRoutineLastDone()
    suspend fun exerciseSummaries(ids: List<String>) = if (ids.isEmpty()) emptyList() else dao.exerciseSummaries(ids)
    suspend fun get(id: String) = dao.get(id)

    private fun defaultsFor(tracking: Tracking) = when (tracking) {
        Tracking.REPS_ONLY -> 8 to 15
        Tracking.DURATION -> 1 to 1
        else -> 8 to 12
    }

    // ------------------------------------------------------------------ iniciar
    suspend fun startEmpty(name: String = tr("Entrenamiento libre", "Empty workout")): String {
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
                val trk = Tracking.of(catalog[id]?.tracking ?: "weight_reps")
                val (mn, mx) = defaultsFor(trk)
                // v1.9 (N1): el cardio trae 1 serie y no arranca descanso (antes 3 series con 1:30 entre ellas, como una plancha).
                val cardio = catalog[id]?.isCardio == true
                val we = WorkoutExerciseEntity(Ids.uuidV7(), workoutId, user, id, pos++, restSec = if (cardio) 0 else s.defaultRestSec, repMin = mn, repMax = mx, updatedAt = t)
                dao.upsertExercise(we)
                dao.upsertSets((0 until if (cardio) 1 else 3).map { k -> WorkoutSetEntity(Ids.uuidV7(), we.id, workoutId, user, id, k, performedOn = w.localDate, updatedAt = t) })
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

    /**
     * Subir (-1) o bajar (+1). v1.9: con las mismas reglas que "Ordenar" ([ReorderOps]): dentro de una superserie cambia
     * de lugar con su vecino; en la orilla mueve la superserie completa. Antes intercambiaba con el de al lado aunque
     * fuera de otro grupo y podía partir una superserie.
     */
    suspend fun move(workoutId: String, workoutExerciseId: String, delta: Int) {
        val list = dao.exercises(workoutId)
        val moved = com.rupio.app.domain.ReorderOps.moveStep(list, workoutExerciseId, delta, { it.id }, { it.supersetId }) ?: return
        savePositions(list, moved)
    }

    /**
     * v1.9: guarda el orden que dejaste en "Ordenar ejercicios". Devuelve false (y no cambia nada) si la lista ya no
     * coincide con la del entreno (p. ej. se quitó un ejercicio mientras tanto) o si partiría una superserie.
     */
    suspend fun reorder(workoutId: String, orderedIds: List<String>): Boolean = db.withTransaction {
        val list = dao.exercises(workoutId)
        val ordered = com.rupio.app.domain.ReorderOps.applyOrder(list, orderedIds, { it.id }, { it.supersetId }) ?: return@withTransaction false
        savePositions(list, ordered)
        true
    }

    /** Solo se marcan para subir los que cambiaron de lugar. */
    private suspend fun savePositions(before: List<WorkoutExerciseEntity>, ordered: List<WorkoutExerciseEntity>) {
        val oldPos = before.associate { it.id to it.position }
        val t = now()
        val changed = ordered.mapIndexedNotNull { k, e -> if (oldPos[e.id] == k) null else e.copy(position = k, updatedAt = t, dirty = true) }
        if (changed.isNotEmpty()) dao.upsertExercises(changed)
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
        // v1.9: vacía. El peso de la anterior sale en gris como sugerencia (y se usa al marcar ✓); antes se copiaba como
        // si lo hubieras escrito, y al terminar no se distinguía de una serie con datos sin marcar.
        dao.upsertSet(WorkoutSetEntity(Ids.uuidV7(), workoutExerciseId, we.workoutId, uid(), we.exerciseId, (last?.position ?: -1) + 1,
            performedOn = w.localDate, updatedAt = now()))
    }

    /**
     * v1.9 · "Agregar calentamiento": una serie C ANTES de la primera serie efectiva (después de los calentamientos que
     * ya haya). Antes la única forma era convertir la serie 1 y te quedabas con una serie de trabajo menos.
     */
    suspend fun addWarmup(workoutExerciseId: String) {
        val workoutId = dao.workoutIdOf(workoutExerciseId) ?: return
        val we = dao.exercises(workoutId).firstOrNull { it.id == workoutExerciseId } ?: return
        val w = dao.get(workoutId) ?: return
        val sets = dao.sets(workoutId).filter { it.workoutExerciseId == workoutExerciseId }.sortedBy { it.position }
        val at = sets.indexOfFirst { it.setType != SetType.WARMUP.code }.let { if (it < 0) sets.size else it }
        val t = now()
        val warm = WorkoutSetEntity(Ids.uuidV7(), workoutExerciseId, workoutId, uid(), we.exerciseId, at, setType = SetType.WARMUP.code,
            performedOn = w.localDate, updatedAt = t)
        val list = sets.toMutableList().apply { add(at, warm) }
        db.withTransaction { dao.upsertSets(list.mapIndexed { k, s -> if (s.position == k && s.id != warm.id) s else s.copy(position = k, updatedAt = t, dirty = true) }) }
    }

    suspend fun removeSet(setId: String) {
        val s = dao.getSet(setId) ?: return
        dao.upsertSet(s.copy(deleted = true, dirty = true, updatedAt = now()))
    }

    suspend fun updateSet(setId: String, weightKg: Double?, reps: Int?, durationSec: Int?, rpe: Double?, setType: Int? = null) {
        val s = dao.getSet(setId) ?: return
        dao.upsertSet(s.copy(weightKg = weightKg, reps = reps, durationSec = durationSec, rpe = rpe, setType = setType ?: s.setType,
            updatedAt = now(), dirty = true))
    }

    /**
     * v1.7 (N1): guarda lo que cambiaste en un entreno YA terminado, en una sola transacción:
     * series cambiadas (updateSet), quitadas (borrado suave) y nuevas; los ejercicios que se quedan sin series se
     * quitan (como al terminar). Recalcula volumen, series y repeticiones del entreno, lo marca para subir y recalcula
     * los récords de los ejercicios afectados. Devuelve las series cambiadas o nuevas y las quitadas.
     */
    suspend fun saveEdits(workoutId: String, edited: List<EditedSet>): EditResult {
        val w = dao.get(workoutId) ?: return EditResult(emptySet(), emptySet())
        val t = now(); val user = uid()
        val before = dao.sets(workoutId).filter { it.completed }
        val byId = before.associateBy { it.id }
        val keep = edited.mapNotNull { it.id }.toSet()
        val touched = mutableSetOf<String>(); val removed = mutableSetOf<String>(); val affected = mutableSetOf<String>()
        db.withTransaction {
            before.filter { it.id !in keep }.forEach {
                dao.upsertSet(it.copy(deleted = true, dirty = true, updatedAt = t)); removed += it.id; affected += it.exerciseId
            }
            val nextPos = before.groupBy { it.workoutExerciseId }.mapValues { (_, l) -> l.maxOf { it.position } + 1 }.toMutableMap()
            for (e in edited) {
                val kg = e.weightKg?.takeIf { it.isFinite() }?.coerceIn(-com.rupio.app.core.Fmt.MAX_KG, com.rupio.app.core.Fmt.MAX_KG)
                val reps = e.reps?.coerceIn(0, 1000); val dur = e.durationSec?.coerceIn(0, 86_400)
                val old = e.id?.let { byId[it] }
                if (old == null) {
                    val pos = nextPos[e.workoutExerciseId] ?: 0; nextPos[e.workoutExerciseId] = pos + 1
                    val id = Ids.uuidV7()
                    dao.upsertSet(WorkoutSetEntity(id, e.workoutExerciseId, workoutId, user, e.exerciseId, pos, setType = e.setType, weightKg = kg, reps = reps,
                        durationSec = dur, rpe = e.rpe, completed = true, completedAt = w.endedAt ?: t, performedOn = w.localDate, updatedAt = t))
                    touched += id; affected += e.exerciseId
                } else if (old.weightKg != kg || old.reps != reps || old.durationSec != dur || old.rpe != e.rpe || old.setType != e.setType) {
                    updateSet(old.id, kg, reps, dur, e.rpe, e.setType)
                    touched += old.id; affected += old.exerciseId
                }
            }
            if (touched.isEmpty() && removed.isEmpty()) return@withTransaction
            val withSets = edited.map { it.workoutExerciseId }.toSet()
            dao.exercises(workoutId).filter { it.id !in withSets }.forEach { dao.upsertExercise(it.copy(deleted = true, dirty = true, updatedAt = t)) }
            // Mismas cuentas que al terminar: sin calentamientos; el volumen nunca suma pesos negativos (ayuda).
            val working = dao.sets(workoutId).filter { it.completed && it.setType != SetType.WARMUP.code }
            dao.upsertWorkout(w.copy(totalVolumeKg = working.sumOf { (it.weightKg ?: 0.0).coerceAtLeast(0.0) * (it.reps ?: 0) },
                totalSets = working.size, totalReps = working.sumOf { it.reps ?: 0 }, updatedAt = t, dirty = true))
        }
        if (affected.isEmpty()) return EditResult(touched, removed)
        rebuildRecords(affected)
        // Trofeo de las series que cambiaste: lo tienen si ahora sostienen un récord (las demás conservan el suyo).
        // v1.9: y solo si superan algo anterior (la primera vez que haces un ejercicio no hay récord que romper).
        val headline = Records.HEADLINE.map { it.code }.toSet()
        val holders = affected.flatMap { db.records().forExercise(it) }.filter { it.kind in headline }.mapNotNull { it.setId }.toSet()
        touched.forEach { id ->
            dao.getSet(id)?.let { s ->
                val pr = id in holders && dao.hasEarlierSets(s.exerciseId, workoutId, s.performedOn, s.completedAt ?: Long.MAX_VALUE)
                if (s.isPr != pr) dao.upsertSet(s.copy(isPr = pr))
            }
        }
        return EditResult(touched, removed)
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
        val headline = beaten.intersect(Records.HEADLINE)
        dao.upsertSet(filled.copy(isPr = headline.isNotEmpty()))
        return ToggleResult(true, headline)
    }

    /**
     * La marca a superar: tus récords de sesiones anteriores y, si ya los superaste hoy, tu mejor serie de hoy.
     * v1.9: un tipo sin marca anterior no entra (la primera vez de un ejercicio no es "récord").
     */
    private suspend fun currentBest(exerciseId: String, workoutId: String, excludeSet: String): Map<RecordKind, Double> {
        val stored = db.records().forExercise(exerciseId).associate { RecordKind.of(it.kind) to it.value }.toMutableMap()
        if (stored.isEmpty()) return stored
        val tracking = Tracking.of(db.exercises().get(exerciseId)?.tracking ?: "weight_reps")
        dao.sets(workoutId).filter { it.exerciseId == exerciseId && it.completed && it.id != excludeSet }.forEach { st ->
            Records.candidates(tracking, st.toData()).forEach { (k, v) -> val cur = stored[k]; if (cur != null && v > cur) stored[k] = v }
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
            // Récords definitivos. v1.9: la primera marca de cada ejercicio (o de cada tipo) se guarda para compararte la
            // próxima vez, pero no se cuenta ni se celebra: solo es récord lo que supera una marca anterior.
            val newRecords = mutableListOf<PersonalRecordEntity>()
            val beatPrevious = mutableListOf<PersonalRecordEntity>()
            for ((exId, exSets) in done.groupBy { it.exerciseId }) {
                val tracking = Tracking.of(catalog[exId]?.tracking ?: "weight_reps")
                val before = db.records().forExercise(exId).associateBy { it.kind }
                val best = before.toMutableMap()
                for (st in exSets) {
                    for ((kind, v) in Records.candidates(tracking, st.toData())) {
                        if (v > (best[kind.code]?.value ?: 0.0) + 1e-6) {
                            best[kind.code] = PersonalRecordEntity(exId, kind.code, uid(), v, st.id, st.completedAt ?: t, t, dirty = true)
                        }
                    }
                }
                val changed = best.values.filter { it.updatedAt == t }
                newRecords += changed
                beatPrevious += changed.filter { it.kind in before }
            }
            db.records().upsert(newRecords)
            val headline = Records.HEADLINE.map { it.code }.toSet()
            prCount = beatPrevious.count { it.kind in headline }
            val prSetIds = beatPrevious.filter { it.kind in headline }.mapNotNull { it.setId }.toSet()
            done.forEach { dao.upsertSet(it.copy(isPr = it.id in prSetIds, updatedAt = t, dirty = true)) }

            val working = done.filter { it.setType != SetType.WARMUP.code }
            val volume = working.sumOf { (it.weightKg ?: 0.0).coerceAtLeast(0.0) * (it.reps ?: 0) }
            // v1.9 (N2): si anotaste 20 min de caminadora al terminarla, el entreno dura al menos eso (antes "2 min").
            val cardioSec = done.filter { catalog[it.exerciseId]?.isCardio == true }.sumOf { (it.durationSec ?: 0).toLong() }
            val clockSec = ((t - w.startedAt) / 1000).coerceIn(0, 86_400 * 2)
            dao.upsertWorkout(w.copy(
                name = name?.takeIf { it.isNotBlank() } ?: w.name, notes = notes?.ifBlank { null } ?: w.notes,
                // Si el reloj del teléfono se atrasó a media sesión, nunca termina antes de empezar.
                endedAt = maxOf(t, w.startedAt), durationSec = maxOf(clockSec, cardioSec.coerceAtMost(86_400L * 2)).toInt(),
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
            // v1.7 (N4): si el récord vuelve a existir (p. ej. "Deshacer" tras borrar el entreno), ya no se borra en el
            // servidor. Antes quedaba el borrado pendiente y la sincronización subía el récord y luego lo eliminaba.
            best.keys.forEach { k -> pendingDeletes?.done(ex, k.code) }
            (existing.keys - best.keys).forEach { k -> db.records().delete(ex, k.code); pendingDeletes?.add(ex, k.code) }
        }
    }


    /** Una sola vez tras actualizar la app: deja los récords consistentes con el historial. */
    suspend fun rebuildAllRecords() = rebuildRecords(db.records().allExerciseIds())

    /**
     * v1.9 (N5) · Una vez al actualizar: trofeos 🏆 de cada serie y "récords" de cada entreno con la regla nueva (la
     * primera vez de un ejercicio no es récord). Recorre tu historial en orden, ejercicio por ejercicio: un tipo de
     * récord cuenta cuando un entreno supera lo mejor de los entrenos anteriores. Solo toca (y sube) lo que cambia.
     * Devuelve cuántos entrenos cambiaron.
     */
    suspend fun recomputeTrophies(): Int {
        val ids = db.stats().trainedExerciseIds()
        val catalog = db.exercises().getMany(ids).associateBy { it.id }
        val prSets = HashSet<String>(); val prCount = HashMap<String, Int>(); val stored = HashMap<String, Boolean>()
        for (ex in ids) {
            val tracking = Tracking.of(catalog[ex]?.tracking ?: "weight_reps")
            val best = HashMap<RecordKind, Double>()
            // groupBy conserva el orden en que aparecen: los entrenos del más viejo al más nuevo.
            for ((wid, rows) in dao.trophyRows(ex).groupBy { it.workoutId }) {
                val top = HashMap<RecordKind, Pair<Double, String>>()
                for (r in rows) {
                    stored[r.id] = r.isPr
                    for ((k, v) in Records.candidates(tracking, SetData(r.weightKg, r.reps, r.durationSec, null, SetType.of(r.setType))))
                        if (v > (top[k]?.first ?: 0.0) + 1e-6) top[k] = v to r.id
                }
                for ((k, p) in top) {
                    val prev = best[k]
                    if (prev != null && p.first > prev + 1e-6 && k in Records.HEADLINE) { prSets += p.second; prCount[wid] = (prCount[wid] ?: 0) + 1 }
                    if (prev == null || p.first > prev) best[k] = p.first
                }
            }
        }
        val t = now()
        val counts = dao.finishedPrCounts()
        val changedWorkouts = counts.filter { (prCount[it.id] ?: 0) != it.prCount }
        db.withTransaction {
            stored.forEach { (id, was) -> val now = id in prSets; if (now != was) dao.setPr(id, now, t) }
            changedWorkouts.forEach { dao.setPrCount(it.id, prCount[it.id] ?: 0, t) }
        }
        return changedWorkouts.size
    }

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
            previousSessions(exercise.id, excludeWorkout), cardio = exercise.isCardio,
        ))
    }
}

private fun WorkoutSetEntity.toData() = SetData(weightKg, reps, durationSec, rpe, SetType.of(setType))
