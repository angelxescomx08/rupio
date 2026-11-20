package com.rupio.app.ui.components


import com.rupio.app.core.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rupio.app.core.Fmt
import com.rupio.app.domain.SupersetRestMode
import com.rupio.app.ui.theme.RupioColors

/*
 * Superseries que se distinguen de verdad (no solo un color como en Hevy):
 *  1. Contenedor con borde del color del grupo y cabecera "A · Superserie".
 *  2. Árbol a la izquierda (├ └) que une a los miembros, dibujado con líneas.
 *  3. Etiqueta con letra + número (A1, A2, A3) en cada ejercicio.
 *  4. La cabecera dice cómo es el descanso: compartido tras la ronda o individual.
 * Los colores están validados para daltonismo y además nunca son la única pista.
 */

fun supersetColor(index: Int): Color = RupioColors.Supersets[index % RupioColors.Supersets.size]

/** supersetId → índice (A=0, B=1…) en orden de aparición. */
fun supersetIndexMap(ids: List<String?>): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    ids.forEach { if (it != null && it !in out) out[it] = out.size }
    return out
}

/** Agrupa elementos consecutivos con el mismo supersetId (null = suelto). */
fun <T> groupConsecutive(items: List<T>, ss: (T) -> String?): List<List<T>> {
    val out = mutableListOf<MutableList<T>>()
    for (it in items) {
        val last = out.lastOrNull()
        val id = ss(it)
        if (id != null && last != null && ss(last.first()) == id) last += it else out += mutableListOf(it)
    }
    return out
}

fun supersetRestText(mode: SupersetRestMode, sharedSec: Int?): String = when (mode) {
    SupersetRestMode.SHARED -> tr("Descanso compartido: ${Fmt.restLabel(sharedSec ?: 0)} al terminar la ronda", "Shared rest: ${Fmt.restLabel(sharedSec ?: 0)} after the round")
    SupersetRestMode.INDIVIDUAL -> tr("Descanso individual: cada ejercicio usa el suyo", "Individual rest: each exercise uses its own")
}

/** Contenedor de una superserie. [content] recibe (índice del miembro, total) para dibujar cada uno. */
@Composable
fun SupersetGroup(
    index: Int,
    count: Int,
    mode: SupersetRestMode,
    sharedRestSec: Int?,
    onEditRest: (() -> Unit)?,
    onUngroup: (() -> Unit)?,
    modifier: Modifier = Modifier,
    member: @Composable (Int) -> Unit,
) {
    val color = supersetColor(index)
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.06f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SupersetTag(index, null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Superserie · $count ejercicios", "Superset · $count exercises"), style = MaterialTheme.typography.labelLarge, color = color)
                    Text(supersetRestText(mode, sharedRestSec), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                }
                if (onEditRest != null) IconButton(onClick = onEditRest) { Icon(Icons.Rounded.Timer, tr("Descanso de la superserie", "Superset rest"), tint = RupioColors.Rest) }
                if (onUngroup != null) IconButton(onClick = onUngroup) { Icon(Icons.Rounded.LinkOff, tr("Separar superserie", "Split superset"), tint = RupioColors.TextDim) }
            }
            for (i in 0 until count) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    TreeGutter(color, isLast = i == count - 1, Modifier.width(18.dp).fillMaxHeight())
                    Box(Modifier.weight(1f).padding(vertical = 3.dp)) { member(i) }
                }
            }
        }
    }
}

/** Línea vertical + rama horizontal: ├ para miembros intermedios, └ para el último. */
@Composable
fun TreeGutter(color: Color, isLast: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val x = size.width * 0.45f
        val midY = size.height * 0.5f
        val w = 2.5.dp.toPx()
        drawLine(color, Offset(x, 0f), Offset(x, if (isLast) midY else size.height), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(x, midY), Offset(size.width, midY), strokeWidth = w, cap = StrokeCap.Round)
    }
}

/**
 * Crear / editar el descanso de una superserie: compartido (uno solo al terminar la ronda A1→A2→descanso)
 * o individual (cada ejercicio usa su propio descanso).
 */
@Composable
fun SupersetRestDialog(
    title: String,
    initialMode: SupersetRestMode,
    initialRestSec: Int,
    sameMuscle: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (SupersetRestMode, Int?) -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var rest by remember { mutableIntStateOf(initialRestSec) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeOption(mode == SupersetRestMode.SHARED, tr("Un descanso para toda la superserie", "One rest for the whole superset"),
                    tr("Haces A1 → A2 sin parar y descansas al terminar la ronda.", "You do A1 → A2 without stopping and rest when the round is done.")) { mode = SupersetRestMode.SHARED }
                if (mode == SupersetRestMode.SHARED) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RupioColors.Rest.copy(alpha = 0.14f))
                            .clickable { picking = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Timer, null, tint = RupioColors.Rest)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Descanso tras la ronda: ${Fmt.restLabel(rest)}", "Rest after the round: ${Fmt.restLabel(rest)}"), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(tr("Cambiar", "Change"), color = RupioColors.Primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                ModeOption(mode == SupersetRestMode.INDIVIDUAL, tr("Descanso individual", "Individual rest"),
                    tr("Cada ejercicio usa su propio descanso después de su serie.", "Each exercise uses its own rest after its set.")) { mode = SupersetRestMode.INDIVIDUAL }
                Surface(shape = RoundedCornerShape(12.dp), color = RupioColors.Surface2) {
                    Text(
                        if (sameMuscle) tr("⚠ Estos ejercicios trabajan el mismo músculo: en superserie harás menos peso total. Combinar músculos opuestos (pecho + espalda) rinde más.", "⚠ These exercises work the same muscle: in a superset you'll lift less total weight. Pairing opposing muscles (chest + back) works better.")
                        else tr("Tip: las superseries ahorran tiempo sin perder resultados; combinar músculos opuestos (pecho + espalda, bíceps + tríceps) te deja hacer más repeticiones.", "Tip: supersets save time without losing results; pairing opposing muscles (chest + back, biceps + triceps) lets you do more reps."),
                        style = MaterialTheme.typography.bodySmall, color = if (sameMuscle) RupioColors.Warning else RupioColors.TextDim,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode, if (mode == SupersetRestMode.SHARED) rest else null) }) { Text(tr("Listo", "Done")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
    if (picking) RestPickerDialog(tr("Descanso tras la ronda", "Rest after the round"), rest, { picking = false }, { rest = it; picking = false })
}

@Composable
private fun ModeOption(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) RupioColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        }
    }
}
