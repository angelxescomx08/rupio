-- =====================================================================
-- Rupio · 004_plans_body_weight.sql  (correr en el SQL Editor de Neon)
--  1) set_plan: peso/reps/tipo planeados por serie en rutinas (y su copia en el entreno).
--  2) body_weights: registro de peso corporal, UNA fila por usuario y día.
-- =====================================================================

alter table public.routine_exercises add column if not exists set_plan jsonb;
alter table public.workout_exercises add column if not exists set_plan jsonb;

-- Columnas ordenadas por alineación (16 · 8 · 4 · 4 · 1 bytes) → filas compactas, sin relleno.
-- Clave primaria natural (user_id, measured_on):
--   * registrar el mismo día dos veces actualiza, no duplica (upsert idempotente desde varios teléfonos);
--   * el rango "mis pesos entre A y B" es un recorrido ordenado de la clave primaria, sin índice extra;
--   * INCLUDE (weight_kg, deleted): la gráfica se responde SOLO con el índice (index-only scan), sin
--     tocar la tabla: ~5 bloques en vez de ~370 para un año (ver db/bench/results_body_weight_neon.md).
create table if not exists public.body_weights (
  user_id     uuid        not null,
  updated_at  timestamptz not null default now(),
  measured_on date        not null,
  weight_kg   real        not null check (weight_kg between 20 and 400),
  deleted     boolean     not null default false,
  primary key (user_id, measured_on) include (weight_kg, deleted)
);
-- Sincronización incremental: "lo mío que cambió desde X".
create index if not exists body_weights_user_sync_idx on public.body_weights (user_id, updated_at);

drop trigger if exists body_weights_touch on public.body_weights;
create trigger body_weights_touch before insert or update on public.body_weights
  for each row execute function public.rupio_touch_updated_at();

grant select, insert, update, delete on public.body_weights to authenticated;
alter table public.body_weights enable row level security;
drop policy if exists body_weights_select on public.body_weights;
drop policy if exists body_weights_insert on public.body_weights;
drop policy if exists body_weights_update on public.body_weights;
drop policy if exists body_weights_delete on public.body_weights;
create policy body_weights_select on public.body_weights for select to authenticated using (user_id = (select auth.uid()));
create policy body_weights_insert on public.body_weights for insert to authenticated with check (user_id = (select auth.uid()));
create policy body_weights_update on public.body_weights for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy body_weights_delete on public.body_weights for delete to authenticated using (user_id = (select auth.uid()));

analyze public.body_weights;
