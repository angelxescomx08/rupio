# Rupio en Google Play: lo que falta llenar (v1.6)

La v1.6 resolvió en el código los bloqueantes de la auditoría QA. Esto es lo que se hace **fuera del código**, en Neon, GitHub y Play Console. Ve en orden.

## 1. Neon: activar "Eliminar mi cuenta" (5 min)

1. Neon → tu proyecto → **SQL Editor** → pega y corre `db/migrations/005_delete_account.sql`.
2. Listo: la app llama `POST /rpc/rupio_delete_my_account`. Sin este paso, "Eliminar mi cuenta" responde "No se pudo eliminar la cuenta (código 404). No se borró nada".
3. Al final del archivo hay 3 líneas comentadas para borrar la cuenta de prueba `qa-borrado-…@example.com` que creó la revisión al comprobar que el `/delete-user` de Neon Auth está apagado.

**Por qué una función SQL:** el endpoint `/delete-user` de Neon Auth responde 404 (está desactivado) y la app no puede tocar el esquema `neon_auth`. La función borra todo en una sola transacción y solo la cuenta de quien la llama (`auth.uid()`).

**Sesiones:** desde la v1.6 la app ya no guarda la contraseña. La sesión de Neon Auth dura 7 días y se renueva sola cada vez que se usa la app; quien pase más de 7 días sin abrirla verá "Tu sesión venció · Entrar de nuevo" (sus datos del teléfono no se pierden). Si Neon Auth te permite alargar la duración de la sesión en su configuración, puedes subirla.

## 2. GitHub Pages: publicar el aviso de privacidad y la página de borrado (5 min)

Play pide dos URLs públicas. Los archivos ya están en `docs/`:

- `docs/privacidad.html` → aviso de privacidad (el mismo texto que la app muestra en Perfil → Aviso de privacidad).
- `docs/eliminar-cuenta.html` → cómo eliminar la cuenta desde la app o sin ella.

Pasos: GitHub → `angelxescomx08/rupio` → Settings → Pages → *Deploy from a branch* → rama `main`, carpeta `/docs`. Quedan en:

- https://angelxescomx08.github.io/rupio/privacidad.html
- https://angelxescomx08.github.io/rupio/eliminar-cuenta.html

Esas URLs ya están escritas en la app (`ui/legal/PrivacyPolicy.kt`). Si usas otro dominio, cámbialas ahí y en las páginas. GitHub Pages gratis necesita que el repositorio sea público; si no quieres eso, súbelas a cualquier hosting estático.

**Correo de contacto:** las dos páginas y el aviso dicen "escribe al correo de contacto de la ficha de Rupio en Google Play", así que el correo que pongas en Play Console → *Detalles de la tienda* → *Correo electrónico* es el que atiende las solicitudes. Revisa también que el nombre del responsable ("José Ángel Hernández Radilla") sea como quieres que aparezca.

## 3. Play Console → Contenido de la app

### Política de privacidad
URL: `https://angelxescomx08.github.io/rupio/privacidad.html`

### Eliminación de la cuenta
- ¿La app permite crear cuentas? **Sí**.
- URL para solicitar la eliminación: `https://angelxescomx08.github.io/rupio/eliminar-cuenta.html`
- ¿Se puede eliminar desde la app? **Sí**: Perfil → Eliminar mi cuenta.
- Datos que se borran: todos. Datos que se conservan: ninguno (las copias automáticas del proveedor se borran en ≤ 30 días).

### Seguridad de los datos

| Pregunta | Respuesta |
|---|---|
| ¿Recopila o comparte datos? | Recopila: sí. Comparte con terceros: **no** (Neon solo almacena por encargo: es "proveedor de servicios", no cuenta como compartir). |
| ¿Datos cifrados en tránsito? | Sí (solo HTTPS). |
| ¿El usuario puede pedir que se borren? | Sí (en la app y en la web). |
| **Información personal → Nombre** | Recopilado · Funcionalidad de la app, Administración de la cuenta · Obligatorio para cuenta (no en modo sin cuenta). |
| **Información personal → Correo** | Recopilado · Administración de la cuenta · Obligatorio para cuenta. |
| **Salud y fitness → Información de salud** (peso corporal) | Recopilado · Funcionalidad de la app · Opcional. |
| **Salud y fitness → Información de actividad física** (entrenos, series, pesos) | Recopilado · Funcionalidad de la app · Obligatorio para usar la app. |
| **Información financiera → Historial de compras** | Recopilado (Google Play informa qué nivel de apoyo compró) · Funcionalidad de la app. Rupio no ve datos de pago. |
| Ubicación, contactos, fotos, mensajes, identificadores de publicidad | **No**. |
| ¿Procesamiento efímero? | No. |

### Declaración de apps de salud
Rupio registra entrenamiento y peso corporal. Elige **Fitness / actividad física** (no es app médica, no diagnostica). No usa Health Connect.

### Servicio en primer plano `specialUse` (la revisión lo rechaza sin esto)
Play Console → Contenido de la app → **Permisos de servicios en primer plano** → *Uso especial*.

- **Descripción:** "Mientras la persona tiene un entrenamiento en curso, Rupio muestra un cronómetro de la sesión y una cuenta regresiva del descanso entre series en una notificación, y avisa con un sonido cuando termina el descanso, aunque la pantalla esté apagada. El servicio lo inicia la persona al empezar un entreno y se detiene al terminarlo o descartarlo."
- **Impacto si se interrumpe:** "La persona no sabría cuándo termina su descanso con la pantalla apagada, que es como se usa en el gimnasio; el descanso entre series es parte del método de entrenamiento."
- **Por qué no otro tipo:** "No reproduce contenido (no es mediaPlayback), no usa ubicación ni salud de sensores (no es health) y debe sonar a una hora exacta durante 30 s a 10 min, repetidamente, mientras dura el entreno."
- **Video (30–60 s), guion:** 1) Abre Rupio → Entrenar → *Empezar rutina*. 2) Marca ✓ una serie: empieza el descanso. 3) Apaga la pantalla: se ve la notificación con la cuenta regresiva. 4) Al terminar suena el aviso. 5) Toca *Terminar* → *Guardar*: la notificación desaparece. Grábalo con la grabadora de pantalla de Android y súbelo a YouTube como "No listado".

## 4. Llave de subida y builds

- La v1.6 creó la llave `keystore/rupio-upload.jks` y `keystore.properties` en la carpeta del proyecto (no van en git). **Respáldalas** en tu gestor de contraseñas: con ellas se firman todas las versiones. Si la pierdes, Play deja cambiarla, pero hay que pedirlo y tarda.
- Con la llave, `./gradlew bundleRelease` genera `app/build/outputs/bundle/release/app-release.aab` para Play Console, y `assembleRelease` el APK firmado.
- **No repartas el APK debug** (`debuggable=true` y actividades de prueba exportadas). Para testers: Play Console → *Pruebas internas* → sube el AAB y agrega sus correos.
- API objetivo: la v1.6 apunta a Android 16 (API 36), lo que Play exige desde el 31 de agosto de 2026.

## 5. Después de publicar en prueba interna

Repetir la revisión en un teléfono real (no se pudo aquí): TalkBack en un entreno completo, red limitada a 3G en la primera descarga de la cuenta demo, letra al 200 % y el aviso de descanso con música en audífonos.
