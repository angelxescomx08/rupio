# Rupio · registro de entrenamiento en español (Android · Kotlin)

App tipo Hevy, 100 % en español y con tema negro, que **te dice cuándo subir el peso** con reglas basadas en estudios ([docs/CIENCIA.md](docs/CIENCIA.md)).

## Qué incluye

- **Login con correo y contraseña** y **sesión persistente** (no se cierra sin internet). La contraseña **nunca** se guarda en el teléfono: si la sesión del servidor vence, la app sigue guardando en el teléfono y pide "Entrar de nuevo". Hay **"¿Olvidaste tu contraseña?"** con código por correo (Neon Auth, Email OTP).
- **Modo "Probar sin cuenta"** (datos solo en el teléfono) con **"Crear cuenta y conservar mis datos"**: todo lo guardado pasa a la cuenta nueva y se sube.
- **Eliminar mi cuenta** en Perfil (y página web para Play): borra cuenta y datos en el servidor en una sola transacción (`db/migrations/005_delete_account.sql`). **Aviso de privacidad** dentro de la app y en `docs/privacidad.html`.
- **Estado del respaldo claro**: un mensaje por causa (sesión vencida, sin internet, servidor caído, cambios rechazados con "Revisar", pendientes) y fecha relativa ("hace 5 min", "ayer 14:05"); nube con el número de pendientes en Inicio e Historial.
- **262 ejercicios en español** (6 de cardio) con técnica, botón de **video** (búsqueda en YouTube en español) y ejercicios personalizados. **Imágenes** (inicio/fin del movimiento) de [RepDB](https://repdb.co) para 227 de ellos, incluidas en la app en WebP pero **fuera de git** por su licencia: después de clonar, corre `tools/repdb/instalar_imagenes.py` (ver [docs/IMAGENES_REPDB.md](docs/IMAGENES_REPDB.md)). La lista de ejercicios parte de *free-exercise-db* (dominio público).
- Ejercicios con **peso + reps**, **solo reps**, **peso corporal + lastre**, **asistidos** y **por tiempo**.
- **Motor de progresión**: "Sube a 140 lb", "Mantén y suma 1 rep" o "Baja ~10 %", con el porqué y las fuentes.
- **Temporizador de descanso** que **nunca suena doble** al marcar/desmarcar una serie (un solo temporizador con "generación" y un solo reproductor de sonido; hay pruebas unitarias de esto).
- **Superseries fáciles** ("Unir con el siguiente" o seleccionar varios) que se distinguen claramente: contenedor, árbol ├ └, letra A1/A2 y color apto para daltonismo; descanso **compartido** por ronda o **individual**.
- **Descanso total y duración estimada** de cada rutina.
- **Cronómetro de la sesión**, notificación con cuenta regresiva (+15 s / Saltar), pantalla encendida (se puede apagar). El descanso sobrevive a que Android cierre la app.
- **Récords**: peso máximo, 1RM estimado, mejor serie, más reps, más tiempo.
- **Progreso**: volumen por **semana, mes, 3 meses, año e histórico**, series por músculo/semana, mapa de constancia con racha, curva de 1RM, récords recientes, comparación con el periodo anterior.
- **Libras por defecto**, configurable a kg, y **siempre ves ambas unidades**.
- **Zonas horarias**: el día del entrenamiento se fija con tu zona al empezar; las horas se pintan en tu zona actual y, si entrenaste en otra, se indica ("03:05 (19:05 hora de Ciudad de México)").
- **Avatar con iniciales** estilo Google Meet.
- **Tutorial interactivo**: bienvenida con mini-demos que se pueden tocar + guía que resalta los botones reales la primera vez.
- **Rutinas por serie**: cada serie guarda su peso, reps y tipo (calentamiento, drop set, al fallo); la primera vez se usan como sugerencia.
- **Guía para armar tu rutina** (opcional, se puede saltar): días, tiempo, equipo (recomienda máquinas, pero preguntando), experiencia de principiante a avanzado y objetivo → propone cuerpo completo, torso/pierna o empuje/jalón/pierna, explicada y con series por músculo.
- **Tu progreso**: fuerza antes → ahora por ejercicio, este mes contra el anterior, racha, récords, todo lo levantado y qué significa (también si te estancas).
- **Peso corporal** con **pestaña propia** en la barra de abajo: un registro por día, captura en 3 toques, tendencia que ignora los días raros y ritmo semanal en %. Va en **kilos por defecto** (aparte de las pesas, que siguen en libras) y se cambia a libras con un botón kg | lb al registrar o arriba de la pantalla.
- **Compartir Rupio** desde Perfil, Apoya Rupio y la pantalla de gracias (menú de Android con el enlace de Google Play).
- **Aprende** (en Perfil y al final de Progreso): 7 tutoriales que se pueden repetir (uso de la app, básicos, cómo armar tu rutina, cuánto entrenar, cómo progresar, gráficas, nutrición), glosario con más de 30 conceptos y sus estudios, beneficios del ejercicio y botones "?" junto a cada término.
- **Fin de descanso con sonido**: notificación con sonido propio aunque la pantalla esté apagada; con la app a la vista avisa dentro de la pantalla del entreno. En Perfil: **Probar**, sonido, solo vibración o **con el volumen de la música** (baja la música un momento). El permiso de notificaciones se pide al empezar el primer entreno, explicando para qué.
- **Historial** con búsqueda por ejercicio y salto a cualquier mes; no regresa arriba cuando cambia la lista. Quitar ejercicio, eliminar serie y borrar entreno tienen **Deshacer**.
- **Offline-first**: todo funciona sin internet y se sincroniza con Neon después.
- **Rendimiento medido** con datos dummy en SQLite y en tu base Neon: [docs/RENDIMIENTO_SQL.md](docs/RENDIMIENTO_SQL.md).
- **Apoya Rupio** (v1.4): todo sigue gratis; quien quiera apoya con un **pago único** de Google Play en 3 niveles (☕ $49 · 💪 $99 · 🏆 $199 MXN) que quitan el mensaje y dan adornos: insignia, colores de la app, ícono dorado y pantalla de gracias con confeti. El mensaje sale al terminar un entreno (desde el 3.º), se cierra con un toque y está escrito con base en estudios de psicología: [docs/APOYO_CIENCIA.md](docs/APOYO_CIENCIA.md). Activar los pagos en Play Console: [docs/PAGOS_PLAY.md](docs/PAGOS_PLAY.md).
- **Cerrar sesión por pasos**: pantalla que explica qué pasa y por qué tarda (sube lo pendiente → borra el teléfono → cierra la sesión); si no hay internet, pregunta antes de perder cambios.
- **Auditoría QA v1.5 → arreglos v1.6**: [docs/QA_V16.md](docs/QA_V16.md).
- **Segunda revisión QA → v1.7**: editar un entreno terminado (y "Corregir" en Revisar), permiso expreso para el peso corporal (se retira en Perfil y borra el historial), código de 6 dígitos al crear la cuenta, exportar tus datos en CSV, récords que no se pisan al juntar cuentas: [docs/QA_V17.md](docs/QA_V17.md).
- **v1.8**: nivel **Mecenas** ($499) con **muro de agradecimientos** (nombre, lugar y fecha; aportes extra de $99/$249/$499 suben de lugar; cada compra se verifica en el servidor con la firma de Google Play, en SQL: `db/migrations/007_supporters_wall.sql`); **gráfica del ejercicio dentro del entreno** (peso máx., 1RM, volumen); **kilos dentro de la cajita** del peso (filas alineadas, también en el editor de rutina y al editar un entreno); más aire en la serie completada; **calorías estimadas** (MET del Compendio 2024) y **reparto de músculos** al terminar y en el detalle; el **aviso de descanso** ahora suena en modo vibración y sobre un video (por defecto con el volumen de la música) y avisa si el volumen está en 0: [docs/QA_V18.md](docs/QA_V18.md).
- **QA en casos extremos** (pruebas automáticas, auditoría de 95 consultas, carga y seguridad en el servidor real, cuenta demo de 8 años): [docs/QA_RENDIMIENTO.md](docs/QA_RENDIMIENTO.md).

## Backend: Neon (equivalente a Supabase)

La URL que se usó es de **Neon**. Rupio usa **Neon Auth** (correo/contraseña, basado en Better Auth) y la **Neon Data API** (REST compatible con PostgREST, con seguridad por fila). Es el mismo modelo que Supabase: la app habla directo con el backend y **la contraseña de la base nunca va dentro de la app**.

### Pasos (una sola vez)

1. **Esquema y catálogo**: ya están aplicados en tu base (`001_schema.sql`, `002_seed_exercises.sql`).
2. En la consola de Neon → tu proyecto → **Auth** → *Enable Neon Auth*.
3. **Postgres database → Data API** → marca *Use Neon Auth* → *Enable Data API*.
4. En el **SQL Editor** corre `db/migrations/003_security.sql` (RLS, permisos y funciones de estadísticas), `004_plans_body_weight.sql` (series por serie y peso corporal), `005_delete_account.sql` (eliminar la cuenta) y `006_health_consent.sql` (fecha del permiso del peso corporal). Todos ya están aplicados en tu base.
5. Las URLs ya vienen dentro de la app (no hay nada que configurar en el teléfono). Para otro proyecto, ponlas en `local.properties`:
   ```properties
   NEON_AUTH_URL=https://ep-xxxx.neonauth.us-east-2.aws.neon.tech/neondb/auth
   NEON_DATA_API_URL=https://ep-xxxx.apirest.us-east-2.aws.neon.tech/neondb/rest/v1
   ```
6. **Cambia la contraseña de `neondb_owner`** (se compartió en un chat): Neon → Roles → Reset password.

## Compilar

Requisitos: JDK 17+ y Android SDK 36 (la app apunta a Android 16, API 36). Para publicar: `docs/PLAY_DECLARACIONES.md`.

```bash
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/ (solo para ti: no lo repartas)
./gradlew assembleRelease        # APK firmado con keystore/rupio-upload.jks (para probar en teléfonos)
./gradlew bundleRelease          # AAB para Play Console
./gradlew testDebugUnitTest      # pruebas: progresión, sincronización, UI con letra al 200 %, migraciones…
python3 db/bench/qa_sql_audit.py # auditoría SQL con un usuario extremo
python3 db/bench/qa_server.py    # carga y seguridad contra Neon (usuario temporal)
DEMO_EMAIL=… DEMO_PASSWORD=… python3 db/demo/demo_extremo.py  # cuenta demo con 8 años de datos
```

O abre la carpeta en Android Studio.

## Estructura

```
app/src/main/java/com/rupio/app/
  core/        UUIDv7, unidades lb/kg, formatos, fechas y zonas horarias
  domain/      progresión, 1RM, descansos/superseries, récords (puro Kotlin, con pruebas)
  data/local/  Room: entidades, índices, DAOs (SQL medido)
  data/auth/   Neon Auth + sesión persistente cifrada (Android Keystore)
  data/remote/ Sync offline-first con la Data API (push por lotes, pull incremental)
  data/billing/ Google Play Billing: niveles de apoyo, compras pendientes (OXXO), colores e ícono dorado
  timer/       temporizador de descanso (anti sonido doble) + servicio en primer plano
  ui/          Compose: inicio, rutinas y guía, entrenamiento, ejercicios, historial, progreso y "tu progreso",
               peso corporal, aprende (tutoriales, glosario, beneficios), perfil
db/migrations  esquema Postgres, catálogo, RLS
db/bench       generadores de datos dummy y benchmarks (Neon, Postgres local, SQLite)
docs/          ciencia y rendimiento
```
