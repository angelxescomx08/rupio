-- Solo para pruebas locales (en Neon esto ya lo dan Neon Auth y pg_session_jwt).
do $$ begin
  if not exists (select 1 from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
  if not exists (select 1 from pg_roles where rolname='anonymous') then create role anonymous nologin; end if;
end $$;
create schema if not exists auth;
create or replace function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('test.uid', true), '')::uuid $$;
grant usage on schema auth to authenticated; grant execute on function auth.uid() to authenticated;
-- Igual que Neon (visto en production, pg_default_acl): todo lo nuevo en public queda abierto a authenticated.
-- Sin esto, las pruebas locales no ven funciones internas que la app podría llamar por /rpc.
grant usage on schema public to authenticated;
alter default privileges in schema public grant select, insert, update, delete on tables to authenticated;
alter default privileges in schema public grant usage on sequences to authenticated;
alter default privileges in schema public grant execute on functions to authenticated;
