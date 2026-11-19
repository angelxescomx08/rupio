# Rupio 1.9 · segunda vuelta de QA

Volví a probar la app después de los arreglos de `b6b5e09` ("new things"), que responden a [QA_V19_HALLAZGOS.md](QA_V19_HALLAZGOS.md) y a [QA_V19.md](QA_V19.md). Revisé uno por uno los 25 hallazgos y el pulido, probé lo nuevo (cardio, técnica en pasos, "Te toca hoy", "Registrar otro día", descanso por defecto) y repetí los caminos que ya funcionaban.

- **Entorno**: emulador Pixel 8 Pro, Android 16, APK release 1.9 compilado desde `b6b5e09`.
- **Dos maneras de instalar**: primero actualicé encima de la versión anterior con datos (entrenos, rutinas y peso) y después hice una instalación limpia.
- **Evidencia**: las capturas de esta vuelta van del `200_` al `229_` en `captures/qa/` (carpeta local, git la ignora).

## Resumen

De los 25 hallazgos, **22 quedaron bien**, **3 a medias** (#6, #8 y #21) y ninguno sigue igual. Los cambios crearon algunos problemas nuevos, sobre todo en el cardio, y apareció uno que ya existía y no había detectado (las gráficas bloquean el scroll).

Lo que yo atacaría primero:
1. **N1 y N2**: el cardio se registra en segundos, trae 3 series con descanso y no cuenta en la duración ni en las calorías.
2. **N3**: arrastrar el dedo sobre una gráfica de Progreso no mueve la página.
3. **#6**: la hoja de la serie se abre a media altura y "Eliminar serie" sigue debajo de la barra de gestos.
4. **N4**: "Registrar otro día" reemplaza sin avisar el peso de un día que ya tenía registro.

## Estado de cada hallazgo

| # | Estado | Lo que vi |
|---|---|---|
| 1 | ✅ | Bienvenida de 2 pantallas, sin globos en Inicio y 2 globos en el entreno. El permiso de notificaciones se pide al marcar la primera ✓ y explica "Tu descanso ya empezó…". Con la guía son 14 toques hasta el primer peso (antes 24); sin la guía, 3 toques hasta Inicio. |
| 2 | ✅ | "1 serie tiene datos pero no la marcaste · Peso muerto rumano en Smith: serie 2" con **Marcar y guardar**: guardó las 2 series (1,160 lb). Detalle: con una sola serie dice "Si guardas sin marcar**las**". |
| 3 | ✅ | A 200 %, una sola línea "0:17 · 0 lb · 0 series" (llega al 20 % de la pantalla, antes al 56 %) y encabezados "#", "ANTES". En horizontal también sale compacto. |
| 4 | ✅ | "Agregar calentamiento" mete una serie C antes de la 1, quedan las 3 series de trabajo, la C conserva "sugerido 8" y después descansas 60 s. |
| 5 | ✅ | Con "C, 1, 2", la barra y la notificación dicen "serie 1", igual que la tabla y la hoja. |
| 6 | ⚠️ | La hoja se abre a media altura y "Eliminar serie" sigue cortado, con la rayita de gestos encima (`204_hoja_serie.png`). Al arrastrar la hoja hacia arriba ya queda bien (`205_`). Propuesta: abrirla completa (`skipPartiallyExpanded = true`). |
| 7 | ✅ | "Quedan 0:41 de descanso", "de 1 min" y barra 41/60. "+15 s" desde la notificación funciona. |
| 8 | ⚠️ | Los entrenos nuevos ya no anuncian récord la primera vez. Pero los entrenos guardados **antes de actualizar** conservan sus 🏆 (3, 2 y 3 en Historial) aunque eran primeras veces (`211_eliminar_deshacer.png`). Falta recalcular los récords una vez al actualizar. |
| 9 | ✅ | Al unir en superserie propone el 1 min 30 s que ya tenía, y el descanso compartido sale una sola vez. |
| 10 | ✅ | Deshacer a los 7.6 s devolvió el ejercicio. Al borrar un entreno, el aviso sale arriba y "Deshacer" lo regresa. |
| 11 | ✅ | "▶ Te toca hoy" y "▶ La próxima", "Última vez: hoy" y "Aún no la haces". La rotación pasa de A a B. |
| 12 | ✅ | "Agrega al menos un ejercicio para guardar la rutina." |
| 13 | ✅ | Tocar la tarjeta abre "Editar rutina". |
| 14 | ✅ | La guía y la tarjeta de Inicio dicen ≈ 39 min. |
| 15 | ✅ | caminadora, cardio, desplante, gemelos, bici, espalda y abs ya encuentran resultados; burpee sigue en 0 (estaba avisado). El ejercicio propio ya tiene "Cardio" y "Cuerpo completo". Ver N6 sobre el orden de los resultados. |
| 16 | ✅ | Prensa de piernas trae técnica en pasos numerados (`217_tecnica_pasos.png`). Los ejercicios que no salen en la guía siguen con una sola frase (estaba avisado). |
| 17 | ✅ | Filtro "Espalda" y títulos "Músculo" y "Equipo". |
| 18 | ✅ | Después de editar, el detalle ya no repite la celebración ni el mensaje de apoyo. |
| 19 | ✅ | "Llevas menos de una semana: aquí verás si cada músculo recibe lo que necesita", sin "poco". |
| 20 | ✅ | "1 dic 2025 – 19 nov 2026". |
| 21 | ⚠️ | El botón "Registrar otro día" existe y propone "Ayer". Pero si ese día ya tenía peso, lo reemplaza sin avisar (ver N4). |
| 22 | ✅ | El campo sale vacío la primera vez y aparece "Marca la casilla para poder guardar tu peso". |
| 23 | ✅ | Sin cuenta, la tarjeta "Tus datos se guardan solo en este teléfono" va justo después del nombre. El Muro ya no está en Perfil y el botón dice "Borrar mis datos y salir". |
| 24 | ✅ | Salen los tres errores a la vez, cada uno bajo su campo. |
| 25 | ✅ | Tras "¿Correo equivocado?" volvió el nombre anterior ("QA temporal"). |

**Pulido:**

| Punto | Estado | Lo que vi |
|---|---|---|
| Teclado | ✅ | Se abre solo al cambiar el nombre del entreno y en la búsqueda del Historial. |
| Zona horaria | ✅ | "Horas en tu zona" ya no sale. |
| Descartar vacío | ✅ | Un entreno sin series se descarta sin preguntar. |
| Recomendación | ✅ | "la vez pasada tu serie más floja fue de 8". |
| Drop set | ✅ | "Bajas el peso y sigues sin descansar". |
| Plurales | ⚠️ | Quedan "1 ejercicios · 1 series" en la tarjeta de rutina (`HomeScreen.kt:293-294`), "🔥 1 semana seguidas" (`StatsScreen.kt:156`) y "1 series/sem" (`Charts.kt:211`). |

## Hallazgos nuevos

**N1. Media · El cardio se registra como una plancha.**

**Qué pasa.** Caminadora, bicicleta y elíptica:
- Piden **SEGUNDOS**: 20 minutos de caminadora son "1200". Abajo sí muestra "20:00".
- Traen **3 series** con un descanso de 1 min 30 s entre ellas. Después de la primera arranca el descanso con "Siguiente: Caminadora (cinta) · serie 2" (`226_cardio_completado.png`).
- El consejo de "Primera vez" es el de los ejercicios de aguante: "Aguanta con buena técnica y anota tu tiempo; la próxima intentarás superarlo".

**Propuesta.** Para el grupo Cardio: capturar en minutos (mm:ss), 1 serie por defecto, sin descanso automático y un consejo propio (por ejemplo, "Anota cuántos minutos hiciste; si quieres, sube un poco la velocidad o la inclinación la próxima vez").

**N2. Media · La duración y las calorías ignoran el tiempo del cardio.**

**Qué pasa.** Con 20:00 de caminadora registrados, el resumen dice "Duración 2 min" y "≈ 10 kcal", y el Historial "2 min · 0 lb · 1 serie" (`227_terminar_cardio.png`). La duración es la del reloj del entreno, y las calorías usan esa duración con los 3.5 MET de las pesas. Quien anota su caminata al terminarla ve un número que no tiene que ver con lo que hizo.

**Propuesta.** Usar como duración el máximo entre el reloj y la suma de los tiempos de cardio, calcular las calorías del cardio con su propio MET (Compendio 2024) y no mostrar "0 lb" cuando el entreno es solo cardio.

**N3. Media · Arrastrar sobre una gráfica no mueve la página.**

**Qué pasa.** En Progreso, si el dedo empieza sobre una gráfica (volumen, 1RM, año), la página no se desplaza: la gráfica se queda con el gesto para mover la barra elegida. Como las gráficas ocupan buena parte de la pantalla, hay que buscar los márgenes para bajar. Esto ya pasaba antes y no lo había detectado.

**Propuesta.** Usar `detectHorizontalDragGestures` en lugar de `detectDragGestures`, para que el gesto vertical llegue a la página.

**Dónde.** `Charts.kt:83` y `:170`.

**N4. Baja · "Registrar otro día" reemplaza sin avisar.**

**Qué pasa.** El 18 nov ya tenía 80.5 kg. Con "Registrar otro día" → "Ayer" (18 nov) → 81.2, el 80.5 desapareció sin aviso y quedó "1 registro".

**Propuesta.** Si el día ya tiene peso, mostrarlo y preguntar "¿Reemplazar 80.5 kg del 18 nov?".

**N5. Baja · Los trofeos de antes de actualizar no se recalcularon.**

**Qué pasa.** Es la parte pendiente de #8. Las primeras sesiones guardadas con la versión anterior siguen con 🏆 3 en Historial.

**Propuesta.** Correr `rebuildAllRecords()` una vez al actualizar, junto con el catálogo v3.

**N6. Baja · Los sinónimos salen antes que el nombre exacto.**

**Qué pasa.** Al buscar "lagartijas", los primeros resultados son "Flexión lateral con mancuerna" (un ejercicio de oblicuos) y "Flexiones de pino". "Lagartijas" sale tercero (`216_busqueda_lagartijas.png`).

**Propuesta.** Ordenar primero las coincidencias del nombre y después las de sinónimos o músculo.

**N7. Baja · "Guardar como rutina" repite el nombre.**

**Qué pasa.** Creó una segunda "Pierna casa" (con 1 ejercicio) junto a la original, y la rotación la marcó como "La próxima".

**Propuesta.** Pedir el nombre o proponer "Pierna casa (2)".

**N8. Baja · La foto del ejercicio es un botón sin nombre.**

**Qué pasa.** En el entreno, la miniatura abre el detalle, pero no tiene descripción: TalkBack solo dice "botón". La prueba `QaUiTest.pantallasEnElPeorCaso` lo detecta en `12_entrenamiento` y `13_terminar` cuando se corre la suite completa (ver Pruebas).

**Propuesta.** Agregarle una descripción como "Ver técnica de Prensa de piernas", o unir la foto con el nombre en un solo botón.

**N9. Baja · El selector de ejercicios en horizontal.**

**Qué pasa.** Entre la búsqueda, las dos filas de filtros y el botón de abajo, solo se ve un ejercicio a la vez (`228_horizontal_v2.png`).

**N10. Baja · El registro no aprovecha el nombre que ya tienes.**

**Qué pasa.** Desde el modo sin cuenta, el campo "Nombre" del registro sale vacío aunque la app ya sabe cómo te llamas.

## Lo que sigue funcionando

- **Las validaciones del entreno**: "Escribe el peso…" y "Escribe las repeticiones…".
- **Doble toque en ✓, Atrás a media sesión y girar la pantalla** no pierden nada.
- **La actualización con datos** no tuvo errores ni cierres: entrenos, rutinas y peso siguen ahí, y los ejercicios de cardio y la técnica nueva aparecen.
- **El descanso por defecto en Perfil** (nuevo) se guarda: "1 min · para ejercicios nuevos".
- **La cuenta**: el código incorrecto da un mensaje claro y "¿Correo equivocado?" borra la cuenta del servidor (verificado).

## Pruebas automáticas

En una copia aparte del repo en `b6b5e09`:
- **Sin `--rerun`**, Gradle tomó los resultados de su caché (189 pruebas, 0 fallas, 3 omitidas). Esos resultados son de tu corrida, no de la mía.
- **Con `--rerun`** (ejecutándolas de verdad): 189 pruebas, **185 pasan, 3 omitidas y 1 falla**. Falla `QaUiTest.pantallasEnElPeorCaso`: "botón sin texto ni descripción en (56, 1153)" en `12_entrenamiento` y `13_terminar`, que es la miniatura de N8.
- **Esa clase sola** (`--tests com.rupio.app.QaUiTest --rerun`) pasa 3 de 3. La prueba depende de lo que corrió antes. Lo más probable es que, según si la imagen carga o no, la miniatura tenga descripción o no. Arreglar N8 debería volverla estable.

Las 2 de exportar en Windows ahora se omiten, como dice `QA_V19.md`.

## Rendimiento

La primera vez medí con la máquina compilando y los números salieron muy malos (78 % de cuadros con retraso). Una medición de control en Ajustes de Android también salió lenta, así que el problema era el emulador cansado, no la app. Reinicié el emulador y volví a medir:

| Medición | Antes (57e6a64) | Ahora (b6b5e09) |
|---|---:|---:|
| Primer arranque tras instalar | 1.27 s | 1.43 s |
| Arranque en frío | 0.39 s | 0.40–0.49 s |
| Arranque en caliente | 0.14 s | 0.16 s |
| Scroll del selector, cuadros con retraso | 2.5 % | 2.9 % |
| Scroll del selector, p90 | 22 ms | 40 ms |
| APK | 11.8 MB | 12.1 MB (fotos del cardio) |

No hay una regresión clara, pero el p90 del scroll subió. El primer arranque después de actualizar con datos tardó 3.9 s, mientras la máquina estaba ocupada: incluye reescribir el catálogo v3. Vale la pena medir las dos cosas en un teléfono de gama baja.

## Datos creados en producción

- **Cuenta temporal `qa-cdc882a56e@rupio-qa.test`**: la creé para probar #25 y la borré con "¿Correo equivocado?". Entrar con ella devuelve `401 INVALID_EMAIL_OR_PASSWORD`.
- **Correos**: no llegó ninguno a una persona, porque `.test` no es un dominio real.
- **La de la primera vuelta** (`qa-177a046304@rupio-qa.test`) también sigue borrada. No queda nada que limpiar.
