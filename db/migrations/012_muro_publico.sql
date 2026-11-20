-- =====================================================================
-- Rupio · 012_muro_publico.sql  (v2.1, QA de docs/QA_V21.md, propuesta A)
-- APLICADA en production el 20 nov 2026, 14:48 CDMX, con visto bueno del usuario (proyecto Rupio, rama production,
-- ep-wild-art-bdlip6s4). Verificado: anonymous ejecuta rupio_wall_public y no lee wall_entries; por HTTP, con el JWT de
-- GET /token/anonymous, rupio_wall_public responde 200 y rupio_wall y la tabla wall_entries responden 403.
--
-- El muro de agradecimientos se puede ver SIN cuenta. Antes rupio_wall() pedía sesión, así que quien usaba "Probar sin
-- cuenta" (el camino más común al empezar) veía las tarjetas del muro en Inicio, Progreso y Perfil sin un solo nombre.
-- Ver a otras personas que ya apoyan sube los aportes (Shang y Croson 2009, Economic Journal 119:1422).
--
-- Qué se expone: lo mismo que ya ve cualquier persona con cuenta, y nada más: lugar, nombre público y desde cuándo, de
-- quien no se ocultó ni fue moderado (los primeros 300), y cuántas personas hay. Sin puntos, sin ids, sin "tú". Los
-- Mecenas aceptaron que su nombre "se muestre a todas las personas que usan Rupio".
--
-- Cómo llega la app: Neon Auth da un JWT de rol `anonymous` en GET /token/anonymous y la Data API corre la función con
-- ese rol. `anonymous` no puede leer ninguna tabla (011 lo dejó así): solo puede ejecutar esta función, que es
-- SECURITY DEFINER, estable (solo lee) y con search_path fijo.
-- Se puede correr más de una vez.
-- =====================================================================

create or replace function public.rupio_wall_public()
returns jsonb language sql stable security definer
set search_path = public, pg_temp
as $$
  select jsonb_build_object(
    'entries', coalesce((
      select jsonb_agg(jsonb_build_object('rank', t.rank, 'name', t.display_name, 'since', t.first_at) order by t.rank)
      from (select e.display_name, e.first_at, row_number() over (order by e.points desc, e.first_at) as rank
            from wall_entries e where not e.hidden and not e.blocked
            order by e.points desc, e.first_at limit 300) t), '[]'::jsonb),
    'total', (select count(*) from wall_entries e where not e.hidden and not e.blocked));
$$;

comment on function public.rupio_wall_public() is
  'Muro de agradecimientos sin cuenta (rol anonymous): lugar, nombre y fecha de los primeros 300, y el total. Sin puntos ni ids.';

-- Las funciones nuevas se pueden ejecutar por PUBLIC si no se quita: se deja solo para quien la necesita.
revoke all on function public.rupio_wall_public() from public;
grant execute on function public.rupio_wall_public() to anonymous, authenticated;

notify pgrst, 'reload schema';

-- Verificar después (debe dar: t | t | f y que entries sea un arreglo):
--   select has_function_privilege('anonymous', 'public.rupio_wall_public()', 'execute') as anon,
--          has_function_privilege('authenticated', 'public.rupio_wall_public()', 'execute') as auth,
--          has_table_privilege('anonymous', 'public.wall_entries', 'select') as lee_tabla,
--          jsonb_typeof(public.rupio_wall_public()->'entries') as tipo;
