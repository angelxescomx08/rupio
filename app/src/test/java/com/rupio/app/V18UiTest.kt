package com.rupio.app

import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.rupio.app.core.Ids
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.billing.SupportState
import com.rupio.app.data.billing.WallEntry
import com.rupio.app.data.billing.WallMe
import com.rupio.app.data.billing.WallState
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.domain.SupportTier
import com.rupio.app.domain.Tracking
import com.rupio.app.ui.history.WorkoutDetailScreen
import com.rupio.app.ui.support.ThanksDialog
import com.rupio.app.ui.support.WallAccess
import com.rupio.app.ui.support.WallContent
import com.rupio.app.ui.support.WallNameDialog
import com.rupio.app.ui.support.WallRaisedDialog
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.workout.ActiveWorkoutScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** v1.8: pantallas nuevas y arreglos visuales (capturas en app/screenshots/8x–9x). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rMX-w393dp-h852dp-night-xhdpi")
class V18UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val zone = ZoneId.of("America/Mexico_City")
    private val lb = WeightUnit.LB
    private val bench = "press_de_banca_con_barra"
    private val row = "remo_con_barra"

    private fun settle(ms: Long = 800) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("screenshots/$name.png") }
    private fun screen(name: String) { settle(); captureScreenRoboImage("screenshots/$name.png") }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }

    private suspend fun setup() {
        runCatching { androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().setExecutor { it.run() }.build()) }
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("José Ángel Hernández")
        c.wipeLocalData()
        c.settings.setUnit(lb)
        // Volumen normal (Robolectric empieza en 0 y saldría el aviso de "no se va a oír").
        val am = app.getSystemService(AudioManager::class.java)
        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 8, 0)
    }

    /** Seis sesiones pasadas de press de banca subiendo de peso (para la gráfica) y remo. */
    private suspend fun seedHistory() {
        val user = c.auth.currentUserId!!
        val today = Dates.today(zone).toEpochDay().toInt()
        listOf(42 to 50.0, 35 to 52.5, 28 to 55.0, 21 to 55.0, 14 to 57.5, 7 to 60.0).forEach { (ago, kg) ->
            val day = today - ago
            val start = day * 86_400_000L + 18 * 3_600_000L
            val w = WorkoutEntity(Ids.uuidV7(start), user, null, "Pecho y espalda", start, start + 3_900_000, zone.id, day, 3900,
                totalVolumeKg = kg * 8 * 3 + 50.0 * 10 * 3, totalSets = 6, totalReps = 54, updatedAt = start, dirty = false)
            val b = WorkoutExerciseEntity(Ids.uuidV7(start), w.id, user, bench, 0, updatedAt = start, dirty = false)
            val r = WorkoutExerciseEntity(Ids.uuidV7(start), w.id, user, row, 1, updatedAt = start, dirty = false)
            val sets = (0 until 3).map { i -> WorkoutSetEntity(Ids.uuidV7(start), b.id, w.id, user, bench, i, weightKg = kg, reps = 8, completed = true, completedAt = start,
                performedOn = day, isPr = ago == 7 && i == 0, updatedAt = start, dirty = false) } +
                (0 until 3).map { i -> WorkoutSetEntity(Ids.uuidV7(start), r.id, w.id, user, row, i, weightKg = 50.0, reps = 10, completed = true, completedAt = start,
                    performedOn = day, updatedAt = start, dirty = false) }
            c.db.workouts().insertFull(w, listOf(b, r), sets)
        }
    }

    private fun bounds(desc: String) = compose.onAllNodesWithContentDescription(desc, substring = true)[0].fetchSemanticsNode().boundsInRoot

    /** Kilos dentro de la cajita, todas las cajitas a la misma altura, aire en la zona verde y la gráfica del ejercicio. */
    @Test fun a_entreno_kilos_alineados_y_grafica() = runBlocking {
        setup(); seedHistory()
        val wid = c.workouts.startEmpty("Pecho y espalda")
        c.workouts.addExercises(wid, listOf(bench, row))
        val first = c.db.workouts().sets(wid).first { it.exerciseId == bench }
        c.workouts.editSet(first.id) { it.copy(weightKg = lb.toKg(135.0), reps = 8) }
        c.workouts.toggleComplete(first.id, Tracking.WEIGHT_REPS, null, null, null)
        c.restTimer.cancel()
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("Press de banca"); waitText("61.2 kg")
        shot("80_entreno_kg_alineado")

        // La cajita del peso, la de reps y la ✓ miden lo mismo y empiezan a la misma altura.
        val w = bounds("Peso en libras, serie 1, Press de banca"); val r = bounds("Repeticiones, serie 1, Press de banca"); val k = bounds("Desmarcar serie 1 de Press de banca")
        assertEquals("misma altura peso/reps", w.height, r.height, 1f); assertEquals("misma altura peso/✓", w.height, k.height, 1f)
        assertEquals("mismo arriba", w.top, r.top, 1f); assertEquals(w.top, k.top, 1f)
        compose.onNodeWithText("61.2 kg").assertExists()            // los kilos van dentro de la cajita

        compose.onAllNodesWithText("Progreso")[0].performClick(); settle(1200)
        waitText("Peso máx.")
        compose.onAllNodesWithText("Progreso")[0].performScrollTo()
        shot("81_entreno_grafica_peso")
        compose.onAllNodes(hasText("Volumen") and hasClickAction())[0].performClick(); settle(800)
        shot("82_entreno_grafica_volumen")
    }

    /** Antes, dentro de una superserie, las cajitas se estiraban a cientos de dp (IntrinsicSize + fillMaxSize). */
    @Test fun b_superserie_sin_cajitas_estiradas() = runBlocking {
        setup()
        val wid = c.workouts.startEmpty("Superserie")
        c.workouts.addExercises(wid, listOf(bench, row))
        val ids = c.db.workouts().exercises(wid).map { it.id }
        c.workouts.createSuperset(wid, ids, com.rupio.app.domain.SupersetRestMode.SHARED, 90)
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("Press de banca")
        val h = bounds("Peso en libras, serie 1, Press de banca").height
        val density = app.resources.displayMetrics.density
        assertTrue("la cajita mide ~50 dp (midió ${h / density} dp)", h / density < 70f)
        shot("83_entreno_superserie")
    }

    /** Terminar: calorías estimadas y reparto de músculos. */
    @Test fun c_terminar_calorias_y_musculos() = runBlocking {
        setup()
        c.settings.setHealthConsent(System.currentTimeMillis())
        c.bodyWeight.log(Dates.today(zone), 82.0)
        val wid = c.workouts.startEmpty("Pecho y espalda")
        c.workouts.addExercises(wid, listOf(bench, row))
        c.db.workouts().sets(wid).forEach { s ->
            c.workouts.editSet(s.id) { it.copy(weightKg = 60.0, reps = 8) }
            c.workouts.toggleComplete(s.id, Tracking.WEIGHT_REPS, null, null, null)
        }
        c.restTimer.cancel()
        // Empezó hace 50 min (las calorías se estiman con la duración).
        c.db.workouts().get(wid)!!.let { c.db.workouts().upsertWorkout(it.copy(startedAt = System.currentTimeMillis() - 50 * 60_000L)) }
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("Terminar")
        compose.onNodeWithText("Terminar").performClick(); settle(1500)
        waitText("Reparto de músculos")
        compose.onNodeWithText("Calorías").assertExists()
        compose.onNodeWithText("Pecho", substring = false).assertExists()
        screen("84_terminar_calorias_musculos")
        compose.onNodeWithText("≈", substring = true).performClick(); settle()
        waitText("¿Cómo se calculan?")
        screen("85_calorias_explicacion")
    }

    /** Detalle: calorías, reparto y el kg en su columna aunque haya 🏆. */
    @Test fun d_detalle_calorias_musculos_y_kg_alineado() = runBlocking {
        setup(); seedHistory()
        val last = c.db.workouts().historyPage(Int.MAX_VALUE, Long.MAX_VALUE, 1).first()
        app { WorkoutDetailScreen(rememberNavController(), last.id, 0) }
        waitText("Calorías")
        shot("86_detalle_calorias")
        compose.onNodeWithText("Reparto de músculos").performScrollTo()
        waitText("Pecho")
        shot("87_detalle_reparto_y_kg")
        // Los kilos de las series de press de banca terminan en la misma x, con o sin trofeo.
        compose.onAllNodesWithText("60 kg", useUnmergedTree = true)[2].performScrollTo(); settle()
        val kg = compose.onAllNodesWithText("60 kg", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.right }
        assertTrue(kg.size >= 3)
        assertTrue("kg alineados: $kg", kg.max() - kg.min() < 1f)
    }

    /** El aviso de "no vas a oír el fin del descanso" sale con el volumen en 0 y se va al subirlo. */
    @Test fun e_aviso_volumen_en_cero() = runBlocking {
        setup()
        val am = app.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        val wid = c.workouts.startEmpty("Pierna"); c.workouts.addExercises(wid, listOf("sentadilla_con_barra"))
        app { ActiveWorkoutScreen(rememberNavController()) }
        waitText("No vas a oír el fin del descanso")
        shot("88_aviso_volumen_cero")
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 8, 0)
        settle(3500)
        compose.waitUntil(8_000) { compose.onAllNodesWithText("No vas a oír", substring = true).fetchSemanticsNodes().isEmpty() }
    }

    // ------------------------------------------------------------------ muro

    private val entries = listOf(
        WallEntry(1, "Carolina M.", ms("2026-10-02"), false), WallEntry(2, "El Profe Toño", ms("2026-10-19"), false),
        WallEntry(3, "José Ángel", ms("2026-11-17"), true), WallEntry(4, "Fer del gym", ms("2026-11-03"), false), WallEntry(5, "Mau", ms("2026-11-10"), false),
    )
    private fun ms(d: String) = java.time.LocalDate.parse(d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val support = SupportState()

    @Test fun f_muro_sin_cuenta() {
        app { WallContent(WallState(), support, WallAccess.LOCAL_ONLY, onJoin = {}, onBoost = {}, onRename = {}, onHide = {}, onCreateAccount = {}, onRetry = {}) }
        waitText("Crear cuenta y conservar mis datos")
        shot("89_muro_sin_cuenta")
    }

    @Test fun g_muro_unete() {
        val list = entries.map { it.copy(me = false) }.filter { it.rank != 3 }.mapIndexed { i, e -> e.copy(rank = i + 1) }
        app { WallContent(WallState(list, null, loaded = true), support, WallAccess.READY, onJoin = {}, onBoost = {}, onRename = {}, onHide = {}, onCreateAccount = {}, onRetry = {}) }
        waitText("¿Quieres aparecer aquí?")
        shot("90_muro_unete")
    }

    @Test fun h_muro_mecenas_con_lugar() {
        val me = WallMe("José Ángel", 598, 3, 152, hidden = false, blocked = false, sinceMs = ms("2026-11-17"), total = 5)
        var boosted: com.rupio.app.domain.WallBoost? = null
        app { WallContent(WallState(entries, me, loaded = true), support, WallAccess.READY, onJoin = {}, onBoost = { boosted = it }, onRename = {}, onHide = {}, onCreateAccount = {}, onRetry = {}) }
        waitText("Te faltan 152 puntos para pasar al lugar #2")
        shot("91_muro_mecenas")
        compose.onNodeWithContentDescription("suma 249 puntos", substring = true).performClick()
        assertEquals("se sugiere y se compra el aporte que alcanza", com.rupio.app.domain.WallBoost.MEDIUM, boosted)
    }

    /**
     * Nombre + casilla de permiso. En Robolectric el diálogo enfoca el campo y el cursor parpadea sin fin (Compose nunca
     * queda "idle"), así que se dibuja sin esperar. La regla de la casilla y del nombre se prueba en V18Test.
     */
    @Test fun i_muro_nombre_y_permiso() {
        compose.activity.setContent { RupioTheme { WallNameDialog("Ser Mecenas · \$499 MXN", "Escribe cómo quieres aparecer. Después de pagar entras al muro con este nombre.",
            "José Ángel", "Continuar al pago", askConsent = true, onDismiss = {}) {} } }
        repeat(8) { ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) }
        captureScreenRoboImage("screenshots/92_muro_nombre_permiso.png")
    }

    @Test fun j_gracias_mecenas_y_subiste() {
        app { ThanksDialog(SupportTier.MECENAS, SupportState(owned = setOf(SupportTier.MECENAS)), {}, {}, {}, wallRank = 3, onSeeWall = {}) }
        waitText("¡Ya estás en el muro! Lugar #3")
        settle(3500)
        screen("93_gracias_mecenas")
    }

    @Test fun k_subiste_de_lugar() {
        app { WallRaisedDialog(2, 3, onSeeWall = {}, onClose = {}) }
        waitText("Subiste del lugar #3 al #2")
        settle(3500)
        screen("94_muro_subiste")
    }

    @Test fun l_apoya_con_mecenas() = runBlocking {
        setup()
        app { com.rupio.app.ui.support.SupportScreen(rememberNavController()) }
        waitText("Un café")
        compose.onNodeWithText("Mecenas").performScrollTo()
        shot("95_apoya_mecenas")
        compose.onNodeWithText("Mecenas").performClick(); settle()
        compose.onNodeWithText("Apoyar con", substring = true).performScrollTo().performClick(); settle()
        waitText("Necesitas una cuenta")                                          // modo sin cuenta → primero la cuenta
    }
}
