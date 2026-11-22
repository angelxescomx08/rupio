package com.rupio.app.data.update

import android.app.Activity
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.rupio.app.core.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Lo que sigue en memoria y todavía no se escribe en el teléfono (p. ej. el peso de una serie a medio escribir, que
 * se guarda 350 ms después). Antes de reiniciar Rupio para instalar una actualización se escribe todo aquí.
 */
class UnsavedWork {
    interface Source {
        /** Escribe lo pendiente y regresa cuando ya quedó guardado. */
        suspend fun save()
        fun hasUnsaved(): Boolean
    }

    private val sources = CopyOnWriteArraySet<Source>()
    fun register(s: Source) { sources.add(s) }
    fun unregister(s: Source) { sources.remove(s) }

    /**
     * Guarda todo y comprueba que no quedó nada pendiente. Se llama desde el hilo principal (el aviso de reinicio), el
     * mismo de las pantallas que lo llenan.
     */
    suspend fun saveAll(): Boolean {
        sources.forEach { it.save() }
        return sources.none { it.hasUnsaved() }
    }
}

/**
 * v2.2: actualizaciones flexibles de Google Play
 * (https://developer.android.com/guide/playcore/in-app-updates/kotlin-java).
 *
 *  1. Al abrir o volver a Rupio ([check]) se pregunta a Google Play si hay una versión nueva para esta cuenta y este
 *     teléfono. Si la hay y permite el tipo FLEXIBLE, se ofrece «Actualizar» / «Ahora no».
 *  2. «Actualizar» abre el diálogo oficial de Play ([accept]); la descarga sigue en segundo plano mientras se usa la app.
 *  3. Descargada, se ofrece «Reiniciar y actualizar». Antes de reiniciar se guarda lo pendiente ([restartAndInstall]).
 *
 * Una respuesta a la oferta (aceptar, «Ahora no», cancelar en Play o un error) vale por toda la sesión: no se vuelve a
 * ofrecer hasta que Rupio se abra de nuevo. Si Play falla o no está, no pasa nada: la actualización nunca bloquea la app.
 * Vive en el contenedor (un solo listener para todo el proceso, aunque la pantalla se recree). Todo corre en el hilo
 * principal: ahí llegan las respuestas de Play.
 *
 * [manager] null = Google Play no disponible en este teléfono.
 */
class AppUpdateController(
    private val manager: AppUpdateManager?,
    private val saveProgress: suspend () -> Boolean,
    /** La instalación no se hizo (Play la rechazó o falló): se borra la nota de «abrir el entreno al volver». */
    private val onInstallFailed: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Phase { IDLE, AVAILABLE, DOWNLOADING, DOWNLOADED, INSTALLING }

    data class Ui(
        val phase: Phase = Phase.IDLE,
        /** Aviso «Actualizar» / «Ahora no». */
        val offer: Boolean = false,
        /** Aviso «Reiniciar y actualizar». */
        val restart: Boolean = false,
        /** 0..1 mientras se descarga, si Play dice el tamaño. */
        val progress: Float? = null,
        /** Guardando el progreso antes de reiniciar. */
        val saving: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    /**
     * Aviso corto pendiente («Descargando la actualización…»). Se queda aquí hasta que AppUpdateHost lo muestre: así no
     * se pierde si en ese momento estás en el inicio de sesión, la bienvenida o con la app en segundo plano.
     */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    /** Ya se mostró [text]. Si mientras tanto llegó otro aviso, ese se queda para mostrarse después. */
    fun messageShown(text: String? = null) { if (text == null) _message.value = null else _message.compareAndSet(text, null) }
    private fun onMessage(text: String) { _message.value = text }

    /** Última respuesta de Play con una versión disponible. Cada una sirve para iniciar el flujo UNA sola vez. */
    private var info: AppUpdateInfo? = null
    private var queryStartedAt = 0L
    private var offerAnswered = false
    private var restartPostponed = false
    private var restartAfterWorkout = false
    private var training = false
    private var listening = false
    private val listener = InstallStateUpdatedListener { s -> onInstallState(s.installStatus(), s.bytesDownloaded(), s.totalBytesToDownload()) }

    /** Al abrir o volver a Rupio. Se puede llamar en cada onResume: no duplica avisos ni listeners. */
    fun check() {
        val m = manager ?: return
        val t = now()
        if (queryStartedAt != 0L && t - queryStartedAt < QUERY_TIMEOUT_MS) return
        queryStartedAt = t
        val task = try { m.appUpdateInfo } catch (e: Exception) { queryStartedAt = 0; warn("consulta", e); return }
        task.addOnCompleteListener { r ->
            queryStartedAt = 0
            if (r.isSuccessful) r.result?.let(::onInfo) else warn("consulta", r.exception)
        }
    }

    private fun onInfo(i: AppUpdateInfo) {
        when (i.installStatus()) {
            // Se descargó en esta sesión o en una anterior (la app se cerró a media descarga): falta reiniciar.
            InstallStatus.DOWNLOADED -> { listen(); showDownloaded() }
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> {
                offerAnswered = true
                listen()
                _ui.update { it.copy(phase = Phase.DOWNLOADING, offer = false, progress = progressOf(i.bytesDownloaded(), i.totalBytesToDownload()) ?: it.progress) }
            }
            InstallStatus.INSTALLING -> _ui.update { it.copy(phase = Phase.INSTALLING, offer = false, restart = false) }
            else -> {
                val available = i.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && i.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                val phase = _ui.value.phase
                if (phase != Phase.IDLE && phase != Phase.AVAILABLE) return
                if (available) {
                    info = i
                    _ui.value = Ui(Phase.AVAILABLE, offer = !offerAnswered)
                } else {
                    info = null
                    _ui.value = Ui()
                }
            }
        }
    }

    /** «Actualizar»: abre el diálogo oficial de Google Play para una actualización flexible. */
    fun accept(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        offerAnswered = true
        _ui.update { it.copy(offer = false) }
        val m = manager ?: return
        val i = info ?: return
        info = null
        listen() // antes de iniciar, como pide la documentación
        val started = try {
            m.startUpdateFlowForResult(i, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
        } catch (e: Exception) { warn("inicio", e); false }
        if (!started) { stopListening(); _ui.value = Ui() }
    }

    /** «Ahora no». No se vuelve a ofrecer en esta sesión. */
    fun decline() {
        offerAnswered = true
        info = null
        if (_ui.value.phase == Phase.AVAILABLE) _ui.value = Ui()
    }

    /** Respuesta del diálogo de Google Play (Activity Result API). */
    fun onFlowResult(resultCode: Int) {
        offerAnswered = true
        if (manager == null) return
        if (resultCode == Activity.RESULT_OK) {
            // Aceptó: Play descarga en segundo plano y el listener avisa el avance.
            if (_ui.value.phase == Phase.IDLE || _ui.value.phase == Phase.AVAILABLE) _ui.value = Ui(Phase.DOWNLOADING)
            onMessage(tr("Descargando la actualización. Puedes seguir usando Rupio.", "Downloading the update. You can keep using Rupio."))
            return
        }
        // Canceló (RESULT_CANCELED) o falló (RESULT_IN_APP_UPDATE_FAILED): sigue usando la app sin actualizar.
        if (resultCode != Activity.RESULT_CANCELED) warn("diálogo de Play", null, resultCode)
        if (_ui.value.phase != Phase.DOWNLOADED && _ui.value.phase != Phase.INSTALLING) { stopListening(); _ui.value = Ui() }
    }

    private fun onInstallState(status: Int, bytes: Long, total: Long) {
        when (status) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
                _ui.update { it.copy(phase = Phase.DOWNLOADING, offer = false, progress = progressOf(bytes, total) ?: it.progress) }
            InstallStatus.DOWNLOADED -> showDownloaded()
            InstallStatus.INSTALLING -> _ui.update { it.copy(phase = Phase.INSTALLING, restart = false) }
            InstallStatus.INSTALLED -> { stopListening(); _ui.value = Ui() }
            InstallStatus.CANCELED -> { stopListening(); _ui.value = Ui(); onInstallFailed() }
            InstallStatus.FAILED -> {
                stopListening(); _ui.value = Ui(); onInstallFailed()
                onMessage(tr("No se pudo descargar la actualización. Puedes seguir usando Rupio.", "The update couldn't be downloaded. You can keep using Rupio."))
            }
        }
    }

    private fun showDownloaded() {
        val show = !restartPostponed && !(restartAfterWorkout && training)
        _ui.update { it.copy(phase = Phase.DOWNLOADED, offer = false, restart = show || it.restart, progress = null) }
    }

    /**
     * «Más tarde» / «Después del entreno». [untilWorkoutEnds]: se vuelve a ofrecer al terminar el entreno. Si no, la
     * próxima vez que Rupio vuelva a primer plano ([onAppBackgrounded]), como recomienda Google: mientras no se
     * instala, la descarga ocupa espacio.
     */
    fun postponeRestart(untilWorkoutEnds: Boolean) {
        if (untilWorkoutEnds && training) restartAfterWorkout = true else restartPostponed = true
        _ui.update { it.copy(restart = false, error = null) }
    }

    /**
     * Rupio pasó a segundo plano (ProcessLifecycleOwner: no cuenta girar la pantalla). Al volver, [check] ofrece otra vez
     * reiniciar si se había dejado para «Más tarde». «Después del entreno» se respeta hasta que el entreno termine
     * (entre series se bloquea el teléfono a cada rato).
     */
    fun onAppBackgrounded() { restartPostponed = false }

    /**
     * Hay (o ya no hay) un entreno en curso. Solo el paso de «con entreno» a «sin entreno» cuenta como terminarlo: llamar
     * con false sin haber tenido entreno no cambia nada.
     */
    fun onTrainingChanged(active: Boolean) {
        val wasTraining = training
        training = active
        if (wasTraining && !active && restartAfterWorkout) {
            restartAfterWorkout = false
            if (_ui.value.phase == Phase.DOWNLOADED && !restartPostponed) _ui.update { it.copy(restart = true) }
        }
    }

    /**
     * «Reiniciar y actualizar»: primero guarda el progreso y comprueba que quedó guardado; solo entonces pide a Play
     * instalar (Rupio se cierra y vuelve a abrir). Si algo falla, la app sigue igual y se puede intentar otra vez.
     */
    suspend fun restartAndInstall() {
        val m = manager ?: return
        if (_ui.value.phase != Phase.DOWNLOADED || _ui.value.saving) return
        _ui.update { it.copy(saving = true, error = null) }
        val saved = try { saveProgress() } catch (e: CancellationException) { _ui.update { it.copy(saving = false) }; throw e } catch (e: Exception) { warn("guardar", e); false }
        if (!saved) {
            _ui.update { it.copy(saving = false, error = tr("No se pudo guardar tu progreso, así que no reiniciamos. Inténtalo de nuevo.",
                "Your progress couldn't be saved, so we didn't restart. Please try again.")) }
            return
        }
        _ui.update { it.copy(saving = false, phase = Phase.INSTALLING, restart = false) }
        val failed = { e: Exception? ->
            warn("instalar", e)
            onInstallFailed()
            _ui.update { it.copy(phase = Phase.DOWNLOADED, restart = true, error = tr("No se pudo instalar la actualización. Inténtalo más tarde.",
                "The update couldn't be installed. Please try again later.")) }
        }
        try { m.completeUpdate().addOnFailureListener { e -> failed(e) } } catch (e: Exception) { failed(e) }
    }

    private fun listen() {
        if (listening) return
        val m = manager ?: return
        try { m.registerListener(listener); listening = true } catch (e: Exception) { warn("listener", e) }
    }

    private fun stopListening() {
        if (!listening) return
        listening = false
        runCatching { manager?.unregisterListener(listener) }
    }

    private fun progressOf(bytes: Long, total: Long): Float? = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null

    private fun warn(what: String, e: Exception?, code: Int? = null) {
        runCatching { Log.w(TAG, "Actualización de Play ($what) sin éxito${code?.let { " · código $it" }.orEmpty()}: ${e?.message}") }
    }

    companion object {
        private const val TAG = "RupioUpdate"
        /** Si Play nunca responde, se puede volver a preguntar después de esto. */
        const val QUERY_TIMEOUT_MS = 30_000L
    }
}
