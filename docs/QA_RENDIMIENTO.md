# QA y rendimiento en casos extremos

Objetivo: que Rupio sea **entendible, eficiente, tolerante a errores y rápida** incluso con el peor usuario posible
(años de datos, letra gigante, pantalla pequeña, mala conexión, datos corruptos, dobles toques).

Estado final: **73 pruebas automáticas, 0 fallas** (+1 contra el servidor real con la cuenta demo) · **95 consultas SQL auditadas, 0 lentas** · **19/19 pruebas de carga y seguridad contra el servidor real**.

## Cómo repetir todo

```bash
gradle :app:testDebugUnitTest --offline        # 71 pruebas (dominio, datos, sincronización, UI, migraciones)
python3 db/bench/qa_sql_audit.py               # auditoría de las 95 consultas con un usuario extremo
python3 db/bench/qa_server.py                  # servidor real: carga, paginación y seguridad (usuario temporal que se borra)
DEMO_EMAIL=… DEMO_PASSWORD=… python3 db/demo/demo_extremo.py   # (re)crea la cuenta demo de 8 años
```

Capturas del peor caso (320 dp de ancho, letra del sistema al 200 %): `app/screenshots/qa/`.

## Errores encontrados y corregidos

| # | Severidad | Qué pasaba | Cómo se encontró | Corrección |
|---|---|---|---|---|
| 1 | **Crítica** | La sincronización perdía datos: si más de 400 filas cambiaban en el mismo intervalo de 5 s (p. ej. al importar o al cambiar de teléfono), solo bajaban 400. **Comprobado contra el servidor real: bajaba 400 de 3 600 series** tras 5 sincronizaciones. | Servidor simulado + `qa_server.py` | Paginación por llave `(updated_at, id)` con el filtro `or=` de PostgREST. Verificado en Neon: 3 600 de 3 600, sin repetidos. |
| 2 | Alta | Tocar *Entrenar* a veces llevaba a *Perfil* (el estado guardado de la pestaña se restauraba mal). | Reporte de uso + `NavTabsTest` (reproduce la lógica anterior) | `goToTab`: Inicio se alcanza desapilando, sin restaurar estado. |
| 3 | Alta | Tocar «atrás» dos veces rápido podía dejar la app en blanco. | `NavTabsTest` | `safePop` en las 19 pantallas: solo regresa si la pantalla está activa y hay a dónde volver. |
| 4 | Alta | Una sola fila rechazada por el servidor bloqueaba para siempre la subida de todo lo demás. | `QaDataSyncTest` con reglas CHECK reales | Datos saneados antes de subir; si un lote falla, se sube fila por fila y solo se aparta la mala. Aviso claro: «N cambios no se pudieron subir; se reintentará». |
| 5 | Media | Valores imposibles (NaN, infinito, 10¹² kg, reps negativas) podían mostrarse o romper cálculos. | Pruebas de fuzzing (miles de entradas aleatorias) | Límites en captura, en el motor de progresión, en récords y al formatear («—» si no es válido). |
| 6 | Media | *Tu progreso* no mostraba «antes y ahora» para quien entrena con más de 15 repeticiones. | `QaUiTest` | Se aceptan series de hasta 30 repeticiones (fórmula de Epley por encima de 10). |
| 7 | Media | Terminar un entrenamiento sin series completadas dejaba al usuario atascado en el diálogo. | `QaUiTest` | Explicación clara + botón «Descartar». |
| 8 | Media | Con letra grande, textos cortados o encimados (tarjetas, títulos, botones, pestañas). | Capturas a 320 dp y 200 % | Tarjetas que se apilan solas, textos que se ajustan, botones en columna, pestañas del mismo tamaño. |
| 9 | Media | Algunos textos grises y avatares no llegaban al contraste mínimo de lectura (WCAG AA 4.5:1). | `contrasteDeTextosAA` | Colores ajustados; ahora todos ≥ 4.5:1. |
| 10 | Baja | Botones y filas con zona táctil menor a 48 dp. | `pantallasEnElPeorCaso` | Todas las zonas táctiles ≥ 48 dp y con descripción para lectores de pantalla. |
| 11 | Baja | Doble toque en «Crear rutinas» de la guía podía crear rutinas duplicadas. | Revisión + prueba | Bloqueo mientras se crea. |
| 12 | Baja | Dos IDs creados en el mismo milisegundo no quedaban en orden. | `QaDomainTest` | UUIDv7 monótono (RFC 9562). |
| 13 | **Alta** | Al cambiar de cuenta en el mismo teléfono, el peso corporal de la cuenta anterior se quedaba guardado, y si tenía cambios sin subir **se subía a la cuenta nueva**. | Revisión al preparar la cuenta demo + `cambiarDeCuentaNoMezclaDatos` | «Cerrar sesión» borra también el peso; la subida solo toma filas del usuario actual; y si entra otra cuenta sin haber limpiado (la app se cerró a medias), se borra todo lo local antes de sincronizar. |
| 14 | Media | La primera descarga de una cuenta con años de datos tardaba ~40 s de red y hacía una consulta por cada serie para revisar su entrenamiento (39 000 consultas). | `primeraSincronizacionDeUnaCuentaDe8Anios` | Páginas de 2 000 filas al bajar y revisión de padres en bloque (consultas IN en trozos de 500, compatibles con Android 8). App: 3.5 s → 0.8 s; peticiones: 140 → 34. **Contra Neon real: 6.4 s para 8 años de datos.** |
| 15 | Media | Mientras bajaban los datos, el Inicio decía «Aún no tienes rutinas» e invitaba a crear una (riesgo de rutinas duplicadas). | Revisión de la primera sesión | Aviso «Bajando tus datos de la nube…» (o «Aún no pudimos bajar tus datos» sin conexión) hasta terminar la primera descarga. |

## Rendimiento con un usuario extremo

**SQLite (auditoría, 10 000 entrenamientos, 240 000 series, 6 153 pesos corporales, base de 117 MB):**

| Operación | Antes | Después |
|---|---:|---:|
| *Tu progreso* (historia de cada ejercicio) | ~370 ms | **~4 ms** |
| Recalcular récords de un ejercicio | 11 ms | **3 ms** |
| Ejercicios principales sin datos recientes | 44 ms | **~1 ms** |
| Consulta más lenta de las 95 | — | 3.4–5.7 ms (según la carga de la máquina) |

Ninguna de las 95 consultas recorre tablas grandes completas. Las marcadas ⚠ tardan entre 2 y 6 ms con ese
volumen de datos (un teléfono medio es ~3–5× más lento: siguen muy por debajo de lo perceptible). La exactitud se
verifica contra el cálculo directo: totales, volumen por semana, progreso, récords y peso corporal de 20 años.

**Dentro de la app (Robolectric, 36 000 series):** todas las pantallas cargan en ≤ 23 ms; recalcular TODOS los
récords, 122 ms; historial completo por páginas, 72 ms; borrar un entrenamiento y recalcular sus récords, 45 ms.

**Servidor real (Neon):**

| Prueba | Resultado |
|---|---|
| Subida de 4 650 filas en lotes de 400 | 3.6 s · lote p50 262 ms · ~1 400 series/s |
| Bajada completa de 3 600 series | 2.8 s en 10 páginas, sin huecos ni repetidos |
| Sincronización sin cambios | ~250 ms de ida y vuelta |
| Reenviar un lote | no duplica nada |

## Cuenta demo de 8 años (servidor real)

`db/demo/demo_extremo.py` crea una cuenta con un atleta inventado de 8 años: principiante en cuerpo completo,
torso/pierna, cierre de gimnasios por COVID (pierde fuerza y la recupera), empuje/jalón/pierna 6 días, un viaje a
Madrid (otra zona horaria) y su rutina actual con máquinas, superseries y drop sets. Peso corporal casi diario con
etapas de volumen y definición. La fuerza sigue rendimientos decrecientes y cada ejercicio usa doble progresión.

| Dato | Cantidad |
|---|---:|
| Entrenamientos | 1 722 |
| Series | 39 154 |
| Ejercicios registrados | 11 187 |
| Registros de peso | 2 485 |
| Rutinas / récords | 14 / 167 |

`QaRealServerTest` corre el código de sincronización de la app contra esa cuenta en Neon: **primera descarga 6.4 s**
(54 800 filas, todas exactas), y con esos datos Progreso tarda ≤ 33 ms, Tu progreso 59 ms y el historial completo 133 ms.

## Seguridad (servidor real)

Todas pasaron: no se pueden leer, crear, modificar ni borrar datos de otro usuario (se intentó con la cuenta real y
sus 2 rutinas siguen intactas); sin JWT o con JWT alterado → 401; datos imposibles (99 999 repeticiones, 5 000 kg) →
rechazados por el servidor; inyección SQL en filtros → tratada como texto; cuerpo de ~7 MB → rechazado sin caerse.

## Qué cubre cada suite

| Suite | Pruebas | Qué revisa |
|---|---:|---|
| DomainTest / NewFeaturesTest | 34 | Motor de progresión, 1RM, récords, peso corporal, unidades |
| QaDomainTest | 17 | Fuzzing, catálogo de ejercicios, las 4 050 combinaciones de la guía de rutinas, enlaces a estudios, textos sin restos técnicos |
| QaDataSyncTest | 8 | Sincronización sin huecos, saneado, filas rechazadas, red caída / error 500 / JSON basura, usuario extremo, cambio de cuenta, primera descarga de 8 años |
| QaRealServerTest | 1 | La sincronización de la app contra Neon real con la cuenta demo (solo con `RUPIO_DEMO_JWT`) |
| QaUiTest | 2 | 14 pantallas a 320 dp y letra al 200 %: textos completos, zonas táctiles ≥ 48 dp, contraste AA |
| NavTabsTest | 4 | Pestañas y doble «atrás» |
| MigrationTest | 1 | Actualizar desde la versión 1 de la base sin perder datos |
| ScreenshotTest / RestAlertTest | 7 | Capturas de referencia y alerta de descanso con un solo sonido |
