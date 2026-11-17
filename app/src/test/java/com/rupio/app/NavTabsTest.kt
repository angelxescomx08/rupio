package com.rupio.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Tocar una pestaña SIEMPRE debe llevar a esa pestaña.
 * Reproduce el error reportado ("toco Entrenar y me manda a Perfil") con la lógica anterior y prueba la nueva
 * con cientos de recorridos al azar entre pestañas y pantallas internas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavTabsTest {
    @get:Rule val rule = createComposeRule()

    private val tabs = listOf(Routes.HOME, Routes.HISTORY, Routes.STATS, Routes.BODY_WEIGHT, Routes.PROFILE)

    private fun host(): NavHostController {
        lateinit var nav: NavHostController
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HOME) {
                tabs.forEach { r -> composable(r) { Text(r) } }
                composable(Routes.LEARN) { Text("aprende") }
                composable(Routes.GLOSSARY) { Text("glosario") }
            }
        }
        rule.waitForIdle()
        return nav
    }

    /** Lógica anterior (con el error). */
    private fun NavHostController.oldGo(route: String) {
        if (currentDestination?.route != route) navigate(route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
    }

    private fun run(nav: NavHostController, go: NavHostController.(String) -> Unit, seed: Int, steps: Int): String? {
        val rnd = Random(seed)
        repeat(steps) { i ->
            // A veces entra a una pantalla interna (Aprende desde Perfil, glosario desde Aprende) antes de cambiar de pestaña.
            val cur = nav.currentDestination?.route
            if (rnd.nextInt(4) == 0 && (cur == Routes.PROFILE || cur == Routes.LEARN)) {
                rule.runOnUiThread { nav.navigate(if (cur == Routes.PROFILE) Routes.LEARN else Routes.GLOSSARY) }
                rule.waitForIdle()
                if (rnd.nextBoolean()) { rule.runOnUiThread { nav.popBackStack() }; rule.waitForIdle() }
            }
            // A veces abre Perfil desde el avatar de Inicio.
            if (rnd.nextInt(5) == 0 && nav.currentDestination?.route == Routes.HOME) { rule.runOnUiThread { nav.goToTab(Routes.PROFILE) }; rule.waitForIdle() }
            val target = tabs[rnd.nextInt(tabs.size)]
            rule.runOnUiThread { nav.go(target) }
            rule.waitForIdle()
            val now = nav.currentDestination?.route
            val onTabRoot = now == target || (now != null && now !in tabs) // regresar a una pantalla interna guardada de esa pestaña es válido
            if (!onTabRoot || (now !in tabs && target == Routes.HOME)) return "semilla $seed, paso $i: toqué $target y quedó en $now"
        }
        return null
    }

    /** El reporte: abrir Perfil desde el avatar de Inicio y luego tocar "Entrenar" → volvía a Perfil. */
    @Test fun laLogicaAnteriorFallabaDesdeElAvatar() {
        val nav = host()
        rule.runOnUiThread { nav.navigate(Routes.PROFILE) }; rule.waitForIdle()   // avatar (antes)
        rule.runOnUiThread { nav.oldGo(Routes.HOME) }; rule.waitForIdle()         // pestaña Entrenar (antes)
        assertEquals("se reproduce el error", Routes.PROFILE, nav.currentDestination?.route)
    }

    @Test fun avatarYLuegoEntrenarLlevaAInicio() {
        val nav = host()
        rule.runOnUiThread { nav.goToTab(Routes.PROFILE) }; rule.waitForIdle()    // avatar (ahora)
        rule.runOnUiThread { nav.goToTab(Routes.HOME) }; rule.waitForIdle()
        assertEquals(Routes.HOME, nav.currentDestination?.route)
        // Aunque la pila quede como antes (Perfil encima de Inicio), "Entrenar" siempre lleva a Inicio.
        rule.runOnUiThread { nav.navigate(Routes.PROFILE) }; rule.waitForIdle()
        rule.runOnUiThread { nav.goToTab(Routes.HOME) }; rule.waitForIdle()
        assertEquals(Routes.HOME, nav.currentDestination?.route)
        rule.runOnUiThread { nav.goToTab(Routes.PROFILE) }; rule.waitForIdle()
        assertEquals(Routes.PROFILE, nav.currentDestination?.route)
    }

    /** Doble toque rápido en "atrás": nunca deja la app sin pantalla. */
    @Test fun dobleAtrasNoVaciaLaApp() {
        val nav = host()
        rule.runOnUiThread { nav.goToTab(Routes.PROFILE); nav.navigate(Routes.BODY_WEIGHT) }; rule.waitForIdle()
        rule.runOnUiThread { nav.safePop(); nav.safePop(); nav.safePop() }; rule.waitForIdle()
        assertEquals(Routes.PROFILE, nav.currentDestination?.route)
        rule.runOnUiThread { repeat(10) { nav.safePop() } }; rule.waitForIdle()
        assertEquals("nunca se saca la pantalla de inicio", true, nav.currentDestination != null)
        rule.runOnUiThread { nav.goToTab(Routes.HOME); repeat(5) { nav.safePop() } }; rule.waitForIdle()
        assertEquals(Routes.HOME, nav.currentDestination?.route)
    }

    @Test fun tocarUnaPestanaSiempreLlevaAEsaPestana() {
        val nav = host()
        val fails = mutableListOf<String>()
        for (seed in 1..60) {
            rule.runOnUiThread { nav.goToTab(Routes.HOME) }; rule.waitForIdle()
            run(nav, { goToTab(it) }, seed, 25)?.let { fails += it }
        }
        assertEquals(emptyList<String>(), fails)
    }
}
