# Resultados · neon_1M

Mediana de 5 ejecuciones (planning + execution, medido en el servidor con EXPLAIN ANALYZE).

Filas totales: workouts=37,757, workout_exercises=226,542, workout_sets=906,168, users=805

- Usuario intensivo: 1,200 entrenamientos, 28,800 series
- Usuario normal: 40 entrenamientos, 960 series

| ID | Consulta | Usuario intensivo (ms) | Usuario normal (ms) | Plan |
|---|---|---:|---:|---|
| Q01 | Historial: primera página (20 entrenamientos) | 0.157 | 0.153 | Index Scan, Limit (workouts_user_date_idx) |
| Q02 | Historial: página profunda con OFFSET 1000 (anti-patrón) | 0.680 | 0.188 | Bitmap Heap Scan, Bitmap Index Scan, Limit, Sort (workouts_user_sync_idx) |
| Q03 | Historial: misma página con keyset (recomendado) | 0.167 | 0.167 | Index Scan, Limit (workouts_user_date_idx) |
| Q04 | Detalle de un entrenamiento (ejercicios + series) | 0.397 | 0.406 | Incremental Sort, Index Scan, Nested Loop (workout_exercises_workout_idx, workout_sets_exercise_pos_idx) |
| Q05 | Últimas series de un ejercicio (motor de progresión) | 0.185 | 0.193 | Index Only Scan, Limit (workout_sets_user_exercise_idx) |
| Q06 | Récords del ejercicio: peso máx y 1RM estimado | 0.886 | 0.293 | Aggregate, Index Only Scan (workout_sets_user_exercise_idx) |
| Q07 | Curva de 1RM estimado por sesión (último año) | 0.364 | 0.246 | Aggregate, Index Only Scan (workout_sets_user_exercise_idx) |
| Q08 | Volumen semanal · últimos 3 meses | 0.227 | 0.227 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q09 | Volumen mensual · último año | 0.304 | 0.213 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q10 | Volumen anual · histórico completo | 0.682 | 0.217 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q11 | Volumen histórico calculado desde series (sin desnormalizar) | 11.553 | 0.603 | Aggregate, Index Only Scan, Sort (workout_sets_user_date_idx) |
| Q12 | Series por grupo muscular · últimas 4 semanas | 0.543 | 0.548 | Aggregate, Hash, Hash Join, Index Only Scan, Seq Scan, Sort (workout_sets_user_date_idx) |
| Q13 | Sync incremental (series cambiadas en 7 días) | 0.199 | 0.230 | Index Scan, Limit (workout_sets_user_sync_idx) |
| Q14 | Q05 sin índices (seq scan forzado, anti-patrón) | 96.568 | 92.140 | Gather Merge, Limit, Seq Scan, Sort |
| Q15 | Q05 con RLS · auth.uid() sin envolver, sin filtro | 0.215 | 0.211 | Index Only Scan, Limit (workout_sets_user_exercise_idx) |
| Q16 | Q05 con RLS · (select auth.uid()), sin filtro | 0.224 | 0.216 | Index Only Scan, Limit, Result (workout_sets_user_exercise_idx) |
| Q17 | Q05 con RLS · (select auth.uid()) + filtro user_id | 0.216 | 0.224 | Index Only Scan, Limit, Result (workout_sets_user_exercise_idx) |

Tamaños (tablas e índices > 1 MB):

```
workout_sets	146 MB	153010176
workout_sets_user_exercise_idx	77 MB	81215488
workout_sets_user_date_idx	68 MB	71483392
workout_sets_pkey	36 MB	37224448
workout_sets_exercise_pos_idx	35 MB	36806656
workout_exercises	30 MB	31178752
workouts	11 MB	11722752
workout_exercises_pkey	9256 kB	9478144
workout_exercises_workout_idx	9000 kB	9216000
workout_sets_user_sync_idx	7432 kB	7610368
workout_exercises_user_sync_idx	3192 kB	3268608
workouts_user_date_idx	2864 kB	2932736
workouts_pkey	2312 kB	2367488
workouts_user_sync_idx	1520 kB	1556480
```
