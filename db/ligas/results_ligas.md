# Resultados · ligas y muro

Mediana de 9 ejecuciones tras 1 de calentamiento (planning + execution, EXPLAIN ANALYZE en el servidor, sin red).

PostgreSQL 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1) on x86_64-pc-linux-gnu local · perfiles=200,000 · muro=100,000 · participantes=100,000 · grupos=52,020 · puntajes semanales=1,558,402 · participantes esta semana=60,114


## Muro hoy (007) · 100,000 visibles

| ID | Consulta | Mediana (ms) | Máx (ms) | Plan |
|---|---|---:|---:|---|
| M1A | Lista del muro (300) + tu lugar, siendo el último | 67.478 | 110.229 | Result |
| M2Ap | Tu lugar y lo que te falta · primero | 60.451 | 69.284 | Result |
| M2Am | Tu lugar y lo que te falta · mitad | 73.386 | 88.641 | Result |
| M2Aú | Tu lugar y lo que te falta · último | 75.249 | 80.554 | Result |

## Muro hoy (007) · 1,000 visibles

| ID | Consulta | Mediana (ms) | Máx (ms) | Plan |
|---|---|---:|---:|---|
| M1Ar | Lista del muro (300) + tu lugar, siendo el último | 7.213 | 10.914 | Result |
| M2Arp | Tu lugar y lo que te falta · primero | 5.717 | 5.889 | Result |
| M2Arm | Tu lugar y lo que te falta · mitad | 5.706 | 6.294 | Result |
| M2Arú | Tu lugar y lo que te falta · último | 5.683 | 5.915 | Result |

## Muro con 008 · 100,000 visibles

| ID | Consulta | Mediana (ms) | Máx (ms) | Plan |
|---|---|---:|---:|---|
| M1B | Lista del muro (300) + tu lugar, siendo el último | 7.625 | 9.02 | Result |
| M2Bp | Tu lugar y lo que te falta · primero | 0.165 | 0.213 | Result |
| M2Bm | Tu lugar y lo que te falta · mitad | 3.509 | 4.327 | Result |
| M2Bú | Tu lugar y lo que te falta · último | 7.454 | 8.074 | Result |

## Muro con 008 · 1,000 visibles

| ID | Consulta | Mediana (ms) | Máx (ms) | Plan |
|---|---|---:|---:|---|
| M1Br | Lista del muro (300) + tu lugar, siendo el último | 1.463 | 1.561 | Result |
| M2Brp | Tu lugar y lo que te falta · primero | 0.25 | 0.315 | Result |
| M2Brm | Tu lugar y lo que te falta · mitad | 0.232 | 0.304 | Result |
| M2Brú | Tu lugar y lo que te falta · último | 0.289 | 0.402 | Result |

## Ligas · lectura

| ID | Consulta | Mediana (ms) | Máx (ms) | Plan |
|---|---|---:|---:|---|
| L1 | Tu liga completa (rupio_league: grupo de 30, zonas, apodos e insignias) | 0.495 | 0.567 | Result |
| L2 | Solo la tabla del grupo (30 filas en orden) | 0.031 | 0.062 | Index Only Scan (league_scores_group_idx) |
| L2x | L2 sin league_scores_group_idx (anti-patrón) | 51.694 | 54.725 | Gather Merge, Seq Scan, Sort |
| L3 | Top global de la semana · primera página (50 de 60,114) | 0.667 | 0.858 | Result |
| L4 | Top global · página después del lugar 5,000 (keyset) | 0.778 | 1.012 | Result |
| L4x | La misma página con OFFSET 5000 (anti-patrón) | 0.92 | 1.029 | Index Only Scan, Limit (league_scores_week_idx) |
| L5 | Tu lugar global y tu porcentaje · siendo el último | 4.78 | 8.066 | Result |
| L5x | L5 sin league_scores_week_idx (anti-patrón) | 182.049 | 210.208 | Result |

## Ligas · escritura

| ID | Consulta | Mediana (ms) | Máx (ms) | Plan |
|---|---|---:|---:|---|
| E1 | Primer entreno de la semana: calcula liga y entra a un grupo | 0.42 | 0.507 | Result |
| E2 | Otro entreno en la misma semana (actualiza tu puntaje) | 0.035 | 0.052 | Result |
| T0 | Subir 400 entrenos (un lote de sync) sin triggers de ligas | 2.834 | 3.292 | Function Scan, ModifyTable, Subquery Scan · trigger 0.392 ms |
| T1f | 400 entrenos · no participa · trigger por fila | 4.971 | 7.682 | Function Scan, ModifyTable, Subquery Scan · trigger 2.179 ms |
| T1 | 400 entrenos · no participa · trigger por sentencia (008) | 4.061 | 5.879 | Function Scan, ModifyTable, Subquery Scan · trigger 0.625 ms |
| T2f | 400 entrenos · participante, historial de 8 años · por fila | 11.295 | 12.17 | Function Scan, ModifyTable, Subquery Scan · trigger 6.692 ms |
| T2 | 400 entrenos · participante, historial de 8 años · por sentencia (008) | 4.795 | 7.578 | Function Scan, ModifyTable, Subquery Scan · trigger 0.595 ms |
| T3f | 400 entrenos · participante, todos en esta semana (peor caso) · por fila | 35.89 | 50.733 | Function Scan, ModifyTable, Subquery Scan · trigger 30.595 ms |
| T3 | 400 entrenos · participante, todos en esta semana (peor caso) · por sentencia (008) | 4.185 | 4.844 | Function Scan, ModifyTable, Subquery Scan · trigger 1.246 ms |
| T4 | Editar 400 entrenos de esta semana en un UPDATE · por sentencia (008) | 6.628 | 7.09 | Bitmap Heap Scan, Bitmap Index Scan, ModifyTable (workouts_user_sync_idx) · trigger 1.614 ms |

## Tamaños

```
league_scores	460 MB
league_scores_group_idx	133 MB
league_scores_week_idx	113 MB
wall_entries	44 MB
wall_entries_rank_idx	17 MB
league_members	12 MB
league_groups	3864 kB
league_groups_open_idx	16 kB
```
