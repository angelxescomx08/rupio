# Rendimiento local (SQLite / Room) con datos dummy

SQLite 3.53.1 · mediana y p95 de 40 ejecuciones por consulta · SQL extraído de `Daos.kt` · esquema e índices de Room.
Un teléfono de gama media es ~3–5× más lento que esta máquina: aun así todo queda muy por debajo de un fotograma (16 ms).

- Usuario típico: 300 entrenamientos, 7,200 series
- Usuario extremo: 5 000 entrenamientos, 120,000 series (≈14 años entrenando a diario)

| Consulta | Típico (ms) | Extremo (ms) | Extremo p95 | Extremo SIN índices (ms) | Plan con índices (extremo) |
|---|---:|---:|---:|---:|---|
| Entrenamiento activo al abrir la app | 0.004 | 0.004 | 0.009 | 0.110 | `SEARCH workouts USING INDEX index_workouts_ended_at (ended_at=?) | USE TEMP B-TREE FOR ORDER BY` |
| Inicio: últimos 40 entrenamientos (resumen semanal) | 0.057 | 0.057 | 0.073 | 1.984 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?)` |
| Historial: primera página (30) | 0.050 | 0.051 | 0.070 | 2.095 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date<?)` |
| Historial: página 1 000 entrenamientos atrás (keyset) | 0.014 | 0.051 | 0.069 | 2.749 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date<?)` |
| Historial: ejercicios de una página en 1 consulta (sin N+1) | 0.355 | 0.387 | 0.418 | 66.500 | `SEARCH we USING INDEX index_workout_exercises_workout_id_position (workout_id=?) | SEARCH e USING INDEX sqlite_autoindex_exercises_1 (id=?) ` |
| Entrenamiento: todas sus series | 0.038 | 0.039 | 0.047 | 29.803 | `SEARCH we USING INDEX index_workout_exercises_workout_id_position (workout_id=?) | SEARCH s USING INDEX index_workout_sets_workout_exercise_` |
| Motor de progresión: series anteriores del ejercicio | 0.145 | 0.163 | 0.194 | 14.995 | `SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) | USE TEMP B-TREE FOR ` |
| Récords del ejercicio | 0.003 | 0.002 | 0.003 | 0.002 | `SEARCH personal_records USING INDEX sqlite_autoindex_personal_records_1 (exercise_id=?)` |
| Progreso · Semana (por día) | 0.006 | 0.007 | 0.010 | 0.217 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) | USE TEMP B-TREE FOR` |
| Progreso · 3 meses (por semana) | 0.036 | 0.039 | 0.058 | 0.243 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) | USE TEMP B-TREE FOR` |
| Progreso · Año (por mes) | 0.113 | 0.136 | 0.164 | 0.328 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) | USE TEMP B-TREE FOR` |
| Progreso · Histórico (por año) | 0.097 | 1.703 | 1.794 | 1.514 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?) | USE TEMP B-TREE FOR` |
| Totales del periodo | 0.010 | 0.011 | 0.020 | 0.224 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?)` |
| Series por músculo · 4 semanas | 0.112 | 0.127 | 0.154 | 8.998 | `SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (ANY(exercise_id) AND completed=? AND performed_on>? A` |
| Series por músculo · 1 año | 1.311 | 1.616 | 1.834 | 10.414 | `SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (ANY(exercise_id) AND completed=? AND performed_on>? A` |
| Curva de 1RM de un ejercicio (todo el historial, agregada en SQL) | 0.134 | 2.873 | 3.093 | 11.360 | `SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=? AND performed_on>?)` |
| Ejercicios más usados · todo el historial | 0.696 | 15.678 | 16.690 | 17.792 | `SEARCH w USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>?) | SEARCH we USING INDEX index_workout_exercis` |
| Ejercicios más usados · último año | 0.703 | 0.785 | 0.832 | 7.057 | `SEARCH w USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>?) | SEARCH we USING INDEX index_workout_exercis` |
| Detalle de ejercicio: últimas 300 series | 0.278 | 0.291 | 0.337 | 15.303 | `SEARCH workout_sets USING INDEX index_workout_sets_exercise_id_completed_performed_on (exercise_id=? AND completed=?) | USE TEMP B-TREE FOR ` |
| Mapa de constancia (18 semanas) | 0.038 | 0.038 | 0.046 | 0.256 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=? AND local_date>? AND local_date<?)` |
| Primer día registrado | 0.001 | 0.001 | 0.001 | 0.278 | `SEARCH workouts USING INDEX index_workouts_deleted_local_date_started_at (deleted=?)` |
| Cambios pendientes de sincronizar | 0.002 | 0.002 | 0.002 | 0.167 | `SCAN CONSTANT ROW | SCALAR SUBQUERY 1 | SEARCH workouts USING INDEX index_workouts_dirty (dirty=?) | SCALAR SUBQUERY 2 | SEARCH routines USI` |
| Sync: series por subir | 0.185 | 0.172 | 0.201 | 10.949 | `SEARCH s USING INDEX index_workout_sets_dirty (dirty=?) | SEARCH w USING INDEX sqlite_autoindex_workouts_1 (id=?)` |
