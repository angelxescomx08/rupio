# Activar los pagos de "Apoya Rupio" en Google Play

El código ya está listo (Google Play Billing 9.1). Lo que falta se hace en Play Console, una sola vez.

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

## 4. Crea los 3 productos

**Monetizar → Productos → Productos únicos → Crear producto**. Los IDs deben ser **exactamente** estos (la app los busca así):

| ID del producto | Nombre | Precio (MXN) |
|---|---|---|
| `rupio_apoyo_cafe` | Un café ☕ | 49 |
| `rupio_apoyo_licuado` | Un licuado 💪 | 99 |
| `rupio_apoyo_fan` | Fan de Rupio 🏆 | 199 |

- Descripción sugerida: «Apoyo voluntario a Rupio. Quita el mensaje de apoyo y agrega adornos. No desbloquea funciones.»
- Deja que Google convierta el precio a otros países (puedes ajustar después).
- **Actívalos** (si quedan como borrador, la app no los ve).

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
- Sin servidor propio, la verificación es en el teléfono. Para un apoyo voluntario que solo da adornos es suficiente. Si algún día vendes funciones de pago, conviene verificar los recibos en un servidor (Google Play Developer API).

## 7. Dinero e impuestos (México, noviembre 2026)

- Google se queda con **15 %** (primer millón de dólares al año). El esquema de 10 % + pasarela propia llega a México el **30 de septiembre de 2027**.
- En México, Google cobra el **IVA** al comprador y lo entrega al SAT. El **ISR** de tus ganancias es tuyo: consúltalo con un contador.
- Ejemplo con $99: unos $85 sin IVA, menos 15 % → **unos $72** para ti.
