# Auditoría QA de SQL (SQLite 3.53.1, esquema Room v3)

Usuario extremo: 10,000 entrenamientos, 240,000 series, 6,153 pesos corporales, 120 rutinas, 300 ejercicios propios · base ≈ 117 MB · generada en 3.0 s.
Consultas en Daos.kt: **95** · sin parámetros en la auditoría: **0** 
Criterio: p95 ≤ 2.0 ms ✓ · ≤ 8.0 ms ⚠ · más ✗ (un teléfono medio es ~3–5× más lento que esta máquina).

Resultado: ✗ 0 · ⚠ 8 · escaneos completos de tablas grandes: 0

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
| `WorkoutDao.recordMaxes` | 4.545 | 5.744 | 1 | ⚠ | SEARCH s USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · SEARCH w EXISTS USING INDEX sqlite_autoindex_workou |
| `StatsDao.volumeByYear` | 4.768 | 4.980 | 26 | ⚠ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) · USE TEMP B-TREE FOR GROUP BY |
| `StatsDao.e1rmCurve` | 3.096 | 3.565 | 1412 | ⚠ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=? AND performed_on>?) |
| `StatsDao.bestRepsPerDay` | 2.889 | 2.937 | 1419 | ⚠ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) |
| `StatsDao.exerciseSetCounts@1año` | 2.816 | 2.872 | 44 | ⚠ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (ANY(exercise_id) AND completed=? AND performed_on>? AND performed_on<?) |
| `WorkoutDao.recordHolder` | 2.471 | 2.537 | 0 | ⚠ | SEARCH s USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · SEARCH w EXISTS USING INDEX sqlite_autoindex_workou |
| `StatsDao.topExercises` | 2.162 | 2.311 | 12 | ⚠ | SEARCH w USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>?) · SEARCH we USING INDEX index_workout_exercises_workout_id_positi |
| `StatsDao.trainingWeeks` | 1.848 | 2.284 | 1310 | ⚠ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) · USE TEMP B-TREE FOR DISTINCT |
| `StatsDao.bestDurationPerDay` | 1.822 | 1.874 | 0 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) |
| `RoutineDao.observeAllExercises` | 1.681 | 1.738 | 1200 | ✓ | SCAN routine_exercises USING INDEX index_routine_exercises_routine_id_position |
| `BodyWeightDao.observeSeries@20años` | 1.357 | 1.387 | 153 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?) · USE TEMP B-TREE FOR GROUP BY · USE TEMP B-TREE |
| `BodyWeightDao.seriesOnce` | 1.351 | 1.387 | 153 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?) · USE TEMP B-TREE FOR GROUP BY · USE TEMP B-TREE |
| `StatsDao.workoutTotals` | 1.151 | 1.212 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) |
| `ExerciseDao.observeAll` | 0.883 | 1.107 | 556 | ✓ | SCAN exercises · USE TEMP B-TREE FOR ORDER BY |
| `WorkoutDao.observeCount` | 0.661 | 0.790 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) |
| `WorkoutDao.exerciseSummaries` | 0.496 | 0.523 | 180 | ✓ | SEARCH we USING INDEX index_workout_exercises_workout_id_position (workout_id=?) · SEARCH e USING INDEX sqlite_autoindex_exercises_1 (id=?) · SEARCH s USING IND |
| `WorkoutDao.existingWorkoutIds` | 0.445 | 0.468 | 400 | ✓ | SEARCH workouts USING COVERING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `AdminDao.clearWorkouts` | 0.391 | 0.439 | 0 | ✓ |  |
| `WorkoutDao.dirtyWorkoutIds` | 0.365 | 0.383 | 3 | ✓ | SEARCH workouts USING INDEX index_workouts_dirty (dirty=?) |
| `RoutineDao.dirtyExerciseIds` | 0.325 | 0.359 | 30 | ✓ | SEARCH routine_exercises USING INDEX index_routine_exercises_dirty (dirty=?) |
| `WorkoutDao.dirtySetIds` | 0.311 | 0.333 | 0 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_dirty (dirty=?) |
| `WorkoutDao.dirtyExerciseIds` | 0.294 | 0.322 | 0 | ✓ | SEARCH workout_exercises USING INDEX index_workout_exercises_dirty (dirty=?) |
| `BodyWeightDao.dirtyDays` | 0.239 | 0.262 | 3 | ✓ | SEARCH body_weights USING INDEX index_body_weights_dirty (dirty=?) |
| `StatsDao.volumeByMonth` | 0.162 | 0.227 | 13 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) · USE TEMP B-TREE FOR GROUP BY |
| `AdminDao.clearCustomExercises` | 0.166 | 0.210 | 0 | ✓ | SEARCH exercises USING INDEX index_exercises_is_custom_dirty (is_custom=?) |
| `WorkoutDao.previousSets` | 0.187 | 0.210 | 150 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · USE TEMP B-TREE FOR LAST TERM OF ORDER B |
| `StatsDao.exerciseSetCounts` | 0.176 | 0.198 | 41 | ✓ | SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (ANY(exercise_id) AND completed=? AND performed_on>? AND performed_on<?) |
| `WorkoutDao.dirtySets` | 0.134 | 0.159 | 72 | ✓ | SEARCH s USING INDEX index_workout_sets_dirty (dirty=?) · SEARCH w USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `RoutineDao.observeRoutines` | 0.100 | 0.116 | 120 | ✓ | SEARCH routines USING INDEX index_routines_deleted_position (deleted=?) · USE TEMP B-TREE FOR LAST TERM OF ORDER BY |
| `BodyWeightDao.observeSeries` | 0.063 | 0.084 | 84 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?) · USE TEMP B-TREE FOR GROUP BY · USE TEMP B-TREE |
| `AdminDao.clearBodyWeights` | 0.057 | 0.084 | 0 | ✓ |  |
| `StatsDao.trainingDays` | 0.066 | 0.077 | 127 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) |
| `WorkoutDao.observeRecent` | 0.068 | 0.073 | 40 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) |
| `ExerciseDao.getMany` | 0.064 | 0.072 | 30 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `WorkoutDao.sets` | 0.060 | 0.071 | 24 | ✓ | SEARCH we USING INDEX index_workout_exercises_workout_id_position (workout_id=?) · SEARCH s USING INDEX index_workout_sets_workout_exercise_id_position (workout |
| `BodyWeightDao.page` | 0.050 | 0.070 | 60 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on<?) |
| `WorkoutDao.historyPage@5000` | 0.048 | 0.068 | 30 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date<?) |
| `ExerciseDao.dirty` | 0.060 | 0.066 | 30 | ✓ | SEARCH exercises USING INDEX index_exercises_is_custom_dirty (is_custom=? AND dirty=?) |
| `WorkoutDao.historyPage` | 0.048 | 0.055 | 30 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date<?) |
| `WorkoutDao.observeSets` | 0.046 | 0.051 | 24 | ✓ | SEARCH we USING INDEX index_workout_exercises_workout_id_position (workout_id=?) · SEARCH s USING INDEX index_workout_sets_workout_exercise_id_position (workout |
| `RoutineDao.dirtyExercises` | 0.041 | 0.051 | 30 | ✓ | SEARCH routine_exercises USING INDEX index_routine_exercises_dirty (dirty=?) |
| `StatsDao.volumeByWeek` | 0.047 | 0.049 | 14 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) · USE TEMP B-TREE FOR GROUP BY |
| `WorkoutDao.dirtyExercises` | 0.038 | 0.044 | 18 | ✓ | SEARCH we USING INDEX index_workout_exercises_dirty (dirty=?) · SEARCH w USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `RecordDao.allExerciseIds` | 0.026 | 0.034 | 44 | ✓ | MERGE (UNION) · LEFT · SCAN personal_records USING COVERING INDEX sqlite_autoindex_personal_records_1 · RIGHT · SCAN workout_sets USING COVERING INDEX index_wor |
| `StatsDao.repsBestsOn` | 0.032 | 0.033 | 11 | ✓ | SEARCH s USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=? AND performed_on=?) · SEARCH w EXISTS USING INDEX sqli |
| `StatsDao.weightedBestsOn` | 0.029 | 0.032 | 11 | ✓ | SEARCH s USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=? AND performed_on=?) · SEARCH w EXISTS USING INDEX sqli |
| `StatsDao.totals` | 0.019 | 0.031 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) |
| `RecordDao.observeRecent` | 0.027 | 0.030 | 10 | ✓ | SCAN r · SEARCH e USING INDEX sqlite_autoindex_exercises_1 (id=?) · USE TEMP B-TREE FOR ORDER BY |
| `RoutineDao.existingIds` | 0.016 | 0.022 | 30 | ✓ | SEARCH routines USING COVERING INDEX sqlite_autoindex_routines_1 (id=?) |
| `ExerciseDao.dirtyIds` | 0.018 | 0.021 | 0 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `WorkoutDao.dirtyWorkouts` | 0.016 | 0.017 | 3 | ✓ | SEARCH workouts USING INDEX index_workouts_dirty (dirty=?) |
| `WorkoutDao.hardDelete` | 0.005 | 0.013 | 0 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `WorkoutDao.exercises` | 0.010 | 0.013 | 6 | ✓ | SEARCH workout_exercises USING INDEX index_workout_exercises_workout_id_position (workout_id=?) |
| `BodyWeightDao.observeRecent` | 0.008 | 0.013 | 1 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=?) |
| `RoutineDao.exercises` | 0.012 | 0.013 | 10 | ✓ | SEARCH routine_exercises USING INDEX index_routine_exercises_routine_id_position (routine_id=?) |
| `RoutineDao.softDeleteOthers` | 0.008 | 0.011 | 0 | ✓ | SEARCH routine_exercises USING INDEX index_routine_exercises_routine_id_position (routine_id=?) |
| `WorkoutDao.observeExercises` | 0.010 | 0.010 | 6 | ✓ | SEARCH workout_exercises USING INDEX index_workout_exercises_workout_id_position (workout_id=?) |
| `RoutineDao.dirtyRoutineIds` | 0.009 | 0.010 | 3 | ✓ | SEARCH routines USING INDEX index_routines_dirty (dirty=?) |
| `BodyWeightDao.dirty` | 0.008 | 0.010 | 3 | ✓ | SEARCH body_weights USING INDEX index_body_weights_dirty (dirty=?) |
| `StatsDao.avgDuration` | 0.008 | 0.008 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) |
| `RecordDao.dirty` | 0.007 | 0.008 | 0 | ✓ | SCAN personal_records |
| `RoutineDao.dirtyRoutines` | 0.007 | 0.008 | 3 | ✓ | SEARCH routines USING INDEX index_routines_dirty (dirty=?) |
| `StatsDao.volumeByDay` | 0.007 | 0.007 | 7 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) · USE TEMP B-TREE FOR GROUP BY |
| `ExerciseDao.catalogCount` | 0.006 | 0.007 | 1 | ✓ | SEARCH exercises USING COVERING INDEX index_exercises_is_custom_dirty (is_custom=?) |
| `WorkoutDao.softDelete` | 0.005 | 0.006 | 0 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `RoutineDao.cleanExercise` | 0.003 | 0.005 | 0 | ✓ | SEARCH routine_exercises USING INDEX sqlite_autoindex_routine_exercises_1 (id=?) |
| `WorkoutDao.active` | 0.004 | 0.005 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_ended_at (ended_at=?) · USE TEMP B-TREE FOR ORDER BY |
| `RecordDao.delete` | 0.004 | 0.004 | 0 | ✓ | SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=? AND kind=?) |
| `RoutineDao.softDelete` | 0.003 | 0.004 | 0 | ✓ | SEARCH routines USING INDEX sqlite_autoindex_routines_1 (id=?) |
| `WorkoutDao.observeActive` | 0.004 | 0.004 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_ended_at (ended_at=?) · USE TEMP B-TREE FOR ORDER BY |
| `AdminDao.clearRecords` | 0.003 | 0.004 | 0 | ✓ |  |
| `RoutineDao.count` | 0.003 | 0.004 | 1 | ✓ | SEARCH routines USING COVERING INDEX index_routines_deleted_position (deleted=?) |
| `StatsDao.recordsSince` | 0.004 | 0.004 | 1 | ✓ | SCAN personal_records |
| `ExerciseDao.get` | 0.004 | 0.004 | 1 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `ExerciseDao.observe` | 0.004 | 0.004 | 1 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `AdminDao.clearRoutines` | 0.004 | 0.004 | 0 | ✓ |  |
| `ExerciseDao.markClean` | 0.003 | 0.004 | 0 | ✓ | SEARCH exercises USING INDEX sqlite_autoindex_exercises_1 (id=?) |
| `RoutineDao.cleanRoutine` | 0.002 | 0.003 | 0 | ✓ | SEARCH routines USING INDEX sqlite_autoindex_routines_1 (id=?) |
| `StatsDao.firstDays` | 0.002 | 0.003 | 0 | ✓ | CO-ROUTINE d · SETUP · SEARCH workout_sets USING COVERING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · RECURSIV |
| `WorkoutDao.getSet` | 0.003 | 0.003 | 1 | ✓ | SEARCH workout_sets USING INDEX sqlite_autoindex_workout_sets_1 (id=?) |
| `WorkoutDao.get` | 0.003 | 0.003 | 1 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `WorkoutDao.observe` | 0.003 | 0.003 | 1 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
| `WorkoutDao.workoutIdOf` | 0.002 | 0.003 | 1 | ✓ | SEARCH workout_exercises USING INDEX sqlite_autoindex_workout_exercises_1 (id=?) |
| `StatsDao.trainedExerciseIds` | 0.002 | 0.003 | 0 | ✓ | CO-ROUTINE ex · SETUP · SEARCH workout_sets USING COVERING INDEX index_workout_sets_exercise_id_completed_performed_on · RECURSIVE STEP · SCAN ex · CORRELATED S |
| `BodyWeightDao.get` | 0.002 | 0.003 | 1 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on=?) |
| `RecordDao.observeForExercise` | 0.002 | 0.003 | 2 | ✓ | SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=?) |
| `RecordDao.forExercise` | 0.002 | 0.002 | 2 | ✓ | SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=?) |
| `WorkoutDao.observePending` | 0.002 | 0.002 | 1 | ✓ | SCAN CONSTANT ROW · SCALAR SUBQUERY 1 · SEARCH workouts USING INDEX index_workouts_dirty (dirty=?) · SCALAR SUBQUERY 2 · SEARCH routines USING COVERING INDEX in |
| `StatsDao.lastDays` | 0.002 | 0.002 | 0 | ✓ | CO-ROUTINE d · SETUP · SEARCH workout_sets USING COVERING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) · RECURSIV |
| `BodyWeightDao.firstDay` | 0.002 | 0.002 | 1 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=?) |
| `RoutineDao.get` | 0.002 | 0.002 | 1 | ✓ | SEARCH routines USING INDEX sqlite_autoindex_routines_1 (id=?) |
| `RecordDao.clean` | 0.002 | 0.002 | 0 | ✓ | SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=? AND kind=?) |
| `StatsDao.firstDay` | 0.001 | 0.002 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) |
| `StatsDao.lastDay` | 0.002 | 0.002 | 1 | ✓ | SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?) |
| `BodyWeightDao.clean` | 0.001 | 0.001 | 0 | ✓ | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on=?) |
| `WorkoutDao.cleanSet` | 0.001 | 0.001 | 0 | ✓ | SEARCH workout_sets USING INDEX sqlite_autoindex_workout_sets_1 (id=?) |
| `WorkoutDao.cleanExercise` | 0.001 | 0.001 | 0 | ✓ | SEARCH workout_exercises USING INDEX sqlite_autoindex_workout_exercises_1 (id=?) |
| `WorkoutDao.cleanWorkout` | 0.001 | 0.001 | 0 | ✓ | SEARCH workouts USING INDEX sqlite_autoindex_workouts_1 (id=?) |
