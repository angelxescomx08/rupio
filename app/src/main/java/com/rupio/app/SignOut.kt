package com.rupio.app

import com.rupio.app.data.remote.SyncEngine
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class SignOutStep { UPLOAD, WIPE, SIGN_OUT, DONE }

data class SignOutState(
    val active: Boolean = false,
    val step: SignOutStep = SignOutStep.UPLOAD,
    val localOnly: Boolean = false,
    /** Cambios que faltaban por subir al empezar. */
    val pendingAtStart: Int = 0,
    /** Cambios que siguen sin subir (cuando falla la subida). */
    val pendingLeft: Int = 0,
    /** La subida falló o tardó demasiado: se espera la decisión de la persona. */
    val uploadFailed: Boolean = false,
    /** true = el servidor respondió pero rechazó algunos cambios; false = no hubo conexión. */
    val serverRejected: Boolean = false,
)

/**
 * "Cerrar sesión" en pasos visibles, para que se entienda qué pasa y por qué tarda:
 *   1. Subir lo pendiente (para no perder nada).  2. Borrar los datos del teléfono.  3. Cerrar la sesión.
 *
 * Vive en el contenedor (no en la pantalla): sigue aunque la UI cambie a la pantalla de inicio de sesión.
 * Si la subida falla, NO se borra nada sin preguntar: se ofrece reintentar, salir sin subir o cancelar.
 */
class SignOutController(
    private val c: AppContainer,
    private val uploadTimeoutMs: Long = UPLOAD_TIMEOUT_MS,
    private val uploader: suspend () -> Boolean = { c.sync.sync() },
) {
    private val _state = MutableStateFlow(SignOutState())
    val state: StateFlow<SignOutState> = _state
    private var job: Job? = null
    private var upload: Deferred<Boolean>? = null

    fun start(localOnly: Boolean) {
        if (_state.value.active) return
        _state.value = SignOutState(active = true, localOnly = localOnly, step = if (localOnly) SignOutStep.WIPE else SignOutStep.UPLOAD)
        job = c.scope.launch { if (localOnly) finish() else uploadThenFinish() }
    }

    fun retry() {
        if (!_state.value.uploadFailed) return
        _state.update { it.copy(uploadFailed = false) }
        job = c.scope.launch { uploadThenFinish() }
    }

    /** Salir aunque queden cambios sin subir (la persona ya vio cuántos se pierden). */
    fun exitWithoutUpload() {
        if (!_state.value.uploadFailed) return
        _state.update { it.copy(uploadFailed = false) }
        job = c.scope.launch {
            // Una subida que sigue en curso se detiene y se espera, para que no escriba nada después de borrar.
            upload?.let { it.cancel(); runCatching { it.join() } }
            finish()
        }
    }

    /** Se arrepintió: sigue en la app con todo como estaba. */
    fun cancel() {
        if (!_state.value.uploadFailed) return
        _state.value = SignOutState()
    }

    private suspend fun uploadThenFinish() {
        val before = c.db.admin().countDirty()
        _state.update { it.copy(step = SignOutStep.UPLOAD, pendingAtStart = maxOf(it.pendingAtStart, before)) }
        val started = System.currentTimeMillis()
        // Si no hay nada pendiente, igual se intenta (rápido) para dejar la nube al día, pero sin bloquear si no hay red.
        val running = upload?.takeIf { it.isActive } ?: c.scope.async { runCatching { uploader() }.getOrDefault(false) }.also { upload = it }
        val ok = withTimeoutOrNull(if (before == 0) minOf(6_000L, uploadTimeoutMs) else uploadTimeoutMs) { running.await() }
        val left = c.db.admin().countDirty()
        minStep(started)
        if (left > 0) {
            _state.update { it.copy(uploadFailed = true, pendingLeft = left, serverRejected = ok == true) }
            return
        }
        if (ok == null) { running.cancel(); runCatching { running.join() } }   // nada pendiente: no hace falta esperar
        finish()
    }

    private suspend fun finish() {
        SyncEngine.cancel(c.app)
        // Primero se borra (con la sesión aún abierta) y al final se cierra: así la pantalla de inicio de sesión
        // aparece cuando ya no queda nada de esta cuenta en el teléfono. claimLocalData cubre un cierre a medias.
        var t = System.currentTimeMillis()
        _state.update { it.copy(step = SignOutStep.WIPE) }
        // Un entreno en curso se descarta (ya se avisó en la confirmación): se apagan el cronómetro y su notificación.
        c.restTimer.cancel(); runCatching { com.rupio.app.timer.WorkoutService.stop(c.app) }
        c.wipeLocalData()
        minStep(t)
        t = System.currentTimeMillis()
        _state.update { it.copy(step = SignOutStep.SIGN_OUT) }
        // El aviso al servidor va en segundo plano: con mala señal ya no se queda colgado aquí.
        c.auth.signOut(background = c.scope)
        minStep(t)
        _state.update { it.copy(step = SignOutStep.DONE) }
        delay(DONE_VISIBLE_MS)
        _state.value = SignOutState()
    }

    /** Cada paso se ve al menos un momento: si todo pasa en 50 ms, la persona no alcanza a leer qué ocurrió. */
    private suspend fun minStep(startedAt: Long) {
        val left = MIN_STEP_MS - (System.currentTimeMillis() - startedAt)
        if (left > 0) delay(left)
    }

    companion object {
        const val UPLOAD_TIMEOUT_MS = 20_000L
        const val MIN_STEP_MS = 450L
        const val DONE_VISIBLE_MS = 900L
    }
}
