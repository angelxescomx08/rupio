package com.rupio.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onLast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.rupio.app.data.billing.SupportState
import com.rupio.app.domain.Accent
import com.rupio.app.domain.SupportTier
import com.rupio.app.ui.auth.SignOutOverlay
import com.rupio.app.ui.components.LoadingButton
import com.rupio.app.ui.components.RupioSpinner
import com.rupio.app.ui.profile.ProfileScreen
import com.rupio.app.ui.support.SupportPromptCard
import com.rupio.app.ui.support.SupportScreen
import com.rupio.app.ui.support.ThanksDialog
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.RupioTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** Capturas de lo nuevo en la v1.4: apoyo (mensaje, niveles, gracias, recompensas), carga y cierre de sesión. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class SupportScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container

    private fun settle(ms: Long = 800) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("screenshots/$name.png") }
    /** Diálogos de pantalla completa viven en otra ventana: se captura toda la pantalla. */
    private fun screen(name: String, ms: Long = 800) { settle(ms); captureScreenRoboImage("screenshots/$name.png") }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }

    @After fun reset() { c.support.debugReset(); c.support.setAccent(Accent.AZUL) }

    @Test fun a_loaders_antes_y_despues() {
        app {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Antes: botón apagado (gris) con el indicador azul marino", style = MaterialTheme.typography.titleSmall)
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text("Ahora: se queda azul, con indicador visible y qué está pasando", style = MaterialTheme.typography.titleSmall)
                LoadingButton("Iniciar sesión", loading = true, onClick = {}, modifier = Modifier.fillMaxWidth().height(54.dp), loadingText = "Entrando…")
                Spacer(Modifier.height(8.dp))
                Text("Indicadores en la app (con riel para que siempre se vea la forma)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    RupioSpinner(size = 24.dp); RupioSpinner(size = 32.dp); RupioSpinner(size = 32.dp, color = RupioColors.Rest)
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1100)
        compose.onRoot().captureRoboImage("screenshots/48_loaders_antes_despues.png")
    }

    @Test fun b_mensaje_al_terminar() {
        // v1.6: ya no es un diálogo a pantalla completa, sino una tarjeta dentro del resumen del entreno (se puede ignorar).
        app { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) { SupportPromptCard(SupportState(), workouts = 25, lifted = "48,300 lb", onSeeOptions = {}, onNotNow = {}) } }
        screen("49_mensaje_apoyo")
    }

    @Test fun c_pantalla_apoya_rupio() {
        c.support.debugReset()
        app { SupportScreen(rememberNavController()) }
        shot("50_apoya_rupio")
    }

    @Test fun d_gracias_fan() {
        c.support.debugReset()
        c.support.debugGrant(SupportTier.FAN)
        compose.mainClock.autoAdvance = false
        app { ThanksDialog(SupportTier.FAN, c.support.state.value, onAccent = {}, onGoldIcon = {}, onClose = {}) }
        repeat(12) { compose.mainClock.advanceTimeBy(100); ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) }
        captureScreenRoboImage("screenshots/51_gracias_fan.png")
    }

    @Test fun e_recompensas_licuado_rosa() {
        c.support.debugReset()
        c.support.debugGrant(SupportTier.LICUADO)
        c.support.setAccent(Accent.ROSA)
        app { SupportScreen(rememberNavController()) }
        shot("52_recompensas_rosa")
    }

    @Test fun f_perfil_con_insignia_fan_dorado() = runBlocking {
        c.auth.useLocalMode("José Ángel Hernández")
        c.support.debugReset()
        c.support.debugGrant(SupportTier.FAN)
        c.support.setAccent(Accent.DORADO)
        app { ProfileScreen(rememberNavController()) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Tus recompensas").fetchSemanticsNodes().isNotEmpty() }
        shot("53_perfil_fan_dorado")
    }

    @Test fun g_perfil_sin_apoyo() = runBlocking {
        c.auth.useLocalMode("José Ángel Hernández")
        c.support.debugReset()
        app { ProfileScreen(rememberNavController()) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Apoya Rupio").fetchSemanticsNodes().isNotEmpty() }
        shot("54_perfil_apoya")
    }

    @Test fun h_cerrando_sesion() {
        var s = SignOutState(active = true, step = SignOutStep.UPLOAD, pendingAtStart = 12)
        app { SignOutOverlay(s, {}, {}, {}) }
        compose.mainClock.autoAdvance = false; compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("screenshots/55_cerrando_sesion_subiendo.png")
    }

    @Test fun i_cerrando_sesion_borrando() {
        app { SignOutOverlay(SignOutState(active = true, step = SignOutStep.WIPE, pendingAtStart = 12), {}, {}, {}) }
        compose.mainClock.autoAdvance = false; compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("screenshots/56_cerrando_sesion_borrando.png")
    }

    @Test fun j_sin_internet() {
        app { SignOutOverlay(SignOutState(active = true, step = SignOutStep.UPLOAD, pendingAtStart = 12, pendingLeft = 12, uploadFailed = true), {}, {}, {}) }
        shot("57_cerrar_sesion_sin_internet")
    }

    @Test fun k_sesion_cerrada() {
        app { SignOutOverlay(SignOutState(active = true, step = SignOutStep.DONE), {}, {}, {}) }
        shot("58_sesion_cerrada")
    }

    // ------------------------------------------------------------------ flujo real

    private suspend fun seedFinished(n: Int): String {
        val uid = c.auth.currentUserId!!
        var last = ""
        repeat(n) { k ->
            val t = System.currentTimeMillis() - (n - k) * 86_400_000L
            last = com.rupio.app.core.Ids.uuidV7(t)
            c.db.workouts().upsertWorkout(com.rupio.app.data.local.WorkoutEntity(last, uid, null, "Pierna $k", t, t + 3_600_000, "America/Mexico_City",
                (t / 86_400_000L).toInt(), 3600, totalVolumeKg = 5_000.0, updatedAt = t))
        }
        return last
    }

    @Test fun l_mensaje_sale_al_terminar_el_tercer_entreno_y_no_se_repite() = runBlocking {
        c.auth.useLocalMode("José Ángel Hernández")
        c.support.debugReset()
        val id = seedFinished(3)
        var celebrate by androidx.compose.runtime.mutableIntStateOf(1)
        app { com.rupio.app.ui.history.WorkoutDetailScreen(rememberNavController(), id, celebrate) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Hola, soy Ángel", substring = true).fetchSemanticsNodes().isNotEmpty() }
        screen("59_mensaje_tras_terminar")
        // Habla de SUS datos: 3 entrenos y 15,000 kg ≈ 33,069 lb.
        compose.onAllNodesWithText("3 entrenamientos", substring = true).fetchSemanticsNodes().let { org.junit.Assert.assertTrue(it.isNotEmpty()) }
        // v1.6: es una tarjeta dentro del resumen; "Ahora no" la quita (y cuenta para dejar de mostrarla tras 3).
        compose.onNode(androidx.compose.ui.test.hasText("Ahora no") and androidx.compose.ui.test.hasClickAction()).performScrollTo().performClick(); settle()
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("Hola, soy Ángel", substring = true).fetchSemanticsNodes().isEmpty())
        org.junit.Assert.assertEquals(1, c.support.dismissals)
        // Volver a abrir el mismo entreno (o girar el teléfono) no lo muestra otra vez.
        celebrate = 0; settle(); celebrate = 1; settle(2_000)
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("Hola, soy Ángel", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun m_no_sale_en_el_segundo_entreno_ni_a_quien_apoya() = runBlocking {
        c.auth.useLocalMode("José Ángel Hernández")
        c.support.debugReset()
        val id2 = seedFinished(2)
        app { com.rupio.app.ui.history.WorkoutDetailScreen(rememberNavController(), id2, 1) }
        settle(2_500)
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("Hola, soy Ángel", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun n_quien_apoya_nunca_ve_el_mensaje() = runBlocking {
        c.auth.useLocalMode("José Ángel Hernández")
        c.support.debugReset(); c.support.debugGrant(SupportTier.CAFE)
        val id = seedFinished(5)
        app { com.rupio.app.ui.history.WorkoutDetailScreen(rememberNavController(), id, 1) }
        settle(2_500)
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("Hola, soy Ángel", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun o_boton_cerrar_sesion_abre_los_pasos() = runBlocking {
        c.auth.useLocalMode("José Ángel Hernández")
        app { ProfileScreen(rememberNavController()) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Cerrar sesión").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(androidx.compose.ui.test.hasText("Cerrar sesión") and androidx.compose.ui.test.hasClickAction()).performScrollTo().performClick(); settle()
        compose.onAllNodesWithText("Cerrar sesión").fetchSemanticsNodes().size.let { org.junit.Assert.assertTrue(it >= 2) }   // título + botón del diálogo
        compose.onAllNodes(androidx.compose.ui.test.hasText("Cerrar sesión") and androidx.compose.ui.test.hasClickAction()).onLast().performClick()
        compose.waitUntil(5_000) { c.signOut.state.value.active || c.auth.state.value == com.rupio.app.data.auth.AuthState.SignedOut }
    }
}
