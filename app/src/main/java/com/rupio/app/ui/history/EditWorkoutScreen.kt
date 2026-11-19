package com.rupio.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rupio.app.AppContainer
import com.rupio.app.appViewModel
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.remote.SyncEngine
import com.rupio.app.data.repo.EditedSet
import com.rupio.app.domain.SetType
import com.rupio.app.domain.Tracking
import com.rupio.app.domain.Cardio
import com.rupio.app.safePop
import com.rupio.app.ui.components.ConfirmDialog
import com.rupio.app.ui.components.ExerciseThumb
import com.rupio.app.ui.components.NumberCell
import com.rupio.app.ui.components.RupioTopBar
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.launch

/**
 * v1.7 (N1): corregir un entreno YA terminado (un peso mal escrito, una serie de más o de menos, el tipo de serie).
 * Los cambios se hacen en una copia y solo se guardan al tocar "Guardar": volumen, series, repeticiones y récords se
 * recalculan, el entreno se marca para subir y se sincroniza. También se abre desde "Revisar" → "Corregir" ([focusSet]).
 */
class EditWorkoutVM(private val c: AppContainer, private val workoutId: String) : ViewModel() {
    data class Ex(val weId: String, val exerciseId: String, val name: String, val image: String?, val tracking: Tracking, val cardio: Boolean = false)
    /** Una fila del editor; los números van como texto mientras escribes. [origKg]: peso guardado (para no redondearlo). */
    data class Row(val key: String, val id: String?, val weId: String, val exerciseId: String, val setType: Int,
                   val weight: String, val reps: String, val seconds: String, val rpe: Double?, val origKg: Double?, val origWeight: String)

    var loaded by mutableStateOf(false); private set
    var unit by mutableStateOf(WeightUnit.LB); private set
    var name by mutableStateOf(""); private set
    val exercises = mutableStateListOf<Ex>()
    val rows = mutableStateListOf<Row>()
    private var original: List<Row> = emptyList()
    private var nextKey = 0
    /** Filas que no se pueden guardar así (sin repeticiones, sin peso…); se marcan en rojo. */
    var invalid by mutableStateOf<Set<String>>(emptySet()); private set
    var saving by mutableStateOf(false); private set

    val changed: Boolean get() = rows.toList() != original

    init {
        viewModelScope.launch {
            unit = c.settings.current().unit
            name = c.workouts.get(workoutId)?.name.orEmpty()
            val exs = c.db.workouts().exercises(workoutId)
            val catalog = c.db.exercises().getMany(exs.map { it.exerciseId }.distinct()).associateBy { it.id }
            exercises += exs.map { Ex(it.id, it.exerciseId, catalog[it.exerciseId]?.name ?: "Ejercicio", catalog[it.exerciseId]?.image0, Tracking.of(catalog[it.exerciseId]?.tracking ?: "weight_reps"),
                cardio = catalog[it.exerciseId]?.let { e -> Cardio.isCardio(e.primaryMuscle, e.tracking) } == true) }
            val sets = c.db.workouts().sets(workoutId).filter { it.completed }
            rows += sets.map { s ->
                val ex = exercises.firstOrNull { it.weId == s.workoutExerciseId }
                val tr = ex?.tracking ?: Tracking.WEIGHT_REPS
                val w = shownWeight(s.weightKg, tr)
                // v1.9: el cardio se corrige en minutos, como se anotó.
                val time = if (ex?.cardio == true) Cardio.minutesText(s.durationSec) else s.durationSec?.toString().orEmpty()
                Row("k${nextKey++}", s.id, s.workoutExerciseId, s.exerciseId, s.setType, w, s.reps?.toString().orEmpty(), time, s.rpe, s.weightKg, w)
            }
            original = rows.toList()
            loaded = true
        }
    }

    private fun shownWeight(kg: Double?, tr: Tracking): String =
        kg?.let { Fmt.num(unit.fromKg(if (tr == Tracking.ASSISTED_BODYWEIGHT) -it else it)).replace(",", ".") }.orEmpty()

    fun rowsOf(weId: String) = rows.filter { it.weId == weId }

    fun update(key: String, f: (Row) -> Row) {
        val i = rows.indexOfFirst { it.key == key }
        if (i >= 0) { rows[i] = f(rows[i]); if (key in invalid) invalid = invalid - key }
    }

    fun cycleType(key: String) = update(key) { it.copy(setType = SetType.of(it.setType).next().code) }
    fun remove(key: String) { rows.removeAll { it.key == key }; invalid = invalid - key }

    /** Serie nueva al final del ejercicio, con el peso de la última (como al entrenar). */
    fun add(weId: String) {
        val ex = exercises.firstOrNull { it.weId == weId } ?: return
        val last = rows.lastOrNull { it.weId == weId }
        val r = Row("k${nextKey++}", null, weId, ex.exerciseId, SetType.NORMAL.code, last?.weight.orEmpty(), "", "", null, null, "")
        val at = rows.indexOfLast { it.weId == weId }
        if (at >= 0) rows.add(at + 1, r) else rows += r
    }

    private fun trackingOf(r: Row) = exercises.firstOrNull { it.weId == r.weId }?.tracking ?: Tracking.WEIGHT_REPS
    private fun secondsOf(r: Row): Int? = if (exercises.firstOrNull { it.weId == r.weId }?.cardio == true) Cardio.parseMinutes(r.seconds) else r.seconds.toIntOrNull()

    /** Lo que cada tipo de ejercicio necesita para contar como serie hecha. */
    private fun problem(r: Row): Boolean {
        val tr = trackingOf(r)
        val w = r.weight.toDoubleOrNull(); val reps = r.reps.toIntOrNull(); val sec = secondsOf(r)
        return when (tr) {
            Tracking.DURATION -> sec == null || sec !in 1..86_400
            Tracking.WEIGHT_REPS -> w == null || w < 0 || reps == null || reps !in 1..1000
            else -> reps == null || reps !in 1..1000 || (r.weight.isNotBlank() && (w == null || w < 0))
        }
    }

    /** Guarda; devuelve false si hay filas incompletas (quedan marcadas). */
    suspend fun save(): Boolean {
        val bad = rows.filter(::problem).map { it.key }.toSet()
        if (bad.isNotEmpty()) { invalid = bad; return false }
        saving = true
        val edited = rows.map { r ->
            val tr = trackingOf(r)
            val kg = when {
                !tr.usesWeight -> null
                r.weight == r.origWeight && r.id != null -> r.origKg               // sin tocar: el peso exacto de siempre
                else -> r.weight.toDoubleOrNull()?.let { v -> unit.toKg(v).let { if (tr == Tracking.ASSISTED_BODYWEIGHT) -it else it } }
            }
            EditedSet(r.id, r.weId, r.exerciseId, r.setType, kg,
                if (tr == Tracking.DURATION) null else r.reps.toIntOrNull(),
                if (tr == Tracking.DURATION) secondsOf(r) else null, r.rpe)
        }
        val res = c.workouts.saveEdits(workoutId, edited)
        // Lo que el servidor había rechazado: corregido se vuelve a intentar; quitado deja de intentarse.
        val rejected = c.sync.rejectedStore.keys("workout_sets")
        res.touched.filter { it in rejected }.forEach { c.sync.retryRejected("workout_sets", it) }
        res.removed.filter { it in rejected }.forEach { c.sync.discardRejected("workout_sets", it) }
        if (!c.auth.isLocalOnly) SyncEngine.now(c.app)
        original = rows.toList()
        saving = false
        return true
    }
}

private val W_TYPE = 44.dp; private val W_W = 80.dp; private val W_R = 64.dp

@Composable
fun EditWorkoutScreen(nav: NavHostController, workoutId: String, focusSet: String?) {
    val vm = appViewModel(key = "edit-$workoutId") { EditWorkoutVM(it, workoutId) }
    val scope = rememberCoroutineScope()
    var discard by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    fun back() { if (vm.changed) discard = true else nav.safePop() }
    BackHandler { back() }
    // Desde "Corregir": se baja hasta el ejercicio de la serie rechazada.
    LaunchedEffect(vm.loaded, focusSet) {
        if (!vm.loaded || focusSet == null) return@LaunchedEffect
        val we = vm.rows.firstOrNull { it.id == focusSet }?.weId ?: return@LaunchedEffect
        val idx = vm.exercises.indexOfFirst { it.weId == we }
        if (idx >= 0) list.scrollToItem(idx + 1)
    }

    Scaffold(containerColor = RupioColors.Background, topBar = {
        RupioTopBar("Editar entreno", onBack = { back() }, subtitle = vm.name.ifBlank { null }) {
            TextButton(enabled = vm.loaded && !vm.saving, onClick = {
                scope.launch { if (vm.save()) nav.safePop() }
            }) { Text("Guardar") }
        }
    }) { pad ->
        if (!vm.loaded) return@Scaffold
        LazyColumn(Modifier.padding(pad), state = list, contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Corrige peso, repeticiones, tiempo o tipo de serie; agrega o borra series. Al guardar se recalculan el volumen y tus récords.",
                    style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                if (vm.invalid.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = RupioColors.Danger.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = RupioColors.Danger); Spacer(Modifier.width(8.dp))
                            Text("Completa las series marcadas en rojo o bórralas.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            items(vm.exercises, key = { it.weId }) { ex -> ExerciseEditor(ex, vm, focusSet) }
        }
    }
    if (discard) ConfirmDialog("¿Descartar los cambios?", "El entreno se queda como estaba.", "Descartar", { discard = false }, danger = true) {
        discard = false; nav.safePop()
    }
}

@Composable
private fun ExerciseEditor(ex: EditWorkoutVM.Ex, vm: EditWorkoutVM, focusSet: String?) {
    val unit = vm.unit
    val tr = ex.tracking
    val rows = vm.rowsOf(ex.weId)
    Surface(shape = RoundedCornerShape(16.dp), color = RupioColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(ex.image, 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(ex.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            val st = MaterialTheme.typography.labelSmall
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SERIE", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_TYPE), textAlign = TextAlign.Center)
                if (tr.usesWeight) Text(when (tr) { Tracking.ASSISTED_BODYWEIGHT -> "AYUDA"; Tracking.WEIGHTED_BODYWEIGHT -> "+" + unit.label.uppercase(); else -> unit.label.uppercase() },
                    style = st, color = RupioColors.TextFaint, modifier = Modifier.width(W_W), textAlign = TextAlign.Center)
                Text(if (ex.cardio) "MIN" else if (tr == Tracking.DURATION) "SEGUNDOS" else "REPS", style = st, color = RupioColors.TextFaint,
                    modifier = Modifier.width(if (tr == Tracking.DURATION) W_W else W_R), textAlign = TextAlign.Center)
                Spacer(Modifier.weight(1f))
            }
            if (rows.isEmpty()) Text("Sin series: al guardar se quita este ejercicio del entreno.", style = MaterialTheme.typography.bodySmall,
                color = RupioColors.Warning, modifier = Modifier.padding(vertical = 6.dp))
            rows.forEachIndexed { i, r -> EditSetRow(ex, r, i, rows, vm, highlight = r.id != null && r.id == focusSet) }
            TextButton(onClick = { vm.add(ex.weId) }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Agregar serie")
            }
        }
    }
}

/** Una serie en el editor: tipo (toca para cambiarlo), peso, reps o segundos y borrar. Mismas celdas que al entrenar. */
@Composable
private fun EditSetRow(ex: EditWorkoutVM.Ex, r: EditWorkoutVM.Row, index: Int, all: List<EditWorkoutVM.Row>, vm: EditWorkoutVM, highlight: Boolean) {
    val tr = ex.tracking
    val unit = vm.unit
    val type = SetType.of(r.setType)
    val bad = r.key in vm.invalid
    // v1.9: la misma numeración que se ve ("C, 1, 2"): un calentamiento ya no es "Serie 1" para TalkBack.
    val spoken = com.rupio.app.domain.SetNumbering.spoken(all.map { it.setType }, index)
    val working = com.rupio.app.domain.SetNumbering.working(all.map { it.setType }, index) ?: 0
    val border = when { bad -> RupioColors.Danger; highlight -> RupioColors.Warning; else -> Color.Transparent }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).border(BorderStroke(1.5.dp, border), RoundedCornerShape(10.dp)).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.width(W_TYPE).heightIn(min = 50.dp).clip(RoundedCornerShape(8.dp)).background(RupioColors.Surface2)
                .clickable(onClickLabel = "cambiar el tipo de serie", role = Role.Button) { vm.cycleType(r.key) }
                .clearAndSetSemantics { contentDescription = "${spoken.replaceFirstChar { it.uppercase() }} de ${ex.name}, ${type.label.lowercase()}. Toca para cambiar el tipo"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (type == SetType.NORMAL) "$working" else type.short, style = MaterialTheme.typography.titleSmall.merge(Tabular),
                color = when (type) { SetType.WARMUP -> RupioColors.Warning; SetType.DROP -> RupioColors.Rest; SetType.FAILURE -> RupioColors.Danger; else -> RupioColors.Text })
        }
        if (tr.usesWeight) {
            val what = when (tr) { Tracking.WEIGHTED_BODYWEIGHT -> "Lastre"; Tracking.ASSISTED_BODYWEIGHT -> "Ayuda"; else -> "Peso" }
            // v1.8: la otra unidad dentro de la misma cajita (como al entrenar).
            val other = r.weight.toDoubleOrNull()?.takeIf { it != 0.0 }?.let { com.rupio.app.core.Fmt.weight(unit.toKg(kotlin.math.abs(it)), unit.other) }
            NumberCell(r.weight, unit.label, { t -> vm.update(r.key) { it.copy(weight = t.replace("-", "")) } },
                Modifier.width(W_W), label = "$what en ${if (unit == WeightUnit.LB) "libras" else "kilos"}, $spoken, ${ex.name}", sub = other, minHeight = 50.dp)
        }
        if (ex.cardio) {
            NumberCell(r.seconds, "min", { t -> vm.update(r.key) { it.copy(seconds = t.replace("-", "")) } }, Modifier.width(W_W),
                decimal = true, label = "Minutos, $spoken, ${ex.name}", minHeight = 50.dp)
        } else if (tr == Tracking.DURATION) {
            NumberCell(r.seconds, "seg", { t -> vm.update(r.key) { it.copy(seconds = t.filter(Char::isDigit)) } }, Modifier.width(W_W),
                decimal = false, label = "Segundos, $spoken, ${ex.name}", minHeight = 50.dp)
        } else {
            NumberCell(r.reps, "reps", { t -> vm.update(r.key) { it.copy(reps = t.filter(Char::isDigit)) } }, Modifier.width(W_R),
                decimal = false, label = "Repeticiones, $spoken, ${ex.name}", minHeight = 50.dp)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.remove(r.key) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.DeleteOutline, "Borrar $spoken de ${ex.name}", tint = RupioColors.TextDim)
        }
    }
}
