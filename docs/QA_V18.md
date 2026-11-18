# Rupio 1.8 · pedidos del 17 de noviembre de 2026

Cada punto tiene su prueba en `V18Test.kt` (lógica, servidor simulado) o `V18UiTest.kt` (pantallas, capturas 80–95), y el SQL del muro en `db/wall/prueba_muro.py` (24 casos contra Postgres local).

## Nivel Mecenas y muro de agradecimientos
- **Qué es**: un cuarto nivel, más caro (`rupio_mecenas`, $499 MXN), con todo lo de Fan **y tu nombre en el muro de agradecimientos** (Perfil → Muro de agradecimientos, también desde Apoya Rupio y desde la pantalla de gracias).
- **Orden**: por la suma de puntos (1 punto = 1 peso del precio de lista, lo decide el servidor en `wall_products`); empate → quien llegó antes. Para subir de lugar hay aportes extra de **+99, +249 y +499**; la tarjeta dice cuántos puntos faltan para pasar al de arriba y resalta el aporte que alcanza.
- **Datos**: nombre o apodo (2–30 caracteres, sin enlaces ni correos), lugar y fecha en que llegaste. Nunca correo ni montos de otros. Antes de pagar se pide el nombre y una **casilla de permiso** para publicarlo (LFPDPPP); se puede cambiar el nombre o **quitarse del muro** (conserva el nivel). Aviso de privacidad y página de eliminar cuenta actualizados.
- **Cuenta**: el muro necesita cuenta con correo confirmado (la Data API pide JWT). En modo sin cuenta se ofrece "Crear cuenta y conservar mis datos".
- **Seguridad**: cada compra se verifica **en el servidor** con la llave pública de licencias de Google Play (firma RSA-SHA1 en SQL puro, `rupio_play_signature_ok`), paquete, estado del pago, token único y que la compra sea de **esa** cuenta (`obfuscatedAccountId` = sha256 del id). Las tablas no se pueden leer ni escribir directo: solo por funciones `SECURITY DEFINER` sobre `auth.uid()`.
- **Flujo de compra**: se reconoce al momento → se registra en el servidor → se consume. Si falla (sin internet, servidor sin llave), queda pendiente y se reintenta al abrir la app; un reintento no suma dos veces. Si la compra llega sin nombre (p. ej. se cerró la app), se pide el nombre y el permiso al volver.
- **Neon**: `007_supporters_wall.sql` **aplicada en production el 17 nov 2026** (28 sentencias, caché de la Data API refrescada). Falta crear los 3 productos consumibles y cargar la llave de licencias con `db/wall/llave_play.py` (ver `docs/PAGOS_PLAY.md` §4b). Reembolsos: `rupio_wall_admin_revoke`; nombres ofensivos: `blocked = true`.
- **Cuenta del responsable («Angel», 5b614c54-…)**: Mecenas puesto a mano, sin pago, **oculto del muro** (499 puntos; pago de marca `manual-mecenas-angel-2026-11-17` con firma `manual`). Para mostrarlo: Perfil → Muro de agradecimientos → «Volver a aparecer». Para quitarlo: `delete from public.wall_entries where user_id = '5b614c54-a42e-49ca-b7a7-2214abf3c32d'; delete from public.wall_payments where purchase_token = 'manual-mecenas-angel-2026-11-17';`

## Aviso de fin de descanso que no sonaba
- **Causa probable**: el modo por defecto era "sonido de notificación", que en **vibración** no suena (y el modo "volumen de la música" también callaba en vibración sin audífonos). Además el reproductor era una variable local que el recolector de basura podía liberar a media reproducción.
- **Arreglos**: por defecto ahora es **"Con el volumen de la música (recomendado)"** (para quien nunca eligió); en vibración **suena**; en silencio total suena si hay audífonos o **si ya hay algo sonando** (un video, música); el reproductor se guarda hasta terminar. Fuera de la app el sonido sigue la misma regla.
- **Nuevo aviso** en la pantalla del entreno si no se va a oír (volumen multimedia en 0, o "Como notificación" con el teléfono en vibración), con botón **Probar sonido**.
- Se revisó la regla N8 de la v1.7 (en vibración solo vibraba); el silencio total sin audífonos ni nada sonando sigue sin sonar.

## Pantalla de entrenamiento
- **Gráfica del ejercicio** dentro del entreno (chip "Progreso" junto al descanso): peso máximo, 1RM estimado o volumen por sesión (reps, tiempo o ayuda según el tipo de ejercicio), últimas 30 sesiones, incluye lo que llevas hoy y se actualiza al palomear.
- **Kilos sin desalinear**: la otra unidad va **dentro** de la cajita del peso ("135" y "61.2 kg"); todas las cajitas y la ✓ miden 50 dp y empiezan a la misma altura (se mide en la prueba). Igual en el editor de rutina y al editar un entreno. "Anterior" ya no repite "lb" para caber en una línea.
- **Más aire en la serie completada**: margen arriba, abajo y a los lados de la zona verde y más separación entre cajitas (en pantallas de menos de 360 dp se reduce para que quepa).
- **Bug encontrado y arreglado**: dentro de una superserie las cajitas se estiraban a cientos de dp (`fillMaxSize` en el campo + `IntrinsicSize` del contenedor).
- En el detalle, los kilos van en su columna aunque la serie tenga 🏆.

## Calorías y reparto de músculos
- **Calorías estimadas** al terminar y en el detalle: 3.5 MET (Compendio 2024, código 02054) × tu último peso × duración; sin peso registrado usa 70 kg y lo dice; máximo 5 min por serie si el entreno quedó abierto; al tocarla explica el cálculo y que puede variar ±20–30 %.
- **Reparto de músculos** al terminar y en el detalle: series por músculo con el método fraccional (principal 1, secundario 0.5) y porcentaje.
