package com.rupio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import com.rupio.app.data.repo.MuscleLoad
import com.rupio.app.data.local.Muscles
import com.rupio.app.data.repo.Bar
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.Tabular
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Gráficas propias en Canvas (sin dependencias): una sola serie por gráfica → sin leyenda,
 * el título la nombra. Marcas delgadas, extremos redondeados de 4 dp anclados a la base,
 * 2 dp de separación, rejilla tenue, toque/arrastre para ver el valor exacto.
 * El texto usa colores de texto, nunca el color de la serie.
 */

private fun niceMax(v: Double): Double {
    if (v <= 0) return 1.0
    val exp = Math.pow(10.0, Math.floor(Math.log10(v)))
    val f = v / exp
    val nice = when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 2.5 -> 2.5; f <= 5 -> 5.0; else -> 10.0 }
    return nice * exp
}

@Composable
fun VolumeBarChart(bars: List<Bar>, unit: WeightUnit, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    var selected by remember(bars) { mutableIntStateOf(bars.indexOfLast { it.volumeKg > 0 }.let { if (it < 0) bars.lastIndex else it }) }
    val maxV = niceMax(bars.maxOf { unit.fromKg(it.volumeKg) })
    val density = LocalDensity.current
    val sel = bars.getOrNull(selected)
    Column(modifier.semantics { contentDescription = "Gráfica de volumen: " + bars.joinToString { "${it.label} ${Fmt.volume(it.volumeKg, unit)}" } }) {
        // Tooltip fijo arriba (no tapa las barras)
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sel != null) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.volume(sel.volumeKg, unit) + "  ·  " + Fmt.volume(sel.volumeKg, unit.other), style = MaterialTheme.typography.titleSmall.merge(Tabular))
                    Text("${sel.label} · ${Fmt.plural(sel.workouts, "entreno", "entrenos")} · ${Fmt.plural(sel.sets, "serie", "series")}", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(170.dp)) {
            Column(Modifier.fillMaxHeight().width(40.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maxV, maxV / 2, 0.0).forEach {
                    Text(if (it >= 1000) "${Fmt.num(it / 1000, 1)}k" else Fmt.num(it, 0), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = RupioColors.TextFaint)
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(
                    Modifier.fillMaxSize().padding(bottom = 20.dp).pointerInput(bars) {
                        detectTapGestures { o -> selected = ((o.x / size.width) * bars.size).toInt().coerceIn(0, bars.lastIndex) }
                    }.pointerInput(bars) {
                        detectDragGestures { change, _ -> selected = ((change.position.x / size.width) * bars.size).toInt().coerceIn(0, bars.lastIndex) }
                    },
                ) {
                    val gap = with(density) { 2.dp.toPx() }
                    val r = with(density) { 4.dp.toPx() }
                    val slot = size.width / bars.size
                    val bw = (slot - gap).coerceAtMost(with(density) { 36.dp.toPx() })
                    // rejilla tenue
                    listOf(0f, 0.5f, 1f).forEach { f ->
                        val y = size.height * f
                        drawLine(RupioColors.Outline.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                            pathEffect = if (f == 1f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    }
                    bars.forEachIndexed { i, b ->
                        val h = (unit.fromKg(b.volumeKg) / maxV * size.height).toFloat()
                        if (h <= 0f) return@forEachIndexed
                        val x = i * slot + (slot - bw) / 2
                        val color = if (i == selected) RupioColors.Chart else RupioColors.Chart.copy(alpha = 0.55f)
                        // extremo superior redondeado, anclado a la base
                        val path = Path().apply {
                            val top = size.height - h
                            val rr = minOf(r, h, bw / 2)
                            moveTo(x, size.height); lineTo(x, top + rr)
                            quadraticBezierTo(x, top, x + rr, top); lineTo(x + bw - rr, top)
                            quadraticBezierTo(x + bw, top, x + bw, top + rr); lineTo(x + bw, size.height); close()
                        }
                        drawPath(path, color)
                    }
                }
                // etiquetas X (se adelgazan si hay muchas)
                val step = max(1, (bars.size / 7.0).roundToInt())
                Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(18.dp)) {
                    bars.forEachIndexed { i, b ->
                        Text(if (i % step == 0 || i == bars.lastIndex) b.label else "", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, color = if (i == selected) RupioColors.Text else RupioColors.TextFaint, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Línea de progreso (p. ej. 1RM estimado). Arrastra el dedo para ver cada punto. */
@Composable
fun ProgressLineChart(
    points: List<Pair<LocalDate, Double>>,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    toDisplay: (Double) -> Double = { unit.fromKg(it) },
    label: (Double) -> String = { Fmt.weight(it, unit) },
    subLabel: ((Double) -> String)? = { Fmt.weight(it, unit.other) },
    deltaLabel: (Double) -> String = { "${Fmt.num(it)} ${unit.label}" },
    /** Qué mide la gráfica, para el resumen que lee TalkBack ("1RM estimado"). */
    title: String = "Progreso",
    chartHeight: androidx.compose.ui.unit.Dp = 160.dp,
    emptyText: String = "Registra al menos dos sesiones para ver tu curva de progreso.",
) {
    if (points.size < 2) {
        Text(emptyText, style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim, modifier = modifier.padding(vertical = 16.dp))
        return
    }
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val vals = points.map { toDisplay(it.second) }
    val lo = vals.min(); val hi = vals.max()
    val pad = max((hi - lo) * 0.15, 1.0)
    val minY = (lo - pad).coerceAtLeast(0.0); val maxY = hi + pad
    val density = LocalDensity.current
    val sel = points[selected]
    val first = vals.first(); val last = vals.last()
    // Resumen hablado: "1RM estimado: de 80 kg a 95 kg entre marzo de 2019 y el 14 de noviembre de 2026".
    val spoken = "$title: de ${label(points.first().second)} a ${label(points.last().second)} entre " +
        Dates.spokenDay(points.first().first) + " de ${points.first().first.year} y " + Dates.spokenDay(points.last().first) + " de ${points.last().first.year}"
    Column(modifier.semantics { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label(sel.second), style = MaterialTheme.typography.titleLarge.merge(Tabular))
            Spacer(Modifier.width(8.dp))
            if (subLabel != null) Text(subLabel(sel.second), style = MaterialTheme.typography.labelMedium.merge(Tabular), color = RupioColors.TextFaint, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            val delta = last - first
            Text((if (delta >= 0) "+" else "−") + deltaLabel(kotlin.math.abs(delta)) + " desde el inicio", style = MaterialTheme.typography.labelMedium, color = RupioColors.TextDim)
        }
        Text(Dates.formatDayShort(sel.first) + " " + sel.first.year, style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(chartHeight).pointerInput(points) {
                detectTapGestures { o -> selected = ((o.x / size.width) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex) }
            }.pointerInput(points) {
                detectDragGestures { c, _ -> selected = ((c.position.x / size.width) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex) }
            },
        ) {
            fun px(i: Int) = size.width * i / (points.size - 1)
            fun py(v: Double) = (size.height - (v - minY) / (maxY - minY) * size.height).toFloat()
            listOf(0f, 0.5f, 1f).forEach { f ->
                drawLine(RupioColors.Outline.copy(alpha = 0.45f), Offset(0f, size.height * f), Offset(size.width, size.height * f), 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            val path = Path()
            vals.forEachIndexed { i, v -> if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v)) }
            val area = Path().apply { addPath(path); lineTo(px(vals.lastIndex), size.height); lineTo(0f, size.height); close() }
            drawPath(area, RupioColors.Chart.copy(alpha = 0.10f))
            drawPath(path, RupioColors.Chart, style = Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round))
            val x = px(selected); val y = py(vals[selected])
            drawLine(RupioColors.TextFaint, Offset(x, 0f), Offset(x, size.height), 1f)
            drawCircle(RupioColors.Surface, with(density) { 7.dp.toPx() }, Offset(x, y))
            drawCircle(RupioColors.Chart, with(density) { 5.dp.toPx() }, Offset(x, y))
        }
    }
}

/**
 * Series efectivas por músculo por semana, contadas con el método fraccional
 * (Pelland et al. 2026: ejercicio principal = 1 serie, músculo secundario = 0.5).
 * Marca en 10 series/sem (Schoenfeld et al. 2017: 10+ series/sem → más hipertrofia).
 * Más volumen sigue sumando, pero con rendimientos decrecientes. El estado va en texto (no solo color).
 */
@Composable
fun MuscleSetsChart(rows: List<MuscleLoad>, weeks: Double, modifier: Modifier = Modifier, judge: Boolean = true) {
    val perWeek = rows.map { it to it.sets / weeks.coerceAtLeast(1.0) }
    val maxV = max(20.0, perWeek.maxOfOrNull { it.second } ?: 0.0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // v1.9: en tu primera semana solo el número: decir "poco" a quien acaba de empezar no ayuda.
        if (!judge) Text("Llevas menos de una semana: aquí verás si cada músculo recibe lo que necesita.",
            style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
        perWeek.forEach { (row, pw) ->
            val status = when { !judge -> null; pw < 4 -> "poco"; pw < 10 -> "moderado"; else -> "10+ ✓" }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Muscles.label(row.muscle), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${Fmt.num(pw, 1)} series/sem" + (status?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium.merge(Tabular), color = RupioColors.TextDim)
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(RupioColors.Surface3, size = size, cornerRadius = r)
                    drawRoundRect(RupioColors.Chart, size = Size((pw / maxV * size.width).toFloat().coerceAtLeast(size.height), size.height), cornerRadius = r)
                    val x10 = (10 / maxV * size.width).toFloat()
                    drawLine(RupioColors.Text, Offset(x10, -2f), Offset(x10, size.height + 2f), strokeWidth = 2.dp.toPx())
                }
            }
        }
        Text("La rayita marca 10 series por semana: a partir de ahí la evidencia muestra más crecimiento; más series siguen sumando, pero cada vez menos. Los músculos secundarios cuentan como media serie.",
            style = MaterialTheme.typography.bodySmall, color = RupioColors.TextDim)
    }
}

/** Mapa de calor de constancia (estilo GitHub): columnas = semanas, filas = días. */
@Composable
fun TrainingHeatmap(days: Map<Int, Int>, from: LocalDate, to: LocalDate, modifier: Modifier = Modifier) {
    val weeks = ((to.toEpochDay() - from.toEpochDay()) / 7 + 1).toInt()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(weeks / 7f)) {
            val gap = 3.dp.toPx()
            val cell = (size.width - gap * (weeks - 1)) / weeks
            for (w in 0 until weeks) for (d in 0 until 7) {
                val day = from.plusDays((w * 7 + d).toLong())
                if (day.isAfter(to)) continue
                val n = days[day.toEpochDay().toInt()] ?: 0
                val color = if (n == 0) RupioColors.Surface3 else RupioColors.Heat[(n + 1).coerceAtMost(RupioColors.Heat.lastIndex)]
                drawRoundRect(color, Offset(w * (cell + gap), d * (cell + gap)), Size(cell, cell), CornerRadius(4.dp.toPx()))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${Fmt.plural(days.size, "día entrenado", "días entrenados")} en ${Fmt.plural(weeks, "semana", "semanas")}", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextDim, modifier = Modifier.weight(1f))
            Text("Menos ", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
            (listOf(RupioColors.Surface3) + RupioColors.Heat.drop(2)).forEach {
                Box(Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(it))
            }
            Text(" Más", style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
        }
    }
}

/**
 * v1.8 · Reparto de músculos de UN entreno: barras horizontales con series y porcentaje (método fraccional: el
 * músculo principal cuenta 1 por serie y cada secundario 0.5). El porcentaje va en texto, no solo en la barra.
 */
@Composable
fun MuscleSplitBars(shares: List<com.rupio.app.domain.MuscleShare>, modifier: Modifier = Modifier) {
    if (shares.isEmpty()) return
    val top = shares.maxOf { it.share }.coerceAtLeast(0.01)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        shares.forEach { s ->
            val name = if (s.muscle.isBlank()) "Otros" else Muscles.label(s.muscle)
            val pct = (s.share * 100).roundToInt()
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$name: ${Fmt.num(s.sets, 1)} series, $pct por ciento" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${Fmt.num(s.sets, 1)} ${if (s.sets == 1.0) "serie" else "series"}", style = MaterialTheme.typography.labelMedium.merge(Tabular), color = RupioColors.TextDim)
                    Text("$pct %", style = MaterialTheme.typography.labelLarge.merge(Tabular), modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.End)
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(RupioColors.Surface3, size = size, cornerRadius = r)
                    drawRoundRect(if (s.muscle.isBlank()) RupioColors.TextFaint else RupioColors.Chart,
                        size = Size((s.share / top * size.width).toFloat().coerceAtLeast(size.height), size.height), cornerRadius = r)
                }
            }
        }
        Text("Cada serie cuenta 1 para el músculo principal y media para los que ayudan. Los calentamientos no cuentan.",
            style = MaterialTheme.typography.labelSmall, color = RupioColors.TextFaint)
    }
}
