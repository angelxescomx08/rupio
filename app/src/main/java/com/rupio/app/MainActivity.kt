package com.rupio.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rupio.app.core.Fmt
import com.rupio.app.data.auth.AuthState
import com.rupio.app.timer.WorkoutService
import com.rupio.app.ui.auth.AuthScreen
import com.rupio.app.ui.exercises.ExerciseDetailScreen
import com.rupio.app.ui.exercises.ExercisePickerScreen
import com.rupio.app.ui.history.HistoryScreen
import com.rupio.app.ui.history.WorkoutDetailScreen
import com.rupio.app.ui.home.HomeScreen
import com.rupio.app.ui.profile.ProfileScreen
import com.rupio.app.ui.routine.RoutineEditorScreen
import com.rupio.app.ui.stats.StatsScreen
import com.rupio.app.ui.components.FitText
import com.rupio.app.ui.components.FitGroup
import androidx.compose.ui.platform.LocalConfiguration
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.theme.Tabular
import com.rupio.app.ui.tutorial.CoachController
import com.rupio.app.ui.tutorial.CoachHost
import com.rupio.app.ui.tutorial.OnboardingScreen
import com.rupio.app.ui.workout.ActiveWorkoutScreen
import com.rupio.app.ui.body.BodyWeightScreen
import com.rupio.app.ui.learn.BenefitsScreen
import com.rupio.app.ui.learn.ConceptScreen
import com.rupio.app.ui.learn.GlossaryScreen
import com.rupio.app.ui.learn.LearnScreen
import com.rupio.app.ui.learn.TutorialScreen
import com.rupio.app.ui.routine.RoutineGuideScreen
import com.rupio.app.ui.stats.ProgressStoryScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        val container = (application as RupioApp).container
        setContent {
            RupioTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                  // Arriba y a los lados nada se dibuja bajo la barra de estado ni el recorte de la cámara.
                  // (Abajo lo maneja cada Scaffold para que la barra de navegación quede pintada.)
                  Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                    val auth by container.auth.state.collectAsStateWithLifecycle()
                    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
                    val scope = rememberCoroutineScope()
                    when (val s = auth) {
                        AuthState.Loading -> Box(Modifier.fillMaxSize())
                        AuthState.SignedOut -> AuthScreen()
                        is AuthState.SignedIn -> {
                            val st = settings ?: return@Surface
                            if (!st.onboardingDone) OnboardingScreen(s.name) { scope.launch { container.settings.setOnboardingDone(true) } }
                            else MainShell()
                        }
                    }
                  }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as RupioApp).container.refreshZone()
    }
}

/** ViewModels con el contenedor de dependencias. */
@Composable
inline fun <reified T : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> T): T {
    val c = (LocalContext.current.applicationContext as RupioApp).container
    return viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create(c) as V
    })
}

@Composable
fun container(): AppContainer = (LocalContext.current.applicationContext as RupioApp).container

object Routes {
    const val HOME = "home"; const val HISTORY = "history"; const val STATS = "stats"; const val PROFILE = "profile"
    const val WORKOUT = "workout"
    const val ROUTINE = "routine/{id}"; fun routine(id: String) = "routine/$id"
    const val PICKER = "picker/{mode}/{target}"; fun picker(mode: String, target: String) = "picker/$mode/$target"
    const val EXERCISE = "exercise/{id}"; fun exercise(id: String) = "exercise/$id"
    const val DETAIL = "detail/{id}?celebrate={c}"; fun detail(id: String, celebrate: Int = 0) = "detail/$id?celebrate=$celebrate"
    const val LEARN = "learn"; const val GLOSSARY = "glossary"; const val BENEFITS = "benefits"
    const val CONCEPT = "concept/{id}"; fun concept(id: String) = "concept/$id"
    const val TUTORIAL = "tutorial/{id}"; fun tutorial(id: String) = "tutorial/$id"
    const val BODY_WEIGHT = "body_weight"; const val PROGRESS = "progress"; const val GUIDE = "guide"
}

/**
 * "Atrás" a prueba de doble toque: solo regresa si la pantalla actual está activa (no a media animación) y si hay
 * a dónde regresar. Antes, dos toques rápidos sacaban dos pantallas y podían dejar la app en blanco.
 */
fun NavHostController.safePop(): Boolean {
    val cur = currentBackStackEntry ?: return false
    if (previousBackStackEntry == null) return false
    if (cur.lifecycle.currentState != androidx.lifecycle.Lifecycle.State.RESUMED) return false
    return popBackStack()
}

/**
 * Cambiar de pestaña (barra inferior y avatar de Inicio).
 *
 * "Entrenar" (Inicio) es la pantalla raíz: se regresa a ella quitando lo que haya encima, SIN restaurar estado.
 * Antes se navegaba con restoreState también a Inicio, y Navigation asocia lo que se quitó (p. ej. Perfil abierto
 * desde el avatar) al destino de popUpTo (Inicio): al tocar "Entrenar" restauraba Perfil. Las demás pestañas sí
 * guardan y restauran su estado (scroll, pantalla interna abierta).
 */
fun NavHostController.goToTab(route: String) {
    if (currentDestination?.route == route) return
    if (route == Routes.HOME) {
        if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME) { popUpTo(graph.id) { inclusive = true }; launchSingleTop = true }
        return
    }
    navigate(route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MainShell() {
    val nav = rememberNavController()
    val c = container()
    val coach = remember { CoachController() }
    val active by c.workouts.active.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    LaunchedEffect(active?.id) { if (active != null) WorkoutService.start(context) }
    // Android 13+: sin este permiso no hay notificación ni sonido de fin de descanso con la pantalla apagada.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val tabs = listOf(
        Tab(Routes.HOME, "Entrenar", Icons.Rounded.FitnessCenter), Tab(Routes.HISTORY, "Historial", Icons.Rounded.History),
        Tab(Routes.STATS, "Progreso", Icons.Rounded.Insights), Tab(Routes.LEARN, "Aprende", Icons.Rounded.School),
        Tab(Routes.PROFILE, "Perfil", Icons.Rounded.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    CoachHost(coach) {
        Scaffold(
            containerColor = RupioColors.Black,
            bottomBar = {
                if (showBar) Column {
                    AnimatedVisibility(active != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                        active?.let { w -> ActiveWorkoutBanner(w.name, w.startedAt) { nav.navigate(Routes.WORKOUT) } }
                    }
                    val navLabels = remember(LocalConfiguration.current.fontScale, LocalConfiguration.current.screenWidthDp) { FitGroup() }
                    NavigationBar(containerColor = RupioColors.Surface, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = { nav.goToTab(t.route) },
                                icon = { Icon(t.icon, null) }, label = { FitText(t.label, MaterialTheme.typography.labelMedium, minScale = 0.35f, group = navLabels) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = RupioColors.Primary.copy(alpha = 0.18f), selectedIconColor = RupioColors.Primary, selectedTextColor = RupioColors.Text),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            // Respeta barra de estado, barra de navegación, recortes de cámara y teclado en TODAS las pantallas.
            NavGraph(nav, Modifier.padding(pad).consumeWindowInsets(pad).imePadding())
        }
    }
}

@Composable
private fun NavGraph(nav: NavHostController, modifier: Modifier) {
    NavHost(nav, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.HISTORY) { HistoryScreen(nav) }
        composable(Routes.STATS) { StatsScreen(nav) }
        composable(Routes.PROFILE) { ProfileScreen(nav) }
        composable(Routes.WORKOUT) { ActiveWorkoutScreen(nav) }
        composable(Routes.ROUTINE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { RoutineEditorScreen(nav, it.arguments?.getString("id") ?: "new") }
        composable(Routes.PICKER) { e -> ExercisePickerScreen(nav, e.arguments?.getString("mode") ?: "workout", e.arguments?.getString("target") ?: "") }
        composable(Routes.EXERCISE) { e -> ExerciseDetailScreen(nav, e.arguments?.getString("id") ?: "") }
        composable(Routes.LEARN) { LearnScreen(nav) }
        composable(Routes.GLOSSARY) { GlossaryScreen(nav) }
        composable(Routes.BENEFITS) { BenefitsScreen(nav) }
        composable(Routes.CONCEPT) { e -> ConceptScreen(nav, e.arguments?.getString("id") ?: "") }
        composable(Routes.TUTORIAL) { e -> TutorialScreen(nav, e.arguments?.getString("id") ?: "") }
        composable(Routes.BODY_WEIGHT) { BodyWeightScreen(nav) }
        composable(Routes.PROGRESS) { ProgressStoryScreen(nav) }
        composable(Routes.GUIDE) { RoutineGuideScreen(nav) }
        composable(Routes.DETAIL, arguments = listOf(navArgument("c") { type = NavType.IntType; defaultValue = 0 })) { e ->
            WorkoutDetailScreen(nav, e.arguments?.getString("id") ?: "", e.arguments?.getInt("c") ?: 0)
        }
    }
}

@Composable
private fun ActiveWorkoutBanner(name: String, startedAt: Long, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(RupioColors.Primary).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayCircle, null, tint = RupioColors.OnPrimary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Entrenamiento en curso", style = MaterialTheme.typography.labelMedium, color = RupioColors.OnPrimary.copy(alpha = 0.8f))
            Text(name, style = MaterialTheme.typography.titleSmall, color = RupioColors.OnPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Fmt.duration(((now - startedAt) / 1000).coerceAtLeast(0)), style = MaterialTheme.typography.titleMedium.merge(Tabular), color = RupioColors.OnPrimary)
    }
}
