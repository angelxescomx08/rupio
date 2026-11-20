package com.rupio.app

import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.rupio.app.core.Ids
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.RoutineEntity
import com.rupio.app.data.local.RoutineExerciseEntity
import com.rupio.app.domain.ReorderOps
import com.rupio.app.domain.SupersetRestMode
import com.rupio.app.ui.components.ReorderDialog
import com.rupio.app.ui.components.ReorderEntry
import com.rupio.app.ui.home.HomeScreen
import com.rupio.app.ui.routine.RoutineEditorScreen
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.workout.ActiveWorkoutScreen
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

/**
 * v1.9 · Arrastrar para ordenar y deslizar para eliminar, con gestos reales (dedo simulado): ≡, mantener presionado,
 * superseries que no se parten, TalkBack, cancelar, deshacer y lo que queda guardado en la base.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class DragReorderUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val bench = "press_de_banca_con_barra"
    private val row = "remo_con_barra"
    private val squat = "sentadilla_con_barra"
    private val curl = "curl_de_biceps_con_barra"

    private fun settle(ms: Long = 800) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    private val density get() = app.resources.displayMetrics.density

    private suspend fun setup() {
        runCatching { androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().setExecutor { it.run() }.build()) }
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("José Ángel Hernández")
        c.wipeLocalData()
        val am = app.getSystemService(AudioManager::class.java)
        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 8, 0)
    }

    // ------------------------------------------------------------------ la pantalla "Ordenar" sola

    /** a · [b1 b2] (superserie S) · c · d */
    private val entries = listOf(
        ReorderEntry("a", "Alfa", "3 series"), ReorderEntry("b1", "Beta uno", group = "S"), ReorderEntry("b2", "Beta dos", group = "S"),
        ReorderEntry("c", "Gama"), ReorderEntry("d", "Delta"),
    )
    private var result: List<String>? = null
    private var dismissed = false
    private var dialogOpen by mutableStateOf(false)
    private var contentSet = false
    private fun showDialog(focus: String? = null) {
        result = null; dismissed = false
        if (!contentSet) {
            contentSet = true
            app {
                if (dialogOpen) ReorderDialog("Ordenar ejercicios", "Arrastra con ≡ o mantén presionado.", entries, onDismiss = { dismissed = true; dialogOpen = false },
                    focusKey = focus, showImages = false) { result = it; dialogOpen = false }
            }
        }
        compose.runOnUiThread { dialogOpen = true }
        waitText("Alfa"); settle()
    }
    /** La ventana de arriba (el diálogo, si está abierto): ahí van los gestos. */
    private fun top(): SemanticsNodeInteraction = compose.onAllNodes(isRoot()).let { it[it.fetchSemanticsNodes().size - 1] }
    /** Espera a que la base cumpla algo (los cambios se guardan en otro hilo). */
    private fun until(what: String, cond: suspend () -> Boolean) {
        repeat(40) { if (runBlocking { cond() }) return; settle(250) }
        fail("no pasó: $what")
    }
    /** Desplaza la lista principal hasta que se vea un texto (las listas solo dibujan lo que cabe en pantalla). */
    private fun scrollTo(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text, substring = true))
        settle(300)
    }
    private fun rowNode(title: String) = compose.onNode(hasContentDescription(title, substring = true) and hasStateDescriptionAny())
    private fun hasStateDescriptionAny() = SemanticsMatcher("tiene posición") { it.config.getOrNull(SemanticsProperties.StateDescription) != null }
    /** Lo que hace TalkBack al elegir una acción del menú de acciones. */
    private fun act(node: SemanticsNodeInteraction, label: String) {
        val acts = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnUiThread { assertTrue(label, acts.first { it.label == label }.action()) }
        compose.waitForIdle()
    }
    private fun center(title: String) = rowNode(title).fetchSemanticsNode().boundsInRoot.center
    /** El ≡ de un elemento (o "handle:S" para el de una superserie). */
    private fun handleCenter(key: String) = compose.onNodeWithTag("handle:$key", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

    /** Arrastre real con el dedo: bajar, moverse en pasos (como una persona) y soltar. */
    private fun drag(from: Offset, dy: Float, longPress: Boolean = false, release: Boolean = true) {
        top().performTouchInput {
            down(from)
            if (longPress) advanceEventTime(700)
            val steps = 24
            repeat(steps) { moveBy(Offset(0f, dy / steps)); advanceEventTime(16) }
            if (release) up()
        }
        settle(600)
    }

    @Test fun a_arrastrar_desde_el_icono() {
        showDialog()
        // Alfa baja una fila y media: queda después de la superserie (las filas de un bloque van juntas).
        val alfa = handleCenter("a")
        drag(alfa, dy = 150f * density)
        compose.onNodeWithText("Listo").performClick(); settle()
        val r = result!!
        assertTrue("Alfa ya no es el primero: $r", r.indexOf("a") > 0)
        assertEquals("la superserie sigue junta: $r", r.indexOf("b1") + 1, r.indexOf("b2"))
        assertEquals(entries.map { it.key }.toSet(), r.toSet())
    }

    @Test fun b_mantener_presionado_la_fila_y_arrastrar() {
        showDialog()
        // Delta (la última) sube manteniéndola presionada desde el nombre (no desde el ≡) hasta arriba de todo.
        drag(center("Delta"), dy = -500f * density, longPress = true)
        compose.onNodeWithText("Listo").performClick(); settle()
        val r = result!!
        assertEquals("Delta quedó arriba: $r", "d", r.first())
        assertTrue("nunca en medio de la superserie: $r", ReorderOps.isValid(r.map { k -> entries.first { it.key == k } }) { it.group })
    }

    @Test fun c_un_toque_sin_mantener_no_arrastra_la_fila() {
        showDialog()
        // Deslizar rápido sobre la fila (sin mantener) es desplazar la lista, no mover el ejercicio.
        drag(center("Delta"), dy = -300f * density, longPress = false)
        compose.onNodeWithText("Listo").performClick(); settle()
        assertEquals(entries.map { it.key }, result)
    }

    @Test fun d_superserie_se_mueve_completa_y_por_dentro_se_reacomoda() {
        showDialog()
        // Dentro: "Beta dos" sube arriba de "Beta uno".
        drag(handleCenter("b2"), dy = -80f * density)
        // Completa: desde su encabezado, la superserie baja al final.
        drag(handleCenter("S"), dy = 400f * density)
        compose.onNodeWithText("Listo").performClick(); settle()
        assertEquals(listOf("a", "c", "d", "b2", "b1"), result)
    }

    @Test fun e_con_talkback_mover_arriba_abajo_inicio_final() {
        showDialog()
        act(rowNode("Gama"), "Mover al inicio")
        settle()
        act(rowNode("Alfa"), "Mover abajo")
        settle()
        // La primera ya no ofrece "Mover arriba" y la última no ofrece "Mover abajo".
        val labels = { t: String -> rowNode(t).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label } }
        assertFalse(labels("Gama").contains("Mover arriba"))
        assertFalse(labels("Delta").contains("Mover abajo"))
        compose.onNodeWithText("Listo").performClick(); settle()
        assertEquals(listOf("c", "b1", "b2", "a", "d"), result)
    }

    @Test fun f_cancelar_pregunta_si_ya_moviste_algo() {
        showDialog()
        // Sin cambios: atrás cierra sin preguntar.
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        assertTrue(dismissed); assertNull(result)

        showDialog()
        act(rowNode("Delta"), "Mover al inicio")
        settle()
        compose.onNodeWithContentDescription("Atrás").performClick(); settle()
        compose.onNodeWithText("¿Descartar el nuevo orden?").assertExists()
        compose.onNodeWithText("Descartar").performClick(); settle()
        assertTrue(dismissed); assertNull("no se aplicó nada", result)
    }

    @Test fun g_captura_arrastrando() {
        showDialog(focus = "c")
        captureScreenRoboImage("screenshots/111_ordenar_ejercicios.png")
        // A medio arrastre (sin soltar): la fila se levanta con sombra.
        drag(handleCenter("d"), dy = -90f * density, release = false)
        captureScreenRoboImage("screenshots/112_ordenar_arrastrando.png")
        top().performTouchInput { up() }; settle()
    }

    // ------------------------------------------------------------------ en el entreno (base de datos)

    private suspend fun workoutWith(): String {
        val wid = c.workouts.startEmpty("Pierna y empuje")
        c.workouts.addExercises(wid, listOf(bench, row, squat, curl))
        val ex = c.db.workouts().exercises(wid)
        c.workouts.createSuperset(wid, listOf(ex[1].id, ex[2].id), SupersetRestMode.SHARED, 90) // [remo, sentadilla]
        c.restTimer.cancel()
        return wid
    }
    private suspend fun order(wid: String) = c.db.workouts().exercises(wid).map { it.exerciseId }

    @Test fun h_entreno_ordenar_arrastrando_se_guarda_y_se_puede_deshacer() = runBlocking {
        setup()
        val wid = workoutWith()
        val before = order(wid)
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("Press de banca")
        captureScreenRoboImage("screenshots/116_entreno_boton_ordenar.png")
        compose.onNodeWithText("Ordenar").performClick(); settle()
        waitText("Ordenar ejercicios")
        // Curl (el último) va al inicio manteniéndolo presionado.
        drag(center("Curl de bíceps con barra"), dy = -600f * density, longPress = true)
        compose.onNodeWithText("Listo").performClick(); settle(1200)
        until("curl primero") { order(wid).first() == curl }
        val after = order(wid)
        assertEquals("curl quedó primero: $after", curl, after.first())
        val ex = c.db.workouts().exercises(wid)
        assertTrue("la superserie sigue junta", ReorderOps.isValid(ex) { it.supersetId })
        assertEquals("posiciones 0..n sin huecos", ex.indices.toList(), ex.map { it.position })
        assertTrue("marcados para subir", ex.first { it.exerciseId == curl }.dirty)
        // Deshacer regresa al orden de antes.
        waitText("Orden de ejercicios cambiado")
        compose.onNodeWithText("Deshacer").performClick(); settle(1200)
        until("orden de antes") { order(wid) == before }
    }

    @Test fun i_entreno_mantener_presionado_el_nombre_abre_ordenar_y_menu_subir_bajar() = runBlocking {
        setup()
        val wid = workoutWith()
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("Press de banca")
        scrollTo("Curl de bíceps con barra")
        compose.onAllNodesWithText("Curl de bíceps con barra")[0].performTouchInput { longClick() }
        settle()
        compose.onNodeWithText("Ordenar ejercicios").assertExists()
        compose.onAllNodesWithContentDescription("Atrás").onLast().performClick(); settle()
        assertEquals("el diálogo se cerró", 1, compose.onAllNodes(isRoot()).fetchSemanticsNodes().size)

        // ⋮ del primero: "Subir" apagado; "Bajar" brinca la superserie completa (antes la partía).
        scrollTo("Press de banca con barra")
        compose.onAllNodesWithContentDescription("Opciones del ejercicio")[0].performClick(); settle()
        compose.onNodeWithText("Subir").assertIsNotEnabled()
        compose.onNodeWithText("Bajar").performClick(); settle(1000)
        until("bajó brincando la superserie") { order(wid) == listOf(row, squat, bench, curl) }
        assertTrue(ReorderOps.isValid(c.db.workouts().exercises(wid)) { it.supersetId })
    }

    @Test fun j_repositorio_no_guarda_ordenes_invalidos_ni_viejos() = runBlocking {
        setup()
        val wid = workoutWith()
        val ex = c.db.workouts().exercises(wid)
        val ids = ex.map { it.id }
        // Partir la superserie [remo, sentadilla] → no se guarda.
        assertFalse(c.workouts.reorder(wid, listOf(ids[1], ids[0], ids[2], ids[3])))
        // Lista vieja (falta uno) → no se guarda.
        assertFalse(c.workouts.reorder(wid, ids.dropLast(1)))
        assertEquals(ids, c.db.workouts().exercises(wid).map { it.id })
        // Mismo orden: no marca nada para subir.
        val dirtyBefore = c.db.workouts().exercises(wid).map { it.updatedAt }
        assertTrue(c.workouts.reorder(wid, ids))
        assertEquals(dirtyBefore, c.db.workouts().exercises(wid).map { it.updatedAt })
    }

    // ------------------------------------------------------------------ deslizar una serie

    private fun setRowY(spoken: String): Float = compose.onAllNodesWithContentDescription(spoken, substring = true)[0].fetchSemanticsNode().boundsInRoot.center.y

    @Test fun k_deslizar_a_la_izquierda_elimina_la_serie_y_se_puede_deshacer() = runBlocking {
        setup()
        val wid = c.workouts.startEmpty("Pecho"); c.workouts.addExercises(wid, listOf(bench))
        val sets = c.db.workouts().sets(wid)
        assertEquals(3, sets.size)
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("Press de banca")
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val y = setRowY("Serie 2 de Press de banca")

        // Hacia la derecha: no hace nada.
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.3f, y), Offset(width * 0.95f, y), 300) }; settle()
        assertEquals(3, c.db.workouts().sets(wid).size)
        // Poquito a la izquierda (menos del 40 %): regresa y no borra.
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.6f, y), Offset(width * 0.45f, y), 600) }; settle()
        assertEquals(3, c.db.workouts().sets(wid).size)

        // A la izquierda de verdad: se elimina la serie 2.
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.9f, y), Offset(width * 0.1f, y), 250) }; settle(1200)
        until("se eliminó la serie") { c.db.workouts().sets(wid).size == 2 }
        val left = c.db.workouts().sets(wid)
        assertEquals(2, left.size)
        assertFalse(left.any { it.id == sets[1].id })
        waitText("Serie eliminada")
        captureScreenRoboImage("screenshots/113_serie_deslizada_deshacer.png")
        compose.onNodeWithText("Deshacer").performClick(); settle(1200)
        until("volvió la serie") { c.db.workouts().sets(wid).map { it.id } == sets.map { it.id } }
    }

    @Test fun l_talkback_eliminar_serie_como_accion() = runBlocking {
        setup()
        val wid = c.workouts.startEmpty("Pecho"); c.workouts.addExercises(wid, listOf(bench))
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("Press de banca")
        act(compose.onAllNodesWithContentDescription("Serie 3 de Press de banca", substring = true)[0], "Eliminar serie")
        until("se eliminó con TalkBack") { c.db.workouts().sets(wid).size == 2 }
    }

    @Test fun m_a_medio_deslizar_se_ve_el_rojo() = runBlocking {
        setup()
        val wid = c.workouts.startEmpty("Pecho"); c.workouts.addExercises(wid, listOf(bench))
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("Press de banca")
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val y = setRowY("Serie 1 de Press de banca")
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.85f, y)); repeat(20) { moveBy(Offset(-width * 0.025f, 0f)); advanceEventTime(16) }
        }
        settle(300)
        captureScreenRoboImage("screenshots/114_deslizar_serie.png")
        compose.onRoot().performTouchInput { up() }; settle()
    }

    // ------------------------------------------------------------------ rutinas

    private suspend fun routines(vararg names: String): List<String> {
        val uid = c.auth.currentUserId!!
        return names.mapIndexed { i, n ->
            val t = System.currentTimeMillis() + i
            val r = RoutineEntity(Ids.uuidV7(t), uid, n, null, i, t, t)
            c.routines.save(r, listOf(RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, bench, 0, null, 0, null, 90, 3, 8, 12, null, t)))
            r.id
        }
    }

    @Test fun n_rutinas_ordenar_desde_inicio_cambia_cual_toca() = runBlocking {
        setup()
        val ids = routines("Empuje", "Jalón", "Pierna")
        app { HomeScreen(rememberNavController()) }
        scrollTo("Pierna")
        compose.onNodeWithText("Pierna").performTouchInput { longClick() }
        settle()
        waitText("Ordenar rutinas")
        captureScreenRoboImage("screenshots/115_ordenar_rutinas.png")
        act(rowNode("Pierna"), "Mover al inicio")
        settle()
        compose.onNodeWithText("Listo").performClick(); settle(1200)
        until("Pierna primero") { c.routines.routines.first().first().name == "Pierna" }
        val now = c.routines.routines.first()
        assertEquals(listOf("Pierna", "Empuje", "Jalón"), now.map { it.name })
        assertEquals(listOf(0, 1, 2), now.map { it.position })
        assertEquals(ids.toSet(), now.map { it.id }.toSet())
        // Sin entrenos todavía, la que toca es la primera del orden.
        assertEquals(now.first().id, com.rupio.app.domain.RoutineRotation.next(now.map { it.id }, emptyList()))
        assertTrue(now.first().dirty)
        // Si la lista cambió mientras tanto (se borró una), no se guarda nada.
        assertFalse(c.routines.reorder(ids.take(2)))
    }

    @Test fun o_editor_de_rutina_ordenar_y_guardar() = runBlocking {
        setup()
        val uid = c.auth.currentUserId!!
        val t = System.currentTimeMillis()
        val r = RoutineEntity(Ids.uuidV7(t), uid, "Completa", null, 0, t, t)
        val items = listOf(bench, row, squat).mapIndexed { i, e -> RoutineExerciseEntity(Ids.uuidV7(t + i), r.id, uid, e, i, null, 0, null, 90, 3, 8, 12, null, t) }
        c.routines.save(r, items)
        app { RoutineEditorScreen(rememberNavController(), r.id) }
        waitText("Press de banca")
        scrollTo("Ordenar")
        captureScreenRoboImage("screenshots/117_rutina_boton_ordenar.png")
        compose.onNodeWithText("Ordenar").performClick(); settle()
        waitText("Ordenar ejercicios")
        val squatKey = c.routines.exercises(r.id).first { it.exerciseId == squat }.id
        drag(handleCenter(squatKey), dy = -400f * density)
        compose.onNodeWithText("Listo").performClick(); settle()
        compose.onNodeWithText("Guardar").performClick(); settle(1200)
        until("sentadilla primero") { c.routines.exercises(r.id).first().exerciseId == squat }
    }
}
