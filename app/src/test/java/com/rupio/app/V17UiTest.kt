package com.rupio.app

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.rupio.app.data.local.BodyWeightEntity
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.ui.auth.AuthScreen
import com.rupio.app.ui.auth.VerifyEmailScreen
import com.rupio.app.ui.body.HEALTH_CONSENT_TEXT
import com.rupio.app.ui.body.LogWeightDialog
import com.rupio.app.ui.components.StatTile
import com.rupio.app.ui.history.EditWorkoutScreen
import com.rupio.app.ui.profile.ProfileScreen
import com.rupio.app.ui.theme.RupioTheme
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

/** v1.7: las pantallas nuevas (editar entreno, casilla de consentimiento, código del correo, exportar, aviso al juntar). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rMX-w393dp-h852dp-night-xhdpi")
class V17UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val zone = ZoneId.of("America/Mexico_City")

    private fun settle(ms: Long = 800) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("screenshots/$name.png") }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    private fun poll(what: String, tries: Int = 60, cond: suspend () -> Boolean) = runBlocking {
        repeat(tries) { if (cond()) return@runBlocking; settle(100); Thread.sleep(20) }
        fail(what)
    }

    private suspend fun setup() {
        runCatching { androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().setExecutor { it.run() }.build()) }
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("José Ángel Hernández")
        c.wipeLocalData()
        c.settings.setBodyUnit(WeightUnit.KG); c.settings.setUnit(WeightUnit.KG)
    }

    /** N1: el editor de un entreno terminado; desde "Revisar" → "Corregir" la serie rechazada se marca y al guardar se reintenta. */
    @Test fun n1_editar_entreno_terminado_y_corregir() = runBlocking {
        setup()
        val user = c.auth.currentUserId!!
        val start = System.currentTimeMillis() - 86_400_000L; val day = Dates.localEpochDay(start, zone)
        val w = WorkoutEntity(Ids.uuidV7(start), user, null, "Pecho y espalda", start, start + 3_600_000, zone.id, day, 3600, updatedAt = start, dirty = false)
        val bench = WorkoutExerciseEntity(Ids.uuidV7(start), w.id, user, "press_de_banca_con_barra", 0, updatedAt = start, dirty = false)
        val row = WorkoutExerciseEntity(Ids.uuidV7(start), w.id, user, "remo_con_barra", 1, updatedAt = start, dirty = false)
        val sets = listOf(bench to 60.0, bench to 60.0, row to 50.0).mapIndexed { i, (we, kg) ->
            WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, user, we.exerciseId, i, weightKg = kg, reps = 8, completed = true, completedAt = start, performedOn = day, updatedAt = start, dirty = false)
        }
        c.db.workouts().insertFull(w, listOf(bench, row), sets)
        c.sync.rejectedStore.add("workout_sets", sets[0].id)          // el servidor la había rechazado

        app { EditWorkoutScreen(rememberNavController(), w.id, focusSet = sets[0].id) }
        waitText("Editar entreno"); waitText("Agregar serie")
        shot("70_editar_entreno")
        compose.onNode(hasSetTextAction() and hasContentDescription("Repeticiones, serie 1, Press de banca", substring = true)).performTextReplacement("12")
        compose.onNodeWithContentDescription("Borrar serie 2 de Press de banca", substring = true).performClick()
        settle()
        compose.onNodeWithText("Guardar").performClick()
        poll("se guardó") { c.db.workouts().getSet(sets[0].id)?.reps == 12 }
        assertTrue(c.db.workouts().getSet(sets[1].id)!!.deleted)
        assertTrue(c.db.workouts().get(w.id)!!.dirty)
        assertEquals("volumen recalculado", 60.0 * 12 + 50.0 * 8, c.db.workouts().get(w.id)!!.totalVolumeKg, 1e-9)
        poll("la serie corregida se vuelve a intentar") { sets[0].id !in c.sync.rejectedStore.keys("workout_sets") }
    }

    /** N2: la primera vez que registras tu peso se pide permiso con una casilla; sin marcarla no se guarda. */
    @Test fun n2_casilla_de_consentimiento_del_peso() = runBlocking {
        setup()
        var saved: Double? = null
        app { LogWeightDialog(80.0, WeightUnit.KG, onDismiss = {}) { _, kg -> saved = kg } }
        waitText(HEALTH_CONSENT_TEXT)
        // v1.9: "Guardar" sin la casilla no guarda, pero ya no está gris sin explicación: dice que falta marcarla.
        compose.onNodeWithText("Guardar").performClick(); settle()
        assertNull("sin la casilla no se guarda", saved)
        waitText(com.rupio.app.ui.body.HEALTH_CONSENT_NUDGE)
        settle(); captureScreenRoboImage("screenshots/71_consentimiento_peso.png")
        compose.onNode(hasText(HEALTH_CONSENT_TEXT, substring = true) and hasClickAction()).performClick(); settle()
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick(); settle()
        assertEquals(80.0, saved!!, 0.0)
        poll("se guardó la fecha del consentimiento") { c.settings.current().healthConsentAt != null }
    }

    /** N2 + N9: en Perfil se retira el permiso (se borra el peso) y se exportan los datos con el menú de compartir. */
    @Test fun n2_n9_perfil_retirar_permiso_y_exportar() = runBlocking {
        setup()
        c.giveHealthConsent()
        val user = c.auth.currentUserId!!; val day = Dates.today(zone).toEpochDay().toInt()
        c.db.bodyWeights().upsertAll(listOf(BodyWeightEntity(user, day, 80.0, System.currentTimeMillis())))
        app { ProfileScreen(rememberNavController()) }
        waitText("Permiso de peso corporal")
        compose.onNodeWithText("Exportar mis datos").performScrollTo()
        shot("73_perfil_cuenta_y_datos")

        compose.onNode(hasText("Permiso de peso corporal") and hasClickAction()).performScrollTo().performClick(); settle()
        waitText("¿Retirar tu permiso?")
        compose.onNodeWithText("Retirar y borrar").performClick(); settle()
        poll("se retiró y se borró el peso") { c.settings.current().healthConsentAt == null && c.db.admin().exportBodyWeights().isEmpty() }
        waitText("No lo has dado")

        // FileProvider solo reconoce rutas con "/" (las de Android); en Windows Robolectric arma la caché con "\".
        org.junit.Assume.assumeTrue("FileProvider necesita rutas con /: corre esta parte en Linux o en CI", java.io.File.separatorChar == '/')
        compose.onNode(hasText("Exportar mis datos") and hasClickAction()).performScrollTo().performClick()
        var started: Intent? = null
        // Los CSV se escriben en segundo plano (Dispatchers.IO) y luego se abre el menú de compartir.
        poll("se abrió el menú de compartir", tries = 200) { started = shadowOf(app).nextStartedActivity; started != null }
        assertEquals(3, java.io.File(app.cacheDir, "export").listFiles()!!.size)
        assertEquals(Intent.ACTION_CHOOSER, started!!.action)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, started!!.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!.action)
    }

    /** N6: la pantalla del código de 6 dígitos (con "Reenviar" en espera y salida si el correo estaba mal). */
    @Test fun n6_pantalla_del_codigo_del_correo() = runBlocking {
        setup()
        app { VerifyEmailScreen("ana@ejemplo.com", newAccount = true, sentAt = System.currentTimeMillis()) }
        waitText("Confirma tu correo"); waitText("ana@ejemplo.com")
        waitText("Reenviar código en")
        compose.onNode(hasText("Reenviar código en", substring = true)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("12a")
        assertEquals("solo dígitos", "12", compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        shot("72_confirmar_correo")
        compose.onNodeWithText("¿Correo equivocado? Usar otro correo").performClick(); settle()
        waitText("todavía está vacía")
    }

    @Test fun n6_error_inicial_de_envio_muestra_reintento_inmediato() = runBlocking {
        setup()
        val message = "El servidor no responde (código 503). Inténtalo en unos minutos."
        app { VerifyEmailScreen("ana@ejemplo.com", initialSendError = message) }

        waitText(message)
        compose.onNodeWithText("Reenviar código").assertIsEnabled()
        compose.onNodeWithText("Te enviamos un código", substring = true).assertDoesNotExist()
    }

    /** N3: al entrar con una cuenta que ya existe desde el modo sin cuenta, se avisa que se juntan los datos. */
    @Test fun n3_aviso_se_juntaran_con_tu_cuenta() = runBlocking {
        setup()
        app { AuthScreen(upgrade = true, onBack = {}) }
        waitText("Respalda tus datos")
        assertTrue("al crear cuenta no aplica", compose.onAllNodesWithText("Se juntarán", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("¿Ya tienes cuenta? Inicia sesión").performScrollTo().performClick(); settle()
        waitText("Se juntarán con lo que ya tienes en tu cuenta")
        shot("74_entrar_y_juntar")
    }
}

/** N7: con letra al 200 % en una pantalla angosta, las tarjetas usan hasta 3 líneas y no bajan de ~150 %. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rMX-w320dp-h640dp-night-xhdpi", fontScale = 2.0f)
class V17FitTextUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun n7_tarjetas_con_letra_al_200() {
        var base = 0f
        compose.setContent {
            RupioTheme { Surface(color = MaterialTheme.colorScheme.background) {
                base = MaterialTheme.typography.labelMedium.fontSize.value
                androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth()) {
                    StatTile("Repeticiones totales", "1 234", androidx.compose.ui.Modifier.weight(1f))
                    StatTile("Volumen", "156.5 mil lb", androidx.compose.ui.Modifier.weight(1f))
                }
            } }
        }
        compose.waitForIdle()
        val node = compose.onNodeWithText("Repeticiones totales").fetchSemanticsNode()
        val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        val l = layout.first()
        assertTrue("hasta 3 líneas: ${l.lineCount}", l.lineCount in 1..3)
        assertFalse("no se corta", l.hasVisualOverflow)
        assertTrue("no baja de 150 % del normal: ${l.layoutInput.style.fontSize} (base $base sp a 200 %)",
            l.layoutInput.style.fontSize.value >= base * 0.75f - 0.01f)
        compose.onRoot().captureRoboImage("screenshots/75_tarjetas_letra_200.png")
    }
}
