package com.rupio.app.ui.history


import com.rupio.app.core.tr
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.container
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.Muscles
import com.rupio.app.domain.SetType
import com.rupio.app.domain.CalorieEstimate
import com.rupio.app.domain.Cardio
import com.rupio.app.data.repo.isCardio
import kotlin.math.roundToInt
import com.rupio.app.domain.MuscleSplit
import com.rupio.app.domain.SupersetRestMode
import com.rupio.app.domain.Tracking
import com.rupio.app.ui.components.*
import com.rupio.app.ui.exercises.formatSet
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import com.rupio.app.domain.SupportPolicy
import com.rupio.app.ui.support.SupportPromptCard

/** [celebrate] > 0 → recién terminado; récords = celebrate − 1. */
@Composable
fun WorkoutDetailScreen(nav: NavHostController, id: String, celebrate: Int) {
    val c = container()
    val scope = rememberCoroutineScope()
    val w by c.workouts.observe(id).collectAsStateWithLifecycle(initialValue = null)
    val exercises by c.workouts.observeExercises(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val sets by c.workouts.observeSets(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val catalog by c.exercises.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val zone by c.zone.collectAsStateWithLifecycle()
    val unit = settings?.unit ?: WeightUnit.LB
    val byId = remember(catalog) { catalog.associateBy { it.id } }
    // v1.8: peso corporal más reciente para estimar calorías (si no hay, se usa 70 kg y se dice).
    val bodyKg by c.bodyWeight.recent(1).collectAsStateWithLifecycle(initialValue = emptyList())
    var menu by remember { mutableStateOf(false) }
    var routineName by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val workout = w
    routineName?.let { suggested ->
        RenameDialog(tr("Nombre de la rutina", "Routine name"), suggested, { routineName = null }) { name ->
            routineName = null
            scope.launch {
                if (c.routines.saveFromWorkout(id, name.ifBlank { suggested }) != null)
                    snackbar.showSnackbar(tr("Rutina «${name.ifBlank { suggested }.trim()}» creada. La encuentras en Entrenar.", "Routine “${name.ifBlank { suggested }.trim()}” created. You'll find it in Workout."))
            }
        }
    }

    // Mensaje "Apoya Rupio": solo al terminar (celebrate > 0), en hitos (entreno 3, 10, 25, 50, 100…), máximo una vez
    // cada 30 días y nunca después de 3 "Ahora no" (SupportPolicy). Es una tarjeta dentro del resumen que se puede
    // ignorar, no un diálogo. Se marca antes de mostrarse: girar el teléfono o volver atrás no lo repite.
    val support by c.support.state.collectAsStateWithLifecycle()
    var supportAsk by rememberSaveable { mutableStateOf<Pair<Int, Double>?>(null) }
    // v1.9: la celebración y el mensaje de apoyo son solo de "acabas de terminar". Al volver de tr("Editar", "Edit") ya no salen
    // (antes reaparecían, y el mensaje decía el volumen de antes de corregir).
    var celebrating by rememberSaveable(celebrate) { mutableStateOf(celebrate > 0) }
    LaunchedEffect(id, celebrate) {
        if (!celebrating) return@LaunchedEffect
        val finished = c.db.admin().finishedCount()
        val s = c.support
        if (!SupportPolicy.shouldPrompt(s.state.value.isSupporter, finished, id, s.lastPromptedWorkoutId, s.lastPromptedMilestone, s.lastPromptAt, s.dismissals)) return@LaunchedEffect
        s.markPrompted(id, SupportPolicy.milestone(finished))
        delay(SupportPolicy.DELAY_AFTER_SUMMARY_MS)
        supportAsk = finished to c.db.admin().totalVolumeKg()
    }

    Scaffold(containerColor = RupioColors.Background, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        RupioTopBar(workout?.name ?: tr("Entrenamiento", "Workout"), onBack = { nav.safePop() }) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr("Opciones", "Options")) }
                DropdownMenu(menu, { menu = false }) {
                    // v1.7 (N1): corregir un peso mal escrito, una serie de más o de menos, el tipo de serie.
                    DropdownMenuItem(text = { Text(tr("Editar", "Edit")) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, enabled = workout?.endedAt != null, onClick = {
                        menu = false; celebrating = false; supportAsk = null; nav.navigate(com.rupio.app.Routes.edit(id))
                    })
                    // v1.9 (N7): pide el nombre (propone "Pierna casa (2)" si ya existe) en vez de crear otra con el mismo.
                    DropdownMenuItem(text = { Text(tr("Guardar como rutina", "Save as routine")) }, leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, null) }, onClick = {
                        menu = false
                        scope.launch { routineName = c.routines.suggestedName(workout?.name.orEmpty()) }
                    })
                    DropdownMenuItem(text = { Text(tr("Eliminar entrenamiento", "Delete workout"), color = RupioColors.Danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = RupioColors.Danger) },
                        onClick = {
                            menu = false
                            // Se puede deshacer (el borrado es suave): sin diálogo, se borra y sale "Deshacer" 5 s.
                            // El aviso dice la verdad sobre los récords: los de este entreno se recalculan con los demás.
                            c.scope.launch {
                                val undo = c.workouts.deleteWorkout(id)
                                c.notices.tryEmit(com.rupio.app.Notice(tr("Entrenamiento eliminado; sus récords se recalcularon", "Workout deleted; its PRs were recalculated"), undo))
                            }
                            nav.safePop()
                        })
                }
            }
        }
    }) { pad ->
        if (workout == null) return@Scaffold
        val groups = groupConsecutive(exercises) { it.supersetId }
        val ssIndex = supersetIndexMap(exercises.map { it.supersetId })
        val setsBy = sets.groupBy { it.workoutExerciseId }
        // El cardio anotado (con su MET): para las calorías, la línea de "Incluye N min" y el mensaje final (R2, R3).
        val parts = exercises.filter { byId[it.exerciseId]?.isCardio == true }.flatMap { we ->
            setsBy[we.id].orEmpty().filter { it.completed }.map { CalorieEstimate.CardioPart(Cardio.met(we.exerciseId), it.durationSec ?: 0) }
        }
        val cardioMin = (parts.sumOf { it.sec } / 60.0).roundToInt()
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (celebrating) item {
                // v2.0: quien apoya ve su insignia y un "gracias" (así se nota que es por su apoyo); desde Fan, con confeti.
                val best = support.best
                Box {
                Surface(shape = RoundedCornerShape(20.dp), color = RupioColors.Success.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", style = MaterialTheme.typography.displaySmall)
                        Text(tr("¡Entrenamiento guardado!", "Workout saved!"), style = MaterialTheme.typography.titleLarge, color = RupioColors.Success)
                        val prs = celebrate - 1
                        // v1.9 (R3): un entreno solo de cardio no habla de subir peso.
                        Text(when {
                            prs > 0 -> tr("Rompiste ${Fmt.plural(prs, "récord personal", "récords personales")}. ¡Así se progresa!", "You set ${Fmt.plural(prs, "new PR", "new PRs")}. That's progress!")
                            workout.totalVolumeKg <= 0.0 && cardioMin > 0 -> tr("Anotaste $cardioMin min de cardio. La próxima vez intenta un poco más.", "You logged $cardioMin min of cardio. Next time, try a bit more.")
                            else -> tr("Cada sesión cuenta. La próxima vez Rupio te dirá cuánto subir.", "Every session counts. Next time Rupio will tell you how much to add.")
                        },
                            style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, textAlign = TextAlign.Center)
                        if (best != null) {
                            Spacer(Modifier.height(10.dp))
                            com.rupio.app.ui.support.SupporterBadge(best)
                            Text(tr("Gracias por apoyar Rupio", "Thanks for supporting Rupio"), style = MaterialTheme.typography.labelMedium,
                                color = RupioColors.TextDim, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                if (best?.hasGoldPerks == true) com.rupio.app.ui.support.Confetti(Modifier.matchParentSize(), pieces = 40, colors = com.rupio.app.ui.support.confettiColors(best))
                }
            }
            supportAsk?.let { (n, kg) ->
                if (!support.isSupporter) item(key = "apoyo") {
                    SupportPromptCard(support, n, Fmt.volume(kg, unit),
                        onSeeOptions = { supportAsk = null; nav.navigate(Routes.SUPPORT) },
                        onNotNow = { supportAsk = null; c.support.markDismissed() })
                }
            }
            item {
                SectionCard {
                    Text(Dates.formatDay(workout.localDate), style = MaterialTheme.typography.titleSmall)
                    Text(tr("Empezó a las ", "Started at ") + Dates.formatTimeWithOrigin(workout.startedAt, zone, workout.timeZone) +
                        (workout.endedAt?.let { tr(" · terminó a las ", " · ended at ") + Dates.formatTime(it, zone) } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                    // v1.9 (R2): las horas son las reales; si la duración es mayor es porque cuenta el cardio que anotaste.
                    val clockSec = workout.endedAt?.let { (it - workout.startedAt) / 1000 } ?: 0L
                    if (cardioMin > 0 && (workout.durationSec ?: 0) > clockSec + 30)
                        Text(tr("Incluye $cardioMin min de cardio que anotaste", "Includes $cardioMin min of cardio you logged"), style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
                    Spacer(Modifier.height(12.dp))
                    // v1.9: sin "0 lb" cuando no hubo pesas (cardio o solo peso corporal).
                    TileRow {
                        Tile(tr("Duración", "Duration"), Fmt.durationHuman((workout.durationSec ?: 0).toLong()))
                        if (workout.totalVolumeKg > 0) Tile(tr("Volumen", "Volume"), Fmt.volume(workout.totalVolumeKg, unit), sub = Fmt.volume(workout.totalVolumeKg, unit.other))
                    }
                    Spacer(Modifier.height(8.dp))
                    TileRow {
                        Tile(tr("Series", "Sets"), "${workout.totalSets}")
                        if (workout.totalReps > 0) Tile(tr("Repeticiones", "Reps"), Fmt.count(workout.totalReps))
                        Tile(tr("Récords", "PRs"), "${workout.prCount}")
                    }
                    // v1.8: calorías estimadas (método MET, ver CalorieEstimate). v1.9: el cardio con su MET y sus minutos.
                    CalorieEstimate.estimate((workout.durationSec ?: 0).toLong(), workout.totalSets - parts.size, bodyKg.firstOrNull()?.weightKg, parts)?.let { kcal ->
                        Spacer(Modifier.height(8.dp)); TileRow { CaloriesTile(kcal) }
                    }
                    workout.notes?.let { Spacer(Modifier.height(10.dp)); Text("📝 $it", style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim) }
                }
            }
            // v1.8: reparto de músculos de este entreno.
            val split = MuscleSplit.compute(exercises.mapNotNull { we ->
                val ex = byId[we.exerciseId] ?: return@mapNotNull null
                MuscleSplit.Item(ex.primaryMuscle, ex.secondaryMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    setsBy[we.id].orEmpty().count { it.completed && it.setType != SetType.WARMUP.code })
            })
            if (split.isNotEmpty()) item(key = "musculos") {
                SectionCard {
                    Text(tr("Reparto de músculos", "Muscle split"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    MuscleSplitBars(split)
                }
            }
            items(groups, key = { it.first().id }) { g ->
                val ss = g.first().supersetId
                @Composable
                fun card(k: Int, idx: Int?) {
                    val we = g[k]
                    val ex = byId[we.exerciseId]
                    val trk = Tracking.of(ex?.tracking ?: "weight_reps")
                    val ss2 = setsBy[we.id].orEmpty().filter { it.completed }
                    Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Surface, modifier = Modifier.fillMaxWidth(),
                        onClick = { nav.navigate(Routes.exercise(we.exerciseId)) }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ExerciseThumb(ex?.image0, 40.dp)
                                Spacer(Modifier.width(10.dp))
                                if (idx != null) { SupersetTag(idx, k); Spacer(Modifier.width(6.dp)) }
                                Column(Modifier.weight(1f)) {
                                    Text(ex?.name ?: tr("Ejercicio", "Exercise"), style = MaterialTheme.typography.titleSmall)
                                    Text(Muscles.label(ex?.primaryMuscle ?: ""), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            ss2.forEachIndexed { i, s ->
                                val type = SetType.of(s.setType)
                                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (type == SetType.NORMAL) "${ss2.take(i + 1).count { it.setType != SetType.WARMUP.code }}" else type.short,
                                        modifier = Modifier.width(28.dp), color = if (type == SetType.WARMUP) RupioColors.Warning else RupioColors.TextDim,
                                        style = MaterialTheme.typography.bodyMedium.merge(Tabular))
                                    Text(formatSet(s.weightKg, s.reps, s.durationSec, trk, unit), style = MaterialTheme.typography.bodyMedium.merge(Tabular), modifier = Modifier.weight(1f))
                                    // v1.8: el kg va en su columna fija y el trofeo en la suya: ya no se recorre cuando hay récord.
                                    if (trk.usesWeight) Text(if ((s.weightKg ?: 0.0) != 0.0) Fmt.weight(kotlin.math.abs(s.weightKg ?: 0.0), unit.other) else "",
                                        style = MaterialTheme.typography.labelMedium.merge(Tabular), color = RupioColors.TextFaint, textAlign = TextAlign.End, maxLines = 1)
                                    Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) { if (s.isPr) Text("🏆", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
                if (ss != null && g.size > 1) {
                    val idx = ssIndex[ss] ?: 0
                    SupersetGroup(idx, g.size, SupersetRestMode.of(g.first().supersetRestMode), g.first().supersetRestSec, null, null) { k -> card(k, idx) }
                } else card(0, null)
            }
        }
    }

}
