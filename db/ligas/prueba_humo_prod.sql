-- Prueba de humo en production: TODO se deshace (termina con raise exception). Correr como neondb_owner.
-- Solo deja rastro en la secuencia de league_groups.id (las secuencias no se deshacen); es inofensivo.
do $$
declare uid uuid := '5b614c54-a42e-49ca-b7a7-2214abf3c32d'; wk date := rupio_league_week(current_date);
        v double precision; n integer; g integer; p bigint;
begin
  insert into league_members (user_id, consent_at, nickname) values (uid, now(), 'Angel');
  insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
  values (gen_random_uuid(), uid, now() - interval '1 hour', now(), 4321, current_date, 'prueba ligas (se deshace)', 'America/Mexico_City');
  select s.volume_kg, s.workouts, gr.members into v, n, g
  from league_scores s join league_groups gr on gr.id = s.group_id where s.user_id = uid and s.week = wk;
  select participants into p from league_weeks where week = wk;
  raise exception 'PRUEBA_LIGAS (todo se deshace): kg=% entrenos=% en_grupo=% participantes=%', v, n, g, p;
end $$;
