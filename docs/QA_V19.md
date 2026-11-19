# Rupio 1.9 · arreglos de la auditoría de QA

Respuesta a `docs/QA_V19_HALLAZGOS.md`. Cada punto tiene su prueba en `V19QaTest.kt` (lógica) o `V19QaUiTest` (Inicio), salvo los cambios solo visuales, que se ven en las capturas de `app/screenshots/`.

## Lo más urgente
- **#2 Series con datos sin ✓**: al terminar, las series vacías ya no dan aviso naranja; las que tienen peso, reps o tiempo escritos sin marcar se listan aparte ("1 serie tiene datos pero no la marcaste: Press de banca: serie 2") con **Marcar y guardar**. Si a alguna le falta algo que no se puede sugerir (el peso la primera vez), no termina y dice cuál completar. "Agregar serie" ya no copia el peso de la anterior como si lo hubieras escrito: sale en gris como sugerencia y se usa al marcar ✓.
- **#1 Primer uso**: bienvenida de 4 a 2 pantallas; sin globos en Inicio; el entreno tiene 2 globos ("Marca cada serie" y "Termina y guarda"). Lo demás lo explica cada control al tocarlo (el descanso dice que arranca solo y suena una vez). El permiso de notificaciones se pide al marcar la primera ✓, cuando arranca el primer descanso; la tarjeta "Notificaciones desactivadas" solo sale si ya se pidió y se negó.
- **#11 Qué rutina toca**: la siguiente de la rotación (la que sigue a la última que terminaste) va primero con "Te toca hoy" ("La próxima" si ya entrenaste hoy). Cada tarjeta dice "Última vez: hace 2 días" o "Aún no la haces".
- **#3 Encabezado fijo**: con letra grande o en horizontal, una sola línea ("4:21 · 2,400 lb · 3 series"). En la tabla, los números crecen hasta 150 % (como `FitText`) y los encabezados se acortan donde no caben ("#", "ANTES", "SEG").
- **#12 Rutina vacía**: no se guarda y dice "Agrega al menos un ejercicio para guardar la rutina". "Empezar rutina" se desactiva en rutinas viejas sin ejercicios.

## Entreno
- **#4** "Agregar calentamiento" inserta una serie C antes de la primera efectiva. El calentamiento conserva la sugerencia de reps y después descansas 60 s (o menos si tu descanso es menor). El plan de la rutina se empareja por tipo: agregar un calentamiento no recorre los pesos planeados.
- **#5** La numeración que se ve ("C, 1, 2") es la misma en la barra del descanso, la notificación, TalkBack, la hoja de la serie y el editor (`SetNumbering`).
- **#6** La hoja de la serie respeta la barra de gestos y tiene scroll.
- **#7** La notificación dice lo que falta como título ("Quedan 1:24 de descanso"), con barra que se vacía; el total va chico ("de 1 min 30 s"). Se actualiza cada segundo solo durante el descanso.
- **#8** La primera vez de un ejercicio se guarda la marca sin anunciarla: ni "¡Nuevo récord!", ni trofeo, ni "Rompiste 3 récords", ni cuenta en Progreso. Solo es récord lo que supera una marca de otro entreno (también en "Récords recientes", que ya no lista la mejor serie por volumen). En SQL: `BEATS_EARLIER` en `Daos.kt`.
- **#9** Al unir en superserie se propone el descanso que ya tenía el ejercicio. Con descanso compartido, el descanso sale solo en la cabecera del grupo.
- **#10** "Deshacer" dura 10 s (`UNDO_MS`) y los avisos de la app van arriba, ya no encima de "Empezar rutina".

## Inicio, rutinas y ejercicios
- **#13** Toda la tarjeta de una rutina abre el editor.
- **#14** La guía usa la misma cuenta que Inicio (`RestPlanner.summarize`) y el mismo formato.
- **#15** Búsqueda con sinónimos (`ExerciseSearch`): desplante, gemelos, flexiones, cinta, correr, bici, espalda, pierna y algunas en inglés del gimnasio. 6 ejercicios de cardio por tiempo con sus fotos de dominio público de free-exercise-db (caminadora, bicicleta fija, elíptica, escaladora, remo de cardio y saltar la cuerda). Nuevos grupos "Cardio" y "Cuerpo completo", que no cuentan para las series por músculo. Burpees no se agregó porque free-exercise-db no tiene foto.
- **#16** Los 55 ejercicios que puede proponer la guía (todas las combinaciones de equipo, nivel y objetivo) traen técnica en 3 a 5 pasos y terminan con el error más común. Es el campo `cue` con un paso por línea, así que no hizo falta migrar la base. Catálogo v3: se vuelve a escribir al actualizar.
- **#17** Filtro "Espalda" (dorsales, espalda media, lumbar y trapecio; al elegirlo aparecen las partes) y título por fila ("Músculo", "Equipo").

## Historial, Progreso y Peso
- **#18** Al volver de "Editar", el detalle ya no repite la celebración ni el mensaje de apoyo.
- **#19** "Series por músculo" divide entre las semanas desde tu primer entreno; en tu primera semana muestra solo el número, sin "poco".
- **#20** "1 dic 2025 – 18 nov 2026" cuando los años son distintos.
- **#21** Pestaña Peso: botón "Registrar otro día".
- **#22** La primera vez el campo va vacío. "Guardar" sin la casilla marcada la resalta y dice "Marca la casilla para poder guardar tu peso". Con un solo registro no se dibuja la gráfica.

## Perfil y cuenta
- **#23** Sin cuenta, la tarjeta "Tus datos se guardan solo en este teléfono" va justo después del nombre. El Muro de agradecimientos quedó solo dentro de Apoya Rupio. El botón rojo dice "Borrar mis datos y salir".
- **#24** Registro: cada campo con su error, todos a la vez (`AuthValidation`).
- **#25** "¿Correo equivocado?" regresa al nombre que tenías sin cuenta.

## Pulido
- El teclado se abre solo al cambiar el nombre del entreno o el tuyo (texto seleccionado) y al tocar la lupa del Historial.
- Plurales en Historial, Progreso, Perfil, Peso y gráficas (`Fmt.plural`).
- "Horas en tu zona" solo si algún entreno se hizo en otra zona.
- Descartar un entreno sin nada registrado no pide confirmación.
- "Mantén 100 lb" usa el ícono de repetir (no "+") y dice "la vez pasada".
- Drop set: "Bajas el peso y sigues sin descansar".

## Pruebas automáticas
- 189 pruebas (18 nuevas): 186 pasan y 3 se omiten (la que ya se omitía y las 2 de exportar en Windows).
- Las 2 de exportar fallaban porque `FileProvider` solo reconoce rutas con `/` y en Windows Robolectric arma la caché con `\` ("Failed to find configured root"). En el teléfono funciona. La parte de compartir se omite en Windows con `assumeTrue` y corre completa en Linux o en CI; la generación de los CSV sigue probándose en todos lados.
- Pruebas que cambiaron a propósito: la bienvenida de 2 páginas (`ScreenshotTest`), la casilla del peso (`V17UiTest`), el botón de salir sin cuenta (`SupportScreenshotTest`) y el trofeo al editar, que ahora necesita una sesión anterior (`V17Test`).

## Pendiente
- Probar en un teléfono real: el permiso de notificaciones en la primera ✓, la notificación cada segundo con la pantalla apagada y la hoja de la serie con navegación por gestos.
- El catálogo del servidor (`002_seed_exercises.sql`) no tiene los ejercicios de cardio. La sincronización no lo necesita (`exercise_id` es texto sin llave foránea), pero `rupio_muscle_sets` del servidor no los contaría.

## Segunda vuelta (docs/QA_V19_VERIFICACION.md)
Las pruebas están en `V19QaTest.kt` (las que empiezan con `n`).
- **N1 Cardio**: se anota en minutos ("20", abajo "20:00"; encabezado "MIN"), trae 1 serie, no arranca descanso y tiene su consejo ("Anota cuántos minutos hiciste…", luego "Intenta 22 min"). También en el editor del entreno y en el de la rutina. Es cardio lo que está en el grupo Cardio y se registra por tiempo (`Cardio.isCardio`).
- **N2 Duración y calorías**: el entreno dura al menos lo que anotaste de cardio. Las calorías separan pesas (3.5 MET por el reloj menos el cardio) y cardio (MET de cada máquina a esfuerzo moderado, Compendio 2024: caminadora 3.8, bicicleta fija 5.8, elíptica 5.0, escaladora 9.3, remo 5.0, cuerda 11.0). Sin pesas no sale "0 lb" ni "0 repeticiones" (Historial, detalle y resumen).
- **N3 Gráficas**: solo el arrastre horizontal es de la gráfica; el vertical mueve la página (Progreso, 1RM, ejercicio y peso).
- **#6 Hoja de la serie**: se abre completa (`skipPartiallyExpanded`).
- **#21/N4 Otro día**: si ese día ya tiene peso, lo dice ("Ya registraste 80.5 kg el 18 nov. Si guardas, se reemplaza.") y el botón dice "Reemplazar".
- **#8/N5 Trofeos viejos**: una vez al actualizar se recalculan con la regla nueva el 🏆 de cada serie y los récords de cada entreno (`recomputeTrophies`). Recorre el historial en orden, ejercicio por ejercicio, y solo cambia y sube lo que no cuadra. También corre después de pasar los datos sin cuenta a una cuenta.
- **N6 Búsqueda**: primero los nombres que empiezan con lo que escribiste, luego los que lo contienen y al final los sinónimos y el músculo.
- **N7 Guardar como rutina**: pide el nombre y propone "Pierna casa (2)" si ya existe.
- **N8 Miniatura**: la foto del ejercicio se anuncia como "Ver técnica de …" (en el entreno y en el editor de rutinas). `QaUiTest.pantallasEnElPeorCaso` pasa en la suite completa.
- **N9 Horizontal**: en pantallas bajas los filtros del selector se van con el scroll y el botón de abajo es más bajo.
- **N10 Registro**: desde el modo sin cuenta, el campo Nombre ya trae tu nombre.
- **Plurales**: "1 ejercicio · 1 serie", "1 semana seguida", "1 serie/sem", "Rompiste 1 récord personal", "Si guardas sin marcarla".
- **Pruebas**: 195 con `--rerun` (sin caché): 192 pasan y 3 se omiten (las mismas de antes).

## Tercera vuelta (docs/QA_V19_RONDA3.md)
- **R1 Buscador**: al cambiar la búsqueda o un filtro, la lista regresa arriba; al volver del detalle de un ejercicio sigue donde estaba. La prueba `V19QaUiTest.r1_…` falla sin el arreglo y pasa con él.
- **R2 Duración del cardio**: en el detalle, debajo de las horas reales, "Incluye 20 min de cardio que anotaste" cuando la duración es mayor que el reloj. Durante el entreno, el encabezado cuenta el cardio marcado y sin pesas no muestra "0 lb".
- **R3 Mensaje final**: un entreno solo de cardio dice "Anotaste 20 min de cardio. La próxima vez intenta un poco más."
- **Catálogo del servidor**: `db/migrations/010_cardio.sql` agrega los 6 de cardio y hace que `rupio_muscle_sets` no cuente "cardio" ni "cuerpo_completo". **Aplicada en production el 18 nov 2026** con `db/neon/aplicar.ps1` (proyecto Rupio, rama production, `ep-wild-art-bdlip6s4`). Verificado: los 6 ejercicios están en `public.exercises` y la función excluye esos grupos.
- **Pruebas**: 197 con `--rerun`: 194 pasan y 3 se omiten. En una corrida falló una vez `V17UiTest.n2_n9…` por un archivo de DataStore bloqueado en Windows (la instancia de `preferencesDataStore` es única por proceso y queda apuntando a la carpeta temporal de la primera prueba que la usa). La siguiente corrida pasó completa.
