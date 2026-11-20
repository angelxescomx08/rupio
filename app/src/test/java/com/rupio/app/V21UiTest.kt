package com.rupio.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rupio.app.data.billing.SupportState
import com.rupio.app.data.billing.WallEntry
import com.rupio.app.data.billing.WallMe
import com.rupio.app.data.billing.WallState
import com.rupio.app.domain.AppIconStyle
import com.rupio.app.domain.SupportTier
import com.rupio.app.ui.support.AccentPicker
import com.rupio.app.ui.support.AppIconPicker
import com.rupio.app.ui.support.AppIconPreview
import com.rupio.app.ui.support.SupporterAvatar
import com.rupio.app.ui.support.WallAccess
import com.rupio.app.ui.support.WallContent
import com.rupio.app.ui.theme.Appearance
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** v2.1 · QA de recompensas, íconos y muro (capturas en app/screenshots/v21_*). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rMX-w393dp-h852dp-night-xhdpi")
class V21UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()

    @After fun restore() { Appearance.setMode(ThemeMode.DARK) }

    private fun settle(ms: Long = 600) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("screenshots/$name.png") }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }

    @Test fun a_el_selector_muestra_los_iconos_de_verdad() {
        val licuado = SupportState(owned = setOf(SupportTier.LICUADO))
        app {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIconPicker(licuado) {}
                // Los cuatro, grandes, para revisar que el dibujo quede dentro del círculo y centrado.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { AppIconStyle.entries.forEach { AppIconPreview(it, 80.dp) } }
            }
        }
        shot("v21_01_iconos_reales")
        // Los bloqueados siguen diciendo con qué nivel se abren.
        compose.onNodeWithContentDescription("Dorado. Se desbloquea con 🏆 Fan de Rupio").assertExists()
    }

    @Test fun b_marco_del_avatar_en_tema_claro() {
        Appearance.setMode(ThemeMode.LIGHT)
        app {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SupportTier.entries.forEach { SupporterAvatar("Ana López", 64.dp, it) }
            }
        }
        shot("v21_02_marcos_tema_claro")
    }

    @Test fun c_muro_con_barra_y_aporte_que_te_sube() {
        val entries = listOf(WallEntry(1, "Carolina M.", 0, false), WallEntry(2, "El Profe Toño", 0, false), WallEntry(3, "José Ángel", 0, true))
        val me = WallMe("José Ángel", 598, 3, 152, hidden = false, blocked = false, sinceMs = 0, total = 3)
        app { WallContent(WallState(entries, me, loaded = true), SupportState(), WallAccess.READY, onJoin = {}, onBoost = {}, onRename = {}, onHide = {}, onCreateAccount = {}, onRetry = {}) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Llegas al #2").fetchSemanticsNodes().isNotEmpty() }
        // Solo el aporte que alcanza dice a dónde te lleva (152 puntos: el de 249).
        assertTrue(compose.onAllNodesWithText("Llegas al #2").fetchSemanticsNodes().size == 1)
        compose.onNodeWithContentDescription("suma 249 puntos. Llegas al #2", substring = true).assertExists()
        compose.onNodeWithContentDescription("del camino al lugar #2", substring = true).assertExists()
        shot("v21_03_muro_barra")
    }

    @Test fun e_el_nombre_por_defecto_sigue_al_idioma() {
        val c = app.container
        try {
            com.rupio.app.core.I18n.set(com.rupio.app.core.AppLanguage.EN)
            c.auth.useLocalMode("")
            assertTrue((c.auth.state.value as com.rupio.app.data.auth.AuthState.SignedIn).name == "Athlete")
            com.rupio.app.core.I18n.set(com.rupio.app.core.AppLanguage.ES)
            c.onLanguageChanged()
            assertTrue((c.auth.state.value as com.rupio.app.data.auth.AuthState.SignedIn).name == "Atleta")
            // Un nombre escrito por la persona no cambia.
            c.auth.updateName("Ana"); com.rupio.app.core.I18n.set(com.rupio.app.core.AppLanguage.EN); c.localizeDefaultName()
            assertTrue((c.auth.state.value as com.rupio.app.data.auth.AuthState.SignedIn).name == "Ana")
        } finally { com.rupio.app.core.I18n.set(com.rupio.app.core.AppLanguage.ES) }
    }

    /** Comentario del usuario: "Opción de reemplazar ejercicio". */
    @Test fun f_reemplazar_ejercicio() = kotlinx.coroutines.runBlocking {
        val c = app.container
        com.rupio.app.data.local.CatalogSeeder.seedIfNeeded(app, c.db)
        c.auth.useLocalMode("Ana")
        c.db.workouts().active()?.let { c.workouts.discard(it.id) }
        val wid = c.workouts.startEmpty("Pierna")
        c.workouts.addExercises(wid, listOf("sentadilla_con_barra", "caminadora"))
        fun ex() = kotlinx.coroutines.runBlocking { c.db.workouts().exercises(wid).filter { !it.deleted }.sortedBy { it.position } }
        fun sets(weId: String) = kotlinx.coroutines.runBlocking { c.db.workouts().sets(wid).filter { it.workoutExerciseId == weId && !it.deleted } }
        val squat = ex().first()
        // 1) Sin series marcadas: se cambia en su lugar y conserva cuántas series había.
        c.db.workouts().upsertSet(sets(squat.id).first().copy(weightKg = 60.0, reps = 8, completed = false))
        assertTrue(c.workouts.replaceExercise(squat.id, "prensa_de_piernas"))
        ex().first().let { assertTrue(it.id == squat.id && it.exerciseId == "prensa_de_piernas" && it.position == 0) }
        sets(squat.id).let { s -> assertTrue(s.size == 3 && s.all { it.exerciseId == "prensa_de_piernas" && it.weightKg == null && it.reps == null }) }
        // 2) Con una serie marcada: esa se queda con la prensa y las otras 2 pasan al nuevo, justo después.
        val first = sets(squat.id).minBy { it.position }
        c.db.workouts().upsertSet(first.copy(weightKg = 100.0, reps = 10, completed = true))
        assertTrue(c.workouts.replaceExercise(squat.id, "prensa_en_smith"))
        val list = ex()
        assertTrue("prensa, la nueva y luego la caminadora", list.map { it.exerciseId } == listOf("prensa_de_piernas", "prensa_en_smith", "caminadora"))
        assertTrue(sets(squat.id).let { s -> s.size == 1 && s.single().completed && s.single().weightKg == 100.0 })
        assertTrue(sets(list[1].id).size == 2)
        // 3) A cardio: 1 serie y sin descanso.
        assertTrue(c.workouts.replaceExercise(list[1].id, "caminadora"))
        assertTrue(ex()[1].restSec == 0 && sets(list[1].id).size == 1)
        c.workouts.discard(wid)
    }

    @Test fun d_muro_sin_cuenta_no_dice_estas_personas() {
        app { WallContent(WallState(), SupportState(), WallAccess.LOCAL_ONLY, onJoin = {}, onBoost = {}, onRename = {}, onHide = {}, onCreateAccount = {}, onRetry = {}) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Aquí aparecen", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Estas personas", substring = true).fetchSemanticsNodes().isEmpty())
    }
}

/** Con letra al 200 % en 320 dp (el peor caso de QaUiTest): las etiquetas de colores e íconos se leen completas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rMX-w320dp-h640dp-night-xhdpi", fontScale = 2.0f)
class V21BigTextTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()

    private fun overflows(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().any { n ->
        val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layout)
        layout.firstOrNull()?.hasVisualOverflow == true
    }

    /**
     * Los botones anchos tenían alto fijo (48–56 dp): con letra al 200 % "Agregar ejercicios" ocupa 2 líneas y la
     * segunda quedaba cortada ("+ Agregar"). Ahora el alto es un mínimo y el botón crece.
     */
    @Test fun los_botones_anchos_crecen_con_la_letra() = kotlinx.coroutines.runBlocking {
        val c = app.container
        c.auth.useLocalMode("Ana")
        val wid = c.workouts.startEmpty("Pierna"); c.workouts.addExercises(wid, listOf("sentadilla_con_barra"))
        compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) {
            com.rupio.app.ui.workout.ActiveWorkoutScreen(androidx.navigation.compose.rememberNavController())
        } } }
        compose.waitUntil(15_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Agregar ejercicios"))
        repeat(4) { ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(150); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/v21_05_agregar_ejercicios_letra_200.png")
        val info = compose.onAllNodesWithText("Agregar ejercicios", useUnmergedTree = true).fetchSemanticsNodes().map { n ->
            val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layout)
            layout.firstOrNull()?.let { "lines=${it.lineCount} size=${it.size} h=${it.didOverflowHeight} w=${it.didOverflowWidth} max=${it.layoutInput.constraints}" } ?: "sin layout"
        }
        assertTrue("\"Agregar ejercicios\" se corta con letra al 200 %: $info", !overflows("Agregar ejercicios"))
        c.workouts.discard(wid)
    }

    @Test fun selectores_con_letra_grande() {
        val licuado = SupportState(owned = setOf(SupportTier.LICUADO))
        compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { AccentPicker(licuado) {}; AppIconPicker(licuado) {} }
        } } }
        repeat(4) { ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(150); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/v21_04_selectores_letra_200.png")
        // Cada etiqueta cabe en su celda (antes "Turquesa" se cortaba a "Turq" y "Plateado" a "Plate").
        listOf("Turquesa", "Plateado", "Dorado", "Aurora", "Normal").forEach { label ->
            compose.onAllNodesWithText(label).fetchSemanticsNodes().forEach { n ->
                val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layout)
                layout.firstOrNull()?.let { assertTrue("\"$label\" se corta con letra al 200 %", !it.hasVisualOverflow) }
            }
        }
    }
}
