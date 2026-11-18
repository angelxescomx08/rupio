package com.rupio.app

import androidx.test.core.app.ApplicationProvider
import com.rupio.app.core.Ids
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.domain.Accent
import com.rupio.app.domain.SupportCopy
import com.rupio.app.domain.SupportPolicy
import com.rupio.app.domain.SupportTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow
import androidx.compose.ui.graphics.Color
import com.rupio.app.ui.theme.RupioColors

/** Apoyo voluntario (reglas, colores, recompensas) y "Cerrar sesión" por pasos, incluido sin internet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupportAndSignOutTest {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container

    // ------------------------------------------------------------------ reglas del mensaje

    @Test fun mensaje_solo_desde_el_tercer_entreno_y_una_vez_por_entreno() {
        assertFalse(SupportPolicy.shouldPrompt(false, 1, "w1", null))
        assertFalse(SupportPolicy.shouldPrompt(false, 2, "w2", null))
        assertTrue(SupportPolicy.shouldPrompt(false, 3, "w3", null))
        assertTrue(SupportPolicy.shouldPrompt(false, 40, "w40", "w39"))
        assertFalse("girar el teléfono no lo repite", SupportPolicy.shouldPrompt(false, 3, "w3", "w3"))
        assertFalse("quien apoya nunca lo ve", SupportPolicy.shouldPrompt(true, 99, "w99", null))
    }

    @Test fun niveles_y_textos() {
        assertEquals(SupportTier.FAN, SupportTier.best(setOf(SupportTier.CAFE, SupportTier.FAN)))
        assertNull(SupportTier.best(emptySet()))
        assertEquals(SupportTier.LICUADO, SupportTier.RECOMMENDED)
        // v1.8: 4 niveles (Mecenas es el cuarto, con el muro de agradecimientos).
        assertEquals(4, SupportTier.entries.map { it.productId }.toSet().size)
        // Sin culpa, sin "el más elegido" inventado, y la libertad explícita.
        val all = listOf(SupportCopy.TITLE, SupportCopy.story(5, "1,000 lb"), SupportCopy.ASK, SupportCopy.FREEDOM).joinToString(" ").lowercase()
        listOf("culpa", "más elegido", "más popular", "no quiero", "egoísta").forEach { assertFalse(it, it in all) }
        assertTrue("gratis" in all && "tú decides" in all && "una sola vez" in all)
        assertEquals("Ya llevas 1 entrenamiento", SupportCopy.story(1, "x").substringAfter("libres. ").substringBefore(" y "))
    }

    @Test fun colores_desbloqueo_y_contraste() {
        assertTrue(Accent.AZUL.unlockedBy(null))
        assertFalse(Accent.ROSA.unlockedBy(SupportTier.CAFE))
        assertTrue(Accent.ROSA.unlockedBy(SupportTier.LICUADO))
        assertFalse(Accent.DORADO.unlockedBy(SupportTier.LICUADO))
        assertTrue(Accent.DORADO.unlockedBy(SupportTier.FAN))
        assertEquals("tras un reembolso vuelve al azul", Accent.AZUL, Accent.effective(Accent.DORADO, SupportTier.CAFE))
        // Texto de los botones (OnPrimary) sobre cada acento, y cada acento sobre el fondo negro: WCAG AA.
        fun lum(argb: Long): Double {
            fun ch(v: Long) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * ch(argb shr 16 and 0xFF) + 0.7152 * ch(argb shr 8 and 0xFF) + 0.0722 * ch(argb and 0xFF)
        }
        fun ratio(a: Long, b: Long) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)
        for (a in Accent.entries) {
            assertTrue("${a.label} con texto oscuro", ratio(a.argb, 0xFF0B1020) >= 4.5)
            assertTrue("${a.label} sobre negro", ratio(a.argb, 0xFF000000) >= 4.5)
        }
    }

    @Test fun recompensas_simuladas_cambian_el_color_y_se_pueden_quitar() {
        val s = c.support
        s.debugReset()
        assertFalse(s.state.value.isSupporter)
        s.setAccent(Accent.ROSA)                                   // bloqueado: no cambia
        assertEquals(Accent.AZUL, s.state.value.effectiveAccent)
        s.debugGrant(SupportTier.LICUADO)
        assertTrue(s.state.value.isSupporter)
        s.setAccent(Accent.ROSA)
        assertEquals(Accent.ROSA, s.state.value.effectiveAccent)
        assertEquals(Color(0xFFFF7AB4), RupioColors.Primary)
        s.setGoldIcon(true)                                        // solo nivel Fan
        assertFalse(s.state.value.goldIcon)
        s.debugReset()
        assertEquals("sin el nivel, vuelve al azul", Accent.AZUL, s.state.value.effectiveAccent)
        assertEquals(Color(0xFF5B8CFF), RupioColors.Primary)
    }

    // ------------------------------------------------------------------ cerrar sesión

    @Before fun setUp() = runBlocking { c.auth.useLocalMode("QA Cierre") }

    private suspend fun seedFinishedDirtyWorkout() {
        val uid = c.auth.currentUserId!!
        val t = System.currentTimeMillis()
        c.db.workouts().upsertWorkout(WorkoutEntity(Ids.uuidV7(t), uid, null, "Pierna", t - 3_600_000, t, "America/Mexico_City", 20_000, 3600, updatedAt = t))
    }

    private suspend fun waitFor(ms: Long = 10_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) { assertTrue("se agotó el tiempo esperando", System.currentTimeMillis() < end); delay(20) }
    }

    @Test fun cerrar_sesion_local_pasa_por_todos_los_pasos_y_borra() = runBlocking {
        seedFinishedDirtyWorkout()
        val ctl = SignOutController(c)
        val seen = mutableSetOf<SignOutStep>()
        ctl.start(localOnly = true)
        waitFor { seen += ctl.state.value.step; !ctl.state.value.active }
        assertTrue(seen.containsAll(listOf(SignOutStep.WIPE, SignOutStep.SIGN_OUT, SignOutStep.DONE)))
        assertEquals(AuthState.SignedOut, c.auth.state.value)
        assertEquals(0, c.db.admin().finishedCount())
    }

    @Test fun sin_internet_pregunta_antes_de_borrar_y_cancelar_no_toca_nada() = runBlocking {
        seedFinishedDirtyWorkout()
        val ctl = SignOutController(c, uploadTimeoutMs = 2_000) { false }   // la subida falla (sin red)
        ctl.start(localOnly = false)
        waitFor { ctl.state.value.uploadFailed }
        assertEquals(1, ctl.state.value.pendingLeft)
        assertFalse(ctl.state.value.serverRejected)
        assertTrue("no se borra nada sin preguntar", c.auth.state.value is AuthState.SignedIn)
        assertEquals(1, c.db.admin().finishedCount())
        ctl.cancel()
        assertFalse(ctl.state.value.active)
        assertEquals(1, c.db.admin().finishedCount())
        // Segundo intento: ahora sí sale aunque se pierda el cambio.
        ctl.start(localOnly = false)
        waitFor { ctl.state.value.uploadFailed }
        ctl.exitWithoutUpload()
        waitFor { !ctl.state.value.active }
        assertEquals(AuthState.SignedOut, c.auth.state.value)
        assertEquals(0, c.db.admin().finishedCount())
    }

    @Test fun subida_que_se_cuelga_no_deja_la_pantalla_atorada() = runBlocking {
        seedFinishedDirtyWorkout()
        val ctl = SignOutController(c, uploadTimeoutMs = 300) { delay(60_000); true }
        val t0 = System.currentTimeMillis()
        ctl.start(localOnly = false)
        waitFor(5_000) { ctl.state.value.uploadFailed }
        assertTrue("avisa rápido, no a los 45 s", System.currentTimeMillis() - t0 < 3_000)
        ctl.exitWithoutUpload()
        waitFor(5_000) { !ctl.state.value.active }
        assertEquals(AuthState.SignedOut, c.auth.state.value)
    }

    @Test fun si_la_subida_funciona_no_pregunta_nada() = runBlocking {
        seedFinishedDirtyWorkout()
        val ctl = SignOutController(c) { c.db.workouts().dirtyWorkouts(100).forEach { c.db.workouts().cleanWorkout(it.id, it.updatedAt) }; true }
        var asked = false
        ctl.start(localOnly = false)
        waitFor { if (ctl.state.value.uploadFailed) asked = true; !ctl.state.value.active }
        assertFalse(asked)
        assertEquals(AuthState.SignedOut, c.auth.state.value)
    }
}
