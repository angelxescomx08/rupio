package com.rupio.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.rupio.app.ui.auth.AuthScreen
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.tutorial.OnboardingScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Capturas de las pantallas REALES (Robolectric + Roborazzi) con datos de ejemplo.
 * Se guardan en app/screenshots/. Las imágenes de ejercicios vienen de internet y aquí salen en blanco.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val lb = WeightUnit.LB

    private fun settle(ms: Long = 600) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("screenshots/$name.png") }
    /** Igual que MainActivity: tema + Surface negro. */
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    private fun scrollTo(text: String) {
        val lazy = compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()
        if (lazy) compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text, substring = true))
        else compose.onAllNodesWithText(text, substring = true)[0].performScrollTo()
        settle()
    }
    private fun waitText(text: String, substring: Boolean = true) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test fun a_bienvenida() {
        app { OnboardingScreen("José Ángel Hernández") {} }
        shot("01_bienvenida")
        compose.onNodeWithText("Siguiente").performClick(); settle()
        compose.onNodeWithContentDescription("Completar").performClick()
        shot("02_bienvenida_demo_serie")
        compose.onNodeWithText("Siguiente").performClick(); settle()
        compose.onNodeWithText("Siguiente").performClick()
        shot("03_bienvenida_superseries")
    }

    @Test fun b_login() {
        app { AuthScreen() }
        shot("04_login")
    }

    @Test fun c_recorrido() = runBlocking {
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("José Ángel Hernández")
        c.routines.seedSampleIfEmpty()
        seedHistory()

        app { MainShell() }
        waitText("Cuerpo completo")
        shot("05_inicio")

        // Editor de rutina (con superserie, descanso total y duración estimada)
        compose.onAllNodesWithContentDescription("Opciones")[0].performClick(); settle()
        compose.onNodeWithText("Editar").performClick()
        waitText("Descanso total")
        shot("06_editor_rutina")
        scrollTo("Agregar ejercicios")
        shot("07_editor_rutina_superserie")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()

        // Progreso
        compose.onNodeWithText("Progreso").performClick()
        waitText("Tu progreso")
        // Las gráficas cargan en segundo plano: se espera a que aparezcan en vez de suponer que ya están.
        for (k in 0 until 20) { if (runCatching { scrollTo("Volumen por") }.isSuccess) break; settle(500) }
        shot("08_progreso")
        scrollTo("Constancia")
        shot("09_progreso_musculos")
        scrollTo("Récords recientes")
        shot("10_progreso_1rm_records")
        compose.onNodeWithText("Año").performClick(); settle()
        for (k in 0 until 20) { if (runCatching { scrollTo("Volumen por mes") }.isSuccess) break; settle(500) }
        settle(1200)
        shot("11_progreso_anual")

        // Historial y detalle
        compose.onNodeWithText("Historial").performClick()
        waitText("entrenos")
        shot("12_historial")
        compose.onAllNodesWithText("Pierna")[0].performClick()
        waitText("Duración")
        shot("13_detalle_entrenamiento")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()

        // Perfil
        compose.onNodeWithText("Perfil").performClick()
        waitText("Unidad de las pesas")
        shot("14_perfil")

        // Entrenamiento activo con una serie completada y el descanso corriendo
        val routineId = c.routines.routines.first().first().id
        val wid = c.workouts.startFromRoutine(routineId)
        val sets = c.db.workouts().sets(wid)
        val first = sets.first()
        c.workouts.editSet(first.id) { it.copy(weightKg = lb.toKg(50.0), reps = 12) }
        c.workouts.toggleComplete(first.id, com.rupio.app.domain.Tracking.WEIGHT_REPS, null, null, null)
        c.restTimer.start(90, first.id, "Siguiente: Sentadilla goblet · serie 2")
        compose.onNodeWithText("Entrenar").performClick(); settle()
        compose.onNodeWithText("Continuar entrenamiento").performClick()
        waitText("Terminar")
        shot("15_entrenamiento")
        scrollTo("Remo con mancuerna")
        shot("16_entrenamiento_superserie")

        // Opciones de serie (tipo + RPE)
        // El número de serie se anuncia completo para TalkBack ("Serie 2 de Sentadilla goblet, normal").
        scrollTo("Sentadilla goblet"); compose.onAllNodesWithContentDescription("Serie 2 de Sentadilla goblet", substring = true)[0].performClick(); settle()
        shot("17_opciones_de_serie")
        compose.onAllNodes(isRoot()).onLast().performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 60f)) }; settle(1500)

        // Crear superserie desde el menú del ejercicio
        compose.onAllNodesWithContentDescription("Opciones del ejercicio")[0].performClick(); settle()
        compose.onNodeWithText("Unir en superserie con el siguiente").performClick(); settle()
        shot("18_crear_superserie")
        compose.onNodeWithText("Cancelar").performClick(); settle()

        // Selector de ejercicios con búsqueda sin acentos
        scrollTo("Agregar ejercicios"); compose.onNodeWithText("Agregar ejercicios").performClick(); waitText("de 256")
        compose.onNode(hasSetTextAction()).performTextInput("press manc"); settle()
        compose.onAllNodesWithContentDescription("Ver detalles")[0].performClick(); waitText("Buscar videos")
        shot("20_detalle_ejercicio")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        compose.onAllNodesWithText("Press", substring = true)[0].performClick(); settle()
        shot("19_selector_ejercicios")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()

        // Tutorial interactivo sobre los botones reales
        c.settings.resetTutorial(); c.settings.setOnboardingDone(true)
        settle(2500)
        shot("22_tutorial_entrenamiento")
        compose.onNodeWithText("Saltar tutorial").performClick(); settle()
        compose.onNodeWithContentDescription("Atrás").performClick(); settle(2500)
        shot("23_tutorial_inicio")
    }

    private suspend fun setupLocal() {
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("José Ángel Hernández")
    }

    /** 120 días de peso: bajando ~0.4 kg/semana con el ruido normal del agua. */
    private suspend fun seedBodyWeight() {
        val zone = ZoneId.of("America/Mexico_City")
        val today = Dates.today(zone)
        for (i in 0 until 120) {
            if (i % 5 == 3) continue
            val noise = kotlin.math.sin(i * 2.1) * 0.5 + (if (i % 7 == 5) 0.6 else 0.0)
            c.bodyWeight.log(today.minusDays((119 - i).toLong()), 84.0 - i * 0.055 + noise)
        }
    }

    @Test fun d_progreso_y_peso() = runBlocking {
        setupLocal(); seedHistory(); seedBodyWeight()
        app { MainShell() }
        compose.onNodeWithText("Progreso").performClick()
        waitText("Tu progreso"); waitText("Tendencia")
        shot("24_progreso_tu_progreso")
        compose.onNodeWithText("Ver cómo has cambiado").performClick()
        waitText("antes y ahora"); settle(1500)
        shot("25_tu_progreso")
        scrollTo("Qué significa")
        shot("26_tu_progreso_explicacion")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        compose.onAllNodesWithText("Peso corporal")[0].performClick()
        waitText("Tu peso (tendencia)")
        shot("27_peso_corporal")
        compose.onAllNodes(hasClickAction() and (hasText("Corregir el de hoy") or hasText("Registrar peso de hoy")))[0].performClick(); settle()
        shot("28_registrar_peso")
    }

    @Test fun e_aprende() = runBlocking {
        setupLocal()
        app { MainShell() }
        compose.onAllNodesWithText("Perfil").onLast().performClick(); waitText("Tutoriales, glosario")
        compose.onNodeWithText("Tutoriales, glosario", substring = true).performScrollTo().performClick()
        waitText("Lo más importante")
        shot("29_aprende")
        scrollTo("Cómo progresar")
        shot("30_aprende_tutoriales")
        compose.onNodeWithText("Cómo progresar").performClick(); waitText("Sobrecarga progresiva")
        compose.onNodeWithText("Siguiente").performClick(); settle(1200)
        shot("31_tutorial_tecnica")
        compose.onNodeWithText("Siguiente").performClick(); settle(1200)
        shot("32_tutorial_cuando_subir")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        scrollTo("Entender tus gráficas"); compose.onNodeWithText("Entender tus gráficas").performClick(); waitText("Volumen por semana o mes")
        shot("33_tutorial_graficas")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        scrollTo("Nutrición y peso corporal"); compose.onNodeWithText("Nutrición y peso corporal").performClick(); waitText("Calorías y mantenimiento")
        shot("34_tutorial_nutricion_energia")
        compose.onNodeWithText("Siguiente").performClick(); settle(800)
        compose.onNodeWithText("Siguiente").performClick(); settle(1200)
        shot("35_tutorial_bajar_de_peso")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        scrollTo("Cuánto entrenar"); compose.onNodeWithText("Cuánto entrenar (sin matarte)").performClick(); waitText("cada vez menos")
        shot("36_tutorial_cuanto_entrenar")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        scrollTo("Glosario"); compose.onNodeWithText("Glosario").performClick(); waitText("Buscar")
        compose.onNode(hasSetTextAction()).performTextInput("deficit"); settle()
        shot("37_glosario")
        compose.onNodeWithText("Déficit calórico").performClick(); waitText("Lo respalda")
        shot("38_concepto_deficit")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        scrollTo("Beneficios del ejercicio"); compose.onNodeWithText("Beneficios del ejercicio").performClick(); waitText("Vivir más")
        shot("39_beneficios")
    }

    @Test fun f_guia_y_editor() = runBlocking {
        setupLocal()
        app { MainShell() }
        waitText("Armar mi rutina con la guía")
        shot("40_inicio_sin_rutinas")
        compose.onNodeWithText("Armar mi rutina con la guía").performClick(); waitText("Te ayudo")
        shot("41_guia_inicio")
        compose.onNodeWithText("Empezar").performClick(); settle()
        compose.onNodeWithText("4").performClick(); settle()
        shot("42_guia_dias")
        compose.onNodeWithText("Siguiente").performClick(); settle()
        compose.onNodeWithText("Siguiente").performClick(); settle()
        shot("43_guia_equipo")
        compose.onNodeWithText("Siguiente").performClick(); settle()
        compose.onNodeWithText("Siguiente").performClick(); settle()
        compose.onNodeWithText("Ver mi propuesta").performClick(); waitText("Tu propuesta"); settle(1200)
        shot("44_guia_propuesta")
        scrollTo("Series por músculo a la semana")
        shot("45_guia_series_musculo")
        compose.onNodeWithText("Crear 4 rutinas").performClick(); waitText("Torso A"); settle(800)
        shot("46_inicio_con_rutinas_guia")
        compose.onAllNodesWithContentDescription("Opciones")[0].performClick(); settle()
        compose.onNodeWithText("Editar").performClick(); waitText("SERIE")
        shot("47_editor_series")
    }

    /** 36 entrenamientos en 12 semanas (Empuje / Tirón / Pierna) con progresión realista. */
    private suspend fun seedHistory() {
        val uid = c.auth.currentUserId!!
        val zone = ZoneId.of("America/Mexico_City")
        val plans = listOf(
            "Empuje" to listOf("press_de_banca_con_barra" to 135.0, "press_militar_con_barra" to 75.0, "elevaciones_laterales_con_mancuernas" to 15.0, "extension_de_triceps_en_polea" to 40.0),
            "Tirón" to listOf("remo_con_barra" to 115.0, "jalon_al_pecho_agarre_ancho" to 100.0, "curl_de_biceps_con_barra" to 55.0, "curl_martillo" to 25.0),
            "Pierna" to listOf("sentadilla_con_barra" to 155.0, "peso_muerto_rumano" to 135.0, "prensa_de_piernas" to 230.0, "curl_femoral_acostado" to 70.0),
        )
        val now = System.currentTimeMillis()
        for (k in 0 until 36) {
            val (name, exs) = plans[k % 3]
            val start = now - (36 - k) * 56L * 3600_000L - 2 * 3600_000L
            val day = Dates.localEpochDay(start, zone)
            val w = WorkoutEntity(Ids.uuidV7(start), uid, null, name, start, null, zone.id, day, updatedAt = start)
            val wes = exs.mapIndexed { i, (ex, _) -> WorkoutExerciseEntity(Ids.uuidV7(start), w.id, uid, ex, i, updatedAt = start, restSec = 120) }
            val sets = wes.zip(exs).flatMap { (we, pair) ->
                val step = if (pair.second >= 100) 5.0 else 2.5
                val kg = lb.toKg(pair.second + step * (k / 6))
                val reps = 8 + (k / 3) % 5
                (0 until 4).map { s ->
                    WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, uid, we.exerciseId, s, setType = if (s == 0) 1 else 0,
                        weightKg = if (s == 0) kg * 0.5 else kg, reps = if (s == 0) 10 else reps - (s - 1).coerceAtLeast(0) / 2,
                        completed = true, completedAt = start + s * 150_000L, performedOn = day, updatedAt = start)
                }
            }
            c.db.workouts().insertFull(w, wes, sets)
            c.workouts.finish(w.id, name, null)
            val dur = 48 * 60 + (k * 7) % 25 * 60
            c.db.workouts().get(w.id)?.let { c.db.workouts().upsertWorkout(it.copy(endedAt = start + dur * 1000L, durationSec = dur)) }
        }
    }
}
