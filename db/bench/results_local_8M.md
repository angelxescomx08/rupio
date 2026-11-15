# Resultados · local_8M

Mediana de 7 ejecuciones (planning + execution, medido en el servidor con EXPLAIN ANALYZE).

Filas totales: workouts=339,224, workout_exercises=2,035,344, workout_sets=8,141,376, users=3,020

- Usuario intensivo: 1,200 entrenamientos, 28,800 series
- Usuario normal: 105 entrenamientos, 2,520 series

| ID | Consulta | Usuario intensivo (ms) | Usuario normal (ms) | Plan |
|---|---|---:|---:|---|
| Q01 | Historial: primera página (20 entrenamientos) | 0.529 | 0.529 | Index Scan, Limit (workouts_user_date_idx) |
| Q02 | Historial: página profunda con OFFSET 1000 (anti-patrón) | 4.371 | 1.197 | Index Scan, Limit (workouts_user_date_idx) |
| Q03 | Historial: misma página con keyset (recomendado) | 0.518 | 0.493 | Index Scan, Limit (workouts_user_date_idx) |
| Q04 | Detalle de un entrenamiento (ejercicios + series) | 0.748 | 0.780 | Incremental Sort, Index Scan, Nested Loop (workout_exercises_workout_idx, workout_sets_exercise_pos_idx) |
| Q05 | Últimas series de un ejercicio (motor de progresión) | 0.502 | 0.498 | Index Only Scan, Limit (workout_sets_user_exercise_idx) |
| Q06 | Récords del ejercicio: peso máx y 1RM estimado | 1.241 | 0.671 | Aggregate, Index Only Scan (workout_sets_user_exercise_idx) |
| Q07 | Curva de 1RM estimado por sesión (último año) | 0.681 | 0.600 | Aggregate, Index Only Scan (workout_sets_user_exercise_idx) |
| Q08 | Volumen semanal · últimos 3 meses | 0.507 | 0.513 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q09 | Volumen mensual · último año | 0.585 | 0.580 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q10 | Volumen anual · histórico completo | 0.886 | 0.523 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q11 | Volumen histórico calculado desde series (sin desnormalizar) | 9.768 | 1.392 | Aggregate, Index Only Scan, Sort (workout_sets_user_date_idx) |
| Q12 | Series por grupo muscular · últimas 4 semanas | 1.015 | 0.913 | Aggregate, Hash, Hash Join, Index Only Scan, Seq Scan, Sort (workout_sets_user_date_idx) |
| Q13 | Sync incremental (series cambiadas en 7 días) | 0.483 | 0.469 | Index Scan, Limit (workout_sets_user_sync_idx) |
| Q14 | Q05 sin índices (seq scan forzado, anti-patrón) | 378.211 | 390.096 | Gather Merge, Limit, Seq Scan, Sort |
| Q15 | Q05 con RLS · auth.uid() sin envolver, sin filtro | 1.238 | 2.082 | Index Only Scan, Limit (workout_sets_user_exercise_idx) |
| Q16 | Q05 con RLS · (select auth.uid()), sin filtro | 1.285 | 1.252 | Index Only Scan, Limit, Result (workout_sets_user_exercise_idx) |
| Q17 | Q05 con RLS · (select auth.uid()) + filtro user_id | 1.143 | 1.643 | Index Only Scan, Limit, Result (workout_sets_user_exercise_idx) |

Tamaños (tablas e índices > 1 MB):

```
workout_sets	1309 MB	1373085696
workout_sets_user_exercise_idx	694 MB	727515136
workout_sets_user_date_idx	611 MB	640303104
workout_sets_exercise_pos_idx	315 MB	330555392
workout_sets_pkey	314 MB	329646080
workout_exercises	267 MB	279904256
workouts	100 MB	105160704
workout_exercises_pkey	79 MB	83337216
workout_exercises_workout_idx	79 MB	82665472
workout_sets_user_sync_idx	65 MB	68231168
workout_exercises_user_sync_idx	28 MB	29155328
workouts_user_date_idx	25 MB	26157056
workouts_pkey	21 MB	21569536
workouts_user_sync_idx	13 MB	13803520
```
