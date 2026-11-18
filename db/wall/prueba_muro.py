#!/usr/bin/env python3
"""
Pruebas del muro de agradecimientos (db/migrations/007_supporters_wall.sql) contra un Postgres LOCAL (nunca Neon).

Necesita: Postgres 16 con pgcrypto, `pip install cryptography`, y una base "t" con:
    create role authenticated nologin; create role anonymous nologin;
    create schema auth;
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('test.uid', true), '')::uuid $$;
    grant usage on schema auth to authenticated; grant execute on function auth.uid() to authenticated;
    \\i db/migrations/001_schema.sql
    \\i db/migrations/007_supporters_wall.sql
Variables: PGHOST, PGPORT (por defecto /tmp/pgtest y 5499).

Genera una llave RSA de 2048 bits como la de Google Play, firma compras con SHA1withRSA y revisa 24 casos:
firma válida/alterada, token repetido, compra de otra cuenta, pago pendiente, otro paquete, nombres, orden, ocultarse,
reembolso y borrar la cuenta. Última corrida: 24/24 (18 nov 2026).
"""
import os
import base64, hashlib, json, subprocess, sys, uuid
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes, serialization

key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
spki = base64.b64encode(key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
sql = subprocess.check_output([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "llave_play.py"), spki]).decode()
P = ["psql", "-h", os.environ.get("PGHOST", "/tmp/pgtest"), "-p", os.environ.get("PGPORT", "5499"), "-U", os.environ.get("PGUSER", "postgres"), "-d", "t", "-qAt", "-v", "ON_ERROR_STOP=1"]

def psql(q, uid=None, role=None):
    pre = ""
    if uid: pre += f"set test.uid = '{uid}';"
    if role: pre += f"set role {role};"
    r = subprocess.run(P + ["-c", pre + q], capture_output=True, text=True)
    return (r.stdout.strip(), r.stderr.strip())

A = str(uuid.uuid4()); B = str(uuid.uuid4()); C = str(uuid.uuid4())
for u, n in [(A, "Ana"), (B, "Beto")]:
    psql(f"insert into profiles(user_id, display_name) values ('{u}', '{n}')")

def purchase(uid, product, token=None, qty=None, acct=True, pkg="com.rupio.app", state=0):
    d = {"orderId": "GPA.3300-1111-2222-" + str(uuid.uuid4().int)[:5], "packageName": pkg, "productId": product,
         "purchaseTime": 1795000000000, "purchaseState": state, "purchaseToken": token or ("tok-" + uuid.uuid4().hex * 3),
         "quantity": qty or 1, "acknowledged": False}
    if acct: d["obfuscatedAccountId"] = hashlib.sha256(uid.encode()).hexdigest()
    js = json.dumps(d, separators=(",", ":"))
    sig = base64.b64encode(key.sign(js.encode(), padding.PKCS1v15(), hashes.SHA1())).decode()
    return js, sig

def reg(uid, js, sig, name=None):
    q = "select public.rupio_wall_register($j$%s$j$, $s$%s$s$, %s)" % (js, sig, ("$n$%s$n$" % name) if name else "null")
    return psql(q, uid, "authenticated")

ok = True
def check(label, cond, extra=""):
    global ok
    print(("OK   " if cond else "FAIL ") + label, extra if not cond else "")
    ok &= bool(cond)

js, sig = purchase(A, "rupio_mecenas")
out, err = reg(A, js, sig, "Ana")
check("sin llave → not_configured", "rupio:not_configured" in err, err)
psql(sql)
out, err = reg(A, js, sig, "Ana G.")
check("A entra al muro", out and json.loads(out)["rank"] == 1 and json.loads(out)["points"] == 499, out + err)
out2, err = reg(A, js, sig, "Ana G.")
check("mismo token otra vez = idempotente (no suma)", json.loads(out2)["points"] == 499, out2 + err)
out, err = reg(B, js, sig, "Beto")
check("B con token de A → token_used", "rupio:token_used" in err, err)
jsA2, sigA2 = purchase(A, "rupio_mecenas")
out, err = reg(B, jsA2, sigA2, "Beto")
check("B con compra atada a A → other_account", "rupio:other_account" in err, err)
bad = js.replace("rupio_mecenas", "rupio_muro_249")
out, err = reg(A, bad, sig, None)
check("JSON alterado → bad_signature", "rupio:bad_signature" in err, err)
jsB, sigB = purchase(B, "rupio_muro_99")
out, err = reg(B, jsB, sigB, None)
check("aporte sin lugar y sin nombre → name_required", "rupio:name_required" in err, err)
out, err = reg(B, jsB, sigB, "visita www.spam.com")
check("nombre con enlace → name_invalid", "rupio:name_invalid" in err, err)
out, err = reg(B, jsB, sigB, "  Beto   R  ")
me = json.loads(out) if out else {}
check("B entra con 99 → lugar 2, le faltan 401", me.get("rank") == 2 and me.get("to_next") == 401 and me.get("name") == "Beto R", out + err)
jsB2, sigB2 = purchase(B, "rupio_muro_249", qty=2)
out, err = reg(B, jsB2, sigB2, None)
me = json.loads(out) if out else {}
check("B suma 2×249 → 597 pts, lugar 1", me.get("rank") == 1 and me.get("points") == 597, out + err)
out, err = psql("select public.rupio_wall()", A, "authenticated")
w = json.loads(out)
check("lista ordenada: Beto, Ana; 'me' marca a A", [e["name"] for e in w["entries"]] == ["Beto R", "Ana G."] and w["entries"][1]["me"] and w["me"]["to_next"] == 99, out + err)
check("la lista no trae puntos ni ids de otros", all(set(e) == {"rank", "name", "since", "me"} for e in w["entries"]), out)
jsP, sigP = purchase(A, "rupio_mecenas", state=4)
out, err = reg(A, jsP, sigP, None)
check("pago pendiente → not_completed", "rupio:not_completed" in err, err)
jsX, sigX = purchase(A, "rupio_mecenas", pkg="com.otra.app")
out, err = reg(A, jsX, sigX, None)
check("otro paquete → bad_purchase", "rupio:bad_purchase" in err, err)
out, err = psql("select public.rupio_wall_set_hidden(true)", A, "authenticated")
out, err = psql("select public.rupio_wall()", B, "authenticated")
check("A se oculta → solo Beto en la lista", [e["name"] for e in json.loads(out)["entries"]] == ["Beto R"], out + err)
out, err = psql("select public.rupio_wall_set_hidden(false)", A, "authenticated")
out, err = psql("select public.rupio_wall_set_name('Ana la del gym')", A, "authenticated")
check("cambiar nombre", json.loads(out)["name"] == "Ana la del gym", out + err)
out, err = psql("select public.rupio_wall_set_name('x')", A, "authenticated")
check("nombre de 1 letra → name_length", "rupio:name_length" in err, err)
out, err = psql("select count(*) from public.wall_entries", A, "authenticated")
check("tabla cerrada para authenticated", "permission denied" in err, err)
out, err = psql("select public.rupio_wall_admin_revoke('x')", A, "authenticated")
check("admin_revoke no se puede llamar desde la app", "permission denied" in err, err)
out, err = psql("select public.rupio_wall()", None, "authenticated")
check("sin sesión → 28000", "sesión válida" in err, err)
out, err = psql("select public.rupio_wall()", C, "authenticated")
check("C sin lugar: me = null", json.loads(out)["me"] is None, out + err)
jsC, sigC = purchase(C, "rupio_mecenas")
out, err = reg(C, jsC, sigC, "Caro")
check("C sin perfil aún: se crea el perfil y entra", json.loads(out)["rank"] in (1, 2, 3), out + err)
tokB = json.loads(jsB2)["purchaseToken"]
psql(f"select public.rupio_wall_admin_revoke('{tokB}')")
out, err = psql("select public.rupio_wall_me_json('%s')" % B)
check("reembolso de 2×249 → B vuelve a 99", json.loads(out)["points"] == 99, out + err)
psql(f"delete from profiles where user_id = '{A}'")
out, err = psql(f"select count(*) from wall_entries where user_id = '{A}'")
out2, err2 = psql(f"select count(*) from wall_payments where user_id is null")
check("borrar cuenta → sin lugar; pagos quedan sin dueño", out == "0" and int(out2) >= 1, out + out2 + err + err2)
import time
t = time.time(); psql("select public.rupio_play_signature_ok($j$%s$j$, $s$%s$s$, (select rsa_modulus from wall_config))" % (jsC, sigC)); print("tiempo verificación+psql: %.0f ms" % ((time.time() - t) * 1000))
print("TODO BIEN" if ok else "HAY FALLAS")
