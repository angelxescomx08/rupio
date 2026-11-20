package com.rupio.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.league.League
import com.rupio.app.data.league.LeagueGlobalMe
import com.rupio.app.data.league.LeagueRow
import com.rupio.app.data.league.LeagueState
import com.rupio.app.data.league.TopRow
import com.rupio.app.domain.LeagueCopy
import com.rupio.app.domain.LeagueRules
import com.rupio.app.domain.SupportTier
import com.rupio.app.ui.support.LeagueCardContent
import com.rupio.app.ui.support.LeagueContent
import com.rupio.app.ui.support.LeagueView
import com.rupio.app.ui.support.SupportScreen
import com.rupio.app.ui.support.SupportTab
import com.rupio.app.ui.support.SupporterBadge
import com.rupio.app.ui.support.WallAccess
import com.rupio.app.ui.support.WallNameDialog
import com.rupio.app.ui.theme.RupioTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** v1.9: Apoya Rupio con pestañas, ligas y metales (capturas en app/screenshots/1xx). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rMX-w393dp-h852dp-night-xhdpi")
class V19UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val monday = LocalDate.parse("2026-11-16")
    private val today = monday.plusDays(3)

    private fun settle(ms: Long = 800) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }
    private fun waitText(t: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("screenshots/$name.png") }
    private fun app(content: @Composable () -> Unit) = compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }

    /** Zonas táctiles ≥ 48 dp y todo lo tocable con nombre (mismo criterio que QaUiTest). */
    private fun auditTouch(screen: String): List<String> {
        val density = compose.activity.resources.displayMetrics.density
        return compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { n ->
            if (n.config.getOrNull(SemanticsProperties.Disabled) != null || n.boundsInRoot.height < 1) return@mapNotNull null
            val w = maxOf(n.size.width, n.touchBoundsInRoot.width.toInt()) / density; val h = maxOf(n.size.height, n.touchBoundsInRoot.height.toInt()) / density
            if (w < 47.5f || h < 47.5f) "[$screen] zona táctil ${"%.0f".format(w)}×${"%.0f".format(h)} dp" else null
        }
    }

    private val names = listOf("Caro Fit", "El Profe Toño", "Mau", "Fer del gym", "Lupita", "Sebas 💪", "José Ángel", "Dani R.", "La Güera", "Chuy",
        "Memo", "Pao", "Rodo", "Ximena", "Beto", "Itzel", "Kike", "Majo", "Lalo", "Vale", "Pancho", "Nayeli", "Toñito", "Gaby", "Rafa", "Sofi", "Nacho", "Abril")

    private fun group(size: Int = 28, me: Int = 7, tier: Int = 3): League {
        val up = if (size >= LeagueRules.UP_MIN_SIZE) 5 else 0; val down = if (size >= LeagueRules.DOWN_MIN_SIZE) 5 else 0
        val rows = (1..size).map { r ->
            val kg = 24_800.0 - r * 610 - (r % 3) * 45
            LeagueRow("${names[r - 1]}|$r", r, names[r - 1], badge = listOf(0, 0, 3, 0, 1, 0, 2, 0, 0, 4)[r % 10], kg = kg, workouts = 1 + (size - r) % 5,
                me = r == me, zone = LeagueRules.zone(r, size, up, down))
        }
        return League(true, monday, tier, false, size, up, down, rows)
    }
    private fun top(n: Int) = (1..n).map { i -> TopRow("t$i", i, names[(i - 1) % names.size] + if (i > names.size) " ${i / names.size}" else "", listOf(0, 4, 0, 3, 0, 0, 2, 0, 1, 0)[i % 10],
        tier = (7 - (i / 15).coerceAtMost(5)).coerceIn(1, 6), kg = 61_000.0 - i * 320, k = 61_000.0 - i * 320, at = "2026-11-16T10:00:00+00:00") }

    @Composable
    private fun league(state: LeagueState, view: LeagueView = LeagueView.GROUP, onLoadMore: () -> Unit = {}) {
        LeagueContent(state, WeightUnit.KG, WallAccess.READY, today, view, {}, rememberLazyListState(), rememberLazyListState(),
            onJoin = {}, onLeave = {}, onCreateAccount = {}, onRetry = {}, onLoadMore = onLoadMore)
    }

    @Test fun a_apoya_con_pestanas() = runBlocking {
        c.auth.useLocalMode("José Ángel Hernández"); c.support.debugReset()
        app { SupportScreen(rememberNavController()) }
        waitText("Un café")
        listOf("Apoyar", "Muro", "Ligas").forEach { compose.onNode(hasText(it) and hasClickAction()).assertExists() }
        shot("100_apoya_pestanas")
        compose.onNode(hasText("Muro") and hasClickAction()).performClick(); settle()
        waitText("Gracias 💙"); shot("101_apoya_muro")
        compose.onNode(hasText("Ligas") and hasClickAction()).performClick(); settle()
        waitText(LeagueCopy.NEEDS_ACCOUNT.take(30)); shot("102_ligas_sin_cuenta")
        // Vuelve a Apoyar y sigue donde estaba (el contenido de las otras pestañas no queda compuesto).
        compose.onNode(hasText("Apoyar") and hasClickAction()).performClick(); settle()
        waitText("Un café")
        assertTrue(compose.onAllNodesWithText("Gracias 💙").fetchSemanticsNodes().isEmpty())
    }

    @Test fun b_abre_directo_en_ligas() = runBlocking {
        c.auth.useLocalMode("José Ángel")
        app { SupportScreen(rememberNavController(), SupportTab.LIGAS) }
        waitText(LeagueCopy.NEEDS_ACCOUNT.take(30))
    }

    @Test fun c_participar() {
        app { league(LeagueState(league = League(false, monday, 1, false, 0, 0, 0, emptyList()), loaded = true)) }
        waitText("Participar"); waitText("1 · Calentamiento (empiezas aquí)")
        shot("103_ligas_participar")
    }

    @Test fun d_mi_grupo() {
        app { league(LeagueState(league = group(), loaded = true)) }
        waitText("Liga Sólido"); waitText("Semana 16 – 22 nov"); waitText("Quedan 4 días · 28 personas")
        waitText("Zona para subir")
        // Tu lugar fijo abajo: cuánto te falta para la zona para subir (5.º lugar + 1 kg).
        val l = group()
        val need = LeagueRules.kgToReach(5, 7, l.me!!.kg, l.rows.map { it.kg })!!
        waitText("Tú · lugar 7 de 28")
        waitText("Te faltan ${com.rupio.app.ui.support.leagueKg(need, WeightUnit.KG)} para la zona para subir")
        shot("104_ligas_mi_grupo")
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Salir de las ligas")); settle()
        compose.onNodeWithText("Zona para bajar").assertExists()
        shot("105_ligas_zona_bajar")
    }

    @Test fun e_global_pide_paginas_al_final() {
        var asked = 0
        val state = LeagueState(league = group(), global = LeagueGlobalMe(1234, 60_000, 3), top = top(60), loaded = true)
        app { league(state, LeagueView.GLOBAL) { asked++ } }
        waitText("Top de la semana")
        waitText("Tú · lugar 1,234 de 60,000 · top 3 %")
        shot("106_ligas_global")
        val before = asked
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToIndex(55); settle()
        assertTrue("al acercarse al final pide la siguiente página", asked > before)
    }

    @Test fun f_sin_entreno_y_tarjeta_de_progreso() {
        app {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LeagueCardContent(null, WeightUnit.LB) {}
                LeagueCardContent(League(true, monday, 2, false, 0, 0, 0, emptyList()), WeightUnit.LB) {}
                LeagueCardContent(group(), WeightUnit.LB) {}
                LeagueCardContent(group(me = 2), WeightUnit.LB) {}
                SupportTier.entries.forEach { SupporterBadge(it) }
            }
        }
        waitText("Liga Sólido · lugar 7 de 28")
        compose.mainClock.autoAdvance = false; compose.mainClock.advanceTimeBy(1300)
        compose.onRoot().captureRoboImage("screenshots/107_progreso_tu_liga_y_metales.png")
        compose.mainClock.autoAdvance = true
    }

    @Test fun g_inscrito_sin_entrenar_esta_semana() {
        app { league(LeagueState(league = League(true, monday, 4, false, 0, 0, 0, emptyList()), loaded = true)) }
        waitText(LeagueCopy.NO_WORKOUT); waitText("Liga Fuerte")
        shot("108_ligas_sin_entreno")
    }

    @Test fun h_apodo_y_permiso() {
        compose.activity.setContent { RupioTheme { WallNameDialog("Participar en las ligas", "Elige cómo te verán en tu grupo. Puede ser un apodo.",
            "José Ángel", "Participar", askConsent = true, onDismiss = {}, label = "Apodo", consentText = LeagueCopy.CONSENT) {} } }
        repeat(8) { ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) }
        captureScreenRoboImage("screenshots/109_ligas_apodo_permiso.png")
    }

    /** Peor caso: 320 dp y letra al 200 %. Nada se corta, todo se puede tocar. */
    @Config(qualifiers = "es-rMX-w320dp-h640dp-night-xhdpi", fontScale = 2.0f)
    @Test fun i_peor_caso_letra_200() = runBlocking {
        val problems = mutableListOf<String>()
        app { league(LeagueState(league = group(), loaded = true)) }
        waitText("Liga Sólido"); shot("qa/14_ligas_grupo"); problems += auditTouch("ligas grupo")
        compose.activity.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) {
            league(LeagueState(league = League(false, monday, 1, false, 0, 0, 0, emptyList()), loaded = true)) } } }
        waitText("Participar"); shot("qa/15_ligas_participar"); problems += auditTouch("ligas participar")
        c.auth.useLocalMode("María Guadalupe Hernández de la Cruz Villaseñor"); c.support.debugReset()
        compose.activity.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { SupportScreen(rememberNavController()) } } }
        waitText("Un café"); shot("qa/16_apoya_pestanas"); problems += auditTouch("apoya")
        // Las tres pestañas caben (o se desplazan) sin cortar la palabra.
        listOf("Apoyar", "Muro", "Ligas").forEach { compose.onNode(hasText(it) and hasClickAction()).assertExists() }
        assertEquals("Problemas:\n" + problems.joinToString("\n"), emptyList<String>(), problems)
    }
}
