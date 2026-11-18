-- =====================================================================
-- Rupio · 009_permisos_internos.sql  (APLICADA en production el 18 nov 2026, 10:18 CDMX, con visto bueno del usuario)
--
-- Verificado después de aplicarla: `authenticated` solo tiene EXECUTE en la API de la app (rupio_wall,
-- rupio_wall_register, rupio_wall_set_name, rupio_wall_set_hidden, rupio_delete_my_account, rupio_muscle_sets,
-- rupio_volume_series, rupio_league, rupio_league_top, rupio_league_me_global, rupio_league_join,
-- rupio_league_set_hidden) y en rupio_touch_updated_at (función de trigger, inofensiva); wall_counts = false.
-- Prueba de humo (db/ligas/prueba_humo_prod.sql): kg=4321 entrenos=1 en_grupo=1 participantes=1, todo deshecho
-- (league_members/scores/groups/weeks en 0, workouts sigue en 1725).
--
-- Por qué: Neon tiene un "default ACL" en public que da EXECUTE a `authenticated` en TODA función nueva
-- (y SELECT/INSERT/UPDATE/DELETE en toda tabla nueva). Los `revoke ... from public` de la 007 y la 008 no
-- quitan ese permiso directo. Revisado en production el 18 nov 2026 tras aplicar la 008:
--   * rupio_league_add / rupio_league_next_tier: cualquier sesión podría sumar volumen o cambiar la liga de
--     otra persona por /rpc. Hoy falla porque nadie participa todavía, pero hay que cerrarlo antes de lanzar Ligas.
--   * rupio_wall_me_json(uuid): con el id de alguien devuelve sus puntos y si está oculto (la 007 promete
--     "sin montos de otras personas").
--   * rupio_wall_admin_revoke(text): la 007 dice "solo para el responsable".
--   * wall_counts: RLS sin políticas ya devuelve 0 filas, pero el permiso sobra.
-- Ninguna de estas la llama la app: las usan funciones SECURITY DEFINER (corren como el dueño) y triggers
-- (no piden EXECUTE al que escribe). Probado en local con el default ACL de Neon: prueba_muro 24/24 y
-- prueba_ligas sin fallas después de esta migración.
-- =====================================================================

revoke execute on function
  public.rupio_league_add(uuid, date, double precision, integer),
  public.rupio_league_next_tier(uuid, date),
  public.rupio_league_week(date),
  public.rupio_league_cap(double precision),
  public.rupio_league_on_workouts(),
  public.rupio_wall_count(),
  public.rupio_wall_me_json(uuid),
  public.rupio_wall_admin_revoke(text),
  public.rupio_play_signature_ok(text, text, numeric),
  public.rupio_bytes_to_numeric(bytea),
  public.rupio_numeric_to_hex(numeric, integer),
  public.rupio_wall_clean_name(text)
from authenticated, anonymous;

revoke all on public.wall_counts from authenticated, anonymous;

notify pgrst, 'reload schema';
