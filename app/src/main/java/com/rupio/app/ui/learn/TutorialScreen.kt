package com.rupio.app.ui.learn

import com.rupio.app.safePop
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.goToTab
import com.rupio.app.container
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.WeightPoint
import com.rupio.app.data.repo.Bar
import com.rupio.app.data.repo.MuscleLoad
import com.rupio.app.domain.WeightTrend
import com.rupio.app.ui.body.BodyWeightChart
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TutorialScreen(nav: NavHostController, id: String) {
    val t = Tutorials.byId(id) ?: return
    val c = container()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val unit = c.settings.settings.collectAsStateWithLifecycle(initialValue = null).value?.unit ?: WeightUnit.LB
    val hasQuiz = t.quiz.isNotEmpty()
    val pages = t.lessons.size + (if (hasQuiz) 1 else 0) + 1
    val pager = rememberPagerState { pages }
    val cur = pager.currentPage
    LaunchedEffect(cur) { if (cur == pages - 1) LearnProgress.markDone(ctx, t.id) }

    Column(Modifier.fillMaxSize()) {
        RupioTopBar(t.title, onBack = { nav.safePop() }, subtitle = "${cur + 1} de $pages")
        LinearProgressIndicator(progress = { (cur + 1f) / pages }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = RupioColors.Primary, trackColor = RupioColors.Surface3)
        HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    page < t.lessons.size -> LessonPage(t.lessons[page], unit)
                    hasQuiz && page == t.lessons.size -> QuizPage(t.quiz)
                    else -> DonePage(t) { action ->
                        when (action) {
                            TutorialAction.REPLAY_TOUR -> scope.launch {
                                c.settings.resetCoach()
                                nav.goToTab(Routes.HOME)
                            }
                            TutorialAction.OPEN_STATS -> nav.goToTab(Routes.STATS)
                            TutorialAction.LOG_WEIGHT -> nav.goToTab(Routes.BODY_WEIGHT)
                            TutorialAction.OPEN_GUIDE -> nav.navigate(Routes.GUIDE)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(cur - 1) } }, enabled = cur > 0, modifier = Modifier.weight(1f).height(48.dp)) { Text("Anterior") }
            Button(onClick = { if (cur < pages - 1) scope.launch { pager.animateScrollToPage(cur + 1) } else nav.safePop() },
                modifier = Modifier.weight(1f).height(48.dp)) { Text(if (cur < pages - 1) "Siguiente" else "Terminar") }
        }
    }
}

@Composable
private fun LessonPage(l: Lesson, unit: WeightUnit) {
    var sheet by remember { mutableStateOf<String?>(null) }
    Text(l.title, style = MaterialTheme.typography.headlineSmall)
    Text(l.body, style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim)
    l.important?.let { ImportantBox(it) }
    l.demo?.let { d -> SectionCard { DemoView(d, unit) } }
    if (l.concepts.isNotEmpty()) {
        Text("Toca para entender", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        FlowRowChips(l.concepts.mapNotNull { Concepts.byId(it) }) { sheet = it.id }
    }
    PaperLinks(l.papers)
    sheet?.let { ConceptSheet(it) { sheet = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(items: List<Concept>, onClick: (Concept) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { c -> AssistChip(onClick = { onClick(c) }, label = { Text(c.title) }, leadingIcon = { Icon(Icons.Rounded.HelpOutline, null, Modifier.size(16.dp)) }) }
    }
}

@Composable
private fun QuizPage(quiz: List<Quiz>) {
    Text("Mini quiz", style = MaterialTheme.typography.headlineSmall)
    Text("Toca una respuesta. No hay calificación: es para repasar.", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
    quiz.forEach { q ->
        var picked by remember(q) { mutableStateOf<Int?>(null) }
        SectionCard {
            Text(q.question, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            q.options.forEachIndexed { i, o ->
                val state = when { picked == null -> 0; i == q.correct -> 1; i == picked -> 2; else -> 0 }
                val color = when (state) { 1 -> RupioColors.Success; 2 -> RupioColors.Danger; else -> RupioColors.Outline }
                Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = if (state == 0) 0f else 0.12f), border = BorderStroke(1.dp, color),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = picked == null) { picked = i }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(o, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (state == 1) Icon(Icons.Rounded.CheckCircle, "Correcta", tint = RupioColors.Success)
                        if (state == 2) Icon(Icons.Rounded.Cancel, "Incorrecta", tint = RupioColors.Danger)
                    }
                }
            }
            picked?.let { p ->
                Spacer(Modifier.height(6.dp))
                Text((if (p == q.correct) "¡Bien! " else "Casi. ") + q.why, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        }
    }
}

@Composable
private fun DonePage(t: Tutorial, onAction: (TutorialAction) -> Unit) {
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(RupioColors.Success.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CheckCircle, null, tint = RupioColors.Success, modifier = Modifier.size(52.dp))
        }
    }
    Text("¡Listo!", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Text("Puedes repetir este tutorial cuando quieras desde Aprende → Tutoriales. Si un término no te queda claro, búscalo en el Glosario o toca el \"?\" junto a él en la app.",
        style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    t.action?.let { a ->
        Button(onClick = { onAction(a) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text(a.label) }
    }
}

// ------------------------------------------------------------------ demos que se pueden tocar

@Composable
fun DemoView(d: Demo, unit: WeightUnit) {
    when (d) {
        Demo.SETS -> SetsDemo()
        Demo.TECHNIQUE -> TechniqueDemo(unit)
        Demo.PROGRESSION -> ProgressionDemo(unit)
        Demo.DROP -> DropDemo(unit)
        Demo.VOLUME_CHART -> {
            Text("Ejemplo", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
            val start = LocalDate.of(2026, 8, 3)
            val vols = listOf(5200.0, 5600.0, 0.0, 6100.0, 6400.0, 6300.0, 7000.0, 7200.0)
            VolumeBarChart(vols.mapIndexed { i, v -> Bar("S${i + 1}", v, if (v > 0) 34 + i else 0, if (v > 0) 2 else 0, start.plusWeeks(i.toLong())) }, unit)
            Text("La semana 3 no hubo entrenos. Fuera de eso, el volumen va subiendo.", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
        }
        Demo.MUSCLE_CHART -> {
            Text("Ejemplo de una semana", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
            Spacer(Modifier.height(8.dp))
            MuscleSetsChart(listOf(MuscleLoad("cuadriceps", 12.0, 0.0), MuscleLoad("pecho", 10.0, 0.0), MuscleLoad("dorsales", 8.0, 0.0),
                MuscleLoad("triceps", 6.5, 0.0), MuscleLoad("biceps", 4.0, 0.0)), 1.0)
        }
        Demo.HEATMAP -> {
            Text("Ejemplo: lunes, martes, jueves y viernes", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
            Spacer(Modifier.height(8.dp))
            val to = LocalDate.of(2026, 11, 15); val from = to.minusWeeks(11).minusDays(6)
            val days = (0..((to.toEpochDay() - from.toEpochDay()).toInt())).mapNotNull { i ->
                val d = from.plusDays(i.toLong())
                val dow = d.dayOfWeek.value
                if (dow in listOf(1, 2, 4, 5) && (i * 7 % 11) != 3) d.toEpochDay().toInt() to (14 + (i % 9)) else null
            }.toMap()
            TrainingHeatmap(days, from, to)
        }
        Demo.E1RM_CHART -> {
            Text("Ejemplo: press de pecho", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
            Spacer(Modifier.height(8.dp))
            val start = LocalDate.of(2026, 8, 3)
            ProgressLineChart((0 until 12).map { i -> start.plusWeeks(i.toLong()) to (60.0 + i * 0.9 + if (i % 4 == 3) -1.2 else 0.0) }, unit)
        }
        Demo.WEIGHT_CHART -> {
            Text("Ejemplo: 8 semanas en déficit", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
            Spacer(Modifier.height(8.dp))
            val base = LocalDate.of(2026, 9, 20).toEpochDay().toInt()
            val pts = (0 until 56).filter { it % 6 != 4 }.map { i ->
                val noise = sin(i * 2.3) * 0.6 + (if (i % 7 == 6) 0.7 else 0.0)
                WeightPoint(base + i, 84.0 - i * 0.075 + noise, 1)
            }
            BodyWeightChart(pts, WeightTrend.smooth(pts), com.rupio.app.ui.body.rememberBodyUnit(), Modifier.fillMaxWidth().height(200.dp), interactive = true)
            Text("Los puntos brincan (agua, sal, comida); la línea baja poco a poco: eso es lo real.", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
        }
        Demo.ENERGY -> EnergyDemo()
    }
}

@Composable
private fun SetsDemo() {
    val done = remember { mutableStateListOf(false, false, false) }
    Text("Press de pecho · 3 × 10", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    done.forEachIndexed { i, d ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
            .background(if (d) RupioColors.Success.copy(alpha = 0.15f) else RupioColors.Surface2).clickable { done[i] = !d }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Serie ${i + 1}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("10 repeticiones", style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = RupioColors.TextDim)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (d) RupioColors.Success else RupioColors.Surface3), contentAlignment = Alignment.Center) {
                if (d) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
        if (d && i < 2) Text("   ⏱ descanso…", style = MaterialTheme.typography.labelSmall, color = RupioColors.Rest)
    }
    Spacer(Modifier.height(6.dp))
    Text(if (done.all { it }) "¡Ejercicio terminado! Hiciste 30 repeticiones en 3 series." else "Toca cada serie para marcarla (${done.count { it }} de 3).",
        style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
}

@Composable
private fun TechniqueDemo(unit: WeightUnit) {
    var same by remember { mutableStateOf(true) }
    fun w(lb: Double) = Fmt.num(if (unit == WeightUnit.LB) lb else lb / WeightUnit.KG_TO_LB, if (unit == WeightUnit.LB) 0 else 1) + " " + unit.label
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Misma máquina y recorrido completo", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(same, { same = it })
    }
    Spacer(Modifier.height(8.dp))
    Text("Semana 1: ${w(100.0)} × 10 · máquina A · completo", style = MaterialTheme.typography.bodyMedium.merge(Tabular))
    Text(if (same) "Semana 2: ${w(100.0)} × 11 · máquina A · completo" else "Semana 2: ${w(120.0)} × 12 · máquina B · a medias",
        style = MaterialTheme.typography.bodyMedium.merge(Tabular))
    Spacer(Modifier.height(8.dp))
    Surface(shape = RoundedCornerShape(12.dp), color = (if (same) RupioColors.Success else RupioColors.Danger).copy(alpha = 0.14f)) {
        Text(if (same) "✓ Progreso real: misma prueba, una repetición más." else "✗ No sabes si mejoraste: cambiaste la máquina y el recorrido.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp).fillMaxWidth())
    }
}

@Composable
private fun ProgressionDemo(unit: WeightUnit) {
    val reps = remember { mutableStateListOf(10, 10, 9) }
    val lb = 100.0
    fun w(lbv: Double) = Fmt.num(if (unit == WeightUnit.LB) lbv else lbv / WeightUnit.KG_TO_LB, if (unit == WeightUnit.LB) 0 else 1) + " " + unit.label
    Text("Rango 8–12 con ${w(lb)}. ¿Cuántas hiciste?", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    reps.forEachIndexed { i, r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Serie ${i + 1}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Stepper(r, { reps[i] = it }, 4..15, label = "reps")
        }
    }
    val (msg, color) = when {
        reps.all { it >= 12 } -> "Sube a ${w(lb + 5)} y vuelve a buscar 8 reps." to RupioColors.Warning
        reps.any { it < 8 } -> "Mantén ${w(lb)}. Si dos sesiones seguidas no llegas a 8, baja ~10 %." to RupioColors.Danger
        else -> "Mantén ${w(lb)} y suma 1 repetición en las series que puedas." to RupioColors.Primary
    }
    Spacer(Modifier.height(8.dp))
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.14f)) {
        Text("Rupio diría: $msg", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp).fillMaxWidth())
    }
}

@Composable
private fun DropDemo(unit: WeightUnit) {
    fun w(lb: Double) = Fmt.num(if (unit == WeightUnit.LB) lb else lb / WeightUnit.KG_TO_LB, if (unit == WeightUnit.LB) 0 else 1) + " " + unit.label
    Text("Mismo ejercicio, dos semanas", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    listOf("Semana 1" to 8, "Semana 2" to 5).forEach { (label, drop) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
            Column {
                Text("Serie normal: ${w(100.0)} × 10", style = MaterialTheme.typography.bodyMedium.merge(Tabular))
                Text("Drop set: ${w(70.0)} × $drop", style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = RupioColors.TextDim)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text("¿Empeoraste? No: el drop salió peor porque llegaste más cansado. La serie normal (igual las dos semanas) es la que dice la verdad.",
        style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
}

@Composable
private fun EnergyDemo() {
    var eat by remember { mutableFloatStateOf(2300f) }
    val spend = 2300
    val diff = eat.roundToInt() - spend
    Text("Gastas unas ${Fmt.num(spend.toDouble(), 0)} kcal al día", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Text("Comes: ${Fmt.num(eat.toDouble(), 0)} kcal", style = MaterialTheme.typography.bodyLarge.merge(Tabular))
    Slider(eat, { eat = (it / 50).roundToInt() * 50f }, valueRange = 1500f..3100f)
    val (label, body, color) = when {
        diff <= -150 -> Triple("Déficit (${Fmt.num(diff.toDouble(), 0)} kcal)", "Comes menos de lo que gastas → tu peso baja poco a poco.", RupioColors.Primary)
        diff >= 150 -> Triple("Superávit (+${Fmt.num(diff.toDouble(), 0)} kcal)", "Comes más de lo que gastas → tu peso sube (músculo y algo de grasa).", RupioColors.Warning)
        else -> Triple("Mantenimiento", "Comes lo que gastas → tu peso se queda igual.", RupioColors.Success)
    }
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.14f)) {
        Column(Modifier.padding(10.dp).fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text("Los números son un ejemplo: cuánto gasta cada persona varía.", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint, modifier = Modifier.padding(top = 6.dp))
}
