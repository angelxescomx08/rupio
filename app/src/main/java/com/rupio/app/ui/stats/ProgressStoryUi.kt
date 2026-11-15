package com.rupio.app.ui.stats

import com.rupio.app.safePop
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import com.rupio.app.data.local.DayBest
import com.rupio.app.domain.ExerciseProgress
import com.rupio.app.domain.ProgressStory
import com.rupio.app.domain.ProgressStoryBuilder
import com.rupio.app.ui.components.*
import com.rupio.app.ui.learn.ImportantBox
import com.rupio.app.ui.learn.PaperLinks
import com.rupio.app.ui.learn.Papers
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.math.abs

class ProgressStoryVM(private val c: AppContainer) : ViewModel() {
    var story by mutableStateOf<ProgressStory?>(null); private set
    init {
        // Se recalcula al terminar/borrar entrenos o al registrar peso (consultas agregadas: milisegundos).
        viewModelScope.launch {
            merge(c.workouts.observeCount().distinctUntilChanged(), c.bodyWeight.recent(1)).collect {
                val zone = c.zone.value
                story = c.stats.progressStory(zone, c.bodyWeight.trendAll(Dates.today(zone)))
            }
        }
    }
}

private fun setText(d: DayBest, unit: WeightUnit, repsOnly: Boolean) =
    if (repsOnly) "${d.reps ?: 0} reps" else "${Fmt.weight(d.weight, unit)} × ${d.reps ?: 0}"

private fun pctText(p: Double) = (if (p >= 0) "+" else "−") + Fmt.num(abs(p), 0) + " %"

// ------------------------------------------------------------------ tarjeta en Progreso

@Composable
fun ProgressStoryCard(onOpen: () -> Unit) {
    val vm = appViewModel { ProgressStoryVM(it) }
    val c = container()
    val unit = c.settings.settings.collectAsStateWithLifecycle(initialValue = null).value?.unit ?: WeightUnit.LB
    val s = vm.story ?: return
    Surface(shape = RoundedCornerShape(20.dp), color = RupioColors.Primary.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, RupioColors.Primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp)) {
            Text("Tu progreso", style = MaterialTheme.typography.labelLarge, color = RupioColors.Primary)
            Spacer(Modifier.height(2.dp))
            Text(ProgressStoryBuilder.headline(s), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (!s.isEmpty) {
                Spacer(Modifier.height(12.dp))
                s.improved.take(2).forEach { e -> ProgressBarRow(e, unit, compact = true); Spacer(Modifier.height(8.dp)) }
                TileRow {
                    Tile("Entrenos", "${s.total.n}", "en ${s.weeks} ${if (s.weeks == 1) "semana" else "semanas"}")
                    Tile("Racha", "${s.streakWeeks}", "semanas seguidas")
                    Tile("Récords", "${s.recordsTotal}", if (s.records30 > 0) "+${s.records30} este mes" else "en total")
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ver cómo has cambiado", style = MaterialTheme.typography.labelLarge, color = RupioColors.Primary)
                    Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.Primary)
                }
            }
        }
    }
}

/** Antes → ahora con barras que crecen (satisfactorio de ver). */
@Composable
private fun ProgressBarRow(e: ExerciseProgress, unit: WeightUnit, compact: Boolean = false) {
    val ratio = if (e.changePct <= -99) 0f else (1f / (1f + (e.changePct / 100f).toFloat())).coerceIn(0.05f, 1f)
    val anim = remember(e.exerciseId) { Animatable(0f) }
    LaunchedEffect(e.exerciseId, e.changePct) { anim.snapTo(0f); anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.name, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall, maxLines = 1,
                modifier = Modifier.weight(1f))
            Text(pctText(e.changePct), style = MaterialTheme.typography.labelLarge.merge(Tabular),
                color = if (e.changePct >= 1) RupioColors.Success else RupioColors.TextDim, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        // "Antes" (gris) mide lo que tu "ahora" (azul) mide hoy: la diferencia es lo que creciste.
        val beforeFrac = if (e.changePct >= 0) ratio else 1f
        val nowFrac = if (e.changePct >= 0) 1f else (1f + (e.changePct / 100f).toFloat()).coerceIn(0.05f, 1f)
        Bar(beforeFrac * anim.value, RupioColors.Surface3, "Antes: " + setText(e.first, unit, e.repsOnly))
        Spacer(Modifier.height(3.dp))
        Bar(nowFrac * anim.value, RupioColors.Chart, "Ahora: " + setText(e.now, unit, e.repsOnly))
    }
}

@Composable
private fun Bar(frac: Float, color: androidx.compose.ui.graphics.Color, label: String) {
    Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(RupioColors.Surface2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).clip(RoundedCornerShape(6.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium.merge(Tabular), modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp), maxLines = 1)
    }
}

// ------------------------------------------------------------------ pantalla completa

@Composable
fun ProgressStoryScreen(nav: NavHostController) {
    val vm = appViewModel { ProgressStoryVM(it) }
    val c = container()
    val unit = c.settings.settings.collectAsStateWithLifecycle(initialValue = null).value?.unit ?: WeightUnit.LB
    val s = vm.story
    Column(Modifier.fillMaxSize()) {
        RupioTopBar("Tu progreso", onBack = { nav.safePop() })
        if (s == null) { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(ProgressStoryBuilder.headline(s), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                s.firstDay?.let {
                    Text("Desde ${Dates.formatDay(it)} · ${s.weeks} ${if (s.weeks == 1) "semana" else "semanas"} · mejor racha: ${s.bestStreakWeeks} semanas",
                        style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
            }
            if (s.isEmpty) {
                item {
                    EmptyState(Icons.Rounded.Insights, "Aún no hay historia", "Termina tu primer entrenamiento y aquí verás cómo cambias semana a semana.")
                }
                return@LazyColumn
            }
            item {
                SectionCard {
                    Text("Este mes contra el anterior", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    TileRow {
                        Tile("Entrenos", "${s.last30.n}", "antes ${s.prev30.n}", if (s.last30.n >= s.prev30.n) RupioColors.Success else RupioColors.TextDim)
                        val dv = if (s.prev30.vol > 0) (s.last30.vol - s.prev30.vol) / s.prev30.vol * 100 else null
                        Tile("Volumen", Fmt.volume(s.last30.vol, unit), dv?.let { pctText(it) } ?: "primer mes", if ((dv ?: 0.0) >= 0) RupioColors.Success else RupioColors.TextDim)
                        Tile("Récords", "${s.records30}", "nuevos")
                    }
                }
            }
            if (s.exercises.isNotEmpty()) {
                item {
                    Text("Tu fuerza, antes y ahora", style = MaterialTheme.typography.titleMedium)
                    Text("Tu primera vez en cada ejercicio contra tu mejor serie reciente.", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
                items(s.exercises.filter { !it.stalled }.take(12), key = { it.exerciseId }) { e ->
                    SectionCard(Modifier.clickable { nav.navigate(Routes.exercise(e.exerciseId)) }) { ProgressBarRow(e, unit) }
                }
            }
            item {
                SectionCard {
                    Text("Todo lo que has levantado", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(Fmt.volume(s.total.vol, unit), style = MaterialTheme.typography.headlineMedium.merge(Tabular), fontWeight = FontWeight.SemiBold)
                    Text("${s.total.sets} series en ${s.total.n} entrenamientos", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                    ProgressStoryBuilder.equivalence(s.total.vol)?.let { Text("≈ $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            if (s.bodyFirstKg != null && s.bodyNowKg != null) item {
                SectionCard(Modifier.clickable { nav.navigate(Routes.BODY_WEIGHT) }) {
                    Text("Peso corporal (tendencia)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    val d = s.bodyNowKg - s.bodyFirstKg
                    Text("${Fmt.weight(s.bodyFirstKg, unit)} → ${Fmt.weight(s.bodyNowKg, unit)}  (${if (d >= 0) "+" else "−"}${Fmt.weight(abs(d), unit)})",
                        style = MaterialTheme.typography.titleMedium.merge(Tabular))
                }
            }
            item {
                SectionCard {
                    Text("Qué significa", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    ProgressStoryBuilder.meaning(s).forEach { line ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("•", color = RupioColors.Primary); Spacer(Modifier.width(8.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                ImportantBox("Las mayores ganancias llegan en los primeros meses y años de entrenamiento; después el progreso es más lento. " +
                    "Por eso vale tanto empezar con buena técnica, ejercicios estables y constancia.", title = "Aprovecha tus primeros años")
                Spacer(Modifier.height(6.dp))
                PaperLinks(listOf(Papers.LOPEZ, Papers.RHEA))
            }
            if (s.stalled.isNotEmpty()) {
                item {
                    Text("Ejercicios que no han subido", style = MaterialTheme.typography.titleMedium)
                    Text("Es normal estancarse a ratos. Prueba, en este orden:", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                }
                items(s.stalled, key = { "st_" + it.exerciseId }) { e ->
                    SectionCard(Modifier.clickable { nav.navigate(Routes.exercise(e.exerciseId)) }) {
                        Text(e.name, style = MaterialTheme.typography.titleSmall)
                        Text("Mejor reciente: ${setText(e.now, unit, e.repsOnly)} · última vez ${Dates.formatDay(e.lastDay)}", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                        Spacer(Modifier.height(6.dp))
                        Text("1) Revisa que la técnica, la máquina y el descanso sean siempre iguales. 2) Duerme y come suficiente. " +
                            "3) Si dos sesiones no llegas al mínimo de tu rango, baja ~10 % y vuelve a subir.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
