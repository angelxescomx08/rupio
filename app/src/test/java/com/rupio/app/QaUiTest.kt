package com.rupio.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.data.local.*
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.RupioTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * QA de interfaz en el peor caso: pantalla angosta (320 dp), letra al 200 % (accesibilidad del sistema),
 * nombres larguísimos y números enormes. En cada pantalla se audita:
 *  - zonas táctiles de al menos 48 dp (Material / WCAG 2.5.8),
 *  - todo lo que se puede tocar tiene texto o descripción (TalkBack),
 *  - y se guarda captura para revisión visual (app/screenshots/qa/).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2.0f)
class QaUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val problems = mutableListOf<String>()

    private fun settle(ms: Long = 600) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    /** Espera a que aparezca el texto; si está más abajo en una lista, desplaza hasta encontrarlo. */
    private fun waitText(text: String) {
        val t0 = System.currentTimeMillis()
        while (compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()) {
            if (System.currentTimeMillis() - t0 > 15_000) throw AssertionError("No apareció \"$text\" en pantalla")
            settle(300)
            runCatching { compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText(text, substring = true)) }
        }
    }
    /** Desplaza la lista (o la columna) hasta que el texto esté en pantalla. */
    private fun scrollTo(text: String) {
        settle()
        val lazy = compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes()
        if (lazy.isNotEmpty()) runCatching { compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText(text, substring = true)) }
        if (compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty())
            runCatching { compose.onAllNodesWithText(text, substring = true)[0].performScrollTo() }
        settle()
    }
    /** Activa el elemento tocable que contiene el texto (como TalkBack: no depende de dónde quede el centro). */
    private fun tap(text: String) {
        scrollTo(text)
        val clickable = compose.onAllNodes(hasText(text, substring = true) and hasClickAction())
        if (clickable.fetchSemanticsNodes().isNotEmpty()) clickable[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        else compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)[0].performTouchInput { click(center) }
        settle()
    }
    /**
     * Pestañas: con letra al 200 % en 320 dp las etiquetas ya no se achican (v1.6, WCAG 1.4.4): se ocultan y el ícono
     * lleva el nombre como descripción. Se busca por texto o por descripción, como lo haría TalkBack.
     */
    private fun tabNode(label: String) = compose.onAllNodes((hasText(label) or hasContentDescription(label)) and hasClickAction())
    private fun waitTab(label: String) {
        val t0 = System.currentTimeMillis()
        while (tabNode(label).fetchSemanticsNodes().isEmpty()) { if (System.currentTimeMillis() - t0 > 15_000) throw AssertionError("No apareció la pestaña \"$label\""); settle(300) }
    }
    private fun tapTab(label: String) { waitTab(label); tabNode(label).onLast().performClick(); settle() }

    /** Auditoría de accesibilidad de lo que está en pantalla + captura. */
    private fun audit(screen: String) {
        settle()
        compose.onRoot().captureRoboImage("screenshots/qa/$screen.png")
        val density = compose.activity.resources.displayMetrics.density
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        for (n in nodes) {
            if (n.config.getOrNull(SemanticsProperties.Disabled) != null) continue
            if (n.boundsInRoot.width < 1 || n.boundsInRoot.height < 1) continue   // fuera de pantalla
            // Tamaño real del elemento (no el pedazo visible si la lista lo recorta en el borde).
            val w = n.size.width / density; val h = n.size.height / density
            val label = describe(n)
            // El área táctil real incluye el relleno táctil de Material (minimumInteractiveComponentSize).
            val touchW = maxOf(w, n.touchBoundsInRoot.width / density); val touchH = maxOf(h, n.touchBoundsInRoot.height / density)
            if (touchW < 47.5f || touchH < 47.5f) problems += "[$screen] zona táctil ${"%.0f".format(touchW)}×${"%.0f".format(touchH)} dp: $label"
            if (label.isBlank()) problems += "[$screen] botón sin texto ni descripción en (${n.boundsInRoot.left.toInt()}, ${n.boundsInRoot.top.toInt()}) " +
                "${n.config.map { it.key.name }} hijos=${n.children.size} sinFusionar=${compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().count { it.boundsInRoot == n.boundsInRoot }}"
        }
    }

    private fun describe(n: SemanticsNode): String {
        val parts = mutableListOf<String>()
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.let { parts += it }
        n.config.getOrNull(SemanticsProperties.Text)?.let { parts += it.map { t -> t.text } }
        n.config.getOrNull(SemanticsProperties.EditableText)?.let { parts += it.text }
        n.config.getOrNull(SemanticsActions.OnClick)?.label?.let { parts += it }
        if (parts.isEmpty()) n.children.forEach { ch -> parts += describe(ch) }
        return parts.joinToString(" ").trim()
    }

    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }

    private suspend fun seedLong() {
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("María Guadalupe Hernández de la Cruz Villaseñor")
        val uid = c.auth.currentUserId!!
        val long = c.exercises.createCustom("Press inclinado en máquina convergente con agarre neutro y pausa de dos segundos abajo", "pecho", "maquina", "weight_reps", false)
        val t = System.currentTimeMillis()
        val r = RoutineEntity(Ids.uuidV7(t), uid, "Rutina de torso con superseries y nombre larguísimo para ver si se corta bien", null, 0, t, t)
        c.db.routines().saveRoutine(r, listOf(
            RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, long, 0, targetSets = 4, updatedAt = t, setPlan = "[{\"w\":680.4,\"r\":999},{\"w\":680.4,\"r\":12}]"),
            RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, "elevaciones_laterales_con_mancuernas", 1, supersetId = "ss", supersetRestMode = 1, supersetRestSec = 90, updatedAt = t),
            RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, "curl_martillo", 2, supersetId = "ss", supersetRestMode = 1, supersetRestSec = 90, updatedAt = t),
            RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, "plancha", 3, updatedAt = t),
        ), t)
        // Historial con pesos enormes y muchas series para que los números sean largos.
        val zone = ZoneId.of("America/Mexico_City")
        for (k in 0 until 30) {
            val start = t - (30 - k) * 50L * 3600_000L
            val day = Dates.localEpochDay(start, zone)
            val w = WorkoutEntity(Ids.uuidV7(start), uid, null, "Sesión de pierna pesada número $k con nombre largo", start, start + 5_400_000, zone.id, day, 5400, updatedAt = start)
            val we = WorkoutExerciseEntity(Ids.uuidV7(start), w.id, uid, "prensa_de_piernas", 0, updatedAt = start)
            val sets = (0 until 8).map { s -> WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, uid, we.exerciseId, s, weightKg = 400.0 + k, reps = 20 + s,
                completed = true, completedAt = start + s * 60_000L, performedOn = day, updatedAt = start) }
            c.db.workouts().insertFull(w, listOf(we), sets)
            c.workouts.finish(w.id, null, null)
            c.db.workouts().get(w.id)?.let { c.db.workouts().upsertWorkout(it.copy(endedAt = start + 5_400_000, durationSec = 5400)) }
        }
        val today = Dates.today(zone)
        for (i in 0 until 60) c.bodyWeight.log(today.minusDays(i.toLong()), 181.4 - i * 0.05)
    }

    @Test fun pantallasEnElPeorCaso() = runBlocking {
        seedLong()
        app { MainShell() }
        waitTab("Entrenar"); waitText("Esta semana"); audit("01_inicio")
        scrollTo("Rutina de torso"); audit("01b_inicio_rutina")
        tapTab("Progreso"); waitText("Tu progreso"); audit("02_progreso")
        tap("Ver cómo has cambiado")
        waitText("Este mes contra el anterior"); audit("03_tu_progreso")
        scrollTo("Qué significa"); audit("03b_tu_progreso_explicacion")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        tap("Registrar"); audit("05_registrar_peso")
        compose.onNodeWithText("Cancelar").performClick(); settle()
        tapTab("Peso"); waitText("Tu peso (tendencia)"); audit("04_peso")
        tapTab("Historial"); waitText("Sesión de pierna"); audit("06_historial")
        tapTab("Perfil"); waitText("Unidad de las pesas"); audit("09_perfil")
        tap("Tutoriales, glosario"); waitText("Lo más importante"); audit("07_aprende")
        tap("Cómo progresar"); waitText("Sobrecarga"); audit("08_tutorial")
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        tapTab("Entrenar"); waitText("Esta semana")
        tap("Guía"); waitText("rutina a tu medida"); audit("10_guia")
        compose.onNodeWithText("Empezar").performClick(); settle(); audit("10b_guia_dias")
        repeat(6) { if (compose.onAllNodesWithText("Siguiente").fetchSemanticsNodes().isNotEmpty()) { compose.onAllNodesWithText("Siguiente")[0].performClick(); settle() } }
        if (compose.onAllNodesWithText("Ver mi propuesta").fetchSemanticsNodes().isNotEmpty()) { compose.onNodeWithText("Ver mi propuesta").performClick(); settle() }
        waitText("Tu propuesta"); audit("11_guia_propuesta")
        repeat(8) { if (compose.onAllNodesWithContentDescription("Atrás").fetchSemanticsNodes().isNotEmpty()) { compose.onAllNodesWithContentDescription("Atrás")[0].performClick(); settle() } }

        // Entrenamiento activo con el ejercicio de nombre larguísimo y pesos de 4 cifras.
        val routine = c.routines.routines.first().first { it.name.startsWith("Rutina de torso") }
        val wid = c.workouts.startFromRoutine(routine.id)
        c.db.workouts().sets(wid).first().let { c.workouts.editSet(it.id) { s -> s.copy(weightKg = 680.4, reps = 999) } }
        tapTab("Entrenar"); tap("Continuar entrenamiento"); waitText("Terminar"); audit("12_entrenamiento")
        compose.onNodeWithText("Terminar").performClick(); settle(); audit("13_terminar")
        compose.onNodeWithText("Seguir entrenando").performClick(); settle()

        println("Problemas de accesibilidad encontrados (${problems.size}):\n" + problems.distinct().joinToString("\n"))
        assertEquals("Problemas de accesibilidad:\n" + problems.distinct().joinToString("\n"), emptyList<String>(), problems.distinct())
    }


    // ------------------------------------------------------------------ contraste de color (WCAG 2.1 AA)

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.luminance() + 0.05; val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b).toDouble()
    }
    private fun over(fg: Color, bg: Color): Color = Color(  // color con transparencia ya mezclado sobre el fondo
        fg.red * fg.alpha + bg.red * (1 - fg.alpha), fg.green * fg.alpha + bg.green * (1 - fg.alpha), fg.blue * fg.alpha + bg.blue * (1 - fg.alpha))

    @Test fun contrasteDeTextosAA() {
        val text = mapOf("Texto" to RupioColors.Text, "Texto secundario" to RupioColors.TextDim, "Texto tenue" to RupioColors.TextFaint,
            "Azul" to RupioColors.Primary, "Verde" to RupioColors.Success, "Ámbar" to RupioColors.Warning, "Rojo" to RupioColors.Danger, "Violeta" to RupioColors.Rest)
        val bgs = mapOf("negro" to RupioColors.Background, "tarjeta" to RupioColors.Surface, "tarjeta 2" to RupioColors.Surface2, "tarjeta 3" to RupioColors.Surface3,
            "aviso ámbar" to over(RupioColors.Warning.copy(alpha = 0.10f), RupioColors.Background), "chip azul" to over(RupioColors.Primary.copy(alpha = 0.18f), RupioColors.Surface))
        val fails = mutableListOf<String>()
        for ((tn, t) in text) for ((bn, b) in bgs) {
            val r = contrast(t, b)
            if (r < 4.5) fails += "$tn sobre $bn: ${"%.2f".format(r)}:1"
        }
        // Botones: contenido sobre su color (tema y los que están fijos en el código).
        val onPrimary = com.rupio.app.ui.theme.RupioScheme.onPrimary
        listOf("botón azul" to (onPrimary to RupioColors.Primary), "botón verde" to (Color.Black to RupioColors.Success),
            "botón YouTube" to (Color.White to com.rupio.app.ui.theme.RupioColors.YouTube)).forEach { (n, p) ->
            val r = contrast(p.first, p.second); if (r < 4.5) fails += "$n: ${"%.2f".format(r)}:1"
        }
        RupioColors.Avatars.forEachIndexed { i, a -> val r = contrast(Color.White, a); if (r < 4.5) fails += "avatar $i: ${"%.2f".format(r)}:1" }
        RupioColors.Supersets.forEachIndexed { i, s -> val r = contrast(s, RupioColors.Surface); if (r < 3.0) fails += "superserie $i (letra/borde): ${"%.2f".format(r)}:1" }
        // Bordes de controles (WCAG 1.4.11 pide 3:1) sobre todas las superficies.
        listOf(RupioColors.Background, RupioColors.Surface, RupioColors.Surface2, RupioColors.Surface3).forEachIndexed { i, b ->
            val r = contrast(RupioColors.ControlOutline, b); if (r < 3.0) fails += "borde de control sobre superficie $i: ${"%.2f".format(r)}:1"
        }
        assertTrue("Contraste insuficiente (WCAG AA pide 4.5:1):\n" + fails.joinToString("\n"), fails.isEmpty())
    }

    /** v1.9: metales de los niveles (Bronce, Plata, Oro, Aurora). */
    @Test fun contrasteDeMetales() {
        val fails = mutableListOf<String>()
        val bgs = listOf(RupioColors.Background, RupioColors.Surface, RupioColors.Surface2, RupioColors.Surface3)
        for (m in com.rupio.app.ui.theme.Metals.all) {
            (bgs + m.container).forEach { b -> val r = contrast(m.ink, b); if (r < 7.0) fails += "${m.name} (tinta) sobre $b: ${"%.2f".format(r)}:1" }
            m.stops.forEach { s -> val r = contrast(m.on, s); if (r < 6.0) fails += "texto sobre el degradado ${m.name}: ${"%.2f".format(r)}:1" }
        }
        assertTrue("Contraste insuficiente en los metales:\n" + fails.joinToString("\n"), fails.isEmpty())
    }
}
