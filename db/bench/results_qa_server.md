# QA del servidor (Neon Auth + Data API)

Fecha: 2026-11-14 22:37 · usuario temporal (borrado al final).

| Prueba | Resultado | Detalle |
|---|---|---|
| Registro y JWT como la app | ✓ | 601 ms |
| Subida de 4,650 filas en lotes de 400 | ✓ | 3.6 s total · lote p50 262 ms · máx 330 ms · 1400 series/s |
| Reenviar un lote no duplica (upsert idempotente) | ✓ | 3600 en el servidor |
| Bajada completa sin huecos ni repetidos (filtro or= real de PostgREST) | ✓ | 3,600 filas en 10 páginas, 2.8 s |
| Más de 400 filas dentro de una ventana de 5 s (el caso que rompía la sincronización) | ✓ | 2000 filas en 5 s |
| El algoritmo anterior perdía datos (comprobado contra el servidor real) | ✓ | bajaba 400 de 3,600 series tras 5 sincronizaciones |
| No puede LEER rutinas de otro usuario | ✓ | HTTP 200, 0 filas |
| Sin filtro solo ve lo suyo | ✓ |  |
| No puede CREAR filas a nombre de otro usuario | ✓ | HTTP 403 |
| No puede MODIFICAR rutinas de otro usuario | ✓ | HTTP 200, afectadas: [] |
| No puede BORRAR datos de otro usuario | ✓ | HTTP 200 |
| Las rutinas reales de Angel siguen intactas | ✓ | 2 rutinas |
| Sin JWT: rechazado | ✓ | HTTP 401 |
| JWT alterado: rechazado | ✓ | HTTP 401 |
| Datos inválidos (reps = 99 999): rechazados por el servidor | ✓ | HTTP 400 |
| Peso corporal imposible (5 000 kg): rechazado | ✓ | HTTP 400 |
| Inyección SQL en un filtro: se trata como texto | ✓ | HTTP 200 |
| Cuerpo enorme (~7 MB, 20 000 filas): el servidor responde sin caerse | ✓ | HTTP 400 en 726 ms |
| Sincronización sin cambios (consulta incremental) | ✓ | p50 249 ms de ida y vuelta |
| Limpieza: usuario temporal y sus datos borrados | ✓ |  |
