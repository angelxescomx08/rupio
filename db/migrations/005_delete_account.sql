-- =====================================================================
-- 005 · "Eliminar mi cuenta" (requisito de Google Play)
-- Correr UNA vez en Neon → SQL Editor, con el rol dueño (neondb_owner).
--
-- La app llama POST {Data API}/rpc/rupio_delete_my_account con el JWT de la persona.
-- La función borra, en una sola transacción, TODO lo de esa cuenta:
--   récords, peso corporal, entrenos (con sus ejercicios y series), rutinas, ejercicios propios, perfil
--   y el usuario de Neon Auth (sus sesiones y su cuenta de correo y contraseña).
-- Si algo falla, no se borra nada.
--
-- Por qué una función: el endpoint /delete-user de Neon Auth está desactivado (responde 404) y la app no
-- puede tocar el esquema neon_auth. SECURITY DEFINER la ejecuta con los permisos de su dueño, pero SOLO
-- sobre auth.uid(): nadie puede borrar la cuenta de otra persona.
-- =====================================================================

create or replace function public.rupio_delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'Se necesita una sesión válida' using errcode = '28000';
  end if;

  delete from public.personal_records where user_id = uid;
  delete from public.body_weights     where user_id = uid;
  delete from public.workouts         where user_id = uid;   -- workout_exercises y workout_sets caen en cascada
  delete from public.routines         where user_id = uid;   -- routine_exercises cae en cascada
  delete from public.exercises        where owner_id = uid;  -- solo ejercicios propios (el catálogo tiene owner_id NULL)
  delete from public.profiles         where user_id = uid;

  delete from neon_auth.session      where "userId"::text = uid::text;
  delete from neon_auth.account      where "userId"::text = uid::text;
  delete from neon_auth."user"       where id::text = uid::text;
end;
$$;

revoke all on function public.rupio_delete_my_account() from public;
grant execute on function public.rupio_delete_my_account() to authenticated;

-- Para quitar la cuenta de prueba que creó la auditoría de la v1.6 al verificar que /delete-user está apagado:
-- delete from neon_auth.session where "userId" in (select id from neon_auth."user" where email like 'qa-borrado-%@example.com');
-- delete from neon_auth.account where "userId" in (select id from neon_auth."user" where email like 'qa-borrado-%@example.com');
-- delete from neon_auth."user" where email like 'qa-borrado-%@example.com';
