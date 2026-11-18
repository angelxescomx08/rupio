-- Solo para el benchmark: la versión por FILA del trigger de ligas (la 008 usa la versión por sentencia).
create or replace function public.rupio_league_on_workout()
returns trigger language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := coalesce(new.user_id, old.user_id);
  ow date; nw date; ov double precision := 0; nv double precision := 0; oc integer := 0; nc integer := 0;
begin
  if not exists (select 1 from league_members m where m.user_id = uid and not m.hidden and not m.blocked) then
    return null;
  end if;
  if tg_op <> 'INSERT' and not old.deleted and old.ended_at is not null then
    ow := rupio_league_week(old.local_date); ov := rupio_league_cap(old.total_volume_kg); oc := 1;
  end if;
  if tg_op <> 'DELETE' and not new.deleted and new.ended_at is not null then
    nw := rupio_league_week(new.local_date); nv := rupio_league_cap(new.total_volume_kg); nc := 1;
  end if;
  if ow is not distinct from nw then
    if nw is not null and (nv <> ov or nc <> oc) then perform rupio_league_add(uid, nw, nv - ov, nc - oc); end if;
  else
    if ow is not null then perform rupio_league_add(uid, ow, -ov, -oc); end if;
    if nw is not null then perform rupio_league_add(uid, nw, nv, nc); end if;
  end if;
  return null;
end $$;
