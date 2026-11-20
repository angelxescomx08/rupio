package com.rupio.app.ui.exercises


import com.rupio.app.core.tr
import com.rupio.app.safePop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rupio.app.Routes
import com.rupio.app.container
import com.rupio.app.core.searchKey
import com.rupio.app.data.local.ExerciseEntity
import com.rupio.app.data.local.Muscles
import com.rupio.app.domain.Tracking
import com.rupio.app.domain.label
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Selector / biblioteca de ejercicios.
 * mode = "workout" (target = id del entrenamiento) · "routine" (devuelve ids al editor) · "browse" (solo ver).
 * Búsqueda sin acentos y por palabras sueltas: "banca manc" encuentra "Press de banca con mancuernas".
 */
@Composable
fun ExercisePickerScreen(nav: NavHostController, mode: String, target: String) {
    val c = container()
    val scope = rememberCoroutineScope()
    val catalog by c.exercises.all.collectAsStateWithLifecycle(initialValue = emptyList())
    // rememberSaveable: la búsqueda y la selección sobreviven al abrir el detalle de un ejercicio y volver.
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) { mutableStateListOf<String>() }
    var creating by remember { mutableStateOf(false) }
    val browse = mode == "browse"
    // v2.1: "Reemplazar ejercicio" (target = "id~ejercicioDeAntes"): se elige uno y listo, empezando por su mismo músculo.
    val replacing = mode == REPLACE_WORKOUT || mode == REPLACE_ROUTINE
    val replaceId = target.substringBefore('~')
    val oldExerciseId = target.substringAfter('~', "")
    val old = remember(catalog, oldExerciseId) { catalog.firstOrNull { it.id == oldExerciseId } }
    var muscleSet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(old) { if (replacing && !muscleSet && old != null) { muscle = old.primaryMuscle; muscleSet = true } }
    fun replaceWith(id: String) {
        if (id == oldExerciseId) { nav.safePop(); return }
        when (mode) {
            REPLACE_WORKOUT -> scope.launch { c.workouts.replaceExercise(replaceId, id); nav.safePop() }
            REPLACE_ROUTINE -> { nav.previousBackStackEntry?.savedStateHandle?.set("replaced", arrayListOf(replaceId, id)); nav.safePop() }
        }
    }
    // v1.9 (R1): al cambiar la búsqueda o un filtro, la lista regresa arriba (por las llaves conservaba el ejercicio que
    // estaba arriba y los mejores resultados quedaban escondidos). La primera emisión se salta: al volver del detalle
    // de un ejercicio la lista sigue donde estaba.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { Triple(query, muscle, equipment) }.drop(1).collect { listState.scrollToItem(0) }
    }

    val filtered = remember(catalog, query, muscle, equipment) {
        // v1.9: con sinónimos ("desplante" → zancada, "gemelos" → pantorrilla, "caminadora", "cardio"…).
        val tokens = com.rupio.app.domain.ExerciseSearch.tokens(query)
        catalog.filter { e ->
            (muscle == null || e.primaryMuscle == muscle || (muscle == BACK_GROUP && e.primaryMuscle in Muscles.BACK)) &&
                (equipment == null || e.equipment == equipment) &&
                com.rupio.app.domain.ExerciseSearch.matches(e.searchKey, Muscles.label(e.primaryMuscle).searchKey(), tokens)
        }.sortedWith(compareBy<ExerciseEntity>({ com.rupio.app.domain.ExerciseSearch.rank(it.searchKey, query) }, { !it.isCustom }, { it.searchKey }))
    }
    val usedMuscles = remember(catalog) { Muscles.names.keys.filter { m -> catalog.any { it.primaryMuscle == m } } }
    // v1.9: tr("Espalda", "Back") junta dorsales, espalda media, lumbar y trapecio; al elegirla aparecen las partes por si buscas una.
    val backOpen = muscle == BACK_GROUP || muscle in Muscles.BACK
    val usedEquip = remember(catalog) { Muscles.equipment.keys.filter { q -> catalog.any { it.equipment == q } } }

    fun confirm() {
        when (mode) {
            "workout" -> scope.launch { c.workouts.addExercises(target, selected.toList()); nav.safePop() }
            "routine" -> { nav.previousBackStackEntry?.savedStateHandle?.set("picked", ArrayList(selected)); nav.safePop() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        RupioTopBar(when { browse -> tr("Ejercicios", "Exercises"); replacing -> tr("Reemplazar ejercicio", "Replace exercise"); else -> tr("Agregar ejercicios", "Add exercises") },
            onBack = { nav.safePop() },
            subtitle = if (replacing && old != null) tr("En lugar de ${old.name}", "Instead of ${old.name}") else tr("${filtered.size} de ${catalog.size}", "${filtered.size} of ${catalog.size}")) {
            IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.AddCircleOutline, tr("Crear ejercicio", "Create exercise")) }
        }
        OutlinedTextField(
            query, { query = it }, placeholder = { Text(tr("Buscar: press banca, sentadilla, bíceps…", "Search: bench press, squat, biceps…")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, tr("Borrar", "Clear")) } },
            singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        // v1.9: cada fila dice qué filtra (tr("Músculo", "Muscle"), tr("Equipo", "Equipment")): antes eran dos filas de chips sin título.
        // En horizontal (o con poca altura) los filtros se van con el scroll: fijos solo dejaban ver un ejercicio (N9).
        val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
        val filters: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { hPad ->
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = hPad, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilterRowTitle(tr("Músculo", "Muscle"))
                FilterChip(muscle == null, { muscle = null }, label = { Text(tr("Todos", "All")) })
                val back = usedMuscles.filter { it in Muscles.BACK }
                // v2.1: el músculo elegido va primero, siempre a la vista (al reemplazar se elige solo y quedaba fuera de
                // la pantalla: la lista salía filtrada sin que se viera por qué).
                val picked = muscle?.takeIf { it != BACK_GROUP && it !in Muscles.BACK }
                if (picked != null) FilterChip(true, { muscle = null }, label = { Text(Muscles.label(picked)) })
                usedMuscles.filter { it !in Muscles.BACK && it != picked }.forEachIndexed { i, m ->
                    FilterChip(muscle == m, { muscle = if (muscle == m) null else m }, label = { Text(Muscles.label(m)) })
                    if (i == 0 && back.isNotEmpty()) {
                        FilterChip(muscle == BACK_GROUP, { muscle = if (muscle == BACK_GROUP) null else BACK_GROUP }, label = { Text(tr("Espalda", "Back")) })
                        if (backOpen) back.forEach { b ->
                            FilterChip(muscle == b, { muscle = if (muscle == b) BACK_GROUP else b }, label = { Text(Muscles.label(b)) },
                                colors = FilterChipDefaults.filterChipColors(containerColor = RupioColors.Surface2))
                        }
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilterRowTitle(tr("Equipo", "Equipment"))
                usedEquip.forEach { q ->
                    FilterChip(equipment == q, { equipment = if (equipment == q) null else q }, label = { Text(Muscles.equipmentLabel(q)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RupioColors.Rest.copy(alpha = 0.25f)))
                }
            }
        }
        if (!short) filters(16.dp)
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (short) item(key = "filtros") { Column { filters(0.dp) } }
            if (filtered.isEmpty()) item {
                EmptyState(Icons.Rounded.SearchOff, tr("Sin resultados", "No results"), tr("Prueba con otra palabra o crea tu propio ejercicio.", "Try another word or create your own exercise.")) {
                    Button(onClick = { creating = true }) { Text(tr("Crear ejercicio", "Create exercise")) }
                }
            }
            items(filtered, key = { it.id }) { e ->
                val order = selected.indexOf(e.id)
                ExerciseRow(e, order, browse, single = replacing, current = replacing && e.id == oldExerciseId,
                    onClick = { when { browse -> nav.navigate(Routes.exercise(e.id)); replacing -> replaceWith(e.id); order >= 0 -> selected.remove(e.id); else -> selected.add(e.id) } },
                    onInfo = { nav.navigate(Routes.exercise(e.id)) })
            }
        }
        if (!browse && !replacing && selected.isNotEmpty()) Surface(color = RupioColors.Surface) {
            Button(
                onClick = { confirm() },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = if (short) 6.dp else 16.dp).height(if (short) 46.dp else 54.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (selected.size == 1) tr("Agregar 1 ejercicio", "Add 1 exercise") else tr("Agregar ${selected.size} ejercicios", "Add ${selected.size} exercises"), style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (creating) CreateExerciseDialog(query, { creating = false }) { name, m, eq, trk ->
        scope.launch {
            val id = c.exercises.createCustom(name, m, eq, trk.code, m in setOf("cuadriceps", "isquiotibiales", "gluteos", "pantorrillas", "aductores", "abductores", "lumbar"))
            creating = false
            if (replacing) replaceWith(id) else if (!browse) selected.add(id)
        }
    }
}

/** Filtro de grupo: todos los músculos de la espalda. */
private const val BACK_GROUP = "grupo:espalda"

/** v2.1: modos del selector para "Reemplazar ejercicio" (en el entreno y en el editor de rutinas). */
const val REPLACE_WORKOUT = "replace"
const val REPLACE_ROUTINE = "replace-routine"

@Composable
private fun FilterRowTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim, modifier = Modifier.padding(end = 2.dp))
}

@Composable
private fun ExerciseRow(e: ExerciseEntity, order: Int, browse: Boolean, onClick: () -> Unit, onInfo: () -> Unit, single: Boolean = false, current: Boolean = false) {
    val sel = order >= 0 || current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (sel) RupioColors.Primary.copy(alpha = 0.14f) else RupioColors.Surface)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumb(e.image0, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${Muscles.label(e.primaryMuscle)} · ${Muscles.equipmentLabel(e.equipment)}", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim, maxLines = 1)
            val trk = Tracking.of(e.tracking)
            if (trk != Tracking.WEIGHT_REPS || e.isCustom) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                if (trk != Tracking.WEIGHT_REPS) Pill(trk.label, RupioColors.Rest)
                if (e.isCustom) Pill(tr("Personalizado", "Custom"), RupioColors.Warning)
            }
        }
        if (!browse) IconButton(onClick = onInfo) { Icon(Icons.Rounded.Info, tr("Ver detalles", "See details"), tint = RupioColors.TextDim) }
        // Al reemplazar se toca uno y listo: sin numeritos de selección; el de ahora dice "Actual".
        if (single) { if (current) Pill(tr("Actual", "Current"), RupioColors.Primary) else Icon(Icons.Rounded.SwapHoriz, null, tint = RupioColors.TextFaint) }
        else if (!browse) Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (sel) RupioColors.Primary else RupioColors.Surface3),
            contentAlignment = Alignment.Center,
        ) { if (sel) Text("${order + 1}", color = RupioColors.OnPrimary, style = MaterialTheme.typography.labelLarge) }
        else Icon(Icons.Rounded.ChevronRight, null, tint = RupioColors.TextFaint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateExerciseDialog(initial: String, onDismiss: () -> Unit, onCreate: (String, String, String, Tracking) -> Unit) {
    var name by remember { mutableStateOf(initial.replaceFirstChar { it.uppercase() }) }
    var muscle by remember { mutableStateOf("pecho") }
    var equip by remember { mutableStateOf("mancuernas") }
    var tracking by remember { mutableStateOf(Tracking.WEIGHT_REPS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Crear ejercicio", "Create exercise")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Nombre", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Dropdown(tr("Músculo principal", "Main muscle"), Muscles.names.entries.map { it.key to it.value }, muscle) { muscle = it }
                Dropdown(tr("Equipo", "Equipment"), Muscles.equipment.entries.map { it.key to it.value }, equip) { equip = it }
                Dropdown(tr("Cómo se registra", "How it's logged"), Tracking.entries.map { it.code to it.label }, tracking.code) { tracking = Tracking.of(it) }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name, muscle, equip, tracking) }, enabled = name.isNotBlank()) { Text(tr("Crear", "Create")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            options.firstOrNull { it.first == value }?.second.orEmpty(), {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { onPick(k); open = false }) }
        }
    }
}
