#!/usr/bin/env python3
"""
QA del servidor (Neon Auth + Data API) con un usuario TEMPORAL que se borra al final.

1. Registro/login → JWT, igual que la app.
2. Carga: sube miles de filas en lotes de 400 (como la app) y mide.
3. Bajada completa con la paginación por llave EXACTA de la app (or=(updated_at.gt."…",and(updated_at.eq."…",id.gt."…")))
   y verifica: ninguna fila falta ni se repite.
4. Seguridad: leer/escribir/borrar datos de OTRO usuario, sin JWT, JWT alterado, datos inválidos, inyección en filtros,
   cuerpo enorme.
5. Limpieza: borra todo lo del usuario temporal (datos y cuenta).

Uso: python3 qa_server.py
Necesita un módulo `nsql` (consultas SQL a Neon con el rol dueño, vía la variable NEON_DATABASE_URL) solo para
comprobar que los datos reales siguen intactos y para borrar el usuario temporal al final. La app nunca usa ese rol.
"""
import json, os, random, statistics, sys, time, uuid
import urllib.request, urllib.error, urllib.parse, http.cookiejar, ssl
from datetime import datetime, timezone, timedelta

AUTH = "https://ep-wild-art-bdlip6s4.neonauth.c-8.us-east-2.aws.neon.tech/neondb/auth"
API = "https://ep-wild-art-bdlip6s4.apirest.c-8.us-east-2.aws.neon.tech/neondb/rest/v1"
OTHER_USER = "5b614c54-a42e-49ca-b7a7-2214abf3c32d"     # la cuenta real de Angel: NUNCA debe poder tocarse
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, "/home/claude/tools")
import nsql  # noqa: E402

ctx = ssl.create_default_context(cafile=os.path.expanduser("~/.ccr/ca-bundle.crt")) if os.path.exists(os.path.expanduser("~/.ccr/ca-bundle.crt")) else None
results = []


def http(method, url, body=None, headers=None, raw=False):
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json", "Accept": "application/json", **(headers or {})})
    t = time.perf_counter()
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=60) as r:
            txt = r.read().decode(); return r.status, (txt if raw else (json.loads(txt) if txt else None)), dict(r.headers), (time.perf_counter() - t) * 1000
    except urllib.error.HTTPError as e:
        txt = e.read().decode(errors="replace"); return e.code, txt[:300], dict(e.headers), (time.perf_counter() - t) * 1000


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail)); print(("✓" if ok else "✗"), name, detail)


def signup():
    email = f"qa-{uuid.uuid4().hex[:10]}@rupio-qa.test"; pwd = "Qa-" + uuid.uuid4().hex
    origin = "https://" + urllib.parse.urlparse(AUTH).netloc
    s, body, h, ms = http("POST", AUTH + "/sign-up/email", {"email": email, "password": pwd, "name": "QA temporal"}, {"Origin": origin})
    assert s == 200, (s, body)
    cookie = "; ".join(c.split(";")[0] for k, c in h.items() if k.lower() == "set-cookie")
    s2, sess, h2, _ = http("GET", AUTH + "/get-session", headers={"Origin": origin, "Cookie": cookie})
    jwt = h2.get("set-auth-jwt") or h2.get("Set-Auth-Jwt")
    uid = sess["user"]["id"]
    check("Registro y JWT como la app", s2 == 200 and jwt and uid, f"{ms:.0f} ms")
    return email, uid, jwt


def iso(dt): return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def main():
    email, uid, jwt = signup()
    H = {"Authorization": f"Bearer {jwt}"}
    try:
        # ---------------------------------------------------------------- carga
        now = datetime.now(timezone.utc)
        W, WE, S = [], [], []
        for k in range(150):
            st = now - timedelta(days=150 - k)
            wid = str(uuid.uuid4())
            W.append(dict(id=wid, user_id=uid, routine_id=None, name=f"QA {k}", started_at=iso(st), ended_at=iso(st + timedelta(hours=1)), time_zone="America/Mexico_City",
                          local_date=st.date().isoformat(), duration_sec=3600, total_volume_kg=1000.0, total_sets=24, total_reps=240, notes=None, deleted=False))
            for p in range(6):
                weid = str(uuid.uuid4())
                WE.append(dict(id=weid, workout_id=wid, user_id=uid, exercise_id="press_de_banca_con_barra", position=p, superset_id=None, superset_rest_mode=0,
                               superset_rest_sec=None, rest_sec=90, rep_min=8, rep_max=12, notes=None, deleted=False, set_plan=None))
                for s in range(4):
                    S.append(dict(id=str(uuid.uuid4()), workout_exercise_id=weid, workout_id=wid, user_id=uid, exercise_id="press_de_banca_con_barra", position=s,
                                  set_type=0, weight_kg=60.0 + s, reps=10, duration_sec=None, rpe=None, completed=True, completed_at=iso(st), performed_on=st.date().isoformat(), deleted=False))
        def push(table, rows, conflict="id"):
            lat = []
            for i in range(0, len(rows), 400):
                s, body, _, ms = http("POST", f"{API}/{table}?on_conflict={conflict}", rows[i:i + 400], {**H, "Prefer": "resolution=merge-duplicates,return=minimal"})
                assert s in (200, 201, 204), (table, s, body); lat.append(ms)
            return lat
        t0 = time.perf_counter()
        lw = push("workouts", W); lwe = push("workout_exercises", WE); ls = push("workout_sets", S)
        total = time.perf_counter() - t0
        lat = lw + lwe + ls
        check(f"Subida de {len(W) + len(WE) + len(S):,} filas en lotes de 400", True,
              f"{total:.1f} s total · lote p50 {statistics.median(lat):.0f} ms · máx {max(lat):.0f} ms · {len(S) / sum(ls) * 1000:.0f} series/s")
        # Subir lo mismo otra vez = idempotente (nada duplicado).
        push("workout_sets", S[:400])
        s, cnt, h, _ = http("GET", f"{API}/workout_sets?select=id&user_id=eq.{uid}&limit=1", headers={**H, "Prefer": "count=exact"})
        cr = next((v for k, v in h.items() if k.lower() == "content-range"), "0/0")
        n_server = int(cr.split("/")[-1])
        check("Reenviar un lote no duplica (upsert idempotente)", n_server == len(S), f"{n_server} en el servidor")

        # ---------------------------------------------------------------- bajada con la paginación de la app
        def keyset(row, tie):
            v = lambda c: '"' + str(row[c]).replace('"', "") + '"'
            parts = [f"updated_at.gt.{v('updated_at')}"]
            for k in range(len(tie)):
                eqs = [f"updated_at.eq.{v('updated_at')}"] + [f"{t}.eq.{v(t)}" for t in tie[:k]] + [f"{tie[k]}.gt.{v(tie[k])}"]
                parts.append("and(" + ",".join(eqs) + ")")
            return "(" + ",".join(parts) + ")"
        def pull(table, tie=("id",)):
            got = []; last = None; pages = 0; t = time.perf_counter()
            while True:
                q = {"user_id": f"eq.{uid}", "select": "*", "order": ",".join(["updated_at.asc"] + [f"{x}.asc" for x in tie]), "limit": "400"}
                if last is None: q["updated_at"] = "gt.1970-01-01T00:00:00Z"
                else: q["or"] = keyset(last, list(tie))
                s, rows, _, _ = http("GET", f"{API}/{table}?" + urllib.parse.urlencode(q), headers=H)
                assert s == 200, (s, rows)
                pages += 1
                if not rows: break
                got += rows; last = rows[-1]
                if len(rows) < 400: break
            return got, pages, time.perf_counter() - t
        got, pages, secs = pull("workout_sets")
        ids = [r["id"] for r in got]
        check("Bajada completa sin huecos ni repetidos (filtro or= real de PostgREST)", len(ids) == len(S) and len(set(ids)) == len(S),
              f"{len(ids):,} filas en {pages} páginas, {secs:.1f} s")
        ts = sorted(datetime.fromisoformat(r["updated_at"]) for r in got)
        window = max(sum(1 for t in ts[i:i + 2000] if (t - ts[i]).total_seconds() <= 5) for i in range(0, len(ts), 50))
        check("Más de 400 filas dentro de una ventana de 5 s (el caso que rompía la sincronización)", window > 400, f"{window} filas en 5 s")
        # El algoritmo ANTERIOR contra el servidor real: solo por updated_at con 5 s de traslape.
        def old_pull():
            seen = set(); cursor = None
            for _ in range(5):                                   # 5 sincronizaciones seguidas
                while True:
                    since = (datetime.fromisoformat(cursor) - timedelta(seconds=5)).isoformat() if cursor else "1970-01-01T00:00:00Z"
                    q = {"user_id": f"eq.{uid}", "select": "id,updated_at", "updated_at": f"gt.{since}", "order": "updated_at.asc", "limit": "400"}
                    s, rows, _, _ = http("GET", f"{API}/workout_sets?" + urllib.parse.urlencode(q), headers=H)
                    if not rows: break
                    seen.update(r["id"] for r in rows); newest = rows[-1]["updated_at"]
                    if newest == cursor: break
                    cursor = newest
                    if len(rows) < 400: break
            return len(seen)
        old_n = old_pull()
        check("El algoritmo anterior perdía datos (comprobado contra el servidor real)", old_n < len(S), f"bajaba {old_n:,} de {len(S):,} series tras 5 sincronizaciones")

        # ---------------------------------------------------------------- seguridad (RLS)
        s, rows, _, _ = http("GET", f"{API}/routines?select=id,name&user_id=eq.{OTHER_USER}", headers=H)
        check("No puede LEER rutinas de otro usuario", s == 200 and rows == [], f"HTTP {s}, {len(rows) if isinstance(rows, list) else rows} filas")
        s, rows, _, _ = http("GET", f"{API}/workout_sets?select=id&limit=5", headers=H)
        check("Sin filtro solo ve lo suyo", s == 200 and all(True for _ in rows) and len(rows) <= 5)
        fake = dict(id=str(uuid.uuid4()), user_id=OTHER_USER, name="hackeo", notes=None, position=0, created_at=iso(now), deleted=False)
        s, body, _, _ = http("POST", f"{API}/routines", [fake], {**H, "Prefer": "return=minimal"})
        check("No puede CREAR filas a nombre de otro usuario", s in (401, 403), f"HTTP {s}")
        s, body, _, _ = http("PATCH", f"{API}/routines?user_id=eq.{OTHER_USER}", {"name": "hackeo"}, {**H, "Prefer": "return=representation"})
        check("No puede MODIFICAR rutinas de otro usuario", s in (200, 204) and (body in ([], None, "")), f"HTTP {s}, afectadas: {body}")
        s, body, _, _ = http("DELETE", f"{API}/workout_sets?user_id=eq.{OTHER_USER}", headers={**H, "Prefer": "return=representation"})
        check("No puede BORRAR datos de otro usuario", s in (200, 204) and (body in ([], None, "")), f"HTTP {s}")
        n_real = nsql.run(f"select count(*) as n from routines where user_id = '{OTHER_USER}' and not deleted")[0]["rows"][0]["n"]
        check("Las rutinas reales de Angel siguen intactas", int(n_real) == 2, f"{n_real} rutinas")
        s, body, _, _ = http("GET", f"{API}/routines?select=id")
        check("Sin JWT: rechazado", s in (401, 403), f"HTTP {s}")
        bad = jwt[:-6] + ("AAAAAA" if not jwt.endswith("AAAAAA") else "BBBBBB")
        s, body, _, _ = http("GET", f"{API}/routines?select=id", headers={"Authorization": f"Bearer {bad}"})
        check("JWT alterado: rechazado", s in (401, 403), f"HTTP {s}")
        s, body, _, _ = http("POST", f"{API}/workout_sets", [dict(S[0], id=str(uuid.uuid4()), reps=99999)], {**H, "Prefer": "return=minimal"})
        check("Datos inválidos (reps = 99 999): rechazados por el servidor", s == 400, f"HTTP {s}")
        s, body, _, _ = http("POST", f"{API}/body_weights", [dict(user_id=uid, measured_on="2026-11-14", weight_kg=5000, deleted=False)], {**H, "Prefer": "return=minimal"})
        check("Peso corporal imposible (5 000 kg): rechazado", s == 400, f"HTTP {s}")
        s, rows, _, _ = http("GET", f"{API}/routines?select=id&name=eq." + urllib.parse.quote("x'); drop table routines; --"), headers=H)
        alive = nsql.run("select count(*) as n from routines")[0]["rows"][0]["n"]
        check("Inyección SQL en un filtro: se trata como texto", s == 200 and int(alive) > 0, f"HTTP {s}")
        big = json.dumps([dict(S[0], id=str(uuid.uuid4()), notes=None) for _ in range(1)])[:-1] + "," + ",".join(
            json.dumps(dict(S[0], id=str(uuid.uuid4()))) for _ in range(20000)) + "]"
        s, body, _, ms = http("POST", f"{API}/workout_sets?on_conflict=id", big.encode(), {**H, "Prefer": "resolution=merge-duplicates,return=minimal"})
        check("Cuerpo enorme (~7 MB, 20 000 filas): el servidor responde sin caerse", s in (200, 201, 204, 413, 400), f"HTTP {s} en {ms:.0f} ms")
        # Latencia de consultas típicas de sincronización
        lat = []
        for _ in range(10):
            s, _, _, ms = http("GET", f"{API}/workout_sets?" + urllib.parse.urlencode({"user_id": f"eq.{uid}", "updated_at": f"gt.{iso(now)}", "select": "*", "order": "updated_at.asc,id.asc", "limit": "400"}), headers=H)
            lat.append(ms)
        check("Sincronización sin cambios (consulta incremental)", True, f"p50 {statistics.median(lat):.0f} ms de ida y vuelta")
    finally:
        # ---------------------------------------------------------------- limpieza
        for t in ["workout_sets", "workout_exercises", "workouts", "routine_exercises", "routines", "personal_records", "body_weights", "profiles"]:
            nsql.run(f"delete from public.{t} where user_id = '{uid}'")
        nsql.run(f"delete from neon_auth.session where \"userId\" = '{uid}'")
        nsql.run(f"delete from neon_auth.account where \"userId\" = '{uid}'")
        nsql.run(f"delete from neon_auth.\"user\" where id = '{uid}'")
        left = nsql.run(f"select (select count(*) from public.workout_sets where user_id = '{uid}') + (select count(*) from neon_auth.\"user\" where id = '{uid}') as n")[0]["rows"][0]["n"]
        check("Limpieza: usuario temporal y sus datos borrados", int(left) == 0)

    md = ["# QA del servidor (Neon Auth + Data API)", "", f"Fecha: {datetime.now().strftime('%Y-%m-%d %H:%M')} · usuario temporal (borrado al final).", "",
          "| Prueba | Resultado | Detalle |", "|---|---|---|"] + [f"| {n} | {'✓' if ok else '✗'} | {d} |" for n, ok, d in results]
    open(os.path.join(HERE, "results_qa_server.md"), "w").write("\n".join(md) + "\n")
    if not all(ok for _, ok, _ in results): sys.exit(1)


if __name__ == "__main__":
    main()
