package com.rupio.app

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.I18n
import com.rupio.app.data.update.AppUpdateController
import com.rupio.app.ui.auth.VerifyEmailScreen
import com.rupio.app.ui.auth.earlierSendFailedMessage
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.update.AppUpdateHost
import com.rupio.app.ui.update.SaveBeforeRestart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** v2.2 · hallazgos de QA en pantalla: «Confirma tu correo» y los avisos de la actualización. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rMX-w393dp-h852dp-night-xhdpi")
class V22QaUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private var server: LocalAuth? = null

    @After fun down() { server?.stop() }

    private fun settle() {
        repeat(4) { ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
    }
    private fun has(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(t: String) = compose.waitUntil(10_000) { has(t) }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun themed(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }

    private fun setUp() = runBlocking {
        // Al entrar con una cuenta, la app programa la sincronización (WorkManager).
        runCatching { androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().setExecutor { it.run() }.build()) }
        I18n.set(AppLanguage.ES)
        c.auth.useLocalMode("QA")
        repeat(200) { if (c.db.exercises().catalogCount() >= 262) return@repeat; Thread.sleep(50) }
        c.wipeLocalData()
    }

    /** Neon Auth local (127.0.0.1): ningún correo sale de la prueba. */
    private class LocalAuth {
        val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val base get() = "http://127.0.0.1:${socket.localPort}"
        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val s = runCatching { socket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { s.use { handle(it) } }
                }
            }
        }
        private fun jwt() = "h." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"u-ana","exp":${System.currentTimeMillis() / 1000 + 900}}""".toByteArray()) + ".s"
        private fun handle(s: Socket) {
            val input = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val path = input.readLine()?.split(" ")?.getOrNull(1) ?: return
            var len = 0
            while (true) { val h = input.readLine() ?: break; if (h.isEmpty()) break; if (h.lowercase().startsWith("content-length:")) len = h.substringAfter(':').trim().toInt() }
            repeat(len) { input.read() }
            paths += path
            val auth = path.endsWith("/sign-up/email") || path.endsWith("/get-session")
            val body = when {
                path.endsWith("/sign-up/email") -> """{"token":"t","user":{"id":"u-ana","name":"Ana","email":"ana@ejemplo.com","emailVerified":false}}"""
                path.endsWith("/get-session") -> """{"session":{"token":"t"},"user":{"id":"u-ana"}}"""
                path.endsWith("/send-verification-otp") -> """{"success":true}"""
                else -> "{}"
            }.toByteArray()
            val head = "HTTP/1.1 200 X\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n" +
                (if (auth) "set-auth-jwt: ${jwt()}\r\nSet-Cookie: better-auth.session_token=tok; Path=/; HttpOnly\r\n" else "") + "\r\n"
            s.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
        }
        fun stop() = socket.close()
    }

    // ------------------------------------------------------------------ #1

    @Test fun h1_al_volver_a_abrir_tras_un_envio_fallido_se_ve_el_error_y_se_puede_reenviar() {
        setUp()
        themed { VerifyEmailScreen("ana@ejemplo.com", initialSendError = earlierSendFailedMessage(), sentAt = 0) }
        waitText("No pudimos enviarte el código")
        compose.onNode(hasText("Reenviar código") and hasClickAction()).assertIsEnabled()
        assertFalse(has("Reenviar código en"))
    }

    @Test fun h1_horas_despues_del_ultimo_envio_no_hay_que_esperar() {
        setUp()
        themed { VerifyEmailScreen("ana@ejemplo.com", sentAt = System.currentTimeMillis() - 2 * 3_600_000L) }
        waitText("Confirma tu correo")
        compose.onNode(hasText("Reenviar código") and hasClickAction()).assertIsEnabled()
        assertFalse(has("Reenviar código en"))
    }

    @Test fun h1_a_los_45_s_del_envio_faltan_unos_15() {
        setUp()
        themed { VerifyEmailScreen("ana@ejemplo.com", sentAt = System.currentTimeMillis() - 45_000L) }
        waitText("Reenviar código en 1")
        compose.onNode(hasText("Reenviar código en", substring = true)).assertIsNotEnabled()
    }

    // ------------------------------------------------------------------ #2

    @Test fun h2_te_enviamos_un_codigo_nuevo_sigue_al_girar_el_telefono() = runBlocking {
        setUp()
        val local = LocalAuth().also { server = it }
        c.settings.setServer("${local.base}/neondb/auth", "${local.base}/rest/v1")
        assertTrue("solo servidor local", c.settings.current().authUrl.startsWith("http://127.0.0.1"))
        c.auth.signUp("Ana", "ana@ejemplo.com", "secreta123")
        val tester = StateRestorationTester(compose)
        tester.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { VerifyEmailScreen("ana@ejemplo.com", sentAt = 0) } } }
        waitText("Confirma tu correo")
        compose.onNode(hasText("Reenviar código") and hasClickAction()).performClick()
        waitText("Te enviamos un código nuevo")
        tester.emulateSavedInstanceStateRestore(); settle()
        assertTrue("el mensaje sigue después de girar", has("Te enviamos un código nuevo"))
        assertTrue(has("Reenviar código en"))
        assertEquals(2, local.paths.count { it.endsWith("/send-verification-otp") })
    }

    // ------------------------------------------------------------------ actualización

    private fun downloaded(): FakeAppUpdateManager {
        val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(24, AppUpdateType.FLEXIBLE) }
        val launcher = compose.activity.activityResultRegistry.register("play-update", ActivityResultContracts.StartIntentSenderForResult()) { }
        fake.startUpdateFlowForResult(fake.appUpdateInfo.result, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
        fake.userAcceptsUpdate(); fake.downloadStarts(); fake.downloadCompletes()
        return fake
    }

    /** #4: «Después del entreno» y luego la pantalla se recrea (girar, modo oscuro, letra, idioma). */
    @Test fun h4_despues_del_entreno_se_respeta_al_recrear_la_pantalla() = runBlocking {
        setUp()
        val w = c.workouts.startEmpty()
        val u = AppUpdateController(downloaded(), saveProgress = { true })
        var shown by mutableStateOf(true)
        themed { if (shown) AppUpdateHost({}, u) }
        u.check(); idle()
        waitText("Después del entreno")
        compose.onNodeWithText("Después del entreno").performClick(); settle()
        assertFalse(has("La actualización está lista"))

        shown = false; settle()
        shown = true; settle()
        u.check(); idle(); settle()
        assertFalse("no vuelve a media serie", has("La actualización está lista"))

        c.workouts.discard(w)
        waitText("La actualización está lista")
        assertTrue(has("Más tarde"))
    }

    /** #5: fuera de las pestañas (inicio de sesión, confirmar correo, bienvenida) nadie recibe c.notices. */
    @Test fun h5_descargando_se_ve_aunque_no_este_la_pantalla_principal() {
        setUp()
        val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(24, AppUpdateType.FLEXIBLE) }
        val u = AppUpdateController(fake, saveProgress = { true })
        themed { AppUpdateHost({}, u) }
        assertEquals("nadie escucha c.notices", 0, c.notices.subscriptionCount.value)
        u.check(); idle()
        u.accept(compose.activity.activityResultRegistry.register("play-update", ActivityResultContracts.StartIntentSenderForResult()) { })
        fake.userAcceptsUpdate(); u.onFlowResult(android.app.Activity.RESULT_OK)
        waitText("Descargando la actualización")
        assertNull("ya se mostró", u.message.value)
    }

    @Test fun h5_con_la_pantalla_principal_va_con_los_demas_avisos() {
        setUp()
        val received = mutableListOf<String>()
        val shell = CoroutineScope(Dispatchers.Main.immediate)
        shell.launch { c.notices.collect { received += it.text } }   // como MainShell
        try {
            val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(24, AppUpdateType.FLEXIBLE) }
            val u = AppUpdateController(fake, saveProgress = { true })
            themed { AppUpdateHost({}, u) }
            u.check(); idle()
            u.accept(compose.activity.activityResultRegistry.register("play-update", ActivityResultContracts.StartIntentSenderForResult()) { })
            fake.userAcceptsUpdate(); u.onFlowResult(android.app.Activity.RESULT_OK)
            compose.waitUntil(10_000) { received.isNotEmpty() }
            assertTrue(received.single().contains("Descargando la actualización"))
            settle()
            assertFalse("no sale dos veces", has("Descargando la actualización"))
        } finally { shell.cancel() }
    }

    /**
     * #7: lo escrito en un diálogo del entreno sin tocar «Guardar» se guarda antes de reiniciar. Se prueba
     * SaveBeforeRestart, que usan «Notas», el nombre del entreno y «Terminar» (el diálogo de nombre pide el teclado y
     * en Robolectric Compose no queda quieto ni sin el cambio).
     */
    @Test fun h7_lo_escrito_sin_guardar_se_escribe_antes_de_reiniciar() = runBlocking {
        setUp()
        val w = c.workouts.startEmpty()
        var open by mutableStateOf(true)
        var typed by mutableStateOf("Pier")
        themed { if (open) SaveBeforeRestart { c.workouts.renameActive(w, typed) } }
        settle()
        typed = "Pierna pesada"; settle()
        assertTrue(c.saveBeforeRestart())
        assertEquals("se guarda lo último escrito", "Pierna pesada", c.db.workouts().get(w)!!.name)

        open = false; settle()
        c.workouts.renameActive(w, "Otro")
        assertTrue(c.saveBeforeRestart())
        assertEquals("cerrado el diálogo ya no escribe nada", "Otro", c.db.workouts().get(w)!!.name)
        assertTrue(c.consumeRestartForUpdate())
        c.workouts.discard(w)
    }
}
