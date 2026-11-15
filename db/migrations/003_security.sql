-- =====================================================================
-- Rupio · 003_security.sql
-- YA APLICADO en Neon el 14-nov-2026 (RLS en 8 tablas, 32 políticas, probado con 2 usuarios).
-- Correr DESPUÉS de activar Neon Auth + Data API en la consola de Neon (en el SQL Editor: los cuerpos
-- BEGIN ATOMIC contienen ";" y no deben partirse por sentencias)
-- (eso crea los roles `authenticated` / `anonymous` y la extensión
-- pg_session_jwt con auth.uid() / auth.user_id()).
--
-- Seguridad: la app solo habla con la Data API usando el JWT del usuario.
-- Cada fila lleva user_id y RLS garantiza que nadie vea datos ajenos.
--
-- Rendimiento de RLS:
--  * (select auth.uid()) en vez de auth.uid(): Postgres lo evalúa UNA vez
--    por consulta (InitPlan) en lugar de una vez por fila.
--  * Políticas TO authenticated (no se evalúan para otros roles).
--  * Todas las columnas usadas en políticas están indexadas (user_id ...).
--  * Una política por operación (sin FOR ALL) → planes más simples.
-- =====================================================================

grant usage on schema public to authenticated;
grant select on public.exercises to authenticated;
grant insert, update, delete on public.exercises to authenticated;
grant select, insert, update, delete on
  public.profiles, public.routines, public.routine_exercises, public.workouts,
  public.workout_exercises, public.workout_sets, public.personal_records
  to authenticated;

alter table public.profiles          enable row level security;
alter table public.exercises         enable row level security;
alter table public.routines          enable row level security;
alter table public.routine_exercises enable row level security;
alter table public.workouts          enable row level security;
alter table public.workout_exercises enable row level security;
alter table public.workout_sets      enable row level security;
alter table public.personal_records  enable row level security;

-- ---------- políticas genéricas "dueño de la fila" ----------
do $$
declare t text; col text;
begin
  for t, col in select * from (values
      ('profiles','user_id'), ('routines','user_id'), ('workouts','user_id'),
      ('personal_records','user_id')) v(t, c) loop
    execute format('drop policy if exists %1$s_select on public.%1$I', t);
    execute format('drop policy if exists %1$s_insert on public.%1$I', t);
    execute format('drop policy if exists %1$s_update on public.%1$I', t);
    execute format('drop policy if exists %1$s_delete on public.%1$I', t);
    execute format('create policy %1$s_select on public.%1$I for select to authenticated using (%2$I = (select auth.uid()))', t, col);
    execute format('create policy %1$s_insert on public.%1$I for insert to authenticated with check (%2$I = (select auth.uid()))', t, col);
    execute format('create policy %1$s_update on public.%1$I for update to authenticated using (%2$I = (select auth.uid())) with check (%2$I = (select auth.uid()))', t, col);
    execute format('create policy %1$s_delete on public.%1$I for delete to authenticated using (%2$I = (select auth.uid()))', t, col);
  end loop;
end $$;

-- ---------- hijos: además del dueño, el padre debe ser del mismo usuario ----------
drop policy if exists routine_exercises_select on public.routine_exercises;
drop policy if exists routine_exercises_insert on public.routine_exercises;
drop policy if exists routine_exercises_update on public.routine_exercises;
drop policy if exists routine_exercises_delete on public.routine_exercises;
create policy routine_exercises_select on public.routine_exercises for select to authenticated
  using (user_id = (select auth.uid()));
create policy routine_exercises_insert on public.routine_exercises for insert to authenticated
  with check (user_id = (select auth.uid())
              and exists (select 1 from public.routines r where r.id = routine_id and r.user_id = (select auth.uid())));
create policy routine_exercises_update on public.routine_exercises for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid())
              and exists (select 1 from public.routines r where r.id = routine_id and r.user_id = (select auth.uid())));
create policy routine_exercises_delete on public.routine_exercises for delete to authenticated
  using (user_id = (select auth.uid()));

drop policy if exists workout_exercises_select on public.workout_exercises;
drop policy if exists workout_exercises_insert on public.workout_exercises;
drop policy if exists workout_exercises_update on public.workout_exercises;
drop policy if exists workout_exercises_delete on public.workout_exercises;
create policy workout_exercises_select on public.workout_exercises for select to authenticated
  using (user_id = (select auth.uid()));
create policy workout_exercises_insert on public.workout_exercises for insert to authenticated
  with check (user_id = (select auth.uid())
              and exists (select 1 from public.workouts w where w.id = workout_id and w.user_id = (select auth.uid())));
create policy workout_exercises_update on public.workout_exercises for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid())
              and exists (select 1 from public.workouts w where w.id = workout_id and w.user_id = (select auth.uid())));
create policy workout_exercises_delete on public.workout_exercises for delete to authenticated
  using (user_id = (select auth.uid()));

drop policy if exists workout_sets_select on public.workout_sets;
drop policy if exists workout_sets_insert on public.workout_sets;
drop policy if exists workout_sets_update on public.workout_sets;
drop policy if exists workout_sets_delete on public.workout_sets;
create policy workout_sets_select on public.workout_sets for select to authenticated
  using (user_id = (select auth.uid()));
create policy workout_sets_insert on public.workout_sets for insert to authenticated
  with check (user_id = (select auth.uid())
              and exists (select 1 from public.workout_exercises we where we.id = workout_exercise_id and we.user_id = (select auth.uid())));
create policy workout_sets_update on public.workout_sets for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid())
              and exists (select 1 from public.workout_exercises we where we.id = workout_exercise_id and we.user_id = (select auth.uid())));
create policy workout_sets_delete on public.workout_sets for delete to authenticated
  using (user_id = (select auth.uid()));

-- ---------- ejercicios: catálogo visible para todos, personalizados solo para su dueño ----------
drop policy if exists exercises_select on public.exercises;
drop policy if exists exercises_insert on public.exercises;
drop policy if exists exercises_update on public.exercises;
drop policy if exists exercises_delete on public.exercises;
create policy exercises_select on public.exercises for select to authenticated
  using (owner_id is null or owner_id = (select auth.uid()));
create policy exercises_insert on public.exercises for insert to authenticated
  with check (owner_id = (select auth.uid()));
create policy exercises_update on public.exercises for update to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy exercises_delete on public.exercises for delete to authenticated
  using (owner_id = (select auth.uid()));

-- =====================================================================
-- RPC (POST /rest/v1/rpc/<nombre>). SECURITY INVOKER → RLS aplica.
-- La app manda el rango de fechas ya calculado en SU zona horaria,
-- así el servidor nunca usa now() ni su propia zona para agrupar.
-- =====================================================================
-- Cuerpo estándar SQL (BEGIN ATOMIC): se analiza al crearla y guarda auth.uid() por OID.
-- Así el rol `authenticated` no necesita USAGE en el esquema "auth" (en Neon es de cloud_admin y no se puede otorgar).
create or replace function public.rupio_volume_series(
  p_bucket text, p_from date, p_to date, p_week_start int default 1)
returns table (bucket date, volume_kg double precision, sets bigint, reps bigint, workouts bigint)
language sql stable security invoker parallel safe
begin atomic
  select case p_bucket
           when 'week' then (date_trunc('week', w.local_date + (p_week_start = 7)::int) - ((p_week_start = 7)::int * interval '1 day'))::date
           when 'month' then date_trunc('month', w.local_date)::date
           when 'quarter' then date_trunc('quarter', w.local_date)::date
           else date_trunc('year', w.local_date)::date
         end as bucket,
         sum(w.total_volume_kg), sum(w.total_sets)::bigint, sum(w.total_reps)::bigint, count(*)
  from public.workouts w
  where w.user_id = (select auth.uid()) and not w.deleted and w.local_date between p_from and p_to
  group by 1
  order by 1;
end;

create or replace function public.rupio_muscle_sets(p_from date, p_to date)
returns table (muscle text, sets bigint, volume_kg double precision)
language sql stable security invoker parallel safe
begin atomic
  select e.primary_muscle, count(*), sum(s.volume_kg)
  from public.workout_sets s
  join public.exercises e on e.id = s.exercise_id
  where s.user_id = (select auth.uid()) and s.completed and not s.deleted and s.set_type <> 1
    and s.performed_on between p_from and p_to
  group by 1
  order by 2 desc;
end;

revoke all on function public.rupio_volume_series(text, date, date, int) from public;
revoke all on function public.rupio_muscle_sets(date, date) from public;
grant execute on function public.rupio_volume_series(text, date, date, int) to authenticated;
grant execute on function public.rupio_muscle_sets(date, date) to authenticated;
