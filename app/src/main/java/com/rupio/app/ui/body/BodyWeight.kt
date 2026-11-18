package com.rupio.app.ui.body

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.rupio.app.safePop
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
enum class WeightRange(val label: String, val days: Long?) { M1("1M", 30), M3("3M", 90), Y1("1A", 365), ALL("Todo", null) }

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
            Text("Peso corporal", style = MaterialTheme.typography.titleMedium)
            HelpIcon("peso_corporal")
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { logging = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Registrar")
            }
        }
        val l = latest
        if (l == null) {
            Text("Anota tu peso para ver tu tendencia. Tarda 3 segundos y puedes hacerlo cuando quieras.",
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
            Text("Tendencia · último registro ${Dates.formatDay(l.measuredOn)}: ${Fmt.weight(l.weightKg, unit)}",
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
    SingleChoiceSegmentedButtonRow(modifier.semantics { contentDescription = "Unidad del peso corporal: ${unit.label}" }) {
        listOf(WeightUnit.KG, WeightUnit.LB).forEachIndexed { i, u ->
            SegmentedButton(unit == u, { onChange(u) }, SegmentedButtonDefaults.itemShape(i, 2), icon = {},
                modifier = Modifier.widthIn(min = 56.dp)) { Text(u.label) }
        }
    }
}

@Composable
private fun RateText(rate: Double) {
    val arrow = when { abs(rate) < 0.15 -> "="; rate < 0 -> "▼"; else -> "▲" }
    Text("$arrow ${Fmt.num(abs(rate), 1)} %/sem", style = MaterialTheme.typography.labelLarge.merge(Tabular), color = RupioColors.TextDim)
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
        topBar = { RupioTopBar("Peso corporal", onBack = null) { BodyUnitToggle(unit, vm::setUnit, Modifier.padding(end = 12.dp)) } },
        // El botón grande está en el resumen de arriba; el flotante solo aparece cuando ya bajaste (historial).
        floatingActionButton = {
            val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
            androidx.compose.animation.AnimatedVisibility(scrolled, enter = androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.scaleOut()) {
                ExtendedFloatingActionButton(onClick = { logging = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Registrar peso") },
                    modifier = Modifier.semantics { contentDescription = "Registrar peso" },
                    containerColor = RupioColors.Primary, contentColor = RupioColors.OnPrimary)
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { WeightHero(latest, v, unit, today) { if (latest?.measuredOn == today) editing = latest else logging = true } }
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
                        Text(if (latest == null) "Aún no tienes registros. Toca \"Registrar peso\"." else "Sin registros en este rango.",
                            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.padding(vertical = 24.dp))
                    } else if (v.points.isNotEmpty()) {
                        BodyWeightChart(v.points, v.trend, unit, Modifier.fillMaxWidth().height(200.dp), interactive = true)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(RupioColors.TextFaint))
                            Text("  Peso de cada día", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                            Spacer(Modifier.width(14.dp))
                            Box(Modifier.width(16.dp).height(3.dp).background(RupioColors.Chart))
                            Text("  Tendencia", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                        }
                    }
                }
            }
            if (v.points.isNotEmpty()) item {
                val t = v.trend
                TileRow {
                    Tile("Tendencia hoy", Fmt.weight(t.last(), unit), Fmt.weight(t.last(), unit.other))
                    val change = t.last() - t.first()
                    Tile("Cambio en el rango", (if (change >= 0) "+" else "−") + Fmt.weight(abs(change), unit), "${v.points.size} registros")
                    Tile("Ritmo", v.ratePct?.let { (if (it >= 0) "+" else "−") + Fmt.num(abs(it), 1) + " %" } ?: "—",
                        if (v.ratePct != null) "por semana" else "faltan días")
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
                        Text("Pésate siempre igual: al despertar, después de ir al baño y antes de comer o beber. " +
                            "Un día puedes pesar 1–2 kg más por agua o sal; por eso mira la tendencia, no el número de un día.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (vm.history.isNotEmpty()) item { SectionTitle("Registros") }
            items(vm.history, key = { it.measuredOn }) { e ->
                Surface(shape = RoundedCornerShape(14.dp), color = RupioColors.Surface) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Editar") { editing = e }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.formatDay(e.measuredOn), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        WeightBoth(e.weightKg, unit)
                        IconButton(onClick = { deleting = e }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.DeleteOutline, "Borrar registro", tint = RupioColors.TextDim) }
                    }
                }
            }
        }
    }
    if (logging) LogWeightDialog(latest?.weightKg, unit, onUnitChange = vm::setUnit, onDismiss = { logging = false }) { d, kg -> vm.log(d, kg); logging = false }
    editing?.let { e ->
        LogWeightDialog(e.weightKg, unit, LocalDate.ofEpochDay(e.measuredOn.toLong()), lockDate = true, onUnitChange = vm::setUnit, onDismiss = { editing = null }) { d, kg -> vm.log(d, kg); editing = null }
    }
    deleting?.let { e ->
        ConfirmDialog("¿Borrar este registro?", "${Dates.formatDay(e.measuredOn)}: ${Fmt.weightBoth(e.weightKg, unit)}", "Borrar", { deleting = null }, danger = true) {
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
private fun WeightHero(latest: BodyWeightEntity?, v: WeightView, unit: WeightUnit, today: Int, onLog: () -> Unit) {
    SectionCard {
        if (latest == null) {
            Text("⚖️", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("Registra tu peso", style = MaterialTheme.typography.titleLarge)
            Text("Pésate 2 o 3 veces por semana y Rupio te muestra tu tendencia, sin que te asusten los días raros.",
                style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
        } else {
            val shown = v.trend.lastOrNull() ?: latest.weightKg
            Text("Tu peso (tendencia)", style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
            FlowRow(verticalArrangement = Arrangement.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Fmt.weight(shown, unit), style = MaterialTheme.typography.displaySmall.merge(Tabular))
                Text(Fmt.weight(shown, unit.other), style = MaterialTheme.typography.titleSmall.merge(Tabular), color = RupioColors.TextFaint,
                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 8.dp))
            }
            v.ratePct?.let { RateText(it) }
            val todayDone = latest.measuredOn == today
            Text(if (todayDone) "Hoy: ${Fmt.weight(latest.weightKg, unit)} ✓" else "Último registro: ${Dates.formatDay(latest.measuredOn)} · ${Fmt.weight(latest.weightKg, unit)}",
                style = MaterialTheme.typography.bodySmall, color = if (todayDone) RupioColors.Success else RupioColors.TextDim)
        }
        Spacer(Modifier.height(12.dp))
        val todayDone = latest?.measuredOn == today
        Button(onClick = onLog, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(if (todayDone) Icons.Rounded.Edit else Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp))
            Text(if (todayDone) "Corregir el de hoy" else "Registrar peso de hoy", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
    var text by remember { mutableStateOf(Fmt.num(start, 1).replace(",", ".")) }
    var picking by remember { mutableStateOf(false) }
    // v1.7 (N2): el peso corporal es un dato de salud; la primera vez se pide permiso expreso con una casilla.
    val st by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val needConsent = st != null && st?.healthConsentAt == null
    var consent by remember { mutableStateOf(false) }
    val value = text.replace(',', '.').toDoubleOrNull()
    val kg = value?.let { unit.toKg(it) }
    val valid = kg != null && kg in 20.0..400.0
    fun bump(d: Double) { val b = (value ?: start) + d; text = Fmt.num(max(0.0, b), 1).replace(",", ".") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lockDate) "Editar peso" else "Registrar peso") },
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
                    FilledTonalIconButton(onClick = { bump(-0.1) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Remove, "Menos") }
                    // La letra no crece sin límite y con letra muy grande la unidad se queda solo en el botón kg | lb:
                    // así "173.1" se ve completo aun en libras y con letra al 200 % (antes, en libras, solo se veía el "1").
                    // (Ancho fijo a propósito: con weight() dentro de AlertDialog Compose entra en un ciclo de medición.)
                    val fs = LocalDensity.current.fontScale
                    val big = MaterialTheme.typography.headlineSmall
                    OutlinedTextField(
                        text, { t -> text = t.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                        modifier = Modifier.width(if (fs > 1.3f) 136.dp else 150.dp).padding(horizontal = 8.dp).semantics { contentDescription = "Peso en ${unit.label}" },
                        textStyle = big.merge(Tabular).copy(textAlign = TextAlign.Center, fontSize = big.fontSize / maxOf(1f, fs / 1.15f)),
                        singleLine = true, suffix = if (fs > 1.3f) null else ({ Text(unit.label, style = MaterialTheme.typography.labelLarge) }),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = !valid,
                    )
                    FilledTonalIconButton(onClick = { bump(0.1) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "Más") }
                }
                // El error dice qué SÍ es válido (antes solo "Escribe un peso válido").
                Text(if (valid) "= " + Fmt.weight(kg, unit.other) else if (unit == WeightUnit.KG) "Entre 20 y 400 kg (44–880 lb)" else "Entre 44 y 880 lb (20–400 kg)",
                    style = MaterialTheme.typography.labelMedium, color = if (valid) RupioColors.TextFaint else RupioColors.Danger, modifier = Modifier.padding(top = 6.dp))
                if (!lockDate) {
                    Spacer(Modifier.height(12.dp))
                    // Con letra grande los chips bajan de renglón en vez de apretarse letra por letra.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        FilterChip(day == today, { day = today }, { Text("Hoy") })
                        FilterChip(day == today.minusDays(1), { day = today.minusDays(1) }, { Text("Ayer") })
                        FilterChip(day != today && day != today.minusDays(1), { picking = true },
                            { Text(if (day != today && day != today.minusDays(1)) Dates.formatDayShort(day) else "Otro día") },
                            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp)) })
                    }
                } else {
                    Text(Dates.formatDay(day.toEpochDay().toInt()), style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 8.dp))
                }
                if (needConsent) HealthConsentCheckbox(consent) { consent = it }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && st != null && (!needConsent || consent), onClick = {
                if (needConsent) c.scope.launch { c.giveHealthConsent() }
                onSave(day, kg!!)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
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
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}

/** Texto exacto de la casilla de consentimiento (también lo usa la prueba). */
const val HEALTH_CONSENT_TEXT = "Acepto que Rupio guarde mi peso corporal (dato de salud) para mostrarme mi tendencia"

/** Casilla de consentimiento expreso del peso corporal. Toda la fila se puede tocar. */
@Composable
fun HealthConsentCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(HEALTH_CONSENT_TEXT, style = MaterialTheme.typography.bodyMedium)
            Text("Puedes retirar tu permiso cuando quieras en Perfil; se borra tu historial de peso.",
                style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        }
    }
}

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
            Text("Tu permiso para guardar tu peso", style = MaterialTheme.typography.titleSmall)
        }
        Text("Tu peso corporal es un dato de salud. Para seguir guardándolo necesitamos que lo aceptes.",
            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
        HealthConsentCheckbox(checked) { checked = it }
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { deleting = true }, colors = ButtonDefaults.textButtonColors(contentColor = RupioColors.Danger)) { Text("Borrar mi peso") }
            Button(enabled = checked, onClick = { scope.launch { c.giveHealthConsent() } }) { Text("Aceptar") }
        }
    }
    if (deleting) ConfirmDialog("¿Borrar tu peso corporal?", "Se borra todo tu historial de peso de este teléfono y de tu cuenta. No se puede deshacer.",
        "Borrar", { deleting = false }, danger = true) { deleting = false; scope.launch { c.withdrawHealthConsent() } }
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
    val tr = trend.map { unit.fromKg(it) }
    val lo = minOf(raw.min(), tr.min()); val hi = maxOf(raw.max(), tr.max())
    val pad = max((hi - lo) * 0.15, if (unit == WeightUnit.LB) 1.0 else 0.5)
    val minY = lo - pad; val maxY = hi + pad
    val d0 = points.first().day; val d1 = max(points.last().day, d0 + 1)
    val density = LocalDensity.current
    val sel = points[selected.coerceIn(0, points.lastIndex)]
    Column(modifier.semantics { contentDescription = "Gráfica de peso corporal. Tendencia actual ${Fmt.weight(trend.last(), unit)}" }) {
        if (interactive) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.weight(sel.kg, unit), style = MaterialTheme.typography.titleLarge.merge(Tabular))
                Spacer(Modifier.width(8.dp))
                Text("tendencia " + Fmt.weight(trend[selected.coerceIn(0, trend.lastIndex)], unit), style = MaterialTheme.typography.labelMedium.merge(Tabular),
                    color = RupioColors.TextDim, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(Dates.formatDay(sel.day) + if (sel.n > 1) " · promedio de ${sel.n} registros" else "",
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
                    detectDragGestures { ch, _ -> selected = idxAt(ch.position.x, size.width.toFloat()) }
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
                if (tr.size >= 2) {
                    val path = Path()
                    tr.forEachIndexed { i, v -> val x = px(points[i].day); val y = py(v); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    drawPath(path, RupioColors.Chart, style = Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round))
                }
                if (interactive) {
                    val x = px(sel.day)
                    drawLine(RupioColors.TextFaint, Offset(x, 0f), Offset(x, size.height), 1f)
                    drawCircle(RupioColors.Surface, with(density) { 7.dp.toPx() }, Offset(x, py(tr[selected.coerceIn(0, tr.lastIndex)])))
                    drawCircle(RupioColors.Chart, with(density) { 5.dp.toPx() }, Offset(x, py(tr[selected.coerceIn(0, tr.lastIndex)])))
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
