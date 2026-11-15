#!/usr/bin/env python3
"""
Auditoría QA de TODAS las consultas de la app (SQLite / Room) con un usuario EXTREMO.

- Esquema e índices EXACTOS de Room (último JSON en app/schemas).
- SQL EXACTO de cada @Query de Daos.kt (se identifican como Dao.funcion; ninguna se queda sin probar:
  si aparece una consulta nueva sin parámetros definidos aquí, la auditoría FALLA).
- Usuario extremo: 10 000 entrenamientos (≈ 27 años a diario, o 2 al día por 14 años), ~240 000 series,
  20 años de peso corporal diario, 120 rutinas, 300 ejercicios personalizados, entrenamiento activo y cambios sin subir.
- Mide mediana y p95, EXPLAIN QUERY PLAN, y marca: ✗ > 8 ms · ⚠ > 2 ms · escaneo completo de tabla grande.
- Verifica exactitud de las agregaciones contra un cálculo directo en Python.

Las consultas que escriben (UPDATE/DELETE) se ejecutan dentro de un SAVEPOINT y se revierten.
Uso: python3 qa_sql_audit.py [n_entrenamientos]   → results_qa_sql.md / .json
"""
import json, os, random, re, sqlite3, statistics, sys, time, uuid
from datetime import date, datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SCHEMAS = os.path.join(ROOT, "app/schemas/com.rupio.app.data.local.RupioDatabase")
SCHEMA = os.path.join(SCHEMAS, max((f for f in os.listdir(SCHEMAS) if f.endswith(".json")), key=lambda f: int(f.split(".")[0])))
DAOS = os.path.join(ROOT, "app/src/main/java/com/rupio/app/data/local/Daos.kt")
CATALOG = os.path.join(ROOT, "app/src/main/assets/exercises_es.json")
RUNS = 30
BIG = {"workout_sets", "workouts", "workout_exercises", "body_weights"}
WARN_MS, FAIL_MS = 2.0, 8.0


def uuid7(ms):
    r = random.getrandbits(74)
    v = (ms & ((1 << 48) - 1)) << 80 | 0x7 << 76 | (r >> 62) << 64 | 0b10 << 62 | (r & ((1 << 62) - 1))
    return str(uuid.UUID(int=v))


def create(conn):
    d = json.load(open(SCHEMA))["database"]
    cols = {}
    for e in d["entities"]:
        conn.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for i in e.get("indices", []):
            conn.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        cols[e["tableName"]] = [f["columnName"] for f in e["fields"]]
    return cols, d["version"]


def ins(conn, cols, table, rows):
    """rows = lista de dicts; columnas faltantes → default razonable."""
    c = cols[table]
    defaults = {"deleted": 0, "dirty": 0, "updated_at": 0, "set_plan": None, "notes": None, "superset_id": None,
                "superset_rest_mode": 0, "superset_rest_sec": None, "pr_count": 0, "is_pr": 0, "duration_sec": None, "routine_id": None}
    conn.executemany(f"insert into {table} ({','.join(c)}) values ({','.join('?' * len(c))})",
                     [tuple(r.get(k, defaults.get(k)) for k in c) for r in rows])


def queries():
    src = open(DAOS, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'@Query\(\s*(?:"""(.*?)"""|"(.*?)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)\(([^)]*)\)', src, re.S):
        iface = re.findall(r'interface (\w+)', src[:m.start()])[-1]
        out[f"{iface}.{m.group(3)}"] = " ".join((m.group(1) or m.group(2)).split())
    return out


def generate(conn, cols, n_workouts, seed=11):
    random.seed(seed)
    cat = json.load(open(CATALOG, encoding="utf-8"))
    ins(conn, cols, "exercises", [dict(id=e["id"], name=e["name"], search_key=e["name"].lower(), primary_muscle=e["primary"],
        secondary_muscles=",".join(e["secondary"]), equipment=e["equipment"], tracking=e["tracking"], is_lower_body=int(e["lower"]),
        level=e["level"], cue=e["cue"], image0=(e["images"] + [None])[0], image1=(e["images"] + [None, None])[1], is_custom=0, owner_id=None) for e in cat])
    uid = str(uuid.uuid4())
    now = datetime.now(timezone.utc); now_ms = int(now.timestamp() * 1000)
    customs = [dict(id=uuid7(now_ms), name=f"Personalizado {i} con un nombre larguísimo para probar", search_key=f"personalizado {i}",
                    primary_muscle="pecho", secondary_muscles="", equipment="maquina", tracking="weight_reps", is_lower_body=0,
                    level="beginner", cue="", image0=None, image1=None, is_custom=1, owner_id=uid, dirty=int(i % 10 == 0)) for i in range(300)]
    ins(conn, cols, "exercises", customs)
    weighted = [e["id"] for e in cat if e["tracking"] == "weight_reps"]
    repsonly = [e["id"] for e in cat if e["tracking"] == "reps_only"]
    random.shuffle(weighted)
    pool = weighted[:40] + repsonly[:4]
    base = {ex: 20 + random.random() * 80 for ex in pool}
    W, WE, S = [], [], []
    for k in range(n_workouts):
        start = now - timedelta(hours=(n_workouts - k) * 22 + random.random() * 3)
        ms = int(start.timestamp() * 1000)
        day = (start - timedelta(hours=6)).date().toordinal() - date(1970, 1, 1).toordinal()
        wid = uuid7(ms); dur = 2400 + random.randint(0, 3000)
        vol = 0.0; sn = 0; rn = 0
        exs = random.sample(pool, 6)
        for p, ex in enumerate(exs):
            weid = uuid7(ms)
            WE.append(dict(id=weid, workout_id=wid, user_id=uid, exercise_id=ex, position=p, rest_sec=90, rep_min=8, rep_max=12, updated_at=ms,
                           superset_id=None if p < 4 else f"ss{k}", dirty=int(k > n_workouts - 4)))
            prog = 1 + 0.5 * k / max(1, n_workouts)
            for s in range(4):
                t = 1 if s == 0 else (2 if s == 3 and k % 7 == 0 else 0)
                kg = None if ex in repsonly else round(base[ex] * prog * (0.5 if t == 1 else 1) / 2.5) * 2.5
                reps = random.randint(5, 14)
                if t != 1: vol += (kg or 0) * reps; sn += 1; rn += reps
                S.append(dict(id=uuid7(ms), workout_exercise_id=weid, workout_id=wid, user_id=uid, exercise_id=ex, position=s, set_type=t,
                              weight_kg=kg, reps=reps, duration_sec=None, rpe=random.choice([None, None, 8.0, 9.5]), completed=1,
                              completed_at=ms + (p * 4 + s) * 120000, performed_on=day, updated_at=ms, dirty=int(k > n_workouts - 4)))
        W.append(dict(id=wid, user_id=uid, routine_id=None, name=random.choice(["Lunes y jueves", "Martes y viernes", "Libre"]), started_at=ms,
                      ended_at=ms + dur * 1000, time_zone="America/Mexico_City", local_date=day, duration_sec=dur, total_volume_kg=vol,
                      total_sets=sn, total_reps=rn, notes=None, updated_at=ms, dirty=int(k > n_workouts - 4)))
    ins(conn, cols, "workouts", W); ins(conn, cols, "workout_exercises", WE); ins(conn, cols, "workout_sets", S)
    # Entrenamiento activo con 1 ejercicio y series a medias.
    day = (now - timedelta(hours=6)).date().toordinal() - date(1970, 1, 1).toordinal()
    awid = uuid7(now_ms)
    ins(conn, cols, "workouts", [dict(id=awid, user_id=uid, name="Hoy", started_at=now_ms, ended_at=None, time_zone="America/Mexico_City",
                                      local_date=day, total_volume_kg=0, total_sets=0, total_reps=0, updated_at=now_ms, dirty=1)])
    # Rutinas
    R, RE = [], []
    for i in range(120):
        rid = uuid7(now_ms)
        R.append(dict(id=rid, user_id=uid, name=f"Rutina {i}", position=i, created_at=now_ms, updated_at=now_ms, dirty=int(i < 3)))
        for p, ex in enumerate(random.sample(pool, 10)):
            RE.append(dict(id=uuid7(now_ms), routine_id=rid, user_id=uid, exercise_id=ex, position=p, rest_sec=90, target_sets=3,
                           rep_min=8, rep_max=12, updated_at=now_ms, set_plan='[{"w":40.0,"r":12},{"w":40.0,"r":12},{"w":40.0,"r":10,"t":2}]', dirty=int(i < 3)))
    ins(conn, cols, "routines", R); ins(conn, cols, "routine_exercises", RE)
    # Récords
    recs = conn.execute("select exercise_id, max(weight_kg), max(reps) from workout_sets where set_type != 1 group by exercise_id").fetchall()
    ins(conn, cols, "personal_records", [dict(exercise_id=ex, kind=kind, user_id=uid, value=v or 0, set_id=None, achieved_at=now_ms - j * 86400000, updated_at=0)
                                         for j, (ex, w, r) in enumerate(recs) for kind, v in ((1, w), (4, r))])
    # Peso corporal: 20 años casi diarios
    today = day
    bw = []; w = 95.0
    for d in range(today - 7299, today + 1):
        w += random.gauss(-0.001, 0.2)
        if random.random() < 0.15: continue
        bw.append(dict(user_id=uid, measured_on=d, weight_kg=round(w, 2), updated_at=d * 86400000, deleted=0, dirty=int(d > today - 3)))
    ins(conn, cols, "body_weights", bw)
    conn.commit()
    hot = conn.execute("select exercise_id, count(*) c from workout_sets group by exercise_id order by c desc limit 1").fetchone()[0]
    last = conn.execute("select id, local_date, started_at from workouts where ended_at is not null order by local_date desc, started_at desc").fetchall()
    sets = [r[0] for r in conn.execute("select id from workout_sets limit 400")]
    def days_of(ex):
        ds = sorted({r[0] for r in conn.execute("select performed_on from workout_sets where exercise_id = ? and completed = 1", (ex,))})
        return ds[:3] + ds[-8:]
    reps_ex = repsonly[0]
    return dict(uid=uid, today=today, hot=hot, rid=R[0]["id"], rids=[r["id"] for r in R[:30]], last_wid=last[0][0], awid=awid,
                deep=last[min(len(last) - 1, 5000)], page=[r[0] for r in last[:30]], set_ids=sets, set_id=sets[0],
                wids=[r[0] for r in last[:400]], weids=[r["id"] for r in WE[:400]], ex_ids=[e["id"] for e in cat[:30]],
                re_ids=[r["id"] for r in RE[:400]], custom=customs[0]["id"], n_sets=len(S), n_workouts=n_workouts, n_bw=len(bw), now=now_ms,
                bw_days=[b["measured_on"] for b in bw[-400:]], hot_days=days_of(hot), reps_ex=reps_ex, reps_days=days_of(reps_ex))


def params(ctx):
    t, u, n = ctx["today"], ctx["uid"], ctx["now"]
    return {
        "ExerciseDao.observeAll": {}, "ExerciseDao.get": {"id": ctx["hot"]}, "ExerciseDao.observe": {"id": ctx["hot"]},
        "ExerciseDao.getMany": {"ids": ctx["ex_ids"]}, "ExerciseDao.catalogCount": {}, "ExerciseDao.dirty": {"limit": 400},
        "ExerciseDao.markClean": {"id": ctx["custom"], "updatedAt": 0}, "ExerciseDao.dirtyIds": {"ids": ctx["ex_ids"]},
        "RoutineDao.observeRoutines": {}, "RoutineDao.get": {"id": ctx["rid"]}, "RoutineDao.exercises": {"routineId": ctx["rid"]},
        "RoutineDao.observeAllExercises": {}, "RoutineDao.count": {},
        "RoutineDao.softDeleteOthers": {"routineId": ctx["rid"], "keep": ctx["re_ids"][:5], "now": n},
        "RoutineDao.softDelete": {"id": ctx["rid"], "now": n}, "RoutineDao.dirtyRoutines": {"limit": 400}, "RoutineDao.dirtyExercises": {"limit": 400},
        "RoutineDao.cleanRoutine": {"id": ctx["rid"], "u": n}, "RoutineDao.cleanExercise": {"id": ctx["re_ids"][0], "u": n},
        "RoutineDao.dirtyRoutineIds": {"ids": ctx["rids"]}, "RoutineDao.dirtyExerciseIds": {"ids": ctx["re_ids"]},
        "WorkoutDao.observeActive": {}, "WorkoutDao.active": {}, "WorkoutDao.get": {"id": ctx["last_wid"]}, "WorkoutDao.observe": {"id": ctx["last_wid"]},
        "WorkoutDao.observeExercises": {"workoutId": ctx["last_wid"]}, "WorkoutDao.exercises": {"workoutId": ctx["last_wid"]},
        "WorkoutDao.observeSets": {"workoutId": ctx["last_wid"]}, "WorkoutDao.sets": {"workoutId": ctx["last_wid"]},
        "WorkoutDao.getSet": {"id": ctx["set_id"]}, "WorkoutDao.recordMaxes": {"ex": ctx["hot"]},
        "WorkoutDao.recordHolder": {"ex": ctx["hot"], "kind": 2, "value": 100.0},
        "WorkoutDao.workoutIdOf": {"id": ctx["weids"][0]},
        "WorkoutDao.historyPage": {"beforeDay": 2**31 - 1, "beforeStart": 2**63 - 1, "limit": 30},
        "WorkoutDao.historyPage@5000": {"beforeDay": ctx["deep"][1], "beforeStart": ctx["deep"][2], "limit": 30},
        "WorkoutDao.observeRecent": {"limit": 40},
        "WorkoutDao.previousSets": {"exerciseId": ctx["hot"], "excludeWorkout": ctx["awid"], "limit": 150},
        "WorkoutDao.softDelete": {"id": ctx["last_wid"], "now": n}, "WorkoutDao.hardDelete": {"id": ctx["last_wid"]},
        "WorkoutDao.observeCount": {}, "WorkoutDao.exerciseSummaries": {"ids": ctx["page"]},
        "WorkoutDao.dirtyWorkouts": {"limit": 400}, "WorkoutDao.dirtyExercises": {"limit": 400}, "WorkoutDao.dirtySets": {"limit": 400},
        "WorkoutDao.cleanWorkout": {"id": ctx["last_wid"], "u": n}, "WorkoutDao.cleanExercise": {"id": ctx["weids"][0], "u": n},
        "WorkoutDao.cleanSet": {"id": ctx["set_id"], "u": n},
        "WorkoutDao.dirtyWorkoutIds": {"ids": ctx["wids"]}, "WorkoutDao.dirtyExerciseIds": {"ids": ctx["weids"]}, "WorkoutDao.dirtySetIds": {"ids": ctx["set_ids"]},
        "WorkoutDao.observePending": {},
        "RecordDao.forExercise": {"exerciseId": ctx["hot"]}, "RecordDao.observeForExercise": {"exerciseId": ctx["hot"]},
        "RecordDao.observeRecent": {"limit": 10}, "RecordDao.delete": {"ex": ctx["hot"], "kind": 1}, "RecordDao.allExerciseIds": {},
        "RecordDao.dirty": {"limit": 400}, "RecordDao.clean": {"ex": ctx["hot"], "kind": 1, "u": 0},
        "BodyWeightDao.observeSeries": {"uid": u, "from": t - 89, "to": t, "bucket": 1},
        "BodyWeightDao.observeSeries@20años": {"uid": u, "from": t - 7299, "to": t, "bucket": 48},
        "BodyWeightDao.seriesOnce": {"uid": u, "from": t - 7299, "to": t, "bucket": 48},
        "BodyWeightDao.page": {"uid": u, "before": 2**31 - 1, "limit": 60}, "BodyWeightDao.observeRecent": {"uid": u, "limit": 1},
        "BodyWeightDao.get": {"uid": u, "day": t}, "BodyWeightDao.firstDay": {"uid": u}, "BodyWeightDao.dirty": {"limit": 400},
        "BodyWeightDao.clean": {"uid": u, "day": t, "updatedAt": 0}, "BodyWeightDao.dirtyDays": {"uid": u, "days": ctx["bw_days"]},
        "StatsDao.volumeByWeek": {"from": t - 91, "to": t, "shift": 3}, "StatsDao.volumeByDay": {"from": t - 6, "to": t},
        "StatsDao.volumeByMonth": {"from": t - 365, "to": t}, "StatsDao.volumeByYear": {"from": 0, "to": t},
        "StatsDao.totals": {"from": t - 91, "to": t}, "StatsDao.avgDuration": {"from": t - 91, "to": t},
        "StatsDao.muscleSets": {"from": t - 27, "to": t}, "StatsDao.exerciseSetCounts": {"from": t - 27, "to": t},
        "StatsDao.exerciseSetCounts@1año": {"from": t - 365, "to": t},
        "StatsDao.exerciseSets": {"exerciseId": ctx["hot"], "from": 0}, "StatsDao.topExercises": {"from": t - 365, "limit": 12},
        "StatsDao.e1rmCurve": {"exerciseId": ctx["hot"], "from": 0}, "StatsDao.bestRepsPerDay": {"exerciseId": ctx["hot"]},
        "StatsDao.bestDurationPerDay": {"exerciseId": ctx["hot"]}, "StatsDao.trainingDays": {"from": t - 126, "to": t},
        "StatsDao.trainedExerciseIds": {}, "StatsDao.lastDays": {"ex": ctx["hot"], "n": 8}, "StatsDao.firstDays": {"ex": ctx["hot"], "n": 3},
        "StatsDao.weightedBestsOn": {"ex": ctx["hot"], "days": ctx["hot_days"]}, "StatsDao.repsBestsOn": {"ex": ctx["reps_ex"], "days": ctx["reps_days"]},
        "StatsDao.trainingWeeks": {"shift": 3}, "StatsDao.workoutTotals": {"from": 0, "to": t},
        "StatsDao.recordsSince": {"sinceMs": n - 30 * 86400000}, "StatsDao.firstDay": {}, "StatsDao.lastDay": {},
        "AdminDao.clearWorkouts": {}, "AdminDao.clearRoutines": {}, "AdminDao.clearRecords": {}, "AdminDao.clearCustomExercises": {},
    }


def expand(sql, prm):
    for k, v in list(prm.items()):
        if isinstance(v, list):
            names = [f"{k}_{i}" for i in range(len(v))]
            sql = sql.replace(f"(:{k})", "(" + ",".join(":" + x for x in names) + ")")
            prm = {**{x: y for x, y in zip(names, v)}, **{a: b for a, b in prm.items() if a != k}}
    return sql, prm


def measure(conn, sql, prm, runs):
    sql, prm = expand(sql, prm)
    write = not sql.lstrip().upper().startswith("SELECT")
    def once():
        if write: conn.execute("savepoint q")
        t = time.perf_counter(); cur = conn.execute(sql, prm); rows = cur.fetchall() if not write else []; dt = (time.perf_counter() - t) * 1000
        if write: conn.execute("rollback to q"); conn.execute("release q")
        return dt, rows
    for _ in range(2): once()
    ts = []; rows = []
    for _ in range(runs):
        dt, rows = once(); ts.append(dt)
    plan = [r[3] for r in conn.execute("explain query plan " + sql, prm).fetchall()]
    ts.sort()
    full = [p for p in plan if re.match(r"SCAN (\w+)", p) and re.match(r"SCAN (\w+)", p).group(1) in BIG and "INDEX" not in p]
    return dict(median=statistics.median(ts), p95=ts[max(0, int(len(ts) * 0.95) - 1)], rows=len(rows), plan=plan, full_scans=full, write=write)


def e1rm(w, r):
    if not w or w <= 0 or not r or r <= 0: return None
    if r == 1: return w
    if r <= 10: return (w * (1 + r / 30.0) + w * 36.0 / (37 - r)) / 2
    return w * (1 + r / 30.0)


def verify(conn, qs, ctx):
    """Exactitud: las agregaciones SQL contra un cálculo directo en Python."""
    checks = []
    t = ctx["today"]
    rows = conn.execute("select local_date, total_volume_kg, total_sets from workouts where deleted = 0 and ended_at is not null").fetchall()
    tot = conn.execute(qs["StatsDao.totals"], {"from": 0, "to": t}).fetchone()
    checks.append(("Totales = suma directa", abs(tot[0] - sum(r[1] for r in rows)) < 1e-6 and tot[1] == sum(r[2] for r in rows)))
    weeks = conn.execute(qs["StatsDao.volumeByWeek"], {"from": 0, "to": t, "shift": 3}).fetchall()
    checks.append(("Volumen por semana suma el total", abs(sum(w[1] for w in weeks) - tot[0]) < 1e-3))
    # "Tu progreso": el enfoque rápido (primeros 3 + últimos 8 días por ejercicio) da lo MISMO que recorrer todo.
    raw = conn.execute("""select s.exercise_id, s.performed_on, s.weight_kg, s.reps from workout_sets s join workouts w on w.id = s.workout_id
                          where s.completed = 1 and s.deleted = 0 and s.set_type not in (1,2) and s.weight_kg > 0 and s.reps between 1 and 30
                          and w.deleted = 0 and w.ended_at is not null""").fetchall()
    full = {}
    for ex, d, w, r in raw:
        v = e1rm(w, r)
        if v is not None and v > full.setdefault(ex, {}).get(d, 0): full[ex][d] = v
    def story(days_map):   # misma lógica que ProgressStoryBuilder (primero, ahora = mejor de las últimas 3, estancado)
        out = {}
        for ex, m in days_map.items():
            ds = sorted(m)
            if len(ds) < 2: continue
            first = m[ds[0]]; now = max(m[d] for d in ds[-3:])
            recent = [d for d in ds if d >= t - 42]
            stalled = len(recent) >= 4 and len(ds) >= 6 and max(m[d] for d in ds[-3:]) <= max(m[d] for d in ds[-6:-3]) * 1.01
            out[ex] = (ds[0], round(first, 9), round(now, 9), stalled)
        return out
    fast = {}
    ids = [r[0] for r in conn.execute(qs["StatsDao.trainedExerciseIds"])]
    for ex in ids:
        days = sorted(set([r[0] for r in conn.execute(qs["StatsDao.firstDays"], {"ex": ex, "n": 3})] + [r[0] for r in conn.execute(qs["StatsDao.lastDays"], {"ex": ex, "n": 8})]))
        sql, prm = expand(qs["StatsDao.weightedBestsOn"], {"ex": ex, "days": days})
        for _, d, v, w, r in conn.execute(sql, prm):
            fast.setdefault(ex, {})[d] = v
            assert abs(e1rm(w, r) - v) < 1e-9
    a, b = story(full), story(fast)
    checks.append((f"Tu progreso rápido = cálculo completo ({len(a)} ejercicios: primera vez, ahora y estancado)", a == b))
    distinct = {r[0] for r in conn.execute("select distinct exercise_id from workout_sets")}
    checks.append(("Lista de ejercicios por índice = DISTINCT completo", set(ids) == distinct))
    weeks = {r[0] for r in conn.execute(qs["StatsDao.trainingWeeks"], {"shift": 3})}
    checks.append(("Semanas con entreno = cálculo directo", weeks == {(d + 3) // 7 for d, _, _ in rows}))
    # Récords en una pasada = reglas de Records.candidates aplicadas serie por serie.
    ok = True
    for ex in ids[:15]:
        rows2 = conn.execute("""select weight_kg, reps, duration_sec from workout_sets s where exercise_id = ? and completed = 1 and deleted = 0 and set_type != 1
                                and exists (select 1 from workouts w where w.id = s.workout_id and w.deleted = 0)""", (ex,)).fetchall()
        def mx(vals): vals = [v for v in vals if v is not None and v > 0]; return max(vals) if vals else None
        exp = (mx([w for w, r, d in rows2 if (w or 0) > 0 and (r or 0) > 0]),
               mx([(w if r == 1 else e1rm(w, r)) if (w or 0) > 0 and r and 1 <= r <= 12 else None for w, r, d in rows2]),
               mx([w * r for w, r, d in rows2 if (w or 0) > 0 and (r or 0) > 0]), mx([r for w, r, d in rows2]), mx([d for w, r, d in rows2]))
        got = conn.execute(qs["WorkoutDao.recordMaxes"], {"ex": ex}).fetchone()
        ok &= all((a is None and b is None) or (a is not None and b is not None and abs(a - b) < 1e-9) for a, b in zip(exp, got))
        if got[1]:
            h = conn.execute(qs["WorkoutDao.recordHolder"], {"ex": ex, "kind": 2, "value": got[1]}).fetchone()
            ok &= h is not None
    checks.append(("Récords en una pasada = reglas de Records.candidates (15 ejercicios)", ok))
    # Peso corporal agrupado
    pts = conn.execute(qs["BodyWeightDao.seriesOnce"], {"uid": ctx["uid"], "from": t - 7299, "to": t, "bucket": 48}).fetchall()
    n = conn.execute("select count(*) from body_weights where user_id = ? and deleted = 0", (ctx["uid"],)).fetchone()[0]
    checks.append(("Peso 20 años: ≤ 160 puntos y no se pierde ningún registro", len(pts) <= 160 and sum(p[2] for p in pts) == n))
    return checks


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 10000
    conn = sqlite3.connect(":memory:")
    cols, version = create(conn)
    t0 = time.perf_counter(); ctx = generate(conn, cols, n); gen_s = time.perf_counter() - t0
    conn.execute("pragma optimize")
    qs = queries(); prm = params(ctx)
    missing = [k for k in qs if k not in prm]
    for k in [k for k in prm if "@" in k]:
        qs[k] = qs[k.split("@")[0]]
    results = {k: measure(conn, qs[k], prm[k], RUNS) for k in sorted(prm) if k in qs}
    checks = verify(conn, qs, ctx)
    size_mb = conn.execute("pragma page_count").fetchone()[0] * conn.execute("pragma page_size").fetchone()[0] / 1e6

    bad = [k for k, r in results.items() if r["p95"] > FAIL_MS]
    warn = [k for k, r in results.items() if WARN_MS < r["p95"] <= FAIL_MS]
    scans = [k for k, r in results.items() if r["full_scans"] and not k.startswith("AdminDao")]
    md = [f"# Auditoría QA de SQL (SQLite {sqlite3.sqlite_version}, esquema Room v{version})", "",
          f"Usuario extremo: {ctx['n_workouts']:,} entrenamientos, {ctx['n_sets']:,} series, {ctx['n_bw']:,} pesos corporales, 120 rutinas, 300 ejercicios propios · base ≈ {size_mb:.0f} MB · generada en {gen_s:.1f} s.",
          f"Consultas en Daos.kt: **{len(qs) - len([k for k in qs if '@' in k])}** · sin parámetros en la auditoría: **{len(missing)}** {missing if missing else ''}",
          f"Criterio: p95 ≤ {WARN_MS} ms ✓ · ≤ {FAIL_MS} ms ⚠ · más ✗ (un teléfono medio es ~3–5× más lento que esta máquina).", "",
          f"Resultado: ✗ {len(bad)} · ⚠ {len(warn)} · escaneos completos de tablas grandes: {len(scans)}", "",
          "## Exactitud", ""] + [f"- {'✓' if ok else '✗'} {name}" for name, ok in checks] + ["",
          "## Todas las consultas", "", "| Consulta | p50 ms | p95 ms | filas | | Plan |", "|---|---:|---:|---:|---|---|"]
    for k, r in sorted(results.items(), key=lambda kv: -kv[1]["p95"]):
        mark = "✗" if r["p95"] > FAIL_MS else "⚠" if r["p95"] > WARN_MS else "✓"
        if r["full_scans"] and not k.startswith("AdminDao"): mark += " SCAN"
        md.append(f"| `{k}` | {r['median']:.3f} | {r['p95']:.3f} | {r['rows']} | {mark} | {' · '.join(r['plan'])[:160]} |")
    open(os.path.join(HERE, "results_qa_sql.md"), "w").write("\n".join(md) + "\n")
    json.dump(dict(ctx={k: v for k, v in ctx.items() if not isinstance(v, list)}, results=results, checks=checks, missing=missing),
              open(os.path.join(HERE, "results_qa_sql.json"), "w"), indent=1, default=str)
    print("\n".join(md[:12]))
    for k, r in sorted(results.items(), key=lambda kv: -kv[1]["p95"])[:20]:
        print(f"{r['p95']:9.3f} ms  {k:40s} {'SCAN ' + str(r['full_scans']) if r['full_scans'] else ''}  {' · '.join(r['plan'])[:120]}")
    if missing or not all(ok for _, ok in checks): sys.exit(1)


if __name__ == "__main__":
    main()
