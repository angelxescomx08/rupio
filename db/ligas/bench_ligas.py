#!/usr/bin/env python3
"""
Benchmark del muro y de las ligas semanales de Rupio (PostgreSQL 16 local, NUNCA Neon).

  1. Base (desde db/ligas):
       createdb rupio_bench
       psql -d rupio_bench -f setup_local.sql -f ../migrations/001_schema.sql -f ../migrations/007_supporters_wall.sql -f datos_base.sql
  2. Correr: pip install "psycopg[binary]" && python3 bench_ligas.py --dsn "host=/tmp user=postgres dbname=rupio_bench"

Mide primero el muro como está hoy (007), luego aplica ../migrations/008_ligas.sql + datos_ligas.sql y mide todo.
Cada consulta: 1 calentamiento + N ejecuciones con EXPLAIN (ANALYZE, BUFFERS) dentro de una transacción que se
deshace (las escrituras no se quedan). Tiempo = planning + execution medido en el servidor (sin red).
Escribe results_ligas.json y results_ligas.md.
"""
import argparse, datetime, json, os, statistics, subprocess
import psycopg

HERE = os.path.dirname(os.path.abspath(__file__))
N = 9
RESULTS = []


def walk(node, out):
    out["nodes"].add(node["Node Type"])
    if "Index Name" in node:
        out["idx"].add(node["Index Name"])
    for c in node.get("Plans", []):
        walk(c, out)


def measure(conn, rid, label, sql, params=None, uid=None, setup=(), n=N, group=""):
    times, info, trig = [], {"nodes": set(), "idx": set()}, 0.0
    for i in range(n + 1):
        with conn.transaction(force_rollback=True):
            cur = conn.cursor()
            if uid:
                cur.execute("select set_config('test.uid', %s, true)", (str(uid),))
            for s in setup:
                cur.execute(s)
            cur.execute("explain (analyze, buffers, format json) " + sql, params)
            j = cur.fetchone()[0][0]
            if i == 0:
                continue  # calentamiento
            times.append(j["Planning Time"] + j["Execution Time"])
            walk(j["Plan"], info)
            trig = sum(t.get("Time", 0) for t in j.get("Triggers", []))
    r = {"id": rid, "group": group, "label": label, "median_ms": round(statistics.median(times), 3),
         "max_ms": round(max(times), 3), "nodes": sorted(info["nodes"]), "indexes": sorted(info["idx"]),
         "trigger_ms": round(trig, 3)}
    RESULTS.append(r)
    print(f"{rid:5} {r['median_ms']:9.3f} ms  (máx {r['max_ms']:.3f})  {label}")
    return r


def one(conn, sql, params=None):
    with conn.cursor() as c:
        c.execute(sql, params)
        return c.fetchone()


def run_file(conn, name, dsn):
    conn.commit()
    r = subprocess.run(["psql", dsn, "-X", "-q", "-v", "ON_ERROR_STOP=1", "-f", os.path.join(HERE, name)], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr[-2000:])


def wall_users(conn, order):
    """Usuarios del muro en el lugar 1, la mitad y el último (orden actual del muro)."""
    total = one(conn, "select count(*) from wall_entries where not hidden and not blocked")[0]
    pick = lambda off: one(conn, f"select user_id from wall_entries where not hidden and not blocked order by {order} offset %s limit 1", (off,))[0]
    return total, {"primero": pick(0), "mitad": pick(total // 2), "último": pick(total - 1)}


def bench_wall(conn, phase, order):
    for size in ("100k", "1k"):
        if size == "1k":  # tamaño realista: solo 1,000 visibles
            with conn.cursor() as c:
                c.execute("set session_replication_role = replica")   # sin triggers mientras se prepara el escenario
                c.execute(f"update wall_entries set hidden = true where user_id not in (select user_id from wall_entries order by {order} limit 1000)")
                c.execute("set session_replication_role = origin")
                c.execute("select to_regclass('public.wall_counts') is not null")
                if c.fetchone()[0]:
                    c.execute("update wall_counts set visible = (select count(*) from wall_entries where not hidden and not blocked)")
            conn.commit()
            conn.autocommit = True
            conn.execute("vacuum analyze wall_entries")
            conn.autocommit = False
        total, users = wall_users(conn, order)
        g = f"Muro {phase} · {total:,} visibles"
        tag = ("A" if phase == "hoy (007)" else "B") + ("" if size == "100k" else "r")
        measure(conn, f"M1{tag}", "Lista del muro (300) + tu lugar, siendo el último", "select rupio_wall()", uid=users["último"], group=g)
        for k, u in users.items():
            measure(conn, f"M2{tag}{k[0]}", f"Tu lugar y lo que te falta · {k}", "select rupio_wall_me_json(%s)", (u,), group=g)
        if size == "1k":
            with conn.cursor() as c:
                c.execute("set session_replication_role = replica")
                c.execute("update wall_entries set hidden = false")
                c.execute("set session_replication_role = origin")
                c.execute("select to_regclass('public.wall_counts') is not null")
                if c.fetchone()[0]:
                    c.execute("update wall_counts set visible = (select count(*) from wall_entries where not hidden and not blocked)")
            conn.commit()
            conn.autocommit = True
            conn.execute("vacuum analyze wall_entries")
            conn.autocommit = False


def bench_leagues(conn):
    wk = one(conn, "select rupio_league_week(current_date)")[0]
    g = "Ligas · lectura"
    mid = one(conn, """select s.user_id from league_scores s join league_groups gr on gr.id = s.group_id
                       where s.week = %s and gr.tier = 3 and gr.members = 30 order by s.user_id limit 1""", (wk,))[0]
    bottom = one(conn, "select user_id from league_scores where week = %s order by volume_kg, first_at desc limit 1", (wk,))[0]
    gid = one(conn, "select group_id from league_scores where user_id = %s and week = %s", (mid, wk))[0]
    deep = one(conn, "select volume_kg, first_at from league_scores where week = %s order by volume_kg desc, first_at offset 4999 limit 1", (wk,))
    members_week = one(conn, "select count(*) from league_scores where week = %s", (wk,))[0]

    measure(conn, "L1", "Tu liga completa (rupio_league: grupo de 30, zonas, apodos e insignias)", "select rupio_league()", uid=mid, group=g)
    core = "select user_id, volume_kg, workouts from league_scores where group_id = %s order by volume_kg desc, first_at"
    measure(conn, "L2", "Solo la tabla del grupo (30 filas en orden)", core, (gid,), group=g)
    measure(conn, "L2x", "L2 sin league_scores_group_idx (anti-patrón)", core, (gid,), group=g,
            setup=["drop index league_scores_group_idx"])
    measure(conn, "L3", f"Top global de la semana · primera página (50 de {members_week:,})", "select rupio_league_top()", uid=mid, group=g)
    measure(conn, "L4", "Top global · página después del lugar 5,000 (keyset)", "select rupio_league_top(%s, %s)", deep, uid=mid, group=g)
    off = """select s.user_id, s.volume_kg from league_scores s where s.week = %s order by s.volume_kg desc, s.first_at offset 5000 limit 50"""
    measure(conn, "L4x", "La misma página con OFFSET 5000 (anti-patrón)", off, (wk,), group=g)
    measure(conn, "L5", "Tu lugar global y tu porcentaje · siendo el último", "select rupio_league_me_global()", uid=bottom, group=g)
    measure(conn, "L5x", "L5 sin league_scores_week_idx (anti-patrón)", "select rupio_league_me_global()", uid=bottom, group=g,
            setup=["drop index league_scores_week_idx"])

    # Escritura: primer entreno de la semana (liga nueva + grupo) para alguien que entrenó la semana pasada
    g = "Ligas · escritura"
    fresh = one(conn, """select m.user_id from league_members m
                         where exists (select 1 from league_scores s where s.user_id = m.user_id and s.week = %s)
                           and not exists (select 1 from league_scores s where s.user_id = m.user_id and s.week = %s)
                         order by m.user_id limit 1""", (wk - datetime.timedelta(days=7), wk))[0]
    measure(conn, "E1", "Primer entreno de la semana: calcula liga y entra a un grupo", "select rupio_league_add(%s, %s, 9000, 1)", (fresh, wk), group=g)
    measure(conn, "E2", "Otro entreno en la misma semana (actualiza tu puntaje)", "select rupio_league_add(%s, %s, 9000, 1)", (mid, wk), group=g)

    nonmember = one(conn, "select p.user_id from profiles p where not exists (select 1 from league_members m where m.user_id = p.user_id) limit 1")[0]
    ins = """insert into workouts (id, user_id, started_at, ended_at, total_volume_kg, local_date, name, time_zone)
             select gen_random_uuid(), %s, ts, ts + interval '1 hour', 8000 + random() * 4000, ts::date, 'Prueba', 'America/Mexico_City'
             from (select {ts} as ts from generate_series(1, 400) i) x"""
    hist = ins.format(ts="date_trunc('day', now()) - i * interval '7 days'")
    week = ins.format(ts="rupio_league_week(current_date)::timestamp + i * interval '1 minute'")
    no_stmt = ["drop trigger workouts_league_ins on workouts", "drop trigger workouts_league_upd on workouts",
               "drop trigger workouts_league_del on workouts"]
    by_row = no_stmt + ["""create trigger workouts_league_row after insert or delete or update of total_volume_kg, deleted, ended_at, local_date
                           on workouts for each row execute function rupio_league_on_workout()"""]
    measure(conn, "T0", "Subir 400 entrenos (un lote de sync) sin triggers de ligas", hist, (nonmember,), group=g, setup=no_stmt)
    measure(conn, "T1f", "400 entrenos · no participa · trigger por fila", hist, (nonmember,), group=g, setup=by_row)
    measure(conn, "T1", "400 entrenos · no participa · trigger por sentencia (008)", hist, (nonmember,), group=g)
    measure(conn, "T2f", "400 entrenos · participante, historial de 8 años · por fila", hist, (fresh,), group=g, setup=by_row)
    measure(conn, "T2", "400 entrenos · participante, historial de 8 años · por sentencia (008)", hist, (fresh,), group=g)
    measure(conn, "T3f", "400 entrenos · participante, todos en esta semana (peor caso) · por fila", week, (fresh,), group=g, setup=by_row)
    measure(conn, "T3", "400 entrenos · participante, todos en esta semana (peor caso) · por sentencia (008)", week, (fresh,), group=g)
    upd = "update workouts set total_volume_kg = total_volume_kg + 100 where user_id = %s and local_date >= rupio_league_week(current_date) - 7"
    measure(conn, "T4", "Editar 400 entrenos de esta semana en un UPDATE · por sentencia (008)", upd, (fresh,), group=g,
            setup=[ins.format(ts="rupio_league_week(current_date)::timestamp + i * interval '1 minute'").replace("%s", f"'{fresh}'")])
    return {"semana": str(wk), "participantes_semana": members_week}


def sizes(conn):
    with conn.cursor() as c:
        c.execute("""select relname, pg_size_pretty(pg_total_relation_size(oid)), pg_total_relation_size(oid)
                     from pg_class where relname in ('league_members','league_groups','league_scores','wall_entries',
                       'league_scores_group_idx','league_scores_week_idx','league_groups_open_idx','wall_entries_rank_idx')
                     order by 3 desc""")
        return c.fetchall()


def counts(conn):
    q = {t: one(conn, f"select count(*) from {t}")[0] for t in ("profiles", "wall_entries", "league_members", "league_groups", "league_scores")}
    return q


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dsn", default="host=/tmp user=postgres dbname=rupio_bench")
    a = ap.parse_args()
    conn = psycopg.connect(a.dsn)
    print("== Muro hoy (007)")
    bench_wall(conn, "hoy (007)", "points desc, first_at")
    print("== Aplicando 008 y datos de ligas")
    run_file(conn, os.path.join("..", "migrations", "008_ligas.sql"), a.dsn)
    run_file(conn, "variante_por_fila.sql", a.dsn)
    run_file(conn, "datos_ligas.sql", a.dsn)
    conn.autocommit = True
    conn.execute("vacuum analyze wall_entries")   # estado normal: lo que hace autovacuum tras la migración
    conn.autocommit = False
    print("== Muro con 008")
    bench_wall(conn, "con 008", "rank_key desc")
    print("== Ligas")
    meta = bench_leagues(conn)
    meta.update(counts(conn))
    meta["version"] = one(conn, "select version()")[0]
    meta["sizes"] = sizes(conn)
    json.dump({"meta": meta, "results": RESULTS}, open(os.path.join(HERE, "results_ligas.json"), "w"), indent=1, default=str, ensure_ascii=False)
    with open(os.path.join(HERE, "results_ligas.md"), "w", encoding="utf-8") as f:
        f.write("# Resultados · ligas y muro\n\n")
        f.write(f"Mediana de {N} ejecuciones tras 1 de calentamiento (planning + execution, EXPLAIN ANALYZE en el servidor, sin red).\n\n")
        f.write(f"{meta['version'].split(',')[0]} local · perfiles={meta['profiles']:,} · muro={meta['wall_entries']:,} · "
                f"participantes={meta['league_members']:,} · grupos={meta['league_groups']:,} · puntajes semanales={meta['league_scores']:,} · "
                f"participantes esta semana={meta['participantes_semana']:,}\n\n")
        last = None
        for r in RESULTS:
            if r["group"] != last:
                f.write(f"\n## {r['group']}\n\n| ID | Consulta | Mediana (ms) | Máx (ms) | Plan |\n|---|---|---:|---:|---|\n")
                last = r["group"]
            plan = ", ".join(r["nodes"]) + (f" ({', '.join(r['indexes'])})" if r["indexes"] else "")
            if r["trigger_ms"]:
                plan += f" · trigger {r['trigger_ms']} ms"
            f.write(f"| {r['id']} | {r['label']} | {r['median_ms']} | {r['max_ms']} | {plan} |\n")
        f.write("\n## Tamaños\n\n```\n" + "\n".join(f"{n}\t{s}" for n, s, _ in meta["sizes"]) + "\n```\n")
    print("listo: results_ligas.md")


if __name__ == "__main__":
    main()
