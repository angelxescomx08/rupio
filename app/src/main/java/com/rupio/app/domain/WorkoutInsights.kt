package com.rupio.app.domain

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v1.8 · Calorías estimadas de un entreno de pesas. Puro Kotlin: tiene pruebas.
 *
 * Método MET (kcal = MET × kg × horas; 1 MET ≈ 1 kcal por kg por hora), con el valor del 2024 Adult Compendium of
 * Physical Activities (Herrmann et al., J Sport Health Sci 2024;13:6-12), código 02054: "Resistance (weight) training,
 * multiple exercises, 8-15 reps at varied resistance" = 3.5 MET. Es la descripción que más se parece a una sesión
 * típica de Rupio (varios ejercicios, 8–12 reps, descansos entre series). Ver docs/CIENCIA.md §7.
 *
 *  - Incluye el gasto en reposo (los MET son brutos): no es "extra" sobre tu día.
 *  - Es una estimación de grupo: en una persona puede fallar ±20–30 % (masa muscular, intensidad, descansos).
 *  - Si el entreno quedó abierto horas, no se cuentan: máximo 5 min por serie efectiva (y 3 h en total).
 *  - Sin peso corporal registrado se usa 70 kg y se dice.
 */
object CalorieEstimate {
    const val MET = 3.5
    const val COMPENDIUM_CODE = "02054"
    const val DEFAULT_BODY_KG = 70.0
    const val MAX_MIN_PER_SET = 5.0
    const val MAX_MINUTES = 180.0

    data class Result(
        /** Redondeado a 5 kcal: más precisión sería falsa. */
        val kcal: Int,
        val minutesCounted: Int,
        val bodyKg: Double,
        /** true si no había peso corporal y se usó [DEFAULT_BODY_KG]. */
        val assumedWeight: Boolean,
        /** true si se recortó la duración (entreno que quedó abierto). */
        val capped: Boolean,
    )

    fun estimate(durationSec: Long, effectiveSets: Int, bodyKg: Double?): Result? {
        if (effectiveSets <= 0 || durationSec <= 0) return null
        val minutes = durationSec / 60.0
        val cap = min(effectiveSets * MAX_MIN_PER_SET, MAX_MINUTES)
        val used = min(minutes, cap)
        if (used < 1.0) return null                                    // menos de un minuto: nada que estimar
        val kg = bodyKg?.takeIf { it in 25.0..350.0 }
        val w = kg ?: DEFAULT_BODY_KG
        val kcal = MET * w * used / 60.0
        return Result((kcal / 5).roundToInt() * 5, used.roundToInt(), w, kg == null, minutes > cap + 0.5)
    }
}

/** Un músculo y su parte de las series del entreno. [sets] puede ser fraccional (secundario = media serie). */
data class MuscleShare(val muscle: String, val sets: Double, val share: Double)

/**
 * v1.8 · Reparto de músculos de un entreno. Mismo método fraccional que "Series por músculo" en Progreso
 * (Pelland et al. 2026): cada serie efectiva cuenta 1 para el músculo principal y 0.5 para cada secundario.
 * Calentamientos no cuentan. Puro Kotlin: tiene pruebas.
 */
object MuscleSplit {
    data class Item(val primary: String, val secondary: List<String>, val sets: Int)

    /** Los [max] músculos con más series y el resto junto en "otros" (código vacío). */
    fun compute(items: List<Item>, max: Int = 6): List<MuscleShare> {
        val acc = LinkedHashMap<String, Double>()
        for (it in items) {
            if (it.sets <= 0 || it.primary.isBlank()) continue
            acc[it.primary] = (acc[it.primary] ?: 0.0) + it.sets
            it.secondary.filter { m -> m.isNotBlank() && m != it.primary }.distinct().forEach { m -> acc[m] = (acc[m] ?: 0.0) + it.sets * 0.5 }
        }
        val total = acc.values.sum()
        if (total <= 0) return emptyList()
        val sorted = acc.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
        val top = sorted.take(max).map { MuscleShare(it.key, it.value, it.value / total) }
        val rest = sorted.drop(max).sumOf { it.value }
        return if (rest > 0) top + MuscleShare("", rest, rest / total) else top
    }
}
