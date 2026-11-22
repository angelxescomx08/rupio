package com.rupio.app

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.I18n
import com.rupio.app.data.update.AppUpdateController
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.update.AppUpdateHost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** v2.2: los avisos de la actualización flexible (textos, botones y que se cierren sin bloquear la app). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rMX-w393dp-h852dp-night-xhdpi")
class AppUpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container

    private fun settle() {
        repeat(4) { ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
    }
    private fun has(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(t: String) = compose.waitUntil(10_000) { has(t) }
    private fun host(u: AppUpdateController, onUpdate: () -> Unit = {}) =
        compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { AppUpdateHost(onUpdate, u) } } }

    private fun setUp() = runBlocking {
        I18n.set(AppLanguage.ES)
        c.auth.useLocalMode("QA")
        repeat(200) { if (c.db.exercises().catalogCount() >= 262) return@repeat; Thread.sleep(50) }
        c.wipeLocalData()
    }

    @Test fun aviso_de_version_nueva_actualizar_y_ahora_no() {
        setUp()
        val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(23, AppUpdateType.FLEXIBLE) }
        val u = AppUpdateController(fake, saveProgress = { true })
        var updates = 0
        host(u) { updates++ }
        u.check(); shadowOf(Looper.getMainLooper()).idle()
        waitText("Hay una versión nueva de Rupio")
        compose.onNodeWithText("Se descarga en segundo plano", substring = true).assertExists()
        settle(); captureScreenRoboImage("screenshots/90_actualizacion_disponible.png")
        compose.onNodeWithText("Actualizar").assertHasClickAction().performClick(); settle()
        assertEquals("«Actualizar» abre el flujo de Play", 1, updates)
    }

    /** «Ahora no» cierra el aviso y no vuelve en la sesión. */
    @Test fun ahora_no_cierra_el_aviso() {
        setUp()
        val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(23, AppUpdateType.FLEXIBLE) }
        val u = AppUpdateController(fake, saveProgress = { true })
        host(u)
        u.check(); shadowOf(Looper.getMainLooper()).idle()
        waitText("Ahora no")
        compose.onNodeWithText("Ahora no").performClick(); settle()
        assertFalse(has("Hay una versión nueva"))
        u.check(); shadowOf(Looper.getMainLooper()).idle(); settle()
        assertFalse("no vuelve en la misma sesión", has("Hay una versión nueva"))
    }

    @Test fun lista_para_instalar_con_entreno_en_curso() = runBlocking {
        setUp()
        val fake = FakeAppUpdateManager(app).apply { setUpdateAvailable(23, AppUpdateType.FLEXIBLE) }
        val launcher = compose.activity.activityResultRegistry.register("play-update", ActivityResultContracts.StartIntentSenderForResult()) { }
        fake.startUpdateFlowForResult(fake.appUpdateInfo.result, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
        fake.userAcceptsUpdate(); fake.downloadStarts(); fake.downloadCompletes()
        val w = c.workouts.startEmpty()
        val u = AppUpdateController(fake, saveProgress = { c.unsaved.saveAll() })
        host(u)
        u.check(); shadowOf(Looper.getMainLooper()).idle()
        waitText("La actualización está lista")
        waitText("Tienes un entreno en curso")
        compose.onNodeWithText("Reiniciar y actualizar").assertExists()
        settle(); captureScreenRoboImage("screenshots/91_actualizacion_lista_entreno.png")
        compose.onNodeWithText("Después del entreno").performClick(); settle()
        assertFalse("se deja para después", has("La actualización está lista"))

        // Al terminar (o descartar) el entreno se vuelve a ofrecer, ya sin entreno en curso.
        c.workouts.discard(w)
        waitText("La actualización está lista")
        waitText("Más tarde")
        assertFalse(has("Tienes un entreno en curso"))
        compose.onNodeWithText("Reiniciar y actualizar").performClick(); settle()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Play instala", fake.isInstallSplashScreenVisible)
    }
}
