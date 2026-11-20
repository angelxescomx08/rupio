package com.rupio.app.ui.routine


import com.rupio.app.core.tr
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
        RupioTopBar(tr("Arma tu rutina", "Build your routine"), onBack = { if (step > 0) step-- else nav.safePop() }, subtitle = if (step in 1 until last) tr("Paso $step de 5", "Step $step of 5") else null) {
            if (step < last) TextButton(onClick = { skip() }) { Text(tr("Saltar", "Skip")) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    Text(tr("Te ayudo a armar una rutina a tu medida", "I'll help you build a routine that fits you"), style = MaterialTheme.typography.headlineSmall)
                    Text(tr("Contesta 5 preguntas (días, tiempo, equipo, experiencia y objetivo) y te propongo un plan explicado. " +
                    "Es solo una guía: después puedes cambiar cualquier ejercicio, serie o descanso.",
                    "Answer 5 questions (days, time, equipment, experience and goal) and I'll suggest a plan with the reasons. " +
                        "It's only a guide: you can change any exercise, set or rest afterwards."),
                        style = MaterialTheme.typography.bodyLarge, color = RupioColors.TextDim)
                    ImportantBox(tr("Si ya sabes qué quieres hacer, toca \"Saltar\" y crea tu rutina desde cero.", "If you already know what you want to do, tap “Skip” and create your routine from scratch."), title = tr("Opcional", "Optional"))
                }
                1 -> Question(tr("¿Cuántos días a la semana puedes entrenar?", "How many days a week can you train?"),
                    tr("Con 2–3 días ya se logran muy buenos resultados. Elige lo que de verdad puedas sostener: la constancia gana.", "2–3 days already get very good results. Pick what you can really keep up: consistency wins.")) {
                    ChoiceRow((2..6).map { "$it" }, (days - 2)) { days = it + 2; split = null }
                    PaperLinks(listOf(Papers.GRGIC_FREQ, Papers.IVERSEN))
                }
                2 -> Question(tr("¿Cuánto tiempo tienes por sesión?", "How much time do you have per session?"),
                    tr("Una hora alcanza de sobra. Si tienes menos, la guía deja lo más importante y quita accesorios.", "An hour is plenty. If you have less, the guide keeps the most important lifts and drops accessories.")) {
                    ChoiceRow(listOf("45 min", "60 min", "75 min", "90 min"), listOf(45, 60, 75, 90).indexOf(minutes)) { minutes = listOf(45, 60, 75, 90)[it] }
                    PaperLinks(listOf(Papers.IVERSEN))
                }
                3 -> Question(tr("¿Con qué vas a entrenar?", "What will you train with?"), tr("Te recomendamos máquinas, sobre todo al empezar, pero tú decides.", "We recommend machines, especially when starting out, but it's your call.")) {
                    Equip.entries.forEach { e -> OptionCard(e.label, e.sub, equip == e) { equip = e } }
                    ImportantBox(tr("Las máquinas con respaldo y apoyos son más fáciles: el recorrido está guiado, no tienes que equilibrar el peso " +
                        "y puedes acercarte al fallo sin que nadie te cuide. Además haces la técnica siempre igual, así que es más fácil medir tu progreso. " +
                        "En los estudios, máquinas y pesos libres dan resultados parecidos en fuerza y músculo: eres más fuerte en lo que practicas.",
                        "Machines with a backrest and pads are easier: the movement is guided, you don't have to balance the weight " +
                            "and you can get close to failure without a spotter. Your form also stays the same every time, so progress is easier to measure. " +
                            "In studies, machines and free weights give similar results for strength and muscle: you get stronger at what you practice."),
                        title = tr("¿Por qué máquinas?", "Why machines?"))
                    PaperLinks(listOf(Papers.HAUGEN))
                }
                4 -> Question(tr("¿Cuánta experiencia tienes?", "How much experience do you have?"), tr("Cambia cuántas series te tocan y qué ejercicios usa.", "It changes how many sets you get and which exercises it uses.")) {
                    Level.entries.forEach { l -> OptionCard(l.label, l.sub, level == l) { level = l } }
                }
                5 -> Question(tr("¿Cuál es tu objetivo principal?", "What's your main goal?"), tr("Cambia el rango de repeticiones y los descansos.", "It changes the rep range and rest times.")) {
                    Goal.entries.forEach { g -> OptionCard(g.label, g.sub, goal == g) { goal = g } }
                    Text(when (goal) {
                        Goal.STRENGTH -> tr("Fuerza: los ejercicios grandes van de 5 a 8 repeticiones con más descanso. Levantar pesado mejora más la fuerza máxima.", "Strength: big lifts go from 5 to 8 reps with more rest. Lifting heavy improves max strength the most.")
                        Goal.MUSCLE -> tr("Músculo: de 8 a 12 en los grandes y 10–15 en los pequeños. El músculo crece parecido de 6 a 30 reps si llegas cerca del fallo.", "Muscle: 8 to 12 on big lifts and 10–15 on small ones. Muscle grows similarly from 6 to 30 reps if you get close to failure.")
                        Goal.HEALTH -> tr("Salud: de 8 a 15 repeticiones con descansos cortos. Lo importante es la constancia.", "Health: 8 to 15 reps with short rests. What matters is consistency.")
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
                            val r = RoutineEntity(Ids.uuidV7(t), uid, s.name, tr("Creada con la guía · ", "Created with the guide · ") + chosen.label, base + k, t, t)
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
                Button(onClick = create, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(tr("Crear ${plan.sessions.size} rutinas", "Create ${plan.sessions.size} routines")) }
                OutlinedButton(onClick = { skip() }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(tr("Desde cero", "From scratch")) }
            }
        } else Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step == last) {
                OutlinedButton(onClick = { skip() }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { FitText(tr("Desde cero", "From scratch"), MaterialTheme.typography.labelLarge) }
                Button(onClick = create, modifier = Modifier.weight(1.3f).heightIn(min = 50.dp)) { FitText(tr("Crear ${plan.sessions.size} rutinas", "Create ${plan.sessions.size} routines"), MaterialTheme.typography.labelLarge) }
            } else {
                Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (step == 0) tr("Empezar", "Start") else if (step == 5) tr("Ver mi propuesta", "See my plan") else tr("Siguiente", "Next")) }
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
    Text(tr("Tu propuesta", "Your plan"), style = MaterialTheme.typography.headlineSmall)
    Text(tr("Para ${input.days} días, ${input.minutes} min por sesión.", "For ${input.days} days, ${input.minutes} min per session."), style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
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
        if (chosen == rec) Text(tr("★ Recomendada para tus días", "★ Recommended for your days"), style = MaterialTheme.typography.labelMedium, color = RupioColors.Warning, modifier = Modifier.padding(top = 6.dp))
    }
    SectionCard {
        Text(tr("Tu semana", "Your week"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        plan.schedule.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(tr("Deja al menos un día entre sesiones que repitan los mismos músculos cuando puedas.", "When you can, leave at least one day between sessions that repeat the same muscles."), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim, modifier = Modifier.padding(top = 6.dp))
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
                        Text(tr("${it2.sets} series × ${it2.repMin}–${it2.repMax} reps · descanso ${Fmt.restShort(it2.restSec)}", "${it2.sets} sets × ${it2.repMin}–${it2.repMax} reps · rest ${Fmt.restShort(it2.restSec)}"),
                            style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                    }
                }
            }
        }
    }
    val weekly = RoutineGuide.weeklySets(plan, input.days) { id -> catalog[id]?.let { it.primaryMuscle to it.secondaryMuscles.split(',').filter { m -> m.isNotBlank() } } }
    if (weekly.isNotEmpty()) SectionCard {
        Text(tr("Series por músculo a la semana", "Sets per muscle per week"), style = MaterialTheme.typography.titleMedium)
        Text(tr("Los músculos que ayudan cuentan como media serie. 10 o más se asocian con más crecimiento; desde 4 ya hay buenos resultados.", "Helper muscles count as half a set. 10 or more are linked to more growth; from 4 you already get good results."),
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
    ImportantBox(tr("Es solo una guía. Mantén los mismos ejercicios varias semanas para poder medir tu progreso; " +
                    "cambia uno solo si te molesta o no tienes la máquina. Empieza con pesos cómodos y deja 2–3 reps en reserva las primeras semanas.",
                    "It's only a guide. Keep the same exercises for several weeks so you can measure your progress; " +
                        "change one only if it bothers you or you don't have the machine. Start with comfortable weights and leave 2–3 reps in reserve the first few weeks."))
}

/** Fila de chips que se puede desplazar si no cabe. */
@Composable
private fun Modifier.horizontalScrollSafe(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
