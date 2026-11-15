package com.rupio.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rupio.app.AppContainer
import com.rupio.app.Routes
import com.rupio.app.appViewModel
import com.rupio.app.container
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseSummary
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.TextStyle

/** Historial con keyset pagination: cada página cuesta lo mismo aunque tengas 10 años de entrenamientos. */
class HistoryVM(private val c: AppContainer) : ViewModel() {
    val items = mutableStateListOf<WorkoutEntity>()
    val summaries = mutableStateMapOf<String, List<WorkoutExerciseSummary>>()
    var end by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    private val mutex = Mutex()
    private val page = 30

    init {
        viewModelScope.launch { c.workouts.observeCount().distinctUntilChanged().collect { reload() } }
    }

    private suspend fun reload() = mutex.withLock {
        val first = c.workouts.historyPage(Int.MAX_VALUE, Long.MAX_VALUE, page)
        items.clear(); items.addAll(first); end = first.size < page
        summaries.clear(); load(first); loaded = true
    }

    fun more() = viewModelScope.launch {
        mutex.withLock {
            if (end) return@withLock
            val last = items.lastOrNull() ?: return@withLock
            val next = c.workouts.historyPage(last.localDate, last.startedAt, page)
            items.addAll(next); end = next.size < page
            load(next)
        }
    }

    private suspend fun load(ws: List<WorkoutEntity>) {
        c.workouts.exerciseSummaries(ws.map { it.id }).groupBy { it.workoutId }.forEach { (k, v) -> summaries[k] = v }
    }
}

@Composable
fun HistoryScreen(nav: NavHostController) {
    val vm = appViewModel { HistoryVM(it) }
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val zone by c.zone.collectAsStateWithLifecycle()
    val unit = settings?.unit ?: WeightUnit.LB
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, vm.items.size) { if (nearEnd && vm.loaded) vm.more() }

    val byMonth = vm.items.groupBy { LocalDate.ofEpochDay(it.localDate.toLong()).withDayOfMonth(1) }

    Column(Modifier.fillMaxSize()) {
        RupioTopBar("Historial", onBack = null, subtitle = if (vm.items.isNotEmpty()) "Horas en tu zona: ${zone.id}" else null)
        LazyColumn(state = list, contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.loaded && vm.items.isEmpty()) item {
                EmptyState(Icons.Rounded.History, "Aún no hay entrenamientos", "Cuando termines tu primer entrenamiento aparecerá aquí con tu volumen, récords y series.")
            }
            byMonth.forEach { (month, ws) ->
                item(key = "m$month") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Fmt.locale).replaceFirstChar { it.uppercase() } + " " + month.year,
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${ws.size} entrenos · ${Fmt.volume(ws.sumOf { it.totalVolumeKg }, unit)}", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                    }
                }
                items(ws, key = { it.id }) { w -> WorkoutCard(w, vm.summaries[w.id].orEmpty(), unit, zone) { nav.navigate(Routes.detail(w.id)) } }
            }
            if (!vm.end && vm.items.isNotEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            }
        }
    }
}

@Composable
private fun WorkoutCard(w: WorkoutEntity, ex: List<WorkoutExerciseSummary>, unit: WeightUnit, zone: java.time.ZoneId, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = RupioColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Dates.formatDay(w.localDate) + " · " + Dates.formatTimeWithOrigin(w.startedAt, zone, w.timeZone),
                        style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
                if (w.prCount > 0) Pill("🏆 ${w.prCount}", RupioColors.Warning)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric(Icons.Rounded.Schedule, Fmt.durationHuman((w.durationSec ?: 0).toLong()))
                Metric(Icons.Rounded.FitnessCenter, Fmt.volume(w.totalVolumeKg, unit), Fmt.volume(w.totalVolumeKg, unit.other))
                Metric(Icons.Rounded.Repeat, "${w.totalSets} series")
            }
            if (ex.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val ssIdx = supersetIndexMap(ex.map { it.supersetId })
                ex.take(6).forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                        s.supersetId?.let { id -> ssIdx[id]?.let { SupersetTag(it, null); Spacer(Modifier.width(6.dp)) } }
                        Text("${s.sets} × ${s.name}", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (ex.size > 6) Text("y ${ex.size - 6} más", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
            }
        }
    }
}

@Composable
private fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RupioColors.TextFaint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(text, style = MaterialTheme.typography.labelLarge.merge(Tabular))
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall.merge(Tabular), color = RupioColors.TextFaint)
        }
    }
}
