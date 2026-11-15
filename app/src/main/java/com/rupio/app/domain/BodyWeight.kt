package com.rupio.app.domain

import com.rupio.app.data.local.WeightPoint
import kotlin.math.abs
import kotlin.math.pow

/**
 * Tendencia del peso corporal.
 *
 * El peso de un día sube o baja 1–2 kg por agua, sal, comida en el estómago o el baño. Por eso se mira la
 * TENDENCIA: una media móvil exponencial con 10 % de suavizado por día. Si faltan días, el dato nuevo pesa
 * más (1 − 0.9^días), así no importa si te pesas diario o cada tanto.
 */
object WeightTrend {
    private const val KEEP = 0.9

    fun smooth(points: List<WeightPoint>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<Double>(points.size)
        var t = points[0].kg
        var prevDay = points[0].day
        for ((i, p) in points.withIndex()) {
            if (i > 0) {
                val gap = (p.day - prevDay).coerceAtLeast(1)
                val alpha = 1 - KEEP.pow(gap)
                t += alpha * (p.kg - t)
                prevDay = p.day
            }
            out += t
        }
        return out
    }

    /** Cubeta de la consulta según el largo del rango: ≤ ~150 puntos siempre. */
    fun bucketFor(days: Int): Int = when { days <= 150 -> 1; days <= 1000 -> 7; else -> maxOf(30, days / 150) }

    /**
     * Ritmo en % del peso por semana, comparando la tendencia de hoy con la de ~2–4 semanas atrás.
     * null si hay menos de 7 días de datos (todavía no se puede saber).
     */
    fun ratePerWeek(points: List<WeightPoint>, trend: List<Double> = smooth(points)): Double? {
        if (points.size < 2) return null
        val last = points.last().day
        val idx = points.indexOfLast { it.day <= last - 14 }.let { if (it >= 0) it else 0 }
        val span = last - points[idx].day
        if (span < 7) return null
        val from = trend[idx]; val to = trend.last()
        if (from <= 0) return null
        return (to - from) / from * 100.0 / (span / 7.0)
    }

    enum class Pace { STABLE, LOSING_SLOW, LOSING_GOOD, LOSING_FAST, GAINING_GOOD, GAINING_FAST }

    fun pace(rate: Double): Pace = when {
        abs(rate) < 0.15 -> Pace.STABLE
        rate <= -1.0 -> Pace.LOSING_FAST
        rate <= -0.5 -> Pace.LOSING_GOOD
        rate < 0 -> Pace.LOSING_SLOW
        rate <= 0.5 -> Pace.GAINING_GOOD
        else -> Pace.GAINING_FAST
    }

    /** Explicación neutral del ritmo (referencias: Helms 2014 para bajar; Iraki 2019 para subir). */
    fun explain(rate: Double): String = when (pace(rate)) {
        Pace.STABLE -> "Estable: tu peso casi no cambia. Comes más o menos lo que gastas (mantenimiento)."
        Pace.LOSING_SLOW -> "Bajando despacio. Si tu meta es definir, un ritmo de 0.5–1 % por semana ayuda a conservar el músculo."
        Pace.LOSING_GOOD -> "Bajando a buen ritmo para definir (0.5–1 % por semana ayuda a conservar el músculo)."
        Pace.LOSING_FAST -> "Bajando rápido: más de 1 % por semana. Ir más lento ayuda a no perder músculo ni fuerza."
        Pace.GAINING_GOOD -> "Subiendo poco a poco. Para ganar músculo con poca grasa se sugiere 0.25–0.5 % por semana."
        Pace.GAINING_FAST -> "Subiendo rápido: más de 0.5 % por semana suele sumar más grasa que músculo."
    }
}
