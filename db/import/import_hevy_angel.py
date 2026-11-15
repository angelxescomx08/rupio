#!/usr/bin/env python3
"""
Carga las 2 rutinas de Hevy de Angel en Neon y borra (soft delete) su rutina/entreno de prueba "ejemplo".
Pesos de Hevy en libras → se guardan en kg. Se corre una sola vez: python3 import_hevy_angel.py [--dry]
"""
import json, os, secrets, sys, time
sys.path.insert(0, "/home/claude/tools")
import nsql

UID = "5b614c54-a42e-49ca-b7a7-2214abf3c32d"
LB = 2.2046226218

def uuid7():
    ms = int(time.time() * 1000)
    b = bytearray(ms.to_bytes(6, "big") + secrets.token_bytes(10))
    b[6] = (b[6] & 0x0F) | 0x70; b[8] = (b[8] & 0x3F) | 0x80
    h = b.hex(); time.sleep(0.002)
    return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:]}"

def q(s): return "null" if s is None else "'" + str(s).replace("'", "''") + "'"

CUSTOM = {  # nombre → (músculo, secundarios, equipo, inferior)
    "pendulo": ("Sentadilla péndulo (máquina)", "cuadriceps", ["gluteos"], "maquina", True),
    "lateral": ("Elevación lateral (máquina)", "hombros", [], "maquina", False),
    "cadera": ("Empuje de cadera (máquina)", "gluteos", ["isquiotibiales"], "maquina", True),
    "lumbar": ("Extensión de espalda (máquina)", "lumbar", ["gluteos", "isquiotibiales"], "maquina", True),
}
custom_ids = {k: uuid7() for k in CUSTOM}

def S(lb, reps, warm=False): return {"w": None if lb is None else round(lb / LB, 4), "r": reps, **({"t": 1} if warm else {})}

DAY_A = ("Lunes y jueves", "Planta baja: piernas (1–4) · Planta alta: torso y brazos (5–11)", [
    (custom_ids["pendulo"], 120, None, [S(50, 12)] * 3),
    ("extension_de_piernas", 90, None, [S(80, 12, True), S(165, 12), S(165, 12)]),
    ("aductores_en_maquina", 60, None, [S(110, 12)] * 2),
    ("elevacion_de_talones_de_pie_en_maquina", 60, None, [S(100, 12)] * 3),
    ("press_inclinado_en_maquina", 120, None, [S(90, 12, True), S(120, 12), S(120, 11)]),
    ("peck_deck_aperturas_en_maquina", 60, None, [S(150, 12)] * 2),
    (custom_ids["lateral"], 60, None, [S(120, 12)] * 3),
    ("extension_de_triceps_en_maquina", 60, None, [S(80, 12), S(90, 12), S(100, 12)]),
    ("curl_predicador_en_maquina", 90, None, [S(100, 12)] * 2),
    ("curl_de_muneca_invertido_con_mancuerna", 60, None, [S(30, 18)] * 2),
    ("curl_de_muneca_palmas_arriba_con_mancuerna", 60, None, [S(30, 20)] * 2),
])
DAY_B = ("Martes y viernes", "Planta baja: piernas (1–3) · Planta alta: espalda y core (4–9)", [
    ("curl_femoral_sentado", 120, None, [S(100, 12), S(100, 9), S(100, 7)]),
    (custom_ids["cadera"], 120, None, [S(140, 12)] * 2),
    ("abductores_en_maquina", 60, None, [S(170, 12)] * 2),
    ("jalon_al_pecho_agarre_ancho", 120, "En máquina", [S(80, 12, True), S(120, 12), S(120, 12)]),
    ("remo_en_maquina", 90, None, [S(115, 12)] * 2),
    ("aperturas_invertidas_en_maquina", 60, None, [S(150, 7)] * 2),
    (custom_ids["lumbar"], 90, None, [S(40, 12), S(40, 12), S(40, 9)]),
    ("crunch_en_polea", 60, None, [S(140, 12)] * 2),
    ("elevacion_de_rodillas_acostado", 60, None, [S(None, 12)] * 2),
])

def rep_range(plan):
    m = max(p["r"] for p in plan if p.get("t", 0) != 1)
    return (8, 12) if m <= 12 else (m - 4, m)

stmts = []
# 1) Borrar datos de prueba (soft delete para que el teléfono lo reciba al sincronizar) y récords del servidor.
stmts += [
    f"update routines set deleted = true where user_id = '{UID}' and name = 'ejemplo'",
    f"update routine_exercises set deleted = true where user_id = '{UID}' and routine_id in (select id from routines where user_id = '{UID}' and name = 'ejemplo')",
    f"update workout_sets set deleted = true where user_id = '{UID}' and workout_id in (select id from workouts where user_id = '{UID}' and name = 'ejemplo')",
    f"update workout_exercises set deleted = true where user_id = '{UID}' and workout_id in (select id from workouts where user_id = '{UID}' and name = 'ejemplo')",
    f"update workouts set deleted = true where user_id = '{UID}' and name = 'ejemplo'",
    f"delete from personal_records where user_id = '{UID}'",
]
# 2) Ejercicios personalizados (máquinas que no están en el catálogo).
for k, (name, pm, sec, eq, lower) in CUSTOM.items():
    arr = "'{" + ",".join(sec) + "}'::text[]"
    stmts.append(f"insert into exercises (id, owner_id, name, primary_muscle, secondary_muscles, equipment, tracking, is_lower_body) values "
                 f"({q(custom_ids[k])}, '{UID}', {q(name)}, {q(pm)}, {arr}, {q(eq)}, 'weight_reps', {str(lower).lower()})")
# 3) Rutinas.
for pos, (name, notes, items) in enumerate([DAY_A, DAY_B]):
    rid = uuid7()
    stmts.append(f"insert into routines (id, user_id, name, notes, position) values ('{rid}', '{UID}', {q(name)}, {q(notes)}, {pos})")
    for i, (ex, rest, note, plan) in enumerate(items):
        lo, hi = rep_range(plan)
        stmts.append("insert into routine_exercises (id, routine_id, user_id, exercise_id, position, rest_sec, target_sets, rep_min, rep_max, notes, set_plan) values "
                     f"('{uuid7()}', '{rid}', '{UID}', {q(ex)}, {i}, {rest}, {len(plan)}, {lo}, {hi}, {q(note)}, {q(json.dumps(plan, separators=(',', ':')))}::jsonb)")

if "--dry" in sys.argv:
    print("\n".join(stmts)); sys.exit()
r = nsql.post({"queries": [{"query": s, "params": []} for s in stmts]})  # una transacción
print("OK" if "results" in r else json.dumps(r)[:800], len(stmts), "sentencias")
