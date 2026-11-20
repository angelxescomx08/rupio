-- =====================================================================
-- Rupio · 011_paquete_play.sql  (v2.1)
-- APLICADA en production el 20 nov 2026, 12:08 CDMX, con visto bueno del usuario (proyecto Rupio, rama production,
-- ep-wild-art-bdlip6s4). Verificado: package_name y su default = mx.rupio.app; authenticated no lee wall_config ni
-- ejecuta funciones internas; la API (rupio_wall*, rupio_league*) sigue abierta.
--
-- Por qué: en Google Play el nombre de paquete com.rupio.app quedó apartado por una cuenta que Google cerró y no se
-- libera. La app se publica como mx.rupio.app (applicationId; el namespace y los paquetes de Kotlin siguen siendo
-- com.rupio.app). rupio_wall_register (007) rechaza la compra si su "packageName" no es wall_config.package_name,
-- así que con el valor viejo ninguna compra del muro de la app nueva entraría (rupio:bad_purchase).
--
-- Solo se acepta mx.rupio.app: com.rupio.app ya no puede vender (su ficha quedó en una cuenta cerrada). El paquete solo se
-- revisa al registrar una compra nueva; los pagos ya guardados en wall_payments y los lugares del muro no se tocan.
-- Se puede correr más de una vez.
-- =====================================================================

alter table public.wall_config alter column package_name set default 'mx.rupio.app';
insert into public.wall_config (id, package_name) values (true, 'mx.rupio.app') on conflict (id) do nothing;
update public.wall_config set package_name = 'mx.rupio.app', updated_at = now()
where id and package_name is distinct from 'mx.rupio.app';

-- ---------- permisos ----------
-- Neon da por "default ACL" permisos directos a `authenticated` sobre lo nuevo en public (ver 009). Esta migración no
-- crea objetos, pero se repite el cierre de todo lo interno para que quede explícito: la app solo usa las funciones de
-- la API (rupio_wall, rupio_wall_register, rupio_wall_set_name, rupio_wall_set_hidden, rupio_league*, …), que no se
-- tocan aquí.
revoke all on
  public.wall_config, public.wall_products, public.wall_entries, public.wall_payments, public.wall_counts,
  public.league_members, public.league_groups, public.league_scores, public.league_weeks
from authenticated, anonymous;

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

notify pgrst, 'reload schema';

-- Verificar después (debe dar: mx.rupio.app | f | f | t):
--   select package_name,
--          has_table_privilege('authenticated', 'public.wall_config', 'select') as lee_config,
--          has_function_privilege('authenticated', 'public.rupio_play_signature_ok(text, text, numeric)', 'execute') as firma,
--          has_function_privilege('authenticated', 'public.rupio_wall_register(text, text, text)', 'execute') as registra
--   from public.wall_config;
