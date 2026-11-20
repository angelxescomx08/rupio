package com.rupio.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.goToTab
import com.rupio.app.container
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.data.auth.AuthState
import com.rupio.app.data.local.RoutineEntity
import com.rupio.app.data.local.RoutineExerciseEntity
import com.rupio.app.domain.PlanItem
import com.rupio.app.domain.RestPlanner
import com.rupio.app.domain.SupersetRestMode
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.launch
import java.time.LocalTime
import com.rupio.app.core.tr
import com.rupio.app.domain.SupportPolicy
import com.rupio.app.ui.support.SupporterAvatar
import com.rupio.app.ui.support.WallPreviewCard
import com.rupio.app.ui.support.wallAccess

fun RoutineExerciseEntity.toPlan() = PlanItem(id, targetSets, restSec, supersetId, SupersetRestMode.of(supersetRestMode), supersetRestSec)

@Composable
fun HomeScreen(nav: NavHostController) {
    val c = container()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val name = (auth as? AuthState.SignedIn)?.name.orEmpty()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val routines by c.routines.routines.collectAsStateWithLifecycle(initialValue = null)
    val items by c.routines.allExercises.collectAsStateWithLifecycle(initialValue = emptyList())
    val catalog by c.exercises.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val active by c.workouts.active.collectAsStateWithLifecycle(initialValue = null)
    val recent by c.workouts.observeRecent(40).collectAsStateWithLifecycle(initialValue = emptyList())
    val zone by c.zone.collectAsStateWithLifecycle()
    val syncSt by c.sync.status.collectAsStateWithLifecycle()
    // Recién entraste con tu cuenta y aún no termina la primera descarga: no decir "aún no tienes rutinas".
    val firstDownload = (auth as? AuthState.SignedIn)?.localOnly == false && syncSt.lastSuccess == null
    val names = remember(catalog) { catalog.associate { it.id to it.name } }
    val byRoutine = remember(items) { items.groupBy { it.routineId } }
    var toDelete by remember { mutableStateOf<RoutineEntity?>(null) }
    // v1.9: tr("Ordenar rutinas", "Reorder routines") (null = cerrada; "" = abierta sin resaltar ninguna).
    var reorderFocus by remember { mutableStateOf<String?>(null) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun openReorder(id: String?) {
        if ((routines?.size ?: 0) < 2) return
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        reorderFocus = id ?: ""
    }
    val unit = settings?.unit ?: com.rupio.app.core.WeightUnit.LB
    LaunchedEffect(Unit) { com.rupio.app.ui.learn.LearnProgress.load(context) }
    val learned by com.rupio.app.ui.learn.LearnProgress.done.collectAsStateWithLifecycle()
    // v1.9: qué rutina toca (la que sigue a la última que terminaste) y cuándo hiciste cada una.
    val lastDone by c.workouts.routineLastDone.collectAsStateWithLifecycle(initialValue = emptyList())
    val lastDay = remember(lastDone) { lastDone.associate { it.routineId to it.lastDay } }
    // v2.0: quien apoya ve su marco en el avatar; el muro de agradecimientos se asoma al final (ver WallPreviewCard).
    val support by c.support.state.collectAsStateWithLifecycle()
    val wall by c.wall.state.collectAsStateWithLifecycle()
    val wallHidden by c.support.homeWallHidden.collectAsStateWithLifecycle()
    val finished by c.workouts.observeCount().collectAsStateWithLifecycle(initialValue = 0)

    // v1.9: sin globos de tutorial en Inicio (el estado vacío ya dice qué hacer) y el permiso de notificaciones ya no
    // se pide aquí: se pide al marcar la primera serie, cuando arranca el primer descanso (ver ActiveWorkoutScreen).
    fun start(routineId: String?) {
        scope.launch {
            if (active == null) { if (routineId == null) c.workouts.startEmpty() else c.workouts.startFromRoutine(routineId) }
            nav.navigate(Routes.WORKOUT)
        }
    }

    // Resumen de la semana (en la zona horaria del usuario).
    val week = remember(recent, settings?.weekStart, zone) {
        val today = Dates.today(zone)
        val ws = Dates.weekStart(today, settings?.weekStart ?: 1)
        val inWeek = recent.filter { it.localDate >= ws.toEpochDay().toInt() }
        Triple(ws, inWeek, inWeek.map { it.localDate }.toSet())
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val h = LocalTime.now(zone).hour
                    Text(when { h < 12 -> tr("Buenos días", "Good morning"); h < 19 -> tr("Buenas tardes", "Good afternoon"); else -> tr("Buenas noches", "Good evening") }, style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
                    Text(name.substringBefore(' ').ifBlank { tr("Atleta", "Athlete") }, style = MaterialTheme.typography.headlineMedium)
                }
                SyncStatusIcon()
                Box(Modifier.clip(CircleShape).clickable(onClickLabel = tr("Abrir perfil", "Open profile")) { nav.goToTab(Routes.PROFILE) }) { SupporterAvatar(name, 44.dp, support.best) }
            }
        }
        item { SyncAlertCard() }
        if (firstDownload) item {
            SectionCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (syncSt.lastError == null) RupioSpinner(size = 24.dp, label = tr("Bajando tus datos", "Downloading your data"))
                    else Icon(Icons.Rounded.CloudOff, null, tint = RupioColors.Warning)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (syncSt.lastError == null) tr("Bajando tus datos de la nube…", "Downloading your data from the cloud…") else tr("Aún no pudimos bajar tus datos", "We couldn't download your data yet"), style = MaterialTheme.typography.titleSmall)
                        Text(if (syncSt.lastError == null) tr("La primera vez tarda unos segundos si tienes mucho historial. Puedes seguir usando la app.", "The first time takes a few seconds if you have a lot of history. You can keep using the app.")
                             else tr("Revisa tu conexión; lo reintentamos solos.", "Check your connection; we'll retry on our own."), style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    }
                }
            }
        }
        item {
            val (ws, inWeek, days) = week
            SectionCard {
                Text(tr("Esta semana", "This week"), style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
                Spacer(Modifier.height(8.dp))
                // Conteo arriba y volumen abajo: nunca se aprietan (antes el volumen se partía letra por letra con letra grande).
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${inWeek.size}", style = MaterialTheme.typography.displaySmall.merge(Tabular))
                    FitText(if (inWeek.size == 1) tr(" entrenamiento", " workout") else tr(" entrenamientos", " workouts"), MaterialTheme.typography.titleMedium, color = RupioColors.TextDim,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                val vol = inWeek.sumOf { it.totalVolumeKg }
                if (inWeek.isNotEmpty()) {
                    FitText(Fmt.volume(vol, unit) + tr(" levantados", " lifted"), MaterialTheme.typography.titleMedium.merge(Tabular))
                    FitText(Fmt.volume(vol, unit.other) + " · " + Fmt.durationHuman(inWeek.sumOf { (it.durationSec ?: 0).toLong() }) + tr(" entrenando", " training"),
                        MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val mondayFirst = if (com.rupio.app.core.I18n.isEnglish) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("L", "M", "M", "J", "V", "S", "D")
                    val letters = if ((settings?.weekStart ?: 1) == 7) listOf(mondayFirst.last()) + mondayFirst.dropLast(1) else mondayFirst
                    val today = Dates.today(zone).toEpochDay().toInt()
                    letters.forEachIndexed { i, l ->
                        val date = ws.plusDays(i.toLong())
                        val day = date.toEpochDay().toInt()
                        val done = day in days
                        // El lector de pantalla dice el día completo ("martes 11 de noviembre, entrenaste"): "M" no distingue martes de miércoles.
                        val spoken = Dates.spokenDay(date).replaceFirstChar { it.uppercase() } + (if (day == today) tr(", hoy", ", today") else "") + if (done) tr(", entrenaste", ", you trained") else tr(", sin entreno", ", no workout")
                        Column(Modifier.clearAndSetSemantics { contentDescription = spoken }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (done) RupioColors.Success else if (day == today) RupioColors.Surface3 else RupioColors.Surface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done) Icon(Icons.Rounded.Check, tr("Entrenaste", "You trained"), tint = RupioColors.OnFill, modifier = Modifier.size(18.dp))
                                else Text(l, style = MaterialTheme.typography.labelLarge, color = if (day == today) RupioColors.Text else RupioColors.TextFaint)
                            }
                        }
                    }
                }
            }
        }
        if (recent.size < 5 && "basicos" !in learned) item {
            Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Warning.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, RupioColors.Warning.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.tutorial("basicos")) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🏋️", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("¿Nuevo en el gym?", "New to the gym?"), style = MaterialTheme.typography.titleSmall)
                        Text(tr("Aprende lo básico en 3 minutos: series, peso, descanso y técnica.", "Learn the basics in 3 minutes: sets, weight, rest and form."), style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
                }
            }
        }
        item {
            val label: @Composable RowScope.() -> Unit = {
                Icon(if (active != null) Icons.Rounded.PlayArrow else Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (active != null) tr("Continuar entrenamiento", "Resume workout") else tr("Empezar entrenamiento libre", "Start empty workout"), style = MaterialTheme.typography.titleMedium)
            }
            val mod = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            // v2.1: con rutinas, lo principal es "Empezar rutina" de la que toca; el entreno libre pasa a botón con borde
            // (mismo lugar y tamaño). Sin rutinas o con un entreno en curso sigue siendo el botón lleno.
            if (active == null && !routines.isNullOrEmpty()) OutlinedButton(onClick = { start(null) }, modifier = mod, shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RupioColors.ControlOutline), content = label)
            else Button(
                onClick = { start(null) }, modifier = mod, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (active != null) RupioColors.Success else RupioColors.Primary, contentColor = if (active != null) RupioColors.OnFill else RupioColors.OnPrimary),
                content = label,
            )
        }
        item {
            SectionTitle(tr("Mis rutinas", "My routines")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if ((routines?.size ?: 0) >= 2) IconButton(onClick = { openReorder(null) }) { Icon(Icons.Rounded.SwapVert, tr("Ordenar rutinas", "Reorder routines"), tint = RupioColors.Primary) }
                    TextButton(onClick = { nav.navigate(Routes.GUIDE) }) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Guía", "Guide"))
                    }
                    TextButton(onClick = { nav.navigate(Routes.routine("new")) }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Nueva", "New"))
                    }
                }
            }
        }
        val list = routines
        if (list != null && list.isEmpty() && !firstDownload) item {
            EmptyState(Icons.Rounded.ListAlt, tr("Aún no tienes rutinas", "No routines yet"),
                tr("Te ayudamos a armar una según tus días, tu tiempo y tu equipo. O créala tú desde cero.", "We'll help you build one based on your days, your time and your equipment. Or create it yourself from scratch.")) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { nav.navigate(Routes.GUIDE) }) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Armar mi rutina con la guía", "Build my routine with the guide"))
                    }
                    TextButton(onClick = { nav.navigate(Routes.routine("new")) }) { Text(tr("Crear desde cero", "Create from scratch")) }
                }
            }
        }
        if (list != null) {
            // v1.9: la que toca va primero y lo dice ("Te toca hoy"; "La próxima" si ya entrenaste hoy).
            val today = Dates.today(zone).toEpochDay().toInt()
            val nextId = if (list.size >= 2) com.rupio.app.domain.RoutineRotation.next(list.map { it.id },
                lastDone.map { com.rupio.app.domain.RoutineRotation.Done(it.routineId, it.lastDay, it.lastEnd) }) else null
            val trainedToday = lastDone.any { it.lastDay == today }
            val ordered = com.rupio.app.domain.RoutineRotation.withNextFirst(list, nextId) { it.id }
            items(ordered, key = { r -> r.id }) { r ->
                RoutineCard(
                    routine = r, items = byRoutine[r.id].orEmpty(), names = names, activeExists = active != null,
                    badge = if (r.id == nextId) (if (trainedToday) tr("La próxima", "Up next") else tr("Te toca hoy", "Today's routine")) else null,
                    lastDone = lastDay[r.id]?.let { tr("Última vez: ", "Last done: ") + Dates.daysAgo(it, today).replaceFirstChar { ch -> ch.lowercase() } } ?: tr("Aún no la haces", "Not done yet"),
                    onStart = { start(r.id) }, onEdit = { nav.navigate(Routes.routine(r.id)) }, onDelete = { toDelete = r },
                    onReorder = if (list.size >= 2) ({ openReorder(r.id) }) else null,
                    // Lleno: la que toca (o la única). Con un entreno en curso, ninguno compite con "Continuar".
                    emphasized = active == null && (nextId == null || r.id == nextId),
                )
            }
        }
        // v2.0: el muro de agradecimientos al final de Inicio, cuando ya hay costumbre (desde el 3.er entreno) y hasta
        // que lo ocultes. No va arriba: lo primero es entrenar (ver docs/TEMA_IDIOMA_APOYO.md).
        if (!support.isPatron && SupportPolicy.showWallTeaserOnHome(finished, wallHidden)) item(key = "muro") {
            WallPreviewCard(wall, wallAccess(auth), onOpen = { nav.navigate(Routes.WALL) }, modifier = Modifier.padding(top = 8.dp),
                onHide = { c.support.hideHomeWall() })
        }
    }

    reorderFocus?.let { focus ->
        val list = routines.orEmpty()
        ReorderDialog(
            tr("Ordenar rutinas", "Reorder routines"), tr("Arrastra con ≡ o mantén presionada una rutina. Este orden decide cuál te toca después.", "Drag with ≡ or press and hold a routine. This order decides which one comes next."),
            list.map { r -> ReorderEntry(r.id, r.name, Fmt.plural(byRoutine[r.id].orEmpty().size, tr("ejercicio", "exercise"), tr("ejercicios", "exercises"))) },
            onDismiss = { reorderFocus = null }, focusKey = focus.ifEmpty { null }, showImages = false,
        ) { ids -> scope.launch { c.routines.reorder(ids) }; reorderFocus = null }
    }
    toDelete?.let { r ->
        ConfirmDialog(tr("¿Eliminar rutina?", "Delete routine?"), tr("\"${r.name}\" se eliminará. Tu historial de entrenamientos no se toca.", "\"${r.name}\" will be deleted. Your workout history isn't touched."), tr("Eliminar", "Delete"), { toDelete = null }, danger = true) {
            scope.launch { c.routines.delete(r.id) }; toDelete = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineCard(
    routine: RoutineEntity, items: List<RoutineExerciseEntity>, names: Map<String, String>, activeExists: Boolean,
    badge: String?, lastDone: String,
    onStart: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onReorder: (() -> Unit)? = null, emphasized: Boolean = true,
) {
    val summary = remember(items) { RestPlanner.summarize(items.map { it.toPlan() }) }
    val ssIndex = remember(items) { supersetIndexMap(items.map { it.supersetId }) }
    var menu by remember { mutableStateOf(false) }
    // v1.9: toda la tarjeta abre la rutina (antes solo "Empezar rutina" y ⋮ → Editar).
    // v1.9: mantener presionada la tarjeta abre tr("Ordenar rutinas", "Reorder routines") con esta resaltada.
    SectionCard(Modifier.combinedClickable(onClickLabel = tr("Ver y editar la rutina", "View and edit the routine"), onClick = onEdit,
        onLongClickLabel = if (onReorder != null) tr("ordenar rutinas", "reorder routines") else null, onLongClick = onReorder)) {
        if (badge != null) {
            Pill("▶ $badge", RupioColors.Success, filled = true)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(routine.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr("Opciones", "Options")) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Editar", "Edit")) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                    if (onReorder != null) DropdownMenuItem(text = { Text(tr("Ordenar rutinas", "Reorder routines")) }, leadingIcon = { Icon(Icons.Rounded.SwapVert, null) }, onClick = { menu = false; onReorder() })
                    DropdownMenuItem(text = { Text(tr("Eliminar", "Delete"), color = RupioColors.Danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = RupioColors.Danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text(lastDone, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        Spacer(Modifier.height(4.dp))
        val groups = remember(items) { groupConsecutive(items) { it.supersetId } }
        groups.take(5).forEach { g ->
            if (g.size > 1 && g.first().supersetId != null) {
                val idx = ssIndex[g.first().supersetId] ?: 0
                // La barra de color mide lo que midan sus filas (con letra grande las filas crecen y ya no se enciman).
                Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 2.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(supersetColor(idx)))
                    Spacer(Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        g.forEachIndexed { k, e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SupersetTag(idx, k); Spacer(Modifier.width(6.dp))
                                Text("${e.targetSets} × ${names[e.exerciseId] ?: tr("Ejercicio", "Exercise")}", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else g.forEach { e ->
                Text("${e.targetSets} × ${names[e.exerciseId] ?: tr("Ejercicio", "Exercise")}", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        if (groups.size > 5) Text(tr("y ${groups.drop(5).sumOf { it.size }} más…", "and ${groups.drop(5).sumOf { it.size }} more…"), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(Fmt.plural(items.size, tr("ejercicio", "exercise"), tr("ejercicios", "exercises")), RupioColors.TextDim)
            Pill(Fmt.plural(summary.totalSets, tr("serie", "set"), tr("series", "sets")), RupioColors.TextDim)
            Pill("≈ ${Fmt.durationHuman(summary.estimatedSec.toLong())}", RupioColors.Primary)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, null, tint = RupioColors.Rest, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(tr("Descanso total en la rutina: ", "Total rest in this routine: ") + Fmt.durationHuman(summary.totalRestSec.toLong()), style = MaterialTheme.typography.labelMedium, color = RupioColors.Rest)
        }
        Spacer(Modifier.height(12.dp))
        // v2.1: un solo botón lleno por pantalla, el de la rutina que toca; las demás llevan uno tonal (mismo tamaño y
        // texto). Antes había 3 o más botones azules iguales y ninguno decía cuál era el siguiente paso.
        val content: @Composable RowScope.() -> Unit = {
            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp))
            Text(if (activeExists) tr("Ir al entrenamiento en curso", "Go to workout in progress") else tr("Empezar rutina", "Start routine"), fontWeight = FontWeight.SemiBold)
        }
        val mod = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        if (emphasized) Button(onClick = onStart, enabled = items.isNotEmpty() || activeExists, modifier = mod, shape = RoundedCornerShape(14.dp), content = content)
        else FilledTonalButton(onClick = onStart, enabled = items.isNotEmpty() || activeExists, modifier = mod, shape = RoundedCornerShape(14.dp), content = content)
    }
}
