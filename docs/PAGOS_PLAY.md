# Activar los pagos de "Apoya Rupio" en Google Play

El código ya está listo (Google Play Billing 9.1). Lo que falta se hace en Play Console, una sola vez.

> **Nombre de paquete en Play: `mx.rupio.app`** (desde v2.1). `com.rupio.app` quedó apartado por una cuenta anterior que Google cerró y no se libera. En Play no se puede cambiar después. Es solo el `applicationId`: el `namespace` y los paquetes de Kotlin siguen siendo `com.rupio.app`. Cuenta de desarrollador: angelitox105@gmail.com.

## 1. Cuenta y perfil de pagos

1. Cuenta de desarrollador de Google Play (pago único de 25 USD).
2. **Play Console → Configuración → Perfil de pagos**: crea el perfil de comerciante. En México te pedirá datos fiscales (RFC). Sin esto no puedes crear productos de pago.

## 2. Llave de subida (firma)

Play Console no acepta una app firmada con la llave de debug. Crea tu llave **una vez** y guárdala muy bien (si la pierdes, Google te ayuda a cambiarla, pero tarda):

```bash
keytool -genkeypair -v -keystore rupio-upload.jks -alias rupio -keyalg RSA -keysize 4096 -validity 10000
```

En la raíz del proyecto crea `keystore.properties` (ya está en `.gitignore`, no se sube a git):

```properties
storeFile=rupio-upload.jks
storePassword=TU_CONTRASEÑA
keyAlias=rupio
keyPassword=TU_CONTRASEÑA
```

Después genera el paquete para Play:

```bash
./gradlew bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
```

## 3. Sube a "Prueba interna"

**Prueba y lanzamiento → Pruebas → Prueba interna → Crear versión** y sube el `.aab`. Google solo deja crear productos cuando ya subiste una versión que incluye la biblioteca de pagos.

> **Protección automática** (Play la activa por defecto): agrega una comprobación del instalador y, si la app se instaló desde otra fuente, pide descargarla desde Play. Mientras se reparta el APK directo, decide antes de publicar si se deja activa.

## 4. Crea los productos

**Monetizar → Productos → Productos únicos → Crear producto**. Los IDs deben ser **exactamente** estos (la app los busca así):

| ID del producto | Nombre | Precio (MXN) |
|---|---|---|
| `rupio_apoyo_cafe` | Un café ☕ | 49 |
| `rupio_apoyo_licuado` | Un licuado 💪 | 99 |
| `rupio_apoyo_fan` | Fan de Rupio 🏆 | 199 |

- Descripción sugerida: «Apoyo voluntario a Rupio. Quita el mensaje de apoyo y agrega adornos. No desbloquea funciones.»
- Deja que Google convierta el precio a otros países (puedes ajustar después).
- **Actívalos** (si quedan como borrador, la app no los ve).

### 4b. Nivel Mecenas y muro de agradecimientos (v1.8)

Estos tres son **consumibles** (se pueden comprar varias veces): en Play Console, al crearlos, en «Opciones de compra» deja que se puedan volver a comprar. La app los consume cuando el servidor ya los registró.

| ID del producto | Nombre | Precio (MXN) | Puntos en el muro |
|---|---|---|---|
| `rupio_mecenas` | Mecenas 🌟 | 499 | 499 |
| `rupio_muro_99` | Aporte al muro | 99 | 99 |
| `rupio_muro_249` | Aporte al muro | 249 | 249 |

- Descripción sugerida: «Apoyo voluntario a Rupio. Mecenas pone tu nombre en el muro de agradecimientos (con todo lo de Fan). Los aportes extra suman puntos y te suben de lugar. No desbloquea funciones.»
- Los **puntos** los decide el servidor (tabla `wall_products`), no el teléfono ni el precio en otra moneda. Si cambias un precio, cambia también los puntos ahí.
- **Antes de activar estos productos**, carga la llave pública de licencias en el servidor (sin ella el muro no acepta compras y la app las deja pendientes):
  1. Play Console → Monetizar con Play → Configuración de monetización → **Licencias** → copia la llave (empieza con `MIIBIjANBgkq…`). No es secreta.
  2. `python3 db/wall/llave_play.py "MIIBIjANBgkq…"` → imprime un `update public.wall_config …`.
  3. Pégalo en Neon → SQL Editor (rol `neondb_owner`) y córrelo.
- El servidor verifica **cada compra** con esa llave (firma RSA-SHA1 de Google, en SQL puro: `rupio_play_signature_ok`), que el paquete sea `mx.rupio.app` (columna `wall_config.package_name`; ver `db/migrations/011_paquete_play.sql`), que el pago esté completo, que el token no se haya usado y que la compra sea de **esa cuenta de Rupio** (la app manda `obfuscatedAccountId` = sha256 del id de la cuenta). Pruebas: `db/wall/prueba_muro.py` (25 casos contra un Postgres local).
- **Reembolsos**: el muro no se entera solo. Si reembolsas un aporte, corre `select public.rupio_wall_admin_revoke('<purchaseToken>');` (lo ves en la orden de Play Console o en `wall_payments`). Nombre ofensivo: `update public.wall_entries set blocked = true where display_name ilike '%…%';` (la persona ve que fue ocultado y puede cambiarlo).
- Para el muro hace falta **cuenta de Rupio** (la Data API pide JWT): en modo sin cuenta la app ofrece «Crear cuenta y conservar mis datos».

## 5. Prueba sin que te cobren

1. **Configuración → Pruebas de licencias**: agrega tu correo de Google.
2. En **Prueba interna → Testers**, agrega tu correo y abre el enlace de participación.
3. Instala Rupio **desde ese enlace de Play** (no el APK suelto: el APK de debug está firmado con otra llave y Play no le vende).
4. Termina 3 entrenos (o ve a Perfil → Apoya Rupio) y compra: Play mostrará «Compra de prueba» y no te cobrará.
5. Prueba también **«Tarjeta de prueba, se rechaza»** y **«pago lento»** (simula OXXO): la app debe mostrar «Pago pendiente» y activarse sola.

> En el **APK de debug** hay un botón «Simular compra (solo versión de pruebas)» para ver las recompensas sin Google Play, y «Quitar compras simuladas» en Perfil → Apoya Rupio. En la versión de Play (release) esos botones no existen.

## 6. Lo que hace la app con los pagos

- **Reconoce** cada compra al momento; si no, Google la reembolsaría a los 3 días.
- Al abrir la app o volver a ella, **confirma con Google Play**: respeta reembolsos (se quitan los adornos y el color vuelve a azul) y recupera las compras al reinstalar o cambiar de teléfono («Restaurar compras»).
- Las compras pertenecen a la **cuenta de Google**, no a la cuenta de Rupio: cerrar sesión no las quita.
- Café, licuado y Fan: sin servidor propio, la verificación es en el teléfono. Para un apoyo voluntario que solo da adornos es suficiente. Si algún día vendes funciones de pago, conviene verificar los recibos en un servidor (Google Play Developer API).
- Mecenas y aportes del muro: **sí se verifican en el servidor** (ver 4b), porque el lugar en el muro lo ven todos. La compra se reconoce enseguida, se registra en el servidor y solo entonces se consume; si falla (sin internet, servidor sin llave), queda pendiente y se reintenta al abrir la app, sin sumar dos veces.

## 7. Dinero e impuestos (México, noviembre 2026)

- Google se queda con **15 %** (primer millón de dólares al año). El esquema de 10 % + pasarela propia llega a México el **30 de septiembre de 2027**.
- En México, Google cobra el **IVA** al comprador y lo entrega al SAT. El **ISR** de tus ganancias es tuyo: consúltalo con un contador.
- Ejemplo con $99: unos $85 sin IVA, menos 15 % → **unos $72** para ti.
