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
import androidx.compose.ui.graphics.toArgb
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
import com.rupio.app.ui.auth.SignOutOverlay
import com.rupio.app.data.billing.SupportEvent
import com.rupio.app.domain.SupportTier
import com.rupio.app.ui.support.SupportScreen
import com.rupio.app.ui.support.ThanksDialog
import com.rupio.app.ui.exercises.ExerciseDetailScreen
import com.rupio.app.ui.exercises.ExercisePickerScreen
import com.rupio.app.ui.history.HistoryScreen
import com.rupio.app.ui.history.WorkoutDetailScreen
import com.rupio.app.ui.home.HomeScreen
import com.rupio.app.ui.profile.ProfileScreen
import com.rupio.app.ui.routine.RoutineEditorScreen
import com.rupio.app.ui.stats.StatsScreen
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.rupio.app.core.I18n
import com.rupio.app.core.tr
import com.rupio.app.data.prefs.AppearancePrefs
import com.rupio.app.ui.theme.Appearance

class MainActivity : ComponentActivity() {
    /**
     * v2.2: respuesta del diálogo de Google Play al pedir una actualización flexible (Activity Result API). Se registra
     * al crear la pantalla, como pide la API; si la pantalla se recrea, la respuesta llega al launcher nuevo.
     */
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        (application as RupioApp).container.updates.onFlowResult(r.resultCode)
    }

    /**
     * v2.0: antes de Android 13 el idioma elegido en Rupio se aplica aquí a la configuración de la pantalla, para que
     * los textos propios de Android y Material (p. ej. "Cerrar" de un menú) salgan en el mismo idioma. En Android 13+
     * lo hace el sistema (LocaleManager).
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val choice = if (Build.VERSION.SDK_INT < 33) AppearancePrefs.languageChoice(newBase) else null
        if (choice == null) { super.attachBaseContext(newBase); return }
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocales(android.os.LocaleList.forLanguageTags(choice.code))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RupioApp).container
        // Android recrea la pantalla al cambiar el idioma de la app (Ajustes o Perfil) o el modo oscuro del teléfono.
        val before = I18n.language
        I18n.set(AppearancePrefs.resolvedLanguage(this))
        if (I18n.language != before) container.onLanguageChanged()
        Appearance.setSystemDark(AppearancePrefs.systemDark(this))
        setContent {
            // Barras del sistema y fondo de la ventana del color del tema (íconos claros en oscuro y oscuros en claro).
            val dark = Appearance.isDark
            val bg = RupioColors.Background
            LaunchedEffect(dark) {
                val c = bg.toArgb()
                val style = if (dark) SystemBarStyle.dark(c) else SystemBarStyle.light(c, c)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(c))
            }
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
                            val st = settings
                            if (st == null) Box(Modifier.fillMaxSize())
                            // v1.7 (N6): la cuenta recién creada no está lista hasta escribir el código del correo.
                            else if (s.needsVerification) com.rupio.app.ui.auth.VerifyEmailScreen(s.email, s.newAccount,
                                initialSendError = s.verificationSendError ?: if (s.verificationSendFailed) com.rupio.app.ui.auth.earlierSendFailedMessage() else null,
                                sentAt = s.verificationSentAt)
                            else if (!st.onboardingDone) OnboardingScreen(s.name) { scope.launch { container.settings.setOnboardingDone(true) } }
                            else MainShell()
                        }
                    }
                    // Encima de todo: sigue visible aunque la app cambie a la pantalla de inicio de sesión.
                    val signOut by container.signOut.state.collectAsStateWithLifecycle()
                    SignOutOverlay(signOut, onRetry = container.signOut::retry, onExitAnyway = container.signOut::exitWithoutUpload, onCancel = container.signOut::cancel)
                    SupportThanksHost()
                    com.rupio.app.ui.update.AppUpdateHost(onUpdate = { container.updates.accept(updateLauncher) })
                    val deleted by container.accountDeleted.collectAsStateWithLifecycle()
                    if (deleted) com.rupio.app.ui.legal.AccountDeletedDialog { container.accountDeleted.value = false }
                  }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as RupioApp).container.refreshZone()
        // Un pago pendiente (p. ej. en OXXO) pudo completarse mientras la app no estaba a la vista.
        (application as RupioApp).container.support.refresh()
        // v2.2: ¿hay una versión nueva en Google Play, o una descarga que ya terminó y falta instalar?
        (application as RupioApp).container.updates.check()
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
    /** v1.9: Apoya Rupio con pestañas Apoyar · Muro · Ligas. "support" abre en Apoyar. */
    const val SUPPORT = "support"
    const val SUPPORT_ROUTE = "support?tab={tab}"
    /** v1.8: muro de agradecimientos (nivel Mecenas). v1.9: es la pestaña Muro de Apoya Rupio. */
    const val WALL = "support?tab=muro"
    /** v1.9: ligas semanales por volumen (pestaña Ligas de Apoya Rupio). */
    const val LEAGUE = "support?tab=ligas"
    /** Modo sin cuenta → crear cuenta (o entrar) conservando los datos del teléfono. */
    const val ACCOUNT = "account"
    const val DELETE_ACCOUNT = "delete_account"
    /** v1.7 (N1): editar un entreno terminado; [set] = serie rechazada a corregir (desde "Revisar"). */
    const val EDIT = "edit/{id}?set={set}"; fun edit(id: String, set: String? = null) = "edit/$id" + (set?.let { "?set=$it" } ?: "")
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
    // v2.2: después de «Reiniciar y actualizar» a media sesión, se abre directo el entreno (no Entrenar).
    LaunchedEffect(Unit) {
        if (c.takeOpenWorkoutAfterUpdate() && c.workouts.active.first() != null) runCatching { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } }
    }
    // Pedidos de navegación desde fuera del NavHost (p. ej. "Ver el muro" en la pantalla de gracias).
    LaunchedEffect(Unit) { c.navRequests.collect { r -> runCatching { nav.navigate(r) { launchSingleTop = true } } } }
    // El permiso de notificaciones ya NO se pide al abrir la app: se pide al empezar el primer entreno, después de
    // explicar para qué sirve (ver HomeScreen · NotificationRationaleDialog).
    val syncUi = remember { com.rupio.app.ui.components.SyncUiController() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        c.notices.collect { n ->
            // v1.9: 10 s para "Deshacer" (con 5 s se iba mientras lo tocabas).
            val r = kotlinx.coroutines.withTimeoutOrNull(if (n.undo != null) com.rupio.app.ui.components.UNDO_MS else 3_000L) {
                snackbar.showSnackbar(n.text, actionLabel = if (n.undo != null) tr("Deshacer", "Undo") else null, duration = SnackbarDuration.Indefinite)
            }
            if (r == null) snackbar.currentSnackbarData?.dismiss()
            if (r == SnackbarResult.ActionPerformed) n.undo?.let { c.scope.launch { it() } }
        }
    }

    val tabs = listOf(
        Tab(Routes.HOME, tr("Entrenar", "Workout"), Icons.Rounded.FitnessCenter), Tab(Routes.HISTORY, tr("Historial", "History"), Icons.Rounded.History),
        // Peso corporal tiene su propia pestaña (antes estaba escondido dentro de Progreso/Perfil). Aprende vive en Perfil y Progreso.
        Tab(Routes.STATS, tr("Progreso", "Progress"), Icons.Rounded.Insights), Tab(Routes.BODY_WEIGHT, tr("Peso", "Weight"), Icons.Rounded.MonitorWeight),
        Tab(Routes.PROFILE, tr("Perfil", "Profile"), Icons.Rounded.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    CompositionLocalProvider(com.rupio.app.ui.components.LocalSyncUi provides syncUi) {
    CoachHost(coach) {
        Scaffold(
            containerColor = RupioColors.Background,
            bottomBar = {
                if (showBar) Column {
                    AnimatedVisibility(active != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                        val done by c.restTimer.finished.collectAsStateWithLifecycle()
                        active?.let { w -> ActiveWorkoutBanner(w.name, w.startedAt, restDone = done != null) { c.restTimer.clearFinished(); nav.navigate(Routes.WORKOUT) } }
                    }
                    // Las etiquetas NUNCA se achican (antes bajaban hasta 35 %, más chicas que el tamaño normal aun con letra
                    // al 200 %). Si alguna no cabe a su tamaño, se ocultan todas y el ícono dice su nombre al lector de pantalla.
                    var hideLabels by remember(LocalConfiguration.current.fontScale, LocalConfiguration.current.screenWidthDp) { mutableStateOf(false) }
                    NavigationBar(containerColor = RupioColors.Surface, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = { nav.goToTab(t.route) },
                                icon = { Icon(t.icon, if (hideLabels) t.label else null) },
                                label = if (hideLabels) null else ({
                                    Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
                                        onTextLayout = { if (it.hasVisualOverflow) hideLabels = true })
                                }),
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(indicatorColor = RupioColors.Primary.copy(alpha = 0.18f), selectedIconColor = RupioColors.Primary, selectedTextColor = RupioColors.Text),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            // Respeta barra de estado, barra de navegación, recortes de cámara y teclado en TODAS las pantallas.
            Box(Modifier.fillMaxSize()) {
                NavGraph(nav, Modifier.padding(pad).consumeWindowInsets(pad).imePadding())
                // v1.9: los avisos (y su "Deshacer") van ARRIBA: abajo quedaban encima de "Empezar rutina" y, si el aviso se
                // iba justo al tocar "Deshacer", empezabas una rutina sin querer.
                SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(top = pad.calculateTopPadding() + 4.dp))
            }
        }
        com.rupio.app.ui.components.SyncUiHost(syncUi, onCreateAccount = { nav.navigate(Routes.ACCOUNT) }, onFixSet = { setId ->
            // "Revisar" → "Corregir": abre el editor del entreno de esa serie.
            c.scope.launch {
                val wid = c.db.workouts().getSet(setId)?.workoutId
                if (wid != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nav.navigate(Routes.edit(wid, setId)) }
                else c.notices.emit(Notice(tr("Esa serie ya no existe en este teléfono. Puedes descartarla.", "That set no longer exists on this phone. You can dismiss it.")))
            }
        })
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
        composable(Routes.SUPPORT_ROUTE, arguments = listOf(navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
            SupportScreen(nav, com.rupio.app.ui.support.SupportTab.of(e.arguments?.getString("tab")))
        }
        composable(Routes.ACCOUNT) { AuthScreen(upgrade = true, onBack = { nav.safePop() }, onDone = { nav.safePop() }) }
        composable(Routes.DELETE_ACCOUNT) { com.rupio.app.ui.legal.DeleteAccountScreen(nav) }
        composable(Routes.EDIT, arguments = listOf(navArgument("set") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
            com.rupio.app.ui.history.EditWorkoutScreen(nav, e.arguments?.getString("id") ?: "", e.arguments?.getString("set"))
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("c") { type = NavType.IntType; defaultValue = 0 })) { e ->
            WorkoutDetailScreen(nav, e.arguments?.getString("id") ?: "", e.arguments?.getInt("c") ?: 0)
        }
    }
}

@Composable
private fun ActiveWorkoutBanner(name: String, startedAt: Long, restDone: Boolean = false, onClick: () -> Unit) {
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
            Text(if (restDone) tr("¡Descanso terminado! A la siguiente serie", "Rest's over! On to the next set") else tr("Entrenamiento en curso", "Workout in progress"), style = MaterialTheme.typography.labelMedium, color = RupioColors.OnPrimary.copy(alpha = 0.8f))
            Text(name, style = MaterialTheme.typography.titleSmall, color = RupioColors.OnPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Fmt.duration(((now - startedAt) / 1000).coerceAtLeast(0)), style = MaterialTheme.typography.titleMedium.merge(Tabular), color = RupioColors.OnPrimary)
    }
}

/** Muestra la pantalla de gracias cuando Google Play confirma una compra de apoyo (desde cualquier pantalla). */
@Composable
private fun SupportThanksHost() {
    val c = container()
    val state by c.support.state.collectAsStateWithLifecycle()
    val wall by c.wall.state.collectAsStateWithLifecycle()
    val auth by c.auth.state.collectAsStateWithLifecycle()
    var thanks by remember { mutableStateOf<SupportEvent.Thanks?>(null) }
    var raised by remember { mutableStateOf<SupportEvent.WallRaised?>(null) }
    LaunchedEffect(Unit) {
        c.support.events.collect { e ->
            when (e) { is SupportEvent.Thanks -> thanks = e; is SupportEvent.WallRaised -> raised = e }
        }
    }
    thanks?.let { t ->
        ThanksDialog(t.tier, state, onAccent = { c.support.setAccent(it) }, onIcon = { c.support.setAppIcon(it) }, onClose = { thanks = null },
            wallRank = t.wallRank, onSeeWall = { thanks = null; c.navRequests.tryEmit(Routes.WALL) })
    }
    raised?.let { r ->
        com.rupio.app.ui.support.WallRaisedDialog(r.rank, r.previousRank, onSeeWall = { raised = null; c.navRequests.tryEmit(Routes.WALL) }) { raised = null }
    }
    // Llegó una compra del muro sin nombre (p. ej. se pagó y se cerró la app antes): se pide aquí, con el permiso.
    if (state.wallNeedsName) com.rupio.app.ui.support.WallNameDialog(
        title = tr("¡Gracias! ¿Cómo quieres aparecer?", "Thank you! How do you want to appear?"),
        intro = tr("Tu aporte llegó. Escribe el nombre que verán en el muro de agradecimientos.", "Your contribution arrived. Enter the name people will see on the thank-you wall."),
        initial = wall.me?.name ?: (auth as? com.rupio.app.data.auth.AuthState.SignedIn)?.name.orEmpty(),
        confirm = tr("Aparecer en el muro", "Show me on the wall"), askConsent = true, onDismiss = { c.support.dismissWallName() },
    ) { name -> c.support.completeWallName(name) }
}
