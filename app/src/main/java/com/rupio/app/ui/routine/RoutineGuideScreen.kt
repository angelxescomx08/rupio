package com.rupio.app.ui.routine

import com.rupio.app.safePop
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.goToTab
import com.rupio.app.container
import com.rupio.app.core.Fmt
import com.rupio.app.core.Ids
import com.rupio.app.data.local.ExerciseEntity
import com.rupio.app.data.local.Muscles
import com.rupio.app.data.local.RoutineEntity
import com.rupio.app.data.local.RoutineExerciseEntity
import com.rupio.app.domain.RoutineGuide
import com.rupio.app.domain.RoutineGuide.Equip
import com.rupio.app.domain.RoutineGuide.Goal
import com.rupio.app.domain.RoutineGuide.Level
import com.rupio.app.domain.RoutineGuide.Split
import com.rupio.app.ui.components.*
import com.rupio.app.ui.learn.ImportantBox
import com.rupio.app.ui.learn.PaperLinks
import com.rupio.app.ui.learn.Papers
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.launch

/**
 * Guía para armar tu rutina: 5 preguntas → propuesta explicada → se crean las rutinas (editables).
 * Es solo un punto de partida; se puede saltar en cualquier paso.
 */
@Composable
fun RoutineGuideScreen(nav: NavHostController) {
    val c = container()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var days by rememberSaveable { mutableIntStateOf(3) }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var equip by rememberSaveable { mutableStateOf(Equip.MACHINES) }
    var level by rememberSaveable { mutableStateOf(Level.NEW) }
    var goal by rememberSaveable { mutableStateOf(Goal.MUSCLE) }
    var split by rememberSaveable { mutableStateOf<Split?>(null) }
    val input = RoutineGuide.Input(days, minutes, equip, level, goal)
    val chosen = split ?: RoutineGuide.recommendedSplit(days, level)
    val plan = remember(input, chosen) { RoutineGuide.build(input, chosen) }
    var catalog by remember { mutableStateOf<Map<String, ExerciseEntity>>(emptyMap()) }
    LaunchedEffect(plan) {
        val ids = plan.sessions.flatMap { s -> s.items.map { it.exerciseId } }.distinct()
        catalog = c.exercises.getMany(ids).associateBy { it.id }
    }
    val last = 6
    fun skip() { nav.navigate(Routes.routine("new")) { popUpTo(Routes.GUIDE) { inclusive = true } } }

    Column(Modifier.fillMaxSize()) {
        RupioTopBar("Arma tu rutina", onBack = { if (step > 0) step-- else nav.safePop() }, subtitle = if (step in 1 until last) "Paso $step de 5" else null) {
            if (step < last) TextButton(onClick = { skip() }) { Text("Saltar") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    Text("Te ayudo a armar una rutina a tu medida", style = MaterialTheme.typography.headlineSmall)
                    Text("Contesta 5 preguntas (días, tiempo, equipo, experiencia y objetivo) y te propongo un plan explicado. " +
                        "Es solo una guía: después puedes cambiar cualquier ejercicio, serie o descanso.",
                        style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim)
                    ImportantBox("Si ya sabes qué quieres hacer, toca \"Saltar\" y crea tu rutina desde cero.", title = "Opcional")
                }
                1 -> Question("¿Cuántos días a la semana puedes entrenar?",
                    "Con 2–3 días ya se logran muy buenos resultados. Elige lo que de verdad puedas sostener: la constancia gana.") {
                    ChoiceRow((2..6).map { "$it" }, (days - 2)) { days = it + 2; split = null }
                    PaperLinks(listOf(Papers.GRGIC_FREQ, Papers.IVERSEN))
                }
                2 -> Question("¿Cuánto tiempo tienes por sesión?",
                    "Una hora alcanza de sobra. Si tienes menos, la guía deja lo más importante y quita accesorios.") {
                    ChoiceRow(listOf("45 min", "60 min", "75 min", "90 min"), listOf(45, 60, 75, 90).indexOf(minutes)) { minutes = listOf(45, 60, 75, 90)[it] }
                    PaperLinks(listOf(Papers.IVERSEN))
                }
                3 -> Question("¿Con qué vas a entrenar?", "Te recomendamos máquinas, sobre todo al empezar, pero tú decides.") {
                    Equip.entries.forEach { e -> OptionCard(e.label, e.sub, equip == e) { equip = e } }
                    ImportantBox("Las máquinas con respaldo y apoyos son más fáciles: el recorrido está guiado, no tienes que equilibrar el peso " +
                        "y puedes acercarte al fallo sin que nadie te cuide. Además haces la técnica siempre igual, así que es más fácil medir tu progreso. " +
                        "En los estudios, máquinas y pesos libres dan resultados parecidos en fuerza y músculo: eres más fuerte en lo que practicas.",
                        title = "¿Por qué máquinas?")
                    PaperLinks(listOf(Papers.HAUGEN))
                }
                4 -> Question("¿Cuánta experiencia tienes?", "Cambia cuántas series te tocan y qué ejercicios usa.") {
                    Level.entries.forEach { l -> OptionCard(l.label, l.sub, level == l) { level = l } }
                }
                5 -> Question("¿Cuál es tu objetivo principal?", "Cambia el rango de repeticiones y los descansos.") {
                    Goal.entries.forEach { g -> OptionCard(g.label, g.sub, goal == g) { goal = g } }
                    Text(when (goal) {
                        Goal.STRENGTH -> "Fuerza: los ejercicios grandes van de 5 a 8 repeticiones con más descanso. Levantar pesado mejora más la fuerza máxima."
                        Goal.MUSCLE -> "Músculo: de 8 a 12 en los grandes y 10–15 en los pequeños. El músculo crece parecido de 6 a 30 reps si llegas cerca del fallo."
                        Goal.HEALTH -> "Salud: de 8 a 15 repeticiones con descansos cortos. Lo importante es la constancia."
                    }, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                    PaperLinks(listOf(Papers.SCHOENFELD_LOAD, Papers.LOPEZ))
                }
                else -> Proposal(plan, chosen, input, catalog) { split = it }
            }
            Spacer(Modifier.height(8.dp))
        }
        // Un doble toque rápido no debe crear las rutinas dos veces.
        var creating by remember { mutableStateOf(false) }
        val create: () -> Unit = create@{
                    if (creating) return@create
                    creating = true
                    scope.launch {
                        val uid = c.auth.currentUserId ?: return@launch
                        val base = c.routines.count()
                        plan.sessions.forEachIndexed { k, s ->
                            val t = System.currentTimeMillis()
                            val r = RoutineEntity(Ids.uuidV7(t), uid, s.name, "Creada con la guía · ${chosen.label}", base + k, t, t)
                            c.routines.save(r, s.items.map { it2 ->
                                RoutineExerciseEntity(Ids.uuidV7(), r.id, uid, it2.exerciseId, 0, null, 0, null, it2.restSec, it2.sets, it2.repMin, it2.repMax, updatedAt = t)
                            })
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                            nav.goToTab(Routes.HOME)
                        }
                    }
        }
        // Con letra muy grande, los botones van uno sobre otro a todo lo ancho (no se cortan sus textos).
        if (step == last && androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = create, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Crear ${plan.sessions.size} rutinas") }
                OutlinedButton(onClick = { skip() }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Desde cero") }
            }
        } else Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step == last) {
                OutlinedButton(onClick = { skip() }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { FitText("Desde cero", MaterialTheme.typography.labelLarge) }
                Button(onClick = create, modifier = Modifier.weight(1.3f).heightIn(min = 50.dp)) { FitText("Crear ${plan.sessions.size} rutinas", MaterialTheme.typography.labelLarge) }
            } else {
                Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (step == 0) "Empezar" else if (step == 5) "Ver mi propuesta" else "Siguiente") }
            }
        }
    }
}

@Composable
private fun Question(title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o -> SegmentedButton(selected == i, { onPick(i) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(o) } }
    }
}

@Composable
private fun OptionCard(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (selected) RupioColors.Primary.copy(alpha = 0.12f) else RupioColors.Surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) RupioColors.Primary else RupioColors.Outline.copy(alpha = 0.6f)),
        // Toda la tarjeta es UNA opción: TalkBack dice "botón de opción, seleccionado" (antes el radio y la tarjeta iban separados).
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, null)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
            }
        }
    }
}

@Composable
private fun Proposal(plan: RoutineGuide.Plan, chosen: Split, input: RoutineGuide.Input, catalog: Map<String, ExerciseEntity>, onSplit: (Split) -> Unit) {
    val rec = RoutineGuide.recommendedSplit(input.days, input.level)
    Text("Tu propuesta", style = MaterialTheme.typography.headlineSmall)
    Text("Para ${input.days} días, ${input.minutes} min por sesión.", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollSafe()) {
        Split.entries.forEach { s ->
            FilterChip(chosen == s, { onSplit(s) }, label = { Text(s.label.substringBefore(" (") + if (s == rec) " ★" else "") })
        }
    }
    SectionCard {
        Text(chosen.label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(chosen.what, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(chosen.why, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        if (chosen == rec) Text("★ Recomendada para tus días", style = MaterialTheme.typography.labelMedium, color = RupioColors.Warning, modifier = Modifier.padding(top = 6.dp))
    }
    SectionCard {
        Text("Tu semana", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        plan.schedule.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Deja al menos un día entre sesiones que repitan los mismos músculos cuando puedas.", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 6.dp))
    }
    plan.sessions.forEach { s ->
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // v1.9: el mismo cálculo y formato que la tarjeta de la rutina en Inicio.
                Text("≈ ${Fmt.durationHuman(s.estimatedSec.toLong())}", style = MaterialTheme.typography.labelLarge, color = RupioColors.TextDim)
            }
            Spacer(Modifier.height(6.dp))
            s.items.forEach { it2 ->
                val e = catalog[it2.exerciseId]
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumb(e?.image0, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e?.name ?: it2.exerciseId, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text("${it2.sets} series × ${it2.repMin}–${it2.repMax} reps · descanso ${Fmt.restShort(it2.restSec)}",
                            style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                    }
                }
            }
        }
    }
    val weekly = RoutineGuide.weeklySets(plan, input.days) { id -> catalog[id]?.let { it.primaryMuscle to it.secondaryMuscles.split(',').filter { m -> m.isNotBlank() } } }
    if (weekly.isNotEmpty()) SectionCard {
        Text("Series por músculo a la semana", style = MaterialTheme.typography.titleMedium)
        Text("Los músculos que ayudan cuentan como media serie. 10 o más se asocian con más crecimiento; desde 4 ya hay buenos resultados.",
            style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
        Spacer(Modifier.height(6.dp))
        weekly.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.forEach { (m, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(Muscles.label(m), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Fmt.num(v, if (v % 1.0 == 0.0) 0 else 1), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        PaperLinks(listOf(Papers.SCHOENFELD, Papers.PELLAND))
    }
    ImportantBox("Es solo una guía. Mantén los mismos ejercicios varias semanas para poder medir tu progreso; " +
        "cambia uno solo si te molesta o no tienes la máquina. Empieza con pesos cómodos y deja 2–3 reps en reserva las primeras semanas.")
}

/** Fila de chips que se puede desplazar si no cabe. */
@Composable
private fun Modifier.horizontalScrollSafe(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
