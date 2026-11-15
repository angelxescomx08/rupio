# Auditoría QA de SQL (SQLite 3.53.1, esquema Room v3)

Usuario extremo: 10,000 entrenamientos, 240,000 series, 6,153 pesos corporales, 120 rutinas, 300 ejercicios propios · base ≈ 117 MB · generada en 2.5 s.
Consultas en Daos.kt: **92** · sin parámetros en la auditoría: **0** 
Criterio: p95 ≤ 2.0 ms ✓ · ≤ 8.0 ms ⚠ · más ✗ (un teléfono medio es ~3–5× más lento que esta máquina).

Resultado: ✗ 0 · ⚠ 4 · escaneos completos de tablas grandes: 0

## Exactitud

- ✓ Totales = suma directa
- ✓ Volumen por semana suma el total
- ✓ Tu progreso rápido = cálculo completo (40 ejercicios: primera vez, ahora y estancado)
- ✓ Lista de ejercicios por índice = DISTINCT completo
- ✓ Semanas con entreno = cálculo directo
- ✓ Récords en una pasada = reglas de Records.candidates (15 ejercicios)
- ✓ Peso 20 años: ≤ 160 puntos y no se pierde ningún registro

## Todas las consultas

| Consulta | p50 ms | p95 ms | filas | | Plan |
|---|---:|---:|---:|---|---|
| `StatsDao.volumeByYear` | 3.350 | 3.467 | 26 | ⚠ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) · USE TEMP B-TREE FOR GROUP BY |
| `WorkoutDao.recordMaxes` | 3.185 | 3.272 | 1 | ⚠ | SEARCH s USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · SEARCH w EXISTS USING INDEX sqlite_autoindex_workou |
| `StatsDao.e1rmCurve` | 2.293 | 2.408 | 1412 | ⚠ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=? AND performed_on>?) |
| `StatsDao.exerciseSetCounts@1año` | 1.948 | 2.029 | 44 | ⚠ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (ANY(exercise_id) AND completed=? AND performed_on>? AND performed_on<?) |
| `StatsDao.bestRepsPerDay` | 1.701 | 1.840 | 1419 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) |
| `WorkoutDao.recordHolder` | 1.515 | 1.581 | 0 | ✓ | SEARCH s USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · SEARCH w EXISTS USING INDEX sqlite_autoindex_workou |
| `RoutineDao.observeAllExercises` | 1.288 | 1.346 | 1200 | ✓ | SCAN routine_exercises USING INDEX index_routine_exercises_routine_id_position |
| `StatsDao.topExercises` | 1.198 | 1.288 | 12 | ✓ | SEARCH w USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>?) · SEARCH we USING INDEX index_workout_exercises_workout_id_positi |
| `BodyWeightDao.seriesOnce` | 1.141 | 1.221 | 153 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?) · USE TEMP B-TREE FOR GROUP BY · USE TEMP B-TREE |
| `BodyWeightDao.observeSeries@20años` | 1.139 | 1.187 | 153 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?) · USE TEMP B-TREE FOR GROUP BY · USE TEMP B-TREE |
| `StatsDao.workoutTotals` | 1.091 | 1.157 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) |
| `StatsDao.trainingWeeks` | 0.992 | 1.123 | 1310 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) · USE TEMP B-TREE FOR DISTINCT |
| `StatsDao.bestDurationPerDay` | 0.953 | 1.013 | 0 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) |
| `ExerciseDao.observeAll` | 0.847 | 0.940 | 556 | ✓ | SCAN exercises · USE TEMP B-TREE FOR ORDER BY |
| `WorkoutDao.exerciseSummaries` | 0.425 | 0.583 | 180 | ✓ | SEARCH we USING INDEX index_workout_exercises_workout_id_position (workout_id=?) · SEARCH e USING INDEX sqlite_autoindex_exercises_1 (id=?) · SEARCH s USING IND |
| `WorkoutDao.observeCount` | 0.491 | 0.522 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) |
| `WorkoutDao.dirtyExerciseIds` | 0.302 | 0.375 | 0 | ✓ | SEARCH workout_exercises USING INDEX index_workout_exercises_dirty (dirty=?) |
| `RoutineDao.dirtyExerciseIds` | 0.324 | 0.364 | 30 | ✓ | SEARCH routine_exercises USING INDEX index_routine_exercises_dirty (dirty=?) |
| `WorkoutDao.dirtyWorkoutIds` | 0.312 | 0.348 | 3 | ✓ | SEARCH workouts USING INDEX index_workouts_dirty (dirty=?) |
| `WorkoutDao.dirtySetIds` | 0.276 | 0.315 | 0 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_dirty (dirty=?) |
| `AdminDao.clearWorkouts` | 0.217 | 0.271 | 0 | ✓ |  |
| `BodyWeightDao.dirtyDays` | 0.236 | 0.268 | 3 | ✓ | SEARCH body_weights USING INDEX index_body_weights_dirty (dirty=?) |
| `AdminDao.clearCustomExercises` | 0.168 | 0.228 | 0 | ✓ | SEARCH exercises USING INDEX index_exercises_is_custom_dirty (is_custom=?) |
| `StatsDao.exerciseSetCounts` | 0.173 | 0.206 | 41 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (ANY(exercise_id) AND completed=? AND performed_on>? AND performed_on<?) |
| `WorkoutDao.previousSets` | 0.144 | 0.169 | 150 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · USE TEMP B-TREE FOR LAST TERM OF ORDER B |
| `StatsDao.volumeByMonth` | 0.147 | 0.167 | 13 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) · USE TEMP B-TREE FOR GROUP BY |
| `RoutineDao.observeRoutines` | 0.099 | 0.116 | 120 | ✓ | SEARCH routines USING INDEX index_routines_deleted_position (deleted=?) · USE TEMP B-TREE FOR LAST TERM OF ORDER BY |
| `WorkoutDao.dirtySets` | 0.105 | 0.115 | 72 | ✓ | SEARCH s USING INDEX index_workout_sets_dirty (dirty=?) · SEARCH w USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `WorkoutDao.dirtyExercises` | 0.051 | 0.071 | 18 | ✓ | SEARCH we USING INDEX index_workout_exercises_dirty (dirty=?) · SEARCH w USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `WorkoutDao.observeRecent` | 0.053 | 0.071 | 40 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) |
| `ExerciseDao.getMany` | 0.050 | 0.068 | 30 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `WorkoutDao.historyPage` | 0.049 | 0.061 | 30 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date<?) |
| `BodyWeightDao.observeSeries` | 0.049 | 0.055 | 84 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?) · USE TEMP B-TREE FOR GROUP BY · USE TEMP B-TREE |
| `ExerciseDao.dirty` | 0.047 | 0.054 | 30 | ✓ | SEARCH exercises USING INDEX index_exercises_is_custom_dirty (is_custom=? AND dirty=?) |
| `StatsDao.trainingDays` | 0.042 | 0.050 | 127 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) |
| `WorkoutDao.historyPage@5000` | 0.047 | 0.050 | 30 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date<?) |
| `RoutineDao.dirtyExercises` | 0.041 | 0.047 | 30 | ✓ | SEARCH routine_exercises USING INDEX index_routine_exercises_dirty (dirty=?) |
| `BodyWeightDao.page` | 0.037 | 0.043 | 60 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on<?) |
| `WorkoutDao.sets` | 0.036 | 0.040 | 24 | ✓ | SEARCH we USING INDEX index_workout_exercises_workout_id_position (workout_id=?) · SEARCH s USING INDEX index_workout_sets_workout_exercise_id_position (workout |
| `RecordDao.observeRecent` | 0.031 | 0.039 | 10 | ✓ | SCAN r · SEARCH e USING INDEX sqlite_autoindex_exercises_1 (id=?) · USE TEMP B-TREE FOR ORDER BY |
| `WorkoutDao.observeSets` | 0.036 | 0.037 | 24 | ✓ | SEARCH we USING INDEX index_workout_exercises_workout_id_position (workout_id=?) · SEARCH s USING INDEX index_workout_sets_workout_exercise_id_position (workout |
| `StatsDao.volumeByWeek` | 0.035 | 0.037 | 14 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) · USE TEMP B-TREE FOR GROUP BY |
| `StatsDao.weightedBestsOn` | 0.029 | 0.031 | 11 | ✓ | SEARCH s USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=? AND performed_on=?) · SEARCH w EXISTS USING INDEX sqli |
| `RecordDao.allExerciseIds` | 0.026 | 0.030 | 44 | ✓ | MERGE (UNION) · LEFT · SCAN personal_records USING COVERING INDEX sqlite_autoindex_personal_records_1 · RIGHT · SCAN workout_sets USING COVERING INDEX index_wor |
| `StatsDao.repsBestsOn` | 0.025 | 0.028 | 11 | ✓ | SEARCH s USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=? AND performed_on=?) · SEARCH w EXISTS USING INDEX sqli |
| `WorkoutDao.dirtyWorkouts` | 0.013 | 0.018 | 3 | ✓ | SEARCH workouts USING INDEX index_workouts_dirty (dirty=?) |
| `StatsDao.totals` | 0.012 | 0.015 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) |
| `ExerciseDao.dirtyIds` | 0.014 | 0.015 | 0 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `RoutineDao.softDeleteOthers` | 0.008 | 0.014 | 0 | ✓ | SEARCH routine_exercises USING INDEX index_routine_exercises_routine_id_position (routine_id=?) |
| `RoutineDao.exercises` | 0.012 | 0.013 | 10 | ✓ | SEARCH routine_exercises USING INDEX index_routine_exercises_routine_id_position (routine_id=?) |
| `RoutineDao.dirtyRoutines` | 0.007 | 0.010 | 3 | ✓ | SEARCH routines USING INDEX index_routines_dirty (dirty=?) |
| `RoutineDao.dirtyRoutineIds` | 0.009 | 0.009 | 3 | ✓ | SEARCH routines USING INDEX index_routines_dirty (dirty=?) |
| `BodyWeightDao.dirty` | 0.006 | 0.009 | 3 | ✓ | SEARCH body_weights USING INDEX index_body_weights_dirty (dirty=?) |
| `BodyWeightDao.observeRecent` | 0.006 | 0.009 | 1 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=?) |
| `WorkoutDao.observeExercises` | 0.008 | 0.008 | 6 | ✓ | SEARCH workout_exercises USING INDEX index_workout_exercises_workout_id_position (workout_id=?) |
| `StatsDao.volumeByDay` | 0.008 | 0.008 | 7 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) · USE TEMP B-TREE FOR GROUP BY |
| `WorkoutDao.exercises` | 0.008 | 0.008 | 6 | ✓ | SEARCH workout_exercises USING INDEX index_workout_exercises_workout_id_position (workout_id=?) |
| `StatsDao.avgDuration` | 0.007 | 0.008 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) |
| `RecordDao.dirty` | 0.007 | 0.007 | 0 | ✓ | SCAN personal_records |
| `ExerciseDao.catalogCount` | 0.006 | 0.006 | 1 | ✓ | SEARCH exercises USING COVERING INDEX index_exercises_is_custom_dirty (is_custom=?) |
| `RoutineDao.cleanExercise` | 0.003 | 0.006 | 0 | ✓ | SEARCH routine_exercises USING INDEX sqlite_autoindex_routine_exercises_1 (id=?) |
| `RecordDao.delete` | 0.003 | 0.005 | 0 | ✓ | SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=? AND kind=?) |
| `WorkoutDao.hardDelete` | 0.005 | 0.005 | 0 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `WorkoutDao.observeActive` | 0.004 | 0.004 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_ended_at (ended_at=?) · USE TEMP B-TREE FOR ORDER BY |
| `WorkoutDao.active` | 0.004 | 0.004 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_ended_at (ended_at=?) · USE TEMP B-TREE FOR ORDER BY |
| `WorkoutDao.getSet` | 0.003 | 0.004 | 1 | ✓ | SEARCH workout_sets USING INDEX sqlite_autoindex_workout_sets_1 (id=?) |
| `AdminDao.clearRoutines` | 0.004 | 0.004 | 0 | ✓ |  |
| `RoutineDao.softDelete` | 0.003 | 0.004 | 0 | ✓ | SEARCH routines USING INDEX sqlite_autoindex_routines_1 (id=?) |
| `WorkoutDao.softDelete` | 0.003 | 0.003 | 0 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `ExerciseDao.get` | 0.003 | 0.003 | 1 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `ExerciseDao.observe` | 0.003 | 0.003 | 1 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `StatsDao.recordsSince` | 0.003 | 0.003 | 1 | ✓ | SCAN personal_records |
| `AdminDao.clearRecords` | 0.003 | 0.003 | 0 | ✓ |  |
| `RoutineDao.count` | 0.003 | 0.003 | 1 | ✓ | SEARCH routines USING COVERING INDEX index_routines_deleted_position (deleted=?) |
| `WorkoutDao.get` | 0.003 | 0.003 | 1 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `WorkoutDao.observe` | 0.003 | 0.003 | 1 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `ExerciseDao.markClean` | 0.002 | 0.003 | 0 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `StatsDao.trainedExerciseIds` | 0.002 | 0.003 | 0 | ✓ | CO-ROUTINE ex · SETUP · SEARCH workout_sets USING COVERING INDEX index_workout_sets_exercise_id_completed_performed_on · RECURSIVE STEP · SCAN ex · CORRELATED S |
| `BodyWeightDao.clean` | 0.002 | 0.003 | 0 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on=?) |
| `RecordDao.observeForExercise` | 0.002 | 0.003 | 2 | ✓ | SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=?) |
| `WorkoutDao.observePending` | 0.002 | 0.003 | 1 | ✓ | SCAN CONSTANT ROW · SCALAR SUBQUERY 1 · SEARCH workouts USING INDEX index_workouts_dirty (dirty=?) · SCALAR SUBQUERY 2 · SEARCH routines USING COVERING INDEX in |
| `RecordDao.forExercise` | 0.002 | 0.002 | 2 | ✓ | SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=?) |
| `StatsDao.firstDays` | 0.002 | 0.002 | 0 | ✓ | CO-ROUTINE d · SETUP · SEARCH workout_sets USING COVERING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · RECURSIV |
| `RoutineDao.cleanRoutine` | 0.002 | 0.002 | 0 | ✓ | SEARCH routines USING INDEX sqlite_autoindex_routines_1 (id=?) |
| `RecordDao.clean` | 0.002 | 0.002 | 0 | ✓ | SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=? AND kind=?) |
| `RoutineDao.get` | 0.002 | 0.002 | 1 | ✓ | SEARCH routines USING INDEX sqlite_autoindex_routines_1 (id=?) |
| `BodyWeightDao.get` | 0.002 | 0.002 | 1 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on=?) |
| `StatsDao.lastDays` | 0.002 | 0.002 | 0 | ✓ | CO-ROUTINE d · SETUP · SEARCH workout_sets USING COVERING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · RECURSIV |
| `WorkoutDao.cleanExercise` | 0.001 | 0.002 | 0 | ✓ | SEARCH workout_exercises USING INDEX sqlite_autoindex_workout_exercises_1 (id=?) |
| `WorkoutDao.cleanSet` | 0.001 | 0.002 | 0 | ✓ | SEARCH workout_sets USING INDEX sqlite_autoindex_workout_sets_1 (id=?) |
| `BodyWeightDao.firstDay` | 0.001 | 0.001 | 1 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=?) |
| `WorkoutDao.workoutIdOf` | 0.001 | 0.001 | 1 | ✓ | SEARCH workout_exercises USING INDEX sqlite_autoindex_workout_exercises_1 (id=?) |
| `StatsDao.lastDay` | 0.001 | 0.001 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) |
| `StatsDao.firstDay` | 0.001 | 0.001 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) |
| `WorkoutDao.cleanWorkout` | 0.001 | 0.001 | 0 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
