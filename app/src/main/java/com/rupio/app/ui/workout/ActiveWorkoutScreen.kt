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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.saveable.rememberSaveable
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
    // v1.8: ¿se va a oír el fin del descanso? Se revisa cada 3 s (el volumen se cambia con los botones del teléfono).
    var inaudible by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings?.restAlert) {
        val mode = settings?.restAlert ?: return@LaunchedEffect
        while (true) { inaudible = com.rupio.app.timer.RestAlert.inaudibleReason(context, mode); delay(3_000) }
    }

    // Pantalla encendida mientras entrenas (se puede apagar en Perfil → Pantalla encendida al entrenar).
    val view = LocalView.current
    val keepOn = settings?.keepScreenOn ?: true
    DisposableEffect(keepOn) { view.keepScreenOn = keepOn; onDispose { view.keepScreenOn = false } }

    // Si ya no hay entrenamiento activo (terminado o descartado), volver.
    LaunchedEffect(ui) { if (ui != null && ui?.workout == null) nav.safePop() }

    // v1.9: el permiso de notificaciones se pide al marcar la PRIMERA serie (cuando arranca el primer descanso y se
    // entiende para qué sirve), ya no al tocar "Empezar rutina" entre la guía y el entreno.
    var askNotif by remember { mutableStateOf(false) }
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        notifOk = com.rupio.app.timer.canNotify(context)
    }
    fun maybeAskNotif() {
        val s = settings ?: return
        if (android.os.Build.VERSION.SDK_INT >= 33 && !s.notifAsked && !com.rupio.app.timer.canNotify(context)) askNotif = true
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is WorkoutEvent.Message -> snackbar.showSnackbar(e.text)
                is WorkoutEvent.Record -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    maybeAskNotif()
                    snackbar.showSnackbar("🏆 ¡Nuevo récord en ${e.exercise}! " + e.kinds.joinToString(" · ") { it.label })
                }
                WorkoutEvent.Completed -> { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); maybeAskNotif() }
                is WorkoutEvent.Undoable -> {
                    // v1.9: 10 s para deshacer (con 5 s el aviso se iba mientras lo tocabas).
                    val r = kotlinx.coroutines.withTimeoutOrNull(UNDO_MS) {
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

    // Tutorial del entrenamiento (una sola vez). v1.9: solo 2 globos; lo demás lo explica cada control al tocarlo
    // (el número de serie, el descanso y la recomendación abren su explicación). Antes eran 6 encima de las series.
    LaunchedEffect(settings?.tutorialWorkout, items.isNotEmpty()) {
        val s = settings ?: return@LaunchedEffect
        if (s.tutorialWorkout || items.isEmpty() || coach.active) return@LaunchedEffect
        delay(800)
        coach.start(listOf(
            CoachStep("set_check", "Marca cada serie", "Escribe peso y reps (o deja el valor gris sugerido) y toca ✓. Arranca el descanso solo."),
            CoachStep("finish", "Termina y guarda", "Al terminar se guardan tu volumen y tus marcas. ¡A entrenar!"),
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
    // Cardio marcado (segundos) y si todo lo marcado es cardio (para no mostrar "0 lb").
    val cardioSec = items.filter { it.isCardio }.sumOf { e -> e.sets.filter { it.completed }.sumOf { (it.durationSec ?: 0).toLong() } }
    val onlyCardio = cardioSec > 0 && items.all { e -> e.isCardio || e.sets.none { it.completed } }

    Scaffold(
        containerColor = RupioColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.background(RupioColors.Background)) {
                // Con letra muy grande el nombre va debajo de la barra (completo) en vez de cortarse junto a "Terminar".
                val bigText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                // v1.9: con letra grande o en horizontal el encabezado fijo ocupaba más de la mitad de la pantalla
                // (3 tarjetas apiladas): ahí va una sola línea y las series tienen el resto.
                val compactHeader = bigText || androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
                val started = "Empezó a las " + Dates.formatTimeWithOrigin(w.startedAt, zone, w.timeZone)
                RupioTopBar(if (bigText) "" else w.name, onBack = { nav.safePop() }, subtitle = if (compactHeader) null else started) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Cambiar nombre", tint = RupioColors.TextDim) }
                    Button(onClick = { finishing = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(end = 8.dp).coachTarget("finish"),
                        colors = ButtonDefaults.buttonColors(containerColor = RupioColors.Success, contentColor = Color.Black)) { Text("Terminar", fontWeight = FontWeight.SemiBold) }
                }
                if (bigText) Text(w.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp))
                // v1.9 (R2): el cardio que ya anotaste cuenta en la duración (20 min de caminadora no son "3:12"), y sin
                // pesas marcadas no se muestra "0 lb".
                val clockSec = ((now - w.startedAt) / 1000).coerceAtLeast(0)
                val dur = Fmt.duration(maxOf(clockSec, cardioSec))
                val showVolume = volume > 0.0 || !onlyCardio
                val volText = if (showVolume) " · ${Fmt.volume(volume, unit)}" else ""
                if (compactHeader) {
                    FitText("$dur$volText · ${Fmt.plural(doneSets, "serie", "series")}", MaterialTheme.typography.titleMedium.merge(Tabular),
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                            .semantics { contentDescription = "Duración $dur" + (if (showVolume) ", volumen ${Fmt.volume(volume, unit)}" else "") + ", ${Fmt.plural(doneSets, "serie", "series")}" },
                        color = RupioColors.TextDim, maxLines = 1)
                } else TileRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Tile("Duración", dur)
                    if (showVolume) Tile("Volumen", Fmt.volume(volume, unit), sub = Fmt.volume(volume, unit.other))
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
            // Solo si ya se pidió (y se negó) o si Android las tiene apagadas: antes de preguntar no se asusta a nadie.
            if (!notifOk && (android.os.Build.VERSION.SDK_INT < 33 || settings?.notifAsked == true)) item { NotificationsOffCard() }
            // v1.8: si el aviso no se va a oír (volumen en 0, o "Como notificación" con el teléfono en vibración), se dice aquí.
            inaudible?.let { why -> item { InaudibleCard(why, settings?.restAlert ?: com.rupio.app.data.prefs.RestAlertMode.MEDIA) } }
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
    val bodyKg by produceState<Double?>(null, finishing) { if (finishing) value = vm.latestBodyKg() }
    fun done(prs: Int) {
        if (!c.auth.isLocalOnly) SyncEngine.now(context)
        nav.navigate(Routes.detail(w.id, 1 + prs)) { popUpTo(Routes.WORKOUT) { inclusive = true } }
    }
    if (finishing) FinishDialog(w.name, items, vm.unmarkedWithData(items), doneSets, volume, unit, ((now - w.startedAt) / 1000).coerceAtLeast(0), bodyKg, { finishing = false },
        onDiscard = { finishing = false; discarding = true },
        onMarkAndSave = { name, notes, sets -> finishing = false; scope.launch { vm.finishMarking(w.id, name, notes, sets)?.let { done(it) } } }) { name, notes ->
        finishing = false
        scope.launch { done(vm.finish(w.id, name, notes)) }
    }
    // v1.9: un entreno sin nada registrado se descarta sin preguntar (no hay nada que perder).
    LaunchedEffect(discarding) { if (discarding && !vm.hasAnyData(items)) { discarding = false; scope.launch { vm.discard(w.id) } } }
    if (discarding && vm.hasAnyData(items)) ConfirmDialog("¿Descartar entrenamiento?", "Se borrará todo lo registrado en esta sesión. No se puede deshacer.", "Descartar", { discarding = false }, danger = true) {
        discarding = false; scope.launch { vm.discard(w.id) }
    }
    if (renaming) RenameDialog("Nombre del entrenamiento", w.name, { renaming = false }) { vm.rename(w.id, it); renaming = false }
    restFor?.let { e ->
        RestPickerDialog("Descanso · ${e.name}", e.we.restSec, { restFor = null }, { vm.setRest(e, it); restFor = null }) {
            Text("Arranca solo al marcar ✓ y suena una sola vez al terminar, aunque marques y desmarques. Más de 60–90 s entre series ayuda a rendir; en ejercicios pesados 2–3 min.",
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 10.dp))
        }
    }
    if (askNotif) AlertDialog(
        onDismissRequest = { askNotif = false; scope.launch { c.settings.setNotifAsked(true) } },
        icon = { Icon(Icons.Rounded.NotificationsActive, null, tint = RupioColors.Rest) },
        title = { Text("¿Te avisamos al terminar el descanso?") },
        text = { Text("Tu descanso ya empezó. Para avisarte cuando termine, aunque tengas la pantalla apagada, Rupio necesita mostrar notificaciones. Solo suena al final de cada descanso.", color = RupioColors.TextDim) },
        confirmButton = { TextButton(onClick = {
            askNotif = false; scope.launch { c.settings.setNotifAsked(true) }
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }) { Text("Permitir") } },
        dismissButton = { TextButton(onClick = { askNotif = false; scope.launch { c.settings.setNotifAsked(true) } }) { Text("Ahora no") } },
    )
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
        // v1.9: el descanso que ya elegiste en este ejercicio (antes tomaba el mayor de los dos y volvía a 2 min sin avisar).
        SupersetRestDialog("Superserie: " + members.joinToString(" + ") { it.name.substringBefore(" con ") },
            existing?.let { SupersetRestMode.of(it.supersetRestMode) } ?: SupersetRestMode.SHARED,
            existing?.supersetRestSec ?: members.firstOrNull()?.we?.restSec ?: 90,
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
                // v1.9 (N8): la foto es un botón con nombre (TalkBack decía solo "botón").
                Box(Modifier.clickable(onClickLabel = "ver técnica", role = Role.Button, onClick = onOpen)
                    .clearAndSetSemantics { contentDescription = "Ver técnica de ${e.name}"; role = Role.Button }) { ExerciseThumb(e.ex?.image0, 46.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ssIdx != null && member != null) { SupersetTag(ssIdx, member); Spacer(Modifier.width(6.dp)) }
                        Text(e.name, style = MaterialTheme.typography.titleMedium, color = RupioColors.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${Muscles.label(e.ex?.primaryMuscle ?: "")} · ${if (tr == Tracking.DURATION) "tiempo" else "${e.we.repMin}–${e.we.repMax} reps"}",
                        style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
                Box {
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
                RecommendationCard(info.rec, unit)
            }
            Spacer(Modifier.height(8.dp))
            var showChart by rememberSaveable(e.we.id) { mutableStateOf(false) }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // v1.9: con descanso compartido el descanso vive SOLO en la cabecera de la superserie (antes salía 3 veces).
                if (!shared) Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(RupioColors.Rest.copy(alpha = 0.12f)).clickable(onClick = onRest)
                        .heightIn(min = 36.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = RupioColors.Rest, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Descanso: ${Fmt.restLabel(e.we.restSec)}", color = RupioColors.Rest, style = MaterialTheme.typography.labelLarge)
                }
                // v1.8: gráfica de cómo ha ido subiendo este ejercicio, sin salir del entreno.
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(RupioColors.Chart.copy(alpha = if (showChart) 0.22f else 0.12f))
                        .toggleable(showChart, role = Role.Switch, onValueChange = { showChart = it })
                        .heightIn(min = 36.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ShowChart, null, tint = RupioColors.Chart, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Progreso", color = RupioColors.Chart, style = MaterialTheme.typography.labelLarge)
                    Icon(if (showChart) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = RupioColors.Chart, modifier = Modifier.size(16.dp))
                }
            }
            AnimatedVisibility(showChart) { ExerciseProgressPanel(e, vm, unit) }
            Spacer(Modifier.height(8.dp))
            val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
            val bigText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
            SetHeader(tr, unit, compact, bigText, cardio = e.isCardio)
            e.sets.forEachIndexed { i, s ->
                SetRow(e, s, i, vm, unit, first && i == 0, compact, onSetMenu)
            }
            // v1.9: "Agregar calentamiento" pone una serie C antes de la primera (convertir la serie 1 te quitaba una de trabajo).
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { vm.addSet(e) }) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Agregar serie")
                }
                if (tr != Tracking.DURATION) TextButton(onClick = { vm.addWarmup(e) }, colors = ButtonDefaults.textButtonColors(contentColor = RupioColors.Warning)) {
                    Icon(Icons.Rounded.Whatshot, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Agregar calentamiento")
                }
            }
        }
    }
}

private val W_SET = 32.dp; private val W_VAL = 70.dp; private val W_REPS = 56.dp; private val W_CHECK = 46.dp
/** v1.8: todas las celdas de una serie miden lo mismo (antes el "kg" iba debajo y descuadraba la fila). */
private val CELL_H = 50.dp
/** Separación entre cajitas y margen de la zona verde (más aire que antes; en pantallas angostas, menos). */
private fun gap(compact: Boolean) = if (compact) 4.dp else 8.dp
private fun rowPadH(compact: Boolean) = if (compact) 2.dp else 6.dp

/** v1.9: con letra grande los números de la tabla crecen hasta 150 % (más no cabe en la fila: "45.4 …", "REP / S"). */
private const val TABLE_MAX_SCALE = 1.5f

@Composable
private fun SetHeader(tr: Tracking, unit: WeightUnit, compact: Boolean, bigText: Boolean, cardio: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = rowPadH(compact), vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap(compact))) {
        val st = cappedScale(MaterialTheme.typography.labelSmall, TABLE_MAX_SCALE)
        // Encabezados cortos donde no cabe el largo (letra grande o pantalla de 320 dp: "SE", "ANTERI…").
        Text(if (bigText) "#" else "SERIE", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_SET), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        Text(if (compact || bigText) "ANTES" else "ANTERIOR", style = st, color = RupioColors.TextFaint, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false,
            overflow = TextOverflow.Clip)
        when (tr) {
            Tracking.WEIGHT_REPS -> Text(unit.label.uppercase(), style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_VAL), textAlign = TextAlign.Center, maxLines = 1)
            Tracking.WEIGHTED_BODYWEIGHT -> Text("+${unit.label.uppercase()}", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_VAL), textAlign = TextAlign.Center, maxLines = 1)
            Tracking.ASSISTED_BODYWEIGHT -> Text("AYUDA", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_VAL), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            else -> {}
        }
        when (tr) {
            Tracking.DURATION -> Text(if (cardio) "MIN" else if (bigText) "SEG" else "SEGUNDOS", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_VAL), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            else -> Text("REPS", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_REPS), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
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
private fun SetRow(e: ExUi, s: WorkoutSetEntity, index: Int, vm: ActiveWorkoutVM, unit: WeightUnit, coach: Boolean, compact: Boolean, onSetMenu: (WorkoutSetEntity) -> Unit) {
    val tr = e.tracking
    val type = SetType.of(s.setType)
    val prev = vm.previousFor(e, s)
    val sug = vm.suggestion(e, s)
    val bg by animateColorAsState(if (s.completed) RupioColors.Success.copy(alpha = 0.13f) else Color.Transparent, label = "setbg")
    val workingNumber = e.sets.take(index + 1).count { it.setType != SetType.WARMUP.code }
    // v1.8: la zona verde lleva aire arriba, abajo y a los lados, y las cajitas tienen más separación entre sí.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .padding(horizontal = rowPadH(compact), vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap(compact)),
    ) {
        // Número / tipo de serie (TalkBack: "Serie 2 de Press, normal"). v1.9: el número que se ve (los calentamientos
        // no cuentan), igual que la barra del descanso y la notificación.
        val setNo = com.rupio.app.domain.SetNumbering.spoken(e.sets.map { it.setType }, index)
        val spokenSet = "${setNo.replaceFirstChar { it.uppercase() }} de ${e.name}, ${type.label.lowercase()}" + (s.rpe?.let { ", RPE ${Fmt.num(it)}" } ?: "")
        Box(
            Modifier.width(W_SET).heightIn(min = CELL_H).clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "cambiar el tipo de serie o el RPE", role = Role.Button) { onSetMenu(s) }
                .clearAndSetSemantics { contentDescription = spokenSet; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (type == SetType.NORMAL) "$workingNumber" else type.short, style = cappedScale(MaterialTheme.typography.titleSmall.merge(Tabular), TABLE_MAX_SCALE),
                    color = when (type) { SetType.WARMUP -> RupioColors.Warning; SetType.DROP -> RupioColors.Rest; SetType.FAILURE -> RupioColors.Danger; else -> RupioColors.Text })
                s.rpe?.let { Text("@${Fmt.num(it)}", style = cappedScale(MaterialTheme.typography.labelSmall, TABLE_MAX_SCALE), color = RupioColors.TextFaint) }
            }
        }
        // Anterior
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(prev?.let { p -> prevText(p, tr, unit) } ?: "—", style = cappedScale(MaterialTheme.typography.labelMedium.merge(Tabular), TABLE_MAX_SCALE), color = RupioColors.TextFaint, maxLines = 2,
                modifier = Modifier.weight(1f, fill = false))
            if (s.isPr && s.completed) Text(" 🏆", style = MaterialTheme.typography.labelMedium)
        }
        // Peso / lastre / ayuda: el número y, DENTRO de la misma cajita, la otra unidad ("50" y "22.7 kg").
        if (tr.usesWeight) {
            val shown = s.weightKg?.let { kg -> if (tr == Tracking.ASSISTED_BODYWEIGHT) -kg else kg }
            val ph = sug.weightKg?.let { kg -> if (tr == Tracking.ASSISTED_BODYWEIGHT) -kg else kg }
            val what = when (tr) { Tracking.WEIGHTED_BODYWEIGHT -> "Lastre"; Tracking.ASSISTED_BODYWEIGHT -> "Ayuda"; else -> "Peso" }
            val kg = shown ?: ph
            NumberCell(
                label = "$what en ${if (unit == WeightUnit.LB) "libras" else "kilos"}, $setNo, ${e.name}" + (ph?.let { ", sugerido ${Fmt.num(unit.fromKg(it))}" } ?: "") +
                    (kg?.let { ", ${Fmt.weight(it, unit.other)}" } ?: ""),
                value = shown?.let { Fmt.num(unit.fromKg(it)) } ?: "",
                placeholder = ph?.let { Fmt.num(unit.fromKg(it)) } ?: if (tr == Tracking.WEIGHT_REPS) unit.label else "0",
                onValue = { txt ->
                    val v = txt.toDoubleOrNull()?.let { unit.toKg(kotlin.math.abs(it)).coerceAtMost(Fmt.MAX_KG) }
                    vm.edit(s.id) { it.copy(weightKg = v?.let { kg -> if (tr == Tracking.ASSISTED_BODYWEIGHT) -kg else kg }) }
                },
                done = s.completed, modifier = Modifier.width(W_VAL),
                sub = if (kg != null && kg != 0.0) Fmt.weight(kg, unit.other) else null, minHeight = CELL_H, maxFontScale = TABLE_MAX_SCALE,
            )
        }
        // Reps, segundos o (cardio) minutos
        if (e.isCardio) {
            // v1.9 (N1): 20 minutos de caminadora se escriben "20", no "1200"; abajo sigue "20:00".
            val sugMin = sug.durationSec?.let { Cardio.minutesText(it) }
            NumberCell(label = "Minutos, $setNo, ${e.name}" + (sugMin?.let { ", sugerido $it" } ?: ""),
                value = Cardio.minutesText(s.durationSec), placeholder = sugMin ?: "min", onValue =
                { txt -> vm.edit(s.id) { it.copy(durationSec = Cardio.parseMinutes(txt)) } }, decimal = true, done = s.completed,
                modifier = Modifier.width(W_VAL), sub = (s.durationSec ?: sug.durationSec)?.let { Fmt.duration(it.toLong()) }, minHeight = CELL_H, maxFontScale = TABLE_MAX_SCALE)
        } else if (tr == Tracking.DURATION) {
            NumberCell(label = "Segundos, $setNo, ${e.name}" + (sug.durationSec?.let { ", sugerido $it" } ?: ""),
                value = s.durationSec?.toString() ?: "", placeholder = sug.durationSec?.toString() ?: "seg", onValue =
                { txt -> vm.edit(s.id) { it.copy(durationSec = txt.toIntOrNull()?.coerceIn(0, 86_400)) } }, decimal = false, done = s.completed,
                modifier = Modifier.width(W_VAL), sub = (s.durationSec ?: sug.durationSec)?.let { Fmt.duration(it.toLong()) }, minHeight = CELL_H, maxFontScale = TABLE_MAX_SCALE)
        } else {
            NumberCell(label = "Repeticiones, $setNo, ${e.name}" + (sug.reps?.let { ", sugerido $it" } ?: ""),
                value = s.reps?.toString() ?: "", placeholder = sug.reps?.toString() ?: "reps", onValue =
                { txt -> vm.edit(s.id) { it.copy(reps = txt.toIntOrNull()?.coerceIn(0, 1000)) } }, decimal = false, done = s.completed,
                modifier = Modifier.width(W_REPS), minHeight = CELL_H, maxFontScale = TABLE_MAX_SCALE)
        }
        // ✓
        Box(
            Modifier.width(W_CHECK).height(CELL_H).clip(RoundedCornerShape(12.dp))
                .background(if (s.completed) RupioColors.Success else RupioColors.Surface3)
                .clickable { vm.toggle(e, s) }
                .then(if (coach) Modifier.coachTarget("set_check") else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, if (s.completed) "Desmarcar $setNo de ${e.name}" else "Completar $setNo de ${e.name}", tint = if (s.completed) Color.Black else RupioColors.TextDim) }
    }
}

/** Qué se grafica dentro del entreno. */
private enum class ProgressMetric(val label: String) { TOP("Peso máx."), E1RM("1RM est."), VOLUME("Volumen"), REPS("Reps"), TIME("Tiempo"), ASSIST("Ayuda") }

private fun metricsFor(tr: Tracking): List<ProgressMetric> = when (tr) {
    Tracking.WEIGHT_REPS, Tracking.WEIGHTED_BODYWEIGHT -> listOf(ProgressMetric.TOP, ProgressMetric.E1RM, ProgressMetric.VOLUME)
    Tracking.ASSISTED_BODYWEIGHT -> listOf(ProgressMetric.ASSIST, ProgressMetric.REPS)
    Tracking.REPS_ONLY -> listOf(ProgressMetric.REPS)
    Tracking.DURATION -> listOf(ProgressMetric.TIME)
}

/**
 * v1.8 · "Mostrar gráfica en el entrenamiento de cómo ha ido aumentando": las últimas 30 sesiones de este ejercicio,
 * con lo que marques hoy incluido (se actualiza al palomear cada serie).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseProgressPanel(e: ExUi, vm: ActiveWorkoutVM, unit: WeightUnit) {
    val tr = e.tracking
    val metrics = metricsFor(tr)
    var metric by rememberSaveable(e.we.id) { mutableStateOf(metrics.first()) }
    val doneKey = e.sets.filter { it.completed }.joinToString { "${it.id}:${it.weightKg}:${it.reps}:${it.durationSec}" }
    val rows by produceState<List<com.rupio.app.data.local.SessionProgressRow>?>(null, e.we.exerciseId, doneKey) {
        value = vm.progress(e.we.exerciseId)
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(RupioColors.Surface2).padding(12.dp)) {
        if (metrics.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            metrics.forEach { m -> FilterChip(metric == m, { metric = m }, label = { Text(m.label) }) }
        }
        val data = rows
        if (data == null) { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }; return@Column }
        val today = Dates.today(java.time.ZoneId.systemDefault()).toEpochDay().toInt()
        // El volumen de hoy va a medias mientras entrenas (parecería que bajó): se suma al terminar.
        val pts = data.filter { metric != ProgressMetric.VOLUME || it.performedOn != today }.mapNotNull { r ->
            val d = java.time.LocalDate.ofEpochDay(r.performedOn.toLong())
            when (metric) {
                ProgressMetric.TOP -> r.topWeight?.takeIf { it > 0 }
                ProgressMetric.E1RM -> r.e1rm
                ProgressMetric.VOLUME -> r.volume.takeIf { it > 0 }
                ProgressMetric.REPS -> r.maxReps?.toDouble()
                ProgressMetric.TIME -> r.maxDuration?.toDouble()
                ProgressMetric.ASSIST -> r.topWeight?.let { kotlin.math.abs(it) }
            }?.let { d to it }
        }
        val empty = "Con dos sesiones de este ejercicio verás aquí cómo va subiendo."
        when (metric) {
            ProgressMetric.TOP, ProgressMetric.E1RM, ProgressMetric.ASSIST ->
                ProgressLineChart(pts, unit, title = metric.label, chartHeight = 120.dp, emptyText = empty)
            ProgressMetric.VOLUME -> ProgressLineChart(pts, unit, title = "Volumen", chartHeight = 120.dp, emptyText = empty,
                label = { Fmt.volume(it, unit) }, subLabel = { Fmt.volume(it, unit.other) }, deltaLabel = { Fmt.volume(it, unit) })
            ProgressMetric.REPS -> ProgressLineChart(pts, unit, title = "Repeticiones", chartHeight = 120.dp, emptyText = empty, toDisplay = { it },
                label = { "${it.toInt()} reps" }, subLabel = null, deltaLabel = { "${it.toInt()} reps" })
            ProgressMetric.TIME -> ProgressLineChart(pts, unit, title = "Tiempo", chartHeight = 120.dp, emptyText = empty, toDisplay = { it },
                label = { Fmt.duration(it.toLong()) }, subLabel = null, deltaLabel = { Fmt.duration(it.toLong()) })
        }
        Text(when (metric) {
            ProgressMetric.TOP -> "Tu serie más pesada de cada sesión (sin calentamientos)."
            ProgressMetric.E1RM -> "Lo que podrías levantar 1 vez, calculado con tu mejor serie (hasta 12 reps)."
            ProgressMetric.VOLUME -> "Peso × repeticiones de todas tus series de este ejercicio en cada sesión."
            ProgressMetric.REPS -> "Tu mejor serie de cada sesión."
            ProgressMetric.TIME -> "Tu mejor tiempo de cada sesión."
            ProgressMetric.ASSIST -> "La menor ayuda que usaste en cada sesión: si baja, vas progresando."
        } + when {
            metric == ProgressMetric.VOLUME -> " El de hoy se suma al terminar."
            data.lastOrNull()?.performedOn == today -> " Incluye lo que llevas hoy."
            else -> ""
        },
            style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint, modifier = Modifier.padding(top = 6.dp))
    }
}

/** v1.8: el aviso de fin de descanso no se va a oír; se explica por qué y se puede probar. */
@Composable
private fun InaudibleCard(why: String, mode: com.rupio.app.data.prefs.RestAlertMode) {
    val ctx = LocalContext.current
    Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Warning.copy(alpha = 0.12f), border = BorderStroke(1.dp, RupioColors.Warning.copy(alpha = 0.5f))) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeOff, null, tint = RupioColors.Warning)
                Spacer(Modifier.width(10.dp))
                Text("No vas a oír el fin del descanso", style = MaterialTheme.typography.titleSmall)
            }
            Text(why, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
            TextButton(modifier = Modifier.align(Alignment.End), onClick = { com.rupio.app.timer.RestAlert.preview(ctx, mode) }) { Text("Probar sonido") }
        }
    }
}

private fun prevText(p: SetData, tr: Tracking, unit: WeightUnit): String = when (tr) {
    Tracking.DURATION -> Fmt.duration((p.durationSec ?: 0).toLong())
    Tracking.REPS_ONLY -> "${p.reps ?: 0} reps"
    Tracking.ASSISTED_BODYWEIGHT -> "−${Fmt.num(unit.fromKg(-(p.weightKg ?: 0.0)))} × ${p.reps ?: 0}"
    Tracking.WEIGHTED_BODYWEIGHT -> if ((p.weightKg ?: 0.0) > 0) "+${Fmt.num(unit.fromKg(p.weightKg!!))} × ${p.reps ?: 0}" else "${p.reps ?: 0} reps"
    // v1.8: sin "lb"/"kg" (ya lo dice la columna de al lado): así cabe en una línea y no se parte "× 8".
    Tracking.WEIGHT_REPS -> "${Fmt.num(unit.fromKg(p.weightKg ?: 0.0))} × ${p.reps ?: 0}"
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
    // v1.9 (#6): se abre completa; a media altura "Eliminar serie" quedaba debajo de la barra de gestos.
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RupioColors.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // v1.9: con scroll y por encima de la barra de gestos (antes "Eliminar serie" quedaba debajo de la rayita de Android).
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val idx = e.sets.indexOfFirst { it.id == s.id }
            Text(com.rupio.app.domain.SetNumbering.spoken(e.sets.map { it.setType }, idx).replaceFirstChar { it.uppercase() } + " · ${e.name}",
                style = MaterialTheme.typography.titleMedium)
            Text("Tipo de serie", style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetType.entries.forEach { t ->
                    FilterChip(s.setType == t.code, { vm.setType(s, t) }, label = { Text(t.label) })
                }
            }
            Text("Calentamiento: no cuenta para tu volumen ni tus récords y después descansas poco. Para sumar uno sin quitar una serie de trabajo, usa «Agregar calentamiento». " +
                "Drop set: bajas el peso y sigues sin descansar.",
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
private fun FinishDialog(name: String, items: List<ExUi>, withData: List<Pair<ExUi, WorkoutSetEntity>>, doneSets: Int, volumeKg: Double, unit: WeightUnit,
                         durationSec: Long, bodyKg: Double?, onDismiss: () -> Unit, onDiscard: () -> Unit = {},
                         onMarkAndSave: (String, String, List<Pair<ExUi, WorkoutSetEntity>>) -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var notes by remember { mutableStateOf("") }
    // v1.9 (N2): el cardio que anotaste cuenta en la duración (si es más que el reloj) y en las calorías con su MET.
    val cardio = remember(items) { items.filter { it.isCardio }.flatMap { e -> e.sets.filter { it.completed }.map { CalorieEstimate.CardioPart(Cardio.met(e.we.exerciseId), it.durationSec ?: 0) } } }
    val shownSec = maxOf(durationSec, cardio.sumOf { it.sec.toLong() })
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = RupioColors.Background) {
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
                        Tile("Duración", Fmt.durationHuman(shownSec))
                        Tile("Series", "$doneSets")
                        if (volumeKg > 0) Tile("Volumen", Fmt.volume(volumeKg, unit), sub = Fmt.volume(volumeKg, unit.other))
                    }
                    // v1.8: calorías estimadas y reparto de músculos de lo que marcaste.
                    CalorieEstimate.estimate(shownSec, doneSets - cardio.size, bodyKg, cardio)?.let { kcal -> TileRow { CaloriesTile(kcal) } }
                    val split = remember(items) { muscleSplitOf(items) }
                    if (split.isNotEmpty()) SectionCard {
                        Text("Reparto de músculos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(10.dp))
                        MuscleSplitBars(split)
                    }
                    // v1.9: las series vacías del plan no necesitan aviso; las que tienen peso o reps escritos sin ✓ sí
                    // (antes se juntaban en "Hay 16 series sin marcar" y la que tenía datos se borraba igual).
                    if (withData.isNotEmpty()) Surface(shape = RoundedCornerShape(14.dp), color = RupioColors.Warning.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, RupioColors.Warning.copy(alpha = 0.5f))) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (withData.size == 1) "1 serie tiene datos pero no la marcaste" else "${withData.size} series tienen datos pero no las marcaste",
                                style = MaterialTheme.typography.titleSmall)
                            Text(withData.groupBy { it.first.we.id }.values.joinToString(" · ") { l ->
                                l.first().first.name + ": " + l.joinToString(", ") { (e, s) -> SetNumbering.spoken(e.sets.map { it.setType }, e.sets.indexOfFirst { it.id == s.id }) }
                            }, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                            Text(if (withData.size == 1) "Si guardas sin marcarla, no se guarda." else "Si guardas sin marcarlas, no se guardan.", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                            Button(onClick = { onMarkAndSave(n, notes, withData) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RupioColors.Success, contentColor = Color.Black)) {
                                Text(if (withData.size == 1) "Marcar y guardar" else "Marcarlas y guardar", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (doneSets == 0 && withData.isEmpty()) Text("Para guardar, marca con ✓ al menos una serie. Si no entrenaste, puedes descartarlo.", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                    else if (doneSets > 0 && items.any { e -> e.sets.any { !it.completed } }) Text("Las series vacías que no marcaste no se guardan.",
                        style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    OutlinedTextField(n, { n = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("¿Cómo te sentiste? (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
                    if (doneSets == 0 && withData.isEmpty()) Button(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RupioColors.Danger, contentColor = Color.Black)) { Text("Descartar entrenamiento") }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Seguir entrenando") }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Series efectivas marcadas por ejercicio → reparto de músculos (principal 1 · secundario 0.5). */
fun muscleSplitOf(items: List<ExUi>): List<MuscleShare> = MuscleSplit.compute(items.mapNotNull { e ->
    val ex = e.ex ?: return@mapNotNull null
    MuscleSplit.Item(ex.primaryMuscle, ex.secondaryMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        e.sets.count { it.completed && it.setType != SetType.WARMUP.code })
})
