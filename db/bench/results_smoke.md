# Resultados · smoke

Mediana de 3 ejecuciones (planning + execution, medido en el servidor con EXPLAIN ANALYZE).

Filas totales: workouts=5,761, workout_exercises=34,566, workout_sets=138,264, users=52

- Usuario intensivo: 300 entrenamientos, 7,200 series
- Usuario normal: 101 entrenamientos, 2,424 series

| ID | Consulta | Usuario intensivo (ms) | Usuario normal (ms) | Plan |
|---|---|---:|---:|---|
| Q01 | Historial: primera página (20 entrenamientos) | 0.410 | 0.312 | Index Scan, Limit (workouts_user_date_idx) |
| Q02 | Historial: página profunda con OFFSET 1000 (anti-patrón) | 0.335 | 0.373 | Index Scan, Limit (workouts_user_date_idx) |
| Q03 | Historial: misma página con keyset (recomendado) | 0.382 | 0.309 | Index Scan, Limit (workouts_user_date_idx) |
| Q04 | Detalle de un entrenamiento (ejercicios + series) | 0.705 | 0.725 | Incremental Sort, Index Scan, Nested Loop (workout_exercises_workout_idx, workout_sets_exercise_pos_idx) |
| Q05 | Últimas series de un ejercicio (motor de progresión) | 0.452 | 0.426 | Index Only Scan, Limit (workout_sets_user_exercise_idx) |
| Q06 | Récords del ejercicio: peso máx y 1RM estimado | 0.825 | 0.812 | Aggregate, Index Only Scan (workout_sets_user_exercise_idx) |
| Q07 | Curva de 1RM estimado por sesión (último año) | 0.721 | 0.542 | Aggregate, Index Only Scan (workout_sets_user_exercise_idx) |
| Q08 | Volumen semanal · últimos 3 meses | 0.397 | 0.384 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q09 | Volumen mensual · último año | 0.472 | 0.394 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q10 | Volumen anual · histórico completo | 0.447 | 0.463 | Aggregate, Index Only Scan, Sort (workouts_user_date_idx) |
| Q11 | Volumen histórico calculado desde series (sin desnormalizar) | 2.813 | 1.294 | Aggregate, Index Only Scan, Sort (workout_sets_user_date_idx) |
| Q12 | Series por grupo muscular · últimas 4 semanas | 0.914 | 0.861 | Aggregate, Hash, Hash Join, Index Only Scan, Seq Scan, Sort (workout_sets_user_date_idx) |
| Q13 | Sync incremental (series cambiadas en 7 días) | 0.563 | 0.556 | Index Scan, Limit (workout_sets_user_sync_idx) |
| Q14 | Q05 sin índices (seq scan forzado, anti-patrón) | 11.280 | 11.038 | Limit, Seq Scan, Sort |
| Q15 | Q05 con RLS · auth.uid() sin envolver, sin filtro | 1.110 | 0.947 | Index Only Scan, Limit (workout_sets_user_exercise_idx) |
| Q16 | Q05 con RLS · (select auth.uid()), sin filtro | 1.002 | 0.909 | Index Only Scan, Limit, Result (workout_sets_user_exercise_idx) |
| Q17 | Q05 con RLS · (select auth.uid()) + filtro user_id | 0.940 | 0.904 | Index Only Scan, Limit, Result (workout_sets_user_exercise_idx) |

Tamaños (tablas e índices > 1 MB):

```
workout_sets	22 MB	23314432
workout_sets_user_exercise_idx	12 MB	12361728
workout_sets_user_date_idx	10 MB	10895360
workout_sets_exercise_pos_idx	5512 kB	5644288
workout_sets_pkey	5384 kB	5513216
workout_exercises	4640 kB	4751360
workouts	1736 kB	1777664
workout_exercises_workout_idx	1400 kB	1433600
workout_exercises_pkey	1400 kB	1433600
workout_sets_user_sync_idx	1144 kB	1171456
```
