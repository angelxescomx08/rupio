-- Datos de prueba para ligas: 100,000 participantes, 26 semanas, ~60 % entrena cada semana
-- (≈ 1.56 millones de puntajes semanales en ≈ 52 mil grupos de 30). Sin pasar por el trigger.
select setseed(0.7);

insert into public.league_members (user_id, joined_at, consent_at, tier, badge, nickname)
select user_id, now() - interval '200 days', now(),
       case when r < .40 then 1 when r < .65 then 2 when r < .80 then 3 when r < .90 then 4 when r < .96 then 5 else 6 end,
       case when b < .85 then 0 when b < .92 then 1 when b < .96 then 2 when b < .99 then 3 else 4 end,
       'Atleta ' || substr(user_id::text, 1, 8)
from (select user_id, random() r, random() b from public.profiles order by user_id offset 50000 limit 100000) p;

create temp table wk as select public.rupio_league_week(current_date) - 7 * i as week from generate_series(0, 25) i;

create temp table part as
select w.week, m.user_id, m.tier,
       (row_number() over (partition by w.week, m.tier order by random()) - 1) / 30 as gk
from wk w cross join public.league_members m
where random() < 0.6;

alter table public.league_groups add column gkey integer;
insert into public.league_groups (week, tier, members, gkey)
select week, tier, count(*), gk from part group by week, tier, gk;

insert into public.league_scores (user_id, week, group_id, first_at, updated_at, volume_kg, workouts)
select p.user_id, p.week, g.id,
       p.week::timestamp + random() * interval '5 days',
       now(),
       round((p.tier * 4000 * (0.35 + random() * 1.3))::numeric, 1)::double precision,
       1 + floor(random() * 5)::int
from part p join public.league_groups g on g.week = p.week and g.tier = p.tier and g.gkey = p.gk;

alter table public.league_groups drop column gkey;
insert into public.league_weeks (week, participants) select week, count(*) from public.league_scores group by week;
vacuum analyze public.league_members;
vacuum analyze public.league_groups;
vacuum analyze public.league_scores;
vacuum analyze public.league_weeks;
