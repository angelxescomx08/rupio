# Rupio · estado

Actualizado: 20 nov 2026.

## Paquete en Google Play: `mx.rupio.app`

- Cuenta de desarrollador: angelitox105@gmail.com.
- `applicationId = "mx.rupio.app"` en `app/build.gradle.kts`. En Play **no se puede cambiar después**: no usar otro.
- `com.rupio.app` está ocupado en Play: lo apartó una cuenta anterior que Google cerró, y Google no libera esos nombres.
- `namespace` y paquetes de Kotlin siguen en `com.rupio.app` (clases, `R`, `BuildConfig`, alias de íconos `com.rupio.app.Launcher*`). Solo cambió el id de la app.
- Lo que ya usa el id nuevo por su cuenta: FileProvider (`${applicationId}.files` → `mx.rupio.app.files`), enlace de Compartir (`context.packageName`), sonido del descanso, ajustes de notificaciones. La versión benchmark queda `mx.rupio.app.bench`.
- No hay deep links. Los canales de notificación, SharedPreferences y DataStore tienen nombres propios, no dependen del paquete.

## ⚠️ No se instala encima del APK viejo

- Android trata `mx.rupio.app` como **otra app**. El `Rupio.apk` instalado (`com.rupio.app`, v1.8, versionCode 18) no se actualiza: quedan las dos en el teléfono.
- Los datos locales (Room) de la app vieja **no pasan solos** a la nueva. Antes de desinstalar la vieja:
  - entrar con la cuenta en la vieja y esperar a que se sincronice (nube sin pendientes); en la nueva, entrar con la misma cuenta y se bajan; **o**
  - Perfil → Exportar mis datos (CSV) en la vieja.
- En modo "sin cuenta" no hay nube: primero "Crear cuenta y conservar mis datos" en la vieja, o exportar los CSV.
- Las compras de Google Play van por app: lo comprado con `com.rupio.app` no aparece en `mx.rupio.app`. Los lugares del muro sí se conservan, porque van con la cuenta de Rupio en el servidor.

## Versión y firma

- v2.1 (versionCode 21). Incluye todo lo de v2.0 (inglés, modo claro, recompensas, muro visible), que aún no tiene commit.
- Llave de subida: `keystore/rupio-upload.jks` (alias `rupio`, en `keystore.properties`; las dos fuera de git). SHA-256 del certificado: `90:5B:88:5F:92:FC:DD:66:92:44:F4:D6:4B:D4:D0:90:0B:63:58:82:8F:33:E4:C8:95:05:4C:E3:35:11:B5:F7` (la misma de v1.6 a v1.8).
- Artefactos del 20 nov 2026 (R8, sin depuración):
  - `app/build/outputs/bundle/release/app-release.aab` (para Play)
  - `app/build/outputs/apk/release/app-release.apk` (para repartir directo)
- Pruebas: 241, 0 fallas, 3 omitidas.

## Servidor (Neon)

| Migración | Estado |
|---|---|
| 009_permisos_internos.sql | Aplicada en production el 18 nov 2026. Confirmado el 20 nov 2026: ninguna función interna abierta para `authenticated`. |
| 010_cardio.sql | Aplicada en production el 18 nov 2026 (ver `docs/QA_V19.md`). |
| **011_paquete_play.sql** | **Aplicada en production el 20 nov 2026, 12:08 CDMX**, con visto bueno del usuario. |

La 011 cambia `wall_config.package_name` (y su default) de `com.rupio.app` a `mx.rupio.app`. Sin ella, `rupio_wall_register` rechazaría todas las compras del muro de la app nueva (`rupio:bad_purchase`). Termina con revokes explícitos a `authenticated` y `anonymous` en todas las tablas y funciones internas del muro y las ligas.

- Antes de production se probó en Postgres 16 local (Docker), con el default ACL de Neon y 001–010: `prueba_muro.py` 25/25 (con un caso nuevo: el paquete viejo se rechaza), `prueba_ligas.sql` 17 ok, permisos correctos.
- Verificado en production después de aplicarla: `package_name` y su default = `mx.rupio.app`, `authenticated` no lee `wall_config` ni ejecuta funciones internas, y la API (`rupio_wall*`, `rupio_league*`) sigue abierta.
- La llave de licencias **todavía no está cargada** (`rsa_modulus` vacío): el muro no acepta compras hasta cargarla (paso 3 de abajo).
- Nota: `powershell -File db/neon/aplicar.ps1 … --verificar="…"` pierde las comillas cuando la consulta es larga. Funciona llamar directo `node db/neon/aplicar.cjs …` con `NODE_PATH=.agents/neon/node_modules`.

## Neon Auth

No depende del nombre de paquete ni del certificado de la app:
- Solo usa correo + contraseña y códigos por correo (`/sign-up/email`, `/email-otp/*`). No hay Google Sign-In ni OAuth, así que no hay huellas SHA-1 ni redirecciones.
- El encabezado `Origin` que manda la app es el host de Neon Auth mismo, no algo de la app.
- No hay que cambiar nada.

## Al subir a Play (todavía no)

1. **Protección automática**: Play la activa por defecto. Agrega una comprobación del instalador y pide descargar desde Play si la app vino de otra fuente. **Preguntar al usuario** si la quiere, porque hoy reparte el APK directo.
2. Crear los productos en Play Console con los mismos IDs:
   - apoyo: `rupio_apoyo_cafe`, `rupio_apoyo_licuado`, `rupio_apoyo_fan`
   - muro (consumibles): `rupio_mecenas`, `rupio_muro_99`, `rupio_muro_249`
   - Pasos en `docs/PAGOS_PLAY.md`.
3. Cargar la llave de licencias de la app nueva: `python db/wall/llave_play.py "MIIBIjANBgkq…"` y correr el `update` que imprime. Es una llave por app, así que la de `mx.rupio.app` es nueva. Hacerlo antes de activar los productos del muro.
