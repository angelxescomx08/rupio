-- =====================================================================
-- Rupio · 008_ligas.sql  (APLICADA en production el 18 nov 2026, 09:55 CDMX, con visto bueno del usuario)
-- Ligas semanales por volumen + muro más rápido.
-- Probada y medida en PostgreSQL 16 local con datos de prueba (db/ligas/bench_ligas.py, db/ligas/prueba_ligas.sql).
-- OJO: Neon da EXECUTE a `authenticated` en toda función nueva de public (default ACL). Las funciones internas
-- de aquí quedaron abiertas por /rpc; se cierran en 009_permisos_internos.sql (pendiente de visto bueno).
--
-- Cómo funciona:
--  * Participar es opcional (casilla de permiso, como el muro). Solo quien participa tiene filas aquí.
--  * Cada semana (lunes a domingo, fecha local del entreno) compites en un grupo de hasta 30 personas
--    de tu misma liga (1 a 6). Puntaje = volumen de la semana (kg × reps de series efectivas), con tope
--    de 50,000 kg por entreno para cortar capturas imposibles.
--  * Nada corre por horario: el grupo se asigna al subir tu primer entreno de la semana, y en ese momento
--    se calcula si subes o bajas de liga con tu lugar de la semana anterior (top 5 sube, últimos 5 bajan).
--  * Pagar NO da puntos. La insignia de apoyo (badge) solo se muestra junto al nombre.
--  * El puntaje semanal lo suma el servidor (triggers en workouts) con el volumen de cada entreno; la app no lo manda.
--    Los entrenos sí vienen del teléfono: el tope por entreno, los grupos de 30 y la moderación limitan las trampas.
--
-- Rendimiento (ver results_ligas.md):
--  * Tabla de tu grupo = 30 filas leídas en orden del índice league_scores_group_idx (sin Sort).
--  * "Tu lugar global" = contar los que van arriba de ti en league_scores_week_idx (index-only);
--    "de cuántos" sale del contador league_weeks.
--  * Triggers por sentencia: un lote de sync se resuelve con un join y una suma por (persona, semana).
--  * Muro: rank_key (una sola columna de orden) permite lugar, vecinos y "te faltan" con rangos de índice
--    en vez de numerar a todo el muro con row_number() en cada llamada.
-- =====================================================================

-- ---------- utilidades ----------
create or replace function public.rupio_league_week(d date)
returns date language sql immutable parallel safe as
$$ select d - (extract(isodow from d)::int - 1) $$;            -- lunes ISO de la semana de d

create or replace function public.rupio_league_cap(v double precision)
returns double precision language sql immutable parallel safe as
$$ select least(greatest(coalesce(v, 0), 0), 50000) $$;          -- tope por entreno

-- ---------- quién compite ----------
create table if not exists public.league_members (
  user_id    uuid        primary key references public.profiles (user_id) on delete cascade,
  joined_at  timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  consent_at timestamptz not null,                      -- aceptó que su apodo y su volumen semanal se vean
  tier       smallint    not null default 1 check (tier between 1 and 6),
  badge      smallint    not null default 0 check (badge between 0 and 4),  -- 0 nada · 1 Bronce · 2 Plata · 3 Oro · 4 Aurora (verificado)
  hidden     boolean     not null default false,
  blocked    boolean     not null default false,
  nickname   text        not null check (char_length(nickname) between 2 and 30)
);

-- ---------- grupos de la semana (máximo 30) ----------
create table if not exists public.league_groups (
  id      bigint   generated always as identity primary key,
  week    date     not null,
  tier    smallint not null,
  members smallint not null default 0 check (members between 0 and 30)
);
-- Buscar un grupo con lugar en (semana, liga): solo los abiertos → índice parcial diminuto.
create index if not exists league_groups_open_idx on public.league_groups (week, tier, id) where members < 30;

-- ---------- puntaje semanal ----------
create table if not exists public.league_scores (
  user_id    uuid             not null references public.league_members (user_id) on delete cascade,
  week       date             not null,
  group_id   bigint           not null references public.league_groups (id) on delete cascade,
  first_at   timestamptz      not null default now(),   -- desempate: quien empezó la semana antes
  updated_at timestamptz      not null default now(),
  volume_kg  double precision not null default 0 check (volume_kg >= 0),
  workouts   smallint         not null default 0 check (workouts >= 0),
  primary key (user_id, week)                           -- también sirve a "mi fila de esta semana"
);
-- Tu grupo en orden, sin Sort; INCLUDE para no ir a la tabla.
create index if not exists league_scores_group_idx
  on public.league_scores (group_id, volume_kg desc, first_at) include (user_id, workouts);
-- Top global de la semana (keyset) y "tu lugar global" (contar los de arriba): index-only.
create index if not exists league_scores_week_idx
  on public.league_scores (week, volume_kg desc, first_at) include (user_id);
-- La FK hija necesita índice para borrar grupos sin seq scan: group_id ya es prefijo de league_scores_group_idx.

-- ---------- participantes por semana (contador: "de cuántos" sin contar 60 mil filas en cada llamada) ----------
create table if not exists public.league_weeks (
  week         date    primary key,
  participants integer not null default 0 check (participants >= 0)
);

alter table public.league_members enable row level security;
alter table public.league_groups  enable row level security;
alter table public.league_scores  enable row level security;
alter table public.league_weeks   enable row level security;
revoke all on public.league_members, public.league_groups, public.league_scores, public.league_weeks from public;
do $$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    execute 'revoke all on public.league_members, public.league_groups, public.league_scores, public.league_weeks from authenticated';
  end if;
  if exists (select 1 from pg_roles where rolname = 'anonymous') then
    execute 'revoke all on public.league_members, public.league_groups, public.league_scores, public.league_weeks from anonymous';
  end if;
end $$;

-- ---------- liga de la semana que empieza (subir / bajar con la semana anterior) ----------
create or replace function public.rupio_league_next_tier(uid uuid, wk date)
returns smallint language plpgsql volatile
set search_path = public, pg_temp
as $$
declare cur smallint; p record; r integer;
begin
  select tier into cur from league_members where user_id = uid for update;
  select s.group_id, s.volume_kg, s.first_at, g.tier, g.members into p
  from league_scores s join league_groups g on g.id = s.group_id
  where s.user_id = uid and s.week = wk - 7;
  if not found then return cur; end if;                 -- no entrenó la semana pasada: se queda en su liga
  select 1 + count(*) into r from league_scores s
  where s.group_id = p.group_id and (s.volume_kg > p.volume_kg or (s.volume_kg = p.volume_kg and s.first_at < p.first_at));
  cur := p.tier;
  if p.members >= 10 and r <= 5 then cur := least(cur + 1, 6);
  elsif p.members >= 15 and r > p.members - 5 then cur := greatest(cur - 1, 1);
  end if;
  update league_members set tier = cur, updated_at = now() where user_id = uid and tier <> cur;
  return cur;
end $$;

-- ---------- sumar (o restar) volumen a una semana ----------
create or replace function public.rupio_league_add(uid uuid, wk date, dv double precision, dn integer)
returns void language plpgsql volatile
set search_path = public, pg_temp
as $$
declare g bigint; t smallint;
begin
  -- Solo la semana actual y la anterior (sincronización tardía). Semanas cerradas y fechas futuras no cuentan.
  if wk < rupio_league_week(current_date) - 7 or wk > current_date + 1 then return; end if;
  update league_scores set volume_kg = greatest(volume_kg + dv, 0), workouts = greatest(workouts + dn, 0), updated_at = now()
  where user_id = uid and week = wk;
  if found or dv <= 0 then return; end if;
  -- Primer entreno de la semana: liga nueva y un grupo con lugar (sin bloquear a otros: SKIP LOCKED).
  t := rupio_league_next_tier(uid, wk);
  select id into g from league_groups
  where week = wk and tier = t and members < 30 order by id limit 1 for update skip locked;
  if g is null then insert into league_groups (week, tier) values (wk, t) returning id into g; end if;
  update league_groups set members = members + 1 where id = g;
  insert into league_scores (user_id, week, group_id, volume_kg, workouts) values (uid, wk, g, dv, greatest(dn, 0));
  insert into league_weeks (week, participants) values (wk, 1)
  on conflict (week) do update set participants = league_weeks.participants + 1;
end $$;

-- ---------- triggers: el servidor lleva el puntaje ----------
-- Por SENTENCIA, con tablas de transición: un lote de sync (400 entrenos en un solo INSERT/UPSERT de la Data API)
-- se resuelve con UN join contra league_members y una suma por (persona, semana), no con 400 llamadas.
-- Medido: con 400 entrenos de la semana actual, por fila tardaba ~40 ms; por sentencia, ver results_ligas.md.
create or replace function public.rupio_league_on_workouts()
returns trigger language plpgsql security definer
set search_path = public, pg_temp
as $$
declare oldest date := rupio_league_week(current_date) - 7;
begin
  -- plpgsql prepara cada consulta la primera vez que la ejecuta: las ramas que nombran una tabla de
  -- transición que no existe en este evento nunca se preparan.
  if tg_op = 'INSERT' then
    perform rupio_league_add(d.user_id, d.week, d.dv, d.dn)
    from (select n.user_id, rupio_league_week(n.local_date) as week,
                 sum(rupio_league_cap(n.total_volume_kg)) as dv, count(*)::integer as dn
          from new_rows n join league_members m on m.user_id = n.user_id and not m.hidden and not m.blocked
          where not n.deleted and n.ended_at is not null and n.local_date >= oldest
          group by 1, 2) d;
  elsif tg_op = 'DELETE' then
    perform rupio_league_add(d.user_id, d.week, d.dv, d.dn)
    from (select o.user_id, rupio_league_week(o.local_date) as week,
                 -sum(rupio_league_cap(o.total_volume_kg)) as dv, -count(*)::integer as dn
          from old_rows o join league_members m on m.user_id = o.user_id and not m.hidden and not m.blocked
          where not o.deleted and o.ended_at is not null and o.local_date >= oldest
          group by 1, 2) d;
  else
    perform rupio_league_add(d.user_id, d.week, d.dv, d.dn)
    from (select x.user_id, x.week, sum(x.v) as dv, sum(x.c)::integer as dn
          from (select n.user_id, rupio_league_week(n.local_date) as week, rupio_league_cap(n.total_volume_kg) as v, 1 as c
                from new_rows n where not n.deleted and n.ended_at is not null and n.local_date >= oldest
                union all
                select o.user_id, rupio_league_week(o.local_date), -rupio_league_cap(o.total_volume_kg), -1
                from old_rows o where not o.deleted and o.ended_at is not null and o.local_date >= oldest) x
          join league_members m on m.user_id = x.user_id and not m.hidden and not m.blocked
          group by 1, 2
          having sum(x.v) <> 0 or sum(x.c) <> 0) d;
  end if;
  return null;
end $$;

-- Un trigger por evento (las tablas de transición no admiten varios eventos ni lista de columnas).
drop trigger if exists workouts_league on public.workouts;
drop trigger if exists workouts_league_ins on public.workouts;
drop trigger if exists workouts_league_upd on public.workouts;
drop trigger if exists workouts_league_del on public.workouts;
create trigger workouts_league_ins after insert on public.workouts
  referencing new table as new_rows for each statement execute function public.rupio_league_on_workouts();
create trigger workouts_league_upd after update on public.workouts
  referencing old table as old_rows new table as new_rows for each statement execute function public.rupio_league_on_workouts();
create trigger workouts_league_del after delete on public.workouts
  referencing old table as old_rows for each statement execute function public.rupio_league_on_workouts();

-- =====================================================================
-- API para la app (POST /rpc/...): SECURITY DEFINER sobre auth.uid().
-- =====================================================================

-- Tu liga de esta semana: tu grupo completo (≤ 30), zonas de subir/bajar y lo que te falta.
create or replace function public.rupio_league()
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid(); wk date := rupio_league_week(current_date);
  m league_members; s league_scores; g league_groups;
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  select * into m from league_members where user_id = uid;
  if not found then return jsonb_build_object('member', false, 'week', wk); end if;
  select * into s from league_scores where user_id = uid and week = wk;
  if not found then return jsonb_build_object('member', true, 'week', wk, 'tier', m.tier, 'hidden', m.hidden); end if;
  select * into g from league_groups where id = s.group_id;
  return jsonb_build_object(
    'member', true, 'week', wk, 'tier', g.tier, 'size', g.members,
    'up', case when g.members >= 10 then 5 else 0 end,
    'down', case when g.members >= 15 then 5 else 0 end,
    'rows', (select jsonb_agg(jsonb_build_object(
                 'rank', x.rn, 'name', x.nickname, 'badge', x.badge, 'at', x.first_at,   -- name + at = key estable en Compose
                 'kg', round(x.volume_kg::numeric), 'workouts', x.workouts, 'me', x.user_id = uid) order by x.rn)
             from (select sc.user_id, sc.volume_kg, sc.workouts, sc.first_at, lm.nickname, lm.badge,
                          row_number() over (order by sc.volume_kg desc, sc.first_at) as rn
                   from league_scores sc join league_members lm on lm.user_id = sc.user_id
                   where sc.group_id = s.group_id and not lm.blocked) x));
end $$;

-- Top global de la semana, en páginas (keyset: después del volumen/fecha que mandes, nunca OFFSET).
-- Dos ramas (empates del mismo volumen + volúmenes menores) para que cada una sea un rango del índice.
create or replace function public.rupio_league_top(after_kg double precision default null, after_at timestamptz default null, page_size integer default 50)
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare wk date := rupio_league_week(current_date); n integer := least(greatest(coalesce(page_size, 50), 1), 100); r jsonb;
begin
  if after_kg is null then
    select jsonb_agg(jsonb_build_object('name', lm.nickname, 'badge', lm.badge, 'tier', lm.tier,
                                        'kg', round(t.volume_kg::numeric), 'k', t.volume_kg, 'at', t.first_at) order by t.volume_kg desc, t.first_at)
    into r
    from (select s.user_id, s.volume_kg, s.first_at from league_scores s
          where s.week = wk order by s.volume_kg desc, s.first_at limit n) t
    join league_members lm on lm.user_id = t.user_id and not lm.blocked;
  else
    select jsonb_agg(jsonb_build_object('name', lm.nickname, 'badge', lm.badge, 'tier', lm.tier,
                                        'kg', round(t.volume_kg::numeric), 'k', t.volume_kg, 'at', t.first_at) order by t.volume_kg desc, t.first_at)
    into r
    from (select * from (
            (select s.user_id, s.volume_kg, s.first_at from league_scores s
             where s.week = wk and s.volume_kg = after_kg and s.first_at > after_at order by s.first_at limit n)
            union all
            (select s.user_id, s.volume_kg, s.first_at from league_scores s
             where s.week = wk and s.volume_kg < after_kg order by s.volume_kg desc, s.first_at limit n)) u
          order by u.volume_kg desc, u.first_at limit n) t
    join league_members lm on lm.user_id = t.user_id and not lm.blocked;
  end if;
  return coalesce(r, '[]'::jsonb);
end $$;

-- Tu lugar global de la semana y en qué porcentaje vas (contar a los de arriba: rango del índice).
create or replace function public.rupio_league_me_global()
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare uid uuid := auth.uid(); wk date := rupio_league_week(current_date); s league_scores; above bigint; total bigint;
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  select * into s from league_scores where user_id = uid and week = wk;
  if not found then return null; end if;
  select count(*) into above from league_scores a where a.week = wk and a.volume_kg > s.volume_kg;
  above := above + (select count(*) from league_scores a where a.week = wk and a.volume_kg = s.volume_kg and a.first_at < s.first_at);
  select participants into total from league_weeks where week = wk;          -- contador, no count(*)
  return jsonb_build_object('rank', above + 1, 'total', total, 'top_pct', ceil(100.0 * (above + 1) / greatest(total, 1)));
end $$;

-- Entrar, cambiar apodo, ocultarse (mismas reglas de nombre que el muro).
create or replace function public.rupio_league_join(nickname text)
returns jsonb language plpgsql volatile security definer
set search_path = public, pg_temp
as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  insert into league_members (user_id, consent_at, nickname) values (uid, now(), rupio_wall_clean_name(nickname))
  on conflict (user_id) do update set nickname = excluded.nickname, consent_at = now(), hidden = false, updated_at = now();
  return rupio_league();
end $$;

create or replace function public.rupio_league_set_hidden(hidden boolean)
returns jsonb language plpgsql volatile security definer
set search_path = public, pg_temp
as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  update league_members set hidden = coalesce(rupio_league_set_hidden.hidden, true), updated_at = now() where user_id = uid;
  if not found then raise exception 'rupio:no_entry'; end if;
  if coalesce(rupio_league_set_hidden.hidden, true) then          -- ocultarse = salir de la semana en curso
    with gone as (delete from league_scores where user_id = uid and week = rupio_league_week(current_date) returning group_id, week)
    , g as (update league_groups set members = members - 1 where id in (select group_id from gone))
    update league_weeks set participants = participants - 1 where week in (select week from gone);
  end if;
  return rupio_league();
end $$;

-- =====================================================================
-- Muro: una sola columna de orden (rank_key) → lugar, vecinos y "te faltan" con rangos de índice.
-- rank_key = puntos × 10^13 − milisegundos desde 2020 de first_at  (más puntos primero; empate → quien llegó antes).
-- Milisegundos: con segundos, 12 de 100,000 lugares de prueba empataban y cambiaban de orden. Aguanta hasta
-- 922,337 puntos por persona (≈ $922 mil MXN) y fechas hasta 2300 antes de desbordar bigint.
-- =====================================================================
alter table public.wall_entries add column if not exists rank_key bigint generated always as
  (points::bigint * 10000000000000 - floor(extract(epoch from (first_at - timestamptz '2020-01-01 00:00:00+00')) * 1000)::bigint) stored;

drop index if exists public.wall_entries_rank_idx;

-- Total de lugares visibles en un contador (antes: count(*) de todo el muro en cada llamada).
create table if not exists public.wall_counts (
  id      boolean primary key default true check (id),
  visible integer not null default 0 check (visible >= 0)
);
alter table public.wall_counts enable row level security;
revoke all on public.wall_counts from public;
insert into public.wall_counts (id, visible) values (true, (select count(*) from public.wall_entries where not hidden and not blocked))
on conflict (id) do update set visible = excluded.visible;

create or replace function public.rupio_wall_count()
returns trigger language plpgsql security definer
set search_path = public, pg_temp
as $$
declare d integer := 0;
begin
  if tg_op <> 'INSERT' and not old.hidden and not old.blocked then d := d - 1; end if;
  if tg_op <> 'DELETE' and not new.hidden and not new.blocked then d := d + 1; end if;
  if d <> 0 then update wall_counts set visible = visible + d where id; end if;
  return null;
end $$;
drop trigger if exists wall_entries_count on public.wall_entries;
create trigger wall_entries_count after insert or delete or update of hidden, blocked on public.wall_entries
  for each row execute function public.rupio_wall_count();
create index if not exists wall_entries_rank_idx
  on public.wall_entries (rank_key desc) include (display_name, user_id, first_at) where not hidden and not blocked;

create or replace function public.rupio_wall_me_json(uid uuid)
returns jsonb language sql stable
set search_path = public, pg_temp
as $$
  select case when me.user_id is null then null else jsonb_build_object(
    'name', me.display_name, 'points', me.points, 'since', me.first_at, 'hidden', me.hidden, 'blocked', me.blocked,
    'rank', case when me.hidden or me.blocked then null else
              1 + (select count(*) from wall_entries a where not a.hidden and not a.blocked and a.rank_key > me.rank_key) end,
    -- el de arriba: el primero con rank_key mayor (rango del índice, una fila)
    'to_next', case when me.hidden or me.blocked then null else
              (select a.points - me.points + 1 from wall_entries a
               where not a.hidden and not a.blocked and a.rank_key > me.rank_key order by a.rank_key limit 1) end,
    'total', (select visible from wall_counts where id)
  ) end
  from (select 1) one left join wall_entries me on me.user_id = uid
$$;

create or replace function public.rupio_wall()
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  return jsonb_build_object(
    'entries', coalesce((
      -- Sin ids ni puntos de otros (regla de la 007, la revisa prueba_muro.py). En Compose, key = nombre + fecha de llegada.
      select jsonb_agg(jsonb_build_object('rank', rn, 'name', display_name, 'since', first_at, 'me', user_id = uid) order by rn)
      from (select e.user_id, e.display_name, e.first_at, row_number() over (order by e.rank_key desc) as rn
            from wall_entries e where not e.hidden and not e.blocked
            order by e.rank_key desc limit 300) t), '[]'::jsonb),
    'me', rupio_wall_me_json(uid));
end $$;

revoke all on function public.rupio_league_week(date), public.rupio_league_cap(double precision),
  public.rupio_league_next_tier(uuid, date), public.rupio_league_add(uuid, date, double precision, integer),
  public.rupio_league_on_workouts(), public.rupio_wall_count(), public.rupio_league(), public.rupio_league_top(double precision, timestamptz, integer),
  public.rupio_league_me_global(), public.rupio_league_join(text), public.rupio_league_set_hidden(boolean) from public;
do $$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    execute 'grant execute on function public.rupio_league(), public.rupio_league_top(double precision, timestamptz, integer),
             public.rupio_league_me_global(), public.rupio_league_join(text), public.rupio_league_set_hidden(boolean) to authenticated';
  end if;
end $$;

-- ADD COLUMN ... STORED reescribe wall_entries: sin estadísticas ni mapa de visibilidad, "tu lugar" tardó 7 ms en vez
-- de 0.3 ms (100 mil lugares). Después de correr este archivo, corre APARTE (no va dentro de una transacción):
--     vacuum (analyze) public.wall_entries;
analyze public.wall_entries;
analyze public.league_members;
analyze public.league_groups;
analyze public.league_scores;

notify pgrst, 'reload schema';
