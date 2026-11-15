#!/usr/bin/env python3
"""
Benchmark del registro de peso corporal.

  python3 bench_body_weight.py sqlite   → teléfono (SQLite, esquema idéntico al de Room v3)
  python3 bench_body_weight.py neon     → servidor (Postgres en Neon, con RLS como la Data API)

Datos dummy: SQLite 20 años diarios de 1 usuario + 49 usuarios más (365 000 filas);
Neon 1 000 usuarios × 2 años (730 000 filas) en un esquema temporal que se borra al final.
"""
import json, os, sqlite3, statistics, sys, time, random

OUT = os.path.dirname(os.path.abspath(__file__))

# ------------------------------------------------------------------ SQLite (teléfono)
ROOM_DDL = [
    "CREATE TABLE IF NOT EXISTS `body_weights` (`user_id` TEXT NOT NULL, `measured_on` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, `updated_at` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `measured_on`))",
    "CREATE INDEX IF NOT EXISTS `index_body_weights_dirty` ON `body_weights` (`dirty`)",
]
SERIES = """SELECT MIN(measured_on) AS day, AVG(weight_kg) AS kg, COUNT(*) AS n FROM body_weights
            WHERE user_id = ? AND measured_on BETWEEN ? AND ? AND deleted = 0
            GROUP BY (measured_on - ?) / ? ORDER BY day"""
PAGE = "SELECT * FROM body_weights WHERE user_id = ? AND deleted = 0 AND measured_on < ? ORDER BY measured_on DESC LIMIT 60"
LATEST = "SELECT * FROM body_weights WHERE user_id = ? AND deleted = 0 ORDER BY measured_on DESC LIMIT 1"
UPSERT = """INSERT INTO body_weights (user_id, measured_on, weight_kg, updated_at, deleted, dirty) VALUES (?,?,?,?,0,1)
            ON CONFLICT(user_id, measured_on) DO UPDATE SET weight_kg = excluded.weight_kg, updated_at = excluded.updated_at, deleted = 0, dirty = 1"""


def bucket_for(days):
    return 1 if days <= 150 else 7 if days <= 1000 else max(30, days // 150)


def timeit(fn, n=300):
    for _ in range(20): fn()
    xs = []
    for _ in range(n):
        t = time.perf_counter(); fn(); xs.append((time.perf_counter() - t) * 1000)
    xs.sort()
    return {"p50_ms": round(statistics.median(xs), 4), "p95_ms": round(xs[int(len(xs) * 0.95)], 4), "runs": n}


def sqlite_bench():
    path = "/tmp/bw_bench.db"
    if os.path.exists(path): os.remove(path)
    db = sqlite3.connect(path)
    db.execute("PRAGMA journal_mode=WAL")
    for d in ROOM_DDL: db.execute(d)
    today = 20_406  # 14-nov-2025 + 1 año ≈ epoch day; solo importa que sea fijo
    me = "01a1dbd2-0000-7000-8000-000000000000"
    rnd = random.Random(7)
    rows = []
    users = [me] + [f"01a1dbd2-0000-7000-8000-{i:012d}" for i in range(1, 50)]
    for u in users:
        w = rnd.uniform(60, 110)
        days = 7300 if u == me else 7300
        for d in range(today - days + 1, today + 1):
            w += rnd.gauss(-0.002, 0.25)
            if rnd.random() < 0.15: continue  # días sin registrar
            rows.append((u, d, round(w, 2), d * 86_400_000, 0, 0))
    t = time.perf_counter()
    db.executemany("INSERT INTO body_weights VALUES (?,?,?,?,?,?)", rows); db.commit()
    load_s = time.perf_counter() - t
    db.execute("ANALYZE")
    total = db.execute("select count(*) from body_weights").fetchone()[0]
    mine = db.execute("select count(*) from body_weights where user_id=?", (me,)).fetchone()[0]

    res = {"rows_total": total, "rows_user": mine, "load_s": round(load_s, 2), "queries": {}}
    def plan(sql, args):
        return [r[3] for r in db.execute("EXPLAIN QUERY PLAN " + sql, args)]
    cases = {
        "Gráfica 1 semana (diario)": (SERIES, (me, today - 6, today, today - 6, 1)),
        "Gráfica 1 mes (diario)": (SERIES, (me, today - 29, today, today - 29, 1)),
        "Gráfica 3 meses (diario)": (SERIES, (me, today - 89, today, today - 89, 1)),
        "Gráfica 1 año (semanal)": (SERIES, (me, today - 364, today, today - 364, bucket_for(364))),
        "Gráfica histórico 20 años (agrupada)": (SERIES, (me, today - 7299, today, today - 7299, bucket_for(7299))),
        "Historial: página de 60 (keyset)": (PAGE, (me, today - 3000)),
        "Último registro": (LATEST, (me,)),
    }
    for name, (sql, args) in cases.items():
        r = timeit(lambda: db.execute(sql, args).fetchall())
        r["points"] = len(db.execute(sql, args).fetchall())
        r["plan"] = plan(sql, args)
        res["queries"][name] = r
    k = [0]
    def up():
        k[0] += 1
        db.execute(UPSERT, (me, today - (k[0] % 30), 80 + (k[0] % 10) / 10, k[0])); db.commit()
    r = timeit(up, 300); r["plan"] = ["UPSERT por clave primaria (una fila)"]
    res["queries"]["Registrar/actualizar peso (con commit)"] = r
    return res


# ------------------------------------------------------------------ Neon (servidor)
def neon_bench():
    sys.path.insert(0, "/home/claude/tools")
    import nsql
    S = "bw_bench"
    def q(sql):
        r, _ = nsql.run(sql)
        if isinstance(r, dict) and r.get("error"): raise RuntimeError(r["error"][:400])
        return r
    def rows(r):
        return r.get("rows", []) if isinstance(r, dict) else []
    q(f"drop schema if exists {S} cascade")
    q(f"create schema {S}")
    q(f"""create table {S}.body_weights (user_id uuid not null, updated_at timestamptz not null default now(),
          measured_on date not null, weight_kg real not null check (weight_kg between 20 and 400),
          deleted boolean not null default false, primary key (user_id, measured_on) include (weight_kg, deleted))""")
    q(f"create index body_weights_user_sync_idx on {S}.body_weights (user_id, updated_at)")
    t = time.perf_counter()
    q(f"""insert into {S}.body_weights (user_id, updated_at, measured_on, weight_kg)
          select u.id, d::timestamptz + interval '7 hours', d::date, 70 + (abs(hashtext(u.id::text)) % 40) + sin(extract(doy from d)) * 1.5
          from (select ('00000000-0000-7000-8000-' || lpad(g::text, 12, '0'))::uuid id from generate_series(1, 1000) g) u
          cross join generate_series(date '2024-11-15', date '2026-11-14', interval '1 day') d""")
    load_s = time.perf_counter() - t
    q(f"vacuum (analyze) {S}.body_weights")  # autovacuum lo hace solo en producción; aquí se fuerza
    total = int(rows(q(f"select count(*) as n from {S}.body_weights"))[0]["n"])
    size = rows(q(f"select pg_size_pretty(pg_total_relation_size('{S}.body_weights')) as s"))[0]["s"]

    # Rol con RLS igual que la Data API: la política filtra por el "sub" del JWT.
    q("""do $$ begin if not exists (select 1 from pg_roles where rolname = 'rupio_bench_rls') then
         create role rupio_bench_rls nologin; end if; end $$""")
    q("grant rupio_bench_rls to current_user")
    q(f"grant usage on schema {S} to rupio_bench_rls")
    q(f"grant select, insert, update on {S}.body_weights to rupio_bench_rls")
    q(f"""create or replace function {S}.uid() returns uuid language sql stable as
          $f$ select nullif(current_setting('request.jwt.claims', true)::json->>'sub', '')::uuid $f$""")
    q(f"grant execute on function {S}.uid() to rupio_bench_rls")
    q(f"alter table {S}.body_weights enable row level security")
    q(f"create policy p_sel on {S}.body_weights for select to rupio_bench_rls using (user_id = (select {S}.uid()))")
    q(f"create policy p_ins on {S}.body_weights for insert to rupio_bench_rls with check (user_id = (select {S}.uid()))")
    q(f"create policy p_upd on {S}.body_weights for update to rupio_bench_rls using (user_id = (select {S}.uid())) with check (user_id = (select {S}.uid()))")

    uid = "00000000-0000-7000-8000-000000000500"
    cases = {
        "Gráfica 3 meses (Data API: rango por fecha)":
            f"select measured_on, weight_kg from {S}.body_weights where user_id = '{uid}' and measured_on between date '2026-08-17' and date '2026-11-14' and not deleted order by measured_on",
        "Gráfica 1 año agrupada por semana":
            f"select min(measured_on) d, avg(weight_kg) kg from {S}.body_weights where user_id = '{uid}' and measured_on between date '2025-11-15' and date '2026-11-14' and not deleted group by (measured_on - date '2025-11-15') / 7 order by d",
        "Sincronización: cambios desde ayer":
            f"select * from {S}.body_weights where user_id = '{uid}' and updated_at > now() - interval '1 day' order by updated_at limit 400",
        "Primera sincronización: lote de 400":
            f"select * from {S}.body_weights where user_id = '{uid}' and updated_at > 'epoch' order by updated_at limit 400",
        "Upsert de un día (on conflict)":
            f"insert into {S}.body_weights (user_id, measured_on, weight_kg) values ('{uid}', date '2026-11-14', 81.4) on conflict (user_id, measured_on) do update set weight_kg = excluded.weight_kg, deleted = false",
    }
    out = {"rows_total": total, "table_size": size, "load_s": round(load_s, 1), "queries": {}}
    for name, sql in cases.items():
        times, plan_lines = [], None
        for i in range(7):
            res = nsql.post({"queries": [{"query": x, "params": []} for x in [
                "set local role rupio_bench_rls",
                f"set local request.jwt.claims = '{json.dumps({'sub': uid})}'",
                f"explain (analyze, buffers, format json) {sql}",
            ]]})
            if "results" not in res: raise RuntimeError(json.dumps(res)[:400])
            res = res["results"]
            p = res[-1]["rows"][0]["QUERY PLAN"]
            p = json.loads(p) if isinstance(p, str) else p
            times.append(p[0]["Execution Time"] + p[0].get("Planning Time", 0))
            if plan_lines is None:
                plan_lines = []
                def walk(n, depth=0):
                    plan_lines.append("  " * depth + n["Node Type"] + (f" using {n['Index Name']}" if "Index Name" in n else "")
                                      + f" (filas {n.get('Actual Rows')}, bloques {n.get('Shared Hit Blocks', 0) + n.get('Shared Read Blocks', 0)})")
                    for c in n.get("Plans", []): walk(c, depth + 1)
                walk(p[0]["Plan"])
        times.sort()
        out["queries"][name] = {"p50_ms": round(times[len(times) // 2], 3), "best_ms": round(times[0], 3), "plan": plan_lines}
    q(f"drop schema {S} cascade")
    q("drop role if exists rupio_bench_rls")
    return out


def write_md(kind, res):
    path = os.path.join(OUT, f"results_body_weight_{kind}.md")
    with open(path, "w") as f:
        if kind == "sqlite":
            f.write(f"# Peso corporal · SQLite (teléfono)\n\n{res['rows_total']:,} filas en total; usuario medido: {res['rows_user']:,} registros (20 años, ~85 % de los días). Carga: {res['load_s']} s.\n\n")
            f.write("| Consulta | Puntos | p50 (ms) | p95 (ms) | Plan |\n|---|---:|---:|---:|---|\n")
            for k, v in res["queries"].items():
                f.write(f"| {k} | {v.get('points', '')} | {v['p50_ms']} | {v['p95_ms']} | {'; '.join(v['plan'])} |\n")
        else:
            f.write(f"# Peso corporal · Postgres en Neon (con RLS)\n\n{res['rows_total']:,} filas (1 000 usuarios × 2 años), tabla + índices: {res['table_size']}. Carga: {res['load_s']} s.\n\nTiempo = planeación + ejecución medido por Postgres (sin red), mediana de 7.\n\n")
            f.write("| Consulta | p50 (ms) | Mejor (ms) | Plan |\n|---|---:|---:|---|\n")
            for k, v in res["queries"].items():
                f.write(f"| {k} | {v['p50_ms']} | {v['best_ms']} | {' → '.join(l.strip() for l in v['plan'])} |\n")
    with open(path.replace(".md", ".json"), "w") as f: json.dump(res, f, ensure_ascii=False, indent=2)
    print(open(path).read())


if __name__ == "__main__":
    kind = sys.argv[1] if len(sys.argv) > 1 else "sqlite"
    write_md(kind, sqlite_bench() if kind == "sqlite" else neon_bench())
