package com.rupio.app.domain


import com.rupio.app.core.tr
import com.rupio.app.data.local.DayBest
import com.rupio.app.data.local.Totals2

/**
 * "Tu progreso": convierte el historial en una historia fácil de entender (y motivante):
 * fuerza antes → ahora por ejercicio, último mes contra el anterior, rachas, récords y estancamientos.
 * Lógica pura (sin Android) para poder probarla.
 */
data class ExerciseProgress(
    val exerciseId: String,
    val name: String,
    val sessions: Int,
    val firstDay: Int,
    val lastDay: Int,
    val first: DayBest,
    val now: DayBest,
    val changePct: Double,
    val repsOnly: Boolean,
    val stalled: Boolean,
)

data class ProgressStory(
    val firstDay: Int?,
    val weeks: Int,
    val total: Totals2,
    val last30: Totals2,
    val prev30: Totals2,
    val recordsTotal: Int,
    val records30: Int,
    val exercises: List<ExerciseProgress>,
    val medianStrengthPct: Double?,
    val streakWeeks: Int,
    val bestStreakWeeks: Int,
    val bodyFirstKg: Double?,
    val bodyNowKg: Double?,
) {
    val improved get() = exercises.filter { !it.stalled && it.changePct >= 1.0 }
    val stalled get() = exercises.filter { it.stalled }
    val isEmpty get() = total.n == 0
}

object ProgressStoryBuilder {

    /**
     * @param bests mejor serie por (ejercicio, día) — ya agregada en SQL.
     * @param repsOnlyIds ejercicios que se miden en repeticiones.
     * @param trainingDays días (epoch day) con entrenamiento.
     */
    fun build(
        today: Int,
        bests: List<DayBest>,
        repsOnlyIds: Set<String>,
        names: Map<String, String>,
        total: Totals2,
        last30: Totals2,
        prev30: Totals2,
        recordsTotal: Int,
        records30: Int,
        trainingDays: Collection<Int>,
        weekShift: Int,
        bodyTrend: List<Double>,
    ): ProgressStory {
        val exercises = bests.groupBy { it.exerciseId }.mapNotNull { (id, rows) ->
            val days = rows.sortedBy { it.day }
            if (days.size < 2) return@mapNotNull null
            val repsOnly = id in repsOnlyIds
            fun score(d: DayBest) = (if (repsOnly) (d.reps ?: 0).toDouble() else d.e1rm ?: 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0
            val first = days.first()
            // "Ahora" = la mejor de tus últimas 3 sesiones: un mal día no borra tu progreso.
            val now = days.takeLast(3).maxBy { score(it) }
            val base = score(first)
            if (base <= 0) return@mapNotNull null
            val pct = (score(now) - base) / base * 100
            if (!pct.isFinite()) return@mapNotNull null
            // Estancado: ≥ 4 sesiones en 6 semanas y las últimas 3 no superan a las 3 anteriores.
            val recent = days.filter { it.day >= today - 42 }
            val stalled = recent.size >= 4 && days.size >= 6 &&
                days.takeLast(3).maxOf { score(it) } <= days.dropLast(3).takeLast(3).maxOf { score(it) } * 1.01
            ExerciseProgress(id, names[id] ?: id, days.size, first.day, days.last().day, first, now, pct, repsOnly, stalled)
        }.sortedWith(compareByDescending<ExerciseProgress> { it.changePct }.thenByDescending { it.sessions })

        val solid = exercises.filter { it.sessions >= 3 }.map { it.changePct }.sorted()
        val median = if (solid.isEmpty()) null else solid[solid.size / 2]

        val weeks = trainingDays.map { Math.floorDiv(it + weekShift, 7) }.toSortedSet()
        val thisWeek = Math.floorDiv(today + weekShift, 7)
        var w = if (thisWeek in weeks) thisWeek else thisWeek - 1
        var streak = 0
        while (w in weeks) { streak++; w-- }
        var best = 0; var run = 0; var prev: Int? = null
        for (x in weeks) { run = if (prev != null && x == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = x }

        val first = total.first
        return ProgressStory(
            firstDay = first,
            weeks = if (first == null) 0 else ((today - first) / 7 + 1),
            total = total, last30 = last30, prev30 = prev30,
            recordsTotal = recordsTotal, records30 = records30,
            exercises = exercises,
            medianStrengthPct = median,
            streakWeeks = streak, bestStreakWeeks = best,
            bodyFirstKg = bodyTrend.firstOrNull(), bodyNowKg = bodyTrend.lastOrNull(),
        )
    }

    /** Frase principal, en lenguaje sencillo. */
    fun headline(s: ProgressStory): String = when {
        s.isEmpty -> tr("Tu historia empieza con tu primer entrenamiento.", "Your story starts with your first workout.")
        (s.medianStrengthPct ?: 0.0) >= 1.0 -> tr("Eres ~${Math.round(s.medianStrengthPct!!)} % más fuerte que cuando empezaste", "You're ~${Math.round(s.medianStrengthPct!!)}% stronger than when you started")
        s.total.n == 1 -> tr("¡Primer entrenamiento hecho! Ya empezaste.", "First workout done! You've started.")
        else -> tr("Llevas ${s.total.n} entrenamientos: estás construyendo tu base", "${s.total.n} workouts so far: you're building your base")
    }

    /** Explicación de qué está pasando y qué esperar (para que nadie se desmotive). */
    fun meaning(s: ProgressStory): List<String> {
        val out = mutableListOf<String>()
        if (s.isEmpty) return listOf(tr("Cuando termines entrenamientos, aquí verás cómo cambian tu fuerza, tus récords y tu constancia.", "As you finish workouts, you'll see here how your strength, PRs and consistency change."))
        if (s.medianStrengthPct != null) out += tr("El % compara tu mejor serie de ahora con la de tu primera vez en cada ejercicio, " +
            "usando la fuerza estimada (1RM): así cuenta aunque hayas cambiado el peso o las repeticiones.",
            "The % compares your best set now with your first time on each exercise, using estimated strength (1RM), " +
                "so it counts even if you changed the weight or the reps.")
        out += if (s.weeks < 52) tr("Estás en tu primer año: es cuando más rápido se progresa. Aprovecha con buena técnica y constancia; esas bases te sirven para siempre.", "You're in your first year: that's when progress is fastest. Make the most of it with good form and consistency; those foundations last forever.")
            else tr("Ya pasaste tu primer año. Es normal que ahora el progreso sea más lento: cada pequeño aumento vale más.", "You're past your first year. It's normal for progress to be slower now: every small gain is worth more.")
        if (s.stalled.isNotEmpty()) out += tr("Algunos ejercicios no han subido en las últimas sesiones. Es normal y le pasa a todos; abajo te decimos qué probar.", "Some exercises haven't gone up in recent sessions. That's normal and happens to everyone; below we tell you what to try.")
        if (s.last30.n > 0 && s.prev30.n > 0 && s.last30.n >= s.prev30.n) out += tr("Este mes entrenaste igual o más que el anterior. La constancia es lo que más resultados da.", "This month you trained as much as or more than last month. Consistency is what gets the most results.")
        return out
    }

    /** Equivalencia divertida del volumen total (aproximada). */
    fun equivalence(kg: Double): String? {
        val things = listOf(Triple(tr("un auto compacto", "a compact car"), 1_200.0, "🚗"), Triple(tr("un elefante africano", "an African elephant"), 6_000.0, "🐘"),
            Triple(tr("un autobús", "a bus"), 12_000.0, "🚌"), Triple(tr("una ballena azul", "a blue whale"), 150_000.0, "🐋"))
        val (name, w, emoji) = things.lastOrNull { kg >= it.second } ?: return null
        val times = kg / w
        val n = if (times < 10) String.format(java.util.Locale.US, "%.1f", times) else Math.round(times).toString()
        return if (times < 1.5) tr("como levantar $name $emoji", "like lifting $name $emoji")
            else tr("como levantar $n veces $name $emoji", "like lifting $name $n times $emoji")
    }

}
