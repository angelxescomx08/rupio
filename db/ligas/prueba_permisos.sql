-- ¿Qué puede llamar la app (rol authenticated)? Debe ser SOLO la API pública.
select string_agg(p.oid::regprocedure::text, ', ' order by p.proname) filter (where has_function_privilege('authenticated', p.oid, 'execute')
         and p.proname in ('rupio_league_add','rupio_league_next_tier','rupio_league_week','rupio_league_cap','rupio_wall_me_json',
                           'rupio_wall_admin_revoke','rupio_play_signature_ok','rupio_bytes_to_numeric','rupio_numeric_to_hex','rupio_wall_clean_name'))
       as internas_abiertas,
       bool_and(has_function_privilege('authenticated', p.oid, 'execute')) filter (where p.proname in
         ('rupio_league','rupio_league_top','rupio_league_me_global','rupio_league_join','rupio_league_set_hidden',
          'rupio_wall','rupio_wall_register','rupio_wall_set_name','rupio_wall_set_hidden')) as api_ok,
       has_table_privilege('authenticated', 'public.wall_counts', 'select') as wall_counts_abierta,
       has_table_privilege('authenticated', 'public.league_scores', 'select') as scores_abierta
from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname like 'rupio%';
