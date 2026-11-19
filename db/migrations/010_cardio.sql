-- v1.9 · Ejercicios de cardio en el catálogo del servidor (la app los trae desde el catálogo v3).
-- La sincronización no los necesita (exercise_id es texto sin llave foránea); esto deja el catálogo del servidor igual
-- que el de la app y hace que rupio_muscle_sets no cuente el cardio como un músculo, igual que la app
-- (MuscleSplit.NOT_COUNTED). Se puede correr más de una vez.

insert into exercises (id, name, primary_muscle, secondary_muscles, equipment, tracking, is_lower_body) values
('caminadora','Caminadora (cinta)','cardio','{}'::text[],'maquina','duration',false),
('bicicleta_fija','Bicicleta fija','cardio','{}'::text[],'maquina','duration',false),
('eliptica','Elíptica','cardio','{}'::text[],'maquina','duration',false),
('escaladora','Escaladora (escaleras)','cardio','{}'::text[],'maquina','duration',false),
('remo_ergometro','Remo de cardio (ergómetro)','cardio','{}'::text[],'maquina','duration',false),
('saltar_la_cuerda','Saltar la cuerda','cardio','{}'::text[],'otro','duration',false)
on conflict (id) do update set name=excluded.name, primary_muscle=excluded.primary_muscle, secondary_muscles=excluded.secondary_muscles, equipment=excluded.equipment, tracking=excluded.tracking, is_lower_body=excluded.is_lower_body;

-- Mismo cuerpo que en 003_security.sql, sin los grupos que no son un músculo.
create or replace function public.rupio_muscle_sets(p_from date, p_to date)
returns table (muscle text, sets bigint, volume_kg double precision)
language sql stable security invoker parallel safe
begin atomic
  select e.primary_muscle, count(*), sum(s.volume_kg)
  from public.workout_sets s
  join public.exercises e on e.id = s.exercise_id
  where s.user_id = (select auth.uid()) and s.completed and not s.deleted and s.set_type <> 1
    and s.performed_on between p_from and p_to
    and e.primary_muscle not in ('cardio', 'cuerpo_completo')
  group by 1
  order by 2 desc;
end;
