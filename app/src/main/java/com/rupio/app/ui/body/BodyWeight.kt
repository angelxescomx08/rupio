package com.rupio.app.ui.body


import com.rupio.app.core.tr
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.liveRegion
import com.rupio.app.safePop
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rupio.app.AppContainer
import com.rupio.app.appViewModel
import com.rupio.app.container
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.BodyWeightEntity
import com.rupio.app.data.local.WeightPoint
import com.rupio.app.data.remote.SyncEngine
import com.rupio.app.domain.WeightTrend
import com.rupio.app.ui.components.*
import com.rupio.app.ui.learn.HelpIcon
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Rangos de la gráfica de peso. */
enum class WeightRange(val days: Long?) {
    M1(30), M3(90), Y1(365), ALL(null);
    val label: String get() = when (this) { M1 -> "1M"; M3 -> "3M"; Y1 -> tr("1A", "1Y"); ALL -> tr("Todo", "All") }
}

data class WeightView(
    val points: List<WeightPoint> = emptyList(),
    val trend: List<Double> = emptyList(),
    val ratePct: Double? = null,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightVM(private val c: AppContainer) : ViewModel() {
    val range = MutableStateFlow(WeightRange.M3)
    private val zone: ZoneId get() = c.zone.value

    val latest: StateFlow<BodyWeightEntity?> = c.bodyWeight.recent(1).map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val view: StateFlow<WeightView> = range.flatMapLatest { r ->
        flow {
            val today = Dates.today(zone)
            val from = r.days?.let { today.minusDays(it - 1) } ?: (c.bodyWeight.firstDay() ?: today.minusDays(29))
            emitAll(c.bodyWeight.series(from, today).map { pts ->
                val t = WeightTrend.smooth(pts)
                WeightView(pts, t, WeightTrend.ratePerWeek(pts, t), loading = false)
            })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightView())

    // Historial con paginación por clave (rápido aunque tengas años de registros).
    val history = mutableStateListOf<BodyWeightEntity>()
    var endReached by mutableStateOf(false); private set
    private var loadingPage = false

    init {
        // Si registras o borras un peso, el historial se recarga desde el inicio.
        viewModelScope.launch { c.bodyWeight.recent(1).collect { reload() } }
    }

    private suspend fun reload() {
        val first = c.bodyWeight.page(Int.MAX_VALUE, 60)
        history.clear(); history.addAll(first); endReached = first.size < 60
    }

    fun loadMore() {
        if (loadingPage || endReached) return
        val before = history.lastOrNull()?.measuredOn ?: return
        loadingPage = true
        viewModelScope.launch {
            val next = c.bodyWeight.page(before, 60)
            history.addAll(next); endReached = next.size < 60; loadingPage = false
        }
    }

    fun log(day: LocalDate, kg: Double) = viewModelScope.launch { c.bodyWeight.log(day, kg); SyncEngine.now(c.app) }
    fun setUnit(u: WeightUnit) = viewModelScope.launch { c.settings.setBodyUnit(u) }
    fun delete(day: Int) = viewModelScope.launch { c.bodyWeight.delete(day); SyncEngine.now(c.app) }
}

// ------------------------------------------------------------------ tarjeta en Progreso

@Composable
fun BodyWeightCard(onOpen: () -> Unit) {
    val vm = appViewModel { BodyWeightVM(it) }
    val unit = rememberBodyUnit()
    val latest by vm.latest.collectAsStateWithLifecycle()
    val v by vm.view.collectAsStateWithLifecycle()
    var logging by remember { mutableStateOf(false) }
    SectionCard(Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Peso corporal", "Body weight"), style = MaterialTheme.typography.titleMedium)
            HelpIcon("peso_corporal")
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { logging = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Registrar", "Log"))
            }
        }
        val l = latest
        if (l == null) {
            Text(tr("Anota tu peso para ver tu tendencia. Tarda 3 segundos y puedes hacerlo cuando quieras.", "Log your weight to see your trend. It takes 3 seconds and you can do it whenever you want."),
                style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 6.dp))
        } else {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.weight(v.trend.lastOrNull() ?: l.weightKg, unit), style = MaterialTheme.typography.headlineSmall.merge(Tabular))
                Spacer(Modifier.width(8.dp))
                Text(Fmt.weight(v.trend.lastOrNull() ?: l.weightKg, unit.other), style = MaterialTheme.typography.labelMedium.merge(Tabular),
                    color = RupioColors.TextFaint, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f))
                v.ratePct?.let { RateText(it) }
            }
            Text(tr("Tendencia · último registro ${Dates.formatDay(l.measuredOn)}: ${Fmt.weight(l.weightKg, unit)}", "Trend · last entry ${Dates.formatDay(l.measuredOn)}: ${Fmt.weight(l.weightKg, unit)}"),
                style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
            if (v.points.size >= 2) {
                Spacer(Modifier.height(8.dp))
                BodyWeightChart(v.points, v.trend, unit, Modifier.fillMaxWidth().height(90.dp), interactive = false)
            }
        }
    }
    if (logging) LogWeightDialog(latest?.weightKg, unit, onUnitChange = vm::setUnit, onDismiss = { logging = false }) { d, kg -> vm.log(d, kg); logging = false }
}

/** Unidad del peso corporal (kg por defecto, aparte de la de las pesas). */
@Composable
fun rememberBodyUnit(): WeightUnit =
    container().settings.settings.collectAsStateWithLifecycle(initialValue = null).value?.bodyUnit ?: WeightUnit.KG

/** Botón kg | lb. Cambia toda la sección de peso y se recuerda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyUnitToggle(unit: WeightUnit, onChange: (WeightUnit) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.semantics { contentDescription = tr("Unidad del peso corporal: ", "Body weight unit: ") + unit.label }) {
        listOf(WeightUnit.KG, WeightUnit.LB).forEachIndexed { i, u ->
            SegmentedButton(unit == u, { onChange(u) }, SegmentedButtonDefaults.itemShape(i, 2), icon = {},
                modifier = Modifier.widthIn(min = 56.dp)) { Text(u.label) }
        }
    }
}

@Composable
private fun RateText(rate: Double) {
    val arrow = when { abs(rate) < 0.15 -> "="; rate < 0 -> "▼"; else -> "▲" }
    Text("$arrow ${Fmt.num(abs(rate), 1)} " + tr("%/sem", "%/wk"), style = MaterialTheme.typography.labelLarge.merge(Tabular), color = RupioColors.TextDim)
}

// ------------------------------------------------------------------ pantalla completa

@Composable
fun BodyWeightScreen(nav: NavHostController) {
    val vm = appViewModel { BodyWeightVM(it) }
    val c = container()
    val unit = rememberBodyUnit()
    val today = Dates.today(c.zone.collectAsStateWithLifecycle().value).toEpochDay().toInt()
    val range by vm.range.collectAsStateWithLifecycle()
    val v by vm.view.collectAsStateWithLifecycle()
    val latest by vm.latest.collectAsStateWithLifecycle()
    var logging by remember { mutableStateOf(false) }
    var loggingOther by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BodyWeightEntity?>(null) }
    var deleting by remember { mutableStateOf<BodyWeightEntity?>(null) }
    val st by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val consentLoaded = st != null; val consentAt = st?.healthConsentAt
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 8 } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(
        containerColor = RupioColors.Background,
        // Es una pestaña de la barra de abajo: sin flecha de atrás. Arriba a la derecha, kg | lb.
        topBar = { RupioTopBar(tr("Peso corporal", "Body weight"), onBack = null) { BodyUnitToggle(unit, vm::setUnit, Modifier.padding(end = 12.dp)) } },
        // El botón grande está en el resumen de arriba; el flotante solo aparece cuando ya bajaste (historial).
        floatingActionButton = {
            val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
            androidx.compose.animation.AnimatedVisibility(scrolled, enter = androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.scaleOut()) {
                ExtendedFloatingActionButton(onClick = { logging = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(tr("Registrar peso", "Log weight")) },
                    modifier = Modifier.semantics { contentDescription = tr("Registrar peso", "Log weight") },
                    containerColor = RupioColors.Primary, contentColor = RupioColors.OnPrimary)
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { WeightHero(latest, v, unit, today, onOtherDay = { loggingOther = true }) { if (latest?.measuredOn == today) editing = latest else logging = true } }
            if (latest != null && consentLoaded && consentAt == null) item { HealthConsentCard() }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WeightRange.entries.forEachIndexed { i, r ->
                        SegmentedButton(range == r, { vm.range.value = r }, SegmentedButtonDefaults.itemShape(i, WeightRange.entries.size)) { Text(r.label) }
                    }
                }
            }
            item {
                SectionCard {
                    if (v.points.isEmpty() && !v.loading) {
                        Text(if (latest == null) tr("Aún no tienes registros. Toca \"Registrar peso\".", "No entries yet. Tap “Log weight”.") else tr("Sin registros en este rango.", "No entries in this range."),
                            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.padding(vertical = 24.dp))
                    } else if (v.points.size == 1) {
                        // v1.9: con un solo registro la gráfica salía con el eje "81 · 81 · 80" y "18 nov … 18 nov".
                        Text(tr("Con un registro más verás aquí tu tendencia. Pésate 2 o 3 veces por semana.", "One more entry and you'll see your trend here. Weigh yourself 2 or 3 times a week."),
                            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.padding(vertical = 24.dp))
                    } else if (v.points.isNotEmpty()) {
                        BodyWeightChart(v.points, v.trend, unit, Modifier.fillMaxWidth().height(200.dp), interactive = true)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(RupioColors.TextFaint))
                            Text(tr("  Peso de cada día", "  Daily weight"), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                            Spacer(Modifier.width(14.dp))
                            Box(Modifier.width(16.dp).height(3.dp).background(RupioColors.Chart))
                            Text(tr("  Tendencia", "  Trend"), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                        }
                    }
                }
            }
            if (v.points.isNotEmpty()) item {
                val t = v.trend
                TileRow {
                    Tile(tr("Tendencia hoy", "Trend today"), Fmt.weight(t.last(), unit), Fmt.weight(t.last(), unit.other))
                    val change = t.last() - t.first()
                    Tile(tr("Cambio en el rango", "Change in range"), (if (change >= 0) "+" else "−") + Fmt.weight(abs(change), unit), Fmt.plural(v.points.size, tr("registro", "entry"), tr("registros", "entries")))
                    Tile(tr("Ritmo", "Pace"), v.ratePct?.let { (if (it >= 0) "+" else "−") + Fmt.num(abs(it), 1) + " %" } ?: "—",
                        if (v.ratePct != null) tr("por semana", "per week") else tr("faltan días", "needs more days"))
                }
            }
            v.ratePct?.let { r ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Surface2) {
                        Row(Modifier.padding(14.dp)) {
                            Icon(Icons.Rounded.Insights, null, tint = RupioColors.Primary)
                            Spacer(Modifier.width(10.dp))
                            Text(WeightTrend.explain(r), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Surface2) {
                    Row(Modifier.padding(14.dp)) {
                        Icon(Icons.Rounded.TipsAndUpdates, null, tint = RupioColors.Warning)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Pésate siempre igual: al despertar, después de ir al baño y antes de comer o beber. " +
                    "Un día puedes pesar 1–2 kg más por agua o sal; por eso mira la tendencia, no el número de un día.",
                    "Weigh yourself the same way every time: when you wake up, after using the bathroom and before eating or drinking. " +
                        "Some days you can weigh 1–2 kg more from water or salt, so look at the trend, not one day's number."),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (vm.history.isNotEmpty()) item { SectionTitle(tr("Registros", "Entries")) }
            items(vm.history, key = { it.measuredOn }) { e ->
                Surface(shape = RoundedCornerShape(14.dp), color = RupioColors.Surface) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = tr("Editar", "Edit")) { editing = e }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.formatDay(e.measuredOn), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        WeightBoth(e.weightKg, unit)
                        IconButton(onClick = { deleting = e }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.DeleteOutline, tr("Borrar registro", "Delete entry"), tint = RupioColors.TextDim) }
                    }
                }
            }
        }
    }
    if (logging) LogWeightDialog(latest?.weightKg, unit, onUnitChange = vm::setUnit, onDismiss = { logging = false }) { d, kg -> vm.log(d, kg); logging = false }
    // v1.9: un día que se te olvidó, sin ir a Progreso (antes aquí solo estaba tr("Corregir el de hoy", "Fix today's"), con la fecha fija).
    if (loggingOther) LogWeightDialog(latest?.weightKg, unit, initialDay = Dates.today(c.zone.value).minusDays(1), onUnitChange = vm::setUnit,
        onDismiss = { loggingOther = false }) { d, kg -> vm.log(d, kg); loggingOther = false }
    editing?.let { e ->
        LogWeightDialog(e.weightKg, unit, LocalDate.ofEpochDay(e.measuredOn.toLong()), lockDate = true, onUnitChange = vm::setUnit, onDismiss = { editing = null }) { d, kg -> vm.log(d, kg); editing = null }
    }
    deleting?.let { e ->
        ConfirmDialog(tr("¿Borrar este registro?", "Delete this entry?"), "${Dates.formatDay(e.measuredOn)}: ${Fmt.weightBoth(e.weightKg, unit)}", tr("Borrar", "Delete"), { deleting = null }, danger = true) {
            vm.delete(e.measuredOn); deleting = null
        }
    }
}

/**
 * Lo primero que ves en la pestaña: tu peso (tendencia) en grande, en tu unidad y en la otra, el ritmo y si ya te
 * pesaste hoy, con el botón para registrar (o corregir lo de hoy) a un toque.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeightHero(latest: BodyWeightEntity?, v: WeightView, unit: WeightUnit, today: Int, onOtherDay: () -> Unit, onLog: () -> Unit) {
    SectionCard {
        if (latest == null) {
            Text("⚖️", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(tr("Registra tu peso", "Log your weight"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Pésate 2 o 3 veces por semana y Rupio te muestra tu tendencia, sin que te asusten los días raros.", "Weigh yourself 2 or 3 times a week and Rupio shows your trend, so odd days don't scare you."),
                style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
        } else {
            val shown = v.trend.lastOrNull() ?: latest.weightKg
            Text(tr("Tu peso (tendencia)", "Your weight (trend)"), style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
            FlowRow(verticalArrangement = Arrangement.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Fmt.weight(shown, unit), style = MaterialTheme.typography.displaySmall.merge(Tabular))
                Text(Fmt.weight(shown, unit.other), style = MaterialTheme.typography.titleSmall.merge(Tabular), color = RupioColors.TextFaint,
                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 8.dp))
            }
            v.ratePct?.let { RateText(it) }
            val todayDone = latest.measuredOn == today
            Text(if (todayDone) tr("Hoy: ", "Today: ") + Fmt.weight(latest.weightKg, unit) + " ✓" else tr("Último registro: ", "Last entry: ") + "${Dates.formatDay(latest.measuredOn)} · ${Fmt.weight(latest.weightKg, unit)}",
                style = MaterialTheme.typography.bodySmall, color = if (todayDone) RupioColors.Success else RupioColors.TextDim)
        }
        Spacer(Modifier.height(12.dp))
        val todayDone = latest?.measuredOn == today
        Button(onClick = onLog, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(if (todayDone) Icons.Rounded.Edit else Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp))
            Text(if (todayDone) tr("Corregir el de hoy", "Fix today's") else tr("Registrar peso de hoy", "Log today's weight"), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        if (latest != null) OutlinedButton(onClick = onOtherDay, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(tr("Registrar otro día", "Log another day"))
        }
    }
}

// ------------------------------------------------------------------ captura rápida

/**
 * Registrar en 3 toques: viene con tu último peso; ajustas con − / + (0.1) o escribes; guardar.
 * Si ya registraste ese día, se actualiza (una fila por día).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogWeightDialog(
    lastKg: Double?,
    unit: WeightUnit,
    initialDay: LocalDate? = null,
    lockDate: Boolean = false,
    onUnitChange: (WeightUnit) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double) -> Unit,
) {
    val c = container()
    val today = Dates.today(c.zone.value)
    var day by remember { mutableStateOf(initialDay ?: today) }
    var unit by remember { mutableStateOf(unit) }
    val start = lastKg?.let { unit.fromKg(it) } ?: if (unit == WeightUnit.LB) 160.0 else 72.0
    // v1.9: la primera vez el campo va vacío (antes traía 72 kg y era fácil guardar un número inventado);
    // −/+ parten de ahí si los usas.
    var text by remember { mutableStateOf(lastKg?.let { Fmt.num(start, 1).replace(",", ".") } ?: "") }
    var picking by remember { mutableStateOf(false) }
    // v1.7 (N2): el peso corporal es un dato de salud; la primera vez se pide permiso expreso con una casilla.
    val st by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val needConsent = st != null && st?.healthConsentAt == null
    var consent by remember { mutableStateOf(false) }
    // v1.9: tr("Guardar", "Save") sin la casilla marcada ya no está gris sin explicación: resalta la casilla y dice por qué.
    var consentNudge by remember { mutableStateOf(false) }
    // El peso que ya tiene el día elegido (al corregir uno con la fecha fija no hace falta avisar).
    val existing by produceState<BodyWeightEntity?>(null, day, lockDate) { value = if (lockDate) null else c.bodyWeight.on(day) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { if (lastKg == null) { kotlinx.coroutines.delay(150); runCatching { focus.requestFocus() } } }
    val value = text.replace(',', '.').toDoubleOrNull()
    val kg = value?.let { unit.toKg(it) }
    val valid = kg != null && kg in 20.0..400.0
    fun bump(d: Double) { val b = (value ?: start) + d; text = Fmt.num(max(0.0, b), 1).replace(",", ".") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lockDate) tr("Editar peso", "Edit weight") else tr("Registrar peso", "Log weight")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // kg | lb: convierte lo que ya escribiste (no se pierde) y se recuerda para la próxima.
                BodyUnitToggle(unit, { u ->
                    if (u != unit) {
                        val kgNow = value?.let { unit.toKg(it) }
                        unit = u
                        kgNow?.let { text = Fmt.num(u.fromKg(it), 1).replace(",", ".") }
                        onUnitChange(u)
                    }
                })
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { bump(-0.1) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Remove, tr("Menos", "Less")) }
                    // La letra no crece sin límite y con letra muy grande la unidad se queda solo en el botón kg | lb:
                    // así "173.1" se ve completo aun en libras y con letra al 200 % (antes, en libras, solo se veía el "1").
                    // (Ancho fijo a propósito: con weight() dentro de AlertDialog Compose entra en un ciclo de medición.)
                    val fs = LocalDensity.current.fontScale
                    val big = MaterialTheme.typography.headlineSmall
                    OutlinedTextField(
                        text, { t -> text = t.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                        modifier = Modifier.width(if (fs > 1.3f) 136.dp else 150.dp).padding(horizontal = 8.dp).focusRequester(focus).semantics { contentDescription = tr("Peso en ", "Weight in ") + unit.label },
                        textStyle = big.merge(Tabular).copy(textAlign = TextAlign.Center, fontSize = big.fontSize / maxOf(1f, fs / 1.15f)),
                        singleLine = true, suffix = if (fs > 1.3f) null else ({ Text(unit.label, style = MaterialTheme.typography.labelLarge) }),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = !valid && text.isNotEmpty(),
                    )
                    FilledTonalIconButton(onClick = { bump(0.1) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, tr("Más", "More")) }
                }
                // El error dice qué SÍ es válido (antes solo "Escribe un peso válido").
                Text(when {
                    valid -> "= " + Fmt.weight(kg, unit.other)
                    text.isEmpty() -> tr("Escribe lo que marca tu báscula", "Enter what your scale says")
                    unit == WeightUnit.KG -> tr("Entre 20 y 400 kg (44–880 lb)", "Between 20 and 400 kg (44–880 lb)")
                    else -> tr("Entre 44 y 880 lb (20–400 kg)", "Between 44 and 880 lb (20–400 kg)")
                }, style = MaterialTheme.typography.labelMedium, color = if (valid || text.isEmpty()) RupioColors.TextFaint else RupioColors.Danger, modifier = Modifier.padding(top = 6.dp))
                if (!lockDate) {
                    Spacer(Modifier.height(12.dp))
                    // Con letra grande los chips bajan de renglón en vez de apretarse letra por letra.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        FilterChip(day == today, { day = today }, { Text(tr("Hoy", "Today")) })
                        FilterChip(day == today.minusDays(1), { day = today.minusDays(1) }, { Text(tr("Ayer", "Yesterday")) })
                        FilterChip(day != today && day != today.minusDays(1), { picking = true },
                            { Text(if (day != today && day != today.minusDays(1)) Dates.formatDayShort(day) else tr("Otro día", "Another day")) },
                            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp)) })
                    }
                    // v1.9 (N4): si ese día ya tiene peso, se dice antes de reemplazarlo (antes desaparecía sin aviso).
                    existing?.let { e ->
                        Text(tr("Ya registraste ${Fmt.weight(e.weightKg, unit)} el ${Dates.formatDayShort(day)}. Si guardas, se reemplaza.", "You already logged ${Fmt.weight(e.weightKg, unit)} on ${Dates.formatDayShort(day)}. Saving replaces it."),
                            style = MaterialTheme.typography.bodySmall, color = RupioColors.Warning, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                    }
                } else {
                    Text(Dates.formatDay(day.toEpochDay().toInt()), style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 8.dp))
                }
                if (needConsent) HealthConsentCheckbox(consent, nudge = consentNudge && !consent) { consent = it; if (it) consentNudge = false }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && st != null, onClick = {
                if (needConsent && !consent) { consentNudge = true; return@TextButton }
                if (needConsent) c.scope.launch { c.giveHealthConsent() }
                onSave(day, kg!!)
            }) { Text(if (existing != null) tr("Reemplazar", "Replace") else tr("Guardar", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
    if (picking) {
        val zoneUtc = ZoneId.of("UTC")
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(zoneUtc).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.atStartOfDay(zoneUtc).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = java.time.Instant.ofEpochMilli(it).atZone(zoneUtc).toLocalDate() }
                    picking = false
                }) { Text(tr("Listo", "Done")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(tr("Cancelar", "Cancel")) } },
        ) { DatePicker(state) }
    }
}

/** Texto exacto de la casilla de consentimiento (también lo usa la prueba). */
val HEALTH_CONSENT_TEXT: String get() = tr("Acepto que Rupio guarde mi peso corporal (dato de salud) para mostrarme mi tendencia", "I agree that Rupio stores my body weight (health data) to show me my trend")

/** Casilla de consentimiento expreso del peso corporal. Toda la fila se puede tocar. */
@Composable
fun HealthConsentCheckbox(checked: Boolean, nudge: Boolean = false, onChange: (Boolean) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .then(if (nudge) Modifier.background(RupioColors.Danger.copy(alpha = 0.12f)) else Modifier)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(HEALTH_CONSENT_TEXT, style = MaterialTheme.typography.bodyMedium)
            if (nudge) Text(HEALTH_CONSENT_NUDGE, style = MaterialTheme.typography.bodySmall, color = RupioColors.Danger,
                modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            Text(tr("Puedes retirar tu permiso cuando quieras en Perfil; se borra tu historial de peso.", "You can withdraw your permission anytime in Profile; your weight history is erased."),
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        }
    }
}

/** v1.9: lo que se dice al tocar tr("Guardar", "Save") sin marcar la casilla. */
val HEALTH_CONSENT_NUDGE: String get() = tr("Marca la casilla para poder guardar tu peso.", "Check the box so your weight can be saved.")

/**
 * Pesos guardados con una versión anterior (antes de pedir permiso): se pide el permiso una vez, o se borran.
 */
@Composable
private fun HealthConsentCard() {
    val c = container()
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PrivacyTip, null, tint = RupioColors.Warning); Spacer(Modifier.width(8.dp))
            Text(tr("Tu permiso para guardar tu peso", "Your permission to store your weight"), style = MaterialTheme.typography.titleSmall)
        }
        Text(tr("Tu peso corporal es un dato de salud. Para seguir guardándolo necesitamos que lo aceptes.", "Your body weight is health data. To keep storing it, we need you to agree."),
            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
        HealthConsentCheckbox(checked) { checked = it }
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { deleting = true }, colors = ButtonDefaults.textButtonColors(contentColor = RupioColors.Danger)) { Text(tr("Borrar mi peso", "Delete my weight")) }
            Button(enabled = checked, onClick = { scope.launch { c.giveHealthConsent() } }) { Text(tr("Aceptar", "Accept")) }
        }
    }
    if (deleting) ConfirmDialog(tr("¿Borrar tu peso corporal?", "Delete your body weight?"), tr("Se borra todo tu historial de peso de este teléfono y de tu cuenta. No se puede deshacer.", "All your weight history is erased from this phone and your account. This can't be undone."),
        tr("Borrar", "Delete"), { deleting = false }, danger = true) { deleting = false; scope.launch { c.withdrawHealthConsent() } }
}

// ------------------------------------------------------------------ gráfica

/**
 * Puntos tenues = peso de cada día (o promedio semanal/mensual en rangos largos); línea = tendencia.
 * El eje X es proporcional al tiempo (los días sin registro dejan hueco, no se "aprietan").
 */
@Composable
fun BodyWeightChart(points: List<WeightPoint>, trend: List<Double>, unit: WeightUnit, modifier: Modifier = Modifier, interactive: Boolean = true) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val raw = points.map { unit.fromKg(it.kg) }
    val trk = trend.map { unit.fromKg(it) }
    val lo = minOf(raw.min(), trk.min()); val hi = maxOf(raw.max(), trk.max())
    val pad = max((hi - lo) * 0.15, if (unit == WeightUnit.LB) 1.0 else 0.5)
    val minY = lo - pad; val maxY = hi + pad
    val d0 = points.first().day; val d1 = max(points.last().day, d0 + 1)
    val density = LocalDensity.current
    val sel = points[selected.coerceIn(0, points.lastIndex)]
    Column(modifier.semantics { contentDescription = tr("Gráfica de peso corporal. Tendencia actual ", "Body weight chart. Current trend ") + Fmt.weight(trend.last(), unit) }) {
        if (interactive) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.weight(sel.kg, unit), style = MaterialTheme.typography.titleLarge.merge(Tabular))
                Spacer(Modifier.width(8.dp))
                Text(tr("tendencia ", "trend ") + Fmt.weight(trend[selected.coerceIn(0, trend.lastIndex)], unit), style = MaterialTheme.typography.labelMedium.merge(Tabular),
                    color = RupioColors.TextDim, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(Dates.formatDay(sel.day) + if (sel.n > 1) tr(" · promedio de ${sel.n} registros", " · average of ${sel.n} entries") else "",
                style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (interactive) {
                Column(Modifier.fillMaxHeight().width(38.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf(maxY, (maxY + minY) / 2, minY).forEach {
                        Text(Fmt.num(it, 0), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = RupioColors.TextFaint)
                    }
                }
            }
            fun idxAt(x: Float, w: Float): Int {
                val day = d0 + (x / w) * (d1 - d0)
                var best = 0; var bd = Float.MAX_VALUE
                points.forEachIndexed { i, p -> val dd = abs(p.day - day); if (dd < bd) { bd = dd; best = i } }
                return best
            }
            Canvas(
                Modifier.weight(1f).fillMaxHeight().then(if (interactive) Modifier.pointerInput(points) {
                    detectTapGestures { o -> selected = idxAt(o.x, size.width.toFloat()) }
                }.pointerInput(points) {
                    detectHorizontalDragGestures { ch, _ -> selected = idxAt(ch.position.x, size.width.toFloat()) }
                } else Modifier),
            ) {
                fun px(day: Int) = size.width * (day - d0) / (d1 - d0).toFloat()
                fun py(v: Double) = (size.height - (v - minY) / (maxY - minY) * size.height).toFloat()
                listOf(0f, 0.5f, 1f).forEach { f ->
                    drawLine(RupioColors.Outline.copy(alpha = 0.45f), Offset(0f, size.height * f), Offset(size.width, size.height * f), 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
                val r = with(density) { (if (points.size > 120) 1.5.dp else 2.5.dp).toPx() }
                points.forEachIndexed { i, p -> drawCircle(RupioColors.TextFaint.copy(alpha = 0.7f), r, Offset(px(p.day), py(raw[i]))) }
                if (trk.size >= 2) {
                    val path = Path()
                    trk.forEachIndexed { i, v -> val x = px(points[i].day); val y = py(v); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    drawPath(path, RupioColors.Chart, style = Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round))
                }
                if (interactive) {
                    val x = px(sel.day)
                    drawLine(RupioColors.TextFaint, Offset(x, 0f), Offset(x, size.height), 1f)
                    drawCircle(RupioColors.Surface, with(density) { 7.dp.toPx() }, Offset(x, py(trk[selected.coerceIn(0, trk.lastIndex)])))
                    drawCircle(RupioColors.Chart, with(density) { 5.dp.toPx() }, Offset(x, py(trk[selected.coerceIn(0, trk.lastIndex)])))
                }
            }
        }
        if (interactive) {
            Row(Modifier.fillMaxWidth().padding(start = 38.dp, top = 4.dp)) {
                Text(Dates.formatDayShort(LocalDate.ofEpochDay(d0.toLong())), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                Spacer(Modifier.weight(1f))
                Text(Dates.formatDayShort(LocalDate.ofEpochDay(points.last().day.toLong())), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
            }
        }
    }
}
