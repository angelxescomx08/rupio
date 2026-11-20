package com.rupio.app.ui.history


import com.rupio.app.core.tr
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
import java.time.YearMonth
import java.time.format.TextStyle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusRequester

/**
 * Historial con keyset pagination: cada página cuesta lo mismo aunque tengas 10 años de entrenamientos.
 * Se puede buscar por ejercicio o nombre y saltar a un mes (antes, para ver 2019 había que bajar ~57 páginas).
 */
class HistoryVM(private val c: AppContainer) : ViewModel() {
    val items = mutableStateListOf<WorkoutEntity>()
    val summaries = mutableStateMapOf<String, List<WorkoutExerciseSummary>>()
    var end by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var query by mutableStateOf(""); private set
    var fromMonth by mutableStateOf<YearMonth?>(null); private set
    private val mutex = Mutex()
    private val page = 30

    init {
        viewModelScope.launch { c.workouts.observeCount().distinctUntilChanged().collect { refresh() } }
    }

    /** Dónde empieza la lista: lo más reciente o el último día del mes elegido. */
    private fun start(): Pair<Int, Long> = fromMonth?.let { it.plusMonths(1).atDay(1).toEpochDay().toInt() to Long.MIN_VALUE } ?: (Int.MAX_VALUE to Long.MAX_VALUE)

    private suspend fun fetch(beforeDay: Int, beforeStart: Long, n: Int): List<WorkoutEntity> =
        if (query.isBlank()) c.workouts.historyPage(beforeDay, beforeStart, n)
        else c.workouts.historySearch(query.trim(), beforeDay, beforeStart, n)

    /**
     * Cambió el número de entrenos (borraste uno o la sincronización bajó otros): se vuelve a leer lo que YA estaba
     * cargado, sin vaciar la lista. Antes regresaba a la primera página y si ibas en 2021 te subía hasta arriba;
     * ahora la lista conserva la posición por la llave de cada entreno.
     */
    private suspend fun refresh() = mutex.withLock {
        val n = maxOf(page, items.size)
        val (d, st) = start()
        val fresh = fetch(d, st, n)
        if (fresh != items.toList()) { items.clear(); items.addAll(fresh) }
        end = fresh.size < n
        load(fresh.filter { it.id !in summaries }); loaded = true
    }

    private fun restart() = viewModelScope.launch {
        mutex.withLock {
            loaded = false
            val (d, st) = start()
            val first = fetch(d, st, page)
            items.clear(); items.addAll(first); end = first.size < page
            summaries.clear(); load(first); loaded = true
        }
    }

    fun search(q: String) { if (q != query) { query = q; restart() } }
    fun jumpTo(month: YearMonth?) { fromMonth = month; restart() }
    suspend fun months() = c.workouts.months()

    fun more() = viewModelScope.launch {
        mutex.withLock {
            if (end) return@withLock
            val last = items.lastOrNull() ?: return@withLock
            val next = fetch(last.localDate, last.startedAt, page)
            items.addAll(next); end = next.size < page
            load(next)
        }
    }

    private suspend fun load(ws: List<WorkoutEntity>) {
        c.workouts.exerciseSummaries(ws.map { it.id }).groupBy { it.workoutId }.forEach { (k, v) -> summaries[k] = v }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(nav: NavHostController) {
    val vm = appViewModel { HistoryVM(it) }
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val zone by c.zone.collectAsStateWithLifecycle()
    val unit = settings?.unit ?: WeightUnit.LB
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, vm.items.size) { if (nearEnd && vm.loaded) vm.more() }
    var searching by rememberSaveable { mutableStateOf(vm.query.isNotEmpty()) }
    var text by rememberSaveable { mutableStateOf(vm.query) }
    LaunchedEffect(text) { kotlinx.coroutines.delay(300); vm.search(text) }
    var picking by remember { mutableStateOf(false) }

    val byMonth = vm.items.groupBy { LocalDate.ofEpochDay(it.localDate.toLong()).withDayOfMonth(1) }

    Column(Modifier.fillMaxSize()) {
        // v1.9: la zona horaria solo se menciona si algún entreno se hizo en otra (antes "Horas en tu zona: GMT" salía siempre).
        val otherZone = vm.items.any { Dates.isOtherZone(it.startedAt, zone, it.timeZone) }
        RupioTopBar(tr("Historial", "History"), onBack = null, subtitle = if (otherZone && !searching) tr("Horas en tu zona: ${zone.id}", "Times in your zone: ${zone.id}") else null) {
            IconButton(onClick = { searching = !searching; if (!searching) text = "" }) {
                Icon(if (searching) Icons.Rounded.SearchOff else Icons.Rounded.Search, if (searching) tr("Cerrar búsqueda", "Close search") else tr("Buscar por ejercicio", "Search by exercise"))
            }
            IconButton(onClick = { picking = true }) { Icon(Icons.Rounded.CalendarMonth, tr("Ir a un mes", "Go to a month")) }
            SyncStatusIcon()
        }
        // v1.9: al tocar la lupa el campo ya tiene el foco y abre el teclado (antes lo que escribías se perdía).
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(searching) { if (searching && text.isEmpty()) runCatching { focus.requestFocus() } }
        if (searching) OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(focus),
            placeholder = { Text(tr("Buscar ejercicio o entreno (p. ej. sentadilla)", "Search exercise or workout (e.g. squat)")) }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, tr("Borrar búsqueda", "Clear search")) } },
            shape = RoundedCornerShape(14.dp))
        vm.fromMonth?.let { m ->
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputChip(selected = true, onClick = { vm.jumpTo(null) }, label = { Text(tr("Desde ", "From ") + monthName(m)) },
                    trailingIcon = { Icon(Icons.Rounded.Close, tr("Volver a lo más reciente", "Back to most recent"), Modifier.size(18.dp)) })
            }
        }
        LazyColumn(state = list, contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.loaded && vm.items.isEmpty()) item {
                if (vm.query.isNotBlank()) EmptyState(Icons.Rounded.SearchOff, tr("Sin resultados", "No results"), tr("Ningún entreno tiene «${vm.query}»", "No workout has “${vm.query}”") + (vm.fromMonth?.let { tr(" antes de ", " before ") + monthName(it.plusMonths(1)) } ?: "") + ".")
                else EmptyState(Icons.Rounded.History, tr("Aún no hay entrenamientos", "No workouts yet"), tr("Cuando termines tu primer entrenamiento aparecerá aquí con tu volumen, récords y series.", "When you finish your first workout, it'll show up here with your volume, PRs and sets."))
            }
            byMonth.forEach { (month, ws) ->
                item(key = "m$month") {
                    // Mes y totales en un renglón si caben; si no (letra grande), el total baja. Antes el mes se partía letra por letra.
                    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Bottom) {
                        Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Fmt.locale).replaceFirstChar { it.uppercase() } + " " + month.year,
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }.padding(end = 8.dp))
                        Text("${Fmt.plural(ws.size, tr("entreno", "workout"), tr("entrenos", "workouts"))} · ${Fmt.volume(ws.sumOf { it.totalVolumeKg }, unit)}", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim,
                            modifier = Modifier.align(Alignment.Bottom))
                    }
                }
                items(ws, key = { it.id }) { w -> WorkoutCard(w, vm.summaries[w.id].orEmpty(), unit, zone) { nav.navigate(Routes.detail(w.id)) } }
            }
            if (!vm.end && vm.items.isNotEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { RupioSpinner(size = 24.dp, label = tr("Cargando más entrenamientos", "Loading more workouts")) }
            }
        }
    }

    if (picking) {
        var months by remember { mutableStateOf<List<com.rupio.app.data.local.MonthCount>?>(null) }
        LaunchedEffect(Unit) { months = vm.months() }
        ModalBottomSheet(onDismissRequest = { picking = false }, containerColor = RupioColors.Surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(tr("Ir a un mes", "Go to a month"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val ms = months
                if (ms == null) RupioSpinner(size = 24.dp)
                else if (ms.isEmpty()) Text(tr("Aún no hay entrenamientos.", "No workouts yet."), color = RupioColors.TextDim)
                else LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { AssistChip(onClick = { vm.jumpTo(null); picking = false; scope.launch { list.scrollToItem(0) } }, label = { Text(tr("Lo más reciente", "Most recent")) }) }
                    ms.groupBy { it.ym.take(4) }.forEach { (year, inYear) ->
                        item(key = year) {
                            Text("$year · ${Fmt.plural(inYear.sumOf { it.n }, tr("entreno", "workout"), tr("entrenos", "workouts"))}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                inYear.forEach { mc ->
                                    val ym = runCatching { YearMonth.parse(mc.ym) }.getOrNull() ?: return@forEach
                                    FilterChip(selected = vm.fromMonth == ym, onClick = { vm.jumpTo(ym); picking = false; scope.launch { list.scrollToItem(0) } },
                                        label = { Text(ym.month.getDisplayName(TextStyle.SHORT_STANDALONE, Fmt.locale).replaceFirstChar { it.uppercase() }.trimEnd('.') + " · ${mc.n}") })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun monthName(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL_STANDALONE, Fmt.locale) + " " + m.year

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutCard(w: WorkoutEntity, ex: List<WorkoutExerciseSummary>, unit: WeightUnit, zone: java.time.ZoneId, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = RupioColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Dates.formatDay(w.localDate) + " · " + Dates.formatTimeWithOrigin(w.startedAt, zone, w.timeZone),
                        style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
                if (w.prCount > 0) Pill("🏆 ${w.prCount}", RupioColors.Warning)
            }
            Spacer(Modifier.height(10.dp))
            // Con letra grande las métricas bajan de renglón en vez de partirse letra por letra.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric(Icons.Rounded.Schedule, Fmt.durationHuman((w.durationSec ?: 0).toLong()))
                // v1.9: sin "0 lb" en un entreno de cardio o de peso corporal.
                if (w.totalVolumeKg > 0) Metric(Icons.Rounded.FitnessCenter, Fmt.volume(w.totalVolumeKg, unit), Fmt.volume(w.totalVolumeKg, unit.other))
                Metric(Icons.Rounded.Repeat, Fmt.plural(w.totalSets, tr("serie", "set"), tr("series", "sets")))
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
                if (ex.size > 6) Text(tr("y ${ex.size - 6} más", "and ${ex.size - 6} more"), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
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
