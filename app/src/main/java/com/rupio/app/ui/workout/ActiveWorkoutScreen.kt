package com.rupio.app.ui.workout

import com.rupio.app.safePop
import androidx.compose.foundation.BorderStroke
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.appViewModel
import com.rupio.app.container
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.Muscles
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.data.remote.SyncEngine
import com.rupio.app.domain.*
import com.rupio.app.timer.RestTimerController
import com.rupio.app.ui.components.*
import com.rupio.app.ui.exercises.RecommendationCard
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import com.rupio.app.ui.tutorial.CoachStep
import com.rupio.app.ui.tutorial.LocalCoach
import com.rupio.app.ui.tutorial.coachTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ActiveWorkoutScreen(nav: NavHostController) {
    val vm = appViewModel { ActiveWorkoutVM(it) }
    val c = container()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val rest by c.restTimer.state.collectAsStateWithLifecycle()
    val zone by c.zone.collectAsStateWithLifecycle()
    val unit = settings?.unit ?: WeightUnit.LB
    val snackbar = remember { SnackbarHostState() }
    val coach = LocalCoach.current
    // Se vuelve a revisar al regresar de Ajustes.
    var notifOk by remember { mutableStateOf(com.rupio.app.timer.canNotify(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { notifOk = com.rupio.app.timer.canNotify(context); onPauseOrDispose { } }

    // Pantalla encendida mientras entrenas (se puede apagar en Perfil → Pantalla encendida al entrenar).
    val view = LocalView.current
    val keepOn = settings?.keepScreenOn ?: true
    DisposableEffect(keepOn) { view.keepScreenOn = keepOn; onDispose { view.keepScreenOn = false } }

    // Si ya no hay entrenamiento activo (terminado o descartado), volver.
    LaunchedEffect(ui) { if (ui != null && ui?.workout == null) nav.safePop() }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is WorkoutEvent.Message -> snackbar.showSnackbar(e.text)
                is WorkoutEvent.Record -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    snackbar.showSnackbar("🏆 ¡Nuevo récord en ${e.exercise}! " + e.kinds.joinToString(" · ") { it.label })
                }
                WorkoutEvent.Completed -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                is WorkoutEvent.Undoable -> {
                    // 5 s para deshacer (en vez de borrar al instante sin aviso, o de un diálogo para algo reversible).
                    val r = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                        snackbar.showSnackbar(e.text, actionLabel = "Deshacer", duration = SnackbarDuration.Indefinite)
                    }
                    if (r == null) snackbar.currentSnackbarData?.dismiss()
                    if (r == SnackbarResult.ActionPerformed) scope.launch { e.undo() }
                }
            }
        }
    }

    val state = ui ?: return
    val w = state.workout ?: return
    val items = state.items

    // Tutorial del entrenamiento (una sola vez).
    LaunchedEffect(settings?.tutorialWorkout, items.isNotEmpty()) {
        val s = settings ?: return@LaunchedEffect
        if (s.tutorialWorkout || items.isEmpty() || coach.active) return@LaunchedEffect
        delay(800)
        coach.start(listOf(
            CoachStep("rec_0", "Te dice qué hacer hoy", "Rupio revisa tu última sesión: si llegaste al máximo de reps te pide subir el peso; si no, sumar una repetición."),
            CoachStep("set_check", "Marca cada serie", "Escribe peso y reps (o deja el valor gris sugerido) y toca ✓. Arranca el descanso automáticamente."),
            CoachStep("set_number", "Tipo de serie", "Toca el número para marcar calentamiento, drop set, al fallo o anotar tu RPE (qué tan difícil fue)."),
            CoachStep("rest_chip", "Descanso", "Cambia el descanso de este ejercicio. Sonará una sola vez al terminar, aunque marques y desmarques."),
            CoachStep("ex_menu", "Superseries y más", "Desde aquí unes este ejercicio con el siguiente en una superserie, cambias el orden o las repeticiones."),
            CoachStep("finish", "Termina y guarda", "Al terminar se guardan tus récords y tu volumen. ¡A entrenar!"),
        )) { scope.launch { c.settings.setTutorialWorkout(true) } }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    var finishing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var restFor by remember { mutableStateOf<ExUi?>(null) }
    var repsFor by remember { mutableStateOf<ExUi?>(null) }
    var notesFor by remember { mutableStateOf<ExUi?>(null) }
    var setSheet by remember { mutableStateOf<Pair<ExUi, WorkoutSetEntity>?>(null) }
    var ssCreate by remember { mutableStateOf<List<String>?>(null) }
    var ssEdit by remember { mutableStateOf<String?>(null) }

    val doneSets = items.sumOf { e -> e.sets.count { it.completed && it.setType != SetType.WARMUP.code } }
    val volume = items.sumOf { e -> e.sets.filter { it.completed && it.setType != SetType.WARMUP.code }.sumOf { (it.weightKg ?: 0.0).coerceAtLeast(0.0) * (it.reps ?: 0) } }
    val ssIndex = supersetIndexMap(items.map { it.we.supersetId })
    val groups = groupConsecutive(items) { it.we.supersetId }

    Scaffold(
        containerColor = RupioColors.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.background(RupioColors.Black)) {
                // Con letra muy grande el nombre va debajo de la barra (completo) en vez de cortarse junto a "Terminar".
                val bigText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                val started = "Empezó a las " + Dates.formatTimeWithOrigin(w.startedAt, zone, w.timeZone)
                RupioTopBar(if (bigText) "" else w.name, onBack = { nav.safePop() }, subtitle = if (bigText) null else started) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Cambiar nombre", tint = RupioColors.TextDim) }
                    Button(onClick = { finishing = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(end = 8.dp).coachTarget("finish"),
                        colors = ButtonDefaults.buttonColors(containerColor = RupioColors.Success, contentColor = Color.Black)) { Text("Terminar", fontWeight = FontWeight.SemiBold) }
                }
                if (bigText) Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(w.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(started, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
                TileRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Tile("Duración", Fmt.duration(((now - w.startedAt) / 1000).coerceAtLeast(0)))
                    Tile("Volumen", Fmt.volume(volume, unit), sub = Fmt.volume(volume, unit.other))
                    Tile("Series", "$doneSets")
                }
            }
        },
        bottomBar = {
            val finished by c.restTimer.finished.collectAsStateWithLifecycle()
            // El aviso de fin de descanso, DENTRO de la pantalla (ya no como notificación emergente encima de "Terminar").
            LaunchedEffect(finished) { if (finished != null) { delay(8_000); c.restTimer.clearFinished() } }
            Column {
                AnimatedVisibility(rest == null && finished != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    finished?.let { f -> RestDoneBar(f.label) { c.restTimer.clearFinished() } }
                }
                AnimatedVisibility(rest != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    rest?.let { RestBar(it, c.restTimer) }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notifOk) item { NotificationsOffCard() }
            if (items.isEmpty()) item {
                EmptyState(Icons.Rounded.FitnessCenter, "Entrenamiento vacío", "Agrega ejercicios para empezar. Puedes buscar por nombre o por músculo.")
            }
            items(groups, key = { g -> g.first().we.id }) { g ->
                val first = items.indexOfFirst { it.we.id == g.first().we.id } == 0
                val ss = g.first().we.supersetId
                if (ss != null && g.size > 1) {
                    val idx = ssIndex[ss] ?: 0
                    SupersetGroup(idx, g.size, SupersetRestMode.of(g.first().we.supersetRestMode), g.first().we.supersetRestSec,
                        onEditRest = { ssEdit = ss }, onUngroup = { vm.ungroup(w.id, ss) }) { k ->
                        ExerciseCard(g[k], vm, unit, idx, k, first && k == 0, inSuperset = true,
                            onOpen = { nav.navigate(Routes.exercise(g[k].we.exerciseId)) },
                            onRest = { if (g[k].we.supersetRestMode == SupersetRestMode.SHARED.code) ssEdit = ss else restFor = g[k] }, onReps = { repsFor = g[k] },
                            onNotes = { notesFor = g[k] }, onSuperset = { vm.membersWithNext(g[k])?.let { ssCreate = it } }, onSetMenu = { s -> setSheet = g[k] to s })
                    }
                } else {
                    val it = g.first()
                    ExerciseCard(it, vm, unit, null, null, first, inSuperset = false,
                        onOpen = { nav.navigate(Routes.exercise(it.we.exerciseId)) }, onRest = { restFor = it }, onReps = { repsFor = it },
                        onNotes = { notesFor = it }, onSuperset = { vm.membersWithNext(it)?.let { ids -> ssCreate = ids } }, onSetMenu = { s -> setSheet = it to s })
                }
            }
            item {
                Button(
                    onClick = { nav.navigate(Routes.picker("workout", w.id)) },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Agregar ejercicios", style = MaterialTheme.typography.titleMedium) }
            }
            item {
                TextButton(onClick = { discarding = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = RupioColors.Danger)) {
                    Text("Descartar entrenamiento")
                }
            }
        }
    }

    // ------------------------------------------------------------------ diálogos
    if (finishing) FinishDialog(w.name, items, doneSets, volume, unit, ((now - w.startedAt) / 1000).coerceAtLeast(0), { finishing = false },
        onDiscard = { finishing = false; discarding = true }) { name, notes ->
        finishing = false
        scope.launch {
            val prs = vm.finish(w.id, name, notes)
            if (!c.auth.isLocalOnly) SyncEngine.now(context)
            nav.navigate(Routes.detail(w.id, 1 + prs)) { popUpTo(Routes.WORKOUT) { inclusive = true } }
        }
    }
    if (discarding) ConfirmDialog("¿Descartar entrenamiento?", "Se borrará todo lo registrado en esta sesión. No se puede deshacer.", "Descartar", { discarding = false }, danger = true) {
        discarding = false; scope.launch { vm.discard(w.id) }
    }
    if (renaming) {
        var text by remember { mutableStateOf(w.name) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Nombre del entrenamiento") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(w.id, text); renaming = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } })
    }
    restFor?.let { e ->
        RestPickerDialog("Descanso · ${e.name}", e.we.restSec, { restFor = null }, { vm.setRest(e, it); restFor = null }) {
            Text("Más de 60–90 s entre series ayuda a rendir; en ejercicios pesados 2–3 min.", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 10.dp))
        }
    }
    repsFor?.let { e -> RepRangeDialog(e.we.repMin, e.we.repMax, { repsFor = null }) { lo, hi -> vm.setRepRange(e, lo, hi); repsFor = null } }
    notesFor?.let { e ->
        var text by remember(e.we.id) { mutableStateOf(e.we.notes.orEmpty()) }
        AlertDialog(onDismissRequest = { notesFor = null }, title = { Text("Notas · ${e.name}") },
            text = { OutlinedTextField(text, { text = it }, placeholder = { Text("Ej. asiento en 4, agarre cerrado") }, minLines = 2) },
            confirmButton = { TextButton(onClick = { vm.setNotes(e, text); notesFor = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { notesFor = null }) { Text("Cancelar") } })
    }
    setSheet?.let { (e, s) -> SetOptionsSheet(e, s, vm) { setSheet = null } }
    ssCreate?.let { ids ->
        val members = items.filter { it.we.id in ids }
        val existing = members.firstOrNull { it.we.supersetId != null }?.we
        SupersetRestDialog("Superserie: " + members.joinToString(" + ") { it.name.substringBefore(" con ") },
            existing?.let { SupersetRestMode.of(it.supersetRestMode) } ?: SupersetRestMode.SHARED,
            existing?.supersetRestSec ?: members.maxOfOrNull { it.we.restSec } ?: 90,
            sameMusclePair(members.mapNotNull { it.ex?.primaryMuscle }), { ssCreate = null }) { mode, r ->
            vm.createSuperset(w.id, ids, mode, r); ssCreate = null
        }
    }
    ssEdit?.let { ss ->
        val members = items.filter { it.we.supersetId == ss }
        val f = members.firstOrNull()?.we
        SupersetRestDialog("Descanso de la superserie", SupersetRestMode.of(f?.supersetRestMode ?: 1), f?.supersetRestSec ?: f?.restSec ?: 90,
            sameMusclePair(members.mapNotNull { it.ex?.primaryMuscle }), { ssEdit = null }) { mode, r ->
            vm.updateSuperset(w.id, ss, mode, r)
            ssEdit = null
        }
    }
}

// ============================================================================ tarjeta de ejercicio
@Composable
private fun ExerciseCard(
    e: ExUi, vm: ActiveWorkoutVM, unit: WeightUnit, ssIdx: Int?, member: Int?, first: Boolean, inSuperset: Boolean,
    onOpen: () -> Unit, onRest: () -> Unit, onReps: () -> Unit, onNotes: () -> Unit, onSuperset: () -> Unit, onSetMenu: (WorkoutSetEntity) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val info = vm.infos[e.infoKey]
    val tr = e.tracking
    val shared = inSuperset && e.we.supersetRestMode == SupersetRestMode.SHARED.code
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = RupioColors.Surface) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable(onClick = onOpen)) { ExerciseThumb(e.ex?.image0, 46.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ssIdx != null && member != null) { SupersetTag(ssIdx, member); Spacer(Modifier.width(6.dp)) }
                        Text(e.name, style = MaterialTheme.typography.titleMedium, color = RupioColors.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${Muscles.label(e.ex?.primaryMuscle ?: "")} · ${if (tr == Tracking.DURATION) "tiempo" else "${e.we.repMin}–${e.we.repMax} reps"}",
                        style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
                Box(if (first) Modifier.coachTarget("ex_menu") else Modifier) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Opciones del ejercicio") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Unir en superserie con el siguiente") }, leadingIcon = { Icon(Icons.Rounded.Link, null) }, onClick = { menu = false; onSuperset() })
                        if (inSuperset) DropdownMenuItem(text = { Text("Sacar de la superserie") }, leadingIcon = { Icon(Icons.Rounded.LinkOff, null) }, onClick = { menu = false; vm.removeFromSuperset(e) })
                        if (tr != Tracking.DURATION) DropdownMenuItem(text = { Text("Repeticiones objetivo") }, leadingIcon = { Icon(Icons.Rounded.Repeat, null) }, onClick = { menu = false; onReps() })
                        DropdownMenuItem(text = { Text("Notas") }, leadingIcon = { Icon(Icons.Rounded.EditNote, null) }, onClick = { menu = false; onNotes() })
                        DropdownMenuItem(text = { Text("Ver técnica y video") }, leadingIcon = { Icon(Icons.Rounded.PlayCircle, null) }, onClick = { menu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Subir") }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) }, onClick = { menu = false; vm.move(e, -1) })
                        DropdownMenuItem(text = { Text("Bajar") }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) }, onClick = { menu = false; vm.move(e, 1) })
                        DropdownMenuItem(text = { Text("Quitar ejercicio", color = RupioColors.Danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = RupioColors.Danger) }, onClick = { menu = false; vm.remove(e) })
                    }
                }
            }
            e.we.notes?.let { Text("📝 $it", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 6.dp)) }
            if (info != null) {
                Spacer(Modifier.height(8.dp))
                RecommendationCard(info.rec, unit, modifier = if (first) Modifier.coachTarget("rec_0") else Modifier)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(RupioColors.Rest.copy(alpha = 0.12f)).clickable(onClick = onRest)
                    .padding(horizontal = 10.dp, vertical = 6.dp).then(if (first) Modifier.coachTarget("rest_chip") else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Timer, null, tint = RupioColors.Rest, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (shared) "Descanso compartido: ${Fmt.restLabel(e.we.supersetRestSec ?: 0)} tras la ronda" else "Descanso: ${Fmt.restLabel(e.we.restSec)}",
                    color = RupioColors.Rest, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            SetHeader(tr, unit)
            e.sets.forEachIndexed { i, s ->
                SetRow(e, s, i, vm, unit, first && i == 0, onSetMenu)
            }
            TextButton(onClick = { vm.addSet(e) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Agregar serie")
            }
        }
    }
}

private val W_SET = 32.dp; private val W_VAL = 68.dp; private val W_REPS = 54.dp; private val W_CHECK = 42.dp

@Composable
private fun SetHeader(tr: Tracking, unit: WeightUnit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val st = MaterialTheme.typography.labelSmall
        Text("SERIE", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_SET), textAlign = TextAlign.Center)
        Text("ANTERIOR", style = st, color = RupioColors.TextFaint, modifier = Modifier.weight(1f))
        when (tr) {
            Tracking.WEIGHT_REPS -> Text(unit.label.uppercase(), style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_VAL), textAlign = TextAlign.Center)
            Tracking.WEIGHTED_BODYWEIGHT -> Text("+${unit.label.uppercase()}", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_VAL), textAlign = TextAlign.Center)
            Tracking.ASSISTED_BODYWEIGHT -> Text("AYUDA", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_VAL), textAlign = TextAlign.Center)
            else -> {}
        }
        when (tr) {
            Tracking.DURATION -> Text("SEGUNDOS", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_VAL), textAlign = TextAlign.Center)
            else -> Text("REPS", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_REPS), textAlign = TextAlign.Center)
        }
        Box(Modifier.width(W_CHECK), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = RupioColors.TextFaint, modifier = Modifier.size(16.dp)) }
    }
}

/** Aviso cuando Android tiene bloqueadas las notificaciones: sin ellas no suena el fin de descanso con la pantalla apagada. */
@Composable
private fun NotificationsOffCard() {
    val ctx = LocalContext.current
    Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Warning.copy(alpha = 0.12f), border = BorderStroke(1.dp, RupioColors.Warning.copy(alpha = 0.5f))) {
        // El botón va abajo (no al lado): con letra grande el texto no se parte a media palabra.
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsOff, null, tint = RupioColors.Warning)
                Spacer(Modifier.width(10.dp))
                Text("Notificaciones desactivadas", style = MaterialTheme.typography.titleSmall)
            }
            Text("Actívalas para que el fin del descanso suene aunque tengas la pantalla apagada.", style = MaterialTheme.typography.bodySmall,
                color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
            TextButton(modifier = Modifier.align(Alignment.End), onClick = {
                runCatching {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Activar") }
        }
    }
}

@Composable
private fun SetRow(e: ExUi, s: WorkoutSetEntity, index: Int, vm: ActiveWorkoutVM, unit: WeightUnit, coach: Boolean, onSetMenu: (WorkoutSetEntity) -> Unit) {
    val tr = e.tracking
    val type = SetType.of(s.setType)
    val prev = vm.previousFor(e, s)
    val sug = vm.suggestion(e, s)
    val bg by animateColorAsState(if (s.completed) RupioColors.Success.copy(alpha = 0.13f) else Color.Transparent, label = "setbg")
    val workingNumber = e.sets.take(index + 1).count { it.setType != SetType.WARMUP.code }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Número / tipo de serie (TalkBack: "Serie 2, normal. Toca para cambiar el tipo")
        val setNo = index + 1
        val spokenSet = "Serie $setNo de ${e.name}, ${type.label.lowercase()}" + (s.rpe?.let { ", RPE ${Fmt.num(it)}" } ?: "")
        Box(
            Modifier.width(W_SET).heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "cambiar el tipo de serie o el RPE", role = Role.Button) { onSetMenu(s) }
                .clearAndSetSemantics { contentDescription = spokenSet; role = Role.Button }
                .then(if (coach) Modifier.coachTarget("set_number") else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (type == SetType.NORMAL) "$workingNumber" else type.short, style = MaterialTheme.typography.titleSmall.merge(Tabular),
                    color = when (type) { SetType.WARMUP -> RupioColors.Warning; SetType.DROP -> RupioColors.Rest; SetType.FAILURE -> RupioColors.Danger; else -> RupioColors.Text })
                s.rpe?.let { Text("@${Fmt.num(it)}", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint) }
            }
        }
        // Anterior
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(prev?.let { p -> prevText(p, tr, unit) } ?: "—", style = MaterialTheme.typography.labelMedium.merge(Tabular), color = RupioColors.TextFaint, maxLines = 2)
            if (s.isPr && s.completed) Text(" 🏆", style = MaterialTheme.typography.labelMedium)
        }
        // Peso / lastre / ayuda
        if (tr.usesWeight) {
            val shown = s.weightKg?.let { kg -> if (tr == Tracking.ASSISTED_BODYWEIGHT) -kg else kg }
            val ph = sug.weightKg?.let { kg -> if (tr == Tracking.ASSISTED_BODYWEIGHT) -kg else kg }
            Column(Modifier.width(W_VAL), horizontalAlignment = Alignment.CenterHorizontally) {
                val what = when (tr) { Tracking.WEIGHTED_BODYWEIGHT -> "Lastre"; Tracking.ASSISTED_BODYWEIGHT -> "Ayuda"; else -> "Peso" }
                NumberCell(
                    label = "$what en ${if (unit == WeightUnit.LB) "libras" else "kilos"}, serie $setNo, ${e.name}" + (ph?.let { ", sugerido ${Fmt.num(unit.fromKg(it))}" } ?: ""),
                    value = shown?.let { Fmt.num(unit.fromKg(it)) } ?: "",
                    placeholder = ph?.let { Fmt.num(unit.fromKg(it)) } ?: if (tr == Tracking.WEIGHT_REPS) unit.label else "0",
                    onValue = { txt ->
                        val v = txt.toDoubleOrNull()?.let { unit.toKg(kotlin.math.abs(it)).coerceAtMost(Fmt.MAX_KG) }
                        vm.edit(s.id) { it.copy(weightKg = v?.let { kg -> if (tr == Tracking.ASSISTED_BODYWEIGHT) -kg else kg }) }
                    },
                    done = s.completed, modifier = Modifier.fillMaxWidth(),
                )
                val kg = shown ?: ph
                Text(if (kg != null) Fmt.weight(kg, unit.other) else " ", style = MaterialTheme.typography.labelSmall.merge(Tabular), color = RupioColors.TextFaint, maxLines = 1)
            }
        }
        // Reps o segundos
        if (tr == Tracking.DURATION) {
            Column(Modifier.width(W_VAL), horizontalAlignment = Alignment.CenterHorizontally) {
                NumberCell(label = "Segundos, serie $setNo, ${e.name}" + (sug.durationSec?.let { ", sugerido $it" } ?: ""),
                    value = s.durationSec?.toString() ?: "", placeholder = sug.durationSec?.toString() ?: "seg", onValue =
                    { txt -> vm.edit(s.id) { it.copy(durationSec = txt.toIntOrNull()?.coerceIn(0, 86_400)) } }, decimal = false, done = s.completed, modifier = Modifier.fillMaxWidth())
                Text((s.durationSec ?: sug.durationSec)?.let { Fmt.duration(it.toLong()) } ?: " ", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
            }
        } else {
            Column(Modifier.width(W_REPS), horizontalAlignment = Alignment.CenterHorizontally) {
                NumberCell(label = "Repeticiones, serie $setNo, ${e.name}" + (sug.reps?.let { ", sugerido $it" } ?: ""),
                    value = s.reps?.toString() ?: "", placeholder = sug.reps?.toString() ?: "reps", onValue =
                    { txt -> vm.edit(s.id) { it.copy(reps = txt.toIntOrNull()?.coerceIn(0, 1000)) } }, decimal = false, done = s.completed, modifier = Modifier.fillMaxWidth())
                if (tr.usesWeight) Text(" ", style = MaterialTheme.typography.labelSmall)
            }
        }
        // ✓
        Box(
            Modifier.width(W_CHECK).height(40.dp).clip(RoundedCornerShape(10.dp))
                .background(if (s.completed) RupioColors.Success else RupioColors.Surface3)
                .clickable { vm.toggle(e, s) }
                .then(if (coach) Modifier.coachTarget("set_check") else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, if (s.completed) "Desmarcar serie $setNo de ${e.name}" else "Completar serie $setNo de ${e.name}", tint = if (s.completed) Color.Black else RupioColors.TextDim) }
    }
}

private fun prevText(p: SetData, tr: Tracking, unit: WeightUnit): String = when (tr) {
    Tracking.DURATION -> Fmt.duration((p.durationSec ?: 0).toLong())
    Tracking.REPS_ONLY -> "${p.reps ?: 0} reps"
    Tracking.ASSISTED_BODYWEIGHT -> "−${Fmt.num(unit.fromKg(-(p.weightKg ?: 0.0)))} × ${p.reps ?: 0}"
    Tracking.WEIGHTED_BODYWEIGHT -> if ((p.weightKg ?: 0.0) > 0) "+${Fmt.num(unit.fromKg(p.weightKg!!))} × ${p.reps ?: 0}" else "${p.reps ?: 0} reps"
    Tracking.WEIGHT_REPS -> "${Fmt.num(unit.fromKg(p.weightKg ?: 0.0))} ${unit.label} × ${p.reps ?: 0}"
}

// ============================================================================ descanso
@Composable
private fun RestBar(s: RestTimerController.State, timer: RestTimerController) {
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(s.generation, s.endsAt) { while (true) { nowMs = SystemClock.elapsedRealtime(); delay(200) } }
    val left = s.remainingMs(nowMs)
    val progress = (left / (s.totalSec * 1000f)).coerceIn(0f, 1f)
    Surface(color = RupioColors.Surface, tonalElevation = 6.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = RupioColors.Rest, trackColor = RupioColors.Surface3)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Descanso", style = MaterialTheme.typography.labelMedium, color = RupioColors.Rest)
                    Text(Fmt.duration((left + 999) / 1000), style = MaterialTheme.typography.headlineMedium.merge(Tabular))
                    Text(s.label, style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(onClick = { timer.adjust(-15) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("−15") }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick = { timer.adjust(15) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+15") }
                Spacer(Modifier.width(6.dp))
                Button(onClick = { timer.cancel() }, colors = ButtonDefaults.buttonColors(containerColor = RupioColors.Rest), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Saltar") }
            }
        }
    }
}

@Composable
private fun RestDoneBar(label: String, onClose: () -> Unit) {
    Surface(color = RupioColors.Rest, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = Color.Black)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("¡Listo! A la siguiente serie", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onClose, colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)) { Text("OK") }
        }
    }
}

// ============================================================================ opciones de serie
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SetOptionsSheet(e: ExUi, s: WorkoutSetEntity, vm: ActiveWorkoutVM, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RupioColors.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Serie ${e.sets.indexOfFirst { it.id == s.id } + 1} · ${e.name}", style = MaterialTheme.typography.titleMedium)
            Text("Tipo de serie", style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetType.entries.forEach { t ->
                    FilterChip(s.setType == t.code, { vm.setType(s, t) }, label = { Text(t.label) })
                }
            }
            Text("Calentamiento no cuenta para volumen ni récords. Drop set = bajas el peso y sigues sin descanso (no arranca el temporizador antes de él y no se usa para decidir cuándo subir).",
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿Qué tan difícil fue? (RPE)", style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
                com.rupio.app.ui.learn.HelpIcon("rir_rpe")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.rpe == null, { vm.setRpe(s, null) }, label = { Text("Sin RPE") })
                listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0).forEach { r ->
                    FilterChip(s.rpe == r, { vm.setRpe(s, r) }, label = { Text(Fmt.num(r)) })
                }
            }
            Text("RPE 10 = no podías hacer ni una más · 9 = te quedaba 1 · 8 = te quedaban 2. Para crecer, termina la mayoría de series a 0–3 reps del fallo.",
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            OutlinedButton(onClick = { vm.removeSet(s); onDismiss() }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RupioColors.Danger)) { Icon(Icons.Rounded.Delete, null); Spacer(Modifier.width(6.dp)); Text("Eliminar serie") }
        }
    }
}

// ============================================================================ terminar
/**
 * Terminar el entreno a PANTALLA COMPLETA (antes era un diálogo chico con tarjetas, dos campos y scroll: con el
 * teclado abierto quedaba apretado). Material indica diálogo de pantalla completa para tareas con teclado.
 */
@Composable
private fun FinishDialog(name: String, items: List<ExUi>, doneSets: Int, volumeKg: Double, unit: WeightUnit, durationSec: Long, onDismiss: () -> Unit,
                         onDiscard: () -> Unit = {}, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var notes by remember { mutableStateOf("") }
    val pending = items.sumOf { e -> e.sets.count { !it.completed } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = RupioColors.Black) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Seguir entrenando") }
                    Text(if (doneSets == 0) "No has completado series" else "Terminar entrenamiento", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (doneSets > 0) Button(onClick = { onSave(n, notes) }, modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RupioColors.Success, contentColor = Color.Black)) { Text("Guardar", fontWeight = FontWeight.SemiBold) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TileRow {
                        Tile("Duración", Fmt.durationHuman(durationSec))
                        Tile("Series", "$doneSets")
                        Tile("Volumen", Fmt.volume(volumeKg, unit), sub = Fmt.volume(volumeKg, unit.other))
                    }
                    if (doneSets == 0) Text("Para guardar, marca con ✓ al menos una serie. Si no entrenaste, puedes descartarlo.", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                    else if (pending > 0) Text("Hay $pending series sin marcar: no se guardarán.", color = RupioColors.Warning, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(n, { n = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("¿Cómo te sentiste? (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
                    if (doneSets == 0) Button(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RupioColors.Danger, contentColor = Color.Black)) { Text("Descartar entrenamiento") }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Seguir entrenando") }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
