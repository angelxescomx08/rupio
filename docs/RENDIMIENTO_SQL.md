# Rendimiento SQL de Rupio

Rupio tiene **dos bases de datos**, y las dos se diseñaron y midieron:

| Dónde | Motor | Para qué | ¿La UI espera por ella? |
|---|---|---|---|
| El teléfono | SQLite (Room) | Fuente de verdad de la app: todo lo que ves sale de aquí | **Sí** → debe responder en ≪ 16 ms (un fotograma) |
| La nube | PostgreSQL 18 (Neon) | Respaldo y sincronización entre teléfonos | No: se sincroniza en segundo plano |

La app es *offline-first*: marcar una serie, ver el historial o las gráficas nunca espera a internet.

---

## 1. Mejores prácticas aplicadas (y de dónde salen)

**PostgreSQL (Neon)** — `db/migrations/001_schema.sql`, `003_security.sql`

1. **Cada índice existe porque una consulta concreta lo usa.** Índices compuestos que empiezan por `user_id` (filtro + RLS) y terminan en la columna de orden/rango, así el plan no necesita `Sort`.
2. **Índices cubrientes con `INCLUDE`** para que las consultas calientes sean *index-only scans* (no tocan la tabla). Documentación oficial: [PostgreSQL · Index-Only Scans and Covering Indexes](https://www.postgresql.org/docs/18/indexes-index-only-scans.html).
3. **Índices parciales** (`WHERE completed AND NOT deleted`): más pequeños y más rápidos.
4. **Paginación keyset** (`WHERE (local_date, started_at) < (…)`) en vez de `OFFSET`: el costo no crece con la profundidad. Ver [Use The Index, Luke · No Offset](https://use-the-index-luke.com/no-offset).
5. **RLS rápida**: `(select auth.uid())` en vez de `auth.uid()` (se evalúa una vez por consulta), políticas `TO authenticated`, índice en cada columna usada por políticas y filtro explícito `user_id = …` además de la RLS. Ver [Supabase · RLS Performance and Best Practices](https://supabase.com/docs/guides/troubleshooting/rls-performance-and-best-practices-Z5Jjwv) (aplica igual a la Data API de Neon, que usa PostgREST y `pg_session_jwt`).
6. **Desnormalización medida**: cada entrenamiento guarda `total_volume_kg`, `total_sets`, `total_reps`. El volumen histórico sale de 1 fila por entrenamiento en vez de 24 series (Q10 vs Q11 abajo).
7. **UUIDv7 generados en el teléfono**: ordenados en el tiempo → las inserciones caen al final del B-tree (menos *page splits* que UUIDv4) y no hay ida y vuelta al servidor para pedir IDs.
8. **`updated_at` lo pone el servidor** (trigger) → la sincronización incremental no depende del reloj del teléfono.
9. **Fechas locales guardadas** (`local_date`, `performed_on`) con la zona horaria del usuario al empezar: agrupar por semana/mes nunca depende de la zona del servidor.

**SQLite (Room)** — `app/src/main/java/com/rupio/app/data/local/`

1. Índices compuestos alineados con cada `WHERE … ORDER BY` de los DAO (verificado con `EXPLAIN QUERY PLAN`).
2. **WAL** (`setJournalMode(WRITE_AHEAD_LOGGING)`): las lecturas de estadísticas no bloquean al marcar series.
3. **`PRAGMA optimize`** al abrir la base (estadísticas del planificador al día).
4. **`@Upsert`, nunca `REPLACE`** en tablas padre (REPLACE borra y reinserta → dispara `ON DELETE CASCADE`).
5. **Sin N+1**: el historial trae los ejercicios de toda la página en una sola consulta (`exerciseSummaries`).
6. **Agregar en SQL, no en Kotlin**: la curva de 1RM devuelve una fila por sesión, no todas las series.
7. **Escrituras con *debounce*** al teclear pesos/reps (350 ms) y `Mutex` al marcar series: nada de una escritura por tecla ni toques que se crucen.

---

## 2. Benchmark en Neon (tu base real) — ~1 millón de series

`db/bench/run_bench.py --target neon` · esquema temporal `bench` (borrado al terminar) · **905 000+ series, 805 usuarios** · mediana de 5 ejecuciones con `EXPLAIN (ANALYZE, BUFFERS)` (tiempo del servidor, sin red). Resultados completos: [`db/bench/results_neon_1M.md`](../db/bench/results_neon_1M.md).

| Consulta | Usuario intensivo (1 200 entrenos) | Usuario normal | Plan |
|---|---:|---:|---|
| Historial, 1.ª página | 0.157 ms | 0.153 ms | Index Scan |
| Historial, página profunda con **OFFSET** (anti-patrón) | 0.680 ms | 0.188 ms | Bitmap + Sort |
| Historial, misma página con **keyset** | 0.167 ms | 0.167 ms | Index Scan |
| Detalle de un entrenamiento | 0.397 ms | 0.406 ms | Nested Loop, índices |
| Motor de progresión (últimas series del ejercicio) | 0.185 ms | 0.193 ms | **Index Only Scan** |
| Récords del ejercicio | 0.886 ms | 0.293 ms | Index Only Scan |
| Volumen semanal / mensual / anual | 0.23 / 0.30 / 0.68 ms | ≈0.22 ms | Index Only Scan |
| Volumen histórico **sin** desnormalizar (desde series) | 11.553 ms | 0.603 ms | — |
| Series por músculo, 4 semanas | 0.543 ms | 0.548 ms | Index Only Scan |
| Sync incremental | 0.199 ms | 0.230 ms | Index Scan |
| **Misma consulta de progresión SIN índices** | **96.568 ms** | 92.140 ms | Seq Scan |
| Progresión con RLS `(select auth.uid())` + filtro | 0.216 ms | 0.224 ms | Index Only Scan |

**Lectura:** todas las consultas que usa la app quedan por debajo de **1 ms** en el servidor. Sin índices, la consulta del motor de progresión es **≈500× más lenta**. Desnormalizar el volumen ahorra ~17× para el usuario intensivo.

**Escala:** el mismo esquema se probó antes en PostgreSQL local con **8.1 millones de series y 3 020 usuarios** ([`results_local_8M.md`](../db/bench/results_local_8M.md)): todas las consultas de la app siguen entre 0.5 y 2 ms, porque cada una lee solo el rango del usuario en un índice (O(log n + filas del usuario)); el seq scan sube a 378 ms. Es decir, crecer de 1 M a 8 M de series casi no cambia los tiempos.

---

## 3. Benchmark en el teléfono (SQLite / Room)

`db/bench/sqlite_bench.py` crea las tablas e índices **exactos** que genera Room (`app/schemas/…/1.json`) y ejecuta el SQL **extraído automáticamente** de `Daos.kt`. Mediana y p95 de 40 ejecuciones. Resultados completos: [`db/bench/results_sqlite.md`](../db/bench/results_sqlite.md).

- **Típico**: 300 entrenamientos, 7 200 series.
- **Extremo**: 5 000 entrenamientos, 120 000 series (≈14 años entrenando a diario).

| Pantalla / consulta | Típico | Extremo | Extremo **sin índices** |
|---|---:|---:|---:|
| Abrir app: entrenamiento activo | 0.004 ms | 0.004 ms | 0.110 ms |
| Historial, 1.ª página | 0.050 ms | 0.051 ms | 2.095 ms |
| Historial, 1 000 entrenamientos atrás (keyset) | 0.014 ms | 0.051 ms | 2.749 ms |
| Historial: ejercicios de la página (sin N+1) | 0.355 ms | 0.387 ms | 66.500 ms |
| Entrenamiento: todas sus series | 0.038 ms | 0.039 ms | 29.803 ms |
| Motor de progresión | 0.145 ms | 0.163 ms | 14.995 ms |
| Progreso · 3 meses / año / histórico | 0.04 / 0.11 / 0.10 ms | 0.04 / 0.14 / 1.70 ms | — |
| Series por músculo (4 sem / 1 año) | 0.11 / 1.31 ms | 0.13 / 1.62 ms | 9.0 / 10.4 ms |
| Curva de 1RM (todo el historial) | 0.134 ms | 2.873 ms | 11.360 ms |
| Ejercicios más usados (ventana de 1 año) | 0.703 ms | 0.785 ms | 7.057 ms |
| Sync: series por subir | 0.185 ms | 0.172 ms | 10.949 ms |

Un teléfono de gama media es ~3–5× más lento que la máquina de prueba; incluso así, la consulta más pesada del usuario extremo queda en ≈15 ms y todas las demás en ≈1 ms o menos.

### Optimizaciones que salieron de medir

| Consulta (usuario extremo) | Antes | Después | Cambio |
|---|---:|---:|---|
| Ejercicios más usados | 58.6 ms | **0.8 ms** | Contar en `workout_exercises` (6 filas/entreno) en vez de `workout_sets` (24) con `COUNT(DISTINCT)`, y ventana máx. de 1 año |
| Curva de 1RM | 8.6 ms | **2.9 ms** | Agregar en SQL (1 fila por sesión) con la misma fórmula que `OneRepMax.estimate` — el benchmark verifica que coincidan |
| Detalle de ejercicio | todas las series | últimas 300 (0.29 ms) | `LIMIT` sobre el índice `(exercise_id, completed, performed_on)` |

---

## 4. Cómo repetir las mediciones

```bash
# Teléfono (SQLite), sin dependencias
python3 db/bench/sqlite_bench.py

# PostgreSQL local con 8 M de series
python3 db/bench/run_bench.py --target local --generate --hu 20 --hw 1200 --nu 3000

# Neon (SQL sobre HTTPS); crea y borra el esquema "bench"
NEON_CS='postgresql://…' python3 db/bench/run_bench.py --target neon --schema bench --generate \
  --hu 5 --hw 1200 --nu 800 --nmin 20 --nmax 60 --runs 5 --label neon_1M --drop-after
```

## Peso corporal

Ver `db/bench/results_body_weight_sqlite.md` y `db/bench/results_body_weight_neon.md` (script: `db/bench/bench_body_weight.py`).

- **Teléfono (SQLite)**: clave primaria `(user_id, measured_on)` → la gráfica de cualquier rango es un recorrido ordenado de la clave, agrupado en SQL en ≤ ~150 puntos. Con 20 años de registros diarios: gráfica histórica ≈ 1 ms, 3 meses ≈ 0.05 ms, registrar ≈ 0.2 ms con commit.
- **Servidor (Neon)**: clave primaria cubriente `(user_id, measured_on) INCLUDE (weight_kg, deleted)` → la gráfica se responde solo con el índice (Index Only Scan): 1 año en 8 bloques en vez de 370.
- "Tu progreso" se calcula con ~7 consultas agregadas (mejor serie por ejercicio y día, totales, récords), sin traer series sueltas al teléfono.
