# v1.9 · Rendimiento de Apoya Rupio (Ligas, Muro) y paleta v2

Medido el 18 nov 2026. El servidor (migraciones 008 y 009) se midió antes en PostgreSQL 16 con 100 mil participantes:
ver `db/ligas/results_ligas.md` (tu liga 0.50 ms, top global 0.67 ms, tu lugar global 4.78 ms, lote de 400 entrenos 4–5 ms).

## En el teléfono (emulador Pixel 8 Pro, Android 16, compilación `benchmark` = release con R8)

Pantalla de medición `src/benchmark/.../LeagueBenchActivity.kt`: las mismas composables de la app con datos de ejemplo,
sin red. 12 deslizadas rápidas hacia abajo y 4 de regreso, 3 corridas por pantalla, `dumpsys gfxinfo`.

| Pantalla | Arranque en frío | Cuadros con tirón | p50 / p90 / p99 por cuadro | Hilo de UI lento |
|---|---:|---:|---|---:|
| Mi grupo (30 filas + zonas + tu lugar fijo) | 524–611 ms | 2.5–3.1 % | 17 / 18 / 19 ms | 0 |
| Global (de 50 en 50 hasta 2,000 filas) | 476–584 ms | 0.18 % | 17 / 18 / 19 ms | 0 |
| Muro (300 filas) | 516–553 ms | 0.9–1.1 % | 17 / 18–22 / 19–24 ms | 0 |

- **Cero cuadros con el hilo de UI lento** en todas las corridas: la composición y el acomodo de las filas nunca pasaron
  del presupuesto del cuadro. Todos los tirones que sí hubo son "Frame deadline missed" del lado de la GPU del emulador
  (dibuja con la tarjeta de la PC), no del código de la app. En un teléfono real conviene repetirlo antes de publicar.
- La primera corrida de cada pantalla incluye compilar el código con el perfil (`pm compile -m speed-profile`), como
  pasa tras instalar desde Play.

## Lectura de las respuestas (`LeaguePerfTest`, JVM de la PC; en un teléfono medio ~3–5× más)

| Respuesta | Tamaño | Directo a clases (v1.9) | Árbol JSON (como el muro) |
|---|---:|---|---|
| Tu grupo (30 filas) | 3.7 KB | 43–106 µs · 30 KB | 31–92 µs · 65 KB |
| Top global, página de 50 | 5.5 KB | 46–125 µs · 52 KB | — |
| Top global, página de 100 | 11 KB | 80–222 µs · 104 KB | — |
| Sumar una página a 1,000 filas (sin repetidos) | 11 KB | 77–187 µs · 134 KB | — |

Decodificar directo a clases reserva **2.2× menos memoria** (menos trabajo para el recolector mientras se desplaza);
en tiempo es parecido al árbol JSON (los rangos dependen de la carga de la PC). Todo pasa fuera del hilo principal y
es < 1 ms: lo que el usuario nota es la red (~250 ms por llamada), por eso:

- "Tu liga" y "tu lugar global" se piden **en paralelo** (una ida y vuelta, no dos), y si ya se sabe que no tienes grupo
  solo se pide una.
- No se vuelve a pedir si lo último tiene **menos de 30 s** (cambiar de pestaña no gasta red); la tarjeta de Progreso,
  **5 min**.
- Lo último se guarda por cuenta: la pestaña y la tarjeta se ven **al instante y sin internet**.
- El top global va en páginas de 50 con **keyset** (`k` + `at` exactos del último, nunca OFFSET) y pide la siguiente
  cuando faltan 15 filas, leyendo el desplazamiento en un `snapshotFlow` (desplazarse no recompone la lista).

## Recomposición (reportes del compilador de Compose, `gradlew :app:compileReleaseKotlin -PcomposeReports`)

Todas las composables nuevas son **restartable skippable** (con *strong skipping* activo): `LeagueContent`, `GroupList`,
`LeagueRowItem`, `RankRow`, `ZoneDivider`, `GroupMeBar`, `GlobalList`, `TopRowItem`, `GlobalMeBar`, `LeagueHeader`,
`LeagueCardContent`, `TierLadder`, `MetalDot`, `SupporterBadge`, `SupportTabs`. Los modelos (`League`, `LeagueRow`,
`TopRow`, `LeagueState`, `Metal`) son `@Immutable`: si cambia una fila, las demás se saltan.

Otras decisiones medibles:

- **Keys estables** (apodo + inicio de semana; en el muro nombre + fecha) y `contentType` por tipo de fila: al subir
  alguien de lugar, las demás filas no se vuelven a crear. Nunca hay keys repetidas (LazyColumn falla con ellas).
- **Filas sin `Surface`**: fondo con `Modifier` solo en tu fila.
- **Insignias animadas (Oro, Aurora) sin recomponer**: el reflejo y el giro se leen en la fase de dibujo/capa. Antes,
  la de Fan recomponía ~60 veces por segundo mientras Perfil estaba abierto.
- Solo se compone la pestaña visible de Apoya Rupio; cada una conserva su desplazamiento al ir y volver.

## Cómo repetirlo

```powershell
.\gradlew.bat :app:assembleBenchmark
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb shell am start -W -n com.rupio.app.bench/com.rupio.app.bench.LeagueBenchActivity --es screen group   # o global / wall
adb shell dumpsys gfxinfo com.rupio.app.bench reset   # deslizar unas veces y luego:
adb shell dumpsys gfxinfo com.rupio.app.bench
.\gradlew.bat :app:testDebugUnitTest --tests "com.rupio.app.LeaguePerfTest"
```
