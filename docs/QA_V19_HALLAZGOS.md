# Rupio 1.9 · auditoría QA de toda la app

Recorrí la app completa como alguien que nunca ha ido al gimnasio, pantalla por pantalla. El criterio fue uno solo: que se entienda a la primera, que no se pierda nada y que sea cómoda con el teléfono en la mano a media serie.

- **Entorno**: emulador Pixel 8 Pro, Android 16, APK release 1.9 (versionCode 19) compilado desde `main` (57e6a64). También probé con letra al 200 %, pantalla de 320 dp, en horizontal y sin internet.
- **Evidencia**: las capturas están en `captures/qa/` (carpeta local, git la ignora). Cada hallazgo dice qué captura ver.

## Qué se probó

| Área | Qué se probó |
|---|---|
| Primer uso | Inicio de sesión, "Probar sin cuenta", bienvenida, tutoriales de Inicio y del entreno, permiso de notificaciones |
| Guía para armar la rutina | Los 5 pasos, la propuesta, "Crear 2 rutinas" |
| Inicio | Semana, tarjetas de rutina, entreno en curso, nube del respaldo |
| Entreno | Marcar series, validaciones, tipos de serie y RPE, descanso (+15, −15, Saltar, cambiarlo), superseries, agregar varios ejercicios, quitar y deshacer, cambiar el nombre, terminar con nota, descartar, doble toque |
| Notificación | Cuenta regresiva, "+15 s" desde la notificación, aviso de fin con la app en segundo plano |
| Historial | Lista, detalle, editar un entreno terminado, eliminar, buscar, sin resultados |
| Rutinas | Crear desde cero, salir sin guardar, guardar vacía, eliminar |
| Ejercicios | Buscador, filtros, selección múltiple, detalle y técnica, botón de video, crear ejercicio propio |
| Progreso | Semana, Mes, 3 meses, Año, Histórico, "Tu progreso", "Cómo leerlas" |
| Peso corporal | Permiso, valores fuera de rango, guardar, corregir, registrar otro día, retirar el permiso |
| Perfil | Nombre, unidades kg y lb, aviso de fin de descanso, exportar CSV, Aprende, Apoya Rupio, Muro, Ligas |
| Cuenta | Errores del registro, contraseña incorrecta, sin internet, código del correo (correcto e incorrecto), "¿Correo equivocado?" |
| Robustez | Atrás a media sesión, cerrar la app a la fuerza, girar la pantalla, 320 dp, letra al 200 % |
| Pruebas automáticas | Las 171 pruebas del proyecto, en una copia aparte del repo |

## Lo más urgente

Si solo hay tiempo para cinco cosas, son estas:

1. **#2**: una serie con datos escritos pero sin ✓ se borra al terminar.
2. **#1**: hay que dar 24 toques y pasar 3 tutoriales antes de escribir el primer peso.
3. **#11**: Inicio no dice qué rutina toca hoy.
4. **#3**: con letra grande o en horizontal, el encabezado fijo deja muy poco espacio para las series.
5. **#12**: se puede guardar una rutina vacía.

## Hallazgos por área

### Primer uso

**1. Alta · 24 toques y 3 tutoriales antes del primer peso.**

**Qué pasa.** Este es el camino de quien abre la app por primera vez:
1. "Probar sin cuenta" (1 toque).
2. 4 pantallas de bienvenida (4 toques).
3. 2 globos de tutorial en Inicio (2 toques).
4. La guía para armar la rutina (8 toques).
5. "Empezar rutina" (1 toque).
6. Un diálogo propio de notificaciones y luego el de Android (2 toques).
7. 6 globos de tutorial encima del entreno (6 toques).

Los globos tapan justo las series que el usuario quiere llenar (captura `13_entreno.png`). Quien ve 12 globos seguidos aprende a tocar "Siguiente" sin leer.

**Propuesta.**
- Dejar la bienvenida en 2 pantallas: registrar series y "te dice cuándo subir".
- Quitar los globos de Inicio, porque el estado vacío ya explica qué hacer.
- En el entreno, dejar 2 globos ("Marca cada serie" y "Termina y guarda"). El resto debería salir como aviso corto la primera vez que el usuario toca ese control.
- Pedir el permiso de notificaciones al marcar la primera ✓, que es cuando arranca el primer descanso.

**Dónde.** `ui/learn/TutorialScreen.kt`, `ui/tutorial/Tutorial.kt` y los `coachTarget(...)` de `ActiveWorkoutScreen.kt` y `HomeScreen.kt`.

### Entreno

**2. Alta · Las series con datos pero sin ✓ se borran al terminar.**

**Qué pasa.** Escribí 110 lb en una serie sin tocar ✓ y luego toqué "Terminar". El aviso dijo "Hay 16 series sin marcar: no se guardarán". Ese número junta las series vacías del plan (eso es normal) con la serie que sí tenía datos (eso es trabajo perdido). Al guardar, las dos se borran igual.

**Propuesta.** Si hay series con peso o repeticiones escritos sin ✓, decir "1 serie tiene datos pero no la marcaste" y ofrecer **Marcar y guardar**. Las series vacías no necesitan aviso en naranja.

**Dónde.** `ActiveWorkoutScreen.kt:692` y `:718`, `WorkoutRepository.kt:341`.
**Captura.** `47_terminar_con_datos_sin_marcar.png`.

**3. Alta · El encabezado fijo deja muy poco espacio para las series.**

**Qué pasa.**
- Con la letra al 200 %, el título y las 3 tarjetas (Duración, Volumen, Series) se apilan y llegan hasta el px 1 670 de 2 992 (56 %). Ese bloque no se mueve al hacer scroll. Al abrir el entreno, la primera serie queda en el borde de abajo. Durante el descanso queda menos de un tercio de la pantalla para registrar.
- A 200 %, "SERIE" sale como "SE", "REPS" se parte en "REP / S" y los kilos salen como "45.4 …".
- En horizontal pasa lo mismo: solo se ve media fila de series.

**Propuesta.** Con letra grande o en horizontal, poner las 3 tarjetas en una sola línea ("4:21 · 2,400 lb · 3 series") o dejar que se vayan con el scroll. Usar encabezados cortos.

**Dónde.** `ActiveWorkoutScreen.kt:162-181`.
**Capturas.** `41_entreno_letra200.png`, `42_series_letra200.png` y `95_horizontal.png`.

**4. Media · Marcar "Calentamiento" quita una serie de trabajo.**

**Qué pasa.** La rutina tenía 3 series. Al marcar la serie 1 como calentamiento quedaron "C, 1, 2", así que solo 2 series cuentan. Además, la serie convertida perdió su sugerencia de 8 repeticiones: la casilla queda en "reps" y la ✓ responde "Escribe las repeticiones antes de marcar la serie". Después del calentamiento arranca el descanso completo de 2 min, igual que tras una serie fuerte. Quien quiere *agregar* un calentamiento termina con una serie menos sin darse cuenta.

**Propuesta.**
- Un botón "Agregar calentamiento" que inserte una serie C antes de la primera.
- Conservar la sugerencia de repeticiones al cambiar el tipo.
- Usar un descanso corto (unos 60 s) después de un calentamiento.

**Capturas.** `61_tras_calentamiento.png` y `62_check_calentamiento_sin_reps.png`.

**5. Media · La numeración de las series no coincide.**

**Qué pasa.** Con un calentamiento, la tabla muestra "C, 1, 2". Sin embargo, la barra del descanso y la notificación dicen "Siguiente: … · serie 2" para la que en pantalla es la "1". TalkBack y la pantalla de editar también cuentan el calentamiento como "Serie 1".

**Propuesta.** Usar en todos lados la misma numeración que ve el usuario.

**Dónde.** `ActiveWorkoutVM.kt:197-202`.

**6. Media · El botón "Eliminar serie" queda debajo de la barra de gestos.**

**Qué pasa.** En la hoja que se abre al tocar el número de la serie, el botón rojo "Eliminar serie" queda cortado al fondo, con la rayita de gestos de Android encima. Tocarlo puede mandar al usuario a la pantalla principal.

**Propuesta.** Aplicar `navigationBarsPadding()` (o los insets del `ModalBottomSheet`) al contenido de la hoja.

**Dónde.** `ActiveWorkoutScreen.kt:677`.
**Captura.** `60_tipo_serie.png`.

**7. Media · La notificación del descanso destaca el dato equivocado.**

**Qué pasa.** El título grande dice "Descanso · 1 min 30 s", que es el total. Lo que falta aparece en gris y chico junto a "Rupio · 01:24". Al tocar "+15 s", el título cambia a "1 min 45 s" y parece que es lo que queda. Con el teléfono bloqueado, lo único que importa es cuánto falta.

**Propuesta.** Que lo que falta sea el texto principal, por ejemplo una barra de progreso o la cuenta regresiva como título, y el total en segundo plano.

**Dónde.** `timer/WorkoutService.kt:57`.
**Captura.** `97_notificacion_descanso.png`.

**8. Media · "¡Nuevo récord!" desde la primera serie de la vida.**

**Qué pasa.** La primera serie que registré sacó "🏆 ¡Nuevo récord! Peso máximo · 1RM estimado · Más repeticiones", y el aviso tapó las series siguientes. Cada entreno con ejercicios nuevos dice "Rompiste 3 récords personales", y Progreso cuenta "Récords 4" en el primer día. Sin una marca anterior no hay récord, y celebrarlo todo le quita valor al récord de verdad.

**Propuesta.** La primera vez de cada ejercicio se guarda la marca sin anunciarla. Solo se celebra cuando se supera un valor anterior.

**Dónde.** `ActiveWorkoutScreen.kt:107`, `WorkoutDetailScreen.kt:113`.
**Captura.** `19_serie_completada.png`.

**9. Baja · Superseries.**

**Qué pasa.**
- Cambié el descanso del ejercicio a 1 min 30 s, lo uní en superserie y el descanso volvió a 2 min sin avisar.
- Dentro del bloque, "Descanso compartido: 2 min" aparece 3 veces (encabezado, A1 y A2), y cada ejercicio ocupa casi media pantalla.

**Propuesta.** Respetar el descanso que ya había elegido el usuario y mostrar el descanso compartido una sola vez, en el encabezado.

**Captura.** `65_superserie_vista.png`.

**10. Baja · "Deshacer" dura 5 s y su aviso tapa "Empezar rutina".**

**Qué pasa.** Quitar un ejercicio o borrar un entreno deja 5 s para deshacer. Al borrar un entreno, el aviso sale en Inicio justo encima de "Empezar rutina". Si el aviso se va mientras tocas "Deshacer", empiezas una rutina sin querer. A mí me pasó.

**Propuesta.** Darle 10 s (`SnackbarDuration.Long`) y subir el aviso para que no quede encima de un botón.

### Inicio y rutinas

**11. Alta · Inicio no dice qué rutina toca hoy.**

**Qué pasa.** La guía promete "Lunes A · Miércoles B · Viernes A, la semana siguiente sigue donde te quedaste". Después de hacer A, Inicio vuelve a mostrar A primero y no dice "te toca B" ni cuándo hiciste cada rutina.

**Propuesta.** Marcar con "Te toca hoy" la siguiente rutina de la rotación, ponerla primero y agregar en cada tarjeta "Última vez: hace 2 días".

**Dónde.** `HomeScreen.kt:276`.
**Captura.** `50_inicio_rutina_b.png`.

**12. Media · Se puede guardar una rutina vacía.**

**Qué pasa.** "Nueva" seguido de "Guardar", sin nombre y sin ejercicios, crea "Mi rutina" con "0 ejercicios · 0 series" y un botón "Empezar rutina" que abre un entreno vacío.

**Propuesta.** No permitir guardar sin ejercicios y decir "Agrega al menos un ejercicio".

**Dónde.** `RoutineEditorScreen.kt:166-167`.
**Captura.** `75_rutina_vacia.png`.

**13. Media · La tarjeta de una rutina no se abre al tocarla.**

**Qué pasa.** Solo responden "Empezar rutina" y el menú ⋮. Para ver o cambiar una rutina hay que encontrar ⋮ → Editar.

**Propuesta.** Hacer toda la tarjeta tocable para abrir el editor.

**Dónde.** `HomeScreen.kt:276`.

**14. Media · La misma rutina dura 34 min en la guía y 39 min en Inicio.**

**Qué pasa.** Hay dos fórmulas, `RoutineGuide.kt:50` y `RestSummary.estimatedSec` (`Training.kt:302`).

**Propuesta.** Usar una sola función en las dos pantallas.

**Capturas.** `10_propuesta_a.png` y `11_inicio_con_rutinas.png`.

### Ejercicios

**15. Media · El buscador no conoce palabras comunes y no hay cardio.**

**Qué pasa.**
- Estas búsquedas dan 0 resultados: caminadora, cinta, bicicleta, elíptica, cardio, burpee, gemelos y desplante (así se dice "zancada" en México).
- El catálogo no tiene ningún ejercicio de cardio.
- Al crear un ejercicio propio, la lista de músculos no tiene "Cardio" ni "Cuerpo completo". Una caminadora tendría que ir como "Cuádriceps", y eso infla el reparto de músculos y las series por semana.

**Propuesta.**
- Agregar sinónimos: desplante → zancada, gemelos → pantorrilla, flexión ↔ lagartija.
- Agregar 4 o 5 ejercicios de cardio por tiempo.
- Agregar "Cardio" como grupo, sin que cuente para las series por músculo.

**Captura.** `38_busqueda_caminadora.png`.

**16. Media · La técnica es una sola línea.**

**Qué pasa.** Cada ejercicio trae una frase de técnica: la mediana mide 51 caracteres, por ejemplo "Despega los hombros del piso apretando el abdomen; no jales el cuello". Para aprender, el principiante tiene que tocar "Buscar videos", que sale de la app; en el emulador se abrió Chrome.

**Propuesta.** Escribir 3 a 5 pasos cortos por ejercicio: posición, movimiento y el error más común. Empezar por los 30 ejercicios que usa la guía.

**Dónde.** Campo `cue` de `assets/exercises_es.json`.
**Captura.** `78_detalle_ejercicio.png`.

**17. Baja · Los filtros usan nombres de anatomía.**

**Qué pasa.** Los filtros son "Dorsales", "Espalda media", "Lumbar" y "Trapecio", en dos filas que se deslizan de lado, sin título, y la segunda fila sale cortada ("Peso cor…"). Un principiante busca "Espalda".

**Propuesta.** Un filtro "Espalda" que agrupe los tres, y un título por fila ("Músculo", "Equipo").

**Captura.** `67_multiseleccion.png`.

### Historial

**18. Baja · Después de editar un entreno vuelve a salir la celebración.**

**Qué pasa.** Al guardar los cambios, el detalle vuelve a mostrar "¡Entrenamiento guardado! Rompiste 3 récords" y el mensaje de apoyo. Ese mensaje dice "3,820 lb levantados", que es el total de antes de editar; con la corrección son 3,860.

**Propuesta.** Mostrar el bloque de celebración y el mensaje de apoyo solo al terminar un entreno.

**Captura.** `72_detalle_con_apoyo.png`.

### Progreso

**19. Media · "Series por músculo" le dice "poco" a quien acaba de empezar.**

**Qué pasa.** Con 3 series hechas esta semana, Mes dice "0.7 series/sem · poco". El cálculo divide entre todas las semanas del periodo (`Repositories.kt:189`), aunque el usuario empezó hoy.

**Propuesta.** Dividir entre las semanas desde el primer entreno cuando sean menos que el periodo. En la primera semana, mostrar solo el número, sin juicio.

**Captura.** `30_progreso_b.png`.

**20. Baja · El rango de "Año" se lee al revés.**

**Qué pasa.** Dice "1 dic – 18 nov 2026" porque solo pone el año al final.

**Propuesta.** Poner el año en las dos fechas cuando son distintos: "1 dic 2025 – 18 nov 2026".

**Dónde.** `StatsScreen.kt:217`.

### Peso corporal

**21. Media · En la pestaña Peso no se puede registrar un día que se te olvidó.**

**Qué pasa.** Después de registrar el peso de hoy, el único botón es "Corregir el de hoy", con la fecha bloqueada. La opción "Otro día" solo existe en Progreso → Peso corporal → Registrar.

**Propuesta.** Agregar en la pestaña Peso un botón secundario "Registrar otro día".

**Dónde.** `BodyWeight.kt:224` y `:297`.

**22. Baja · El primer registro de peso.**

**Qué pasa.**
- El campo ya trae 72 kg (o 160 lb), así que es fácil guardar un número inventado (`BodyWeight.kt:363`).
- Mientras la casilla de permiso está sin marcar, "Guardar" sale gris sin decir por qué (`BodyWeight.kt:427`).
- Con un solo registro, la gráfica muestra el eje "81 · 81 · 80" y "18 nov … 18 nov".

**Propuesta.** Dejar el campo vacío la primera vez, y que el botón resalte la casilla cuando falte marcarla.

**Captura.** `34_registrar_peso.png`.

### Perfil y cuenta

**23. Media · Sin cuenta, lo más importante de Perfil está al fondo.**

**Qué pasa.** Arriba están Apoya Rupio, Muro y Ligas. El aviso "Tus datos se guardan solo en este teléfono" y su botón "Crear cuenta y conservar mis datos" quedan después de dos scrolls. Abajo hay un botón rojo "Cerrar sesión" para alguien que no tiene sesión y que, al confirmarlo, borra todo.

**Propuesta.**
- Sin cuenta, poner primero la tarjeta del respaldo.
- Dejar Muro solo dentro de Apoya Rupio.
- Cambiar "Cerrar sesión" por "Borrar mis datos y salir".

**Dónde.** `ProfileScreen.kt:92-115`, `:178` y `:214`.
**Capturas.** `32_perfil.png` y `32_perfil_b.png`.

**24. Baja · Los errores del registro salen uno a la vez y lejos del campo.**

**Qué pasa.** Con correo inválido y una contraseña de 3 caracteres, solo sale "Escribe un correo válido". El error aparece en una caja bajo la contraseña, el campo del correo no se marca en rojo, y la caja empuja el botón hacia abajo. El problema de la contraseña aparece hasta el siguiente intento.

**Propuesta.** Marcar cada campo con su propio error y mostrar todos a la vez.

**Dónde.** `AuthScreen.kt:60-62`.
**Captura.** `90_registro_errores.png`.

**25. Baja · "¿Correo equivocado?" deja el nombre de la cuenta cancelada.**

**Qué pasa.** Después de cancelar la cuenta nueva, el modo sin cuenta saluda "QA" con avatar "QT", que es el nombre que escribí en el registro.

**Propuesta.** Volver al nombre que había antes del registro.

### Pulido (rápido de arreglar)

- **El teclado no se abre solo.** Pasa al cambiar el nombre del entreno, al cambiar el nombre en Perfil y en la búsqueda del Historial. En la búsqueda es peor: al tocar la lupa aparece el campo, pero lo que escribes se pierde hasta que lo tocas otra vez.
- **Plurales.** Aparecen "1 entrenos · 2,400 lb" (`HistoryScreen.kt:159` y `:185`), "1 entrenos · 3 series" (`Charts.kt:68`), "1 entrenamientos" (`ProfileScreen.kt:80`), "mejor racha: 1 semanas" (`ProgressStoryUi.kt:146`) y "1 registros" en Peso.
- **Zona horaria.** "Horas en tu zona: GMT" sale siempre en Historial (`HistoryScreen.kt:131`). Solo sirve si algún entreno se hizo en otra zona.
- **Descartar un entreno vacío.** Pide confirmar "Se borrará todo lo registrado… No se puede deshacer" cuando no hay nada registrado (`ActiveWorkoutScreen.kt:252`).
- **Texto de la recomendación.** "Mantén 100 lb" lleva un ícono "+" que sugiere subir. Al abrirla dice "hoy tu serie más floja fue de 8", pero se refiere a la sesión anterior (`Training.kt:240`).
- **Explicación del drop set.** El texto "no arranca el temporizador antes de él y no se usa para decidir cuándo subir" es lenguaje técnico. Basta con "Bajas el peso y sigues sin descansar".
- **Pantalla de 320 dp.** El encabezado "ANTERIOR" se corta como "ANTERI…".

## Lo que funciona bien (no romperlo al arreglar)

- **Atrás a media sesión** no pierde el entreno: queda la barra "Entrenamiento en curso".
- **Cerrar la app a la fuerza** durante un descanso y volver a abrirla conserva el entreno y la notificación.
- **Girar la pantalla** conserva lo escrito.
- **Doble toque rápido en ✓** no marca dos veces.
- **Las validaciones del entreno** dicen exactamente qué falta ("Escribe el peso…", "Escribe las repeticiones…").
- **El fin del descanso con la app en segundo plano** suena (pide el foco de audio) y muestra un aviso. **"+15 s" desde la notificación** funciona.
- **Quitar un ejercicio y deshacer** al momento lo devuelve completo.
- **Editar un entreno terminado** recalcula el volumen (520 → 560 lb) y pregunta antes de descartar cambios. **El editor de rutinas** pregunta antes de salir sin guardar.
- **Cambiar a kg** se aplica en todas las pantallas.
- **Exportar** abre el menú de compartir con los 3 CSV.
- **Retirar el permiso del peso** explica bien qué se borra.
- **La selección múltiple** de ejercicios se conserva entre búsquedas, y la búsqueda del Historial tiene un buen mensaje de "Sin resultados".
- **La cuenta** tiene mensajes claros: "Correo o contraseña incorrectos", "Sin conexión. Revisa tu internet…" (en 5 s), "El código no es correcto…". "¿Correo equivocado?" sí borra la cuenta del servidor y conserva los datos del teléfono.

## Rendimiento

Sin problemas en el emulador:

| Medición | Resultado |
|---|---|
| Primer arranque tras instalar | 1.27 s |
| Arranque en frío | 0.39 s |
| Arranque en caliente | 0.14 s |
| Scroll del selector de 256 ejercicios | 276 cuadros, 2.5 % con retraso, p90 22 ms |
| APK release | 11.8 MB |

Son números de emulador: conviene repetir el arranque y el scroll en un teléfono de gama baja.

## Pruebas automáticas

Corrí `testDebugUnitTest` en una copia aparte del repo: 171 pruebas, 168 pasan, 1 omitida y 2 fallan. Las dos que fallan son de exportar CSV:
- `V17Test.n9_exportarGeneraTresCsvYLosComparte` falla con `FileProvider` "Failed to find configured root" sobre la carpeta temporal de Robolectric.
- `V17UiTest.n2_n9_perfil_retirar_permiso_y_exportar` falla porque no se abre el menú de compartir.

En el emulador, exportar sí funciona. Lo más probable es que la prueba dependa de cómo Windows arma la ruta temporal, pero falta confirmarlo en Linux o en CI.

## Lo que no probé y por qué

- **Sincronización con una cuenta real, Ligas y Muro con cuenta, y cerrar sesión con datos**: no hay credenciales de la cuenta demo en esta máquina, y una cuenta nueva necesita el código del correo.
- **Compras de Apoya Rupio**: el emulador podría abrir un pago real.
- **Un historial grande**: la cuenta demo de 8 años tiene la misma limitación. El rendimiento con muchos datos ya está medido en `docs/QA_RENDIMIENTO.md`.
- **Que el sonido se oiga de verdad y TalkBack de punta a punta**: eso pide un teléfono real.

## Datos creados en producción

- **Cuenta temporal `qa-177a046304@rupio-qa.test`**: la creé en Neon Auth el 18 nov 2026 a las 19:3x para probar la pantalla del código. Ningún correo llega a una persona, porque `.test` no es un dominio real.
- **Ya está borrada**: la quité con "¿Correo equivocado?" desde la app. Lo comprobé: entrar con esa cuenta devuelve `401 INVALID_EMAIL_OR_PASSWORD`. No queda nada que limpiar.
