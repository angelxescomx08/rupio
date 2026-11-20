# Rupio 2.1 · QA de toda la app: rendimiento, accesibilidad, recompensas, muro e ícono

Revisé la app completa después de la v2.0/2.1 (inglés, modo claro, recompensas, muro visible, íconos), que no había pasado por QA. Antes leí lo que ya se revisó para no repetirlo ni deshacerlo: [QA_V19_HALLAZGOS.md](QA_V19_HALLAZGOS.md), [QA_V19_VERIFICACION.md](QA_V19_VERIFICACION.md), [QA_V19_RONDA3.md](QA_V19_RONDA3.md), [QA_V19.md](QA_V19.md), [QA_RENDIMIENTO.md](QA_RENDIMIENTO.md), [RENDIMIENTO_LIGAS.md](RENDIMIENTO_LIGAS.md), [TEMA_IDIOMA_APOYO.md](TEMA_IDIOMA_APOYO.md) y [APOYO_CIENCIA.md](APOYO_CIENCIA.md). Nada de lo que esos documentos dejaron bien se quitó; los cambios de abajo mejoran lo que ya existe.

- **Entorno**: emulador Pixel 8 Pro, Android 16, en español y en inglés, tema oscuro y claro, letra al 100 % y al 200 %. Versión debug (para simular compras y ver las recompensas) y variante benchmark (R8, como la de Play) para medir.
- **Comparación de rendimiento**: la v1.9 (`00e9dbc`) contra la actual, en el mismo emulador y en la misma sesión.
- **Evidencia**: capturas del emulador en `captures/qa/v21/` (carpeta local, git la ignora) y capturas de prueba en `app/screenshots/v21_*`.

## Resumen

La base está bien: el arranque y el scroll no empeoraron con la v2.0 (el APK bajó de 12.1 a 7.3 MB y la primera pasada del buscador pasó de 34 % a 4 % de cuadros con tirón), y las 241 pruebas que ya existían siguen pasando. Los problemas estaban sobre todo en lo nuevo de la v2.0: el ícono que eliges no se parece al que sale en tu teléfono, el dibujo del ícono se sale de la zona segura, algunos textos de color no llegan al contraste mínimo con ciertos acentos, las etiquetas de colores e íconos se cortan con letra grande, el marco Plata casi no se ve en tema claro y el muro de pagos no dice qué tan cerca estás del siguiente lugar. De antes quedaban los botones anchos que cortan su texto con letra grande y un Inicio con varios botones principales iguales. Todo eso quedó arreglado. Hay 4 propuestas que necesitan una decisión tuya (al final).

## Hallazgos y arreglos

### Ícono de la app

**1. Alta · El selector muestra un ícono distinto al real.**
- **Qué pasaba**: en "Ícono de la app" cada opción era la mancuerna de Material en diagonal, y la Aurora era un cuadro de color. El ícono real es una mancuerna horizontal con una flecha verde arriba (`036_launcher_normal.png` contra `035_gracias_iconos.png`). Quien paga por el ícono dorado no ve lo que va a obtener.
- **Arreglo**: `AppIconPreview` dibuja las mismas capas que usa Android (`ic_launcher_*_foreground` sobre su color de fondo), al tamaño en que las muestra el launcher, recortadas en círculo. El anillo de selección va por fuera y el candado ya no tapa el dibujo. Se usa en "Tus recompensas" y en la pantalla de gracias.
- **Prueba**: `V21UiTest.a_el_selector_muestra_los_iconos_de_verdad` y la captura `v21_01_iconos_reales.png`.

**2. Media · El dibujo del ícono se sale de la zona segura.**
- **Qué pasaba**: de la capa de 108 dp, Android solo garantiza los 66 × 66 dp del centro; lo de afuera se recorta según la forma del launcher, y la guía pide un logo de 48 a 66 dp ([Android · Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)). La mancuerna medía 68 dp de ancho, sus discos de afuera llegaban a 35 dp del centro y la flecha dejaba el dibujo cargado hacia arriba (`036b_icono_zoom.png`).
- **Arreglo**: los 4 íconos llevan el dibujo dentro de un `<group>` al 86 % y 3.4 dp más abajo. Lo más lejano queda a 31 dp del centro y el conjunto mide 58 dp de ancho, dentro de lo que pide la guía. Mismo diseño y colores (`070b_icono_nuevo_zoom.png`).

**3. Verificado · El cambio de ícono funciona.** En Android 16 con el launcher de Pixel, cambiar a Plateado y de vuelta a Normal no cerró la app, el cajón de apps se actualizó en unos segundos y el ícono que estaba en la pantalla de inicio siguió ahí con el dibujo nuevo (`039_home_con_icono.png`, `040_home_tras_cambio.png`).

### Recompensas

**4. Media · El marco Plata casi no se ve en tema claro.** El degradado plata (#EEF2F7 → #A7B3C3) sobre una tarjeta blanca da 1.1 a 2.1:1 (`061_perfil_claro_top.png`). En tema claro el marco lleva ahora un filo fino del mismo metal oscurecido por dentro y por fuera, como el canto de una moneda. En oscuro no cambia. Captura: `v21_02_marcos_tema_claro.png`.

**5. Media · Con letra al 200 % las etiquetas de colores e íconos se cortan.** Salían "Turq", "Dora", "Auro", "Norm" y "Plate" (`054_tiers_200_cards.png`, `056_tier_card_200.png`), porque cada opción medía 60–64 dp y la etiqueta era de una línea sin corte (WCAG 1.4.4). Con letra grande la opción mide 96 dp y la etiqueta usa hasta 2 líneas con guion. Prueba: `V21BigTextTest` revisa que ninguna etiqueta se desborde; captura `v21_04_selectores_letra_200.png`.

**6. Baja · "Subir de nivel" no dice que se paga el precio completo.** Quien ya tiene 💪 Un licuado ($99) ve "Puedes subir de nivel" y Fan a $199; es fácil creer que se cobra la diferencia. Debajo del título ahora dice "Cada nivel es otro pago único, por su precio completo. Lo que ya tienes se queda." (`RewardCopy.UPGRADE_NOTE`). Ver la propuesta B para cobrar solo la diferencia.

### Muro de agradecimientos (ranking de pagos)

**7. Media · El muro no muestra qué tan cerca estás del siguiente lugar.**
- **Qué pasaba**: la tarjeta de Mecenas decía "Te faltan 152 puntos para pasar al lugar #2" y abajo tres botones iguales; el sugerido solo tenía otro borde, sin decir por qué.
- **Arreglo**:
  - Una barra con tus puntos contra la meta, rotulada "Tú · 598" y "#2". Ver qué tan cerca está la meta hace que la gente se esfuerce más al acercarse ([Kivetz, Urminsky y Zheng 2006](https://doi.org/10.1509/jmkr.43.1.39)), y un ranking funciona como una meta que cada quien se pone cerca de arriba ([Landers, Bauer y Callan 2017](https://doi.org/10.1016/j.chb.2015.08.008)).
  - El aporte sugerido dice "Llegas al #2" (y TalkBack lo lee). Solo lo dice si de verdad alcanza.
- **Privacidad**: la barra usa solo tus puntos y lo que te falta, que ya se mostraba. No se muestran los puntos de los demás: el permiso del muro cubre nombre, lugar y fecha, no cuánto aportó cada quien.
- **Prueba**: `V21UiTest.c_muro_con_barra_y_aporte_que_te_sube`; captura `v21_03_muro_barra.png`.

**8. Baja · Sin cuenta, el muro dice "Estas personas…" sobre una lista vacía.** El muro solo se baja con cuenta (`rupio_wall()` pide sesión), así que en "Probar sin cuenta" la pestaña quedaba con la frase y nadie debajo (`049_muro_local.png`). Ahora dice "Aquí aparecen, con su nombre, las personas que apoyan Rupio como Mecenas…" (`WallCopy.INTRO_NO_LIST`). Ver la propuesta A para que se vea la lista.

### Contraste con todos los colores (WCAG 1.4.3)

**9. Media · Algunos textos de color no llegan a 4.5:1 con ciertos acentos.** Las pruebas de contraste anteriores medían solo el azul. `V21Test` ahora mide los 7 acentos (los de las recompensas incluidos) en los dos temas: botones, pestañas, chips, la tarjeta del nivel elegido, la escalera de ligas, el mensaje de apoyo y el muro. Fallaban:

| Dónde | Antes | Arreglo |
|---|---|---|
| Píldora "Recomendado" (azul, oscuro) | 4.07:1 | Píldora llena (acento con letra encima). También se distingue mejor como la opción de en medio. |
| Píldora "Recomendado" (turquesa y rosa, claro) | 4.29 y 4.47:1 | La misma |
| Tu liga en la escalera (azul, oscuro; turquesa, claro) | 4.44 y 4.42:1 | Tinte al 10 % en vez de 14 % |

**10. Baja · Los chips y segmentos elegidos eran verdes.** "Mes", "kg", "Lunes", "3M" y el ejercicio elegido en Progreso se marcaban con el verde de "serie completada", mientras la barra de abajo y las pestañas usan el acento. Ahora el contenedor elegido sale del acento (`secondaryContainer` en `Theme.kt`), así que también sigue el color que eligió quien apoya: la recompensa se ve en más lugares. Contraste medido con los 7 acentos en `V21Test`.

### Inicio: qué botón tocar

**11. Media · Inicio tenía 3 o más botones azules iguales.** Con dos rutinas había "Empezar entrenamiento libre" y un "Empezar rutina" lleno en cada tarjeta (`016_inicio_rutinas.png`). La jerarquía visual se arma con tamaño, color y contraste, y lo más importante debe destacar ([NN/g · Visual hierarchy](https://www.nngroup.com/articles/visual-hierarchy-ux-definition/); [Material 3 · Buttons](https://m3.material.io/components/all-buttons)). Ahora:
- Solo el botón de la rutina que toca ("Te toca hoy" / "La próxima") va lleno. Las demás llevan uno tonal del mismo tamaño y texto.
- Con rutinas, "Empezar entrenamiento libre" va con borde. Sin rutinas, o con un entreno en curso ("Continuar entrenamiento"), sigue lleno.

Nada se movió de lugar (`073_inicio_claro_jerarquia.png`, `074_…`).

### Errores pequeños

- **12. La duración se cortaba hacia abajo**: un entreno de 1:58 decía "Duración 1 min" en el resumen y "1 min" en el Historial; uno de 59:50, "59 min". Ahora redondea al minuto más cercano (`Fmt.durationHuman`). Las estimaciones de las rutinas cambian 1 min en algunos casos (39 → 40).
- **13. Repeticiones sin separador de miles**: Progreso mostraba "Reps 1508" junto a "7,990 kg". Ahora "1,508" (y las series también, en el histórico).
- **14. El nombre por defecto no cambiaba de idioma**: quien entró sin cuenta con el teléfono en inglés y luego eligió español veía "Buenas noches, Athlete". Ahora el nombre por defecto sigue al idioma, también si el idioma del teléfono cambió con la app cerrada. Un nombre que tú escribiste no se toca. Prueba: `V21UiTest.e_el_nombre_por_defecto_sigue_al_idioma`.
- **15. Peso corporal**: el aviso vacío decía Toca "Registrar peso", pero el botón se llama "Registrar peso de hoy".
- **16. Botones anchos con alto fijo**: con letra al 200 % en 320 dp, "Agregar ejercicios" se partía en 2 líneas y la segunda quedaba cortada por el alto fijo de 54 dp; en pantalla se leía "+ Agregar" (la captura `qa/12_entrenamiento.png` de `QaUiTest` lo muestra). Los 11 botones anchos con alto fijo (entreno, rutina, guía, tutoriales, cerrar sesión, gracias, video) ahora tienen alto mínimo y crecen con la letra. Prueba: `V21BigTextTest.los_botones_anchos_crecen_con_la_letra`; captura `v21_05_agregar_ejercicios_letra_200.png`.

## Propuestas que no hice (necesitan tu decisión)

**A. Que el muro se vea sin cuenta.** Es lo que más ayudaría a que la gente se anime a pagar: ver a otras personas que ya apoyan sube los aportes, sobre todo en quien aporta por primera vez ([Shang y Croson 2009](https://academic.oup.com/ej/article-abstract/119/540/1422/5089584)). Hoy, quien usa "Probar sin cuenta" (el camino más común al empezar) ve las tarjetas del muro en Inicio, Progreso y Perfil sin un solo nombre. Hace falta una función nueva en el servidor, por ejemplo `rupio_wall_public()` con nombre, lugar y fecha de los primeros 300, sin "tú", abierta al rol `anonymous`. El permiso que aceptan los Mecenas ya dice "a todas las personas que usan Rupio". **Es un cambio de permisos en producción, así que no lo hice sin preguntarte.**

**B. Cobrar solo la diferencia al subir de nivel.** Google Play no cobra diferencias entre productos únicos. Se podrían crear productos de mejora ("De Licuado a Fan", $100) que solo se muestran a quien ya tiene el nivel de abajo. Quien ya pagó $99 difícilmente paga $199 completos. Mientras tanto, la nota del punto 6 evita la sorpresa.

**C. Fondo de los íconos en launchers oscuros.** Los 4 fondos son casi negros (#000000, #10151C, #17110A y #120F1E), así que en un cajón de apps oscuro (#12141A) el círculo no se distingue y la mancuerna parece flotar (`038b_icono_plata_zoom.png`, contraste ~1.05:1). Un fondo con un poco más de luz (por ejemplo, un degradado radial del mismo tono) lo resolvería, pero cambia la imagen de marca.

**D. Botón de videos en el detalle del ejercicio.** El botón rojo lleno "Buscar videos" es lo más llamativo de la pantalla, más que la técnica en pasos, y saca a la persona de la app. Un botón con borde y el ícono rojo mantendría el acceso sin competir con la técnica.

También queda una advertencia: algunos launchers (Samsung, Xiaomi) quitan el ícono de la pantalla de inicio al cambiar de alias. No lo pude probar en el emulador; vale la pena revisarlo en un teléfono de esas marcas antes de publicar.

## Rendimiento

Misma sesión del emulador, variante benchmark (R8) de las dos versiones, entrando sin cuenta. Como referencia, Ajustes de Android tardó 560–860 ms en arrancar en frío en esa misma sesión, así que el emulador iba más lento que en las vueltas anteriores. Por eso solo vale comparar entre columnas.

| Medición | v1.9 (`00e9dbc`) | v2.1 (ahora) |
|---|---:|---:|
| Arranque en frío, mediana (3 tandas de 6 a 10, alternando las dos) | 643–678 ms | 611–746 ms |
| Arranque en caliente | — | 118–157 ms |
| Buscador de ejercicios, 1.ª pasada (carga las fotos) | 34 % con tirón · p90 77 ms | 4.2 % · p90 36 ms |
| Buscador, pasadas 2 y 3 | 3.1–3.6 % · p90 19–21 ms | 3.1 % · p90 18 ms |
| Apoya Rupio (pestaña Apoyar), 3 pasadas | — | 2.9–3.1 % · p90 18–20 ms · 0–1 cuadro lento de la UI |
| Progreso, 3 pasadas | — | 2.9–3.1 % · p90 18–19 ms · 0 cuadros lentos de la UI |
| Memoria (PSS) | 66 MB | 70 MB |
| APK | 12.1 MB | 7.3 MB |

- La v2.0 no trajo regresiones. En el arranque no hay una diferencia clara: las medianas se cruzan entre tandas y el ruido del emulador es de ±10 % (hubo arranques sueltos de 1.1 a 2 s en las dos versiones). La primera pasada del buscador sí mejoró mucho, porque las fotos de RepDB en WebP pesan menos. La última tanda ya incluye los cambios de esta vuelta.
- Los tirones que quedan (~3 %) son de la GPU del emulador ("Frame deadline missed"), igual que en `RENDIMIENTO_LIGAS.md`. El hilo de la UI casi nunca pasó del presupuesto del cuadro.
- En el código, el reloj del entreno solo recompone la barra de arriba (no la lista de series) y las insignias animadas no recomponen. Los cambios de esta vuelta no agregan trabajo por cuadro: la barra del muro es estática y la vista previa de los íconos son vectores.

## Investigación que se usó

| Tema | Qué dice | Dónde se aplicó |
|---|---|---|
| Zona segura del ícono adaptable | Capas de 108 dp; solo los 66 × 66 dp del centro se ven siempre y el logo debe medir de 48 a 66 dp. [Android](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) | Punto 2 |
| Jerarquía visual | Tamaño, color, contraste y agrupación dicen qué es más importante; pocos tamaños distintos. [NN/g](https://www.nngroup.com/articles/visual-hierarchy-ux-definition/), [NN/g · 5 principios](https://www.nngroup.com/articles/principles-visual-design/) | Punto 11 |
| Énfasis de botones | Un botón lleno para la acción principal; tonal y con borde para las secundarias. [Material 3](https://m3.material.io/components/all-buttons) | Punto 11 |
| Tema oscuro | Evitar colores saturados sobre fondos oscuros: no llegan a 4.5:1 y "vibran". [Android · Color](https://developer.android.com/design/ui/wear/guides/m2-5/styles/color) | Puntos 9 y 10 (los acentos del oscuro ya eran claros; se midieron todos) |
| Contraste | Texto ≥ 4.5:1 (1.4.3), controles ≥ 3:1 (1.4.11), texto que crece hasta 200 % sin cortarse (1.4.4). [WCAG 2.2](https://www.w3.org/TR/WCAG22/) | Puntos 5, 9 y 10 |
| Gradiente de la meta | Se acelera el esfuerzo al acercarse a la meta; ver el avance ayuda. [Kivetz et al. 2006](https://doi.org/10.1509/jmkr.43.1.39); progreso regalado: [Nunes y Drèze 2006](https://doi.org/10.1086/500480) | Punto 7 |
| Rankings | Un ranking motiva como una meta difícil: la gente apunta cerca de arriba. [Landers et al. 2017](https://doi.org/10.1016/j.chb.2015.08.008) | Punto 7 y las ligas por grupos de tu nivel (ya existían) |
| Información social al donar | Ver lo que aportan otros sube los aportes ~12 % (hasta 29 % en primerizos). [Shang y Croson 2009](https://academic.oup.com/ej/article-abstract/119/540/1422/5089584) | Propuesta A |
| Insignias | En un experimento de campo de 2 años, las insignias aumentaron la actividad. [Hamari 2017](https://doi.org/10.1016/j.chb.2015.03.036) | Puntos 1 y 4: que las recompensas se vean bien |
| Uso con una mano | ~49 % usa el teléfono con una mano y la mayoría toca con el pulgar. [Hoober 2013](https://www.uxmatters.com/mt/archives/2013/02/how-do-users-really-hold-mobile-devices.php) | Lo de abajo ya estaba bien (barra de pestañas, descanso y "Guardar" abajo); no se movió nada |

Lo que ya estaba respaldado en `APOYO_CIENCIA.md` y `TEMA_IDIOMA_APOYO.md` (momento del mensaje, niveles, tono, dónde va el muro) no se cambió.

## Lo que sigue funcionando (no romperlo)

- Bienvenida de 2 pantallas, guía de 5 pasos y "Crear 2 rutinas": 14 toques hasta el primer peso, como en la v1.9.
- El permiso de notificaciones se pide al marcar la primera ✓, con su explicación.
- "Te toca hoy"/"La próxima", la rotación y "Última vez: hoy".
- La pantalla de gracias con confeti, la insignia, "Lo que desbloqueaste" y elegir color e ícono de una vez.
- El cambio de idioma en Perfil recrea la app en español sin perder nada.
- Tema claro: textos y bordes se leen bien; los únicos problemas eran los del punto 9.

## Pruebas automáticas

- Nuevas: `V21Test` (contraste de los 7 acentos en los dos temas, duración y separador de miles), `V21UiTest` (íconos reales, marcos en claro, muro con barra, muro sin cuenta, nombre por defecto) y `V21BigTextTest` (etiquetas y botones con letra al 200 % en 320 dp).
- Suite completa con `testDebugUnitTest --rerun` (sin caché): **250 pruebas, 247 pasan y 3 se omiten** (las mismas 3 de antes). `assembleRelease` y `assembleBenchmark` compilan con R8.
- Las capturas de Roborazzi en `app/screenshots/` se volvieron a grabar: cambian las que muestran chips o segmentos elegidos (ahora con el acento), Inicio con rutinas y el muro.

## Lo que no probé

- **Compras reales, muro y ligas con cuenta**: no hay cuenta de prueba con correo confirmado en esta máquina, y una compra en el emulador podría ser real. El muro con datos se probó con estados de prueba (`WallContent`).
- **Launchers de otras marcas** y **TalkBack de punta a punta**: piden un teléfono real.

## Segunda vuelta: comentarios del usuario y las propuestas aprobadas

Después de la QA el usuario mandó 5 comentarios y aprobó las propuestas A y B. Todo esto ya está en código y probado.

### Comentarios del usuario

**C1. Opción de reemplazar ejercicio.** En el menú ⋮ de un ejercicio (en el entreno y en el editor de rutinas) hay "Reemplazar ejercicio". Abre el selector con el título "Reemplazar ejercicio · En lugar de …", filtrado por el mismo músculo (el elegido va primero, siempre a la vista) y se toca uno; el de ahora dice "Actual". En el entreno: las series que no marcaste pasan al nuevo (mismo tipo, sin peso ni reps, que eran del otro); las que ya marcaste se quedan con el de antes, porque es lo que hiciste, y el nuevo va justo después (fuera de la superserie, con un aviso que lo explica si había series marcadas). A cardio: 1 serie y sin descanso. En la rutina, cambia el ejercicio en su lugar conservando series, descanso y superserie. `WorkoutRepository.replaceExercise`, `RoutineEditorVM.replace`; pruebas `V21UiTest.f_reemplazar_ejercicio`; capturas `qa/v21b/002`–`004`.

**C2 y C3. El aviso de descanso suena con solo el timbre, aunque la app esté cerrada.** El modo por defecto pasó de "Con el volumen de la música" a **Automático**: suena por el timbre aunque la música esté en 0; con audífonos o algo sonando, va por la música y la baja un momento; en vibración, por la música si no está en 0 (el caso del gimnasio, de la v1.8); en silencio total, solo vibra. Se cambia en Perfil → Aviso de fin de descanso. Antes el reporte era "no suena la notificación; tiene que sonar aunque solo esté activado el timbre", que es justo lo que arreglaba el default "Con el volumen de la música" cuando la música está en 0. `RestAlertMode.AUTO`, `RestAlertPolicy.decide`; prueba `V21Test.aviso_automatico_suena_con_el_timbre`. **Verificado en el emulador** con la app cerrada, timbre activado y música en 0: sonó por el altavoz (`RingtonePlayer play .../rest_done`) y salió el aviso "¡Descanso terminado!" (`qa/v21b/010`, `011`).

**C4. Notifica aunque la app no esté abierta / en primer plano.** Ya funcionaba y se confirmó: con la app cerrada, la notificación de fin de descanso se publica sola (servicio en primer plano + wakelock durante el descanso); en primer plano el aviso sale dentro de la pantalla del entreno.

**C5. Los cambios se suben solos, en algún momento, sin saturar la base.** Los cambios fuera de un entreno (rutinas, pesos, ediciones, borrados) programan una subida ~2 min después, en un solo lote aunque hagas varios seguidos (`SyncEngine.soon`, política KEEP). Durante el entreno no se sube cada serie: el entreno se sube completo al terminarlo (`SyncEngine.now`). Sin cuenta, nunca. Antes solo subía al terminar un entreno o en la sincronización periódica de 15 min, que Android puede atrasar.

### Propuestas aprobadas

**A. El muro se ve sin cuenta.** Migración `012_muro_publico.sql` aplicada en producción el 20 nov 2026 14:48 CDMX. Función `rupio_wall_public()` para el rol `anonymous` con lugar, nombre y fecha de los primeros 300 y el total, sin puntos ni ids ni "tú". La app pide un JWT anónimo a Neon Auth (`GET /token/anonymous`) y muestra el muro en la pestaña Muro y en las tarjetas de Inicio, Progreso y Perfil. Verificado en la app sin cuenta: muestra la lista ("1 person on the wall", Angel). Comprobado por HTTP que `anonymous` no puede leer la tabla ni la función con tu lugar (403). Un bug que salió aquí (la primera carga decía "Sin conexión" y había que tocar "Reintentar") quedó arreglado: `loadPublic` usa un mutex y reintenta el token una vez. `WallRepository.loadPublic`, `AuthRepository.anonymousJwt`.

**B. Subir de nivel pagando solo la diferencia.** `SupportUpgrade` (productos de mejora, pago único) y la lógica para que una mejora dé su nivel solo si tienes el de abajo. La tarjeta muestra el precio de la diferencia con el precio completo tachado ("en vez de $199 MXN") y la nota "pagas solo la diferencia". `SupportRepository`, `SupportState.priceFor`; prueba `V21Test.mejora_de_nivel_cobra_la_diferencia`. **Pendiente para que funcione en Play**: crear en Play Console los productos de mejora (`rupio_mejora_cafe_licuado`, `rupio_mejora_cafe_fan`, `rupio_mejora_licuado_fan`); mientras no existan, se cobra el nivel completo, como antes.

### Pendiente antes de publicar (de Play Console)

Además de lo de `claude/rupio-estado.md` (protección de Play, productos de apoyo y del muro, llave de licencias): crear los 3 productos de mejora de la propuesta B. Si no se crean, subir de nivel cobra el precio completo (no se rompe nada).

## Datos creados en producción

Ninguna cuenta ni dato de usuario. Sí se aplicó la migración `012_muro_publico.sql` en producción (propuesta A, con tu visto bueno): agrega una función de solo lectura para el muro público y no toca datos.
