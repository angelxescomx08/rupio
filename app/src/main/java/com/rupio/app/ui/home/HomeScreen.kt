package com.rupio.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import androidx.core.content.ContextCompat
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
import com.rupio.app.ui.tutorial.CoachStep
import com.rupio.app.ui.tutorial.LocalCoach
import com.rupio.app.ui.tutorial.coachTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

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
    val coach = LocalCoach.current
    var toDelete by remember { mutableStateOf<RoutineEntity?>(null) }
    val unit = settings?.unit ?: com.rupio.app.core.WeightUnit.LB
    LaunchedEffect(Unit) { com.rupio.app.ui.learn.LearnProgress.load(context) }
    val learned by com.rupio.app.ui.learn.LearnProgress.done.collectAsStateWithLifecycle()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun start(routineId: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        scope.launch {
            if (active == null) { if (routineId == null) c.workouts.startEmpty() else c.workouts.startFromRoutine(routineId) }
            nav.navigate(Routes.WORKOUT)
        }
    }

    // Tutorial interactivo la primera vez: resalta los botones reales.
    LaunchedEffect(settings?.tutorialHome, routines?.size) {
        val s = settings ?: return@LaunchedEffect
        if (s.tutorialHome || routines == null || coach.active) return@LaunchedEffect
        delay(700)
        val steps = buildList {
            add(CoachStep("start_empty", "Entrena cuando quieras", "Empieza un entrenamiento libre y agrega ejercicios sobre la marcha."))
            if (!routines.isNullOrEmpty()) {
                add(CoachStep("routine_card", "Tus rutinas", "Una rutina es tu plan: ejercicios, series, repeticiones y descansos."))
                add(CoachStep("start_routine", "Empieza tu rutina", "Carga todos los ejercicios con las series listas para marcar. Rupio te dirá cuándo subir peso."))
            }
            add(CoachStep("new_routine", "Crea la tuya", "Con la guía (según tus días, tiempo y equipo) o desde cero, con más de 200 ejercicios en español con imágenes."))
        }
        coach.start(steps) { scope.launch { c.settings.setTutorialHome(true) } }
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
                    Text(when { h < 12 -> "Buenos días"; h < 19 -> "Buenas tardes"; else -> "Buenas noches" }, style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
                    Text(name.substringBefore(' ').ifBlank { "Atleta" }, style = MaterialTheme.typography.headlineMedium)
                }
                Box(Modifier.clip(CircleShape).clickable { nav.goToTab(Routes.PROFILE) }) { InitialsAvatar(name, 44.dp) }
            }
        }
        if (firstDownload) item {
            SectionCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (syncSt.lastError == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = RupioColors.Primary)
                    else Icon(Icons.Rounded.CloudOff, null, tint = RupioColors.Warning)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (syncSt.lastError == null) "Bajando tus datos de la nube…" else "Aún no pudimos bajar tus datos", style = MaterialTheme.typography.titleSmall)
                        Text(if (syncSt.lastError == null) "La primera vez tarda unos segundos si tienes mucho historial. Puedes seguir usando la app."
                             else "Revisa tu conexión; lo reintentamos solos.", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    }
                }
            }
        }
        item {
            val (ws, inWeek, days) = week
            SectionCard {
                Text("Esta semana", style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
                Spacer(Modifier.height(8.dp))
                // Conteo arriba y volumen abajo: nunca se aprietan (antes el volumen se partía letra por letra con letra grande).
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${inWeek.size}", style = MaterialTheme.typography.displaySmall.merge(Tabular))
                    FitText(if (inWeek.size == 1) " entrenamiento" else " entrenamientos", MaterialTheme.typography.titleMedium, color = RupioColors.TextDim,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                val vol = inWeek.sumOf { it.totalVolumeKg }
                if (inWeek.isNotEmpty()) {
                    FitText(Fmt.volume(vol, unit) + " levantados", MaterialTheme.typography.titleMedium.merge(Tabular))
                    FitText(Fmt.volume(vol, unit.other) + " · " + Fmt.durationHuman(inWeek.sumOf { (it.durationSec ?: 0).toLong() }) + " entrenando",
                        MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val letters = if ((settings?.weekStart ?: 1) == 7) listOf("D", "L", "M", "M", "J", "V", "S") else listOf("L", "M", "M", "J", "V", "S", "D")
                    val today = Dates.today(zone).toEpochDay().toInt()
                    letters.forEachIndexed { i, l ->
                        val day = ws.plusDays(i.toLong()).toEpochDay().toInt()
                        val done = day in days
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (done) RupioColors.Success else if (day == today) RupioColors.Surface3 else RupioColors.Surface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done) Icon(Icons.Rounded.Check, "Entrenaste", tint = Color.Black, modifier = Modifier.size(18.dp))
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
                        Text("¿Nuevo en el gym?", style = MaterialTheme.typography.titleSmall)
                        Text("Aprende lo básico en 3 minutos: series, peso, descanso y técnica.", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
                }
            }
        }
        item {
            Button(
                onClick = { start(null) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).coachTarget("start_empty"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (active != null) RupioColors.Success else RupioColors.Primary, contentColor = if (active != null) Color.Black else RupioColors.OnPrimary),
            ) {
                Icon(if (active != null) Icons.Rounded.PlayArrow else Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (active != null) "Continuar entrenamiento" else "Empezar entrenamiento libre", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            SectionTitle("Mis rutinas") {
                Row(Modifier.coachTarget("new_routine")) {
                    TextButton(onClick = { nav.navigate(Routes.GUIDE) }) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Guía")
                    }
                    TextButton(onClick = { nav.navigate(Routes.routine("new")) }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Nueva")
                    }
                }
            }
        }
        val list = routines
        if (list != null && list.isEmpty() && !firstDownload) item {
            EmptyState(Icons.Rounded.ListAlt, "Aún no tienes rutinas",
                "Te ayudamos a armar una según tus días, tu tiempo y tu equipo. O créala tú desde cero.") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { nav.navigate(Routes.GUIDE) }) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Armar mi rutina con la guía")
                    }
                    TextButton(onClick = { nav.navigate(Routes.routine("new")) }) { Text("Crear desde cero") }
                }
            }
        }
        if (list != null) itemsIndexed(list, key = { _, r -> r.id }) { i, r ->
            RoutineCard(
                routine = r, items = byRoutine[r.id].orEmpty(), names = names, first = i == 0, activeExists = active != null,
                onStart = { start(r.id) }, onEdit = { nav.navigate(Routes.routine(r.id)) }, onDelete = { toDelete = r },
            )
        }
    }

    toDelete?.let { r ->
        ConfirmDialog("¿Eliminar rutina?", "\"${r.name}\" se eliminará. Tu historial de entrenamientos no se toca.", "Eliminar", { toDelete = null }, danger = true) {
            scope.launch { c.routines.delete(r.id) }; toDelete = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineCard(
    routine: RoutineEntity, items: List<RoutineExerciseEntity>, names: Map<String, String>, first: Boolean, activeExists: Boolean,
    onStart: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    val summary = remember(items) { RestPlanner.summarize(items.map { it.toPlan() }) }
    val ssIndex = remember(items) { supersetIndexMap(items.map { it.supersetId }) }
    var menu by remember { mutableStateOf(false) }
    SectionCard(Modifier.then(if (first) Modifier.coachTarget("routine_card") else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(routine.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Opciones") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Eliminar", color = RupioColors.Danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = RupioColors.Danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
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
                                Text("${e.targetSets} × ${names[e.exerciseId] ?: "Ejercicio"}", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else g.forEach { e ->
                Text("${e.targetSets} × ${names[e.exerciseId] ?: "Ejercicio"}", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        if (groups.size > 5) Text("y ${groups.drop(5).sumOf { it.size }} más…", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("${items.size} ejercicios", RupioColors.TextDim)
            Pill("${summary.totalSets} series", RupioColors.TextDim)
            Pill("≈ ${Fmt.durationHuman(summary.estimatedSec.toLong())}", RupioColors.Primary)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, null, tint = RupioColors.Rest, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Descanso total en la rutina: ${Fmt.durationHuman(summary.totalRestSec.toLong())}", style = MaterialTheme.typography.labelMedium, color = RupioColors.Rest)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStart, enabled = !activeExists || true,
            modifier = Modifier.fillMaxWidth().height(48.dp).then(if (first) Modifier.coachTarget("start_routine") else Modifier),
            shape = RoundedCornerShape(14.dp),
        ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (activeExists) "Ir al entrenamiento en curso" else "Empezar rutina", fontWeight = FontWeight.SemiBold) }
    }
}
