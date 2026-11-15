-- =====================================================================
-- Rupio · 001_schema.sql  (PostgreSQL 16+ / Neon)
-- Esquema principal. Idempotente: se puede correr varias veces.
--
-- Principios de rendimiento aplicados (ver docs/RENDIMIENTO_SQL.md):
--  * Cada índice existe porque una consulta concreta de la app lo usa.
--  * Índices compuestos que empiezan por user_id (RLS + filtro) y
--    terminan en la columna de orden/rango → sin "Sort", rangos directos.
--  * INCLUDE (...) para que las consultas calientes sean index-only scans.
--  * Índices parciales (WHERE NOT deleted / completed) → más pequeños.
--  * Las FK hijas tienen índice (borrado en cascada sin seq scans).
--  * Columnas ordenadas por alineación (16/8 → 4 → 2 → 1 → varlena)
--    para evitar padding y ahorrar bytes por fila.
--  * IDs UUIDv7 generados en el cliente: ordenados en el tiempo,
--    inserciones al final del B-tree (menos page splits que UUIDv4).
--  * updated_at lo asigna el SERVIDOR (trigger) → sync incremental sin
--    depender del reloj del teléfono.
--  * Fechas locales (local_date / performed_on) guardadas al crear el
--    entrenamiento con la zona horaria del usuario → agrupar por semana /
--    mes nunca depende de la zona del servidor.
-- =====================================================================

-- ---------- utilidades ----------
create or replace function public.rupio_touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := clock_timestamp();
  return new;
end $$;

-- ---------- perfiles ----------
create table if not exists public.profiles (
  user_id          uuid        primary key,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  default_rest_sec integer     not null default 90 check (default_rest_sec between 0 and 1800),
  week_start       smallint    not null default 1  check (week_start in (1, 7)),   -- ISO: 1 lunes, 7 domingo
  tutorial_done    boolean     not null default false,
  display_name     text        not null check (char_length(display_name) between 1 and 80),
  weight_unit      text        not null default 'lb' check (weight_unit in ('lb', 'kg')),
  time_zone        text        not null default 'America/Mexico_City'
);

-- ---------- ejercicios (catálogo global + personalizados) ----------
create table if not exists public.exercises (
  id                text        primary key,              -- slug del catálogo o UUID del cliente
  owner_id          uuid,                                  -- NULL = catálogo global
  updated_at        timestamptz not null default now(),
  is_lower_body     boolean     not null default false,
  deleted           boolean     not null default false,
  name              text        not null check (char_length(name) between 1 and 120),
  primary_muscle    text        not null,
  secondary_muscles text[]      not null default '{}',
  equipment         text        not null default 'otro',
  tracking          text        not null default 'weight_reps'
                    check (tracking in ('weight_reps','reps_only','weighted_bodyweight','assisted_bodyweight','duration'))
);
-- Sync de ejercicios personalizados (el catálogo global no se sincroniza).
create index if not exists exercises_owner_sync_idx
  on public.exercises (owner_id, updated_at) where owner_id is not null;

-- ---------- rutinas ----------
create table if not exists public.routines (
  id         uuid        primary key,
  user_id    uuid        not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  position   integer     not null default 0,
  deleted    boolean     not null default false,
  name       text        not null check (char_length(name) between 1 and 120),
  notes      text
);
-- Sirve a: lista de rutinas del usuario (prefijo user_id) y sync incremental.
create index if not exists routines_user_sync_idx on public.routines (user_id, updated_at);

create table if not exists public.routine_exercises (
  id                 uuid        primary key,
  routine_id         uuid        not null references public.routines (id) on delete cascade,
  user_id            uuid        not null,
  superset_id        uuid,                    -- mismo valor = misma superserie
  updated_at         timestamptz not null default now(),
  position           integer     not null,
  rest_sec           integer     not null default 90  check (rest_sec between 0 and 1800),
  superset_rest_sec  integer                          check (superset_rest_sec between 0 and 1800),
  target_sets        smallint    not null default 3   check (target_sets between 1 and 20),
  rep_min            smallint    not null default 8   check (rep_min between 1 and 100),
  rep_max            smallint    not null default 12  check (rep_max between 1 and 100),
  superset_rest_mode smallint    not null default 0   check (superset_rest_mode in (0, 1)), -- 0 individual · 1 compartido
  deleted            boolean     not null default false,
  exercise_id        text        not null,
  notes              text,
  check (rep_min <= rep_max)
);
create index if not exists routine_exercises_routine_idx on public.routine_exercises (routine_id, position);
create index if not exists routine_exercises_user_sync_idx on public.routine_exercises (user_id, updated_at);

-- ---------- entrenamientos ----------
create table if not exists public.workouts (
  id              uuid        primary key,
  user_id         uuid        not null,
  routine_id      uuid,
  started_at      timestamptz not null,
  ended_at        timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  total_volume_kg double precision not null default 0,
  local_date      date        not null,       -- fecha local del usuario al iniciar (su zona horaria)
  duration_sec    integer,
  total_sets      integer     not null default 0,
  total_reps      integer     not null default 0,
  deleted         boolean     not null default false,
  name            text        not null check (char_length(name) between 1 and 120),
  time_zone       text        not null,       -- IANA, p. ej. 'America/Mexico_City'
  notes           text,
  check (ended_at is null or ended_at >= started_at)
);
-- Historial (keyset pagination) + estadísticas por rango de fechas: index-only scan.
create index if not exists workouts_user_date_idx
  on public.workouts (user_id, local_date desc, started_at desc)
  include (total_volume_kg, total_sets, total_reps, duration_sec)
  where not deleted;
create index if not exists workouts_user_sync_idx on public.workouts (user_id, updated_at);

create table if not exists public.workout_exercises (
  id                 uuid        primary key,
  workout_id         uuid        not null references public.workouts (id) on delete cascade,
  user_id            uuid        not null,
  superset_id        uuid,
  updated_at         timestamptz not null default now(),
  position           integer     not null,
  rest_sec           integer     not null default 90 check (rest_sec between 0 and 1800),
  superset_rest_sec  integer                         check (superset_rest_sec between 0 and 1800),
  rep_min            smallint    not null default 8,
  rep_max            smallint    not null default 12,
  superset_rest_mode smallint    not null default 0 check (superset_rest_mode in (0, 1)),
  deleted            boolean     not null default false,
  exercise_id        text        not null,
  notes              text
);
create index if not exists workout_exercises_workout_idx on public.workout_exercises (workout_id, position);
create index if not exists workout_exercises_user_sync_idx on public.workout_exercises (user_id, updated_at);

create table if not exists public.workout_sets (
  id                  uuid        primary key,
  workout_exercise_id uuid        not null references public.workout_exercises (id) on delete cascade,
  workout_id          uuid        not null,
  user_id             uuid        not null,
  completed_at        timestamptz,
  updated_at          timestamptz not null default now(),
  performed_on        date        not null,  -- = workouts.local_date (desnormalizado para rangos)
  weight_kg           real,                  -- negativo = asistencia (dominadas asistidas)
  rpe                 real        check (rpe is null or rpe between 1 and 10),
  duration_sec        integer     check (duration_sec is null or duration_sec >= 0),
  reps                smallint    check (reps is null or reps between 0 and 1000),
  position            smallint    not null,
  set_type            smallint    not null default 0 check (set_type between 0 and 3), -- 0 normal · 1 calentamiento · 2 drop · 3 fallo
  completed           boolean     not null default false,
  deleted             boolean     not null default false,
  exercise_id         text        not null,
  -- Volumen = peso × reps solo de series efectivas completadas (calentamiento y asistencia no suman).
  volume_kg           real generated always as (
    case when completed and not deleted and set_type <> 1
         then greatest(coalesce(weight_kg, 0), 0) * coalesce(reps, 0)
         else 0 end) stored
);
-- Cargar las series de un ejercicio dentro de un entrenamiento (+ índice de la FK).
create index if not exists workout_sets_exercise_pos_idx on public.workout_sets (workout_exercise_id, position);
-- Historial por ejercicio, progresión ("¿subo peso?"), récords y 1RM estimado: index-only.
create index if not exists workout_sets_user_exercise_idx
  on public.workout_sets (user_id, exercise_id, performed_on desc)
  include (weight_kg, reps, duration_sec, set_type, rpe)
  where completed and not deleted;
-- Volumen y series por músculo en un rango de fechas: index-only.
create index if not exists workout_sets_user_date_idx
  on public.workout_sets (user_id, performed_on)
  include (exercise_id, volume_kg, reps, set_type)
  where completed and not deleted;
create index if not exists workout_sets_user_sync_idx on public.workout_sets (user_id, updated_at);

-- ---------- récords personales ----------
create table if not exists public.personal_records (
  user_id     uuid             not null,
  set_id      uuid,
  achieved_at timestamptz      not null,
  updated_at  timestamptz      not null default now(),
  value       double precision not null,
  kind        smallint         not null check (kind between 1 and 5), -- 1 peso máx · 2 1RM est. · 3 volumen serie · 4 reps máx · 5 tiempo máx
  exercise_id text             not null,
  primary key (user_id, exercise_id, kind)
);
create index if not exists personal_records_user_sync_idx on public.personal_records (user_id, updated_at);

-- ---------- triggers updated_at (servidor manda) ----------
do $$
declare t text;
begin
  foreach t in array array['profiles','exercises','routines','routine_exercises','workouts',
                           'workout_exercises','workout_sets','personal_records'] loop
    execute format('drop trigger if exists %I_touch on public.%I', t, t);
    execute format('create trigger %I_touch before insert or update on public.%I
                    for each row execute function public.rupio_touch_updated_at()', t, t);
  end loop;
end $$;

-- Estadísticas del planificador al día.
analyze public.exercises;
