package com.rupio.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.ui.profile.ProfileScreen
import com.rupio.app.ui.theme.RupioTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** v1.9: imágenes de RepDB (docs/IMAGENES_REPDB.md): el crédito que pide la licencia y los ejercicios sin imagen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class ImagenesRepDBTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container

    private fun settle(ms: Long = 800) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    private suspend fun setup() {
        runCatching { androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().setExecutor { it.run() }.build()) }
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("José Ángel Hernández")
        c.wipeLocalData()
    }

    /** La licencia gratuita pide un enlace visible "Exercise data by RepDB (repdb.co)" en créditos. */
    @Test fun perfil_muestra_credito_de_repdb_con_enlace() = runBlocking {
        setup()
        app { ProfileScreen(rememberNavController()) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Exercise data by RepDB", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val credit = compose.onNode(hasText("Exercise data by RepDB (repdb.co)", substring = true) and hasClickAction())
        credit.performScrollTo().assertIsDisplayed()
        settle(); compose.onRoot().captureRoboImage("screenshots/110_perfil_credito_repdb.png")
    }

    /** Sin equivalente en RepDB → sin imagen (la app pone el ícono de pesa); con equivalente → ruta a assets/ex. */
    @Test fun catalogo_con_y_sin_imagen() = runBlocking {
        setup()
        val all = c.db.exercises().observeAll().first().associateBy { it.id }
        assertNull(all["press_inclinado_agarre_martillo"]!!.image0)
        val banca = all["press_de_banca_con_barra"]!!
        assertEquals("file:///android_asset/ex/Barbell_Bench_Press_-_Medium_Grip__0.webp", banca.image0)
        assertNotNull(banca.image1)
        assertTrue(all.values.count { !it.isCustom && it.image0 != null } >= 220)
    }
}
