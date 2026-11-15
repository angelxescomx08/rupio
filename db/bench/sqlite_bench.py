#!/usr/bin/env python3
"""
Benchmark de la base LOCAL del teléfono (SQLite vía Room) con datos dummy.

- Crea las tablas e índices EXACTOS que genera Room (el JSON de esquema más reciente en app/schemas/...).
- Extrae el SQL EXACTO de las @Query de Daos.kt (no hay copias a mano que se desincronicen).
- Genera un usuario "típico" (300 entrenamientos ≈ 2 años) y uno "extremo" (5 000 entrenamientos ≈ 14 años
  entrenando a diario, 120 000 series) y mide cada consulta: mediana y p95 de 40 ejecuciones + EXPLAIN QUERY PLAN.
- Repite las consultas clave SIN índices para mostrar la diferencia.

Uso: python3 sqlite_bench.py   → escribe results_sqlite.md / results_sqlite.json
Nota: un teléfono de gama media es ~3–5× más lento que esta máquina; los números se reportan tal cual.
"""
import json, os, random, re, sqlite3, statistics, time, uuid
from datetime import date, datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
_SCHEMAS = os.path.join(ROOT, "app/schemas/com.rupio.app.data.local.RupioDatabase")
SCHEMA = os.path.join(_SCHEMAS, max((f for f in os.listdir(_SCHEMAS) if f.endswith(".json")), key=lambda f: int(f.split(".")[0])))
DAOS = os.path.join(ROOT, "app/src/main/java/com/rupio/app/data/local/Daos.kt")
CATALOG = os.path.join(ROOT, "app/src/main/assets/exercises_es.json")
RUNS = 40

def uuid7(ms):
    r = random.getrandbits(74)
    v = (ms & ((1 << 48) - 1)) << 80 | 0x7 << 76 | (r >> 62) << 64 | 0b10 << 62 | (r & ((1 << 62) - 1))
    return str(uuid.UUID(int=v))

def create(conn, with_indexes=True):
    d = json.load(open(SCHEMA))["database"]
    for e in d["entities"]:
        conn.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        if with_indexes:
            for i in e.get("indices", []):
                conn.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))

def room_queries():
    """{nombre_funcion: sql} desde las anotaciones @Query de Daos.kt."""
    src = open(DAOS, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'@Query\(\s*(?:"""(.*?)"""|"(.*?)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)', src, re.S):
        out.setdefault(m.group(3), " ".join((m.group(1) or m.group(2)).split()))
    return out

def generate(conn, n_workouts, seed=7):
    random.seed(seed)
    cat = json.load(open(CATALOG, encoding="utf-8"))
    conn.executemany("""insert into exercises (id, name, search_key, primary_muscle, secondary_muscles, equipment, tracking, is_lower_body,
                        level, cue, image0, image1, is_custom, owner_id, deleted, updated_at, dirty) values (?,?,?,?,?,?,?,?,?,?,?,?,0,null,0,0,0)""",
                     [(e["id"], e["name"], e["name"].lower(), e["primary"], ",".join(e["secondary"]), e["equipment"], e["tracking"], int(e["lower"]),
                       e["level"], e["cue"], (e["images"] + [None, None])[0], (e["images"] + [None, None])[1]) for e in cat])
    weighted = [e["id"] for e in cat if e["tracking"] == "weight_reps"]
    random.shuffle(weighted)
    split = [weighted[0:6], weighted[6:12], weighted[12:18]]          # empuje / tirón / pierna
    uid = str(uuid.uuid4())
    now = datetime.now(timezone.utc)
    w_rows, we_rows, s_rows = [], [], []
    base = {ex: 20 + random.random() * 80 for ex in weighted[:18]}
    for k in range(n_workouts):
        start = now - timedelta(hours=(n_workouts - k) * 26 + random.random() * 3)
        ms = int(start.timestamp() * 1000)
        day = (start - timedelta(hours=6)).date().toordinal() - date(1970, 1, 1).toordinal()
        wid = uuid7(ms); dur = 2400 + random.randint(0, 3000)
        vol = 0.0; sets_n = 0; reps_n = 0
        for p, ex in enumerate(split[k % 3]):
            weid = uuid7(ms)
            we_rows.append((weid, wid, uid, ex, p, None, 0, None, 90, 8, 12, None, ms, 0, 0))
            prog = 1 + 0.4 * k / max(1, n_workouts)
            for s in range(4):
                warm = s == 0
                kg = round(base[ex] * prog * (0.5 if warm else 1) / 2.5) * 2.5
                reps = random.randint(6, 12)
                if not warm: vol += kg * reps; sets_n += 1; reps_n += reps
                s_rows.append((uuid7(ms), weid, wid, uid, ex, s, 1 if warm else 0, kg, reps, None,
                               random.choice([None, None, 8.0, 9.0]), 1, ms + (p * 4 + s) * 120000, day, 0, ms, 0, int(k > n_workouts - 6)))
        w_rows.append((wid, uid, None, ["Empuje", "Tirón", "Pierna"][k % 3], ms, ms + dur * 1000, "America/Mexico_City", day, dur,
                       vol, sets_n, reps_n, 0, None, ms, 0, int(k > n_workouts - 6)))
    conn.executemany("insert into workouts values (" + ",".join("?" * 17) + ")", w_rows)
    conn.executemany("insert into workout_exercises values (" + ",".join("?" * 15) + ")", we_rows)
    conn.executemany("insert into workout_sets values (" + ",".join("?" * 18) + ")", s_rows)
    recs = conn.execute("""select exercise_id, max(weight_kg), max(reps) from workout_sets where set_type != 1 group by exercise_id""").fetchall()
    conn.executemany("insert into personal_records values (?,?,?,?,?,?,?,0)",
                     [(ex, kind, uid, val, None, int(now.timestamp() * 1000), 0) for ex, w, r in recs for kind, val in ((1, w), (4, r))])
    # Entrenamiento activo (como cuando estás en el gimnasio).
    ms = int(now.timestamp() * 1000); day = (now - timedelta(hours=6)).date().toordinal() - date(1970, 1, 1).toordinal()
    conn.execute("insert into workouts values (" + ",".join("?" * 17) + ")",
                 (uuid7(ms), uid, None, "Hoy", ms, None, "America/Mexico_City", day, None, 0, 0, 0, 0, None, ms, 0, 1))
    conn.commit()
    last = conn.execute("select id, local_date, started_at from workouts where ended_at is not null order by local_date desc, started_at desc").fetchall()
    return dict(uid=uid, hot_ex=split[0][0], last_wid=last[0][0], today=day, page_ids=[r[0] for r in last[:30]],
                deep=last[min(len(last) - 1, 1000)], n_sets=len(s_rows), n_workouts=n_workouts)

def params_for(name, ctx):
    t = ctx["today"]
    p = {
        "observeActive": {}, "active": {}, "observeRecent": {"limit": 40}, "observeCount": {}, "observePending": {},
        "historyPage": {"beforeDay": 2**31 - 1, "beforeStart": 2**63 - 1, "limit": 30},
        "historyPage_deep": {"beforeDay": ctx["deep"][1], "beforeStart": ctx["deep"][2], "limit": 30},
        "observeSets": {"workoutId": ctx["last_wid"]}, "observeExercises": {"workoutId": ctx["last_wid"]},
        "previousSets": {"exerciseId": ctx["hot_ex"], "excludeWorkout": "-", "limit": 150},
        "volumeByDay": {"from": t - 6, "to": t}, "volumeByWeek": {"from": t - 91, "to": t, "shift": 3},
        "volumeByMonth": {"from": t - 365, "to": t}, "volumeByYear": {"from": 0, "to": t},
        "totals": {"from": t - 91, "to": t}, "avgDuration": {"from": t - 91, "to": t},
        "exerciseSetCounts": {"from": t - 27, "to": t}, "exerciseSetCounts_year": {"from": t - 365, "to": t},
        "muscleSets": {"from": t - 27, "to": t},
        "exerciseSets": {"exerciseId": ctx["hot_ex"], "from": 0}, "e1rmCurve": {"exerciseId": ctx["hot_ex"], "from": 0},
        "topExercises": {"from": 0, "limit": 12}, "topExercises_year": {"from": t - 365, "limit": 12},
        "previousSets_history": {"exerciseId": ctx["hot_ex"], "excludeWorkout": "-", "limit": 300},
        "trainingDays": {"from": t - 126, "to": t}, "firstDay": {},
        "dirtySets": {"limit": 400}, "dirtyWorkouts": {"limit": 400},
        "exerciseSummaries": {"ids": ctx["page_ids"]},
        "forExercise": {"exerciseId": ctx["hot_ex"]},
    }
    return p.get(name)

LABELS = [
    ("observeActive", "Entrenamiento activo al abrir la app"),
    ("observeRecent", "Inicio: últimos 40 entrenamientos (resumen semanal)"),
    ("historyPage", "Historial: primera página (30)"),
    ("historyPage_deep", "Historial: página 1 000 entrenamientos atrás (keyset)"),
    ("exerciseSummaries", "Historial: ejercicios de una página en 1 consulta (sin N+1)"),
    ("observeSets", "Entrenamiento: todas sus series"),
    ("previousSets", "Motor de progresión: series anteriores del ejercicio"),
    ("forExercise", "Récords del ejercicio"),
    ("volumeByDay", "Progreso · Semana (por día)"),
    ("volumeByWeek", "Progreso · 3 meses (por semana)"),
    ("volumeByMonth", "Progreso · Año (por mes)"),
    ("volumeByYear", "Progreso · Histórico (por año)"),
    ("totals", "Totales del periodo"),
    ("exerciseSetCounts", "Series por músculo · 4 semanas"),
    ("exerciseSetCounts_year", "Series por músculo · 1 año"),
    ("e1rmCurve", "Curva de 1RM de un ejercicio (todo el historial, agregada en SQL)"),
    ("topExercises", "Ejercicios más usados · todo el historial"),
    ("topExercises_year", "Ejercicios más usados · último año"),
    ("previousSets_history", "Detalle de ejercicio: últimas 300 series"),
    ("trainingDays", "Mapa de constancia (18 semanas)"),
    ("firstDay", "Primer día registrado"),
    ("observePending", "Cambios pendientes de sincronizar"),
    ("dirtySets", "Sync: series por subir"),
]

def expand(sql, prm):
    """Room expande IN (:lista); aquí igual."""
    for k, v in list(prm.items()):
        if isinstance(v, list):
            names = [f"{k}_{i}" for i in range(len(v))]
            sql = sql.replace(f"(:{k})", "(" + ",".join(":" + n for n in names) + ")")
            prm = {**{n: x for n, x in zip(names, v)}, **{a: b for a, b in prm.items() if a != k}}
    return sql, prm

def measure(conn, sql, prm):
    sql, prm = expand(sql, prm)
    for _ in range(3): conn.execute(sql, prm).fetchall()
    ts = []
    for _ in range(RUNS):
        t = time.perf_counter(); rows = conn.execute(sql, prm).fetchall(); ts.append((time.perf_counter() - t) * 1000)
    plan = " | ".join(r[3] for r in conn.execute("explain query plan " + sql, prm).fetchall())
    ts.sort()
    return dict(median=statistics.median(ts), p95=ts[int(len(ts) * 0.95) - 1], rows=len(rows), plan=plan)

def e1rm_kotlin(w, r):
    """Copia de OneRepMax.estimate (Training.kt) para verificar la versión SQL."""
    if w <= 0 or r <= 0: return None
    if r == 1: return w
    if r <= 10: return (w * (1 + r / 30.0) + w * 36.0 / (37 - r)) / 2
    if r <= 12: return w * (1 + r / 30.0)
    return None

def check_e1rm(conn, sql, ctx):
    rows = conn.execute("select performed_on, weight_kg, reps from workout_sets where exercise_id = ? and completed = 1 and deleted = 0 and set_type != 1",
                        (ctx["hot_ex"],)).fetchall()
    best = {}
    for d, w, r in rows:
        v = e1rm_kotlin(w or 0, r or 0)
        if v is not None: best[d] = max(best.get(d, 0), v)
    got = {d: b for d, b, _ in conn.execute(sql, {"exerciseId": ctx["hot_ex"], "from": 0}).fetchall()}
    assert set(got) == set(best) and all(abs(got[d] - best[d]) < 1e-9 for d in got), "La fórmula SQL de 1RM no coincide con Kotlin"

def run_size(n_workouts, with_indexes=True):
    conn = sqlite3.connect(":memory:")
    conn.execute("pragma foreign_keys = on")
    create(conn, with_indexes)
    ctx = generate(conn, n_workouts)
    conn.execute("pragma optimize")   # lo mismo que hace la app al abrir la base
    qs = room_queries()
    qs["historyPage_deep"] = qs["historyPage"]
    qs["exerciseSetCounts_year"] = qs["exerciseSetCounts"]
    qs["topExercises_year"] = qs["topExercises"]
    qs["previousSets_history"] = qs["previousSets"]
    check_e1rm(conn, qs["e1rmCurve"], ctx)
    out = {}
    for name, _ in LABELS:
        out[name] = measure(conn, qs[name], params_for(name, ctx))
    return ctx, out

def main():
    report = {}
    for label, n in [("típico", 300), ("extremo", 5000)]:
        ctx, res = run_size(n)
        report[label] = dict(n_workouts=n, n_sets=ctx["n_sets"], results=res)
        print(f"== {label}: {n} entrenamientos, {ctx['n_sets']} series")
        for name, lab in LABELS:
            r = res[name]; print(f"  {r['median']:8.3f} ms  p95 {r['p95']:8.3f}  {lab}  [{r['plan'][:110]}]")
    _, noidx = run_size(5000, with_indexes=False)
    report["extremo_sin_indices"] = dict(results=noidx)
    md = ["# Rendimiento local (SQLite / Room) con datos dummy", "",
          f"SQLite {sqlite3.sqlite_version} · mediana y p95 de {RUNS} ejecuciones por consulta · SQL extraído de `Daos.kt` · esquema e índices de Room.",
          "Un teléfono de gama media es ~3–5× más lento que esta máquina: aun así todo queda muy por debajo de un fotograma (16 ms).", "",
          f"- Usuario típico: 300 entrenamientos, {report['típico']['n_sets']:,} series",
          f"- Usuario extremo: 5 000 entrenamientos, {report['extremo']['n_sets']:,} series (≈14 años entrenando a diario)", "",
          "| Consulta | Típico (ms) | Extremo (ms) | Extremo p95 | Extremo SIN índices (ms) | Plan con índices (extremo) |", "|---|---:|---:|---:|---:|---|"]
    for name, lab in LABELS:
        a = report["típico"]["results"][name]; b = report["extremo"]["results"][name]; c = noidx[name]
        md.append(f"| {lab} | {a['median']:.3f} | {b['median']:.3f} | {b['p95']:.3f} | {c['median']:.3f} | `{b['plan'][:140]}` |")
    open(os.path.join(HERE, "results_sqlite.md"), "w").write("\n".join(md) + "\n")
    json.dump(report, open(os.path.join(HERE, "results_sqlite.json"), "w"), indent=1)

if __name__ == "__main__":
    main()
