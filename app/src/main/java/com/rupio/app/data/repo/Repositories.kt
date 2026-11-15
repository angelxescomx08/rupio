package com.rupio.app.data.repo

import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.core.searchKey
import com.rupio.app.data.local.*
import com.rupio.app.data.prefs.SettingsStore
import androidx.room.withTransaction
import com.rupio.app.domain.OneRepMax
import com.rupio.app.domain.SetType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ExerciseRepository(private val db: RupioDatabase, private val userId: () -> String?) {
    val all: Flow<List<ExerciseEntity>> = db.exercises().observeAll()
    fun observe(id: String) = db.exercises().observe(id)
    suspend fun get(id: String) = db.exercises().get(id)
    suspend fun getMany(ids: List<String>) = db.exercises().getMany(ids)

    suspend fun createCustom(name: String, muscle: String, equipment: String, tracking: String, lower: Boolean): String {
        val id = Ids.uuidV7(); val t = System.currentTimeMillis()
        db.exercises().upsert(listOf(ExerciseEntity(id, name.trim(), name.searchKey(), muscle, "", equipment, tracking, lower,
            cue = "Ejercicio personalizado", isCustom = true, ownerId = userId(), updatedAt = t, dirty = true)))
        return id
    }
}

class RoutineRepository(private val db: RupioDatabase, private val userId: () -> String?) {
    val routines: Flow<List<RoutineEntity>> = db.routines().observeRoutines()
    val allExercises: Flow<List<RoutineExerciseEntity>> = db.routines().observeAllExercises()
    suspend fun get(id: String) = db.routines().get(id)
    suspend fun exercises(id: String) = db.routines().exercises(id)

    suspend fun save(r: RoutineEntity, items: List<RoutineExerciseEntity>) {
        val t = System.currentTimeMillis()
        db.routines().saveRoutine(r.copy(updatedAt = t, dirty = true), items.mapIndexed { i, e -> e.copy(position = i, routineId = r.id, updatedAt = t, dirty = true) }, t)
    }

    suspend fun delete(id: String) = db.routines().softDelete(id, System.currentTimeMillis())

    suspend fun count() = db.routines().count()

    /** "Guardar como rutina" desde un entrenamiento del historial (series completadas = series objetivo). */
    suspend fun saveFromWorkout(workoutId: String): String? {
        val uid = userId() ?: return null
        val w = db.workouts().get(workoutId) ?: return null
        val ex = db.workouts().exercises(workoutId)
        val sets = db.workouts().sets(workoutId).filter { it.completed }
        val t = System.currentTimeMillis()
        val r = RoutineEntity(Ids.uuidV7(t), uid, w.name, null, db.routines().count(), t, t)
        val ssMap = ex.mapNotNull { it.supersetId }.distinct().associateWith { Ids.uuidV7(t) }
        save(r, ex.map { e ->
            val done = sets.filter { it.workoutExerciseId == e.id }.sortedBy { it.position }
            val plan = done.map { com.rupio.app.domain.PlanSet(it.weightKg, it.reps, it.durationSec, it.setType) }
            RoutineExerciseEntity(Ids.uuidV7(), r.id, uid, e.exerciseId, 0, e.supersetId?.let { ssMap[it] }, e.supersetRestMode,
                e.supersetRestSec, e.restSec, plan.size.coerceIn(1, 20), e.repMin, e.repMax, e.notes, t,
                setPlan = com.rupio.app.domain.SetPlans.encode(plan))
        })
        return r.id
    }

    /** Rutina de ejemplo para principiantes (solo si la pides desde Inicio; incluye una superserie para enseñar cómo funciona). */
    suspend fun seedSampleIfEmpty() {
        if (db.routines().count() > 0) return
        val uid = userId() ?: return
        val t = System.currentTimeMillis()
        val r = RoutineEntity(Ids.uuidV7(t), uid, "Cuerpo completo · Ejemplo", "Rutina de ejemplo. Edítala o crea la tuya.", 0, t, t)
        val ss = Ids.uuidV7(t)
        fun item(ex: String, sets: Int, mn: Int, mx: Int, rest: Int, superset: String? = null, mode: Int = 0, ssRest: Int? = null) =
            RoutineExerciseEntity(Ids.uuidV7(), r.id, uid, ex, 0, superset, mode, ssRest, rest, sets, mn, mx, updatedAt = t)
        save(r, listOf(
            item("sentadilla_goblet", 3, 8, 12, 120),
            item("press_de_banca_con_mancuernas", 3, 8, 12, 0, ss, 1, 90),
            item("remo_con_mancuerna_a_una_mano", 3, 8, 12, 0, ss, 1, 90),
            item("peso_muerto_rumano_con_mancuernas", 3, 8, 12, 120),
            item("elevaciones_laterales_con_mancuernas", 3, 10, 15, 60),
            item("plancha", 3, 1, 1, 60),
        ))
    }
}

// ------------------------------------------------------------------ estadísticas
enum class Period(val label: String, val short: String) {
    WEEK("Semana", "1S"), MONTH("Mes", "1M"), QUARTER("3 meses", "3M"), YEAR("Año", "1A"), ALL("Histórico", "Todo")
}

enum class Bucket { DAY, WEEK, MONTH, YEAR }

data class Bar(val label: String, val volumeKg: Double, val sets: Int, val workouts: Int, val start: LocalDate)
data class Totals(val volumeKg: Double, val sets: Int, val reps: Int, val workouts: Int, val avgDurationSec: Double)
data class E1rmSeries(val exerciseId: String, val name: String, val points: List<Pair<LocalDate, Double>>, val best: Double?)

/** Series por músculo con el método "fraccional" (Pelland et al. 2026): principal = 1, secundario = 0.5. */
data class MuscleLoad(val muscle: String, val sets: Double, val volumeKg: Double)

data class StatsSnapshot(
    val period: Period,
    val from: LocalDate,
    val to: LocalDate,
    val bucket: Bucket,
    val totals: Totals,
    val previous: Totals?,
    val bars: List<Bar>,
    val muscles: List<MuscleLoad>,
    val weeksInPeriod: Double,
    val trainingDays: Map<Int, Int>,
    val heatFrom: LocalDate,
    val topExercises: List<ExerciseUsage>,
)

/** Peso corporal: una fila por día; registrar de nuevo ese día la actualiza. */
class BodyWeightRepository(private val db: RupioDatabase, private val userId: () -> String?) {
    private val dao get() = db.bodyWeights()

    fun series(from: LocalDate, to: LocalDate): Flow<List<WeightPoint>> {
        val uid = userId() ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        val days = (to.toEpochDay() - from.toEpochDay()).toInt()
        return dao.observeSeries(uid, from.toEpochDay().toInt(), to.toEpochDay().toInt(), com.rupio.app.domain.WeightTrend.bucketFor(days))
    }

    fun recent(limit: Int = 1): Flow<List<BodyWeightEntity>> =
        userId()?.let { dao.observeRecent(it, limit) } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun page(before: Int, limit: Int = 60): List<BodyWeightEntity> = userId()?.let { dao.page(it, before, limit) } ?: emptyList()

    suspend fun firstDay(): LocalDate? = userId()?.let { dao.firstDay(it) }?.let { LocalDate.ofEpochDay(it.toLong()) }

    /** Tendencia completa (para "Tu progreso"): agrupada para que nunca pase de ~150 puntos. */
    suspend fun trendAll(today: LocalDate): List<Double> {
        val uid = userId() ?: return emptyList()
        val first = dao.firstDay(uid) ?: return emptyList()
        val to = today.toEpochDay().toInt()
        return com.rupio.app.domain.WeightTrend.smooth(dao.seriesOnce(uid, first, to, com.rupio.app.domain.WeightTrend.bucketFor(to - first)))
    }

    suspend fun log(day: LocalDate, kg: Double) {
        val uid = userId() ?: return
        if (!kg.isFinite()) return
        val clean = Math.round(kg.coerceIn(20.0, 400.0) * 100) / 100.0
        dao.upsert(BodyWeightEntity(uid, day.toEpochDay().toInt(), clean, System.currentTimeMillis(), deleted = false, dirty = true))
    }

    suspend fun delete(day: Int) {
        val uid = userId() ?: return
        val row = dao.get(uid, day) ?: return
        dao.upsert(row.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis()))
    }
}

class StatsRepository(private val db: RupioDatabase, private val settings: SettingsStore) {
    private val dao = db.stats()

    suspend fun snapshot(period: Period, zone: ZoneId): StatsSnapshot {
        val s = settings.current()
        val today = Dates.today(zone)
        val first = dao.firstDay()?.let { LocalDate.ofEpochDay(it.toLong()) } ?: today
        val (from, bucket) = when (period) {
            Period.WEEK -> today.minusDays(6) to Bucket.DAY
            Period.MONTH -> Dates.weekStart(today.minusDays(27), s.weekStart) to Bucket.WEEK
            Period.QUARTER -> Dates.weekStart(today.minusWeeks(12), s.weekStart) to Bucket.WEEK
            Period.YEAR -> today.minusMonths(11).withDayOfMonth(1) to Bucket.MONTH
            Period.ALL -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(today))
                if (months <= 24) first.withDayOfMonth(1) to Bucket.MONTH else first.withDayOfYear(1) to Bucket.YEAR
            }
        }
        val f = from.toEpochDay().toInt(); val t = today.toEpochDay().toInt()
        val rows = when (bucket) {
            Bucket.DAY -> dao.volumeByDay(f, t)
            Bucket.WEEK -> dao.volumeByWeek(f, t, Dates.weekShift(s.weekStart))
            Bucket.MONTH -> dao.volumeByMonth(f, t)
            Bucket.YEAR -> dao.volumeByYear(f, t)
        }.associateBy { it.bucket }
        val bars = buildBars(from, today, bucket, s.weekStart, rows)
        val tot = dao.totals(f, t)
        val totals = Totals(tot.volume, tot.sets, tot.reps, tot.workouts, dao.avgDuration(f, t))
        val previous = if (period != Period.ALL) {
            val days = ChronoUnit.DAYS.between(from, today) + 1
            val pf = from.minusDays(days).toEpochDay().toInt(); val pt = from.minusDays(1).toEpochDay().toInt()
            val p = dao.totals(pf, pt); Totals(p.volume, p.sets, p.reps, p.workouts, dao.avgDuration(pf, pt))
        } else null
        val heatFrom = Dates.weekStart(today.minusWeeks(17), s.weekStart)
        return StatsSnapshot(
            period, from, today, bucket, totals, previous, bars,
            muscleLoads(f, t),
            weeksInPeriod = (ChronoUnit.DAYS.between(from, today) + 1) / 7.0,
            trainingDays = dao.trainingDays(heatFrom.toEpochDay().toInt(), t).associate { it.localDate to it.n },
            heatFrom = heatFrom,
            // Ventana máx. de 1 año: siempre < 1 ms aunque tengas 14 años de historial (ver benchmark).
            // Si no entrenaste en el último año, el año previo a tu último entreno (no todo el historial: 44 ms → 1 ms).
            topExercises = dao.topExercises(maxOf(f, t - 365), 12).ifEmpty { dao.lastDay()?.let { dao.topExercises(it - 365, 12) }.orEmpty() },
        )
    }

    private fun buildBars(from: LocalDate, to: LocalDate, bucket: Bucket, weekStart: Int, rows: Map<String, BucketRow>): List<Bar> {
        val out = mutableListOf<Bar>()
        var d = when (bucket) {
            Bucket.DAY -> from
            Bucket.WEEK -> Dates.weekStart(from, weekStart)
            Bucket.MONTH -> from.withDayOfMonth(1)
            Bucket.YEAR -> from.withDayOfYear(1)
        }
        while (!d.isAfter(to)) {
            val key = when (bucket) {
                Bucket.DAY, Bucket.WEEK -> String.format("%07d", d.toEpochDay())
                Bucket.MONTH -> String.format("%04d-%02d", d.year, d.monthValue)
                Bucket.YEAR -> String.format("%04d", d.year)
            }
            val label = when (bucket) {
                Bucket.DAY -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, com.rupio.app.core.Fmt.locale).take(2).replaceFirstChar { it.uppercase() }
                Bucket.WEEK -> Dates.formatDayShort(d)
                Bucket.MONTH -> d.month.getDisplayName(java.time.format.TextStyle.SHORT, com.rupio.app.core.Fmt.locale).take(3).replaceFirstChar { it.uppercase() }
                Bucket.YEAR -> d.year.toString()
            }
            val r = rows[key]
            out += Bar(label, r?.volume ?: 0.0, r?.sets ?: 0, r?.workouts ?: 0, d)
            d = when (bucket) { Bucket.DAY -> d.plusDays(1); Bucket.WEEK -> d.plusWeeks(1); Bucket.MONTH -> d.plusMonths(1); Bucket.YEAR -> d.plusYears(1) }
        }
        return out
    }

    suspend fun muscleLoads(from: Int, to: Int): List<MuscleLoad> {
        val counts = dao.exerciseSetCounts(from, to)
        if (counts.isEmpty()) return emptyList()
        val ex = db.exercises().getMany(counts.map { it.exerciseId }).associateBy { it.id }
        val sets = HashMap<String, Double>(); val vol = HashMap<String, Double>()
        for (c in counts) {
            val e = ex[c.exerciseId] ?: continue
            sets[e.primaryMuscle] = (sets[e.primaryMuscle] ?: 0.0) + c.sets
            vol[e.primaryMuscle] = (vol[e.primaryMuscle] ?: 0.0) + c.volume
            e.secondaryMuscles.split(',').filter { it.isNotBlank() && it != e.primaryMuscle }.forEach { m ->
                sets[m] = (sets[m] ?: 0.0) + c.sets * 0.5
            }
        }
        return sets.map { (m, n) -> MuscleLoad(m, n, vol[m] ?: 0.0) }.sortedByDescending { it.sets }
    }

    /** "Tu progreso": todo en ~7 consultas agregadas (ninguna trae series sueltas al teléfono). */
    suspend fun progressStory(zone: ZoneId, bodyTrend: List<Double>): com.rupio.app.domain.ProgressStory {
        val s = settings.current()
        val today = Dates.today(zone).toEpochDay().toInt()
        // Por ejercicio: primeros 3 y últimos 8 días y la mejor serie de esos días (ver StatsDao).
        // Fuera del hilo principal: son ~3 consultas mínimas por ejercicio.
        val (bests, repsOnly, names) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val ids = dao.trainedExerciseIds()
            val exs = db.exercises().getMany(ids).associateBy { it.id }
            val repsIds = HashSet<String>()
            val out = ArrayList<com.rupio.app.data.local.DayBest>()
            for (id in ids) {
                val e = exs[id] ?: continue
                val tracking = com.rupio.app.domain.Tracking.of(e.tracking)
                if (tracking == com.rupio.app.domain.Tracking.DURATION) continue
                val days = (dao.firstDays(id, 3) + dao.lastDays(id, 8)).distinct()
                if (days.size < 2) continue
                if (tracking == com.rupio.app.domain.Tracking.REPS_ONLY) { repsIds += id; out += dao.repsBestsOn(id, days) }
                else out += dao.weightedBestsOn(id, days)
            }
            Triple(out, repsIds, exs.mapValues { it.value.name })
        }
        val shift = Dates.weekShift(s.weekStart)
        val days = dao.trainingWeeks(shift).map { it * 7 - shift }
        return com.rupio.app.domain.ProgressStoryBuilder.build(
            today = today,
            bests = bests,
            repsOnlyIds = repsOnly,
            names = names,
            total = dao.workoutTotals(0, today),
            last30 = dao.workoutTotals(today - 29, today),
            prev30 = dao.workoutTotals(today - 59, today - 30),
            recordsTotal = dao.recordsSince(0),
            records30 = dao.recordsSince(System.currentTimeMillis() - 30L * 86_400_000),
            trainingDays = days,
            weekShift = shift,
            bodyTrend = bodyTrend,
        )
    }

    fun recentRecords(limit: Int) = db.records().observeRecent(limit)
    fun recordsFor(exerciseId: String) = db.records().observeForExercise(exerciseId)

    /** Mejor valor por sesión para ejercicios sin peso (reps máximas o tiempo máximo), agregado en SQL. */
    suspend fun bestPerSession(exerciseId: String, duration: Boolean): List<Pair<LocalDate, Double>> =
        (if (duration) dao.bestDurationPerDay(exerciseId) else dao.bestRepsPerDay(exerciseId))
            .map { LocalDate.ofEpochDay(it.performedOn.toLong()) to it.best }

    /** Curva de 1RM estimado (mejor serie de cada sesión), agregada en SQL. */
    suspend fun e1rm(exerciseId: String, name: String, fromDay: Int = 0): E1rmSeries {
        val pts = dao.e1rmCurve(exerciseId, fromDay).map { LocalDate.ofEpochDay(it.performedOn.toLong()) to it.best }
        return E1rmSeries(exerciseId, name, pts, pts.maxOfOrNull { it.second })
    }

    /** Series recientes de un ejercicio (las últimas [limit]; suficiente para ~15 sesiones). */
    suspend fun exerciseHistory(exerciseId: String, limit: Int = 300): List<SessionSetRow> = db.workouts().previousSets(exerciseId, "-", limit)
}
