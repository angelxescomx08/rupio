-- =====================================================================
-- Rupio · 007_supporters_wall.sql  (v1.8)
-- Nivel "Mecenas" y muro de agradecimientos.
-- Correr UNA vez en Neon → SQL Editor, con el rol dueño (neondb_owner). Es idempotente.
--
-- Cómo funciona:
--  * Comprar "Mecenas" (rupio_mecenas, $499) te pone en el muro con el nombre que elijas.
--    Los aportes extra (rupio_muro_99, rupio_muro_249 y volver a comprar rupio_mecenas) suman puntos
--    y te suben de lugar. Orden: más puntos primero; empate → quien llegó antes.
--  * Los puntos salen de esta tabla (precio de lista en MXN), nunca de lo que diga el teléfono.
--  * Cada compra se VERIFICA AQUÍ: la firma RSA-SHA1 de Google Play sobre el JSON de la compra, con la llave
--    pública de licencias de la app (Play Console → Monetizar → Configuración de monetización). Sin llave
--    cargada, el muro no acepta compras (la app las guarda y reintenta). Cargarla: db/wall/llave_play.py.
--  * La compra va atada a la cuenta de Rupio: la app manda obfuscatedAccountId = sha256(user_id) y aquí se
--    compara. Un token solo cuenta una vez.
--  * Las tablas no se pueden leer ni escribir directo (RLS sin políticas, sin permisos): solo con las
--    funciones de abajo, que exponen nombre, lugar y fecha, nunca correos ni montos de otras personas.
--  * Borrar la cuenta borra su lugar en el muro (FK a profiles en cascada); los pagos se quedan sin dueño
--    (para que el mismo token no se pueda volver a usar).
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------- configuración (una sola fila) ----------
create table if not exists public.wall_config (
  id           boolean     primary key default true check (id),
  updated_at   timestamptz not null default now(),
  rsa_modulus  numeric,                                   -- n de la llave pública de Google Play (e = 65537)
  package_name text        not null default 'com.rupio.app'
);
insert into public.wall_config (id) values (true) on conflict (id) do nothing;

-- ---------- productos del muro: puntos = precio de lista en pesos ----------
create table if not exists public.wall_products (
  product_id text    primary key,
  points     integer not null check (points > 0),
  label      text    not null
);
insert into public.wall_products (product_id, points, label) values
  ('rupio_mecenas', 499, 'Mecenas'),
  ('rupio_muro_99',  99, 'Aporte de 99'),
  ('rupio_muro_249', 249, 'Aporte de 249')
on conflict (product_id) do update set points = excluded.points, label = excluded.label;

-- ---------- lugares en el muro ----------
create table if not exists public.wall_entries (
  user_id      uuid        primary key references public.profiles (user_id) on delete cascade,
  first_at     timestamptz not null default now(),   -- desempate: quien llegó antes
  updated_at   timestamptz not null default now(),
  consent_at   timestamptz not null,                 -- aceptó que su nombre se publique (LFPDPPP)
  points       integer     not null default 0 check (points >= 0),
  hidden       boolean     not null default false,   -- la persona se quitó del muro (conserva su nivel)
  blocked      boolean     not null default false,   -- moderación: nombre ofensivo (lo pone el responsable)
  display_name text        not null check (char_length(display_name) between 2 and 30)
);
-- Sirve a la lista del muro (orden y desempate) sin "Sort".
create index if not exists wall_entries_rank_idx
  on public.wall_entries (points desc, first_at) include (display_name) where not hidden and not blocked;

-- ---------- pagos verificados ----------
create table if not exists public.wall_payments (
  purchase_token text        primary key,
  user_id        uuid        references public.profiles (user_id) on delete set null,
  created_at     timestamptz not null default now(),
  purchase_time  timestamptz,
  points         integer     not null check (points > 0),
  quantity       smallint    not null default 1,
  revoked        boolean     not null default false,  -- reembolso: ver rupio_wall_admin_revoke
  order_id       text,
  product_id     text        not null references public.wall_products (product_id),
  purchase_json  text        not null,
  signature      text        not null
);
create index if not exists wall_payments_user_idx on public.wall_payments (user_id);

alter table public.wall_config   enable row level security;
alter table public.wall_products enable row level security;
alter table public.wall_entries  enable row level security;
alter table public.wall_payments enable row level security;
revoke all on public.wall_config, public.wall_products, public.wall_entries, public.wall_payments from public;
do $$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    execute 'revoke all on public.wall_config, public.wall_products, public.wall_entries, public.wall_payments from authenticated';
  end if;
  if exists (select 1 from pg_roles where rolname = 'anonymous') then
    execute 'revoke all on public.wall_config, public.wall_products, public.wall_entries, public.wall_payments from anonymous';
  end if;
end $$;

-- =====================================================================
-- Verificación de la firma de Google Play (RSA PKCS#1 v1.5 con SHA-1), en SQL puro.
-- Con e = 65537: s^e mod n = 16 cuadrados y una multiplicación (milisegundos con numeric).
-- =====================================================================
create or replace function public.rupio_bytes_to_numeric(b bytea)
returns numeric language plpgsql immutable strict as $$
declare r numeric := 0; i integer;
begin
  for i in 0 .. length(b) - 1 loop r := r * 256 + get_byte(b, i); end loop;
  return r;
end $$;

create or replace function public.rupio_numeric_to_hex(n numeric, len integer)
returns text language plpgsql immutable strict as $$
declare h text := ''; x numeric := n; i integer;
begin
  for i in 1 .. len loop
    h := lpad(to_hex(mod(x, 256)::integer), 2, '0') || h;
    x := div(x, 256);
  end loop;
  if x <> 0 then return null; end if;          -- no cabe en len bytes
  return h;
end $$;

create or replace function public.rupio_play_signature_ok(data text, signature_b64 text, n numeric)
returns boolean language plpgsql immutable
set search_path = public, extensions, pg_temp
as $$
declare
  k integer := 0; t numeric := n; s numeric; m numeric; b numeric; i integer; expected text; got text;
begin
  if data is null or signature_b64 is null or n is null or n < 3 then return false; end if;
  while t > 0 loop t := div(t, 256); k := k + 1; end loop;          -- bytes del módulo (256 para 2048 bits)
  s := rupio_bytes_to_numeric(decode(signature_b64, 'base64'));
  if s >= n then return false; end if;
  m := mod(s, n); b := m;
  for i in 1 .. 16 loop m := mod(m * m, n); end loop;               -- s^(2^16)
  m := mod(m * b, n);                                               -- s^(2^16 + 1) = s^65537
  got := rupio_numeric_to_hex(m, k);
  -- EM = 00 01 FF…FF 00 || DigestInfo(SHA-1) || hash
  expected := '0001' || repeat('ff', k - 3 - 35) || '00' || '3021300906052b0e03021a05000414'
              || encode(digest(convert_to(data, 'UTF8'), 'sha1'), 'hex');
  return got = expected;
exception when others then
  return false;                                                     -- base64 inválido, etc.
end $$;

-- ---------- nombre público: 2–30 caracteres, sin enlaces ni correos ----------
create or replace function public.rupio_wall_clean_name(p text)
returns text language plpgsql immutable as $$
declare v text := btrim(regexp_replace(coalesce(p, ''), '\s+', ' ', 'g'));
begin
  if char_length(v) < 2 or char_length(v) > 30 then raise exception 'rupio:name_length'; end if;
  if v ~* '(https?:|www\.|\.com|\.mx|\.net|@)' or v ~ '[[:cntrl:]]' then raise exception 'rupio:name_invalid'; end if;
  return v;
end $$;

-- ---------- tu lugar (JSON) ----------
create or replace function public.rupio_wall_me_json(uid uuid)
returns jsonb language sql stable
set search_path = public, pg_temp
as $$
  with ranked as (
    select e.user_id, e.points, e.first_at,
           row_number() over (order by e.points desc, e.first_at) as rank
    from wall_entries e where not e.hidden and not e.blocked
  ), me as (select * from wall_entries where user_id = uid)
  select case when me.user_id is null then null else jsonb_build_object(
    'name', me.display_name,
    'points', me.points,
    'since', me.first_at,
    'hidden', me.hidden,
    'blocked', me.blocked,
    'rank', r.rank,
    -- puntos que te faltan para pasar al lugar de arriba (en empate gana quien llegó antes)
    'to_next', (select a.points - me.points + 1 from ranked a where a.rank = r.rank - 1),
    'total', (select count(*) from ranked)
  ) end
  from (select 1) one left join me on true left join ranked r on r.user_id = me.user_id
$$;

-- =====================================================================
-- API para la app (POST /rpc/...): SECURITY DEFINER, siempre sobre auth.uid().
-- Errores: el mensaje empieza con "rupio:" para que la app sepa qué decir.
-- =====================================================================

-- Lista del muro (los primeros 300) y tu lugar.
create or replace function public.rupio_wall()
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp
as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  return jsonb_build_object(
    'entries', coalesce((
      select jsonb_agg(jsonb_build_object('rank', rank, 'name', display_name, 'since', first_at, 'me', user_id = uid) order by rank)
      from (select e.user_id, e.display_name, e.first_at, row_number() over (order by e.points desc, e.first_at) as rank
            from wall_entries e where not e.hidden and not e.blocked
            order by e.points desc, e.first_at limit 300) t), '[]'::jsonb),
    'me', rupio_wall_me_json(uid));
end $$;

-- Registrar una compra del muro (Mecenas o aporte extra). Idempotente por token.
create or replace function public.rupio_wall_register(purchase_json text, signature text, display_name text default null)
returns jsonb language plpgsql volatile security definer
set search_path = public, extensions, pg_temp
as $$
declare
  uid uuid := auth.uid();
  cfg wall_config;
  j jsonb;
  tok text; pid text; qty integer; pts integer; acct text; prev uuid; nm text; has_entry boolean;
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  select * into cfg from wall_config where id;
  if cfg.rsa_modulus is null then raise exception 'rupio:not_configured'; end if;
  if not rupio_play_signature_ok(purchase_json, signature, cfg.rsa_modulus) then raise exception 'rupio:bad_signature'; end if;

  begin j := purchase_json::jsonb; exception when others then raise exception 'rupio:bad_purchase'; end;
  tok := j->>'purchaseToken';
  pid := coalesce(j->>'productId', j->'productIds'->>0);
  qty := least(greatest(coalesce((j->>'quantity')::integer, 1), 1), 20);
  acct := j->>'obfuscatedAccountId';
  if tok is null or coalesce(j->>'packageName', '') <> cfg.package_name then raise exception 'rupio:bad_purchase'; end if;
  if coalesce((j->>'purchaseState')::integer, 0) <> 0 then raise exception 'rupio:not_completed'; end if;
  -- Ya registrado: si es tuyo, solo devuelve tu lugar (reintento); si no, error.
  select p.user_id into prev from wall_payments p where p.purchase_token = tok;
  if found then
    if prev is distinct from uid then raise exception 'rupio:token_used'; end if;
    return rupio_wall_me_json(uid);
  end if;
  if acct is not null and acct <> encode(digest(uid::text, 'sha256'), 'hex') then raise exception 'rupio:other_account'; end if;
  select points * qty into pts from wall_products where product_id = pid;
  if pts is null then raise exception 'rupio:unknown_product'; end if;

  has_entry := exists (select 1 from wall_entries where user_id = uid);
  if display_name is not null then nm := rupio_wall_clean_name(display_name);
  elsif not has_entry then raise exception 'rupio:name_required';
  end if;

  -- El perfil normalmente ya existe (lo sube la sincronización); si no, se crea con lo mínimo.
  insert into profiles (user_id, display_name) values (uid, coalesce(nm, 'Mecenas')) on conflict (user_id) do nothing;

  insert into wall_payments (purchase_token, user_id, purchase_time, points, quantity, order_id, product_id, purchase_json, signature)
  values (tok, uid, to_timestamp(nullif(j->>'purchaseTime', '')::bigint / 1000.0), pts, qty, j->>'orderId', pid, purchase_json, signature);

  if has_entry then
    update wall_entries e set
      points = e.points + pts,
      display_name = coalesce(nm, e.display_name),
      consent_at = case when nm is not null then now() else e.consent_at end,
      hidden = case when nm is not null then false else e.hidden end,
      updated_at = now()
    where e.user_id = uid;
  else
    insert into wall_entries (user_id, consent_at, points, display_name) values (uid, now(), pts, nm);
  end if;

  return rupio_wall_me_json(uid);
end $$;

-- Cambiar tu nombre en el muro.
create or replace function public.rupio_wall_set_name(display_name text)
returns jsonb language plpgsql volatile security definer
set search_path = public, pg_temp
as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  update wall_entries set display_name = rupio_wall_clean_name(rupio_wall_set_name.display_name), consent_at = now(), updated_at = now()
  where user_id = uid;
  if not found then raise exception 'rupio:no_entry'; end if;
  return rupio_wall_me_json(uid);
end $$;

-- Quitarte del muro (o volver). Tus puntos y tu nivel se conservan.
create or replace function public.rupio_wall_set_hidden(hidden boolean)
returns jsonb language plpgsql volatile security definer
set search_path = public, pg_temp
as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Se necesita una sesión válida' using errcode = '28000'; end if;
  update wall_entries set hidden = coalesce(rupio_wall_set_hidden.hidden, true),
         consent_at = case when not coalesce(rupio_wall_set_hidden.hidden, true) then now() else consent_at end,
         updated_at = now()
  where user_id = uid;
  if not found then raise exception 'rupio:no_entry'; end if;
  return rupio_wall_me_json(uid);
end $$;

-- ---------- solo para el responsable (no se da permiso a la app) ----------
-- Reembolso: marca el pago y recalcula los puntos de esa persona.
--   select public.rupio_wall_admin_revoke('<purchaseToken>');
create or replace function public.rupio_wall_admin_revoke(token text)
returns void language plpgsql volatile
set search_path = public, pg_temp
as $$
declare uid uuid;
begin
  update wall_payments set revoked = true where purchase_token = token returning user_id into uid;
  if uid is not null then
    update wall_entries set points = coalesce((select sum(points) from wall_payments where user_id = uid and not revoked), 0), updated_at = now()
    where user_id = uid;
  end if;
end $$;
-- Ocultar un nombre ofensivo:  update public.wall_entries set blocked = true where display_name ilike '%…%';

revoke all on function public.rupio_bytes_to_numeric(bytea), public.rupio_numeric_to_hex(numeric, integer),
  public.rupio_play_signature_ok(text, text, numeric), public.rupio_wall_clean_name(text), public.rupio_wall_me_json(uuid),
  public.rupio_wall(), public.rupio_wall_register(text, text, text), public.rupio_wall_set_name(text),
  public.rupio_wall_set_hidden(boolean), public.rupio_wall_admin_revoke(text) from public;
do $$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    execute 'grant execute on function public.rupio_wall(), public.rupio_wall_register(text, text, text),
             public.rupio_wall_set_name(text), public.rupio_wall_set_hidden(boolean) to authenticated';
  end if;
end $$;

-- Que la Data API (PostgREST) vea las funciones nuevas sin esperar.
notify pgrst, 'reload schema';
