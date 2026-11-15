-- Generador de datos de prueba (dummy) para medir rendimiento.
-- Placeholders: {{S}} esquema · {{HU}} usuarios intensivos · {{HW}} entrenamientos por usuario intensivo
--               {{NU}} usuarios normales · {{NMIN}}/{{NMAX}} entrenamientos por usuario normal
-- Cada entrenamiento: 6 ejercicios × 4 series (1 de calentamiento) = 24 series.
-- Cada usuario rota 3 rutinas (Empuje/Tirón/Pierna) con 18 ejercicios propios → historial realista.

create or replace function {{S}}.rupio_uuidv7_at(ts timestamptz) returns uuid
language sql volatile parallel safe as $$
  select encode(set_bit(set_bit(overlay(uuid_send(gen_random_uuid())
         placing substring(int8send(floor(extract(epoch from ts) * 1000)::bigint) from 3) from 1 for 6),
         52, 1), 53, 1), 'hex')::uuid
$$;

create table if not exists {{S}}.bench_users (user_id uuid primary key, n_workouts int, tz text, heavy bool, ex_offset int);
truncate {{S}}.bench_users;
truncate {{S}}.workout_sets, {{S}}.workout_exercises, {{S}}.workouts;

insert into {{S}}.bench_users
select gen_random_uuid(), {{HW}}, 'America/Mexico_City', true, (i * 17) % 150 from generate_series(1, {{HU}}) i
union all
select gen_random_uuid(), {{NMIN}} + floor(random() * ({{NMAX}} - {{NMIN}} + 1))::int,
       (array['America/Mexico_City','America/Bogota','Europe/Madrid','America/Argentina/Buenos_Aires',
              'America/Los_Angeles','Asia/Tokyo'])[1 + floor(random() * 6)::int],
       false, floor(random() * 150)::int
from generate_series(1, {{NU}}) i;

alter table {{S}}.workouts disable trigger user;
alter table {{S}}.workout_exercises disable trigger user;
alter table {{S}}.workout_sets disable trigger user;

insert into {{S}}.workouts (id, user_id, started_at, ended_at, created_at, updated_at, local_date, duration_sec, name, time_zone)
select {{S}}.rupio_uuidv7_at(x.st), u.user_id, x.st, x.st + make_interval(secs => x.dur), x.st,
       x.st + make_interval(secs => x.dur), (x.st at time zone u.tz)::date, x.dur,
       (array['Empuje','Tirón','Pierna'])[1 + k % 3], u.tz
from {{S}}.bench_users u
cross join lateral generate_series(1, u.n_workouts) k
cross join lateral (select now() - ((u.n_workouts - k) * interval '38 hours') - random() * interval '4 hours' as st,
                           (2400 + floor(random() * 3000))::int as dur) x;

insert into {{S}}.workout_exercises (id, workout_id, user_id, updated_at, position, rest_sec, rep_min, rep_max, exercise_id)
select {{S}}.rupio_uuidv7_at(w.started_at), w.id, w.user_id, w.updated_at, p, 90, 8, 12,
       ex.ids[1 + ((u.ex_offset + (array_position(array['Empuje','Tirón','Pierna'], w.name) - 1) * 6 + p) % ex.n)]
from {{S}}.workouts w
join {{S}}.bench_users u on u.user_id = w.user_id
cross join generate_series(1, 6) p
cross join (select array_agg(id order by id) ids, count(*)::int n
            from {{S}}.exercises where tracking = 'weight_reps' and owner_id is null) ex;

insert into {{S}}.workout_sets (id, workout_exercise_id, workout_id, user_id, completed_at, updated_at, performed_on,
                                weight_kg, rpe, reps, position, set_type, completed, exercise_id)
select {{S}}.rupio_uuidv7_at(w.started_at), we.id, we.workout_id, we.user_id,
       w.started_at + ((we.position * 4 + s) * interval '2 minutes'), w.updated_at, w.local_date,
       (round(((10 + abs(hashtext(we.exercise_id || u.user_id::text)) % 90)
              * (case when s = 1 then 0.5 else 1 end)
              * (1 + 0.35 * least(1, extract(epoch from w.started_at - (now() - interval '5 years')) / extract(epoch from interval '5 years'))))::numeric / 2.5) * 2.5)::real,
       case when random() < 0.3 then 7 + floor(random() * 4) else null end,
       6 + floor(random() * 7)::int, s, case when s = 1 then 1 else 0 end, true, we.exercise_id
from {{S}}.workout_exercises we
join {{S}}.workouts w on w.id = we.workout_id
join {{S}}.bench_users u on u.user_id = we.user_id
cross join generate_series(1, 4) s;

update {{S}}.workouts w
   set total_volume_kg = t.v, total_sets = t.c, total_reps = t.r
  from (select workout_id, sum(volume_kg)::float8 v, count(*) filter (where set_type <> 1) c, sum(reps) filter (where set_type <> 1) r
          from {{S}}.workout_sets group by 1) t
 where t.workout_id = w.id;

alter table {{S}}.workouts enable trigger user;
alter table {{S}}.workout_exercises enable trigger user;
alter table {{S}}.workout_sets enable trigger user;
