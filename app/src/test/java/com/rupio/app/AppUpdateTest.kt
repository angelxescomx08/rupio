package com.rupio.app

import android.app.Activity
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.I18n
import com.rupio.app.data.update.AppUpdateController
import com.rupio.app.data.update.AppUpdateController.Phase
import com.rupio.app.data.update.AppUpdateController.Ui
import com.rupio.app.ui.workout.ActiveWorkoutVM
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * v2.2: actualizaciones flexibles de Google Play con el FakeAppUpdateManager oficial de Play
 * (https://developer.android.com/guide/playcore/in-app-updates/test). Cubre detección, cancelación, recuperar una
 * descarga terminada, fallos de la consulta y guardar el entreno antes de reiniciar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateTest {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val bench = "press_de_banca_con_barra"

    /** El manager falso de Play, contando listeners y llamadas; [failQuery]/[throwQuery] simulan que Play falla. */
    private class Play(val fake: FakeAppUpdateManager, val log: MutableList<String> = mutableListOf()) : AppUpdateManager by fake {
        var registered = 0; var unregistered = 0; var queries = 0
        var failQuery: Exception? = null
        var throwQuery: RuntimeException? = null
        var hangQuery = false
        val activeListeners get() = registered - unregistered
        override fun registerListener(l: InstallStateUpdatedListener) { registered++; fake.registerListener(l) }
        override fun unregisterListener(l: InstallStateUpdatedListener) { unregistered++; fake.unregisterListener(l) }
        override fun getAppUpdateInfo(): Task<AppUpdateInfo> {
            queries++
            throwQuery?.let { throw it }
            failQuery?.let { return Tasks.forException(it) }
            if (hangQuery) return TaskCompletionSource<AppUpdateInfo>().task
            return fake.appUpdateInfo
        }
        override fun completeUpdate(): Task<Void> { log += "instalar"; return fake.completeUpdate() }
    }

    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) {}
        override fun unregister() {}
        override val contract: ActivityResultContract<IntentSenderRequest, *> get() = ActivityResultContracts.StartIntentSenderForResult()
    }
    private val created = mutableListOf<AppUpdateController>()
    /** Avisos cortos pendientes de mostrar en todos los controladores de la prueba. */
    private val messages get() = created.mapNotNull { it.message.value }
    private var clock = 1_000_000L
    private fun controller(play: AppUpdateManager?, save: suspend () -> Boolean = { true }) =
        AppUpdateController(play, save, now = { clock }).also { created += it }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun available(type: Int = AppUpdateType.FLEXIBLE) = Play(FakeAppUpdateManager(app).apply { setUpdateAvailable(23, type) })

    @Before fun es() { I18n.set(AppLanguage.ES) }

    // ------------------------------------------------------------------ detección

    @Test fun detecta_una_version_nueva_flexible_y_la_ofrece() {
        val none = Play(FakeAppUpdateManager(app))
        val u0 = controller(none); u0.check(); idle()
        assertEquals("sin versión nueva no se avisa nada", Ui(), u0.ui.value)

        val play = available(); val u = controller(play)
        u.check(); idle()
        assertEquals(Phase.AVAILABLE, u.ui.value.phase)
        assertTrue("aviso «Actualizar» / «Ahora no»", u.ui.value.offer)
        assertEquals("solo consultar no registra listeners", 0, play.registered)

        // Volver a la app (onResume) con el aviso abierto: sigue un solo aviso.
        u.check(); idle()
        assertTrue(u.ui.value.offer); assertEquals(2, play.queries)

        // Si Play solo permite la inmediata para esta versión, no se ofrece la flexible.
        val onlyImmediate = available(AppUpdateType.IMMEDIATE); val ui = controller(onlyImmediate)
        ui.check(); idle()
        assertFalse(ui.ui.value.offer)
    }

    @Test fun actualizar_descarga_en_segundo_plano_y_ofrece_reiniciar() = runBlocking {
        val play = available(); val u = controller(play)
        u.check(); idle()
        u.accept(launcher)
        assertTrue("se abre el diálogo oficial de Play", play.fake.isConfirmationDialogVisible)
        assertFalse(u.ui.value.offer)
        assertEquals("el listener se registra antes de iniciar", 1, play.registered)

        play.fake.userAcceptsUpdate(); u.onFlowResult(Activity.RESULT_OK); idle()
        assertEquals(Phase.DOWNLOADING, u.ui.value.phase)
        assertTrue(messages.last().contains("Puedes seguir usando Rupio"))
        play.fake.downloadStarts()
        play.fake.setTotalBytesToDownload(1000); play.fake.setBytesDownloaded(400); idle()
        assertEquals(0.4f, u.ui.value.progress!!, 0.001f)

        // onResume a media descarga: no se duplica el listener ni vuelve la oferta.
        u.check(); u.check(); idle()
        assertEquals(1, play.activeListeners)
        assertFalse(u.ui.value.offer)

        play.fake.downloadCompletes(); idle()
        assertEquals(Phase.DOWNLOADED, u.ui.value.phase)
        assertTrue("«Reiniciar y actualizar»", u.ui.value.restart)

        u.restartAndInstall(); idle()
        assertTrue("Play instala (pantalla de reinicio)", play.fake.isInstallSplashScreenVisible)
        assertEquals(Phase.INSTALLING, u.ui.value.phase)
        play.fake.installCompletes(); idle()
        assertEquals(Ui(), u.ui.value)
        assertEquals("al terminar se quita el listener", 0, play.activeListeners)
    }

    // ------------------------------------------------------------------ cancelación

    @Test fun ahora_no_no_se_repite_en_la_misma_sesion() {
        val play = available(); val u = controller(play)
        u.check(); idle()
        u.decline()
        assertEquals(Ui(), u.ui.value)
        u.check(); idle(); u.check(); idle()
        assertFalse("no se repite en la sesión", u.ui.value.offer)
        assertEquals(0, play.registered)

        // Rupio se vuelve a abrir (sesión nueva): se ofrece otra vez.
        val next = controller(play); next.check(); idle()
        assertTrue(next.ui.value.offer)
    }

    @Test fun cancelar_en_google_play_deja_seguir_sin_repetir_el_aviso() {
        for (result in listOf(Activity.RESULT_CANCELED, ActivityResult.RESULT_IN_APP_UPDATE_FAILED)) {
            val play = available(); val u = controller(play)
            u.check(); idle()
            u.accept(launcher)
            play.fake.userRejectsUpdate(); u.onFlowResult(result); idle()
            assertEquals("resultado $result", Ui(), u.ui.value)
            assertEquals("resultado $result: se quita el listener", 0, play.activeListeners)
            u.check(); idle()
            assertFalse("resultado $result: no vuelve a ofrecerse", u.ui.value.offer)
        }
        assertTrue("cancelar no muestra errores", messages.isEmpty())
    }

    @Test fun cancelar_la_descarga_desde_la_notificacion_de_play() {
        val play = available(); val u = controller(play)
        u.check(); idle(); u.accept(launcher)
        play.fake.userAcceptsUpdate(); u.onFlowResult(Activity.RESULT_OK); play.fake.downloadStarts(); idle()
        play.fake.userCancelsDownload(); idle()
        assertEquals(Ui(), u.ui.value)
        assertEquals(0, play.activeListeners)
    }

    // ------------------------------------------------------------------ recuperar al volver a abrir

    /** Sesión anterior: aceptó y Play terminó de descargar, pero Rupio se cerró antes de reiniciar. */
    private fun downloadedInPreviousSession(log: MutableList<String> = mutableListOf()): Play {
        val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(23, AppUpdateType.FLEXIBLE) }
        fake.startUpdateFlowForResult(fake.appUpdateInfo.result, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
        fake.userAcceptsUpdate(); fake.downloadStarts(); fake.downloadCompletes()
        return Play(fake, log)
    }

    @Test fun al_volver_a_abrir_recupera_la_descarga_terminada() = runBlocking {
        val log = mutableListOf<String>()
        val play = downloadedInPreviousSession(log)
        val u = controller(play, save = { log += "guardar"; true })
        u.check(); idle()
        assertEquals(Phase.DOWNLOADED, u.ui.value.phase)
        assertTrue(u.ui.value.restart); assertFalse("no ofrece descargar otra vez", u.ui.value.offer)
        // Varios onResume seguidos: un solo aviso y un solo listener.
        u.check(); idle(); u.check(); idle()
        assertTrue(u.ui.value.restart)
        assertEquals(1, play.registered)

        u.restartAndInstall(); idle()
        assertEquals("primero se guarda, después se instala", listOf("guardar", "instalar"), log)
        assertTrue(play.fake.isInstallSplashScreenVisible)
    }

    @Test fun al_volver_a_abrir_sigue_una_descarga_a_medias() {
        val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(23, AppUpdateType.FLEXIBLE) }
        fake.startUpdateFlowForResult(fake.appUpdateInfo.result, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
        fake.userAcceptsUpdate(); fake.downloadStarts()
        val play = Play(fake); val u = controller(play)
        u.check(); idle(); u.check(); idle()
        assertEquals(Phase.DOWNLOADING, u.ui.value.phase)
        assertFalse(u.ui.value.offer)
        assertEquals("un solo listener", 1, play.activeListeners)
        fake.downloadCompletes(); idle()
        assertTrue(u.ui.value.restart)
    }

    @Test fun mas_tarde_no_repite_el_aviso_de_reiniciar_en_la_sesion() {
        val play = downloadedInPreviousSession(); val u = controller(play)
        u.check(); idle()
        u.postponeRestart(untilWorkoutEnds = false)
        u.check(); idle()
        assertFalse(u.ui.value.restart)
        assertEquals("sigue descargada para la próxima vez", Phase.DOWNLOADED, u.ui.value.phase)
        val next = controller(play); next.check(); idle()
        assertTrue("al volver a abrir Rupio se ofrece otra vez", next.ui.value.restart)
    }

    // ------------------------------------------------------------------ entreno en curso

    @Test fun durante_un_entreno_se_puede_dejar_para_despues() {
        val play = downloadedInPreviousSession(); val u = controller(play)
        u.onTrainingChanged(true)
        u.check(); idle()
        assertTrue(u.ui.value.restart)
        u.postponeRestart(untilWorkoutEnds = true)
        u.check(); idle()
        assertFalse("mientras sigue el entreno no se repite", u.ui.value.restart)
        u.onTrainingChanged(false)
        assertTrue("al terminar el entreno se ofrece otra vez", u.ui.value.restart)
        assertFalse("no se instaló nada solo", play.fake.isInstallSplashScreenVisible)
    }

    @Test fun si_no_se_pudo_guardar_no_se_reinicia() = runBlocking {
        val log = mutableListOf<String>()
        val play = downloadedInPreviousSession(log)
        val failing = controller(play, save = { log += "guardar"; false })
        failing.check(); idle()
        failing.restartAndInstall(); idle()
        assertEquals("no se llama a completeUpdate", listOf("guardar"), log)
        assertFalse(play.fake.isInstallSplashScreenVisible)
        assertTrue(failing.ui.value.restart)
        assertTrue(failing.ui.value.error!!.contains("no reiniciamos"))

        val crashing = controller(play, save = { throw IllegalStateException("disco lleno") })
        crashing.check(); idle()
        crashing.restartAndInstall(); idle()
        assertFalse(play.fake.isInstallSplashScreenVisible)
        assertNotNull(crashing.ui.value.error)
    }

    /** Lo que se tecleó hace menos de 350 ms (todavía en memoria) queda escrito antes de instalar. */
    @Test fun antes_de_reiniciar_se_guarda_la_serie_a_medio_escribir() = runBlocking {
        c.auth.useLocalMode("QA")
        repeat(200) { if (c.db.exercises().catalogCount() >= 262) return@repeat; Thread.sleep(50) }
        c.wipeLocalData()
        val w = c.workouts.startEmpty()
        c.workouts.addExercises(w, listOf(bench))
        val set = c.db.workouts().sets(w).first()
        val vm = ActiveWorkoutVM(c)
        vm.edit(set.id) { it.copy(weightKg = 82.5, reps = 7) }
        assertNull("todavía en espera (350 ms)", c.db.workouts().getSet(set.id)!!.weightKg)

        val log = mutableListOf<String>()
        val play = downloadedInPreviousSession(log)
        val u = controller(play, save = { c.unsaved.saveAll().also { log += "guardado=$it" } })
        u.onTrainingChanged(true)
        u.check(); idle()
        u.restartAndInstall(); idle()
        val saved = c.db.workouts().getSet(set.id)!!
        assertEquals(82.5, saved.weightKg!!, 0.0); assertEquals(7, saved.reps)
        assertEquals(listOf("guardado=true", "instalar"), log)
        assertNotNull("el entreno sigue activo para continuar al volver", c.db.workouts().get(w))
        c.workouts.discard(w)
    }

    // ------------------------------------------------------------------ fallos

    @Test fun si_la_consulta_falla_la_app_sigue_igual() {
        val errors = listOf(
            InstallException(InstallErrorCode.ERROR_API_NOT_AVAILABLE),   // Play sin la API
            InstallException(InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND),
            InstallException(InstallErrorCode.ERROR_APP_NOT_OWNED),       // instalada fuera de Play (APK, debug)
            InstallException(InstallErrorCode.ERROR_INTERNAL_ERROR),      // sin internet / error de Play
        )
        for (e in errors) {
            val play = available().apply { failQuery = e }
            val u = controller(play)
            u.check(); idle()
            assertEquals("error ${e.errorCode}", Ui(), u.ui.value)
            assertEquals(0, play.registered)
            // Se recupera en la siguiente consulta.
            play.failQuery = null
            u.check(); idle()
            assertTrue("error ${e.errorCode}: la siguiente consulta funciona", u.ui.value.offer)
        }
        val throwing = available().apply { throwQuery = IllegalStateException("Play no está") }
        controller(throwing).apply { check(); idle(); assertEquals(Ui(), ui.value) }
        assertTrue("sin mensajes de error al usuario", messages.isEmpty())
    }

    @Test fun sin_google_play_ni_respuesta_no_pasa_nada() = runBlocking {
        val none = controller(null)
        none.check(); none.accept(launcher); none.decline(); none.onFlowResult(Activity.RESULT_OK); none.restartAndInstall()
        assertEquals(Ui(), none.ui.value)
        assertTrue(messages.isEmpty())

        // Play nunca responde: no se apilan consultas; pasado el tiempo de espera se vuelve a preguntar.
        val play = available().apply { hangQuery = true }
        val u = controller(play)
        u.check(); u.check(); idle()
        assertEquals(1, play.queries)
        assertEquals(Ui(), u.ui.value)
        clock += AppUpdateController.QUERY_TIMEOUT_MS + 1
        play.hangQuery = false
        u.check(); idle()
        assertEquals(2, play.queries)
        assertTrue(u.ui.value.offer)
    }

    @Test fun si_play_no_puede_instalar_se_puede_reintentar() = runBlocking {
        val play = downloadedInPreviousSession()
        val failingInstall = object : AppUpdateManager by play {
            override fun completeUpdate(): Task<Void> = Tasks.forException(InstallException(InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED))
        }
        val u = controller(failingInstall)
        u.check(); idle()
        u.restartAndInstall(); idle()
        assertEquals(Phase.DOWNLOADED, u.ui.value.phase)
        assertTrue(u.ui.value.restart)
        assertTrue(u.ui.value.error!!.contains("No se pudo instalar"))
    }
}
