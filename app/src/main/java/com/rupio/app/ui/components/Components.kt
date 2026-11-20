package com.rupio.app.ui.components


import com.rupio.app.core.tr
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import kotlinx.coroutines.delay
import java.net.URLEncoder

/** Avatar con las iniciales del nombre, al estilo Google Meet. El color sale del nombre (estable). */
@Composable
fun InitialsAvatar(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val initials = when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1)
        else -> words[0].take(1) + words[1].take(1)
    }.uppercase()
    val color = RupioColors.Avatars[(name.trim().lowercase().hashCode() and Int.MAX_VALUE) % RupioColors.Avatars.size]
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
    }
}

/** Peso en la unidad del usuario y debajo, pequeño, en la otra unidad. */
@Composable
fun WeightBoth(kg: Double?, unit: WeightUnit, big: TextStyle = MaterialTheme.typography.titleMedium) {
    Column {
        Text(Fmt.weight(kg, unit), style = big.merge(Tabular))
        if (kg != null) Text(Fmt.weight(kg, unit.other), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = RupioColors.TextFaint)
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = RupioColors.Surface, border = BorderStroke(1.dp, RupioColors.Outline.copy(alpha = 0.6f))) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, subColor: Color = RupioColors.TextDim) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = RupioColors.Surface2) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            FitText(label, MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
            Spacer(Modifier.height(2.dp))
            // Si el número no cabe (p. ej. "156.5 mil lb" en tres columnas) usa 2 líneas y, solo si aún no cabe, se achica
            // sin bajar nunca del tamaño normal de letra.
            FitText(value, MaterialTheme.typography.titleLarge.merge(Tabular))
            if (sub != null) FitText(sub, MaterialTheme.typography.labelSmall, color = subColor)
        }
    }
}

/** Reglas de [FitText] con letra agrandada (aparte para poder probarlas). */
object FitTextRules {
    /** Lo más que se achica: ~150 % del tamaño normal ([base] / [fontScale] × 1.5), nunca más grande que [base]. */
    fun floorSp(base: Float, fontScale: Float): Float = if (fontScale <= 1f) base else minOf(base, base / fontScale * 1.5f)
    /** Con letra agrandada caben hasta 3 líneas antes de achicar. */
    fun maxLines(requested: Int, fontScale: Float): Int = if (fontScale > 1f) maxOf(requested, 3) else requested
}

/**
 * Texto que respeta el tamaño de letra que pidió la persona (accesibilidad, WCAG 1.4.4):
 *  1. Primero se acomoda en hasta [maxLines] líneas, con guiones en español si una palabra se parte.
 *  2. Solo si aun así no cabe, se achica; con letra agrandada caben hasta 3 líneas y nunca baja de ~150 % del normal.
 *     Con letra normal, una palabra sola se achica hasta 75 % antes que partirse.
 * Antes se achicaba hasta 35–60 % del tamaño pedido: con letra al 200 % las pestañas quedaban más chicas que lo normal.
 * Usa el autoSize de Compose (una sola pasada de medición, sin recomponer ni esconder el texto mientras tanto).
 */
@Composable
fun FitText(
    text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null, textAlign: TextAlign? = null, maxLines: Int = 2,
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val base = if (style.fontSize.isSpecified) style.fontSize else MaterialTheme.typography.bodyMedium.fontSize
    val tint = color.takeOrElse { style.color.takeOrElse { androidx.compose.material3.LocalContentColor.current } }
    // Con letra normal se parte solo entre palabras (sin guiones en títulos); con letra agrandada, si una palabra no cabe,
    // mejor partirla con guion en español que cortarla.
    val big = fontScale > 1f
    val st = style.merge(TextStyle(color = tint, fontWeight = fontWeight, textAlign = textAlign ?: TextAlign.Unspecified,
        hyphens = if (big) androidx.compose.ui.text.style.Hyphens.Auto else androidx.compose.ui.text.style.Hyphens.None,
        lineBreak = if (big) androidx.compose.ui.text.style.LineBreak.Paragraph else androidx.compose.ui.text.style.LineBreak.Simple,
        localeList = androidx.compose.ui.text.intl.LocaleList(if (com.rupio.app.core.I18n.isEnglish) "en-US" else "es-MX")))
    if (fontScale <= 1f) {
        // Letra normal o chica: una palabra sola (p. ej. "Repeticiones" en una tarjeta angosta) se achica un poco en una
        // línea en vez de partirse con guion; varias palabras usan hasta [maxLines] líneas.
        val single = !text.trim().contains(' ')
        androidx.compose.foundation.text.BasicText(text, modifier, style = st, maxLines = if (single) 1 else maxLines, overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = base * 0.75f, maxFontSize = base, stepSize = 0.5.sp))
    } else {
        // Letra agrandada por la persona (v1.7, N7): primero hasta 3 líneas y, solo si aun así no cabe, se achica, pero
        // nunca por debajo de ~150 % del tamaño normal (antes bajaba hasta 100 %: quien pidió 200 % veía la mitad).
        // En sp el tamaño "normal" es base / fontScale (el sistema lo vuelve a multiplicar al dibujar).
        androidx.compose.foundation.text.BasicText(text, modifier, style = st, maxLines = FitTextRules.maxLines(maxLines, fontScale), overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = FitTextRules.floorSp(base.value, fontScale).sp, maxFontSize = base, stepSize = 0.5.sp))
    }
}

/**
 * Tarjetas lado a lado con la misma altura. Si no caben (letra muy grande o pantalla angosta),
 * se acomodan una debajo de otra a todo lo ancho, en vez de cortar los textos.
 */
@Composable
fun TileRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { ms, c ->
        val gap = 10.dp.roundToPx()
        val n = ms.size.coerceAtLeast(1)
        val per = ((c.maxWidth - gap * (n - 1)) / n).coerceAtLeast(0)
        // Mínimo razonable por tarjeta, proporcional al tamaño de letra del sistema (el número ya se achica solo).
        if (per >= (84.dp * fontScale).roundToPx()) {
            val h = ms.maxOf { it.minIntrinsicHeight(per) }
            val ps = ms.map { it.measure(Constraints.fixed(per, h)) }
            layout(c.maxWidth, h) { ps.forEachIndexed { i, p -> p.place(i * (per + gap), 0) } }
        } else {
            val ps = ms.map { it.measure(Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth)) }
            layout(c.maxWidth, ps.sumOf { it.height } + gap * (ps.size - 1)) {
                var y = 0; ps.forEach { p -> p.place(0, y); y += p.height + gap }
            }
        }
    }
}

/** Una tarjeta dentro de TileRow. */
@Composable
fun Tile(label: String, value: String, sub: String? = null, subColor: Color = RupioColors.TextDim) =
    StatTile(label, value, Modifier.fillMaxSize(), sub, subColor)

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (filled) color else color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, color = if (filled) RupioColors.OnFill else color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FitText(text, MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * Fondo de las imágenes de ejercicios: el mismo celeste de las ilustraciones de RepDB (≈ #D9F1FD), para que la
 * imagen cuadrada no deje franjas blancas en la caja 1.4:1 del detalle ni en las miniaturas.
 */
val ExerciseImageBg = Color(0xFFD9F1FD)

/** Miniatura del ejercicio (ilustración de RepDB, ver docs/IMAGENES_REPDB.md). Sin imagen: ícono de pesa. */
@Composable
fun ExerciseThumb(url: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(ExerciseImageBg), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Rounded.FitnessCenter, null, tint = Color.Gray)
    }
}

/** Alterna las dos imágenes (inicio y fin del movimiento) para mostrar cómo se hace. Con una sola, queda fija. */
@Composable
fun AnimatedExerciseImage(image0: String?, image1: String?, modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(image0, image1) { if (image1 != null) while (true) { delay(1100); frame = 1 - frame } }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(ExerciseImageBg)) {
        Crossfade(targetState = frame, animationSpec = tween(450), label = "frames") { f ->
            AsyncImage(model = if (f == 0) image0 else image1, contentDescription = tr("Demostración del ejercicio", "Exercise demonstration"),
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

fun openVideo(context: android.content.Context, exerciseName: String) {
    val q = URLEncoder.encode(com.rupio.app.core.tr("cómo hacer $exerciseName técnica correcta", "how to do $exerciseName proper form"), "UTF-8")
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Campo numérico compacto para la tabla de series. Muestra el valor sugerido como "fantasma"
 * (placeholder) cuando está vacío; al marcar ✓ vacío se usa ese valor.
 */
@Composable
fun NumberCell(
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    done: Boolean = false,
    /** Qué es la celda, para TalkBack: "Peso en libras, serie 2, Press de banca, sugerido 135". */
    label: String? = null,
    /**
     * v1.8: segunda línea DENTRO de la celda (p. ej. "22.7 kg" bajo "50" lb). Antes iba debajo, fuera de la celda: la
     * fila quedaba más alta que las reps y la ✓ y se veía desalineada.
     */
    sub: String? = null,
    minHeight: androidx.compose.ui.unit.Dp = 40.dp,
    /**
     * v1.9: tope del agrandado de letra dentro de la cajita (p. ej. 1.5 = hasta 150 %). Con letra al 200 % los kilos de
     * la tabla de series salían "45.4 …"; así se ven completos y nunca más chicos que el 150 % (como [FitText]).
     */
    maxFontScale: Float? = null,
) {
    var text by remember { mutableStateOf(value) }
    // Solo se reemplaza el texto si el valor guardado es OTRO número (así no se pierde "13." mientras escribes).
    LaunchedEffect(value) { if (text.toDoubleOrNull() != value.toDoubleOrNull()) text = value }
    val bg = if (done) RupioColors.Success.copy(alpha = 0.10f) else RupioColors.Surface2
    val main = cappedScale(MaterialTheme.typography.titleMedium.merge(Tabular), maxFontScale)
    val small = cappedScale(MaterialTheme.typography.labelSmall.merge(Tabular), maxFontScale)
    BasicTextField(
        value = text,
        onValueChange = { v ->
            val clean = v.replace(',', '.').filter { it.isDigit() || (decimal && it == '.') || it == '-' }.take(7)
            text = clean; onValue(clean)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        textStyle = main.copy(color = RupioColors.Text, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(RupioColors.Primary),
        modifier = modifier.heightIn(min = minHeight).clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        decorationBox = { inner ->
            // Sin fillMaxSize: dentro de una fila con IntrinsicSize (superseries) la celda se estiraba a cientos de dp.
            Column(Modifier.fillMaxWidth().heightIn(min = minHeight).padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) Text(placeholder, style = main, color = RupioColors.TextFaint, textAlign = TextAlign.Center, maxLines = 1)
                    inner()
                }
                if (sub != null) Text(sub, style = small, color = RupioColors.TextFaint, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        },
    )
}

/** El estilo con el agrandado de letra del sistema topado en [max] (null = sin tope). */
@Composable
fun cappedScale(style: TextStyle, max: Float?): TextStyle {
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
    if (max == null || fs <= max || !style.fontSize.isSpecified) return style
    return style.copy(fontSize = style.fontSize * (max / fs))
}

val restOptions = listOf(0, 15, 30, 45, 60, 75, 90, 105, 120, 150, 180, 210, 240, 300, 360, 420, 600)

@Composable
fun RestPickerDialog(title: String, current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit, footer: (@Composable () -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 320.dp).selectableGroup(),
                ) {
                    items(restOptions.size) { i ->
                        val sec = restOptions[i]
                        val sel = sec == current
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (sel) RupioColors.Rest.copy(alpha = 0.25f) else RupioColors.Surface2)
                                .border(1.dp, if (sel) RupioColors.Rest else Color.Transparent, RoundedCornerShape(12.dp))
                                // Opción de una lista: TalkBack dice "botón de opción, seleccionado".
                                .selectable(selected = sel, role = Role.RadioButton, onClick = { onPick(sec) }).padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (sec == 0) Fmt.restShort(0) else Fmt.restLabel(sec), style = MaterialTheme.typography.labelLarge) }
                    }
                }
                footer?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cerrar", "Close")) } },
    )
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(RupioColors.Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = RupioColors.Primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Letra de superserie: A, B, C… */
fun supersetLetter(index: Int) = ('A' + (index % 26)).toString()

@Composable
fun SupersetTag(index: Int, member: Int?) {
    val c = RupioColors.Supersets[index % RupioColors.Supersets.size]
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(supersetLetter(index) + (member?.let { "${it + 1}" } ?: ""), color = RupioColors.OnFill, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun rememberContext() = LocalContext.current

/** Barra superior simple y consistente en toda la app. */
@Composable
fun RupioTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 60.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sin flecha: el título queda alineado a 16 dp, igual que el contenido.
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Atrás", "Back")) }
        else Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            FitText(title, MaterialTheme.typography.titleLarge)
            if (subtitle != null) FitText(subtitle, MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        }
        actions()
    }
}

/** −  valor  + (botones grandes, fáciles de tocar con guantes). */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, label: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(RupioColors.Surface2), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > range.first) onChange(value - 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Remove, tr("Menos", "Less"), modifier = Modifier.size(18.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 28.dp)) {
            Text("$value", style = MaterialTheme.typography.titleMedium.merge(Tabular))
            if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
        }
        IconButton(onClick = { if (value < range.last) onChange(value + 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Add, tr("Más", "More"), modifier = Modifier.size(18.dp))
        }
    }
}

/** Rango de repeticiones objetivo (doble progresión: al llegar al máximo en todas las series, sube el peso). */
@Composable
fun RepRangeDialog(min: Int, max: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var lo by remember { mutableIntStateOf(min) }
    var hi by remember { mutableIntStateOf(max) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Repeticiones objetivo", "Target reps")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Cuando hagas el máximo en todas las series, Rupio te dirá que subas el peso y vuelvas al mínimo.", "When you hit the top of the range on every set, Rupio will tell you to add weight and go back to the bottom."),
                    style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stepper(lo, { lo = it; if (hi < it) hi = it }, 1..50, tr("mínimo", "min"))
                    Text("a", color = RupioColors.TextDim)
                    Stepper(hi, { hi = it; if (lo > it) lo = it }, 1..60, tr("máximo", "max"))
                }
                Text(tr("Comunes: 6–8 fuerza · 8–12 músculo · 12–20 resistencia", "Common: 6–8 strength · 8–12 muscle · 12–20 endurance"), style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(lo, hi) }) { Text(tr("Guardar", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

/** v1.9: tiempo para tocar "Deshacer" (con 5 s el aviso se iba mientras lo tocabas). */
const val UNDO_MS = 10_000L

/**
 * v1.9 · Cambiar un nombre. El campo llega con el foco y el texto seleccionado: el teclado se abre solo y lo que
 * escribes reemplaza el nombre (antes había que tocar el campo primero).
 */
@Composable
fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, below: (@Composable (String) -> Unit)? = null, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(0, initial.length))) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus(); keyboard?.show() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                below?.invoke(value.text)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.text) }) { Text(tr("Guardar", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

/** Diálogo de confirmación reutilizable. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, danger: Boolean = false, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = RupioColors.TextDim) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = if (danger) RupioColors.Danger else RupioColors.Primary)) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}
