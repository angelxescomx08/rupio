package com.rupio.app.ui.routine


import com.rupio.app.core.tr
import com.rupio.app.safePop
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.rupio.app.AppContainer
import com.rupio.app.Routes
import com.rupio.app.appViewModel
import com.rupio.app.container
import com.rupio.app.core.Fmt
import com.rupio.app.core.Ids
import com.rupio.app.data.local.ExerciseEntity
import com.rupio.app.data.local.Muscles
import com.rupio.app.data.local.RoutineEntity
import com.rupio.app.data.local.RoutineExerciseEntity
import com.rupio.app.data.repo.isCardio
import com.rupio.app.domain.*
import com.rupio.app.ui.components.*
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditItem(
    val key: String, val exerciseId: String, val sets: Int, val repMin: Int, val repMax: Int, val restSec: Int,
    val supersetId: String? = null, val ssMode: Int = 0, val ssRest: Int? = null, val notes: String? = null,
    /** Una entrada por serie: tipo y (opcional) peso/reps objetivo. sets == plan.size. */
    val plan: List<PlanSet> = List(sets) { PlanSet() },
) {
    fun planItem() = PlanItem(key, sets, restSec, supersetId, SupersetRestMode.of(ssMode), ssRest)
}

class RoutineEditorVM(private val c: AppContainer, private val routineId: String) : ViewModel() {
    val isNew = routineId == "new"
    var name by mutableStateOf("")
    var notes by mutableStateOf("")
    val items = mutableStateListOf<EditItem>()
    var loaded by mutableStateOf(false); private set
    var dirty by mutableStateOf(false); private set
    private var existing: RoutineEntity? = null

    init {
        viewModelScope.launch {
            if (!isNew) {
                existing = c.routines.get(routineId)
                name = existing?.name.orEmpty(); notes = existing?.notes.orEmpty()
                items.addAll(c.routines.exercises(routineId).map {
                    val plan = SetPlans.decode(it.setPlan).ifEmpty { List(it.targetSets) { PlanSet() } }
                    EditItem(it.id, it.exerciseId, plan.size, it.repMin, it.repMax, it.restSec, it.supersetId, it.supersetRestMode, it.supersetRestSec, it.notes, plan)
                })
            }
            loaded = true
        }
    }

    fun touch() { dirty = true }

    fun add(ids: List<String>) = viewModelScope.launch {
        val catalog = c.exercises.getMany(ids).associateBy { it.id }
        val rest = c.settings.current().defaultRestSec
        ids.forEach { id ->
            val (mn, mx) = when (Tracking.of(catalog[id]?.tracking ?: "")) { Tracking.REPS_ONLY -> 8 to 15; Tracking.DURATION -> 1 to 1; else -> 8 to 12 }
            items += EditItem(Ids.uuidV7(), id, 3, mn, mx, rest)
        }
        dirty = true
    }

    /**
     * v2.1 · "Reemplazar": otro ejercicio en el mismo lugar, con sus series (y tipos), descanso y superserie. Los pesos y
     * reps objetivo eran del ejercicio de antes: se quitan, igual que sus notas. Si cambia cómo se registra (p. ej. a
     * uno por tiempo), el rango de reps vuelve al de ese tipo.
     */
    fun replace(key: String, newId: String) = viewModelScope.launch {
        val item = items.firstOrNull { it.key == key } ?: return@launch
        if (item.exerciseId == newId) return@launch
        val catalog = c.exercises.getMany(listOf(item.exerciseId, newId)).associateBy { it.id }
        val oldTrk = Tracking.of(catalog[item.exerciseId]?.tracking ?: ""); val trk = Tracking.of(catalog[newId]?.tracking ?: "")
        val (mn, mx) = if (oldTrk == trk) item.repMin to item.repMax else when (trk) { Tracking.REPS_ONLY -> 8 to 15; Tracking.DURATION -> 1 to 1; else -> 8 to 12 }
        update(key) { it.copy(exerciseId = newId, repMin = mn, repMax = mx, notes = null, plan = it.plan.map { p -> PlanSet(t = p.t) }) }
    }

    fun updatePlan(key: String, f: (List<PlanSet>) -> List<PlanSet>) = update(key) { e ->
        val p = f(e.plan).ifEmpty { listOf(PlanSet()) }
        e.copy(plan = p, sets = p.size)
    }

    fun update(key: String, f: (EditItem) -> EditItem) {
        val i = items.indexOfFirst { it.key == key }; if (i >= 0) { items[i] = f(items[i]); dirty = true }
    }

    fun remove(key: String) {
        val ss = items.firstOrNull { it.key == key }?.supersetId
        items.removeAll { it.key == key }
        ss?.let { dissolveIfSingle(it) }
        dirty = true
    }

    private fun dissolveIfSingle(ss: String) {
        val left = items.withIndex().filter { it.value.supersetId == ss }
        if (left.size == 1) items[left[0].index] = left[0].value.copy(supersetId = null)
    }

    /** Mueve un ejercicio; si sale de su bloque, mueve el bloque entero (las superseries nunca se rompen). */
    fun move(key: String, delta: Int) {
        ReorderOps.moveStep(items.toList(), key, delta, { it.key }, { it.supersetId })?.let { setOrder(it) }
    }

    fun canMove(key: String, delta: Int) = ReorderOps.canMove(items.toList(), key, delta, { it.key }, { it.supersetId })

    /** v1.9: el orden que dejaste en tr("Ordenar ejercicios", "Reorder exercises") (si partiera una superserie, no se aplica). */
    fun reorder(keys: List<String>) {
        ReorderOps.applyOrder(items.toList(), keys, { it.key }, { it.supersetId })?.let { setOrder(it) }
    }

    private fun setOrder(list: List<EditItem>) {
        if (list.map { it.key } == items.map { it.key }) return
        items.clear(); items.addAll(list); dirty = true
    }

    fun createSuperset(keys: List<String>, mode: SupersetRestMode, rest: Int?) {
        if (keys.size < 2) return
        val ss = Ids.uuidV7()
        val old = items.filter { it.key in keys }.mapNotNull { it.supersetId }.toSet()
        val updated = items.map { if (it.key in keys) it.copy(supersetId = ss, ssMode = mode.code, ssRest = rest) else it }
        items.clear(); items.addAll(com.rupio.app.data.repo.SupersetOps.contiguous(updated, keys.toSet()) { it.key })
        old.forEach { dissolveIfSingle(it) }
        dirty = true
    }

    fun groupKeysWithNext(key: String): List<String>? {
        val i = items.indexOfFirst { it.key == key }
        if (i < 0) return null
        val ss = items[i].supersetId
        val members = if (ss != null) items.filter { it.supersetId == ss }.map { it.key } else listOf(key)
        val lastIdx = items.indexOfLast { it.key in members }
        val next = items.getOrNull(lastIdx + 1) ?: return null
        val nextMembers = next.supersetId?.let { s -> items.filter { it.supersetId == s }.map { it.key } } ?: listOf(next.key)
        return members + nextMembers
    }

    fun updateSuperset(ss: String, mode: SupersetRestMode, rest: Int?) {
        items.indices.forEach { i -> if (items[i].supersetId == ss) items[i] = items[i].copy(ssMode = mode.code, ssRest = rest) }
        dirty = true
    }

    /** Saca un ejercicio de su superserie y lo coloca justo después del grupo (el grupo sigue contiguo). */
    fun removeFromSuperset(key: String) {
        val item = items.firstOrNull { it.key == key } ?: return
        val ss = item.supersetId ?: return
        items.remove(item)
        val lastIdx = items.indexOfLast { it.supersetId == ss }
        items.add(if (lastIdx < 0) items.size else lastIdx + 1, item.copy(supersetId = null))
        dissolveIfSingle(ss)
        dirty = true
    }

    fun ungroup(ss: String) {
        items.indices.forEach { i -> if (items[i].supersetId == ss) items[i] = items[i].copy(supersetId = null) }
        dirty = true
    }

    /** v1.9: una rutina sin ejercicios no se guarda (antes quedaba "Mi rutina · 0 ejercicios" y abría un entreno vacío). */
    val canSave get() = items.isNotEmpty()

    suspend fun save(): Boolean {
        if (!canSave) return false
        val uid = c.auth.currentUserId ?: return false
        val t = System.currentTimeMillis()
        val r = existing?.copy(name = name.trim().ifBlank { tr("Mi rutina", "My routine") }, notes = notes.ifBlank { null })
            ?: RoutineEntity(Ids.uuidV7(t), uid, name.trim().ifBlank { tr("Mi rutina", "My routine") }, notes.ifBlank { null }, c.routines.count(), t, t)
        c.routines.save(r, items.map {
            // Si no escribiste ningún objetivo ni tipo, no se guarda plan (solo el número de series).
            val meaningful = it.plan.any { p -> p.w != null || p.r != null || p.d != null || p.t != 0 }
            RoutineExerciseEntity(it.key, r.id, uid, it.exerciseId, 0, it.supersetId, it.ssMode, it.ssRest, it.restSec, it.plan.size.coerceAtLeast(1),
                it.repMin, it.repMax, it.notes, t, setPlan = if (meaningful) SetPlans.encode(it.plan) else null)
        })
        dirty = false
        return true
    }
}

@Composable
fun RoutineEditorScreen(nav: NavHostController, routineId: String) {
    val vm = appViewModel { RoutineEditorVM(it, routineId) }
    val c = container()
    val scope = rememberCoroutineScope()
    val catalog by c.exercises.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val byId = remember(catalog) { catalog.associateBy { it.id } }
    val entry = LocalViewModelStoreOwner.current as? NavBackStackEntry

    // Resultado del selector de ejercicios.
    val picked = entry?.savedStateHandle?.getStateFlow<ArrayList<String>?>("picked", null)?.collectAsStateWithLifecycle()
    LaunchedEffect(picked?.value) {
        picked?.value?.let { vm.add(it); entry.savedStateHandle["picked"] = null }
    }
    // v2.1: "Reemplazar" devuelve [clave del ejercicio, ejercicio nuevo].
    val replaced = entry?.savedStateHandle?.getStateFlow<ArrayList<String>?>("replaced", null)?.collectAsStateWithLifecycle()
    LaunchedEffect(replaced?.value) {
        replaced?.value?.takeIf { it.size == 2 }?.let { vm.replace(it[0], it[1]) }
        if (replaced?.value != null) entry.savedStateHandle["replaced"] = null
    }

    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var ssDialog by remember { mutableStateOf<List<String>?>(null) }
    var ssEdit by remember { mutableStateOf<String?>(null) }
    var restFor by remember { mutableStateOf<String?>(null) }
    var repsFor by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    // v1.9: pantalla tr("Ordenar ejercicios", "Reorder exercises") (null = cerrada; "" = abierta sin resaltar ninguno).
    var reorderFocus by remember { mutableStateOf<String?>(null) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun openReorder(key: String?) {
        if (vm.items.size < 2) return
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        reorderFocus = key ?: ""
    }

    fun exit() { if (vm.dirty) confirmExit = true else nav.safePop() }
    BackHandler { exit() }

    val summary = RestPlanner.summarize(vm.items.map { it.planItem() })
    val ssIndex = supersetIndexMap(vm.items.map { it.supersetId })
    val groups = groupConsecutive(vm.items.toList()) { it.supersetId }

    var needExercise by remember { mutableStateOf(false) }
    LaunchedEffect(vm.items.size) { if (vm.items.isNotEmpty()) needExercise = false }
    Column(Modifier.fillMaxSize()) {
        RupioTopBar(if (vm.isNew) tr("Nueva rutina", "New routine") else tr("Editar rutina", "Edit routine"), onBack = { exit() }) {
            Button(onClick = { if (!vm.canSave) needExercise = true else scope.launch { if (vm.save()) nav.safePop() } },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(end = 8.dp)) { Text(tr("Guardar", "Save")) }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(vm.name, { vm.name = it; vm.touch() }, label = { Text(tr("Nombre de la rutina", "Routine name")) }, placeholder = { Text(tr("Ej. Pierna, Empuje, Cuerpo completo", "E.g. Legs, Push, Full body")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
            item {
                OutlinedTextField(vm.notes, { vm.notes = it; vm.touch() }, label = { Text(tr("Notas (opcional)", "Notes (optional)")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), maxLines = 3)
            }
            item {
                SectionCard(padding = 14.dp) {
                    TileRow {
                        Tile(tr("Ejercicios", "Exercises"), "${vm.items.size}")
                        Tile(tr("Series", "Sets"), "${summary.totalSets}")
                        Tile(tr("Duración", "Duration"), "≈ " + Fmt.durationHuman(summary.estimatedSec.toLong()))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = RupioColors.Rest, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Descanso total: ", "Total rest: ") + Fmt.durationHuman(summary.totalRestSec.toLong()), color = RupioColors.Rest, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(tr("Suma de todos los descansos entre series (las superseries con descanso compartido cuentan uno por ronda).", "Sum of all rests between sets (supersets with shared rest count one per round)."),
                        style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
                }
            }
            if (vm.items.size >= 2) item {
                // v1.9: tr("Ordenar", "Reorder") junto a tr("Crear superserie", "Create superset"); con letra grande los botones bajan de renglón.
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (selecting) tr("Toca los ejercicios que quieres unir", "Tap the exercises you want to combine") else tr("Ejercicios", "Exercises"), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically).padding(end = 4.dp))
                    if (!selecting) FilterChip(
                        selected = false, onClick = { openReorder(null) },
                        label = { Text(tr("Ordenar", "Reorder")) },
                        leadingIcon = { Icon(Icons.Rounded.SwapVert, null, modifier = Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = selecting, onClick = { selecting = !selecting; selected.clear() },
                        label = { Text(if (selecting) tr("Cancelar", "Cancel") else tr("Crear superserie", "Create superset")) },
                        leadingIcon = { Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            if (vm.loaded && vm.items.isEmpty()) item {
                EmptyState(Icons.Rounded.FitnessCenter, tr("Agrega ejercicios", "Add exercises"), tr("Busca por nombre o músculo. Los del catálogo traen imágenes y un botón para buscar videos de cómo hacerlos.", "Search by name or muscle. Catalog exercises include images and a button to find videos on how to do them."))
                if (needExercise) Text(tr("Agrega al menos un ejercicio para guardar la rutina.", "Add at least one exercise to save the routine."), color = RupioColors.Danger, style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            }
            items(groups, key = { g -> g.first().key }) { g ->
                val ss = g.first().supersetId
                if (ss != null && g.size > 1) {
                    val idx = ssIndex[ss] ?: 0
                    SupersetGroup(
                        idx, g.size, SupersetRestMode.of(g.first().ssMode), g.first().ssRest,
                        onEditRest = { ssEdit = ss }, onUngroup = { vm.ungroup(ss) },
                    ) { k ->
                        val it = g[k]
                        ItemCard(it, byId[it.exerciseId], idx, k, true, selecting, it.key in selected,
                            onSelect = { if (it.key in selected) selected.remove(it.key) else selected.add(it.key) },
                            vm = vm, onRest = { restFor = it.key }, onReps = { repsFor = it.key },
                            onSupersetNext = { vm.groupKeysWithNext(it.key)?.let { ks -> ssDialog = ks } },
                            onOpen = { nav.navigate(Routes.exercise(it.exerciseId)) }, onReorder = { openReorder(it.key) },
                            onReplace = { nav.navigate(Routes.picker(com.rupio.app.ui.exercises.REPLACE_ROUTINE, it.key + "~" + it.exerciseId)) })
                    }
                } else {
                    val it = g.first()
                    ItemCard(it, byId[it.exerciseId], null, null, false, selecting, it.key in selected,
                        onSelect = { if (it.key in selected) selected.remove(it.key) else selected.add(it.key) },
                        vm = vm, onRest = { restFor = it.key }, onReps = { repsFor = it.key },
                        onSupersetNext = { vm.groupKeysWithNext(it.key)?.let { ks -> ssDialog = ks } },
                        onOpen = { nav.navigate(Routes.exercise(it.exerciseId)) }, onReorder = { openReorder(it.key) },
                            onReplace = { nav.navigate(Routes.picker(com.rupio.app.ui.exercises.REPLACE_ROUTINE, it.key + "~" + it.exerciseId)) })
                }
            }
            item {
                OutlinedButton(
                    onClick = { nav.navigate(Routes.picker("routine", routineId)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
                ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("Agregar ejercicios", "Add exercises")) }
            }
        }
        if (selecting) Surface(color = RupioColors.Surface, tonalElevation = 4.dp) {
            Button(
                onClick = { ssDialog = selected.toList() }, enabled = selected.size >= 2,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp), shape = RoundedCornerShape(14.dp),
            ) { Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(6.dp)); Text(if (selected.size < 2) tr("Elige al menos 2", "Pick at least 2") else tr("Unir ${selected.size} en superserie", "Superset ${selected.size} exercises")) }
        }
    }

    ssDialog?.let { keys ->
        val first = vm.items.firstOrNull { it.key in keys }
        val muscles = keys.mapNotNull { k -> vm.items.firstOrNull { it.key == k }?.let { byId[it.exerciseId]?.primaryMuscle } }
        // v1.9: el descanso que ya tenía el ejercicio (antes volvía a 90 s sin avisar).
        SupersetRestDialog(
            tr("Nueva superserie", "New superset"), if (first?.supersetId != null) SupersetRestMode.of(first.ssMode) else SupersetRestMode.SHARED,
            first?.ssRest ?: first?.restSec ?: 90, sameMusclePair(muscles), { ssDialog = null },
        ) { mode, rest -> vm.createSuperset(keys, mode, rest); ssDialog = null; selecting = false; selected.clear() }
    }
    ssEdit?.let { ss ->
        val first = vm.items.firstOrNull { it.supersetId == ss }
        val muscles = vm.items.filter { it.supersetId == ss }.mapNotNull { byId[it.exerciseId]?.primaryMuscle }
        SupersetRestDialog(tr("Descanso de la superserie", "Superset rest"), SupersetRestMode.of(first?.ssMode ?: 1), first?.ssRest ?: 90, sameMusclePair(muscles), { ssEdit = null }) { mode, rest ->
            vm.updateSuperset(ss, mode, rest); ssEdit = null
        }
    }
    restFor?.let { key ->
        val it = vm.items.firstOrNull { e -> e.key == key }
        if (it != null) RestPickerDialog(tr("Descanso después de cada serie", "Rest after each set"), it.restSec, { restFor = null }, { s -> vm.update(key) { e -> e.copy(restSec = s) }; restFor = null }) {
            Text(tr("Recomendado: 1–2 min en aislamiento, 2–3 min en ejercicios pesados (más de 60–90 s ayuda a mantener el rendimiento).", "Recommended: 1–2 min for isolation, 2–3 min for heavy lifts (more than 60–90 s helps keep performance up)."),
                style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint, modifier = Modifier.padding(top = 10.dp))
        }
    }
    repsFor?.let { key ->
        val it = vm.items.firstOrNull { e -> e.key == key }
        if (it != null) RepRangeDialog(it.repMin, it.repMax, { repsFor = null }) { lo, hi -> vm.update(key) { e -> e.copy(repMin = lo, repMax = hi) }; repsFor = null }
    }
    reorderFocus?.let { focus ->
        ReorderDialog(
            tr("Ordenar ejercicios", "Reorder exercises"), tr("Arrastra con ≡ o mantén presionado un ejercicio. Las superseries se mueven juntas.", "Drag with ≡ or press and hold an exercise. Supersets move together."),
            vm.items.map { ReorderEntry(it.key, byId[it.exerciseId]?.name ?: tr("Ejercicio", "Exercise"), reorderSubtitle(it, byId[it.exerciseId]), byId[it.exerciseId]?.image0, it.supersetId) },
            onDismiss = { reorderFocus = null }, focusKey = focus.ifEmpty { null },
        ) { keys -> vm.reorder(keys); reorderFocus = null }
    }
    if (confirmExit) ConfirmDialog(tr("¿Salir sin guardar?", "Leave without saving?"), tr("Perderás los cambios de esta rutina.", "You'll lose the changes to this routine."), tr("Salir", "Leave"), { confirmExit = false }, danger = true) {
        confirmExit = false; nav.safePop()
    }
}

@Composable
private fun ItemCard(
    item: EditItem, ex: ExerciseEntity?, ssIdx: Int?, member: Int?, inSuperset: Boolean, selecting: Boolean, isSelected: Boolean,
    onSelect: () -> Unit, vm: RoutineEditorVM, onRest: () -> Unit, onReps: () -> Unit, onSupersetNext: () -> Unit, onOpen: () -> Unit,
    onReorder: () -> Unit, onReplace: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    val tracking = Tracking.of(ex?.tracking ?: "weight_reps")
    val shared = inSuperset && item.ssMode == SupersetRestMode.SHARED.code
    Surface(
        Modifier.fillMaxWidth().then(if (selecting) Modifier.toggleable(value = isSelected, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onSelect() }) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) RupioColors.Primary.copy(alpha = 0.16f) else RupioColors.Surface,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) { Checkbox(isSelected, null); Spacer(Modifier.width(4.dp)) }
                Box(Modifier.clickable(onClickLabel = tr("ver técnica", "see form"), onClick = onOpen)
                    .clearAndSetSemantics { contentDescription = tr("Ver técnica de ${ex?.name ?: "el ejercicio"}", "See form for ${ex?.name ?: "the exercise"}"); role = androidx.compose.ui.semantics.Role.Button }) { ExerciseThumb(ex?.image0, 44.dp) }
                Spacer(Modifier.width(10.dp))
                // v1.9: mantener presionado el nombre abre tr("Ordenar ejercicios", "Reorder exercises") con este resaltado.
                Column(Modifier.weight(1f).then(if (selecting) Modifier else Modifier.combinedClickable(
                    onClickLabel = tr("ver técnica", "see form"), onLongClickLabel = tr("ordenar ejercicios", "reorder exercises"), onLongClick = onReorder, onClick = onOpen))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ssIdx != null && member != null) { SupersetTag(ssIdx, member); Spacer(Modifier.width(6.dp)) }
                        Text(ex?.name ?: tr("Ejercicio", "Exercise"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(Muscles.label(ex?.primaryMuscle ?: "") + " · " + tracking.label, style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim, maxLines = 1)
                }
                if (!selecting) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr("Opciones", "Options")) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Unir en superserie con el siguiente", "Superset with the next one")) }, leadingIcon = { Icon(Icons.Rounded.Link, null) }, onClick = { menu = false; onSupersetNext() })
                        if (inSuperset) DropdownMenuItem(text = { Text(tr("Sacar de la superserie", "Remove from superset")) }, leadingIcon = { Icon(Icons.Rounded.LinkOff, null) }, onClick = {
                            menu = false; vm.removeFromSuperset(item.key)
                        })
                        DropdownMenuItem(text = { Text(tr("Reemplazar ejercicio", "Replace exercise")) }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) }, onClick = { menu = false; onReplace() })
                        if (vm.items.size >= 2) {
                            DropdownMenuItem(text = { Text(tr("Ordenar ejercicios", "Reorder exercises")) }, leadingIcon = { Icon(Icons.Rounded.SwapVert, null) }, onClick = { menu = false; onReorder() })
                            DropdownMenuItem(text = { Text(tr("Subir", "Move up")) }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) }, enabled = vm.canMove(item.key, -1), onClick = { menu = false; vm.move(item.key, -1) })
                            DropdownMenuItem(text = { Text(tr("Bajar", "Move down")) }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) }, enabled = vm.canMove(item.key, 1), onClick = { menu = false; vm.move(item.key, 1) })
                        }
                        DropdownMenuItem(text = { Text(tr("Quitar", "Remove"), color = RupioColors.Danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = RupioColors.Danger) }, onClick = { menu = false; vm.remove(item.key) })
                    }
                }
            }
            if (!selecting) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tracking != Tracking.DURATION) Chip(Icons.Rounded.Repeat, "${item.repMin}–${item.repMax} reps", RupioColors.Primary, onReps)
                    // En superserie con descanso compartido, el descanso vive en la cabecera del grupo.
                    if (!shared) Chip(Icons.Rounded.Timer, Fmt.restShort(item.restSec), RupioColors.Rest, onRest)
                }
                Spacer(Modifier.height(8.dp))
                PlanRows(item, tracking, vm)
            }
        }
    }
}

private val P_TYPE = 40.dp; private val P_VAL = 76.dp; private val P_DEL = 40.dp

/**
 * Series de la rutina, una por fila (como en el entrenamiento): tipo, peso y reps objetivo (opcionales).
 * Esos números son los que verás en gris la primera vez; después manda tu historial.
 */
@Composable
private fun PlanRows(item: EditItem, tracking: Tracking, vm: RoutineEditorVM) {
    val unit = container().settings.settings.collectAsStateWithLifecycle(initialValue = null).value?.unit ?: com.rupio.app.core.WeightUnit.LB
    val assisted = tracking == Tracking.ASSISTED_BODYWEIGHT
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val st = MaterialTheme.typography.labelSmall
        Text(tr("SERIE", "SET"), style = st, color = RupioColors.TextFaint, modifier = Modifier.width(P_TYPE), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (tracking.usesWeight) Text(when (tracking) { Tracking.WEIGHTED_BODYWEIGHT -> "+" + unit.label.uppercase(); Tracking.ASSISTED_BODYWEIGHT -> tr("AYUDA", "ASSIST"); else -> unit.label.uppercase() },
            style = st, color = RupioColors.TextFaint, modifier = Modifier.width(P_VAL), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(if (tracking == Tracking.DURATION) tr("SEGUNDOS", "SECONDS") else "REPS", style = st, color = RupioColors.TextFaint, modifier = Modifier.width(P_VAL), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.weight(1f))
    }
    var normalIndex = 0
    item.plan.forEachIndexed { i, p ->
        val type = p.type
        val label = if (type == SetType.NORMAL) "${++normalIndex}" else type.short
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Toca para cambiar el tipo: normal → calentamiento → drop set → al fallo.
            Box(Modifier.width(P_TYPE).height(50.dp).clip(RoundedCornerShape(10.dp))
                .background(if (type == SetType.NORMAL) RupioColors.Surface2 else RupioColors.Warning.copy(alpha = 0.18f))
                .clickable { vm.updatePlan(item.key) { l -> l.mapIndexed { k, x -> if (k == i) x.copy(t = type.next().code) else x } } },
                contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = if (type == SetType.NORMAL) RupioColors.Text else RupioColors.Warning)
            }
            if (tracking.usesWeight) {
                val shown = p.w?.let { Fmt.num(unit.fromKg(if (assisted) -it else it), 1).replace(",", ".") } ?: ""
                // v1.8: la otra unidad dentro de la misma cajita ("135" y "61.2 kg"), sin descuadrar la fila.
                NumberCell(shown, "—", { v ->
                    val num = v.toDoubleOrNull()
                    vm.updatePlan(item.key) { l -> l.mapIndexed { k, x -> if (k == i) x.copy(w = num?.let { n -> unit.toKg(kotlin.math.abs(n)).coerceAtMost(Fmt.MAX_KG).let { kg -> if (assisted) -kg else kg } }) else x } }
                }, Modifier.width(P_VAL), sub = p.w?.takeIf { it != 0.0 }?.let { Fmt.weight(kotlin.math.abs(it), unit.other) }, minHeight = 50.dp)
            }
            if (tracking == Tracking.DURATION) {
                NumberCell(p.d?.toString() ?: "", "—", { v -> vm.updatePlan(item.key) { l -> l.mapIndexed { k, x -> if (k == i) x.copy(d = v.toIntOrNull()?.coerceIn(0, 86_400)) else x } } },
                    Modifier.width(P_VAL), decimal = false, minHeight = 50.dp)
            } else {
                NumberCell(p.r?.toString() ?: "", "${item.repMin}–${item.repMax}", { v -> vm.updatePlan(item.key) { l -> l.mapIndexed { k, x -> if (k == i) x.copy(r = v.toIntOrNull()?.coerceIn(0, 1000)) else x } } },
                    Modifier.width(P_VAL), decimal = false, minHeight = 50.dp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.updatePlan(item.key) { l -> l.filterIndexed { k, _ -> k != i } } }, enabled = item.plan.size > 1, modifier = Modifier.size(P_DEL)) {
                Icon(Icons.Rounded.Close, tr("Quitar serie", "Remove set"), tint = RupioColors.TextFaint, modifier = Modifier.size(18.dp))
            }
        }
    }
    TextButton(onClick = { vm.updatePlan(item.key) { l -> l + (l.lastOrNull()?.copy(t = SetType.NORMAL.code) ?: PlanSet()) } }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Agregar serie", "Add set"))
    }
    Text(tr("Toca el número para marcar calentamiento (C), drop set (D) o al fallo (F). Peso y reps son opcionales: se usan como sugerencia la primera vez.", "Tap the number to mark a warm-up (W), drop set (D) or failure set (F). Weight and reps are optional: they're used as suggestions the first time."),
        style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)?) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Lo que se ve bajo el nombre en tr("Ordenar", "Reorder"): series y repeticiones (o "tiempo"). */
private fun reorderSubtitle(item: EditItem, ex: ExerciseEntity?): String {
    val tracking = Tracking.of(ex?.tracking ?: "weight_reps")
    val series = Fmt.plural(item.plan.size, tr("serie", "set"), tr("series", "sets"))
        return if (tracking == Tracking.DURATION) "$series · " + tr("tiempo", "time") else "$series · ${item.repMin}–${item.repMax} reps"
}
