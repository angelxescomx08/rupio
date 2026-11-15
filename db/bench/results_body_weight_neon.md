# Peso corporal · Postgres en Neon (con RLS)

730,000 filas (1 000 usuarios × 2 años), tabla + índices: 152 MB. Carga: 5.0 s.

Tiempo = planeación + ejecución medido por Postgres (sin red), mediana de 7.

| Consulta | p50 (ms) | Mejor (ms) | Plan |
|---|---:|---:|---|
| Gráfica 3 meses (Data API: rango por fecha) | 0.213 | 0.202 | Result (filas 90.0, bloques 5) → Result (filas 1.0, bloques 0) → Index Only Scan using body_weights_pkey (filas 90.0, bloques 5) |
| Gráfica 1 año agrupada por semana | 0.372 | 0.366 | Sort (filas 53.0, bloques 8) → Result (filas 1.0, bloques 0) → Aggregate (filas 53.0, bloques 8) → Result (filas 365.0, bloques 8) → Index Only Scan using body_weights_pkey (filas 365.0, bloques 8) |
| Sincronización: cambios desde ayer | 0.183 | 0.175 | Limit (filas 1.0, bloques 4) → Result (filas 1.0, bloques 0) → Result (filas 1.0, bloques 4) → Index Scan using body_weights_user_sync_idx (filas 1.0, bloques 4) |
| Primera sincronización: lote de 400 | 0.666 | 0.628 | Limit (filas 400.0, bloques 407) → Result (filas 1.0, bloques 0) → Result (filas 400.0, bloques 407) → Index Scan using body_weights_user_sync_idx (filas 400.0, bloques 407) |
| Upsert de un día (on conflict) | 0.207 | 0.183 | ModifyTable (filas 0.0, bloques 20) → Result (filas 1.0, bloques 0) → Result (filas 1.0, bloques 0) → Result (filas 1.0, bloques 0) → Result (filas 1.0, bloques 0) |

## Por qué la clave primaria lleva `INCLUDE (weight_kg, deleted)`

Mismo volumen de datos, misma consulta, antes y después:

| Consulta | Clave primaria simple | Con INCLUDE (índice cubriente) |
|---|---|---|
| Gráfica 3 meses | 0.328 ms · 93 bloques (Bitmap Heap Scan) | 0.213 ms · 5 bloques (Index Only Scan) |
| Gráfica 1 año por semana | 0.680 ms · 370 bloques | 0.372 ms · 8 bloques |

Los pesos de todos los usuarios llegan mezclados día con día, así que en la tabla cada fila de un usuario
queda en un bloque distinto. Con el índice cubriente la gráfica se lee solo del índice, que sí está ordenado
por usuario y fecha. El costo es nulo: `updated_at` ya está indexado para la sincronización, así que las
actualizaciones no eran HOT de todos modos.
