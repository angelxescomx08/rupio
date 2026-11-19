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
        /** v1.9: minutos de pesas (a 3.5 MET) y de cardio (cada uno con su MET). */
        val strengthMinutes: Int = minutesCounted,
        val cardioMinutes: Int = 0,
    )

    /** v1.9: un bloque de cardio registrado (su MET y sus segundos). */
    data class CardioPart(val met: Double, val sec: Int)

    /**
     * [effectiveSets] = series efectivas de PESAS. [cardio] = lo que anotaste de cardio: cuenta con su propio MET y con
     * sus minutos, aunque lo anotes al final (antes 20 min de caminadora daban "≈ 10 kcal" con el reloj de 2 min).
     */
    fun estimate(durationSec: Long, effectiveSets: Int, bodyKg: Double?, cardio: List<CardioPart> = emptyList()): Result? {
        val parts = cardio.filter { it.sec > 0 && it.met.isFinite() && it.met > 0 }
        val cardioSec = parts.sumOf { it.sec.coerceAtMost(86_400).toLong() }
        // Pesas: el reloj menos lo que fue cardio, con el tope de 5 min por serie (entreno que quedó abierto).
        val clockMin = (durationSec - cardioSec).coerceAtLeast(0) / 60.0
        val cap = min(effectiveSets.coerceAtLeast(0) * MAX_MIN_PER_SET, MAX_MINUTES)
        val strengthMin = if (effectiveSets > 0) min(clockMin, cap) else 0.0
        val cardioMin = cardioSec / 60.0
        if (strengthMin + cardioMin < 1.0) return null                  // menos de un minuto: nada que estimar
        val kg = bodyKg?.takeIf { it in 25.0..350.0 }
        val w = kg ?: DEFAULT_BODY_KG
        val kcal = MET * w * strengthMin / 60.0 + parts.sumOf { it.met * w * it.sec.coerceAtMost(86_400) / 3600.0 }
        return Result((kcal / 5).roundToInt() * 5, (strengthMin + cardioMin).roundToInt(), w, kg == null,
            effectiveSets > 0 && clockMin > cap + 0.5, strengthMin.roundToInt(), cardioMin.roundToInt())
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

    /** v1.9: grupos que no son un músculo: no cuentan para las series por músculo (una caminadora no es cuádriceps). */
    val NOT_COUNTED = setOf("cardio", "cuerpo_completo")

    /** Los [max] músculos con más series y el resto junto en "otros" (código vacío). */
    fun compute(items: List<Item>, max: Int = 6): List<MuscleShare> {
        val acc = LinkedHashMap<String, Double>()
        for (it in items) {
            if (it.sets <= 0 || it.primary.isBlank() || it.primary in NOT_COUNTED) continue
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
