# Rupio 1.6 · arreglos de la auditoría QA de la v1.5

Auditoría: https://claude.ai/artifact/C2FJsjeqSnwFmpJww17g6X (17 nov 2026). Esta versión ataca **los 43 hallazgos** de una vez (la auditoría proponía repartirlos en 1.6, 1.7 y 1.8). 41 quedaron resueltos en el código; 2 dependen de ti fuera del código (E2 y la parte de Play Console de S2) y 1 no se cambió a propósito (K2).

Lo que se hace fuera del código (Neon, GitHub Pages, Play Console) está paso a paso en [PLAY_DECLARACIONES.md](PLAY_DECLARACIONES.md).

## Bloqueantes (P0)

| ID | Qué se hizo | Dónde |
|---|---|---|
| E1 | `compileSdk` y `targetSdk` 36 (Android 16). versionCode 16, versionName 1.6. | `app/build.gradle.kts` |
| S1 | La contraseña ya no se guarda nunca; la que guardaban las versiones anteriores (`cred_email`, `cred_pass`) se borra al abrir. Se quitó el interruptor "Mantener sesión siempre". | `data/auth/Auth.kt`, `ProfileScreen.kt` |
| Y1 | Estado nuevo `SignedIn.sessionExpired`. Si Neon dice que no hay sesión, Inicio muestra una tarjeta roja "Tu sesión venció · Entrar de nuevo" que pide solo la contraseña de la MISMA cuenta; nada del teléfono se borra y lo pendiente se sube al entrar. Sin internet nunca cuenta como sesión vencida. | `Auth.kt`, `ui/components/SyncUi.kt` |
| S2 | Perfil → **Eliminar mi cuenta**: explica qué se borra, pide la contraseña y llama a la función SQL `rupio_delete_my_account` (borra todas las filas y el usuario de Neon Auth en una transacción). **Aviso de privacidad** dentro de la app (registro y Perfil) y en `docs/privacidad.html`; página de borrado sin app en `docs/eliminar-cuenta.html`. Se comprobó contra el servidor real que el `/delete-user` de Neon Auth está apagado (404), por eso la función. | `ui/legal/`, `db/migrations/005_delete_account.sql`, `docs/*.html` |
| U1 | Modo sin cuenta → **"Crear cuenta y conservar mis datos"** (Perfil, detalle de respaldo y cerrar sesión). Al crear la cuenta o entrar con una existente, todas las filas pasan a la cuenta y quedan pendientes de subir; ya no se borran. "Probar sin cuenta" avisa que después se puede respaldar. Además, tras restaurar una copia de Google, el modo sin cuenta recupera sus entrenos. | `RupioApp.kt` (`adoptLocalData`), `AuthScreen.kt` |

## Graves (P1)

| ID | Qué se hizo |
|---|---|
| S3 | Se generó la llave de subida `keystore/rupio-upload.jks` (+ `keystore.properties`, fuera de git). El APK que se entrega es el **release firmado** (`debuggable=false`, sin actividades de prueba). Para testers: prueba interna de Play. |
| R1 | La descarga guarda el cursor después de **cada página**; ante un 401 pide un JWT nuevo y repite esa petición una vez. Prueba: `primeraDescargaGuardaAvanceYRenuevaElJwt`. |
| Y2 | Un mensaje por causa, en el orden del diagrama de la auditoría: sesión vencida (rojo) → sin internet → Rupio no responde → N cambios no se pudieron subir → subiendo/pendientes → todo respaldado. Siempre con el número de pendientes. (`domain/SyncSummary.kt`) |
| Y3 | Fecha relativa: "hace 5 min", "hoy 14:05", "ayer 14:05", "14 nov". Más de 24 h con pendientes es advertencia. (`Dates.relative`) |
| Y4 | Nube con el color del estado y el número de pendientes en la barra de Inicio e Historial; al tocarla abre el detalle (hoja inferior). |
| U2 | "¿Olvidaste tu contraseña?" en el inicio de sesión, en "Entrar de nuevo" y en "Eliminar mi cuenta": código de 6 dígitos por correo (Neon Auth Email OTP, endpoints verificados contra el servidor real) y contraseña nueva, sin salir de la app. La respuesta no revela si el correo existe. |
| U3 + C3 | El mensaje de apoyo solo sale en hitos (entreno 3, 10, 25, 50, 100 y luego cada 100), máximo una vez cada 30 días y nunca después de 3 "Ahora no". Ya no es un diálogo a pantalla completa: es una tarjeta dentro del resumen que se puede ignorar. Con la historia de la cuenta demo lo verías ~22 veces en 8 años, no 1 720. |
| U4 | El permiso de notificaciones ya no se pide al abrir: se pide al empezar el primer entreno, después de un diálogo "Para avisarte cuando termine tu descanso, aunque tengas la pantalla apagada". |
| A1 | Celdas de peso/reps/tiempo con etiqueta para TalkBack ("Peso en libras, serie 2, Press de banca, sugerido 135"); el número de serie es un botón que dice "Serie 2 de Press de banca, normal" y la acción; el ✓ dice qué serie marca. |
| A2 | `FitText` reescrito con el `autoSize` de Compose: con letra agrandada **nunca** baja del tamaño normal; primero usa 2 líneas (con guiones en español). Las pestañas ya no se achican: si no caben, se ocultan las etiquetas y el ícono dice su nombre. |
| E2 | Fuera del código: la declaración de `specialUse` (texto listo) y el guion del video están en `PLAY_DECLARACIONES.md`. |

## Medianos (P2)

| ID | Qué se hizo |
|---|---|
| S4 | `dataExtractionRules` y `fullBackupContent`: la copia de Google lleva la base de entrenos (única copia de quien no tiene cuenta) y las preferencias; NO la sesión cifrada, los cursores, las borraduras pendientes ni el temporizador. Dicho en el aviso de privacidad. |
| S5 | La contraseña del inicio de sesión usa `remember` (no se guarda en el estado de la actividad). |
| S6 | Las 512 imágenes de ejercicios vienen dentro de la app (WebP 560 px, 8.9 MB) y se leen de `file:///android_asset/ex/`. El catálogo se reescribe una vez para cambiar las rutas (`CatalogSeeder.VERSION = 2`). Ya no se contacta a GitHub. |
| R2 | Historial ya no regresa a la primera página: al cambiar el número de entrenos se vuelve a leer lo cargado y la lista conserva la posición por llave. (Sin Paging 3: no hizo falta.) |
| R3 | Historial con búsqueda por ejercicio o nombre (sin acentos) y botón "Ir a un mes" con los meses y cuántos entrenos tienen. |
| Y5 | "Sincronizar ahora" responde: con red corre en el momento ("Subiendo…"); sin red dice "Sin internet: se subirá cuando vuelva la conexión". |
| Y6 | Los cambios que el servidor rechaza (4xx) ya no se reintentan para siempre: se recuerdan y "Revisar" dice cuáles son ("Serie en «Pierna» del 14 nov") con **Reintentar** o **Descartar**. |
| U5 | El aviso de borrar un entreno ya dice la verdad: "sus récords se recalcularon". |
| U6 | Quitar ejercicio, eliminar serie y borrar entreno: se hace al instante con **Deshacer** 5 s (snackbar). Los diálogos quedan para descartar entreno, eliminar rutina y cerrar sesión. |
| U7 | Peso corporal: "Entre 20 y 400 kg (44–880 lb)". Correo: se valida el formato antes de enviar. |
| C2 | Terminar entrenamiento es un diálogo de pantalla completa con "Guardar" arriba. |
| K1 | Borde de controles `#6A6A76` (3.9:1 sobre negro, 3.4:1 sobre diálogos) como `outline` del tema; `#2C2C35` queda solo para divisores. |
| A3 | Cada día de la semana en Inicio se lee completo: "Martes 11 de noviembre, entrenaste". |
| A4 | Las gráficas de progreso tienen resumen hablado: "1RM estimado: de 80 kg a 95 kg entre…". |
| A5 | Selector de descanso, opciones de la guía y selección de ejercicios del editor anuncian rol y "seleccionado". |
| Q1 | Perfil → **Aviso de fin de descanso**: sonido y vibración / con el volumen de la música (baja la música un momento y se oye en audífonos) / solo vibración, botón **Probar** y enlace a los ajustes del canal. |
| Q2 | Con la app a la vista ya no sale la notificación emergente que tapaba "Terminar": suena/vibra y la pantalla del entreno muestra "¡Listo! A la siguiente serie". |
| E3 | AGP 8.13.2, Kotlin 2.3.21, KSP 2.3.14, Compose BOM 2026.06.01 (Compose 1.11), Room 2.8.5, core 1.18, activity 1.12.4, Robolectric 4.16.1, Roborazzi 1.79. Compose 1.12 pide compileSdk 37 y AGP 9: se deja para cuando Play pida API 37. Room 2.8 no aceptaba el alias `first` en una consulta: se corrigió. |

## Menores (P3)

| ID | Estado |
|---|---|
| R4 | Hecho: la llave del Keystore se abre una vez y los valores descifrados se guardan en memoria; la sesión se lee en segundo plano. Falta medir el arranque con Macrobenchmark y generar un Baseline Profile (necesita teléfono). |
| R5 | Hecho con `autoSize` (A2): una sola pasada de medición, sin esconder el texto. |
| U8 | Hecho: las explicaciones clave (tipo de serie, RPE, la rayita de 10 series, descanso, 1RM) van en `bodySmall` y `TextDim`. |
| U9 | Hecho: Perfil → "Pantalla encendida al entrenar", encendido por defecto. |
| K2 | **No se cambió.** Pasar de negro puro a `#0A0A0C` es un compromiso (halo en astigmatismo contra batería en OLED) que hay que ver en un teléfono real; además las tarjetas (`#0F0F12`) casi no se distinguirían. Queda para la revisión en dispositivo. |
| A6 | Hecho: correo, contraseña, contraseña nueva, nombre y código conectados al autocompletado (`ContentType`). |
| Q3 | Hecho: el descanso se guarda con hora de reloj y se restaura si Android reinicia la app. |
| S7 | Hecho lo que tocaba: ya no se muestran textos técnicos ("Algo salió mal (código A1)"). Compras verificadas solo en el teléfono y sin fijado de certificado siguen como riesgo aceptado. |

## Pruebas

Todas las pruebas pasan (ver el número exacto en el estado del proyecto). Nuevas en la 1.6: `V16FixesTest` (contraseña no guardada, sesión vencida y recuperación, recuperar contraseña, crear cuenta conservando datos, mensaje de apoyo en hitos, mensajes de sincronización, descanso que sobrevive) y en `QaDataSyncTest` la descarga que guarda avance y renueva el JWT y que lo rechazado no se reintenta. La auditoría de accesibilidad a 320 dp con letra al 200 % (`QaUiTest`) sigue pasando con las pestañas sin etiqueta.

No se pudo probar en un teléfono real: TalkBack de punta a punta, 3G limitado, el sonido con música en audífonos y el arranque medido. Es lo primero que conviene hacer con la 1.6 en prueba interna.
