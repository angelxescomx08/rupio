-- Datos de prueba: 200,000 perfiles y 100,000 lugares en el muro (prueba de estrés: hoy hay unos cuantos).
-- Determinista (setseed) para que las mediciones se puedan repetir.
select setseed(0.42);

insert into public.profiles (user_id, display_name)
select gen_random_uuid(), 'Atleta ' || i from generate_series(1, 200000) i;

-- Muro: puntos en múltiplos de los productos reales (499, 99, 249), fecha de llegada en el último año.
insert into public.wall_entries (user_id, first_at, consent_at, points, display_name)
select p.user_id,
       now() - (random() * interval '365 days'),
       now(),
       499 + (floor(random() * 6) * 99)::int + (floor(random() * 3) * 249)::int,
       left('Mecenas ' || p.display_name, 30)
from (select user_id, display_name from public.profiles order by user_id limit 100000) p;

vacuum analyze public.profiles;
vacuum analyze public.wall_entries;
