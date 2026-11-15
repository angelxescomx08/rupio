package com.rupio.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Serie planeada de una rutina (como en Hevy): peso (kg), reps, tiempo y tipo.
 * Se usa como valor sugerido cuando todavía no hay historial de ese ejercicio,
 * y para marcar los calentamientos al empezar el entrenamiento.
 */
@Serializable
data class PlanSet(val w: Double? = null, val r: Int? = null, val d: Int? = null, val t: Int = 0) {
    val type get() = SetType.of(t)
}

object SetPlans {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val ser = ListSerializer(PlanSet.serializer())

    fun decode(text: String?): List<PlanSet> =
        if (text.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(ser, text) }.getOrDefault(emptyList())

    fun encode(plan: List<PlanSet>): String? = if (plan.isEmpty()) null else json.encodeToString(ser, plan)

    /** Ajusta el plan a [n] series: recorta o repite la última. */
    fun resize(plan: List<PlanSet>, n: Int): List<PlanSet> = when {
        n <= 0 -> emptyList()
        plan.size >= n -> plan.take(n)
        else -> plan + List(n - plan.size) { plan.lastOrNull()?.copy(t = SetType.NORMAL.code) ?: PlanSet() }
    }

    /** Rango de repeticiones a partir del plan: máximo = reps más altas; mínimo = 4 menos (doble progresión). */
    fun repRange(plan: List<PlanSet>, fallback: Pair<Int, Int>): Pair<Int, Int> {
        val maxR = plan.filter { it.type != SetType.WARMUP }.mapNotNull { it.r?.takeIf { r -> r in 1..1000 } }.maxOrNull() ?: return fallback
        return (maxR - 4).coerceAtLeast(1) to maxR
    }

    /** "50×12 · 50×12 · C 25×10" en la unidad del usuario. */
    fun summary(plan: List<PlanSet>, unit: com.rupio.app.core.WeightUnit, tracking: Tracking): String = plan.joinToString(" · ") { p ->
        val prefix = if (p.type == SetType.NORMAL) "" else p.type.short + " "
        prefix + when {
            tracking == Tracking.DURATION -> com.rupio.app.core.Fmt.duration((p.d ?: 0).toLong())
            tracking.usesWeight && p.w != null && p.w != 0.0 -> "${com.rupio.app.core.Fmt.num(unit.fromKg(kotlin.math.abs(p.w)))}×${p.r ?: "?"}"
            else -> "${p.r ?: "?"} reps"
        }
    }
}
