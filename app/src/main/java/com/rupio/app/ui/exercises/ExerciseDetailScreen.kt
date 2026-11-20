package com.rupio.app.ui.exercises

import com.rupio.app.safePop
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rupio.app.AppContainer
import com.rupio.app.appViewModel
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.ExerciseEntity
import com.rupio.app.data.local.Muscles
import com.rupio.app.data.local.SessionSetRow
import com.rupio.app.data.prefs.Settings
import com.rupio.app.domain.*
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExerciseDetailVM(private val c: AppContainer, val id: String) : ViewModel() {
    val exercise = c.exercises.observe(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val records = c.stats.recordsFor(id).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val curve = MutableStateFlow<List<Pair<LocalDate, Double>>>(emptyList())
    val sessions = MutableStateFlow<List<List<SessionSetRow>>>(emptyList())
    val recommendation = MutableStateFlow<Recommendation?>(null)

    init {
        viewModelScope.launch {
            val e = exercise.filterNotNull().first()
            val tr = Tracking.of(e.tracking)
            curve.value = when (tr) {
                Tracking.WEIGHT_REPS, Tracking.WEIGHTED_BODYWEIGHT -> c.stats.e1rm(id, e.name).points
                Tracking.DURATION -> c.stats.bestPerSession(id, duration = true)
                else -> c.stats.bestPerSession(id, duration = false)
            }
            sessions.value = c.stats.exerciseHistory(id).groupBy { it.workoutId }.values
                .map { rows -> rows.sortedBy { it.position } }.sortedByDescending { it.first().performedOn }.take(15)
            val (mn, mx) = when (tr) { Tracking.REPS_ONLY -> 8 to 15; Tracking.DURATION -> 1 to 1; else -> 8 to 12 }
            recommendation.value = c.workouts.recommendation(e, mn, mx, "")
        }
    }
}

@Composable
fun ExerciseDetailScreen(nav: NavHostController, id: String) {
    val vm = appViewModel { ExerciseDetailVM(it, id) }
    val e by vm.exercise.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val curve by vm.curve.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val rec by vm.recommendation.collectAsStateWithLifecycle()
    val unit = settings?.unit ?: WeightUnit.LB
    val context = LocalContext.current
    val ex = e

    Column(Modifier.fillMaxSize()) {
        RupioTopBar(ex?.name ?: "Ejercicio", onBack = { nav.safePop() })
        if (ex == null) return@Column
        val tr = Tracking.of(ex.tracking)
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ex.image0 != null) item { Column {
                AnimatedExerciseImage(ex.image0, ex.image1, Modifier.fillMaxWidth().aspectRatio(1.4f))
                // v1.9: si la imagen no es exactamente este ejercicio (otro aparato, agarre…), se dice debajo.
                val note by produceState<String?>(null, ex.id) { value = if (ex.isCustom) null else CatalogSeeder.imageNote(context, ex.id) }
                note?.let { n ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, tint = RupioColors.TextDim, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(n, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    }
                }
            } }
            else if (ex.isCustom) item { SectionCard { Text("Ejercicio personalizado (sin imagen)", color = RupioColors.TextDim) } }
            item {
                Button(
                    onClick = { openVideo(context, ex.name) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RupioColors.YouTube, contentColor = Color.White),
                ) { Icon(Icons.Rounded.PlayCircle, null); Spacer(Modifier.width(8.dp)); Text("Buscar videos de cómo hacerlo", fontWeight = FontWeight.SemiBold) }
            }
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(Muscles.label(ex.primaryMuscle), RupioColors.Primary, filled = true)
                        Pill(Muscles.equipmentLabel(ex.equipment), RupioColors.TextDim)
                        Pill(when (ex.level) { "expert" -> "Avanzado"; "intermediate" -> "Intermedio"; else -> "Principiante" }, RupioColors.TextDim)
                    }
                    val sec = ex.secondaryMuscles.split(',').filter { it.isNotBlank() }
                    if (sec.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("También trabaja: " + sec.joinToString { Muscles.label(it) }, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Se registra: ${tr.label}", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    if (ex.cue.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Técnica", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        TechniqueSteps(ex.cue)
                    }
                }
            }
            rec?.let { r -> item { RecommendationCard(r, unit, expanded = true) } }
            if (records.isNotEmpty()) item {
                SectionCard {
                    Text("Tus récords", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    records.sortedBy { it.kind }.forEach { r ->
                        val kind = RecordKind.of(r.kind)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = RupioColors.Warning, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(kind.label, style = MaterialTheme.typography.bodyMedium)
                                Text(Dates.formatDay(Dates.localEpochDay(r.achievedAt, java.time.ZoneId.systemDefault())), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                            }
                            when (kind) {
                                RecordKind.MAX_REPS -> Text("${r.value.toInt()} reps", style = MaterialTheme.typography.titleMedium.merge(Tabular))
                                RecordKind.MAX_DURATION -> Text(Fmt.duration(r.value.toLong()), style = MaterialTheme.typography.titleMedium.merge(Tabular))
                                else -> Column(horizontalAlignment = Alignment.End) { WeightBoth(r.value, unit) }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard {
                    Text(when (tr) { Tracking.WEIGHT_REPS, Tracking.WEIGHTED_BODYWEIGHT -> "1RM estimado por sesión"; Tracking.DURATION -> "Mejor tiempo por sesión"; else -> "Máximo de repeticiones por sesión" },
                        style = MaterialTheme.typography.titleMedium)
                    Text(when (tr) { Tracking.WEIGHT_REPS, Tracking.WEIGHTED_BODYWEIGHT -> "Lo que podrías levantar 1 vez, calculado con tu mejor serie (Epley/Brzycki, válido hasta ~10 reps)."; else -> "Tu mejor serie de cada día." },
                        style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                    Spacer(Modifier.height(8.dp))
                    when (tr) {
                        Tracking.WEIGHT_REPS, Tracking.WEIGHTED_BODYWEIGHT -> ProgressLineChart(curve, unit, title = "1RM estimado")
                        Tracking.DURATION -> ProgressLineChart(curve, unit, title = "Mejor tiempo", toDisplay = { it }, label = { Fmt.duration(it.toLong()) }, subLabel = null, deltaLabel = { Fmt.duration(it.toLong()) })
                        else -> ProgressLineChart(curve, unit, title = "Más repeticiones", toDisplay = { it }, label = { "${it.toInt()} reps" }, subLabel = null, deltaLabel = { "${it.toInt()} reps" })
                    }
                }
            }
            if (sessions.isNotEmpty()) {
                item { SectionTitle("Historial") }
                items(sessions) { rows -> SessionCard(rows, tr, unit) }
            }
        }
    }
}

/**
 * v1.9 · La técnica en pasos cortos (una línea por paso en el catálogo): posición, movimiento y el error más común,
 * para aprender sin salir de la app. Antes era una sola frase. Un texto de una línea (ejercicio propio) se ve igual.
 */
@Composable
fun TechniqueSteps(cue: String) {
    val steps = techniqueSteps(cue)
    if (steps.size <= 1) { Text(cue.trim(), style = MaterialTheme.typography.bodyMedium); return }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var n = 0
        steps.forEach { s ->
            val warning = s.startsWith(COMMON_ERROR)
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(24.dp), contentAlignment = Alignment.TopCenter) {
                    if (warning) Icon(Icons.Rounded.WarningAmber, null, tint = RupioColors.Warning, modifier = Modifier.size(18.dp))
                    else Text("${++n}.", style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = RupioColors.Primary, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(6.dp))
                Text(s, style = MaterialTheme.typography.bodyMedium, color = if (warning) RupioColors.Warning else RupioColors.Text)
            }
        }
    }
}

private const val COMMON_ERROR = "Error común:"

/** Pasos de la técnica: una línea cada uno, sin vacíos. */
fun techniqueSteps(cue: String): List<String> = cue.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun SessionCard(rows: List<SessionSetRow>, tr: Tracking, unit: WeightUnit) {
    SectionCard(padding = 14.dp) {
        Text(Dates.formatDay(rows.first().performedOn), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 2.dp)) {
                val type = SetType.of(s.setType)
                Text(if (type == SetType.NORMAL) "${rows.take(i + 1).count { SetType.of(it.setType) != SetType.WARMUP }}" else type.short,
                    color = if (type == SetType.WARMUP) RupioColors.Warning else RupioColors.TextDim, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium.merge(Tabular))
                Text(formatSet(s.weightKg, s.reps, s.durationSec, tr, unit), style = MaterialTheme.typography.bodyMedium.merge(Tabular))
                s.rpe?.let { Text("  · RPE ${Fmt.num(it)}", style = MaterialTheme.typography.bodySmall, color = RupioColors.TextFaint) }
            }
        }
    }
}

/** "135 lb × 10", "12 reps", "0:45", "+25 lb × 8", "−30 lb × 6 (asist.)". */
fun formatSet(weightKg: Double?, reps: Int?, durationSec: Int?, tr: Tracking, unit: WeightUnit): String = when (tr) {
    Tracking.DURATION -> Fmt.duration((durationSec ?: 0).toLong())
    Tracking.REPS_ONLY -> "${reps ?: 0} reps"
    Tracking.WEIGHTED_BODYWEIGHT -> if ((weightKg ?: 0.0) > 0) "+${Fmt.weight(weightKg, unit)} × ${reps ?: 0}" else "${reps ?: 0} reps"
    Tracking.ASSISTED_BODYWEIGHT -> if ((weightKg ?: 0.0) < 0) "${reps ?: 0} reps · ayuda ${Fmt.weight(-(weightKg ?: 0.0), unit)}" else "${reps ?: 0} reps"
    Tracking.WEIGHT_REPS -> "${Fmt.weight(weightKg, unit)} × ${reps ?: 0}"
}

/** Tarjeta de recomendación del motor de progresión, con fuentes científicas desplegables. */
@Composable
fun RecommendationCard(r: Recommendation, unit: WeightUnit, expanded: Boolean = false, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(expanded) }
    val (color, icon) = when (r.kind) {
        RecKind.INCREASE_LOAD, RecKind.LESS_ASSISTANCE, RecKind.HARDER_VARIATION -> RupioColors.Warning to Icons.Rounded.ArrowUpward
        RecKind.DELOAD -> RupioColors.Danger to Icons.Rounded.ArrowDownward
        // v1.9: "Mantén 100 lb" ya no lleva un "+" que sugería subir: mismo peso, más repeticiones (o más tiempo).
        RecKind.ADD_REPS -> RupioColors.Primary to Icons.Rounded.Repeat
        RecKind.ADD_TIME -> RupioColors.Primary to Icons.Rounded.Timer
        RecKind.HOLD -> RupioColors.TextDim to Icons.Rounded.Pause
        RecKind.FIRST_TIME -> RupioColors.Success to Icons.Rounded.AutoAwesome
    }
    Surface(onClick = { open = !open }, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.12f)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(r.title, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                r.targetWeightKg?.takeIf { it > 0 }?.let { Text(Fmt.weight(it, unit.other), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = RupioColors.TextFaint) }
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Ocultar" else "Por qué", tint = RupioColors.TextFaint)
            }
            if (open) {
                Spacer(Modifier.height(4.dp))
                Text(r.detail, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                if (r.sources.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Basado en: " + r.sources.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                }
            }
        }
    }
}
