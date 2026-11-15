package com.rupio.app.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fuente ÚNICA de verdad del temporizador de descanso.
 *
 * Por qué no suena doble (el bug que tiene Hevy al marcar/desmarcar una serie):
 *  1. Solo existe UN temporizador a la vez. Iniciar uno nuevo reemplaza al anterior
 *     y le sube la "generación".
 *  2. Desmarcar la serie que lo inició lo cancela (sube la generación → el pendiente queda obsoleto).
 *  3. El sonido lo reproduce un solo componente (WorkoutService) y solo después de
 *     ganar [tryConsumeFinish]: compara la generación y marca "ya sonó" de forma atómica.
 *     Un fin obsoleto o repetido devuelve false y no suena.
 *  4. La UI nunca reproduce sonido; la notificación en curso usa un canal silencioso.
 *
 * El reloj es inyectable (elapsedRealtime en Android) para poder probarlo.
 */
class RestTimerController(private val clock: () -> Long) {

    data class State(
        val generation: Long,
        val endsAt: Long,        // en el reloj monotónico
        val totalSec: Int,
        val sourceSetId: String?,
        val label: String,
    ) {
        fun remainingMs(now: Long) = (endsAt - now).coerceAtLeast(0)
    }

    private val lock = Any()
    private var generation = 0L
    private var lastFired = 0L
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /** Arranca (o reinicia) el descanso. Devuelve la generación asignada. */
    fun start(totalSec: Int, sourceSetId: String?, label: String): Long = synchronized(lock) {
        generation += 1
        _state.value = if (totalSec > 0) State(generation, clock() + totalSec * 1000L, totalSec, sourceSetId, label) else null
        generation
    }

    /** Cancela solo si el temporizador actual lo inició esa serie (desmarcar ✓). */
    fun cancelIfSource(setId: String) = synchronized(lock) {
        if (_state.value?.sourceSetId == setId) { generation += 1; _state.value = null }
    }

    fun cancel() = synchronized(lock) { generation += 1; _state.value = null }

    /** ±15 s. Mantiene la misma generación (no es un descanso nuevo). */
    fun adjust(deltaSec: Int) = synchronized(lock) {
        val s = _state.value ?: return@synchronized
        val newEnd = s.endsAt + deltaSec * 1000L
        if (newEnd <= clock()) { generation += 1; _state.value = null }
        else _state.value = s.copy(endsAt = newEnd, totalSec = (s.totalSec + deltaSec).coerceAtLeast(1))
    }

    /**
     * Llamado por quien va a reproducir el sonido cuando cree que el descanso terminó.
     * Devuelve true UNA sola vez por generación y solo si sigue siendo la vigente y ya venció.
     */
    fun tryConsumeFinish(gen: Long): Boolean = synchronized(lock) {
        val s = _state.value
        if (s == null || s.generation != gen || gen <= lastFired || clock() < s.endsAt) return@synchronized false
        lastFired = gen
        _state.value = null
        true
    }
}
