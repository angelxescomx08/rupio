-- Pruebas funcionales de la 008 (corre en una copia local con datos: setup_local.sql, 001, 007, datos_base.sql,
-- ../migrations/008_ligas.sql, datos_ligas.sql). Cada prueba imprime ok / FALLA. Permisos: prueba_permisos.sql.
\set ON_ERROR_STOP 1
begin;

-- 1) El lugar del muro con rank_key es idéntico al de row_number() de la 007 (orden: puntos desc, first_at asc)
with old as (select user_id, row_number() over (order by points desc, first_at) rn from wall_entries where not hidden and not blocked),
     new as (select user_id, row_number() over (order by rank_key desc) rn from wall_entries where not hidden and not blocked)
select case when count(*) filter (where o.rn <> n.rn) = 0 then 'ok' else 'FALLA' end || ' · 1 muro: mismo orden (diferencias: '
       || count(*) filter (where o.rn <> n.rn) || ' de ' || count(*) || ')' from old o join new n using (user_id);

-- 2) rupio_wall_me_json: rank y to_next coinciden con el cálculo de la 007 en 300 personas al azar
with old as (select user_id, points, row_number() over (order by points desc, first_at) rn from wall_entries where not hidden and not blocked),
     pick as (select user_id from wall_entries where not hidden and not blocked order by md5(user_id::text) limit 300),
     chk as (select o.rn, (rupio_wall_me_json(p.user_id)->>'rank')::bigint r,
                    (select a.points - o.points + 1 from old a where a.rn = o.rn - 1) tn_old,
                    (rupio_wall_me_json(p.user_id)->>'to_next')::int tn_new
             from pick p join old o using (user_id))
select case when count(*) filter (where rn <> r or tn_old is distinct from tn_new) = 0 then 'ok' else 'FALLA' end
       || ' · 2 muro: lugar y "te faltan" iguales en ' || count(*) || ' personas' from chk;

-- 3) Quien no participa: su entreno no crea nada en ligas
select set_config('t.non', (select p.user_id::text from profiles p where not exists (select 1 from league_members m where m.user_id = p.user_id) limit 1), true);
insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
values (gen_random_uuid(), current_setting('t.non')::uuid, now(), now() + interval '1 hour', 9000, current_date, 'x', 'America/Mexico_City');
select case when not exists (select 1 from league_scores where user_id = current_setting('t.non')::uuid) then 'ok' else 'FALLA' end || ' · 3 no participa: sin puntaje';

-- 4) Participante nuevo (sin historial): primer entreno crea grupo/puntaje con el volumen exacto
insert into league_members (user_id, consent_at, nickname) values (current_setting('t.non')::uuid, now(), 'Nuevo');
select set_config('t.w1', gen_random_uuid()::text, true);
insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
values (current_setting('t.w1')::uuid, current_setting('t.non')::uuid, now(), now() + interval '1 hour', 9000, current_date, 'x', 'America/Mexico_City');
select case when (select volume_kg = 9000 and workouts = 1 from league_scores where user_id = current_setting('t.non')::uuid and week = rupio_league_week(current_date))
       then 'ok' else 'FALLA' end || ' · 4 primer entreno: 9000 kg, 1 entreno';

-- 5) Editar el volumen suma la diferencia; borrar (deleted) lo resta; tope de 50,000 por entreno
update workouts set total_volume_kg = 12000 where id = current_setting('t.w1')::uuid;
select case when (select volume_kg from league_scores where user_id = current_setting('t.non')::uuid and week = rupio_league_week(current_date)) = 12000
       then 'ok' else 'FALLA' end || ' · 5a editar: 12000';
update workouts set total_volume_kg = 900000 where id = current_setting('t.w1')::uuid;
select case when (select volume_kg from league_scores where user_id = current_setting('t.non')::uuid and week = rupio_league_week(current_date)) = 50000
       then 'ok' else 'FALLA' end || ' · 5b tope: 50000';
update workouts set deleted = true where id = current_setting('t.w1')::uuid;
select case when (select volume_kg = 0 and workouts = 0 from league_scores where user_id = current_setting('t.non')::uuid and week = rupio_league_week(current_date))
       then 'ok' else 'FALLA' end || ' · 5c borrar: 0';

-- 6) Entreno sin terminar (ended_at null) no cuenta; al terminarlo sí
select set_config('t.w2', gen_random_uuid()::text, true);
insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
values (current_setting('t.w2')::uuid, current_setting('t.non')::uuid, now(), null, 5000, current_date, 'x', 'America/Mexico_City');
select case when (select volume_kg from league_scores where user_id = current_setting('t.non')::uuid and week = rupio_league_week(current_date)) = 0
       then 'ok' else 'FALLA' end || ' · 6a abierto no cuenta';
update workouts set ended_at = now() + interval '1 hour' where id = current_setting('t.w2')::uuid;
select case when (select volume_kg from league_scores where user_id = current_setting('t.non')::uuid and week = rupio_league_week(current_date)) = 5000
       then 'ok' else 'FALLA' end || ' · 6b terminado cuenta';

-- 7) Semana cerrada (hace 3 semanas) no cambia nada
insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
values (gen_random_uuid(), current_setting('t.non')::uuid, now() - interval '21 days', now() - interval '21 days' + interval '1 hour', 7000, current_date - 21, 'x', 'America/Mexico_City');
select case when not exists (select 1 from league_scores where user_id = current_setting('t.non')::uuid and week = rupio_league_week(current_date - 21))
       then 'ok' else 'FALLA' end || ' · 7 semana cerrada: sin cambios';

-- 8) Ascenso: quien quedó 1.º de un grupo de 30 la semana pasada sube una liga en su primer entreno de esta semana
select set_config('t.top', (select s.user_id::text from league_scores s join league_groups g on g.id = s.group_id
   where s.week = rupio_league_week(current_date) - 7 and g.members = 30 and g.tier < 6
     and not exists (select 1 from league_scores x where x.user_id = s.user_id and x.week = rupio_league_week(current_date))
     and s.volume_kg = (select max(volume_kg) from league_scores y where y.group_id = s.group_id) limit 1), true);
select set_config('t.tier', (select g.tier::text from league_scores s join league_groups g on g.id = s.group_id
   where s.user_id = current_setting('t.top')::uuid and s.week = rupio_league_week(current_date) - 7), true);
insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
values (gen_random_uuid(), current_setting('t.top')::uuid, now(), now() + interval '1 hour', 8000, current_date, 'x', 'America/Mexico_City');
select case when (select g.tier from league_scores s join league_groups g on g.id = s.group_id
                  where s.user_id = current_setting('t.top')::uuid and s.week = rupio_league_week(current_date)) = current_setting('t.tier')::int + 1
       then 'ok' else 'FALLA' end || ' · 8 ascenso: liga ' || current_setting('t.tier') || ' → ' || (current_setting('t.tier')::int + 1);

-- 9) Ningún grupo pasa de 30 y members coincide con las filas reales
select case when count(*) = 0 then 'ok' else 'FALLA' end || ' · 9 grupos: members = filas y ≤ 30'
from league_groups g where g.week >= rupio_league_week(current_date) - 7
  and g.members <> (select count(*) from league_scores s where s.group_id = g.id);

-- 10) rupio_league() devuelve tu grupo con tu fila marcada; top y lugar global responden
select set_config('test.uid', current_setting('t.top'), true);
select case when jsonb_array_length(rupio_league()->'rows') between 1 and 30
             and (select count(*) from jsonb_array_elements(rupio_league()->'rows') e where (e->>'me')::boolean) = 1
             and jsonb_array_length(rupio_league_top()) = 50
             and (rupio_league_me_global()->>'rank')::int >= 1
       then 'ok' else 'FALLA' end || ' · 10 API: liga, top 50 y lugar global';

rollback;

begin;
-- 11) Un solo INSERT con entrenos de 2 participantes y 1 que no participa: cada quien suma lo suyo
select set_config('t.a', (select s.user_id::text from league_scores s where s.week = rupio_league_week(current_date) order by s.user_id limit 1), true);
select set_config('t.b', (select s.user_id::text from league_scores s where s.week = rupio_league_week(current_date) order by s.user_id offset 1 limit 1), true);
select set_config('t.n', (select p.user_id::text from profiles p where not exists (select 1 from league_members m where m.user_id = p.user_id) limit 1), true);
select set_config('t.va', (select volume_kg::text from league_scores where user_id = current_setting('t.a')::uuid and week = rupio_league_week(current_date)), true);
select set_config('t.vb', (select volume_kg::text from league_scores where user_id = current_setting('t.b')::uuid and week = rupio_league_week(current_date)), true);
insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
select gen_random_uuid(), u::uuid, now(), now() + interval '1 hour', v, current_date, 'x', 'America/Mexico_City'
from (values (current_setting('t.a'), 1000), (current_setting('t.a'), 2000), (current_setting('t.b'), 3000), (current_setting('t.n'), 4000)) t(u, v);
select case when (select volume_kg from league_scores where user_id = current_setting('t.a')::uuid and week = rupio_league_week(current_date)) = current_setting('t.va')::float8 + 3000
             and (select volume_kg from league_scores where user_id = current_setting('t.b')::uuid and week = rupio_league_week(current_date)) = current_setting('t.vb')::float8 + 3000
             and not exists (select 1 from league_scores where user_id = current_setting('t.n')::uuid)
       then 'ok' else 'FALLA' end || ' · 11 lote mixto: +3000 a cada participante, nada al otro';

-- 12) DELETE real de un entreno resta su volumen
delete from workouts where user_id = current_setting('t.b')::uuid and total_volume_kg = 3000;
select case when (select volume_kg from league_scores where user_id = current_setting('t.b')::uuid and week = rupio_league_week(current_date)) = current_setting('t.vb')::float8
       then 'ok' else 'FALLA' end || ' · 12 DELETE: vuelve al volumen anterior';

-- 13) UPDATE de varios entrenos en una sentencia: suma solo la diferencia
update workouts set total_volume_kg = total_volume_kg + 500 where user_id = current_setting('t.a')::uuid and local_date = current_date;
select case when (select volume_kg from league_scores where user_id = current_setting('t.a')::uuid and week = rupio_league_week(current_date)) = current_setting('t.va')::float8 + 4000
       then 'ok' else 'FALLA' end || ' · 13 UPDATE en lote: +1000 (2 × 500)';

-- 14) Ocultarse saca tu fila de la semana y descuenta grupo y contador
select set_config('t.g', (select group_id::text from league_scores where user_id = current_setting('t.a')::uuid and week = rupio_league_week(current_date)), true);
select set_config('t.gm', (select members::text from league_groups where id = current_setting('t.g')::bigint), true);
select set_config('t.wp', (select participants::text from league_weeks where week = rupio_league_week(current_date)), true);
select set_config('test.uid', current_setting('t.a'), true);
select rupio_league_set_hidden(true) is not null;
select case when not exists (select 1 from league_scores where user_id = current_setting('t.a')::uuid and week = rupio_league_week(current_date))
             and (select members from league_groups where id = current_setting('t.g')::bigint) = current_setting('t.gm')::int - 1
             and (select participants from league_weeks where week = rupio_league_week(current_date)) = current_setting('t.wp')::int - 1
       then 'ok' else 'FALLA' end || ' · 14 ocultarse: fuera de la semana, grupo y contador -1';

-- 15) El contador del muro sigue al real después de ocultar a alguien
update wall_entries set hidden = true where user_id = (select user_id from wall_entries where not hidden limit 1);
select case when (select visible from wall_counts) = (select count(*) from wall_entries where not hidden and not blocked)
       then 'ok' else 'FALLA' end || ' · 15 contador del muro = real';

-- 16) Paginación keyset del top: la página 2 empieza justo después de la 1, sin repetir
select set_config('t.last', (select (e->>'k') || '|' || (e->>'at') from jsonb_array_elements(rupio_league_top(null, null, 50)) with ordinality x(e, i) where i = 50), true);
select case when (select count(*) from (
          select e->>'name' n, e->>'at' a from jsonb_array_elements(rupio_league_top(null, null, 100)) e
          except all
          select * from (select e->>'name', e->>'at' from jsonb_array_elements(rupio_league_top(null, null, 50)) e
                         union all
                         select e->>'name', e->>'at' from jsonb_array_elements(rupio_league_top(split_part(current_setting('t.last'), '|', 1)::float8,
                                                                                            split_part(current_setting('t.last'), '|', 2)::timestamptz, 50)) e) z) d) = 0
       then 'ok' else 'FALLA' end || ' · 16 top: página 1 + página 2 = primeros 100';
rollback;

begin;
-- 17) UPSERT como el de la Data API (INSERT ... ON CONFLICT DO UPDATE): inserta y actualiza en la misma sentencia
select set_config('t.a', (select s.user_id::text from league_scores s where s.week = rupio_league_week(current_date) order by s.user_id limit 1), true);
select set_config('t.v', (select volume_kg::text from league_scores where user_id = current_setting('t.a')::uuid and week = rupio_league_week(current_date)), true);
select set_config('t.w', gen_random_uuid()::text, true);
insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
values (current_setting('t.w')::uuid, current_setting('t.a')::uuid, now(), now() + interval '1 hour', 1000, current_date, 'x', 'America/Mexico_City')
on conflict (id) do update set total_volume_kg = excluded.total_volume_kg;
insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
values (current_setting('t.w')::uuid, current_setting('t.a')::uuid, now(), now() + interval '1 hour', 1500, current_date, 'x', 'America/Mexico_City'),
       (gen_random_uuid(), current_setting('t.a')::uuid, now(), now() + interval '1 hour', 700, current_date, 'x', 'America/Mexico_City')
on conflict (id) do update set total_volume_kg = excluded.total_volume_kg;
select case when (select volume_kg from league_scores where user_id = current_setting('t.a')::uuid and week = rupio_league_week(current_date)) = current_setting('t.v')::float8 + 2200
       then 'ok' else 'FALLA' end || ' · 17 upsert: +1000, 1000→1500 y +700 = +2200';
rollback;
