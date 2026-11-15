package com.rupio.app.domain

import com.rupio.app.core.Fmt
import com.rupio.app.core.WeightUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Cómo se registra un ejercicio. "Hay ejercicios que solo tienen repeticiones y no peso". */
enum class Tracking(val code: String) {
    WEIGHT_REPS("weight_reps"),           // peso + reps (press de banca)
    REPS_ONLY("reps_only"),               // solo reps (lagartijas inclinadas, abdominales)
    WEIGHTED_BODYWEIGHT("weighted_bodyweight"), // reps con lastre opcional (dominadas, fondos)
    ASSISTED_BODYWEIGHT("assisted_bodyweight"), // reps con asistencia (dominadas con banda)
    DURATION("duration");                  // tiempo (plancha)

    val usesWeight get() = this == WEIGHT_REPS || this == WEIGHTED_BODYWEIGHT || this == ASSISTED_BODYWEIGHT
    val usesReps get() = this != DURATION

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: WEIGHT_REPS
    }
}

enum class SetType(val code: Int, val short: String, val label: String) {
    NORMAL(0, "", "Normal"), WARMUP(1, "C", "Calentamiento"), DROP(2, "D", "Drop set"), FAILURE(3, "F", "Al fallo");
    fun next() = entries[(ordinal + 1) % entries.size]
    companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } ?: NORMAL }
}

/** Serie mínima para cálculos (pesos en kg). */
data class SetData(
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int? = null,
    val rpe: Double? = null,
    val type: SetType = SetType.NORMAL,
)

/** Una sesión pasada de un ejercicio (series efectivas completadas, en orden). */
data class SessionData(val epochDay: Int, val sets: List<SetData>) {
    val working get() = sets.filter { it.type != SetType.WARMUP }
    /** Series "rectas" para decidir la progresión: sin calentamiento ni drop sets (su peso depende del cansancio). */
    val straight get() = sets.filter { it.type != SetType.WARMUP && it.type != SetType.DROP }.ifEmpty { working }
}

// ---------------------------------------------------------------------------------------------
// 1RM estimado
// ---------------------------------------------------------------------------------------------
object OneRepMax {
    /**
     * Epley (1985): w·(1 + r/30) · Brzycki (1993): w·36/(37 − r).
     * Son precisas hasta ~10 reps (LeSuer et al. 1997; Mayhew et al. 2008); por encima de 12 no estimamos.
     * Para 2–10 reps usamos el promedio de ambas (Epley sobreestima un poco, Brzycki subestima).
     */
    fun estimate(weightKg: Double, reps: Int): Double? = when {
        !weightKg.isFinite() || weightKg <= 0 || weightKg > 1500.0 || reps <= 0 -> null
        reps == 1 -> weightKg
        reps <= 10 -> (weightKg * (1 + reps / 30.0) + weightKg * 36.0 / (37 - reps)) / 2
        reps <= 12 -> weightKg * (1 + reps / 30.0)
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// Motor de progresión: "te dice cuándo subir peso"
// ---------------------------------------------------------------------------------------------
enum class RecKind { FIRST_TIME, INCREASE_LOAD, ADD_REPS, HOLD, DELOAD, HARDER_VARIATION, LESS_ASSISTANCE, ADD_TIME }

data class Recommendation(
    val kind: RecKind,
    val title: String,
    val detail: String,
    val targetWeightKg: Double? = null,
    val targetReps: Int? = null,
    val targetDurationSec: Int? = null,
    val sources: List<String> = emptyList(),
)

data class ProgressionInput(
    val tracking: Tracking,
    val repMin: Int,
    val repMax: Int,
    val isLowerBody: Boolean,
    val equipment: String,
    val unit: WeightUnit,
    /** Sesiones previas, la MÁS RECIENTE primero. */
    val history: List<SessionData>,
)

object Sources {
    const val ACSM = "ACSM 2009 · Med Sci Sports Exerc 41(3):687-708"
    const val NSCA = "NSCA · regla 2×2 (Baechle & Earle)"
    const val PLOTKIN = "Plotkin et al. 2022 · PeerJ 10:e14142"
    const val HELMS = "Zourdos et al. 2016 · J Strength Cond Res 30(1):267-275"
    const val ROBINSON = "Robinson et al. 2024 · Sports Med 54:2209-2231"
    const val LESUER = "LeSuer et al. 1997 · J Strength Cond Res 11(4):211-213"
    const val SINGER = "Singer et al. 2024 · Front Sports Act Living 6:1429789"
    const val PELLAND = "Pelland et al. 2026 · Sports Med 56(2):481-505"
    const val SCHOENFELD_VOL = "Schoenfeld et al. 2017 · J Sports Sci 35(11):1073-1082"
    const val ZHANG = "Zhang et al. 2025 · Sports Med (superseries vs. series tradicionales)"

    /** Resumen legible para la pantalla "La ciencia detrás de Rupio". */
    val explained = listOf(
        ACSM to "Cuándo subir peso: +2–10 % cuando haces 1–2 reps por encima de tu objetivo.",
        PLOTKIN to "Subir repeticiones con el mismo peso da resultados similares a subir peso (8 semanas, entrenados).",
        ROBINSON to "El músculo crece más cuanto más cerca del fallo terminas la serie; la fuerza depende más de la carga.",
        HELMS to "Escala RPE basada en repeticiones en reserva (RIR): RPE 10 = 0 en reserva, RPE 9 = 1, etc.",
        SINGER to "Descansar más de 60 s entre series ayuda un poco al crecimiento; más de 90 s no mostró diferencias claras.",
        SCHOENFELD_VOL to "Relación dosis-respuesta: 10+ series por músculo a la semana se asocian con más crecimiento.",
        PELLAND to "Más volumen semanal = más crecimiento con rendimientos decrecientes; ejercicios secundarios cuentan como media serie.",
        ZHANG to "Las superseries acortan la sesión sin perder adaptaciones; agonista–antagonista permite más reps.",
        LESUER to "Las fórmulas de 1RM (Epley, Brzycki) son confiables con menos de ~10 repeticiones.",
    )
}

object Progression {

    /** Incremento mínimo realista según equipo y unidad (discos / mancuernas / pila de máquina). */
    fun minIncrementKg(equipment: String, unit: WeightUnit): Double = when (unit) {
        WeightUnit.LB -> WeightUnit.LB.toKg(if (equipment == "mancuernas" || equipment == "pesa_rusa") 5.0 else 5.0)
        WeightUnit.KG -> when (equipment) {
            "mancuernas", "pesa_rusa" -> 2.0
            "maquina", "polea" -> 2.5
            else -> 2.5 // barra: 1.25 kg por lado
        }
    }

    /** Redondea al múltiplo alcanzable en la unidad del usuario (p. ej. 5 lb). */
    fun roundToStep(kg: Double, stepKg: Double, up: Boolean? = null): Double {
        val n = kg / stepKg
        val k = when (up) { true -> ceil(n - 1e-9); false -> floor(n + 1e-9); null -> Math.round(n).toDouble() }
        return k * stepKg
    }

    fun recommend(raw: ProgressionInput): Recommendation {
        // Entradas imposibles (rangos negativos o invertidos, pesos infinitos) se sanean antes de decidir.
        val lo = raw.repMin.coerceIn(1, 100)
        val input = raw.copy(repMin = lo, repMax = raw.repMax.coerceIn(lo, 100), history = raw.history.map { s ->
            s.copy(sets = s.sets.map { it.copy(weightKg = it.weightKg?.takeIf { w -> w.isFinite() && kotlin.math.abs(w) <= 1500.0 }
                    // Asistidos: la ayuda se guarda en negativo. En los demás, un peso negativo es un error de captura.
                    ?.let { w -> if (raw.tracking == Tracking.ASSISTED_BODYWEIGHT) -kotlin.math.abs(w) else w.takeIf { it >= 0 } },
                reps = it.reps?.takeIf { r -> r in 0..1000 }, durationSec = it.durationSec?.takeIf { d -> d in 0..86_400 },
                rpe = it.rpe?.takeIf { r -> r.isFinite() && r in 1.0..10.0 }) })
        })
        val sessions = input.history.filter { it.straight.isNotEmpty() }
        if (sessions.isEmpty()) return firstTime(input)
        return when (input.tracking) {
            Tracking.WEIGHT_REPS, Tracking.WEIGHTED_BODYWEIGHT -> weighted(input, sessions)
            Tracking.REPS_ONLY -> repsOnly(input, sessions)
            Tracking.ASSISTED_BODYWEIGHT -> assisted(input, sessions)
            Tracking.DURATION -> duration(sessions)
        }
    }

    private fun firstTime(i: ProgressionInput) = when (i.tracking) {
        Tracking.DURATION -> Recommendation(RecKind.FIRST_TIME, "Primera vez", "Aguanta con buena técnica y anota tu tiempo; la próxima intentarás superarlo.")
        Tracking.REPS_ONLY -> Recommendation(RecKind.FIRST_TIME, "Primera vez", "Haz las reps que puedas con buena técnica, dejando 1–2 en reserva.", targetReps = i.repMin, sources = listOf(Sources.HELMS))
        else -> Recommendation(
            RecKind.FIRST_TIME, "Primera vez",
            "Elige un peso con el que llegues a ${i.repMin}–${i.repMax} reps sintiendo que te quedan 1–3 en reserva. Rupio ajustará desde ahí.",
            targetReps = i.repMin, sources = listOf(Sources.ACSM, Sources.HELMS),
        )
    }

    private fun weighted(i: ProgressionInput, sessions: List<SessionData>): Recommendation {
        val last = sessions.first()
        val topKg = last.straight.maxOf { it.weightKg ?: 0.0 }
        val atTop = last.straight.filter { (it.weightKg ?: 0.0) >= topKg - 0.01 }
        val minReps = atTop.minOf { it.reps ?: 0 }
        val lastSetReps = atTop.last().reps ?: 0
        val lastRpe = atTop.last().rpe
        val step = minIncrementKg(i.equipment, i.unit)
        val u = i.unit

        if (topKg <= 0.0 && i.tracking == Tracking.WEIGHTED_BODYWEIGHT) {
            // Sin lastre todavía: progresión por reps hasta el tope del rango, luego lastre.
            return if (minReps >= i.repMax) Recommendation(
                RecKind.INCREASE_LOAD, "Añade lastre",
                "Hiciste todas las series en ${i.repMax}+ reps con tu peso corporal. Agrega ${Fmt.weight(step, u)} (cinturón o mancuerna) y vuelve a ${i.repMin} reps.",
                targetWeightKg = step, targetReps = i.repMin, sources = listOf(Sources.ACSM, Sources.PLOTKIN),
            ) else Recommendation(
                RecKind.ADD_REPS, "Suma repeticiones",
                "Busca +1 rep en cada serie hasta llegar a ${i.repMax}. Progresar en reps funciona igual de bien que en peso.",
                targetReps = (minReps + 1).coerceAtMost(i.repMax), sources = listOf(Sources.PLOTKIN),
            )
        }

        // 1) Doble progresión: todas las series al peso tope llegaron al máximo del rango.
        val allHitTop = atTop.all { (it.reps ?: 0) >= i.repMax }
        // 2) Regla 2×2 (NSCA): 2+ reps por encima del objetivo en la última serie, 2 sesiones seguidas al mismo peso.
        val twoForTwo = sessions.size >= 2 && sessions.take(2).all { s ->
            val sTop = s.straight.maxOf { it.weightKg ?: 0.0 }
            val sLast = s.straight.last { (it.weightKg ?: 0.0) >= sTop - 0.01 }
            kotlin.math.abs(sTop - topKg) < 0.01 && (sLast.reps ?: 0) >= i.repMax + 2
        }
        val tooHard = lastRpe != null && lastRpe >= 9.5 && !allHitTop

        if ((allHitTop || twoForTwo) && !tooHard) {
            // ACSM: subir 2–10 % cuando superas el objetivo por 1–2 reps.
            // NSCA: tren superior ~2.5–5 %, tren inferior ~5–10 %. Usamos el extremo conservador.
            val pct = if (i.isLowerBody) 0.05 else 0.025
            val raw = topKg * (1 + pct)
            var next = roundToStep(raw, step, up = false)
            if (next < topKg + step - 1e-6) next = roundToStep(topKg + step, step)
            val why = if (allHitTop) "Completaste todas tus series en ${i.repMax}+ reps con ${Fmt.weight(topKg, u)}."
                      else "Llevas 2 sesiones haciendo ${i.repMax + 2}+ reps en la última serie con ${Fmt.weight(topKg, u)}."
            return Recommendation(
                RecKind.INCREASE_LOAD, "Sube a ${Fmt.weight(next, u)}",
                "$why Sube el peso (+${Fmt.weight(next - topKg, u)}) y vuelve a ${i.repMin} reps.",
                targetWeightKg = next, targetReps = i.repMin, sources = listOf(Sources.ACSM, Sources.NSCA),
            )
        }

        // 3) Bajar: 2 sesiones seguidas al mismo peso por debajo del mínimo del rango.
        val failing = sessions.size >= 2 && sessions.take(2).all { s ->
            val sTop = s.straight.maxOf { it.weightKg ?: 0.0 }
            kotlin.math.abs(sTop - topKg) < 0.01 && s.straight.filter { (it.weightKg ?: 0.0) >= sTop - 0.01 }.any { (it.reps ?: 0) < i.repMin }
        }
        // 4) Estancamiento: el 1RM estimado no mejora en 4 sesiones.
        val stalled = sessions.size >= 4 && run {
            val e = sessions.take(4).map { s -> s.straight.mapNotNull { st -> st.weightKg?.let { w -> st.reps?.let { r -> OneRepMax.estimate(w, r) } } }.maxOrNull() ?: 0.0 }
            e[0] <= e[3] * 1.005 && e[0] <= e[1] && e[1] <= e[2] * 1.01
        }
        if (failing || stalled) {
            val down = roundToStep(topKg * 0.9, step, up = false).coerceAtLeast(0.0)
            return Recommendation(
                RecKind.DELOAD, "Baja a ${Fmt.weight(down, u)}",
                if (failing) "Dos sesiones seguidas quedaste por debajo de ${i.repMin} reps. Baja ~10 % y reconstruye con buena técnica; revisa descanso y sueño."
                else "Tu fuerza estimada no ha subido en 4 sesiones. Una descarga de ~10 % suele destrabar el progreso.",
                targetWeightKg = down, targetReps = i.repMax, sources = listOf(Sources.ACSM),
            )
        }

        // 5) Progresión por repeticiones: mismo peso, +1 rep en la serie más floja.
        val target = (minReps + 1).coerceIn(i.repMin, i.repMax)
        return Recommendation(
            RecKind.ADD_REPS, "Mantén ${Fmt.weight(topKg, u)}",
            "Busca $target reps en todas las series (hoy tu serie más floja fue de $minReps). Cuando todas lleguen a ${i.repMax}, Rupio te pedirá subir peso.",
            targetWeightKg = topKg, targetReps = target, sources = listOf(Sources.PLOTKIN, Sources.ROBINSON),
        )
    }

    private fun repsOnly(i: ProgressionInput, sessions: List<SessionData>): Recommendation {
        val last = sessions.first().straight
        val minReps = last.minOf { it.reps ?: 0 }
        return if (minReps >= i.repMax) Recommendation(
            RecKind.HARDER_VARIATION, "¡Dominado! Hazlo más difícil",
            "Hiciste todas las series en ${i.repMax}+ reps. Pasa a una variante más difícil o añade peso para seguir progresando.",
            targetReps = i.repMax, sources = listOf(Sources.PLOTKIN, Sources.ROBINSON),
        ) else Recommendation(
            RecKind.ADD_REPS, "Suma 1 repetición",
            "Intenta ${minReps + 1} reps en cada serie. Aumentar repeticiones con la misma carga también es sobrecarga progresiva.",
            targetReps = minReps + 1, sources = listOf(Sources.PLOTKIN),
        )
    }

    private fun assisted(i: ProgressionInput, sessions: List<SessionData>): Recommendation {
        val last = sessions.first().straight
        val assist = last.maxOf { -(it.weightKg ?: 0.0) }.coerceAtLeast(0.0)
        val minReps = last.minOf { it.reps ?: 0 }
        val step = minIncrementKg("maquina", i.unit)
        return if (minReps >= i.repMax && assist > 0) {
            val next = (assist - step).coerceAtLeast(0.0)
            Recommendation(
                RecKind.LESS_ASSISTANCE, if (next == 0.0) "¡Intenta sin asistencia!" else "Menos asistencia: ${Fmt.weight(next, i.unit)}",
                "Llegaste a ${i.repMax} reps en todas las series. Reduce la ayuda y vuelve a ${i.repMin} reps.",
                targetWeightKg = -next, targetReps = i.repMin, sources = listOf(Sources.ACSM),
            )
        } else Recommendation(
            RecKind.ADD_REPS, "Suma repeticiones", "Misma asistencia, busca ${(minReps + 1).coerceAtMost(i.repMax)} reps por serie.",
            targetWeightKg = -assist, targetReps = (minReps + 1).coerceAtMost(i.repMax), sources = listOf(Sources.PLOTKIN),
        )
    }

    private fun duration(sessions: List<SessionData>): Recommendation {
        val best = sessions.first().straight.maxOf { it.durationSec ?: 0 }
        val target = max(best + 5, (best * 1.1).roundToInt())
        return Recommendation(RecKind.ADD_TIME, "Intenta ${Fmt.duration(target.toLong())}", "La última vez aguantaste ${Fmt.duration(best.toLong())}. Suma unos segundos manteniendo la técnica.", targetDurationSec = target)
    }
}

// ---------------------------------------------------------------------------------------------
// Descansos: superseries y totales de la rutina
// ---------------------------------------------------------------------------------------------
enum class SupersetRestMode(val code: Int, val label: String) {
    INDIVIDUAL(0, "Individual"), SHARED(1, "Compartido");
    companion object { fun of(code: Int) = if (code == 1) SHARED else INDIVIDUAL }
}

/** Ejercicio dentro de una rutina/entrenamiento visto por el planificador de descansos. */
data class PlanItem(
    val key: String,
    val sets: Int,
    val restSec: Int,
    val supersetId: String?,
    val supersetMode: SupersetRestMode,
    val supersetRestSec: Int?,
)

data class RestSummary(val totalRestSec: Int, val totalSets: Int, val estimatedSec: Int)

object RestPlanner {
    /** Tiempo medio de ejecución de una serie (incluye preparar el peso). */
    const val WORK_SEC_PER_SET = 40

    /** Agrupa elementos consecutivos con el mismo supersetId. */
    fun groups(items: List<PlanItem>): List<List<PlanItem>> {
        val out = mutableListOf<MutableList<PlanItem>>()
        for (it in items) {
            val lastGroup = out.lastOrNull()
            if (it.supersetId != null && lastGroup != null && lastGroup.first().supersetId == it.supersetId) lastGroup += it
            else out += mutableListOf(it)
        }
        return out
    }

    /**
     * Descanso total de una rutina. En superseries:
     * - Compartido: un solo descanso al terminar cada ronda (A1 → A2 → descanso).
     * - Individual: cada ejercicio usa su propio descanso tras su serie.
     * No cuenta el descanso tras la última serie del entrenamiento.
     */
    fun summarize(items: List<PlanItem>): RestSummary {
        val pauses = mutableListOf<Int>()
        var sets = 0
        for (g in groups(items)) {
            val rounds = g.maxOf { it.sets }
            for (r in 0 until rounds) {
                val active = g.filter { it.sets > r }
                sets += active.size
                if (g.size > 1 && g.first().supersetMode == SupersetRestMode.SHARED) {
                    pauses += (g.first().supersetRestSec ?: g.last().restSec)
                } else {
                    active.forEach { pauses += it.restSec }
                }
            }
        }
        if (pauses.isNotEmpty()) pauses.removeAt(pauses.lastIndex)
        val rest = pauses.sum()
        return RestSummary(rest, sets, rest + sets * WORK_SEC_PER_SET)
    }

    /**
     * ¿Cuánto descanso arrancar al completar la serie [setIndex] del ejercicio [item]?
     * [completedInRound] = cuántos miembros de la superserie ya completaron esa ronda (incluyéndolo).
     */
    fun restAfterSet(item: PlanItem, group: List<PlanItem>, setIndex: Int, completedInRound: Int): Int {
        if (group.size <= 1 || item.supersetId == null) return item.restSec
        return when (item.supersetMode) {
            SupersetRestMode.INDIVIDUAL -> item.restSec
            SupersetRestMode.SHARED -> {
                val membersInRound = group.count { it.sets > setIndex }
                if (completedInRound >= membersInRound) (item.supersetRestSec ?: item.restSec) else 0
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Récords personales
// ---------------------------------------------------------------------------------------------
/** Etiqueta en español de cada forma de registro. */
val Tracking.label: String get() = when (this) {
    Tracking.WEIGHT_REPS -> "Peso y repeticiones"
    Tracking.REPS_ONLY -> "Solo repeticiones"
    Tracking.WEIGHTED_BODYWEIGHT -> "Peso corporal (+ lastre opcional)"
    Tracking.ASSISTED_BODYWEIGHT -> "Asistido (menos ayuda = más difícil)"
    Tracking.DURATION -> "Tiempo"
}

/**
 * Superseries: ¿los ejercicios trabajan el mismo músculo principal?
 * Zhang et al. 2025: superseries del mismo patrón bajan el volumen total; agonista–antagonista permite más reps.
 */
fun sameMusclePair(primaryMuscles: List<String>): Boolean = primaryMuscles.size != primaryMuscles.toSet().size

enum class RecordKind(val code: Int, val label: String) {
    MAX_WEIGHT(1, "Peso máximo"), E1RM(2, "1RM estimado"), SET_VOLUME(3, "Mejor serie (volumen)"),
    MAX_REPS(4, "Más repeticiones"), MAX_DURATION(5, "Más tiempo");
    companion object { fun of(code: Int) = entries.first { it.code == code } }
}

object Records {
    /** Valores de récord que produce una serie (solo series efectivas). */
    fun candidates(tracking: Tracking, s: SetData): Map<RecordKind, Double> {
        if (s.type == SetType.WARMUP) return emptyMap()
        val out = mutableMapOf<RecordKind, Double>()
        val w = s.weightKg?.takeIf { it.isFinite() && it <= 1500.0 } ?: 0.0
        val r = s.reps?.takeIf { it in 0..1000 } ?: 0
        when (tracking) {
            Tracking.WEIGHT_REPS, Tracking.WEIGHTED_BODYWEIGHT -> {
                if (w > 0 && r > 0) {
                    out[RecordKind.MAX_WEIGHT] = w
                    OneRepMax.estimate(w, r)?.let { out[RecordKind.E1RM] = it }
                    out[RecordKind.SET_VOLUME] = w * r
                }
                if (r > 0) out[RecordKind.MAX_REPS] = r.toDouble()
            }
            Tracking.REPS_ONLY, Tracking.ASSISTED_BODYWEIGHT -> if (r > 0) out[RecordKind.MAX_REPS] = r.toDouble()
            Tracking.DURATION -> s.durationSec?.takeIf { it in 1..86_400 }?.let { out[RecordKind.MAX_DURATION] = it.toDouble() }
        }
        return out
    }

    /** Devuelve los tipos de récord que la serie supera. */
    fun beaten(tracking: Tracking, s: SetData, current: Map<RecordKind, Double>): Set<RecordKind> =
        candidates(tracking, s).filter { (k, v) -> v > (current[k] ?: 0.0) + 1e-6 }.keys
}
