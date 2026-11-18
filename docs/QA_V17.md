# Rupio 1.7 · segunda revisión QA

Lista de la segunda revisión (N1–N9). Cada cambio tiene su prueba en `V17Test.kt` (lógica y sincronización) o en `V17UiTest.kt` (pantallas). Al final se corrieron **todas** las pruebas del proyecto.

K2 (fondo negro puro) **no se cambió**: falta probarlo en un teléfono real.

## Graves (P1)

| ID | Qué se hizo | Prueba |
|---|---|---|
| N1 | Detalle del entreno → menú → **Editar**. Abre la tabla de series (mismas celdas `NumberCell` que al entrenar): cambiar peso, reps, tiempo o tipo (toca el número de serie), agregar o borrar series. Los cambios se guardan solo al tocar **Guardar** (salir pregunta si descartar). Al guardar (`WorkoutRepository.saveEdits`, una transacción): `updateSet()` en las cambiadas, borrado suave de las quitadas, series nuevas; se recalculan `total_volume_kg`, `total_sets` y `total_reps` (sin calentamientos, igual que al terminar); el entreno queda `dirty`; `rebuildRecords()` de los ejercicios afectados (y el trofeo de las series cambiadas); `SyncEngine.now()`. En **Revisar**, las series rechazadas tienen **Corregir**: abre este editor en el ejercicio de esa serie (marcada en amarillo) y, al guardar, esa serie se vuelve a intentar (o deja de intentarse si la borraste). | `n1_editarEntrenoTerminadoRecalculaTodo`, `n1_editar_entreno_terminado_y_corregir` |
| N2 | **Contacto**: correo directo (jose.angel.hdz.rda@gmail.com) y domicilio del responsable en `PrivacyPolicy.kt`, `docs/privacidad.html` y `docs/eliminar-cuenta.html` (el teléfono no se publica). **Consentimiento**: la primera vez que registras tu peso aparece la casilla «Acepto que Rupio guarde mi peso corporal (dato de salud) para mostrarme mi tendencia»; sin marcarla no se guarda. La fecha se guarda en la cuenta: columna `profiles.health_consent_at` (migración `006_health_consent.sql`, **ya aplicada en Neon**). Quien ya tenía pesos de versiones anteriores ve una tarjeta en Peso para aceptar o borrar su peso. **Retirar**: Perfil → **Permiso de peso corporal** borra todo el peso del teléfono al instante y de la cuenta en la siguiente sincronización (aunque estuvieras sin internet); si lo retiras en otro teléfono, también se borra aquí. | `n2_avisoConCorreoYDomicilio`, `n2_retirarConsentimientoBorraElPeso`, `n2_casilla_de_consentimiento_del_peso`, `n2_n9_perfil_retirar_permiso_y_exportar` |

**Pendiente tuyo (N2):** validar los textos con un abogado (LFPDPPP 2025, art. 8 y lo demás que aplique). Y subir a GitHub los dos HTML de `docs/` para que la página publicada tenga el correo y el domicilio.

## Medianos (P2)

| ID | Qué se hizo | Prueba |
|---|---|---|
| N3 | Al pasar del modo sin cuenta a una cuenta, los récords del teléfono **ya no se suben** (se borran del teléfono) y, después de la primera sincronización completa, se recalculan con todo el historial (`rebuildAllRecords`) y se suben. En "Entrar y subir mis datos" se avisa: «Se juntarán con lo que ya tienes en tu cuenta…». | `n3_elRecordMayorDeLaCuentaSeConserva` (100 kg en la cuenta, 80 kg en el teléfono → quedan 100 kg), `n3_aviso_se_juntaran_con_tu_cuenta` |
| N4 | `rebuildRecords` llama `pendingDeletes.done(ejercicio, tipo)` cuando un récord se crea o se actualiza. | `n4_deshacerBorradoConservaElRecordEnElServidor` |
| N5 | `SyncEngine.exclusive { }` (con el mismo mutex de la sincronización). `deleteAccount` primero cancela WorkManager (`SyncEngine.cancel`) y llama a `rupio_delete_my_account` dentro de `exclusive`, así espera la sincronización en curso. Si falla, se vuelve a programar la sincronización. | `n5_eliminarCuentaEsperaLaSincronizacion` |
| N6 | Después de crear la cuenta se pide el **código de 6 dígitos** que manda Neon Auth (`/email-otp/send-verification-otp` + `/email-otp/verify-email`, verificados contra el servidor real). Hasta confirmarlo la cuenta no está lista y **no se sube nada**. "Reenviar código" cada 60 s. "¿Correo equivocado?" cancela la cuenta recién creada (vacía, con `rupio_delete_my_account`) y, si los datos venían del modo sin cuenta, regresan a él sin perder nada. **Cambiar el correo**: Neon Auth tiene apagado el cambio de correo (`CHANGE_EMAIL_DISABLED`) y no hay ajuste ni en la consola ni en la API para encenderlo. Perfil → **Cambiar correo** explica eso y abre un correo al responsable desde el correo actual (ver abajo cómo hacer el cambio). | `n6_cuentaNuevaPideCodigoAntesDeEstarLista`, `n6_pantalla_del_codigo_del_correo` |

## Menores (P3)

| ID | Qué se hizo | Prueba |
|---|---|---|
| N7 | `FitText` con letra agrandada: hasta 3 líneas antes de achicar y piso de `base / fontScale × 1.5` (≈150 % del normal), nunca más que `base`. | `n7_textoGrandeNoBajaDe150PorCiento`, `n7_tarjetas_con_letra_al_200` |
| N8 | `RestAlertMode.MEDIA`: si el timbre está en silencio o vibración y no hay audífonos ni Bluetooth (`AudioManager.getDevices`), solo vibra. "Probar" sigue sonando. | `n8_musicaEnSilencioSinAudifonosSoloVibra` |
| N9 | Perfil → **Exportar mis datos**: `rupio_entrenamientos.csv`, `rupio_series.csv` y `rupio_peso_corporal.csv` (UTF-8 con BOM, kg con punto decimal, textos que empiezan con = + - @ neutralizados) compartidos con el menú de Android mediante `FileProvider` (solo la carpeta `cache/export/`). | `n9_exportarGeneraTresCsvYLosComparte`, `n2_n9_perfil_retirar_permiso_y_exportar` |

## Cambiar el correo de alguien (a mano, mientras Neon no lo permita)

1. Confirma que el correo llegó **desde el correo actual** de la cuenta.
2. Neon → SQL Editor:

   ```sql
   update neon_auth."user"
      set email = lower('correo-nuevo@ejemplo.com'), "emailVerified" = false, "updatedAt" = now()
    where email = lower('correo-actual@ejemplo.com');
   ```

3. Respóndele que cierre sesión y entre con el correo nuevo y su misma contraseña: la app le pedirá el código que llega al **correo nuevo** (así se comprueba que es suyo). Sus datos no cambian.

Si prefieres que se haga solo desde la app, se puede con una función SQL que toque las tablas de Neon Auth; no se aplicó porque es un cambio delicado en las cuentas y quiero tu visto bueno antes.

## Otros cambios

- `versionCode` 17, `versionName` 1.7.
- El servidor falso de las pruebas ahora acepta `PATCH` y combina columnas en el upsert, como Postgres.
- `docs/privacidad.html` y `docs/eliminar-cuenta.html` actualizados (hay que subirlos a GitHub).
