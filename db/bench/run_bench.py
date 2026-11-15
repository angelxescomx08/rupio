#!/usr/bin/env python3
"""
Benchmark de consultas de Rupio con datos dummy.

  Local (PostgreSQL en localhost):  python3 run_bench.py --target local --generate --hu 20 --hw 1200 --nu 3000
  Neon  (SQL sobre HTTPS):          NEON_CS=postgresql://... python3 run_bench.py --target neon --schema bench --generate ...

Mide con EXPLAIN (ANALYZE, BUFFERS): el tiempo es el del servidor, sin la latencia de red.
Corre cada consulta N veces y reporta la mediana. Escribe resultados en results_<target>.json/.md
"""
import argparse, json, os, re, statistics, subprocess, sys, time, urllib.request
from datetime import date, timedelta

HERE = os.path.dirname(os.path.abspath(__file__))
MIG = os.path.join(HERE, "..", "migrations")

# ------------------------------------------------------------------ ejecutores
class Local:
    def __init__(self, dsn): self.dsn = dsn
    def run(self, sql, tx=False):
        # psql -c manda todo como una sola consulta simple = una transacción implícita
        # (SET LOCAL aplica) y solo imprime el último resultado.
        r = subprocess.run(["psql", self.dsn, "-X", "-q", "-A", "-t", "-F", "\t", "-v", "ON_ERROR_STOP=1", "-c", sql],
                           capture_output=True, text=True)
        if r.returncode != 0: raise RuntimeError(r.stderr[:2000])
        return r.stdout.strip()
    def run_file(self, sql):
        r = subprocess.run(["psql", self.dsn, "-X", "-q", "-v", "ON_ERROR_STOP=1"], input=sql, capture_output=True, text=True)
        if r.returncode != 0: raise RuntimeError(r.stderr[:2000])

class Neon:
    def __init__(self, cs):
        self.cs = cs
        self.host = re.search(r"@([^/]+)/", cs).group(1)
    def _post(self, body, timeout=900):
        req = urllib.request.Request(f"https://{self.host}/sql", data=json.dumps(body).encode(), method="POST",
              headers={"Neon-Connection-String": self.cs, "Content-Type": "application/json",
                       "Neon-Raw-Text-Output": "true", "Neon-Array-Mode": "true"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r: return json.loads(r.read())
        except urllib.error.HTTPError as e:
            raise RuntimeError(e.read().decode()[:2000])
    def run(self, sql, tx=False):
        stmts = split_sql(sql)
        if len(stmts) == 1 and not tx:
            res = self._post({"query": stmts[0], "params": []})
        else:
            res = self._post({"queries": [{"query": s, "params": []} for s in stmts]})["results"][-1]
        rows = res.get("rows") or []
        return "\n".join("\t".join("" if v is None else str(v) for v in row) for row in rows)
    def run_file(self, sql):
        for s in split_sql(sql):
            self._post({"query": s, "params": []})

def split_sql(text):
    out, buf, i, n, dollar, sq = [], [], 0, len(text), None, False
    while i < n:
        c = text[i]
        if dollar:
            if text.startswith(dollar, i): buf.append(dollar); i += len(dollar); dollar = None; continue
            buf.append(c); i += 1; continue
        if sq:
            buf.append(c); i += 1
            if c == "'": sq = False
            continue
        if text.startswith("--", i):
            j = text.find("\n", i); i = n if j < 0 else j; continue
        m = re.match(r"\$[A-Za-z_]*\$", text[i:])
        if m: dollar = m.group(0); buf.append(dollar); i += len(dollar); continue
        if c == "'": sq = True; buf.append(c); i += 1; continue
        if c == ";":
            s = "".join(buf).strip()
            if s: out.append(s)
            buf = []; i += 1; continue
        buf.append(c); i += 1
    s = "".join(buf).strip()
    if s: out.append(s)
    return out

# ------------------------------------------------------------------ preparación
def schema_sql(schema):
    s = open(os.path.join(MIG, "001_schema.sql")).read()
    seed = open(os.path.join(MIG, "002_seed_exercises.sql")).read().replace("insert into exercises", "insert into public.exercises")
    if schema != "public":
        s = s.replace("public.", f"{schema}.")
        seed = seed.replace("public.", f"{schema}.")
        s = f"create schema if not exists {schema};\n" + s
    return s, seed

def index_statements(schema):
    s, _ = schema_sql(schema)
    return [st for st in split_sql(s) if st.lower().startswith("create index")]

def index_names(schema):
    return [re.search(r"create index if not exists (\w+)", st).group(1) for st in index_statements(schema)]

def generate(db, schema, a):
    s, seed = schema_sql(schema)
    t = time.time()
    db.run_file(s); db.run_file(seed)
    for name in index_names(schema):
        db.run(f"drop index if exists {schema}.{name}")
    tpl = open(os.path.join(HERE, "generate_data.sql")).read()
    for k, v in {"S": schema, "HU": a.hu, "HW": a.hw, "NU": a.nu, "NMIN": a.nmin, "NMAX": a.nmax}.items():
        tpl = tpl.replace("{{%s}}" % k, str(v))
    for st in split_sql(tpl):
        t0 = time.time(); db.run(st); print(f"  {time.time()-t0:6.1f}s  {st.splitlines()[0][:90]}")
    for st in index_statements(schema):
        t0 = time.time(); db.run(st); print(f"  {time.time()-t0:6.1f}s  {st.splitlines()[0][:90]}")
    for tb in ["workouts", "workout_exercises", "workout_sets", "exercises"]:
        db.run(f"vacuum (analyze) {schema}.{tb}")
    print(f"Datos generados en {time.time()-t:.0f}s")

# ------------------------------------------------------------------ consultas
def queries(S):
    base_ex = f"""select performed_on, weight_kg, reps, set_type from {S}.workout_sets
  where user_id = :uid and exercise_id = :ex and completed and not deleted
  order by performed_on desc limit 40"""
    rls_ex = f"""select performed_on, weight_kg, reps, set_type from {S}.workout_sets
  where exercise_id = :ex and completed and not deleted
  order by performed_on desc limit 40"""
    return [
        ("Q01", "Historial: primera página (20 entrenamientos)", f"""select id, name, started_at, duration_sec, total_volume_kg, total_sets from {S}.workouts
  where user_id = :uid and not deleted order by local_date desc, started_at desc limit 20""", None),
        ("Q02", "Historial: página profunda con OFFSET 1000 (anti-patrón)", f"""select id, name, started_at, duration_sec, total_volume_kg from {S}.workouts
  where user_id = :uid and not deleted order by local_date desc, started_at desc offset :deep limit 20""", None),
        ("Q03", "Historial: misma página con keyset (recomendado)", f"""select id, name, started_at, duration_sec, total_volume_kg from {S}.workouts
  where user_id = :uid and not deleted and (local_date, started_at) < (:kd, :ks)
  order by local_date desc, started_at desc limit 20""", None),
        ("Q04", "Detalle de un entrenamiento (ejercicios + series)", f"""select we.position, we.exercise_id, s.position, s.weight_kg, s.reps, s.set_type, s.completed
  from {S}.workout_exercises we join {S}.workout_sets s on s.workout_exercise_id = we.id
  where we.workout_id = :wid and not we.deleted and not s.deleted order by we.position, s.position""", None),
        ("Q05", "Últimas series de un ejercicio (motor de progresión)", base_ex, None),
        ("Q06", "Récords del ejercicio: peso máx y 1RM estimado", f"""select max(weight_kg), max(weight_kg * (1 + reps / 30.0)), max(reps) from {S}.workout_sets
  where user_id = :uid and exercise_id = :ex and completed and not deleted and set_type <> 1""", None),
        ("Q07", "Curva de 1RM estimado por sesión (último año)", f"""select performed_on, max(weight_kg * (1 + reps / 30.0)) from {S}.workout_sets
  where user_id = :uid and exercise_id = :ex and completed and not deleted and set_type <> 1 and performed_on >= :d1y
  group by performed_on order by performed_on""", None),
        ("Q08", "Volumen semanal · últimos 3 meses", f"""select date_trunc('week', local_date)::date, sum(total_volume_kg), sum(total_sets), count(*) from {S}.workouts
  where user_id = :uid and not deleted and local_date between :d3m and :today group by 1 order by 1""", None),
        ("Q09", "Volumen mensual · último año", f"""select date_trunc('month', local_date)::date, sum(total_volume_kg), sum(total_sets), count(*) from {S}.workouts
  where user_id = :uid and not deleted and local_date between :d1y and :today group by 1 order by 1""", None),
        ("Q10", "Volumen anual · histórico completo", f"""select date_trunc('year', local_date)::date, sum(total_volume_kg), sum(total_sets), count(*) from {S}.workouts
  where user_id = :uid and not deleted and local_date between '1900-01-01' and :today group by 1 order by 1""", None),
        ("Q11", "Volumen histórico calculado desde series (sin desnormalizar)", f"""select date_trunc('year', performed_on)::date, sum(volume_kg), count(*) from {S}.workout_sets
  where user_id = :uid and completed and not deleted and performed_on between '1900-01-01' and :today group by 1 order by 1""", None),
        ("Q12", "Series por grupo muscular · últimas 4 semanas", f"""select e.primary_muscle, count(*), sum(s.volume_kg) from {S}.workout_sets s join {S}.exercises e on e.id = s.exercise_id
  where s.user_id = :uid and s.completed and not s.deleted and s.set_type <> 1 and s.performed_on between :d4w and :today
  group by 1 order by 2 desc""", None),
        ("Q13", "Sync incremental (series cambiadas en 7 días)", f"""select * from {S}.workout_sets
  where user_id = :uid and updated_at > now() - interval '7 days' order by updated_at limit 1000""", None),
        ("Q14", "Q05 sin índices (seq scan forzado, anti-patrón)", base_ex,
         "set local enable_indexscan = off; set local enable_indexonlyscan = off; set local enable_bitmapscan = off;"),
        ("Q15", "Q05 con RLS · auth.uid() sin envolver, sin filtro", rls_ex, "RLS_BARE"),
        ("Q16", "Q05 con RLS · (select auth.uid()), sin filtro", rls_ex, "RLS_WRAPPED"),
        ("Q17", "Q05 con RLS · (select auth.uid()) + filtro user_id", base_ex, "RLS_WRAPPED"),
    ]

def rls_setup(db, S):
    db.run(f"""do $$ begin if not exists (select 1 from pg_roles where rolname = 'rupio_bench_rls') then
               create role rupio_bench_rls nologin; end if; end $$""")
    # PG16+: quien crea el rol no puede hacer SET ROLE sin membresía explícita (Neon lo exige).
    db.run("grant rupio_bench_rls to current_user")
    db.run(f"grant usage on schema {S} to rupio_bench_rls")
    db.run(f"grant select on {S}.workout_sets to rupio_bench_rls")
    # plpgsql (no inlinable) para parecerse a auth.uid() de pg_session_jwt (función C)
    db.run(f"""create or replace function {S}.bench_uid() returns uuid language plpgsql stable as $f$
               begin return nullif(current_setting('request.jwt.claims', true)::json->>'sub', '')::uuid; end $f$""")
    db.run(f"grant execute on function {S}.bench_uid() to rupio_bench_rls")
    db.run(f"alter table {S}.workout_sets enable row level security")

def rls_policy(db, S, wrapped):
    db.run(f"drop policy if exists bench_p on {S}.workout_sets")
    expr = f"(select {S}.bench_uid())" if wrapped else f"{S}.bench_uid()"
    db.run(f"create policy bench_p on {S}.workout_sets for select to rupio_bench_rls using (user_id = {expr})")

def rls_teardown(db, S):
    db.run(f"drop policy if exists bench_p on {S}.workout_sets")
    db.run(f"alter table {S}.workout_sets disable row level security")
    db.run(f"drop function if exists {S}.bench_uid()")
    db.run(f"revoke all on {S}.workout_sets from rupio_bench_rls")
    db.run(f"revoke all on schema {S} from rupio_bench_rls")
    db.run("drop role if exists rupio_bench_rls")

def lit(v): return "'" + str(v).replace("'", "''") + "'"

def walk(node, acc):
    acc["types"].add(node.get("Node Type"))
    if node.get("Index Name"): acc["indexes"].add(node["Index Name"])
    if "Heap Fetches" in node: acc["heap"] += node["Heap Fetches"]
    for ch in node.get("Plans", []): walk(ch, acc)

def bench(db, S, runs):
    p = {}
    def one(sql): return db.run(sql).split("\n")[0].split("\t")
    for kind, cond in [("intensivo", "heavy"), ("normal", "not heavy")]:
        uid, nw = one(f"""select user_id, n_workouts from {S}.bench_users where {cond}
                          order by abs(n_workouts - (select percentile_disc(0.5) within group (order by n_workouts) from {S}.bench_users where {cond})) limit 1""")
        ex = one(f"select exercise_id from {S}.workout_exercises where user_id = {lit(uid)} group by 1 order by count(*) desc limit 1")[0]
        wid = one(f"select id from {S}.workouts where user_id = {lit(uid)} order by local_date desc limit 1")[0]
        deep = min(1000, max(0, int(nw) - 25))
        kd, ks = one(f"""select local_date, started_at from {S}.workouts where user_id = {lit(uid)} and not deleted
                         order by local_date desc, started_at desc offset {max(deep-1,0)} limit 1""")
        sets = one(f"select count(*) from {S}.workout_sets where user_id = {lit(uid)}")[0]
        p[kind] = dict(uid=uid, ex=ex, wid=wid, deep=deep, kd=kd, ks=ks, n_workouts=nw, n_sets=sets)
    today = date.today()
    totals = dict(zip(["workouts", "workout_exercises", "workout_sets", "users"],
                      one(f"""select (select count(*) from {S}.workouts), (select count(*) from {S}.workout_exercises),
                                     (select count(*) from {S}.workout_sets), (select count(*) from {S}.bench_users)""")))
    sizes = db.run(f"""select c.relname, pg_size_pretty(pg_relation_size(c.oid)), pg_relation_size(c.oid)
                       from pg_class c join pg_namespace n on n.oid = c.relnamespace
                       where n.nspname = '{S}' and c.relkind in ('r','i') and pg_relation_size(c.oid) > 1000000
                       order by 3 desc""")
    rls_setup(db, S)
    results = []
    try:
        current_policy = None
        for qid, label, sql, prefix in queries(S):
            for kind in ("intensivo", "normal"):
                v = p[kind]
                q = sql
                for k, val in [(":uid", lit(v["uid"])), (":ex", lit(v["ex"])), (":wid", lit(v["wid"])), (":deep", v["deep"]),
                               (":kd", lit(v["kd"])), (":ks", lit(v["ks"])), (":today", lit(today)),
                               (":d3m", lit(today - timedelta(days=91))), (":d1y", lit(today - timedelta(days=365))),
                               (":d4w", lit(today - timedelta(days=28)))]:
                    q = q.replace(k, str(val))
                pre = ""
                if prefix and prefix.startswith("RLS"):
                    want = prefix == "RLS_WRAPPED"
                    if current_policy != want: rls_policy(db, S, want); current_policy = want
                    pre = f"set local role rupio_bench_rls; set local request.jwt.claims = {lit(json.dumps({'sub': v['uid']}))};"
                elif prefix:
                    pre = prefix
                times, plan = [], None
                for _ in range(runs):
                    out = db.run(pre + "\nexplain (analyze, buffers, format json) " + q, tx=True)
                    plan = json.loads(out)[0]
                    times.append(plan["Execution Time"] + plan["Planning Time"])
                acc = {"types": set(), "indexes": set(), "heap": 0}
                walk(plan["Plan"], acc)
                results.append(dict(id=qid, label=label, user=kind, ms=round(statistics.median(times), 3),
                                    best=round(min(times), 3), rows=plan["Plan"].get("Actual Rows"),
                                    nodes=sorted(t for t in acc["types"] if t), indexes=sorted(acc["indexes"]),
                                    heap_fetches=acc["heap"]))
                r = results[-1]
                print(f"{qid} {kind:9s} {r['ms']:9.3f} ms  filas={r['rows']}  {','.join(r['nodes'])}  {','.join(r['indexes'])}")
    finally:
        rls_teardown(db, S)
    return dict(params=p, totals=totals, sizes=sizes, results=results, runs=runs)

def write_report(target, data):
    md = [f"# Resultados · {target}", "", f"Mediana de {data['runs']} ejecuciones (planning + execution, medido en el servidor con EXPLAIN ANALYZE).", "",
          "Filas totales: " + ", ".join(f"{k}={int(v):,}" for k, v in data["totals"].items()), ""]
    for k, v in data["params"].items():
        md.append(f"- Usuario {k}: {int(v['n_workouts']):,} entrenamientos, {int(v['n_sets']):,} series")
    md += ["", "| ID | Consulta | Usuario intensivo (ms) | Usuario normal (ms) | Plan |", "|---|---|---:|---:|---|"]
    by = {}
    for r in data["results"]: by.setdefault(r["id"], {})[r["user"]] = r
    for qid, d in by.items():
        h, n = d["intensivo"], d["normal"]
        plan = ", ".join(h["nodes"]) + (f" ({', '.join(h['indexes'])})" if h["indexes"] else "")
        md.append(f"| {qid} | {h['label']} | {h['ms']:.3f} | {n['ms']:.3f} | {plan} |")
    md += ["", "Tamaños (tablas e índices > 1 MB):", "", "```", data["sizes"], "```"]
    open(os.path.join(HERE, f"results_{target}.md"), "w").write("\n".join(md) + "\n")
    json.dump(data, open(os.path.join(HERE, f"results_{target}.json"), "w"), indent=1, default=str)

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", choices=["local", "neon"], required=True)
    ap.add_argument("--schema", default="public")
    ap.add_argument("--dsn", default="postgresql://postgres@localhost:5433/rupio")
    ap.add_argument("--generate", action="store_true")
    ap.add_argument("--hu", type=int, default=20); ap.add_argument("--hw", type=int, default=1200)
    ap.add_argument("--nu", type=int, default=3000); ap.add_argument("--nmin", type=int, default=10); ap.add_argument("--nmax", type=int, default=200)
    ap.add_argument("--runs", type=int, default=7)
    ap.add_argument("--label", default=None)
    ap.add_argument("--drop-after", action="store_true", help="borra el esquema de prueba al terminar")
    a = ap.parse_args()
    db = Local(a.dsn) if a.target == "local" else Neon(os.environ["NEON_CS"])
    if a.generate: generate(db, a.schema, a)
    data = bench(db, a.schema, a.runs)
    write_report(a.label or a.target, data)
    if a.drop_after and a.schema != "public":
        db.run(f"drop schema {a.schema} cascade"); print("Esquema de prueba borrado.")
