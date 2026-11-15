# Rupio · registro de entrenamiento en español (Android · Kotlin)

App tipo Hevy, 100 % en español y con tema negro, que **te dice cuándo subir el peso** con reglas basadas en estudios ([docs/CIENCIA.md](docs/CIENCIA.md)).

## Qué incluye

- **Login con correo y contraseña** y **sesión persistente**: no se cierra sola, ni sin internet. Además hay **modo "Probar sin cuenta"** (datos solo en el teléfono).
- **253 ejercicios en español** con dos imágenes animadas (inicio/fin del movimiento, dominio público de *free-exercise-db*), técnica, botón de **video** (búsqueda en YouTube en español) y ejercicios personalizados.
- Ejercicios con **peso + reps**, **solo reps**, **peso corporal + lastre**, **asistidos** y **por tiempo**.
- **Motor de progresión**: "Sube a 140 lb", "Mantén y suma 1 rep" o "Baja ~10 %", con el porqué y las fuentes.
- **Temporizador de descanso** que **nunca suena doble** al marcar/desmarcar una serie (un solo temporizador con "generación" y un solo reproductor de sonido; hay pruebas unitarias de esto).
- **Superseries fáciles** ("Unir con el siguiente" o seleccionar varios) que se distinguen claramente: contenedor, árbol ├ └, letra A1/A2 y color apto para daltonismo; descanso **compartido** por ronda o **individual**.
- **Descanso total y duración estimada** de cada rutina.
- **Cronómetro de la sesión**, notificación con cuenta regresiva (+15 s / Saltar), pantalla encendida.
- **Récords**: peso máximo, 1RM estimado, mejor serie, más reps, más tiempo.
- **Progreso**: volumen por **semana, mes, 3 meses, año e histórico**, series por músculo/semana, mapa de constancia con racha, curva de 1RM, récords recientes, comparación con el periodo anterior.
- **Libras por defecto**, configurable a kg, y **siempre ves ambas unidades**.
- **Zonas horarias**: el día del entrenamiento se fija con tu zona al empezar; las horas se pintan en tu zona actual y, si entrenaste en otra, se indica ("03:05 (19:05 hora de Ciudad de México)").
- **Avatar con iniciales** estilo Google Meet.
- **Tutorial interactivo**: bienvenida con mini-demos que se pueden tocar + guía que resalta los botones reales la primera vez.
- **Rutinas por serie**: cada serie guarda su peso, reps y tipo (calentamiento, drop set, al fallo); la primera vez se usan como sugerencia.
- **Guía para armar tu rutina** (opcional, se puede saltar): días, tiempo, equipo (recomienda máquinas, pero preguntando), experiencia de principiante a avanzado y objetivo → propone cuerpo completo, torso/pierna o empuje/jalón/pierna, explicada y con series por músculo.
- **Tu progreso**: fuerza antes → ahora por ejercicio, este mes contra el anterior, racha, récords, todo lo levantado y qué significa (también si te estancas).
- **Peso corporal**: un registro por día, captura en 3 toques, tendencia que ignora los días raros y ritmo semanal en %.
- **Aprende**: 7 tutoriales que se pueden repetir (uso de la app, básicos, cómo armar tu rutina, cuánto entrenar, cómo progresar, gráficas, nutrición), glosario con más de 30 conceptos y sus estudios, beneficios del ejercicio y botones "?" junto a cada término.
- **Fin de descanso con sonido**: notificación con sonido propio aunque la pantalla esté apagada (pide permiso de notificaciones y avisa si están desactivadas).
- **Offline-first**: todo funciona sin internet y se sincroniza con Neon después.
- **Rendimiento medido** con datos dummy en SQLite y en tu base Neon: [docs/RENDIMIENTO_SQL.md](docs/RENDIMIENTO_SQL.md).
- **QA en casos extremos** (73 pruebas, auditoría de 95 consultas, carga y seguridad en el servidor real, cuenta demo de 8 años): [docs/QA_RENDIMIENTO.md](docs/QA_RENDIMIENTO.md).

## Backend: Neon (equivalente a Supabase)

La URL que se usó es de **Neon**. Rupio usa **Neon Auth** (correo/contraseña, basado en Better Auth) y la **Neon Data API** (REST compatible con PostgREST, con seguridad por fila). Es el mismo modelo que Supabase: la app habla directo con el backend y **la contraseña de la base nunca va dentro de la app**.

### Pasos (una sola vez)

1. **Esquema y catálogo**: ya están aplicados en tu base (`001_schema.sql`, `002_seed_exercises.sql`).
2. En la consola de Neon → tu proyecto → **Auth** → *Enable Neon Auth*.
3. **Postgres database → Data API** → marca *Use Neon Auth* → *Enable Data API*.
4. En el **SQL Editor** corre `db/migrations/003_security.sql` (RLS, permisos y funciones de estadísticas) y `004_plans_body_weight.sql` (series por serie y peso corporal). Ambos ya están aplicados en tu base.
5. Las URLs ya vienen dentro de la app (no hay nada que configurar en el teléfono). Para otro proyecto, ponlas en `local.properties`:
   ```properties
   NEON_AUTH_URL=https://ep-xxxx.neonauth.us-east-2.aws.neon.tech/neondb/auth
   NEON_DATA_API_URL=https://ep-xxxx.apirest.us-east-2.aws.neon.tech/neondb/rest/v1
   ```
6. **Cambia la contraseña de `neondb_owner`** (se compartió en un chat): Neon → Roles → Reset password.

## Compilar

Requisitos: JDK 17+ y Android SDK 35.

```bash
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # 71 pruebas: progresión, sincronización, UI con letra al 200 %, migraciones…
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
  timer/       temporizador de descanso (anti sonido doble) + servicio en primer plano
  ui/          Compose: inicio, rutinas y guía, entrenamiento, ejercicios, historial, progreso y "tu progreso",
               peso corporal, aprende (tutoriales, glosario, beneficios), perfil
db/migrations  esquema Postgres, catálogo, RLS
db/bench       generadores de datos dummy y benchmarks (Neon, Postgres local, SQLite)
docs/          ciencia y rendimiento
```
