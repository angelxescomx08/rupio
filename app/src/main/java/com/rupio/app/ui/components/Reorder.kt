package com.rupio.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rupio.app.domain.ReorderOps
import com.rupio.app.ui.theme.RupioColors
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * v1.9 · Un elemento de la pantalla "Ordenar". [group] = id de la superserie (null = suelto): los de un mismo grupo
 * van juntos y se mueven como bloque.
 */
data class ReorderEntry(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val group: String? = null,
)

/**
 * v1.9 · Pantalla completa para reordenar arrastrando. Formas de mover, para que a nadie le cueste:
 * - arrastrar desde el ícono ≡ (empieza al instante);
 * - mantener presionada la fila y arrastrar (desde cualquier parte de la fila);
 * - con TalkBack: acciones "Mover arriba / abajo / al inicio / al final" en cada fila.
 * Las superseries se mueven completas desde su encabezado y, dentro, cada ejercicio se puede reacomodar.
 * Al acercarte al borde la lista se desplaza sola. Nada se guarda hasta tocar "Listo"; "Cancelar" o atrás lo
 * dejan como estaba (si ya moviste algo, pregunta antes).
 *
 * @param focusKey si se abrió manteniendo presionado un elemento, ese se muestra y se resalta un momento.
 */
@Composable
fun ReorderDialog(
    title: String,
    hint: String,
    entries: List<ReorderEntry>,
    onDismiss: () -> Unit,
    focusKey: String? = null,
    showImages: Boolean = true,
    onDone: (List<String>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val original = remember { entries.map { it.key } }
    var blocks by remember { mutableStateOf(ReorderOps.blocks(entries) { it.group }) }
    val flat = blocks.flatten()
    val changed = flat.map { it.key } != original
    var confirmCancel by remember { mutableStateOf(false) }
    fun cancel() { if (changed) confirmCancel = true else onDismiss() }
    fun tick() = haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    fun setFlat(list: List<ReorderEntry>?) { if (list != null) { blocks = ReorderOps.blocks(list) { it.group }; tick() } }

    // Superseries: A, B, C… por orden de aparición (igual que en el resto de la app), recalculado al mover.
    val ssIndex = supersetIndexMap(flat.map { it.group })
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = blocks.indexOfFirst { blockKey(it) == from.key }
        val t = blocks.indexOfFirst { blockKey(it) == to.key }
        if (f >= 0 && t >= 0) { blocks = ReorderOps.moveBlock(blocks, f, t); tick() }
    }

    // Resaltar (y mostrar) el elemento con el que se abrió.
    var highlight by remember { mutableStateOf(focusKey) }
    LaunchedEffect(Unit) {
        val bi = blocks.indexOfFirst { b -> b.any { it.key == focusKey } }
        if (bi > 0) listState.scrollToItem(bi)
        if (focusKey != null) { delay(1600); highlight = null }
    }

    Dialog(onDismissRequest = { cancel() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        BackHandler { cancel() }
        Surface(Modifier.fillMaxSize(), color = RupioColors.Background) {
            Column(Modifier.fillMaxSize()) {
                RupioTopBar(title, onBack = { cancel() }) {
                    Button(onClick = { onDone(flat.map { it.key }) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(end = 8.dp)) { Text("Listo") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SwapVert, null, tint = RupioColors.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("reorder_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(blocks, key = { blockKey(it) }) { block ->
                        val bi = blocks.indexOf(block)
                        ReorderableItem(reorderState, key = blockKey(block)) { dragging ->
                            val elevation = if (dragging) 12.dp else 0.dp
                            val g = block.first().group
                            if (g != null && block.size > 1) {
                                val idx = ssIndex[g] ?: 0
                                val color = supersetColor(idx)
                                Surface(
                                    shape = RoundedCornerShape(18.dp), color = if (dragging) RupioColors.Surface2 else color.copy(alpha = 0.06f),
                                    border = BorderStroke(1.5.dp, color.copy(alpha = 0.7f)), shadowElevation = elevation,
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        // Encabezado: arrastra la superserie completa.
                                        val groupName = "Superserie ${supersetLetter(idx)}"
                                        val blockActions = blockActions(
                                            bi, blocks.size, groupName,
                                            onMove = { to -> blocks = ReorderOps.moveBlock(blocks, bi, to); tick() },
                                        )
                                        Row(
                                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                                .longPressDraggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) })
                                                .semantics(mergeDescendants = true) {
                                                    contentDescription = "$groupName, ${block.size} ejercicios"
                                                    stateDescription = "Bloque ${bi + 1} de ${blocks.size}"
                                                    customActions = blockActions
                                                },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Spacer(Modifier.width(6.dp))
                                            SupersetTag(idx, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Superserie · ${block.size} ejercicios", style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f))
                                            DragHandleIcon(
                                                "handle:$g",
                                                Modifier.draggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }),
                                            )
                                        }
                                        // Miembros: se reacomodan dentro de la superserie.
                                        ReorderableColumn(
                                            list = block,
                                            onSettle = { from, to ->
                                                val i = blocks.indexOfFirst { blockKey(it) == blockKey(block) }
                                                if (i >= 0 && from != to) blocks = blocks.toMutableList().also { it[i] = ReorderOps.moveWithinBlock(it[i], from, to) }
                                            },
                                            onMove = { tick() },
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) { k, e, memberDragging ->
                                            key(e.key) {
                                                ReorderableItem {
                                                    EntryRow(
                                                        e, label = supersetLetter(idx) + (k + 1), labelColor = color, position = "${k + 1} de ${block.size} en la $groupName",
                                                        dragging = memberDragging, highlighted = highlight == e.key, showImage = showImages,
                                                        actions = entryActions(flat, e, ::setFlat),
                                                        longPress = Modifier.longPressDraggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }),
                                                        handle = Modifier.draggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                val e = block.first()
                                Box {
                                    EntryRow(
                                        e, label = "${flat.indexOf(e) + 1}", labelColor = RupioColors.TextDim, position = "${flat.indexOf(e) + 1} de ${flat.size}",
                                        dragging = dragging, highlighted = highlight == e.key, showImage = showImages,
                                        actions = entryActions(flat, e, ::setFlat),
                                        longPress = Modifier.longPressDraggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }),
                                        handle = Modifier.draggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (confirmCancel) ConfirmDialog("¿Descartar el nuevo orden?", "Todo se queda como estaba.", "Descartar", { confirmCancel = false }) {
            confirmCancel = false; onDismiss()
        }
    }
}

private fun blockKey(block: List<ReorderEntry>): String =
    block.first().group?.takeIf { block.size > 1 }?.let { "g:$it" } ?: "e:${block.first().key}"

/** Fila compacta: número, imagen, nombre y ≡. Toda la fila se arrastra manteniéndola presionada. */
@Composable
private fun EntryRow(
    e: ReorderEntry, label: String, labelColor: Color, position: String, dragging: Boolean, highlighted: Boolean, showImage: Boolean,
    actions: List<CustomAccessibilityAction>, longPress: Modifier, handle: Modifier,
) {
    val bg by animateColorAsState(
        when { dragging -> RupioColors.Surface3; highlighted -> RupioColors.Primary.copy(alpha = 0.22f); else -> RupioColors.Surface },
        tween(250), label = "reorder_bg",
    )
    // Al levantarla: un poco más grande y con sombra, para que se note que la llevas en el dedo.
    val lift by animateFloatAsState(if (dragging) 1f else 0f, tween(150), label = "reorder_lift")
    Surface(shape = RoundedCornerShape(14.dp), color = bg, modifier = Modifier.fillMaxWidth().graphicsLayer {
        val s = 1f + 0.03f * lift
        scaleX = s; scaleY = s
        shadowElevation = 14.dp.toPx() * lift
        shape = RoundedCornerShape(14.dp); clip = true
    }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).then(longPress)
                .semantics(mergeDescendants = true) {
                    contentDescription = e.title + (e.subtitle?.let { ", $it" } ?: "")
                    stateDescription = position
                    customActions = actions
                }
                .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor, modifier = Modifier.widthIn(min = 26.dp).clearAndSetSemantics { })
            if (showImage) { ExerciseThumb(e.image, 40.dp, Modifier.clearAndSetSemantics { }); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                e.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            DragHandleIcon("handle:${e.key}", handle)
        }
    }
}

/**
 * El ≡: 48 dp de área para tocarlo fácil; el arrastre empieza al instante. TalkBack no lo anuncia (con TalkBack no se
 * arrastra): la fila trae las acciones "Mover arriba/abajo/al inicio/al final".
 */
@Composable
private fun DragHandleIcon(tag: String, modifier: Modifier) {
    Box(modifier.size(48.dp).testTag(tag).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.DragHandle, null, tint = RupioColors.TextDim)
    }
}

/** Acciones de TalkBack de un ejercicio (con las mismas reglas que Subir/Bajar: las superseries no se parten). */
private fun entryActions(flat: List<ReorderEntry>, e: ReorderEntry, apply: (List<ReorderEntry>?) -> Unit): List<CustomAccessibilityAction> {
    val k: (ReorderEntry) -> String = { it.key }; val g: (ReorderEntry) -> String? = { it.group }
    return buildList {
        ReorderOps.moveStep(flat, e.key, -1, k, g)?.let { r -> add(CustomAccessibilityAction("Mover arriba") { apply(r); true }) }
        ReorderOps.moveStep(flat, e.key, 1, k, g)?.let { r -> add(CustomAccessibilityAction("Mover abajo") { apply(r); true }) }
        ReorderOps.moveToEdge(flat, e.key, true, k, g)?.let { r -> add(CustomAccessibilityAction("Mover al inicio") { apply(r); true }) }
        ReorderOps.moveToEdge(flat, e.key, false, k, g)?.let { r -> add(CustomAccessibilityAction("Mover al final") { apply(r); true }) }
    }
}

/** Acciones de TalkBack de una superserie completa. */
private fun blockActions(index: Int, count: Int, name: String, onMove: (Int) -> Unit): List<CustomAccessibilityAction> = buildList {
    if (index > 0) add(CustomAccessibilityAction("Mover $name arriba") { onMove(index - 1); true })
    if (index < count - 1) add(CustomAccessibilityAction("Mover $name abajo") { onMove(index + 1); true })
    if (index > 0) add(CustomAccessibilityAction("Mover $name al inicio") { onMove(0); true })
    if (index < count - 1) add(CustomAccessibilityAction("Mover $name al final") { onMove(count - 1); true })
}
