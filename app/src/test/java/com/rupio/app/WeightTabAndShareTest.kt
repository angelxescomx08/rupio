package com.rupio.app

import android.content.Intent
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
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.rupio.app.core.Dates
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.ui.components.ShareApp
import com.rupio.app.ui.profile.ProfileScreen
import com.rupio.app.ui.theme.RupioTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** v1.5: Peso como pestaña propia, peso corporal en kg (con cambio a lb) y compartir la app. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class WeightTabAndShareTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container

    private fun settle(ms: Long = 800) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("screenshots/$name.png") }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }

    private suspend fun setup(withWeights: Boolean = true) {
        // En el teléfono WorkManager arranca solo (androidx.startup); en Robolectric hay que iniciarlo a mano
        // porque guardar un peso pide una sincronización.
        runCatching { androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().setExecutor { it.run() }.build()) }
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("José Ángel Hernández")
        c.settings.setBodyUnit(WeightUnit.KG); c.settings.setUnit(WeightUnit.LB)   // DataStore vive entre pruebas
        c.settings.setHealthConsent(System.currentTimeMillis())                    // v1.7: ya dio su permiso (ver V17UiTest)
        if (withWeights) {
            val today = Dates.today(ZoneId.of("America/Mexico_City"))
            for (i in 1 until 90) {      // hasta ayer: "Registrar peso de hoy" sigue pendiente
                if (i % 4 == 2) continue
                val noise = kotlin.math.sin(i * 2.1) * 0.5
                c.bodyWeight.log(today.minusDays(i.toLong()), 78.0 + i * 0.04 + noise)
            }
        }
    }

    @Test fun ajustes_peso_corporal_en_kg_por_defecto_y_aparte_de_las_pesas() = runBlocking {
        assertEquals("sin nada guardado: kg", WeightUnit.KG, com.rupio.app.data.prefs.Settings().bodyUnit)
        c.settings.setBodyUnit(WeightUnit.KG)
        val s = c.settings.settings.first()
        assertEquals("las pesas siguen en lb por defecto", WeightUnit.LB, s.unit)
        assertEquals("el peso corporal, en kg", WeightUnit.KG, s.bodyUnit)
        c.settings.setBodyUnit(WeightUnit.LB)
        assertEquals(WeightUnit.LB, c.settings.settings.first().bodyUnit)
        assertEquals("cambiar una no cambia la otra", WeightUnit.LB, c.settings.settings.first().unit)
        c.settings.setUnit(WeightUnit.KG)
        assertEquals(WeightUnit.LB, c.settings.settings.first().bodyUnit)
        c.settings.setBodyUnit(WeightUnit.KG); c.settings.setUnit(WeightUnit.LB)
        Unit
    }

    @Test fun barra_tiene_peso_y_ya_no_aprende() = runBlocking {
        setup()
        app { MainShell() }
        waitText("Entrenar")
        listOf("Entrenar", "Historial", "Progreso", "Peso", "Perfil").forEach { compose.onAllNodesWithText(it).assertAny(hasText(it)) }
        assertTrue(compose.onAllNodesWithText("Aprende").fetchSemanticsNodes().isEmpty())
        compose.onAllNodesWithText("Peso").onLast().performClick()
        waitText("Tu peso (tendencia)")
        assertTrue("es pestaña: sin flecha de atrás", compose.onAllNodesWithContentDescription("Atrás").fetchSemanticsNodes().isEmpty())
        waitText("kg")
        shot("60_peso_pestana_kg")
    }

    @Test fun registrar_en_kg_y_cambiar_a_lb_convierte_y_se_recuerda() = runBlocking {
        setup()
        app { MainShell() }
        waitText("Entrenar")
        compose.onAllNodesWithText("Peso").onLast().performClick()
        waitText("Registrar peso de hoy")
        compose.onNodeWithText("Registrar peso de hoy").performClick(); settle()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        captureScreenRoboImage("screenshots/61_registrar_peso_kg.png")
        val field = compose.onNode(hasSetTextAction())
        val kgText = field.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        val kg = kgText.toDouble()
        assertTrue("arranca en kg con el último peso (~78 kg), no en libras: $kgText", kg in 70.0..90.0)

        // Cambia a lb: el número se convierte (no se pierde) y la elección se guarda.
        compose.onAllNodes(hasText("lb") and hasClickAction()).onLast().performClick(); settle()   // el del diálogo (el de la barra queda detrás)
        val lbText = field.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertEquals(kg * WeightUnit.KG_TO_LB, lbText.toDouble(), 0.11)
        // El ajuste se guarda en segundo plano (DataStore): se espera a que llegue en vez de leerlo al instante.
        repeat(30) { if (c.settings.settings.first().bodyUnit == WeightUnit.LB) return@repeat; settle(100) }
        assertEquals(WeightUnit.LB, c.settings.settings.first().bodyUnit)
        captureScreenRoboImage("screenshots/62_registrar_peso_lb.png")

        // Escribe 176 lb y guarda: se guarda en kg (79.8).
        field.performTextReplacement("176"); settle()
        compose.onNodeWithText("Guardar").performClick(); settle(1200)
        val today = Dates.today(c.zone.value).toEpochDay().toInt()
        val saved = c.bodyWeight.page(Int.MAX_VALUE, 1).first()
        assertEquals(today, saved.measuredOn)
        assertEquals(176 / WeightUnit.KG_TO_LB, saved.weightKg, 0.01)
        waitText("Corregir el de hoy")
        waitText("176 lb")
        shot("63_peso_pestana_lb")
    }

    @Test fun perfil_tiene_aprende_y_compartir_arriba() = runBlocking {
        setup(withWeights = false)
        app { ProfileScreen(rememberNavController()) }
        waitText("Compartir Rupio"); waitText("Unidad del peso corporal")
        shot("64_perfil_aprende_compartir")
    }

    @Test fun compartir_abre_el_menu_de_android_con_el_enlace_de_play() = runBlocking {
        setup(withWeights = false)
        app { ProfileScreen(rememberNavController()) }
        waitText("Compartir Rupio")
        compose.onNode(hasText("Compartir Rupio") and hasClickAction()).performScrollTo().performClick(); settle()
        val started = shadowOf(app).nextStartedActivity
        assertNotNull("abre el menú de compartir", started)
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val inner = started.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        val text = inner.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text, text.contains("https://play.google.com/store/apps/details?id=com.rupio.app"))
        assertTrue(text.contains("gratis"))
        assertEquals(ShareApp.message("com.rupio.app"), text)
    }
}
