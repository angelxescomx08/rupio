# Pendientes de QA (después de la tercera vuelta)

Lista de lo que falta arreglar, sacada de [QA_V19_RONDA3.md](QA_V19_RONDA3.md). Cada punto dice dónde está, qué cambiar y cómo comprobarlo. Las líneas son de la copia de trabajo de hoy (cambios sin commit encima de `b6b5e09`).

## En el código

### R1. Media · El buscador de ejercicios no regresa arriba al cambiar el texto

**Qué pasa.** Al borrar o cambiar la búsqueda, la `LazyColumn` conserva la posición del ejercicio que estaba arriba (lo hace por las llaves `key = { it.id }`). Los mejores resultados quedan escondidos arriba: de "remo" a "sentadilla", el primero visible es "Sentadilla hack con barra".

**Dónde.** `ui/exercises/ExercisePickerScreen.kt`. La lista está en la línea 117 y el estado de la búsqueda en las líneas 49-51.

**Cambio.** Darle un estado a la lista y regresarla arriba cuando cambie la búsqueda o un filtro. La primera emisión se salta, para no perder la posición al volver del detalle de un ejercicio (la búsqueda es `rememberSaveable`):

```kotlin
val listState = rememberLazyListState()
LaunchedEffect(listState) {
    snapshotFlow { Triple(query, muscle, equipment) }
        .drop(1)                       // no al entrar ni al volver del detalle
        .collect { listState.scrollToItem(0) }
}
...
LazyColumn(Modifier.weight(1f), state = listState, contentPadding = ...)
```

**Cómo comprobarlo.**
- Escribe "remo", baja un poco, cambia a "sentadilla": el primer resultado visible debe ser el primero de la lista ordenada.
- Borra todo: debe empezar en "Abdominales".
- Abre el detalle de un ejercicio y regresa: la lista sigue donde estaba.
- En horizontal, el primer elemento es la fila de filtros (`key = "filtros"`), así que `scrollToItem(0)` la muestra; eso está bien.

**Prueba sugerida.** En `V19QaUiTest`: escribir "remo", hacer scroll, reemplazar por "sentadilla" y verificar que el primer resultado esté visible (`onNodeWithText(...).assertIsDisplayed()`).

### R2. Baja · En un entreno solo de cardio, las horas no cuadran con la duración

**Qué pasa.**
- En el detalle dice "Empezó a las 01:19 · terminó a las 01:23" y debajo "Duración 20 min".
- Durante el entreno, el encabezado muestra el reloj (3:12) y "0 lb" aunque ya hay 20 min de caminadora anotados.

**Dónde.**
- Detalle: `ui/history/WorkoutDetailScreen.kt:144-151` (línea de horas y tarjeta "Duración").
- En vivo: `ui/workout/ActiveWorkoutScreen.kt:186-195` (`dur`, la línea compacta y las tarjetas).

**Cambio.**
1. **Detalle**: no cambiar `startedAt` ni `endedAt`, porque son datos reales y de ellos dependen el día y la sincronización. Si `durationSec` es mayor que `endedAt - startedAt`, agregar debajo de las horas una línea en `TextDim`: "Incluye 20 min de cardio que anotaste".
2. **En vivo**: sumar el cardio marcado y usar el mayor de los dos.
   ```kotlin
   val cardioSec = items.filter { it.isCardio }
       .sumOf { e -> e.sets.filter { it.completed }.sumOf { (it.durationSec ?: 0).toLong() } }
   val clockSec = ((now - w.startedAt) / 1000).coerceAtLeast(0)
   val dur = Fmt.duration(maxOf(clockSec, cardioSec))
   ```
   Ocultar "Volumen" cuando `volume == 0.0` y todo lo marcado es cardio, igual que ya se hace en el detalle (línea 151). En la línea compacta, quitar el "· 0 lb" en ese caso.

**Cómo comprobarlo.** En un entreno libre, agrega Caminadora, anota 20 y marca ✓:
- El encabezado debe decir 20:00 o más y no mostrar "0 lb".
- Al terminar, el detalle dice "Duración 20 min" con la línea "Incluye 20 min de cardio que anotaste".

### R3. Baja · El mensaje final de un entreno solo de cardio habla de pesas

**Qué pasa.** Al terminar solo cardio sale "Cada sesión cuenta. La próxima vez Rupio te dirá cuánto subir."

**Dónde.** `ui/history/WorkoutDetailScreen.kt:129`.

**Cambio.** Si el entreno no tuvo pesas (`workout.totalVolumeKg == 0.0`) y tiene cardio, mostrar "Anotaste 20 min de cardio. La próxima vez intenta un poco más." Los minutos salen de las series de cardio, igual que `parts` en la línea 160; conviene calcularlos antes del bloque de la celebración. Si tuvo pesas y cardio, se queda el mensaje actual.

**Cómo comprobarlo.**
- Un entreno solo con Caminadora muestra el mensaje de cardio.
- Uno con Prensa más Caminadora muestra el de siempre.

## Fuera del código (no se puede probar en el emulador)

Vienen de la sección "Pendiente" de [QA_V19.md](QA_V19.md) y de lo que no probé en ninguna vuelta.

| Qué | Cómo |
|---|---|
| Permiso de notificaciones en la primera ✓ | Teléfono real, instalación limpia. |
| Notificación del descanso cada segundo con la pantalla apagada (batería y que no se congele) | Teléfono real, pantalla apagada durante 2 descansos. |
| Hoja de la serie con navegación por gestos y por botones | Teléfono real con cada modo. |
| Catálogo del servidor sin los ejercicios de cardio (`rupio_muscle_sets` no los contaría) | Agregar los 6 de cardio a `002_seed_exercises.sql` o a una migración nueva. |
| Sincronización con cuenta real, Ligas y Muro con cuenta, cerrar sesión con datos | Cuenta de prueba con correo confirmado. |
| Compras de Apoya Rupio | Prueba interna de Play con cuentas de prueba de licencias. |
| TalkBack de punta a punta y rendimiento en gama baja | Teléfono real. |

## Al terminar

- Correr `testDebugUnitTest --rerun` (sin `--rerun`, Gradle puede tomar los resultados de la caché).
- Repetir los "Cómo comprobarlo" de R1, R2 y R3.
- Hacer commit: los cambios de la segunda vuelta y los reportes `QA_V19_VERIFICACION.md` y `QA_V19_RONDA3.md` siguen sin commit.
