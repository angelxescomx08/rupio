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
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.Dates
import com.rupio.app.core.I18n
import com.rupio.app.core.Ids
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.domain.SupportTier
import com.rupio.app.ui.support.SupportScreen
import com.rupio.app.ui.theme.Appearance
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * v2.0: la app en inglés y con tema claro (teléfono en inglés y modo claro), con capturas en app/screenshots/v20_*.
 * Revisa que no quede español a la vista y que lo nuevo de apoyo (marco, muro en Inicio, recompensas) aparezca.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-rUS-w393dp-h852dp-notnight-xhdpi")
class V20UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container

    @Before fun englishLight() { I18n.set(AppLanguage.EN); Appearance.setMode(ThemeMode.LIGHT) }
    @After fun restore() { c.support.debugReset(); I18n.set(AppLanguage.ES); Appearance.setMode(ThemeMode.DARK) }

    private fun settle(ms: Long = 600) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("screenshots/$name.png") }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    private fun waitText(text: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun scrollTo(text: String) {
        val lazy = compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()
        if (lazy) compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text, substring = true))
        else compose.onAllNodesWithText(text, substring = true)[0].performScrollTo()
        settle()
    }
    /** Ningún texto visible con palabras típicas del español (las de los datos de ejemplo no cuentan). */
    private fun assertNoSpanish(where: String) {
        val spanish = Regex("\\b(Entrenar|Historial|Progreso|Perfil|Rutina|Empezar|Guardar|Cancelar|serie|series|descanso|semana|Agregar|Muro|Apoya)\\b")
        val texts = compose.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }.orEmpty().map { it.text } }
        val bad = texts.filter { spanish.containsMatchIn(it) }
        assertTrue("$where tiene español: $bad", bad.isEmpty())
    }

    private suspend fun setup() {
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("Alex Morgan")
    }

    /** 12 entrenos con la misma rutina de ejemplo (en inglés). */
    private suspend fun seedHistory(): String {
        var last = ""
        val uid = c.auth.currentUserId!!
        val zone = ZoneId.of("America/Mexico_City")
        val lb = WeightUnit.LB
        val exs = listOf("press_de_banca_con_barra" to 135.0, "remo_con_barra" to 115.0, "sentadilla_con_barra" to 155.0)
        val now = System.currentTimeMillis()
        for (k in 0 until 12) {
            val start = now - (12 - k) * 72L * 3600_000L
            val day = Dates.localEpochDay(start, zone)
            val w = WorkoutEntity(Ids.uuidV7(start), uid, null, "Full body", start, null, zone.id, day, updatedAt = start)
            last = w.id
            val wes = exs.mapIndexed { i, (ex, _) -> WorkoutExerciseEntity(Ids.uuidV7(start), w.id, uid, ex, i, updatedAt = start, restSec = 120) }
            val sets = wes.zip(exs).flatMap { (we, pair) ->
                (0 until 3).map { s ->
                    WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, uid, we.exerciseId, s, weightKg = lb.toKg(pair.second + 5.0 * (k / 3)), reps = 8 + k % 4,
                        completed = true, completedAt = start + s * 150_000L, performedOn = day, updatedAt = start)
                }
            }
            c.db.workouts().insertFull(w, wes, sets)
            c.workouts.finish(w.id, "Full body", null)
            c.db.workouts().get(w.id)?.let { c.db.workouts().upsertWorkout(it.copy(endedAt = start + 3_000_000L, durationSec = 3000)) }
        }
        return last
    }

    @Test fun a_inicio_progreso_y_perfil_en_ingles_claro() = runBlocking {
        setup(); c.routines.seedSampleIfEmpty(); seedHistory()
        c.support.debugReset(); c.support.debugGrant(SupportTier.FAN)
        app { MainShell() }
        waitText("Full body · Sample")
        compose.onNodeWithText("Start empty workout").assertExists()
        shot("v20_01_home_light_en")
        assertNoSpanish("Inicio")
        // El muro se asoma al final de Inicio (desde el 3.er entreno), con "Ocultar".
        scrollTo("Thank-you wall")
        shot("v20_02_home_wall_teaser")
        compose.onNodeWithText("Progress").performClick(); waitText("Your progress")
        shot("v20_03_progress_light_en")
        compose.onNodeWithText("Profile").performClick(); waitText("Language")
        shot("v20_04_profile_light_en")
        scrollTo("Theme")
        shot("v20_05_profile_language_theme")
        assertNoSpanish("Perfil")
        compose.onNodeWithText("History").performClick(); settle(1200)
        shot("v20_06_history_light_en")
    }

    @Test fun b_apoya_rupio_recompensas_y_niveles() = runBlocking {
        setup()
        c.support.debugReset()
        app { SupportScreen(rememberNavController()) }
        waitText("Supporter rewards")
        shot("v20_07_support_tiers_light_en")
        scrollTo("Patron")
        shot("v20_08_support_patron_tier")
        assertNoSpanish("Apoya Rupio")
    }

    @Test fun c_tus_recompensas_explican_que_son_por_pagar() = runBlocking {
        setup()
        c.support.debugReset(); c.support.debugGrant(SupportTier.LICUADO)
        app { SupportScreen(rememberNavController()) }
        waitText("Your rewards")
        compose.onNodeWithText("You have this for supporting Rupio", substring = true).assertExists()
        shot("v20_09_my_rewards_light_en")
        scrollTo("App icon")
        shot("v20_10_colors_icons_light_en")
    }

    @Test fun d_resumen_con_confeti_para_quien_apoya() = runBlocking {
        setup(); val id = seedHistory()
        c.support.debugReset(); c.support.debugGrant(SupportTier.FAN)
        app { com.rupio.app.ui.history.WorkoutDetailScreen(rememberNavController(), id, 1) }
        waitText("Thanks for supporting Rupio")
        compose.mainClock.autoAdvance = false
        repeat(8) { compose.mainClock.advanceTimeBy(100); ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) }
        compose.onRoot().captureRoboImage("screenshots/v20_11_summary_supporter_confetti.png")
    }

    @Test fun e_tema_oscuro_en_ingles() = runBlocking {
        Appearance.setMode(ThemeMode.DARK)
        setup(); c.routines.seedSampleIfEmpty()
        c.support.debugReset(); c.support.debugGrant(SupportTier.MECENAS)
        app { MainShell() }
        waitText("Full body · Sample")
        shot("v20_12_home_dark_en_patron")
        compose.onNodeWithText("Profile").performClick(); waitText("Language")
        shot("v20_13_profile_dark_en_patron")
    }
}
