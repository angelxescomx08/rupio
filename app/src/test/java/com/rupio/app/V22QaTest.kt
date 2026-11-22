package com.rupio.app

import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.I18n
import com.rupio.app.data.auth.AuthException
import com.rupio.app.data.auth.AuthRepository
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.auth.NeonAuthClient
import com.rupio.app.data.prefs.SecureStore
import com.rupio.app.data.prefs.SettingsStore
import com.rupio.app.data.update.AppUpdateController
import com.rupio.app.ui.auth.RESEND_COOLDOWN_SEC
import com.rupio.app.ui.auth.resendWaitSec
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Base64

/** v2.2 · hallazgos de QA del envío del código y de la actualización de Play (cada prueba dice cuál cubre). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V22QaTest {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val bench = "press_de_banca_con_barra"

    @Before fun setUp() = runBlocking {
        I18n.set(AppLanguage.ES)
        c.auth.useLocalMode("QA")
        repeat(200) { if (c.db.exercises().catalogCount() >= 262) return@repeat; Thread.sleep(50) }
        c.wipeLocalData()
    }

    // ------------------------------------------------------------------ #1 «Reenviar código» al volver a abrir

    /** Neon Auth falso: [otpStatus] decide si el envío del código sale (200) o falla. */
    private class FakeAuth {
        var otpStatus = 200
        private fun jwt() = "h." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"u-ana","exp":${System.currentTimeMillis() / 1000 + 900}}""".toByteArray()) + ".s"
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            fun resp(code: Int, body: String, auth: Boolean = false) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .apply { if (auth) { header("set-auth-jwt", jwt()); header("Set-Cookie", "better-auth.session_token=tok; Path=/; HttpOnly") } }
                .body(body.toResponseBody("application/json".toMediaType())).build()
            when (req.url.encodedPath.substringAfter("/auth")) {
                "/sign-up/email" -> resp(200, """{"token":"t","user":{"id":"u-ana","name":"Ana","email":"ana@ejemplo.com","emailVerified":false}}""", auth = true)
                "/get-session" -> resp(200, """{"session":{"token":"t"},"user":{"id":"u-ana"}}""", auth = true)
                "/email-otp/send-verification-otp" -> if (otpStatus == 200) resp(200, """{"success":true}""") else resp(otpStatus, "{}")
                "/email-otp/verify-email" -> resp(200, """{"status":true,"user":{"id":"u-ana","emailVerified":true}}""", auth = true)
                else -> resp(404, "{}")
            }
        }
    }
    private val settings get() = SettingsStore(app, "https://auth.ejemplo/neondb/auth", "https://api.ejemplo/rest/v1")
    private fun repo(fake: FakeAuth, secure: SecureStore) = AuthRepository(secure, settings, NeonAuthClient(OkHttpClient.Builder().addInterceptor(fake.interceptor).build()))
    private fun reopen(fake: FakeAuth, secure: SecureStore): AuthState.SignedIn {
        val r = repo(fake, secure)
        repeat(50) { if (r.state.value is AuthState.SignedIn) return@repeat; Thread.sleep(20) }
        return r.state.value as AuthState.SignedIn
    }

    @Test fun h1_primer_envio_fallido_sigue_asi_al_volver_a_abrir() = runBlocking {
        val fake = FakeAuth().apply { otpStatus = 503 }; val secure = SecureStore(app)
        repo(fake, secure).signUp("Ana", "ana@ejemplo.com", "secreta123")
        val s = reopen(fake, secure)
        assertTrue(s.needsVerification)
        assertTrue("se recuerda que el envío falló", s.verificationSendFailed)
        assertEquals("se puede reenviar ya", 0, resendWaitSec(s.verificationSendFailed, s.verificationSentAt))
    }

    @Test fun h1_la_espera_se_cuenta_desde_el_ultimo_envio_bueno() = runBlocking {
        val fake = FakeAuth(); val secure = SecureStore(app)
        val before = System.currentTimeMillis()
        repo(fake, secure).signUp("Ana", "ana@ejemplo.com", "secreta123")
        val s = reopen(fake, secure)
        assertFalse(s.verificationSendFailed)
        assertTrue("se guarda la hora del envío", s.verificationSentAt >= before)
        assertTrue(resendWaitSec(false, s.verificationSentAt) in 55..RESEND_COOLDOWN_SEC)
        assertEquals("horas después ya no hay que esperar", 0, resendWaitSec(false, s.verificationSentAt, now = s.verificationSentAt + 2 * 3_600_000L))
        assertEquals(30, resendWaitSec(false, 1_000_000L, now = 1_030_000L))
        assertEquals("reloj del teléfono hacia atrás: no más de 60 s", RESEND_COOLDOWN_SEC, resendWaitSec(false, 2_000_000L, now = 1_000_000L))
        assertEquals("sin hora guardada (versiones anteriores): ya", 0, resendWaitSec(false, 0))
    }

    @Test fun h1_reenvio_fallido_y_luego_bueno_se_recuerdan() = runBlocking {
        val fake = FakeAuth(); val secure = SecureStore(app)
        val auth = repo(fake, secure)
        auth.signUp("Ana", "ana@ejemplo.com", "secreta123")
        fake.otpStatus = 429
        assertThrows(AuthException::class.java) { runBlocking { auth.sendVerificationCode() } }
        assertTrue((auth.state.value as AuthState.SignedIn).verificationSendFailed)
        assertTrue("al volver a abrir", reopen(fake, secure).verificationSendFailed)

        fake.otpStatus = 200
        auth.sendVerificationCode()
        val s = reopen(fake, secure)
        assertFalse(s.verificationSendFailed); assertTrue(s.verificationSentAt > 0)

        auth.verifyEmail("654321")
        assertNull("al confirmar se borra", secure.get("verify_sent_at")); assertNull(secure.get("verify_send_failed"))
    }

    // ------------------------------------------------------------------ actualización: utilidades

    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) {}
        override fun unregister() {}
        override val contract: ActivityResultContract<IntentSenderRequest, *> get() = ActivityResultContracts.StartIntentSenderForResult()
    }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun downloaded() = FakeAppUpdateManager(app).apply {
        setUpdateAvailable(24, AppUpdateType.FLEXIBLE)
        startUpdateFlowForResult(appUpdateInfo.result, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
        userAcceptsUpdate(); downloadStarts(); downloadCompletes()
    }

    // ------------------------------------------------------------------ #4 «Después del entreno» al recrearse la pantalla

    @Test fun h4_solo_terminar_el_entreno_vuelve_a_ofrecer_reiniciar() {
        val u = AppUpdateController(downloaded(), saveProgress = { true })
        u.onTrainingChanged(false)           // primer valor antes de entrenar: no cambia nada
        u.onTrainingChanged(true)
        u.check(); idle()
        u.postponeRestart(untilWorkoutEnds = true)
        u.onTrainingChanged(true)            // la pantalla se recrea y vuelve a leer «hay entreno»
        u.check(); idle()
        assertFalse(u.ui.value.restart)
        u.onTrainingChanged(false)
        assertTrue("al terminar el entreno", u.ui.value.restart)
    }

    // ------------------------------------------------------------------ #5 avisos que nadie recibe

    @Test fun h5_el_aviso_de_descarga_espera_a_que_alguien_lo_muestre() {
        val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(24, AppUpdateType.FLEXIBLE) }
        val u = AppUpdateController(fake, saveProgress = { true })
        u.check(); idle(); u.accept(launcher)
        fake.userAcceptsUpdate(); u.onFlowResult(android.app.Activity.RESULT_OK); idle()
        assertTrue(u.message.value!!.contains("Descargando la actualización"))
        u.messageShown()
        assertNull(u.message.value)
        fake.downloadStarts(); fake.downloadFails(); idle()
        assertTrue("también el de error", u.message.value!!.contains("No se pudo descargar"))
    }

    // ------------------------------------------------------------------ #6 «Más tarde» y volver a Rupio

    @Test fun h6_mas_tarde_vuelve_a_preguntarse_al_regresar_a_rupio() {
        val u = AppUpdateController(downloaded(), saveProgress = { true })
        u.check(); idle()
        u.postponeRestart(untilWorkoutEnds = false)
        u.check(); idle()
        assertFalse("sin salir de la app no se repite", u.ui.value.restart)
        u.onAppBackgrounded()
        u.check(); idle()
        assertTrue("al volver a primer plano se pregunta otra vez", u.ui.value.restart)
    }

    @Test fun h6_despues_del_entreno_se_respeta_aunque_se_bloquee_el_telefono() {
        val u = AppUpdateController(downloaded(), saveProgress = { true })
        u.onTrainingChanged(true)
        u.check(); idle()
        u.postponeRestart(untilWorkoutEnds = true)
        u.onAppBackgrounded()   // bloquear el teléfono entre series
        u.check(); idle()
        assertFalse(u.ui.value.restart)
    }

    // ------------------------------------------------------------------ #7 lo escrito en «Terminar»

    @Test fun h7_borrador_de_terminar_se_guarda_en_el_entreno_en_curso() = runBlocking {
        val w = c.workouts.startEmpty()
        c.workouts.saveFinishDraft(w, "Pierna pesada", "Me sentí fuerte")
        val saved = c.db.workouts().get(w)!!
        assertEquals("Pierna pesada", saved.name); assertEquals("Me sentí fuerte", saved.notes)
        assertNull("sigue en curso", saved.endedAt)
        c.workouts.saveFinishDraft(w, "  ", "")
        assertEquals("un nombre vacío no borra el que había", "Pierna pesada", c.db.workouts().get(w)!!.name)
        c.workouts.discard(w)
    }

    // ------------------------------------------------------------------ #8 volver directo al entreno

    @Test fun h8_tras_reiniciar_para_actualizar_se_abre_el_entreno_una_sola_vez() {
        assertFalse("sin reinicio", c.consumeRestartForUpdate())
        c.markRestartForUpdate()
        assertTrue(c.consumeRestartForUpdate())
        assertFalse("solo una vez", c.consumeRestartForUpdate())
        c.markRestartForUpdate()
        assertFalse("si pasaron más de 10 min no cuenta", c.consumeRestartForUpdate(now = System.currentTimeMillis() + RESTART_RESUME_MS + 1))
    }

    // ------------------------------------------------------------------ #9 no saltar solo al entreno si la instalación falla

    @Test fun h9_si_play_no_puede_instalar_se_borra_la_nota_de_abrir_el_entreno() = runBlocking {
        val w = c.workouts.startEmpty()
        val play = downloaded()
        val refuses = object : com.google.android.play.core.appupdate.AppUpdateManager by play {
            override fun completeUpdate(): com.google.android.gms.tasks.Task<Void> =
                com.google.android.gms.tasks.Tasks.forException(com.google.android.play.core.install.InstallException(
                    com.google.android.play.core.install.model.InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED))
        }
        val u = AppUpdateController(refuses, saveProgress = { c.saveBeforeRestart() }, onInstallFailed = c::clearRestartForUpdate)
        u.check(); idle()
        u.restartAndInstall(); idle()
        assertTrue("el aviso sigue para reintentar", u.ui.value.restart)
        assertFalse("ya no queda la nota", c.consumeRestartForUpdate())
        c.workouts.discard(w)
    }

    @Test fun h9_si_la_instalacion_falla_despues_tambien_se_borra() = runBlocking {
        var failed = 0
        val fake = downloaded()
        val u = AppUpdateController(fake, saveProgress = { c.saveBeforeRestart() }, onInstallFailed = { failed++; c.clearRestartForUpdate() })
        u.check(); idle()
        u.restartAndInstall(); idle()
        assertTrue(fake.isInstallSplashScreenVisible)
        fake.installFails(); idle()
        assertEquals(1, failed)
        assertFalse(c.consumeRestartForUpdate())
    }

    /** La nota solo cuenta al arrancar Rupio: girar o cambiar el modo oscuro (MainShell se vuelve a dibujar) no salta al entreno. */
    @Test fun h9_en_la_misma_sesion_la_nota_no_manda_al_entreno() {
        c.markRestartForUpdate()             // se tocó «Reiniciar y actualizar», pero Rupio no se reinició
        assertFalse("primer dibujo de MainShell", c.takeOpenWorkoutAfterUpdate())
        assertFalse("después de girar", c.takeOpenWorkoutAfterUpdate())
    }

    @Test fun h8_guardar_antes_de_instalar_marca_el_reinicio() = runBlocking {
        val w = c.workouts.startEmpty()
        val u = AppUpdateController(downloaded(), saveProgress = { c.saveBeforeRestart() })
        u.check(); idle()
        u.restartAndInstall(); idle()
        assertTrue(c.consumeRestartForUpdate())
        c.workouts.discard(w)
    }
}
