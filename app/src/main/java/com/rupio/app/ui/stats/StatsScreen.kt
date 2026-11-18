package com.rupio.app.ui.stats

import com.rupio.app.goToTab
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.rupio.app.data.repo.E1rmSeries
import com.rupio.app.data.repo.Period
import com.rupio.app.data.repo.StatsSnapshot
import com.rupio.app.data.repo.Totals
import com.rupio.app.domain.RecordKind
import com.rupio.app.domain.Tracking
import com.rupio.app.ui.components.*
import com.rupio.app.ui.body.BodyWeightCard
import com.rupio.app.ui.learn.HelpIcon
import com.rupio.app.ui.learn.TitleWithHelp
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StatsVM(private val c: AppContainer) : ViewModel() {
    var period by mutableStateOf(Period.MONTH); private set
    var snapshot by mutableStateOf<StatsSnapshot?>(null); private set
    var exerciseId by mutableStateOf<String?>(null); private set
    var curve by mutableStateOf<E1rmSeries?>(null); private set
    private var job: Job? = null

    init {
        // Recalcula si cambian los entrenamientos, la zona horaria o el inicio de semana.
        viewModelScope.launch {
            combine(c.workouts.observeCount(), c.zone, c.settings.settings.map { it.weekStart }) { a, b, d -> Triple(a, b, d) }
                .distinctUntilChanged().collect { load() }
        }
    }

    fun select(p: Period) { period = p; load() }
    fun selectExercise(id: String) { exerciseId = id; loadCurve() }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            val s = c.stats.snapshot(period, c.zone.value)
            snapshot = s
            if (exerciseId == null || s.topExercises.none { it.exerciseId == exerciseId }) exerciseId = s.topExercises.firstOrNull()?.exerciseId
            loadCurve()
        }
    }

    private fun loadCurve() = viewModelScope.launch {
        val id = exerciseId ?: run { curve = null; return@launch }
        val name = snapshot?.topExercises?.firstOrNull { it.exerciseId == id }?.name.orEmpty()
        curve = c.stats.e1rm(id, name)
    }
}

@Composable
fun StatsScreen(nav: NavHostController) {
    val vm = appViewModel { StatsVM(it) }
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val records by c.stats.recentRecords(8).collectAsStateWithLifecycle(initialValue = emptyList())
    val unit = settings?.unit ?: WeightUnit.LB
    val s = vm.snapshot
    // v1.9: "Tu liga". Lo guardado se ve al instante; al servidor solo si lo último tiene más de 5 min.
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val leagueAccess = com.rupio.app.ui.support.wallAccess(auth)
    val league by c.league.state.collectAsStateWithLifecycle()
    LaunchedEffect(leagueAccess) { if (leagueAccess == com.rupio.app.ui.support.WallAccess.READY) { c.league.warmUp(); c.league.load(maxAgeMs = 5 * 60_000L) } }

    Column(Modifier.fillMaxSize()) {
        RupioTopBar("Progreso", onBack = null) {
            // Con letra muy grande solo el ícono (el título "Progreso" no se corta).
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f)
                IconButton(onClick = { nav.navigate(Routes.tutorial("graficas")) }) { Icon(Icons.Rounded.School, "Cómo leer las gráficas", tint = RupioColors.Primary) }
            else TextButton(onClick = { nav.navigate(Routes.tutorial("graficas")) }) {
                Icon(Icons.Rounded.School, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Cómo leerlas")
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Period.entries.forEach { p -> FilterChip(vm.period == p, { vm.select(p) }, label = { Text(p.label) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ProgressStoryCard { nav.navigate(Routes.PROGRESS) } }
            item { BodyWeightCard { nav.goToTab(Routes.BODY_WEIGHT) } }
            if (s == null) { item { LoadingBlock("Calculando tus estadísticas…") }; return@LazyColumn }
            item {
                Text(rangeLabel(s), style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                Spacer(Modifier.height(8.dp))
                TileRow {
                    Tile("Volumen", Fmt.volume(s.totals.volumeKg, unit), sub = delta(s.totals.volumeKg, s.previous?.volumeKg) ?: Fmt.volume(s.totals.volumeKg, unit.other),
                        subColor = deltaColor(s.totals.volumeKg, s.previous?.volumeKg))
                    Tile("Entrenos", "${s.totals.workouts}", sub = delta(s.totals.workouts.toDouble(), s.previous?.workouts?.toDouble()),
                        subColor = deltaColor(s.totals.workouts.toDouble(), s.previous?.workouts?.toDouble()))
                }
                Spacer(Modifier.height(8.dp))
                TileRow {
                    Tile("Series", "${s.totals.sets}", sub = delta(s.totals.sets.toDouble(), s.previous?.sets?.toDouble()),
                        subColor = deltaColor(s.totals.sets.toDouble(), s.previous?.sets?.toDouble()))
                    Tile("Reps", "${s.totals.reps}")
                    Tile("Duración media", Fmt.durationHuman(s.totals.avgDurationSec.toLong()))
                }
            }
            item {
                SectionCard {
                    TitleWithHelp("Volumen " + when (s.bucket) { com.rupio.app.data.repo.Bucket.DAY -> "por día"; com.rupio.app.data.repo.Bucket.WEEK -> "por semana"; com.rupio.app.data.repo.Bucket.MONTH -> "por mes"; else -> "por año" }, "volumen")
                    Text("Peso × repeticiones de tus series efectivas (sin calentamiento). Toca una barra.", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                    Spacer(Modifier.height(8.dp))
                    VolumeBarChart(s.bars, unit)
                }
            }
            // Debajo de tu volumen (lo que cuenta en la liga), no arriba: primero tu progreso, luego la comparación.
            if (com.rupio.app.ui.support.showLeagueCard(leagueAccess, league.league)) item(key = "league") {
                com.rupio.app.ui.support.LeagueCardContent(league.league, unit) { nav.navigate(Routes.LEAGUE) }
            }
            item {
                SectionCard {
                    TitleWithHelp("Series por músculo a la semana", "series_semana")
                    Text("Más series ayudan, pero cada vez menos: no hace falta pasarse.", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                    Spacer(Modifier.height(10.dp))
                    if (s.muscles.isEmpty()) Text("Sin series en este periodo.", color = RupioColors.TextDim, style = MaterialTheme.typography.bodySmall)
                    else MuscleSetsChart(s.muscles, s.weeksInPeriod, judge = !s.firstWeek)
                }
            }
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Constancia", style = MaterialTheme.typography.titleMedium)
                        HelpIcon("constancia")
                        Spacer(Modifier.weight(1f))
                        val streak = weekStreak(s)
                        if (streak > 0) Pill("🔥 $streak ${if (streak == 1) "semana" else "semanas"} seguidas", RupioColors.Warning)
                    }
                    Spacer(Modifier.height(10.dp))
                    TrainingHeatmap(s.trainingDays, s.heatFrom, s.to)
                }
            }
            if (s.topExercises.isNotEmpty()) item {
                SectionCard {
                    TitleWithHelp("Fuerza estimada (1RM)", "1rm")
                    Text("Lo que podrías levantar una vez, calculado de tu mejor serie de cada sesión.", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.topExercises.forEach { e -> FilterChip(vm.exerciseId == e.exerciseId, { vm.selectExercise(e.exerciseId) }, label = { Text(e.name, maxLines = 1) }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    vm.curve?.let { cv ->
                        ProgressLineChart(cv.points, unit, title = "1RM estimado de ${cv.name}")
                        TextButton(onClick = { nav.navigate(Routes.exercise(cv.exerciseId)) }) { Text("Ver historial de ${cv.name}") }
                    }
                }
            }
            if (records.isNotEmpty()) item {
                SectionCard {
                    TitleWithHelp("Récords recientes", "record")
                    Spacer(Modifier.height(6.dp))
                    records.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = RupioColors.Warning, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(RecordKind.of(r.kind).label + " · " + Dates.formatDay(Dates.localEpochDay(r.achievedAt, c.zone.value)), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                            }
                            Text(when (RecordKind.of(r.kind)) {
                                RecordKind.MAX_REPS -> "${r.value.toInt()} reps"
                                RecordKind.MAX_DURATION -> Fmt.duration(r.value.toLong())
                                else -> Fmt.weightBoth(r.value, unit)
                            }, style = MaterialTheme.typography.labelLarge.merge(Tabular))
                        }
                    }
                }
            }
            // Aprende ya no está en la barra (la pestaña es Peso): aquí queda a un toque, junto a tus números.
            item {
                SectionCard(Modifier.clickable { nav.navigate(Routes.LEARN) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.School, null, tint = RupioColors.Primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Aprende", style = MaterialTheme.typography.titleMedium)
                            Text("Cómo progresar, cuánto entrenar, nutrición y glosario con estudios", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
                    }
                }
            }
        }
    }
}

internal fun rangeLabel(s: StatsSnapshot) = if (s.period == Period.ALL) "Desde ${Dates.formatDayShort(s.from)} ${s.from.year} · todo tu historial"
    else rangeText(s.from, s.to) + " · comparado con el periodo anterior"

/** v1.9: con años distintos, el año va en las dos fechas ("1 dic 2025 – 18 nov 2026"; antes "1 dic – 18 nov 2026"). */
internal fun rangeText(from: java.time.LocalDate, to: java.time.LocalDate): String =
    if (from.year != to.year) "${Dates.formatDayShort(from)} ${from.year} – ${Dates.formatDayShort(to)} ${to.year}"
    else "${Dates.formatDayShort(from)} – ${Dates.formatDayShort(to)} ${to.year}"

private fun delta(now: Double, prev: Double?): String? {
    if (prev == null) return null
    if (prev <= 0.0) return if (now > 0) "nuevo" else "igual"
    val pct = (now - prev) / prev * 100
    if (kotlin.math.abs(pct) < 0.5) return "= igual"
    return (if (pct >= 0) "▲ " else "▼ ") + Fmt.num(kotlin.math.abs(pct), 0) + " %"
}

private fun deltaColor(now: Double, prev: Double?) = when {
    prev == null || prev <= 0 || kotlin.math.abs(now - prev) / prev < 0.005 -> RupioColors.TextDim
    now >= prev -> RupioColors.Success
    else -> RupioColors.Danger
}

/** Semanas seguidas (hasta la actual o la anterior) con al menos un entrenamiento. */
private fun weekStreak(s: StatsSnapshot): Int {
    var streak = 0
    var week = s.to.minusDays(((s.to.toEpochDay() - s.heatFrom.toEpochDay()) % 7))
    var allowSkipCurrent = true
    while (!week.isBefore(s.heatFrom)) {
        val has = (0 until 7).any { d -> (s.trainingDays[week.plusDays(d.toLong()).toEpochDay().toInt()] ?: 0) > 0 }
        if (has) streak++ else if (allowSkipCurrent && streak == 0) Unit else break
        allowSkipCurrent = false
        week = week.minusWeeks(1)
    }
    return streak
}
