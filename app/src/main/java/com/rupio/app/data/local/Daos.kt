package com.rupio.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Nota: usamos @Upsert (INSERT ... ON CONFLICT DO UPDATE) y NUNCA REPLACE en tablas padre:
 * REPLACE borra y reinserta la fila → dispararía el ON DELETE CASCADE y perderías las series.
 */

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises WHERE is_custom = 0")
    suspend fun catalogCount(): Int

    @Upsert suspend fun upsert(items: List<ExerciseEntity>)

    @Query("SELECT * FROM exercises WHERE is_custom = 1 AND dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<ExerciseEntity>

    @Query("UPDATE exercises SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT id FROM exercises WHERE dirty = 1 AND id IN (:ids)")
    suspend fun dirtyIds(ids: List<String>): List<String>
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE deleted = 0 ORDER BY position, created_at")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun get(id: String): RoutineEntity?

    @Query("SELECT * FROM routine_exercises WHERE routine_id = :routineId AND deleted = 0 ORDER BY position")
    suspend fun exercises(routineId: String): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_exercises WHERE deleted = 0 ORDER BY routine_id, position")
    fun observeAllExercises(): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT COUNT(*) FROM routines WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert suspend fun upsertRoutine(r: RoutineEntity)
    @Upsert suspend fun upsertRoutines(r: List<RoutineEntity>)
    @Upsert suspend fun upsertExercises(items: List<RoutineExerciseEntity>)

    @Query("UPDATE routine_exercises SET deleted = 1, dirty = 1, updated_at = :now WHERE routine_id = :routineId AND id NOT IN (:keep)")
    suspend fun softDeleteOthers(routineId: String, keep: List<String>, now: Long)

    @Query("UPDATE routines SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Transaction
    suspend fun saveRoutine(r: RoutineEntity, items: List<RoutineExerciseEntity>, now: Long) {
        upsertRoutine(r)
        upsertExercises(items)
        softDeleteOthers(r.id, items.map { it.id }.ifEmpty { listOf("-") }, now)
    }

    @Query("SELECT * FROM routines WHERE dirty = 1 LIMIT :limit") suspend fun dirtyRoutines(limit: Int): List<RoutineEntity>
    @Query("SELECT * FROM routine_exercises WHERE dirty = 1 LIMIT :limit") suspend fun dirtyExercises(limit: Int): List<RoutineExerciseEntity>
    @Query("UPDATE routines SET dirty = 0 WHERE id = :id AND updated_at = :u") suspend fun cleanRoutine(id: String, u: Long)
    @Query("UPDATE routine_exercises SET dirty = 0 WHERE id = :id AND updated_at = :u") suspend fun cleanExercise(id: String, u: Long)
    @Query("SELECT id FROM routines WHERE dirty = 1 AND id IN (:ids)") suspend fun dirtyRoutineIds(ids: List<String>): List<String>
    @Query("SELECT id FROM routine_exercises WHERE dirty = 1 AND id IN (:ids)") suspend fun dirtyExerciseIds(ids: List<String>): List<String>
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE ended_at IS NULL AND deleted = 0 ORDER BY started_at DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE ended_at IS NULL AND deleted = 0 ORDER BY started_at DESC LIMIT 1")
    suspend fun active(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun get(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: String): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout_exercises WHERE workout_id = :workoutId AND deleted = 0 ORDER BY position")
    fun observeExercises(workoutId: String): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercises WHERE workout_id = :workoutId AND deleted = 0 ORDER BY position")
    suspend fun exercises(workoutId: String): List<WorkoutExerciseEntity>

    // Usa (workout_id, position) en workout_exercises y (workout_exercise_id, position) en workout_sets.
    @Query("""SELECT s.* FROM workout_sets s JOIN workout_exercises we ON we.id = s.workout_exercise_id
              WHERE we.workout_id = :workoutId AND s.deleted = 0 ORDER BY we.position, s.position""")
    fun observeSets(workoutId: String): Flow<List<WorkoutSetEntity>>

    @Query("""SELECT s.* FROM workout_sets s JOIN workout_exercises we ON we.id = s.workout_exercise_id
              WHERE we.workout_id = :workoutId AND s.deleted = 0 ORDER BY we.position, s.position""")
    suspend fun sets(workoutId: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE id = :id") suspend fun getSet(id: String): WorkoutSetEntity?

    /*
     * Récords de un ejercicio en UNA pasada (antes: traer sus ~6 000 series a Kotlin, 11 ms; ahora 3 ms).
     * Mismas reglas que Records.candidates: sin calentamientos; 1RM solo de 1 a 12 reps.
     * Cuenta también el entreno en curso (si borras otro entreno, no se pierden los récords de hoy).
     */
    @Query("""SELECT MAX(CASE WHEN weight_kg > 0 AND reps > 0 THEN weight_kg END) AS max_weight,
                     MAX(CASE WHEN weight_kg > 0 AND reps = 1 THEN weight_kg
                              WHEN weight_kg > 0 AND reps BETWEEN 2 AND 10 THEN (weight_kg * (1 + reps / 30.0) + weight_kg * 36.0 / (37 - reps)) / 2
                              WHEN weight_kg > 0 AND reps BETWEEN 11 AND 12 THEN weight_kg * (1 + reps / 30.0) END) AS e1rm,
                     MAX(CASE WHEN weight_kg > 0 AND reps > 0 THEN weight_kg * reps END) AS set_volume,
                     MAX(CASE WHEN reps > 0 THEN reps END) AS max_reps,
                     MAX(CASE WHEN duration_sec > 0 THEN duration_sec END) AS max_duration
              FROM workout_sets s
              WHERE exercise_id = :ex AND completed = 1 AND deleted = 0 AND set_type != 1
                AND EXISTS (SELECT 1 FROM workouts w WHERE w.id = s.workout_id AND w.deleted = 0)""")
    suspend fun recordMaxes(ex: String): RecordMaxes

    /** La serie (más antigua) que logró ese valor de récord; solo se busca si el récord cambió. */
    @Query("""SELECT id, completed_at FROM workout_sets s
              WHERE exercise_id = :ex AND completed = 1 AND deleted = 0 AND set_type != 1
                AND EXISTS (SELECT 1 FROM workouts w WHERE w.id = s.workout_id AND w.deleted = 0)
                AND (CASE :kind
                       WHEN 1 THEN CASE WHEN weight_kg > 0 AND reps > 0 THEN weight_kg END
                       WHEN 2 THEN CASE WHEN weight_kg > 0 AND reps = 1 THEN weight_kg
                                        WHEN weight_kg > 0 AND reps BETWEEN 2 AND 10 THEN (weight_kg * (1 + reps / 30.0) + weight_kg * 36.0 / (37 - reps)) / 2
                                        WHEN weight_kg > 0 AND reps BETWEEN 11 AND 12 THEN weight_kg * (1 + reps / 30.0) END
                       WHEN 3 THEN CASE WHEN weight_kg > 0 AND reps > 0 THEN weight_kg * reps END
                       WHEN 4 THEN reps
                       ELSE duration_sec END) >= :value - 0.000001
              ORDER BY completed_at LIMIT 1""")
    suspend fun recordHolder(ex: String, kind: Int, value: Double): SetRef?
    @Query("SELECT workout_id FROM workout_exercises WHERE id = :id") suspend fun workoutIdOf(id: String): String?

    /** Historial con keyset pagination (sin OFFSET): siempre O(página), no O(página × n). */
    @Query("""SELECT * FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL
              AND (local_date < :beforeDay OR (local_date = :beforeDay AND started_at < :beforeStart))
              ORDER BY local_date DESC, started_at DESC LIMIT :limit""")
    suspend fun historyPage(beforeDay: Int, beforeStart: Long, limit: Int): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL ORDER BY local_date DESC, started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WorkoutEntity>>

    /** Series de sesiones anteriores de un ejercicio (motor de progresión y columna "Anterior"). */
    @Query("""SELECT performed_on, workout_id, weight_kg, reps, duration_sec, rpe, set_type, position FROM workout_sets
              WHERE exercise_id = :exerciseId AND completed = 1 AND deleted = 0 AND workout_id != :excludeWorkout
              ORDER BY performed_on DESC, completed_at DESC LIMIT :limit""")
    suspend fun previousSets(exerciseId: String, excludeWorkout: String, limit: Int = 150): List<SessionSetRow>

    @Upsert suspend fun upsertWorkout(w: WorkoutEntity)
    @Upsert suspend fun upsertWorkouts(w: List<WorkoutEntity>)
    @Upsert suspend fun upsertExercise(e: WorkoutExerciseEntity)
    @Upsert suspend fun upsertExercises(e: List<WorkoutExerciseEntity>)
    @Upsert suspend fun upsertSet(s: WorkoutSetEntity)
    @Upsert suspend fun upsertSets(s: List<WorkoutSetEntity>)

    @Transaction
    suspend fun insertFull(w: WorkoutEntity, ex: List<WorkoutExerciseEntity>, sets: List<WorkoutSetEntity>) {
        upsertWorkout(w); upsertExercises(ex); upsertSets(sets)
    }

    @Query("UPDATE workouts SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id") suspend fun softDelete(id: String, now: Long)
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL") fun observeCount(): Flow<Int>

    /** Resumen de ejercicios de UNA página del historial en una sola consulta (sin N+1). */
    @Query("""SELECT we.workout_id AS workout_id, we.superset_id AS superset_id, e.name AS name, COUNT(s.id) AS sets
              FROM workout_exercises we JOIN exercises e ON e.id = we.exercise_id
              LEFT JOIN workout_sets s ON s.workout_exercise_id = we.id AND s.deleted = 0 AND s.completed = 1
              WHERE we.workout_id IN (:ids) AND we.deleted = 0
              GROUP BY we.id ORDER BY we.workout_id, we.position""")
    suspend fun exerciseSummaries(ids: List<String>): List<WorkoutExerciseSummary>

    // ---- sync
    @Query("SELECT * FROM workouts WHERE dirty = 1 AND ended_at IS NOT NULL LIMIT :limit") suspend fun dirtyWorkouts(limit: Int): List<WorkoutEntity>
    @Query("""SELECT we.* FROM workout_exercises we JOIN workouts w ON w.id = we.workout_id
              WHERE we.dirty = 1 AND w.ended_at IS NOT NULL LIMIT :limit""") suspend fun dirtyExercises(limit: Int): List<WorkoutExerciseEntity>
    @Query("""SELECT s.* FROM workout_sets s JOIN workouts w ON w.id = s.workout_id
              WHERE s.dirty = 1 AND w.ended_at IS NOT NULL LIMIT :limit""") suspend fun dirtySets(limit: Int): List<WorkoutSetEntity>
    @Query("UPDATE workouts SET dirty = 0 WHERE id = :id AND updated_at = :u") suspend fun cleanWorkout(id: String, u: Long)
    @Query("UPDATE workout_exercises SET dirty = 0 WHERE id = :id AND updated_at = :u") suspend fun cleanExercise(id: String, u: Long)
    @Query("UPDATE workout_sets SET dirty = 0 WHERE id = :id AND updated_at = :u") suspend fun cleanSet(id: String, u: Long)
    @Query("SELECT id FROM workouts WHERE dirty = 1 AND id IN (:ids)") suspend fun dirtyWorkoutIds(ids: List<String>): List<String>
    @Query("SELECT id FROM workout_exercises WHERE dirty = 1 AND id IN (:ids)") suspend fun dirtyExerciseIds(ids: List<String>): List<String>
    @Query("SELECT id FROM workout_sets WHERE dirty = 1 AND id IN (:ids)") suspend fun dirtySetIds(ids: List<String>): List<String>
    @Query("""SELECT (SELECT COUNT(*) FROM workouts WHERE dirty = 1 AND ended_at IS NOT NULL)
                   + (SELECT COUNT(*) FROM routines WHERE dirty = 1)
                   + (SELECT COUNT(*) FROM personal_records WHERE dirty = 1)""")
    fun observePending(): Flow<Int>
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM personal_records WHERE exercise_id = :exerciseId")
    suspend fun forExercise(exerciseId: String): List<PersonalRecordEntity>

    @Query("SELECT * FROM personal_records WHERE exercise_id = :exerciseId")
    fun observeForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>

    @Query("""SELECT r.exercise_id, r.kind, r.value, r.achieved_at, e.name, e.tracking FROM personal_records r
              JOIN exercises e ON e.id = r.exercise_id ORDER BY r.achieved_at DESC LIMIT :limit""")
    fun observeRecent(limit: Int): Flow<List<RecordWithName>>

    @Upsert suspend fun upsert(items: List<PersonalRecordEntity>)

    @Query("DELETE FROM personal_records WHERE exercise_id = :ex AND kind = :kind") suspend fun delete(ex: String, kind: Int)

    /** Ejercicios con récords o con series completadas (para recalcular todo una vez). */
    @Query("SELECT exercise_id FROM personal_records UNION SELECT DISTINCT exercise_id FROM workout_sets WHERE completed = 1")
    suspend fun allExerciseIds(): List<String>

    @Query("SELECT * FROM personal_records WHERE dirty = 1 LIMIT :limit") suspend fun dirty(limit: Int): List<PersonalRecordEntity>
    @Query("UPDATE personal_records SET dirty = 0 WHERE exercise_id = :ex AND kind = :kind AND updated_at = :u") suspend fun clean(ex: String, kind: Int, u: Long)
}

@Dao
interface BodyWeightDao {
    @Upsert suspend fun upsert(row: BodyWeightEntity)
    @Upsert suspend fun upsertAll(rows: List<BodyWeightEntity>)

    /**
     * Serie para la gráfica en UNA consulta: agrupa en cubetas de :bucket días (1 = diario, 7 = semanal…)
     * para que un rango de años no dibuje miles de puntos. Recorre la clave primaria (user_id, measured_on).
     */
    @Query("""SELECT MIN(measured_on) AS day, AVG(weight_kg) AS kg, COUNT(*) AS n FROM body_weights
              WHERE user_id = :uid AND measured_on BETWEEN :from AND :to AND deleted = 0
              GROUP BY (measured_on - :from) / :bucket ORDER BY day""")
    fun observeSeries(uid: String, from: Int, to: Int, bucket: Int): Flow<List<WeightPoint>>

    @Query("""SELECT MIN(measured_on) AS day, AVG(weight_kg) AS kg, COUNT(*) AS n FROM body_weights
              WHERE user_id = :uid AND measured_on BETWEEN :from AND :to AND deleted = 0
              GROUP BY (measured_on - :from) / :bucket ORDER BY day""")
    suspend fun seriesOnce(uid: String, from: Int, to: Int, bucket: Int): List<WeightPoint>

    /** Página del historial (keyset: sin OFFSET, siempre igual de rápida). */
    @Query("SELECT * FROM body_weights WHERE user_id = :uid AND deleted = 0 AND measured_on < :before ORDER BY measured_on DESC LIMIT :limit")
    suspend fun page(uid: String, before: Int, limit: Int): List<BodyWeightEntity>

    @Query("SELECT * FROM body_weights WHERE user_id = :uid AND deleted = 0 ORDER BY measured_on DESC LIMIT :limit")
    fun observeRecent(uid: String, limit: Int): Flow<List<BodyWeightEntity>>

    @Query("SELECT * FROM body_weights WHERE user_id = :uid AND measured_on = :day")
    suspend fun get(uid: String, day: Int): BodyWeightEntity?

    @Query("SELECT MIN(measured_on) FROM body_weights WHERE user_id = :uid AND deleted = 0")
    suspend fun firstDay(uid: String): Int?

    @Query("SELECT * FROM body_weights WHERE dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<BodyWeightEntity>

    @Query("UPDATE body_weights SET dirty = 0 WHERE user_id = :uid AND measured_on = :day AND updated_at = :updatedAt")
    suspend fun clean(uid: String, day: Int, updatedAt: Long)

    @Query("SELECT measured_on FROM body_weights WHERE user_id = :uid AND dirty = 1 AND measured_on IN (:days)")
    suspend fun dirtyDays(uid: String, days: List<Int>): List<Int>
}

@Dao
interface StatsDao {
    // Agrupar por semana con aritmética de epochDay: día − ((día + shift) % 7) = inicio de semana.
    @Query("""SELECT printf('%07d', local_date - ((local_date + :shift) % 7)) AS bucket,
                     SUM(total_volume_kg) AS volume, SUM(total_sets) AS sets, SUM(total_reps) AS reps, COUNT(*) AS workouts
              FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL AND local_date BETWEEN :from AND :to
              GROUP BY 1 ORDER BY 1""")
    suspend fun volumeByWeek(from: Int, to: Int, shift: Int): List<BucketRow>

    @Query("""SELECT printf('%07d', local_date) AS bucket, SUM(total_volume_kg) AS volume, SUM(total_sets) AS sets,
                     SUM(total_reps) AS reps, COUNT(*) AS workouts
              FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL AND local_date BETWEEN :from AND :to
              GROUP BY 1 ORDER BY 1""")
    suspend fun volumeByDay(from: Int, to: Int): List<BucketRow>

    @Query("""SELECT strftime('%Y-%m', local_date * 86400, 'unixepoch') AS bucket, SUM(total_volume_kg) AS volume,
                     SUM(total_sets) AS sets, SUM(total_reps) AS reps, COUNT(*) AS workouts
              FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL AND local_date BETWEEN :from AND :to
              GROUP BY 1 ORDER BY 1""")
    suspend fun volumeByMonth(from: Int, to: Int): List<BucketRow>

    @Query("""SELECT strftime('%Y', local_date * 86400, 'unixepoch') AS bucket, SUM(total_volume_kg) AS volume,
                     SUM(total_sets) AS sets, SUM(total_reps) AS reps, COUNT(*) AS workouts
              FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL AND local_date BETWEEN :from AND :to
              GROUP BY 1 ORDER BY 1""")
    suspend fun volumeByYear(from: Int, to: Int): List<BucketRow>

    @Query("""SELECT COALESCE(SUM(total_volume_kg),0) AS volume, COALESCE(SUM(total_sets),0) AS sets,
                     COALESCE(SUM(total_reps),0) AS reps, COUNT(*) AS workouts, '' AS bucket
              FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL AND local_date BETWEEN :from AND :to""")
    suspend fun totals(from: Int, to: Int): BucketRow

    @Query("""SELECT COALESCE(AVG(duration_sec),0) FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL
              AND local_date BETWEEN :from AND :to""")
    suspend fun avgDuration(from: Int, to: Int): Double

    /** Series efectivas por ejercicio en un rango (luego se reparten a músculos: principal 1 · secundario 0.5). */
    @Query("""SELECT exercise_id, COUNT(*) AS sets, SUM(MAX(COALESCE(weight_kg, 0), 0) * COALESCE(reps, 0)) AS volume
              FROM workout_sets WHERE performed_on BETWEEN :from AND :to AND completed = 1 AND deleted = 0 AND set_type != 1
              GROUP BY exercise_id""")
    suspend fun exerciseSetCounts(from: Int, to: Int): List<ExerciseSetCount>

    /*
     * Ejercicios más usados desde :from. Cuenta en workout_exercises (6 filas por entrenamiento) en vez de
     * workout_sets (24): al terminar se borran los ejercicios sin series completadas, así que cada fila = 1 sesión.
     * Benchmark (usuario extremo, 120 000 series): 58.6 ms → ver docs/RENDIMIENTO_SQL.md.
     */
    @Query("""SELECT we.exercise_id, e.name, COUNT(*) AS sessions
              FROM workouts w JOIN workout_exercises we ON we.workout_id = w.id JOIN exercises e ON e.id = we.exercise_id
              WHERE w.deleted = 0 AND w.ended_at IS NOT NULL AND w.local_date >= :from AND we.deleted = 0
                AND e.tracking IN ('weight_reps','weighted_bodyweight')
              GROUP BY we.exercise_id ORDER BY sessions DESC LIMIT :limit""")
    suspend fun topExercises(from: Int, limit: Int): List<ExerciseUsage>

    /*
     * Curva de 1RM agregada EN SQL (una fila por sesión en vez de todas las series).
     * Misma fórmula que OneRepMax.estimate: 1 rep = peso; 2–10 = promedio Epley/Brzycki; 11–12 = Epley; >12 no se estima.
     */
    @Query("""SELECT performed_on,
                     MAX(CASE WHEN reps = 1 THEN weight_kg
                              WHEN reps <= 10 THEN (weight_kg * (1 + reps / 30.0) + weight_kg * 36.0 / (37 - reps)) / 2
                              WHEN reps <= 12 THEN weight_kg * (1 + reps / 30.0) END) AS best,
                     MAX(weight_kg) AS top_weight
              FROM workout_sets
              WHERE exercise_id = :exerciseId AND completed = 1 AND deleted = 0 AND set_type != 1
                AND performed_on >= :from AND weight_kg > 0 AND reps > 0
              GROUP BY performed_on HAVING best IS NOT NULL ORDER BY performed_on""")
    suspend fun e1rmCurve(exerciseId: String, from: Int): List<E1rmPoint>

    @Query("""SELECT performed_on, MAX(reps) AS best, 0.0 AS top_weight FROM workout_sets
              WHERE exercise_id = :exerciseId AND completed = 1 AND deleted = 0 AND set_type != 1 AND reps > 0
              GROUP BY performed_on ORDER BY performed_on""")
    suspend fun bestRepsPerDay(exerciseId: String): List<E1rmPoint>

    @Query("""SELECT performed_on, MAX(duration_sec) AS best, 0.0 AS top_weight FROM workout_sets
              WHERE exercise_id = :exerciseId AND completed = 1 AND deleted = 0 AND set_type != 1 AND duration_sec > 0
              GROUP BY performed_on ORDER BY performed_on""")
    suspend fun bestDurationPerDay(exerciseId: String): List<E1rmPoint>

    @Query("""SELECT local_date, COUNT(*) AS n FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL
              AND local_date BETWEEN :from AND :to GROUP BY local_date""")
    suspend fun trainingDays(from: Int, to: Int): List<DayCount>

    /*
     * "Tu progreso" sin recorrer TODAS tus series (antes: 236 ms con 240 000 series; ahora ~4 ms en total).
     * 1) Ejercicios que has hecho: "loose index scan" (salta de ejercicio en ejercicio por el índice).
     * 2) Por ejercicio: sus primeros 3 y últimos 8 días (otra vez saltando por el índice).
     * 3) La mejor serie SOLO de esos días. EXISTS en vez de IN (SELECT …): no arma la lista de todos tus entrenos.
     * Sin funciones de ventana: Android 8 trae SQLite 3.18.
     */
    @Query("""WITH RECURSIVE ex(id) AS (
                SELECT MIN(exercise_id) FROM workout_sets
                UNION ALL SELECT (SELECT MIN(exercise_id) FROM workout_sets WHERE exercise_id > ex.id) FROM ex WHERE ex.id IS NOT NULL)
              SELECT id FROM ex WHERE id IS NOT NULL""")
    suspend fun trainedExerciseIds(): List<String>

    @Query("""WITH RECURSIVE d(day, n) AS (
                SELECT MAX(performed_on), 1 FROM workout_sets WHERE exercise_id = :ex AND completed = 1
                UNION ALL SELECT (SELECT MAX(performed_on) FROM workout_sets WHERE exercise_id = :ex AND completed = 1 AND performed_on < d.day), n + 1
                FROM d WHERE d.day IS NOT NULL AND n < :n)
              SELECT day FROM d WHERE day IS NOT NULL""")
    suspend fun lastDays(ex: String, n: Int): List<Int>

    @Query("""WITH RECURSIVE d(day, n) AS (
                SELECT MIN(performed_on), 1 FROM workout_sets WHERE exercise_id = :ex AND completed = 1
                UNION ALL SELECT (SELECT MIN(performed_on) FROM workout_sets WHERE exercise_id = :ex AND completed = 1 AND performed_on > d.day), n + 1
                FROM d WHERE d.day IS NOT NULL AND n < :n)
              SELECT day FROM d WHERE day IS NOT NULL""")
    suspend fun firstDays(ex: String, n: Int): List<Int>

    /**
     * Mejor serie por día (1RM estimado) de un ejercicio en ciertos días. weight/reps son de la MISMA fila del MAX.
     * Hasta 30 reps (Epley arriba de 10): para comparar a la MISMA persona en el tiempo basta un estimador constante,
     * y así quien entrena con muchas repeticiones también ve su progreso.
     */
    @Query("""SELECT exercise_id, performed_on,
                     MAX(CASE WHEN reps = 1 THEN weight_kg
                              WHEN reps <= 10 THEN (weight_kg * (1 + reps / 30.0) + weight_kg * 36.0 / (37 - reps)) / 2
                              ELSE weight_kg * (1 + reps / 30.0) END) AS e1rm,
                     weight_kg AS weight, reps
              FROM workout_sets s
              WHERE exercise_id = :ex AND completed = 1 AND performed_on IN (:days) AND deleted = 0 AND set_type NOT IN (1, 2)
                AND weight_kg > 0 AND reps BETWEEN 1 AND 30
                AND EXISTS (SELECT 1 FROM workouts w WHERE w.id = s.workout_id AND w.deleted = 0 AND w.ended_at IS NOT NULL)
              GROUP BY performed_on""")
    suspend fun weightedBestsOn(ex: String, days: List<Int>): List<DayBest>

    /** Igual para ejercicios de solo repeticiones: más reps por día. */
    @Query("""SELECT exercise_id, performed_on, NULL AS e1rm, NULL AS weight, MAX(reps) AS reps
              FROM workout_sets s
              WHERE exercise_id = :ex AND completed = 1 AND performed_on IN (:days) AND deleted = 0 AND set_type NOT IN (1, 2) AND reps > 0
                AND EXISTS (SELECT 1 FROM workouts w WHERE w.id = s.workout_id AND w.deleted = 0 AND w.ended_at IS NOT NULL)
              GROUP BY performed_on""")
    suspend fun repsBestsOn(ex: String, days: List<Int>): List<DayBest>

    /** Semanas con al menos un entreno (para la racha), ya agrupadas en SQL. */
    @Query("SELECT DISTINCT (local_date + :shift) / 7 FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL")
    suspend fun trainingWeeks(shift: Int): List<Int>

    @Query("""SELECT COUNT(*) AS n, COALESCE(SUM(total_volume_kg), 0) AS vol, COALESCE(SUM(total_sets), 0) AS sets, MIN(local_date) AS first
              FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL AND local_date BETWEEN :from AND :to""")
    suspend fun workoutTotals(from: Int, to: Int): Totals2

    @Query("SELECT COUNT(*) FROM personal_records WHERE achieved_at >= :sinceMs")
    suspend fun recordsSince(sinceMs: Long): Int

    @Query("SELECT MAX(local_date) FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL")
    suspend fun lastDay(): Int?

    @Query("SELECT MIN(local_date) FROM workouts WHERE deleted = 0 AND ended_at IS NOT NULL")
    suspend fun firstDay(): Int?
}

@Dao
interface AdminDao {
    @Query("DELETE FROM workouts") suspend fun clearWorkouts()
    @Query("DELETE FROM routines") suspend fun clearRoutines()
    @Query("DELETE FROM personal_records") suspend fun clearRecords()
    @Query("DELETE FROM exercises WHERE is_custom = 1") suspend fun clearCustomExercises()

}
