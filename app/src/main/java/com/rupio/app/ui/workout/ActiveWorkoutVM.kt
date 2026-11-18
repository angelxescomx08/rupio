package com.rupio.app.ui.workout

import kotlinx.coroutines.flow.first

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rupio.app.AppContainer
import com.rupio.app.data.local.ExerciseEntity
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Un ejercicio del entrenamiento con su catálogo y sus series (en orden). */
data class ExUi(val we: WorkoutExerciseEntity, val ex: ExerciseEntity?, val sets: List<WorkoutSetEntity>) {
    val tracking get() = Tracking.of(ex?.tracking ?: "weight_reps")
    val name get() = ex?.name ?: "Ejercicio"
    val infoKey get() = "${we.id}:${we.repMin}:${we.repMax}"
    val plan: List<PlanSet> by lazy { SetPlans.decode(we.setPlan) }
    fun planItem() = PlanItem(we.id, sets.size, we.restSec, we.supersetId, SupersetRestMode.of(we.supersetRestMode), we.supersetRestSec)
}

data class WorkoutUi(val workout: WorkoutEntity?, val items: List<ExUi>)

/** Recomendación + sesión anterior de un ejercicio (no cambian durante el entrenamiento). */
data class ExInfo(val rec: Recommendation, val prev: SessionData?)

/** Valores sugeridos (fantasma) de una serie: los que se usan si marcas ✓ con la celda vacía. */
data class Suggestion(val weightKg: Double?, val reps: Int?, val durationSec: Int?)

sealed interface WorkoutEvent {
    data class Message(val text: String) : WorkoutEvent
    data class Record(val exercise: String, val kinds: Set<RecordKind>) : WorkoutEvent
    /** Se quitó algo: snackbar con "Deshacer" (5 s) en vez de un diálogo de confirmación. */
    data class Undoable(val text: String, val undo: suspend () -> Unit) : WorkoutEvent
    data object Completed : WorkoutEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutVM(private val c: AppContainer) : ViewModel() {

    /** v1.8: lo mejor de cada sesión del ejercicio (gráfica dentro del entreno). Incluye lo ya marcado hoy. */
    suspend fun progress(exerciseId: String) = c.stats.sessionProgress(exerciseId)

    /** v1.8: último peso corporal registrado (para las calorías estimadas), o null. */
    suspend fun latestBodyKg(): Double? = c.bodyWeight.recent(1).first().firstOrNull()?.weightKg

    /** null = cargando; WorkoutUi(workout = null) = no hay entrenamiento activo. */
    val ui: StateFlow<WorkoutUi?> = c.workouts.active.flatMapLatest { w ->
        if (w == null) flowOf(WorkoutUi(null, emptyList()))
        else combine(c.workouts.observeExercises(w.id), c.workouts.observeSets(w.id), c.exercises.all) { ex, sets, catalog ->
            val byId = catalog.associateBy { it.id }
            val setsBy = sets.groupBy { it.workoutExerciseId }
            WorkoutUi(w, ex.map { ExUi(it, byId[it.exerciseId], setsBy[it.id].orEmpty().sortedBy { s -> s.position }) })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val infos = mutableStateMapOf<String, ExInfo>()
    private val loadingInfo = mutableSetOf<String>()

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WorkoutEvent> = _events

    init {
        viewModelScope.launch {
            ui.filterNotNull().collect { u -> val w = u.workout ?: return@collect; u.items.forEach { ensureInfo(it, w.id) } }
        }
    }

    private fun ensureInfo(item: ExUi, workoutId: String) {
        val key = item.infoKey
        val ex = item.ex ?: return
        if (key in infos || !loadingInfo.add(key)) return
        viewModelScope.launch {
            val rec = c.workouts.recommendation(ex, item.we.repMin, item.we.repMax, workoutId)
            val prev = c.workouts.previousSessions(ex.id, workoutId, 1).firstOrNull()
            infos[key] = ExInfo(rec, prev)
        }
    }

    // ------------------------------------------------------------------ sugerencias y "Anterior"
    /** Serie equivalente de la sesión anterior (misma posición entre calentamientos o entre efectivas). */
    fun previousFor(item: ExUi, set: WorkoutSetEntity): SetData? {
        val info = infos[item.infoKey] ?: return null
        val idx = item.sets.indexOfFirst { it.id == set.id }
        val warm = set.setType == SetType.WARMUP.code
        val sameIdx = item.sets.take(idx.coerceAtLeast(0)).count { (it.setType == SetType.WARMUP.code) == warm }
        return info.prev?.sets?.filter { (it.type == SetType.WARMUP) == warm }?.getOrNull(sameIdx)
    }

    /**
     * La serie planeada en la rutina que corresponde a esta (la k-ésima efectiva con la k-ésima efectiva del plan, igual
     * con los calentamientos): así agregar un calentamiento no recorre los pesos planeados una serie.
     */
    private fun plannedFor(item: ExUi, set: WorkoutSetEntity): PlanSet? {
        val idx = item.sets.indexOfFirst { it.id == set.id }
        val warm = set.setType == SetType.WARMUP.code
        val sameIdx = item.sets.take(idx.coerceAtLeast(0)).count { (it.setType == SetType.WARMUP.code) == warm }
        return item.plan.filter { (it.type == SetType.WARMUP) == warm }.getOrNull(sameIdx)
    }

    fun suggestion(item: ExUi, set: WorkoutSetEntity): Suggestion {
        val prev = previousFor(item, set)
        val idx = item.sets.indexOfFirst { it.id == set.id }
        val planned = plannedFor(item, set)
        val rec = infos[item.infoKey]?.rec
        // v1.9: un calentamiento conserva la sugerencia de repeticiones (antes quedaba vacío y la ✓ pedía escribirlas).
        if (set.setType == SetType.WARMUP.code) return Suggestion(prev?.weightKg ?: planned?.w, prev?.reps ?: planned?.r ?: rec?.targetReps ?: item.we.repMin,
            prev?.durationSec ?: planned?.d)
        // Serie efectiva más cercana hacia arriba que ya tenga ese dato (como "igual que la anterior").
        val before = item.sets.take(idx.coerceAtLeast(0)).filter { it.setType != SetType.WARMUP.code }.asReversed()
        // Sin historial todavía: manda el plan de tu rutina (tus pesos de siempre).
        val noHistory = infos[item.infoKey]?.prev == null
        if (noHistory && planned != null && (planned.w != null || planned.r != null || planned.d != null)) {
            return Suggestion(planned.w ?: before.firstNotNullOfOrNull { it.weightKg }, planned.r ?: rec?.targetReps, planned.d ?: rec?.targetDurationSec)
        }
        return Suggestion(
            weightKg = rec?.targetWeightKg ?: prev?.weightKg ?: before.firstNotNullOfOrNull { it.weightKg },
            reps = rec?.targetReps ?: prev?.reps ?: before.firstNotNullOfOrNull { it.reps },
            durationSec = rec?.targetDurationSec ?: prev?.durationSec ?: before.firstNotNullOfOrNull { it.durationSec },
        )
    }

    // ------------------------------------------------------------------ edición con debounce
    // Escribir en cada tecla haría una escritura en disco + recomposición por tecla; se agrupa 350 ms.
    private val editJobs = HashMap<String, Job>()
    private val pending = HashMap<String, (WorkoutSetEntity) -> WorkoutSetEntity>()

    fun edit(setId: String, f: (WorkoutSetEntity) -> WorkoutSetEntity) {
        val prev = pending[setId]
        pending[setId] = if (prev == null) f else { s -> f(prev(s)) }
        editJobs.remove(setId)?.cancel()
        editJobs[setId] = viewModelScope.launch { delay(350); editJobs.remove(setId); flushNow(setId) }
    }

    private suspend fun flushNow(setId: String) {
        val f = pending.remove(setId) ?: return
        c.workouts.editSet(setId, f)
    }

    private suspend fun flushAll() {
        editJobs.values.forEach { it.cancel() }; editJobs.clear()
        pending.keys.toList().forEach { flushNow(it) }
    }

    override fun onCleared() {
        val left = pending.toMap(); pending.clear()
        if (left.isNotEmpty()) c.scope.launch { left.forEach { (id, f) -> c.workouts.editSet(id, f) } }
        super.onCleared()
    }

    // ------------------------------------------------------------------ marcar serie + descanso
    private val toggleMutex = Mutex()

    /**
     * Marca/desmarca en serie (Mutex): toques rápidos nunca se cruzan.
     * Al desmarcar se cancela el descanso que inició ESA serie → nunca quedan dos temporizadores
     * ni suena doble (ver RestTimerController).
     */
    fun toggle(item: ExUi, set: WorkoutSetEntity) = viewModelScope.launch {
        toggleMutex.withLock {
            editJobs.remove(set.id)?.cancel(); flushNow(set.id)
            val fresh = c.workouts.getSet(set.id) ?: return@withLock
            val tr = item.tracking
            val sug = suggestion(item, set)
            if (!fresh.completed) {
                val missing = when {
                    tr == Tracking.WEIGHT_REPS && fresh.weightKg == null && sug.weightKg == null -> "Escribe el peso antes de marcar la serie."
                    tr == Tracking.ASSISTED_BODYWEIGHT && fresh.weightKg == null && sug.weightKg == null -> "Escribe cuánta ayuda usaste (0 si fue sin ayuda)."
                    tr.usesReps && fresh.reps == null && sug.reps == null -> "Escribe las repeticiones antes de marcar la serie."
                    tr == Tracking.DURATION && fresh.durationSec == null && sug.durationSec == null -> "Escribe los segundos antes de marcar la serie."
                    else -> null
                }
                if (missing != null) { _events.tryEmit(WorkoutEvent.Message(missing)); return@withLock }
            }
            val res = c.workouts.toggleComplete(set.id, tr, sug.weightKg, sug.reps, sug.durationSec)
            if (res.completed) {
                val all = ui.value?.items.orEmpty()
                val group = groupOf(all, item)
                val setIndex = item.sets.indexOfFirst { it.id == set.id }
                val completedInRound = group.count { g -> g.we.id == item.we.id || g.sets.getOrNull(setIndex)?.completed == true }
                // Drop set: si la siguiente serie es un drop set, se sigue sin descanso.
                val nextIsDrop = item.sets.getOrNull(setIndex + 1)?.setType == SetType.DROP.code
                val planned = RestPlanner.restAfterSet(item.planItem(), group.map { it.planItem() }, setIndex, completedInRound)
                // v1.9: tras un calentamiento, un descanso corto (antes era el completo de 2 min, como tras una serie pesada).
                val rest = when {
                    nextIsDrop -> 0
                    fresh.setType == SetType.WARMUP.code -> minOf(planned, SetNumbering.WARMUP_REST_SEC)
                    else -> planned
                }
                if (rest > 0) c.restTimer.start(rest, set.id, nextLabel(all, item, setIndex)) else c.restTimer.cancel()
                _events.tryEmit(if (res.newRecords.isNotEmpty()) WorkoutEvent.Record(item.name, res.newRecords) else WorkoutEvent.Completed)
            } else {
                c.restTimer.cancelIfSource(set.id)
            }
        }
    }

    private fun groupOf(all: List<ExUi>, item: ExUi): List<ExUi> {
        val ss = item.we.supersetId ?: return listOf(item)
        return all.filter { it.we.supersetId == ss }
    }

    /** v1.9: "serie 1" es la que en pantalla dice 1 (los calentamientos no cuentan), igual que la tabla. */
    private fun setName(e: ExUi, index: Int) = SetNumbering.spoken(e.sets.map { it.setType }, index)

    private fun nextLabel(all: List<ExUi>, item: ExUi, setIndex: Int): String {
        val group = groupOf(all, item)
        if (group.size > 1) {
            group.dropWhile { it.we.id != item.we.id }.drop(1).firstOrNull { it.sets.getOrNull(setIndex)?.completed == false }
                ?.let { return "Siguiente: ${it.name} · ${setName(it, setIndex)}" }
            group.firstOrNull { it.sets.getOrNull(setIndex + 1)?.completed == false }
                ?.let { return "Siguiente: ${it.name} · ${setName(it, setIndex + 1)}" }
        } else {
            item.sets.withIndex().drop(setIndex + 1).firstOrNull { !it.value.completed }
                ?.let { return "Siguiente: ${item.name} · ${setName(item, it.index)}" }
        }
        val lastOfGroup = all.indexOfLast { g -> group.any { it.we.id == g.we.id } }
        val next = all.drop(lastOfGroup + 1).firstOrNull { it.sets.any { s -> !s.completed } }
            ?: all.firstOrNull { it.sets.any { s -> !s.completed && s.id != item.sets.getOrNull(setIndex)?.id } }
        return next?.let { "Siguiente: ${it.name}" } ?: "¡Última serie! Termina cuando quieras"
    }

    // ------------------------------------------------------------------ acciones simples
    fun addSet(item: ExUi) = viewModelScope.launch { c.workouts.addSet(item.we.id) }
    /** v1.9: un calentamiento ANTES de la primera serie efectiva (no te quita una de trabajo). */
    fun addWarmup(item: ExUi) = viewModelScope.launch { c.workouts.addWarmup(item.we.id) }

    /**
     * v1.9: series sin ✓ en las que sí escribiste algo (peso, reps o tiempo). Al terminar se borrarían: se avisa aparte
     * de las series vacías del plan y se ofrece "Marcar y guardar".
     */
    fun unmarkedWithData(items: List<ExUi>): List<Pair<ExUi, WorkoutSetEntity>> = items.flatMap { e ->
        e.sets.filter { s -> !s.completed && s.hasData() }.map { e to it }
    }

    private fun WorkoutSetEntity.hasData() = weightKg != null || reps != null || durationSec != null

    /**
     * Marca esas series (con lo escrito o lo sugerido) y termina. Si a alguna le falta algo que no se puede sugerir
     * (el peso la primera vez), no termina: dice cuál completar, para no borrar lo que escribiste. null = no terminó.
     */
    suspend fun finishMarking(workoutId: String, name: String, notes: String, sets: List<Pair<ExUi, WorkoutSetEntity>>): Int? {
        flushAll()
        val ready = mutableListOf<Triple<WorkoutSetEntity, Tracking, Suggestion>>()
        for ((e, s) in sets) {
            val fresh = c.workouts.getSet(s.id) ?: continue
            if (fresh.completed) continue
            val sug = suggestion(e, fresh)
            val tr = e.tracking
            val missing = when {
                tr == Tracking.WEIGHT_REPS && fresh.weightKg == null && sug.weightKg == null -> "el peso"
                tr == Tracking.ASSISTED_BODYWEIGHT && fresh.weightKg == null && sug.weightKg == null -> "la ayuda"
                tr.usesReps && fresh.reps == null && sug.reps == null -> "las repeticiones"
                tr == Tracking.DURATION && fresh.durationSec == null && sug.durationSec == null -> "los segundos"
                else -> null
            }
            if (missing != null) {
                val label = SetNumbering.spoken(e.sets.map { it.setType }, e.sets.indexOfFirst { it.id == s.id })
                val where = (if (label.startsWith("serie")) "la " else "el ") + label
                _events.tryEmit(WorkoutEvent.Message("Escribe ${missing} en $where de ${e.name} (o bórrala) y vuelve a terminar."))
                return null
            }
            ready += Triple(fresh, tr, sug)
        }
        ready.forEach { (s, tr, sug) -> c.workouts.toggleComplete(s.id, tr, sug.weightKg, sug.reps, sug.durationSec) }
        return finish(workoutId, name, notes)
    }

    /** ¿Hay algo registrado? (para descartar sin preguntar un entreno que quedó vacío). */
    fun hasAnyData(items: List<ExUi>): Boolean = items.any { e -> e.sets.any { it.completed || it.hasData() } }
    fun removeSet(set: WorkoutSetEntity) = viewModelScope.launch {
        c.restTimer.cancelIfSource(set.id)
        val before = c.workouts.snapshot(set.workoutId, null).let { snap -> snap.copy(exercises = emptyList(), sets = snap.sets.filter { it.id == set.id }) }
        c.workouts.removeSet(set.id)
        _events.tryEmit(WorkoutEvent.Undoable("Serie eliminada") { c.workouts.restore(before) })
    }
    fun setType(set: WorkoutSetEntity, type: SetType) = viewModelScope.launch { c.workouts.editSet(set.id) { it.copy(setType = type.code) } }
    fun setRpe(set: WorkoutSetEntity, rpe: Double?) = viewModelScope.launch { c.workouts.editSet(set.id) { it.copy(rpe = rpe) } }
    fun setRest(item: ExUi, sec: Int) = viewModelScope.launch { c.workouts.updateExerciseRest(item.we.id, item.we.workoutId, sec) }
    fun setNotes(item: ExUi, notes: String) = viewModelScope.launch { c.workouts.updateNotes(item.we.id, item.we.workoutId, notes) }
    fun setRepRange(item: ExUi, lo: Int, hi: Int) = viewModelScope.launch { c.workouts.updateRepRange(item.we.id, item.we.workoutId, lo, hi) }
    fun move(item: ExUi, delta: Int) = viewModelScope.launch { c.workouts.move(item.we.workoutId, item.we.id, delta) }
    fun remove(item: ExUi) = viewModelScope.launch {
        item.sets.forEach { c.restTimer.cancelIfSource(it.id) }
        val before = c.workouts.snapshot(item.we.workoutId, item.we.id)
        c.workouts.removeExercise(item.we.id, item.we.workoutId)
        _events.tryEmit(WorkoutEvent.Undoable("${item.name} quitado") { c.workouts.restore(before) })
    }
    fun removeFromSuperset(item: ExUi) = viewModelScope.launch { c.workouts.removeFromSuperset(item.we.workoutId, item.we.id) }

    /** Ids para "unir con el siguiente": el grupo actual + el siguiente ejercicio (o su grupo). */
    fun membersWithNext(item: ExUi): List<String>? {
        val all = ui.value?.items ?: return null
        val group = groupOf(all, item).map { it.we.id }
        val lastIdx = all.indexOfLast { it.we.id in group }
        val next = all.getOrNull(lastIdx + 1) ?: return null
        val nextGroup = groupOf(all, next).map { it.we.id }
        return group + nextGroup
    }

    fun createSuperset(workoutId: String, ids: List<String>, mode: SupersetRestMode, rest: Int?) =
        viewModelScope.launch { c.workouts.createSuperset(workoutId, ids, mode, rest) }

    fun updateSuperset(workoutId: String, ss: String, mode: SupersetRestMode, rest: Int?) =
        viewModelScope.launch { c.workouts.updateSupersetRest(workoutId, ss, mode, rest) }

    fun ungroup(workoutId: String, ss: String) = viewModelScope.launch {
        c.workouts.ungroup(workoutId, ss)
    }

    fun rename(workoutId: String, name: String) = viewModelScope.launch { c.workouts.renameActive(workoutId, name.trim().ifBlank { "Entrenamiento" }) }

    suspend fun finish(workoutId: String, name: String, notes: String): Int {
        flushAll()
        c.restTimer.cancel()
        return c.workouts.finish(workoutId, name, notes)
    }

    suspend fun discard(workoutId: String) {
        editJobs.values.forEach { it.cancel() }; editJobs.clear(); pending.clear()
        c.restTimer.cancel()
        c.workouts.discard(workoutId)
    }
}
