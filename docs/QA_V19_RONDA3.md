# Rupio 1.9 · tercera vuelta de QA

Volví a probar los pendientes de [QA_V19_VERIFICACION.md](QA_V19_VERIFICACION.md) con los cambios que todavía **no están en un commit**: van encima de `b6b5e09` y los describe la sección "Segunda vuelta" de [QA_V19.md](QA_V19.md).

- **Entorno**: emulador Pixel 8 Pro, Android 16, APK release compilado desde la copia de trabajo.
- **Cómo probé la actualización**: además de instalar encima de la versión anterior con datos, instalé la versión `57e6a64` (variante benchmark), registré dos entrenos y luego actualicé a la versión nueva. Así se ve si se recalculan los trofeos viejos.
- **Evidencia**: las capturas de esta vuelta van del `300_` al `309_` en `captures/qa/` (carpeta local).

## Resumen

Los 16 pendientes quedaron bien. Encontré 3 cosas nuevas, una de prioridad media. Las pruebas automáticas pasan completas, y el rendimiento es el mismo que la versión anterior medida en las mismas condiciones.

## Pendientes de la segunda vuelta

| # | Estado | Lo que vi |
|---|---|---|
| N1 Cardio | ✅ | Columna "MIN", 1 serie, "Descanso: Sin descanso" y consejo propio ("Anota cuántos minutos hiciste…"). En el editor de un entreno terminado también aparece en minutos. |
| N2 Duración y calorías | ✅ | Con 20 min de caminadora: "Duración 20 min" y "≈ 90 kcal" (3.8 MET × 70 kg × 20 min). Ya no sale "0 lb" en el resumen ni en el Historial. Ver R2. |
| N3 Gráficas | ✅ | Arrastrar hacia arriba sobre la gráfica de volumen sí mueve la página. |
| #6 Hoja de la serie | ✅ | Se abre completa y "Eliminar serie" queda arriba de la barra de gestos. |
| #21/N4 Otro día | ✅ | "Ya registraste 80.5 kg el 19 nov. Si guardas, se reemplaza." y el botón dice "Reemplazar". En un día sin registro dice "Guardar". |
| #8/N5 Trofeos viejos | ✅ | Con datos de `57e6a64` (1.ª sesión 🏆 3 falsos, 2.ª sesión 🏆 3 reales), después de actualizar la 1.ª queda sin trofeo, la 2.ª conserva 🏆 3 y Progreso dice "Récords 3". La app no se cerró al actualizar. |
| N6 Orden de búsqueda | ✅ | "lagartijas": primero Lagartijas, Lagartijas abiertas, Lagartijas cerradas… Ver R1: la lista no regresa arriba al cambiar el texto. |
| N7 Guardar como rutina | ✅ | Pide el nombre. La segunda vez propone "Entrenamiento libre (2)". |
| N8 Miniatura | ✅ | TalkBack dice "Ver técnica de Caminadora (cinta)". |
| N9 Horizontal | ✅ | Los filtros se van con el scroll y se ven 3 ejercicios a la vez. |
| N10 Nombre en el registro | ✅ | Con el nombre "Ana", el registro lo trae lleno. Con el nombre por defecto ("Atleta") el campo sale vacío, a propósito. |
| Plurales | ✅ | "1 ejercicio · 1 serie", "🔥 1 semana seguida", "1 serie/sem", "1 día entrenado" y "Si guardas sin marcarla". |

**Regresión.** Repetí estos caminos y siguen bien:
- "Agregar calentamiento" (C, 1, 2, 3).
- "1 serie tiene datos pero no la marcaste" → **Marcar y guardar**.
- La primera vez de un ejercicio no anuncia récord.
- Tocar la tarjeta de una rutina la abre.
- La rutina vacía no se guarda.

## Hallazgos nuevos

**R1. Media · El buscador no regresa arriba al cambiar el texto.**

**Qué pasa.** Cuando borras o cambias lo que buscas, la lista se queda anclada en el ejercicio que estaba arriba. Los mejores resultados, los que el arreglo de N6 pone primero, quedan escondidos arriba de la pantalla:
- De "remo" a "sentadilla", el primero visible es "Sentadilla hack con barra"; Sentadilla al cajón, búlgara y con barra quedan arriba.
- En "remo", el primero sale cortado ("Remo en landmine…").
- Con el campo vacío, la lista empieza en "Bicicleta fija" y no en "Abdominales".

Si escribes desde cero, sale bien. Es el comportamiento normal de una `LazyColumn` con llaves: conserva la posición del elemento que estaba arriba.

**Propuesta.** `LaunchedEffect(query, filtros) { listState.scrollToItem(0) }` en `ExercisePickerScreen.kt`.

**R2. Baja · En un entreno solo de cardio, las horas no cuadran con la duración.**

**Qué pasa.** El detalle dice "Empezó a las 01:19 · terminó a las 01:23" y, debajo, "Duración 20 min". Durante el entreno, la tarjeta de arriba decía 3:12 y "0 lb" aunque ya había 20 min de caminadora.

**Propuesta.** Aclarar "20 min (incluye 20 min de cardio)" o calcular la hora de inicio a partir de la duración, y que la tarjeta en vivo sume el cardio anotado.

**Captura.** `301_terminar_cardio_v3.png`.

**R3. Baja · El mensaje final no sirve para el cardio.**

**Qué pasa.** Al terminar un entreno solo de cardio sale "Cada sesión cuenta. La próxima vez Rupio te dirá cuánto subir", que está pensado para pesas.

**Propuesta.** Algo como "Anotaste 20 min de cardio. La próxima vez intenta un poco más".

## Pruebas automáticas

En una copia aparte (`b6b5e09` más estos cambios), con `testDebugUnitTest --rerun`: **195 pruebas, 192 pasan, 3 omitidas y 0 fallas**. Esta vez sí se ejecutaron, no salieron de la caché. `QaUiTest.pantallasEnElPeorCaso` ya pasa en la suite completa.

## Rendimiento

Comparé la versión anterior y la nueva en el mismo emulador y en el mismo momento, ambas con R8 (variante benchmark). Medí 3 corridas de scroll en el selector de ejercicios:

| | 1.ª corrida | 2.ª | 3.ª |
|---|---|---|---|
| `57e6a64` | 5.2 % con retraso · p90 38 ms | 6.1 % · 38 ms | 2.9 % · 18 ms |
| Ahora | 10.9 % · 46 ms | 3.5 % · 36 ms | 2.9 % · 18 ms |

La 1.ª corrida incluye cargar las fotos por primera vez. A partir de la 2.ª, las dos versiones quedan iguales: el p90 de 40 ms de la vuelta pasada era el emulador, no la app.

Arranques:
- **En frío**: 0.45–0.49 s.
- **En caliente**: 0.15 s.
- **Primero después de actualizar con datos**: 1.07–1.14 s (la vez pasada salió 3.9 s porque la máquina estaba compilando).

## Datos creados

En esta vuelta no creé cuentas ni escribí nada en el servidor. La variante benchmark que usé para probar la actualización ya está desinstalada del emulador.
