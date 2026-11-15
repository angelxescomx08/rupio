#!/usr/bin/env python3
"""
Cuenta DEMO con datos extremos pero realistas: 8 años entrenando, para sentir la app "llena".

Historia del atleta (todo inventado):
  · nov 2018 – oct 2019  Principiante: cuerpo completo 3 días (lun/mié/vie). Sube rapidísimo.
  · nov 2019 – jun 2021  Torso/pierna 4 días. Gimnasios cerrados por COVID mar–jun 2020 (pierde fuerza y la recupera).
  · jul 2021 – feb 2024  Empuje/jalón/pierna 6 días. Vacaciones jul 2022. Viaje a Madrid en mayo 2023 (otra zona horaria).
  · mar 2024 – hoy       Torso/pierna + brazos 5 días, más máquinas, superseries de brazos, drop sets.
  Peso corporal casi diario con etapas de volumen y definición; descargas cada 8 semanas; navidades sin entrenar;
  faltas al azar. La fuerza sigue una curva de rendimientos decrecientes (los primeros años rinden más) y cada
  ejercicio progresa con doble progresión (primero repeticiones, luego peso), en libras "de gimnasio".

Sube todo por la Data API con el JWT de la cuenta (el mismo camino que la app). Si la cuenta ya existe, la vacía antes.
Uso:  DEMO_EMAIL=... DEMO_PASSWORD=... python3 demo_extremo.py
"""
import json, math, os, random, statistics, sys, time, uuid
import urllib.request, urllib.error, urllib.parse, ssl
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

AUTH = "https://ep-wild-art-bdlip6s4.neonauth.c-8.us-east-2.aws.neon.tech/neondb/auth"
API = "https://ep-wild-art-bdlip6s4.apirest.c-8.us-east-2.aws.neon.tech/neondb/rest/v1"
EMAIL = os.environ.get("DEMO_EMAIL", "demo.extremo@rupio.test")
PASSWORD = os.environ["DEMO_PASSWORD"]
NAME = "Demo Extremo"
MX = "America/Mexico_City"
TODAY = date.fromisoformat(os.environ.get("DEMO_TODAY", datetime.now(ZoneInfo(MX)).date().isoformat()))
LB = 0.45359237
rnd = random.Random(20181119)
ca = os.path.expanduser("~/.ccr/ca-bundle.crt")
ctx = ssl.create_default_context(cafile=ca) if os.path.exists(ca) else None


# ------------------------------------------------------------------ HTTP / auth
def http(method, url, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json", "Accept": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=120) as r:
            txt = r.read().decode(); return r.status, (json.loads(txt) if txt else None), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace")[:500], dict(e.headers)


def login():
    origin = {"Origin": "https://" + urllib.parse.urlparse(AUTH).netloc}
    s, body, h = http("POST", AUTH + "/sign-in/email", {"email": EMAIL, "password": PASSWORD, "rememberMe": True}, origin)
    created = False
    if s != 200:
        s, body, h = http("POST", AUTH + "/sign-up/email", {"email": EMAIL, "password": PASSWORD, "name": NAME}, origin)
        assert s == 200, ("registro", s, body); created = True
    cookie = "; ".join(v.split(";")[0] for k, v in h.items() if k.lower() == "set-cookie")
    s, sess, h2 = http("GET", AUTH + "/get-session", headers={**origin, "Cookie": cookie})
    jwt = next(v for k, v in h2.items() if k.lower() == "set-auth-jwt")
    return sess["user"]["id"], jwt, created


# ------------------------------------------------------------------ utilidades
def uuid7(ms):
    """UUIDv7 con la marca de tiempo del evento (como la app)."""
    r = rnd.getrandbits(74)
    v = (ms & ((1 << 48) - 1)) << 80 | 0x7 << 76 | (r >> 62) << 64 | 0b10 << 62 | (r & ((1 << 62) - 1))
    return str(uuid.UUID(int=v))


def iso(dt): return dt.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def lerp_points(points, d):
    for (d0, v0), (d1, v1) in zip(points, points[1:]):
        if d0 <= d <= d1: return v0 + (v1 - v0) * (d - d0).days / max(1, (d1 - d0).days)
    return points[-1][1] if d > points[-1][0] else points[0][1]


# ------------------------------------------------------------------ peso corporal (kg) por etapas
BW_TREND = [(date(2018, 11, 19), 68.5), (date(2020, 3, 20), 77.5), (date(2020, 6, 28), 79.6), (date(2020, 11, 30), 75.0),
            (date(2022, 6, 30), 81.5), (date(2023, 4, 30), 76.2), (date(2024, 9, 30), 83.4), (date(2025, 4, 30), 77.6), (TODAY, 80.1)]


def bw_trend(d):
    v = lerp_points(BW_TREND, d)
    # Navidad: +1.2 kg que se va en ~3 semanas
    for y in range(2018, TODAY.year + 1):
        x = (d - date(y, 12, 24)).days
        if 0 <= x <= 30: v += 1.2 * (min(x, 9) / 9) * math.exp(-max(0, x - 9) / 8)
    return v


def cutting(d):
    return lerp_points(BW_TREND, d + timedelta(days=14)) < lerp_points(BW_TREND, d) - 0.05


# ------------------------------------------------------------------ catálogo usado: (inicio, avanzado, incremento) en lb
#  inicio/avanzado = 1RM estimado del principiante y del atleta con años. Mancuernas: por mancuerna.
S = {
    "press_de_banca_con_barra": (95, 255, 5), "sentadilla_con_barra": (115, 340, 10), "peso_muerto": (155, 425, 10),
    "remo_con_barra": (85, 225, 5), "press_militar_con_barra": (65, 150, 5), "press_de_hombros_con_mancuernas": (25, 70, 5),
    "press_inclinado_con_mancuernas": (30, 90, 5), "curl_de_biceps_con_barra": (45, 120, 5), "curl_con_barra_z": (45, 115, 5),
    "curl_martillo": (20, 55, 5), "curl_de_biceps_con_mancuernas": (18, 50, 5), "jalon_al_pecho_agarre_ancho": (80, 210, 10),
    "jalon_al_pecho_agarre_cerrado": (85, 220, 10), "prensa_de_piernas": (200, 720, 20), "peso_muerto_rumano": (105, 300, 10),
    "extension_de_triceps_con_cuerda": (35, 95, 5), "extension_de_triceps_con_barra_v": (40, 110, 5),
    "extension_de_triceps_con_mancuerna_sobre_la_cabeza": (25, 70, 5), "elevaciones_laterales_con_mancuernas": (12, 35, 5),
    "elevacion_lateral_en_polea": (10, 30, 5), "curl_femoral_acostado": (60, 170, 10), "curl_femoral_sentado": (70, 190, 10),
    "elevacion_de_talones_de_pie_en_maquina": (120, 380, 10), "elevacion_de_talones_sentado": (70, 230, 10),
    "extension_de_piernas": (70, 220, 10), "extension_de_pierna_a_una_pierna": (30, 100, 5),
    "sentadilla_bulgara_con_mancuernas": (20, 75, 5), "peck_deck_aperturas_en_maquina": (70, 200, 10), "face_pull": (30, 80, 5),
    "remo_sentado_en_polea": (80, 230, 10), "remo_en_t_con_apoyo_de_pecho": (70, 230, 10), "press_de_hombros_en_maquina": (60, 180, 10),
    "sentadilla_hack_en_maquina": (140, 450, 10), "press_inclinado_en_maquina": (80, 250, 10), "press_de_pecho_en_maquina": (90, 280, 10),
    "aperturas_invertidas_en_maquina": (40, 130, 10), "hip_thrust_con_barra": (135, 450, 10), "crunch_en_polea": (50, 150, 10),
    "fondos_en_maquina": (90, 280, 10),
    # Con peso corporal (peso EXTRA en lb; la carga real incluye el cuerpo)
    "dominadas": (187, 285, 5), "fondos_para_triceps": (200, 330, 5),
}
REPS_ONLY = {"elevacion_de_piernas_colgado": (6, 20), "elevacion_de_piernas_acostado": (10, 25)}
DURATION = {"plancha": (30, 150)}
BODYWEIGHT = {"dominadas", "fondos_para_triceps"}


def E(ex, sets, rmin, rmax, rest=None, ss=None, drop=False):
    rest = rest or (150 if rmax <= 8 else 90 if rmax <= 12 else 60)
    return dict(ex=ex, sets=sets, rmin=rmin, rmax=rmax, rest=rest, ss=ss, drop=drop)


ROUTINES = {
    # fase 1
    "Cuerpo completo A": [E("sentadilla_con_barra", 3, 8, 10), E("press_de_banca_con_barra", 3, 8, 10), E("remo_con_barra", 3, 8, 12),
                          E("press_de_hombros_con_mancuernas", 3, 10, 12), E("curl_de_biceps_con_barra", 2, 10, 12), E("plancha", 2, 1, 1, 60)],
    "Cuerpo completo B": [E("peso_muerto_rumano", 3, 8, 10), E("press_inclinado_con_mancuernas", 3, 8, 12), E("jalon_al_pecho_agarre_ancho", 3, 10, 12),
                          E("prensa_de_piernas", 3, 10, 12), E("extension_de_triceps_con_cuerda", 2, 10, 15), E("elevacion_de_piernas_acostado", 2, 10, 25, 60)],
    # fase 2
    "Torso A (fuerza)": [E("press_de_banca_con_barra", 4, 6, 8), E("remo_con_barra", 4, 6, 10), E("press_militar_con_barra", 3, 6, 10),
                         E("jalon_al_pecho_agarre_ancho", 3, 8, 12), E("elevaciones_laterales_con_mancuernas", 3, 12, 15),
                         E("curl_de_biceps_con_barra", 3, 8, 12), E("extension_de_triceps_con_cuerda", 3, 10, 15)],
    "Pierna A (fuerza)": [E("sentadilla_con_barra", 4, 5, 8), E("peso_muerto_rumano", 3, 8, 10), E("prensa_de_piernas", 3, 10, 12),
                          E("curl_femoral_acostado", 3, 10, 12), E("elevacion_de_talones_de_pie_en_maquina", 4, 10, 15), E("elevacion_de_piernas_colgado", 3, 8, 20, 60)],
    "Torso B (volumen)": [E("press_inclinado_con_mancuernas", 4, 8, 10), E("dominadas", 4, 6, 10), E("press_de_hombros_con_mancuernas", 3, 8, 12),
                          E("remo_sentado_en_polea", 3, 10, 12), E("peck_deck_aperturas_en_maquina", 3, 12, 15), E("curl_martillo", 3, 10, 12),
                          E("fondos_para_triceps", 3, 8, 12)],
    "Pierna B (volumen)": [E("peso_muerto", 3, 4, 6, 180), E("sentadilla_bulgara_con_mancuernas", 3, 8, 10), E("extension_de_piernas", 3, 12, 15),
                           E("curl_femoral_sentado", 3, 10, 12), E("elevacion_de_talones_sentado", 3, 12, 15), E("plancha", 3, 1, 1, 60)],
    # fase 3
    "Empuje": [E("press_de_banca_con_barra", 4, 5, 8), E("press_inclinado_con_mancuernas", 3, 8, 10), E("press_militar_con_barra", 3, 6, 10),
               E("peck_deck_aperturas_en_maquina", 3, 12, 15), E("elevaciones_laterales_con_mancuernas", 4, 12, 20, drop=True),
               E("extension_de_triceps_con_cuerda", 3, 10, 15), E("extension_de_triceps_con_mancuerna_sobre_la_cabeza", 3, 10, 12)],
    "Jalón": [E("peso_muerto", 3, 3, 6, 180), E("dominadas", 4, 6, 10), E("remo_con_barra", 4, 6, 10), E("jalon_al_pecho_agarre_cerrado", 3, 10, 12),
              E("face_pull", 3, 12, 15), E("curl_de_biceps_con_barra", 3, 8, 12), E("curl_martillo", 3, 10, 12)],
    "Pierna": [E("sentadilla_con_barra", 4, 5, 8), E("peso_muerto_rumano", 3, 8, 10), E("prensa_de_piernas", 4, 10, 15), E("extension_de_piernas", 3, 12, 15),
               E("curl_femoral_acostado", 3, 10, 12), E("elevacion_de_talones_de_pie_en_maquina", 4, 10, 15), E("crunch_en_polea", 3, 12, 15)],
    # fase 4 (actuales)
    "Torso A": [E("press_de_banca_con_barra", 3, 6, 8), E("remo_en_t_con_apoyo_de_pecho", 3, 8, 10), E("press_de_hombros_en_maquina", 3, 8, 12),
                E("jalon_al_pecho_agarre_ancho", 3, 10, 12), E("elevacion_lateral_en_polea", 3, 12, 15),
                E("curl_con_barra_z", 3, 10, 12, 90, ss="brazos1"), E("extension_de_triceps_con_barra_v", 3, 10, 12, 90, ss="brazos1")],
    "Pierna A": [E("sentadilla_hack_en_maquina", 4, 6, 10), E("peso_muerto_rumano", 3, 8, 10), E("extension_de_piernas", 3, 12, 15),
                 E("curl_femoral_sentado", 3, 10, 12), E("elevacion_de_talones_de_pie_en_maquina", 4, 10, 15)],
    "Torso B": [E("press_inclinado_en_maquina", 3, 8, 10), E("dominadas", 3, 6, 10), E("press_de_pecho_en_maquina", 3, 10, 12),
                E("remo_sentado_en_polea", 3, 10, 12), E("aperturas_invertidas_en_maquina", 3, 12, 15), E("face_pull", 3, 12, 15)],
    "Pierna B": [E("prensa_de_piernas", 4, 8, 12), E("hip_thrust_con_barra", 3, 8, 10), E("curl_femoral_acostado", 3, 10, 12),
                 E("extension_de_pierna_a_una_pierna", 2, 12, 15), E("elevacion_de_talones_sentado", 3, 12, 15), E("crunch_en_polea", 3, 12, 15)],
    "Brazos y hombro": [E("curl_de_biceps_con_mancuernas", 3, 10, 12, 75, ss="b1"), E("extension_de_triceps_con_cuerda", 3, 10, 12, 75, ss="b1"),
                        E("curl_martillo", 3, 10, 12, 75, ss="b2"), E("fondos_en_maquina", 3, 10, 12, 75, ss="b2"),
                        E("elevaciones_laterales_con_mancuernas", 4, 12, 20, drop=True), E("plancha", 3, 1, 1, 60)],
}
# (inicio, fin, {día de la semana: rutina}, hora de entreno)
PHASES = [
    (date(2018, 11, 19), date(2019, 10, 31), "fb", 18),
    (date(2019, 11, 4), date(2021, 6, 30), {0: "Torso A (fuerza)", 1: "Pierna A (fuerza)", 3: "Torso B (volumen)", 4: "Pierna B (volumen)"}, 19),
    (date(2021, 7, 5), date(2024, 2, 29), {0: "Empuje", 1: "Jalón", 2: "Pierna", 3: "Empuje", 4: "Jalón", 5: "Pierna"}, 7),
    (date(2024, 3, 4), TODAY, {0: "Torso A", 1: "Pierna A", 3: "Torso B", 4: "Pierna B", 5: "Brazos y hombro"}, 18),
]
CURRENT = ["Torso A", "Pierna A", "Torso B", "Pierna B", "Brazos y hombro"]
BREAKS = [(date(2020, 3, 23), date(2020, 6, 28)), (date(2022, 7, 4), date(2022, 7, 17))]
MADRID = (date(2023, 5, 15), date(2023, 5, 28))
NOTES = ["Muy buena sesión 💪", "Dormí poco, me sentí pesado", "Gym lleno, cambié el orden", "Me molestó un poco el hombro",
         "¡Récord!", "Técnica más limpia hoy", "Entrené con mi hermano", "Poco tiempo, sesión rápida"]


def build(uid):
    zone_mx = ZoneInfo(MX)
    rnd_skip = random.Random(7)
    sick = set()
    for y in range(2018, TODAY.year + 1):
        for _ in range(2):
            d0 = date(y, 1, 1) + timedelta(days=rnd_skip.randrange(0, 360))
            for k in range(rnd_skip.randrange(3, 6)): sick.add(d0 + timedelta(days=k))

    # Rutinas
    routines, routine_ex, rid = [], [], {}
    t0 = datetime(2018, 11, 18, 20, tzinfo=zone_mx)
    order = CURRENT + [n for n in ROUTINES if n not in CURRENT]
    for pos, name in enumerate(order):
        created = t0 if name.startswith("Cuerpo") else datetime.combine(next(p[0] for p in PHASES if (isinstance(p[2], dict) and name in p[2].values()) or (p[2] == "fb" and name.startswith("Cuerpo"))) - timedelta(days=1), datetime.min.time(), zone_mx)
        r_id = uuid7(int(created.timestamp() * 1000)); rid[name] = r_id
        routines.append(dict(id=r_id, user_id=uid, name=name, notes=None, position=pos, created_at=iso(created), deleted=False))
        ss_ids = {}
        for i, e in enumerate(ROUTINES[name]):
            ssid = ss_ids.setdefault(e["ss"], uuid7(int(created.timestamp() * 1000))) if e["ss"] else None
            routine_ex.append(dict(id=uuid7(int(created.timestamp() * 1000)), routine_id=r_id, user_id=uid, exercise_id=e["ex"], position=i,
                                   superset_id=ssid, superset_rest_mode=0, superset_rest_sec=None, rest_sec=e["rest"], target_sets=e["sets"],
                                   rep_min=e["rmin"], rep_max=max(e["rmin"], e["rmax"]), notes=None, deleted=False, set_plan=None))

    # Estado de progresión por ejercicio
    state = {}           # ex -> peso de trabajo en lb (o reps/segundos objetivo)
    age = 0              # sesiones de entrenamiento acumuladas
    detrain = 0.0        # pérdida temporal por la pausa (se recupera con "memoria muscular")
    fb_toggle = 0
    W, WE, WS = [], [], []
    d = PHASES[0][0]
    week_idx = 0
    while d <= TODAY:
        phase = next((p for p in PHASES if p[0] <= d <= p[1]), None)
        in_break = any(b0 <= d <= b1 for b0, b1 in BREAKS)
        if in_break and d.weekday() == 0: detrain = min(0.12, detrain + 0.012)
        week_idx = (d - PHASES[0][0]).days // 7
        name = None
        if phase and not in_break:
            sched = phase[2]
            if sched == "fb":
                if d.weekday() in (0, 2, 4): name = "Cuerpo completo A" if fb_toggle % 2 == 0 else "Cuerpo completo B"
            else:
                name = sched.get(d.weekday())
        madrid = MADRID[0] <= d <= MADRID[1]
        if madrid and d.weekday() not in (0, 2, 4): name = None
        holiday = (d.month == 12 and d.day >= 24) or (d.month == 1 and d.day == 1)
        if name and (holiday or d in sick or (rnd.random() < 0.07 and d != TODAY and d != TODAY - timedelta(days=1))): name = None
        if d == TODAY and phase and isinstance(phase[2], dict) and d.weekday() not in phase[2]: name = None
        if name:
            if name.startswith("Cuerpo"): fb_toggle += 1
            tzname = "Europe/Madrid" if madrid else MX
            zone = ZoneInfo(tzname)
            deload = week_idx % 8 == 7
            hour = phase[3] + rnd.choice([-1, 0, 0, 0, 1])
            start = datetime(d.year, d.month, d.day, hour, rnd.randrange(0, 50), tzinfo=zone)
            age += 1
            detrain = max(0.0, detrain - 0.006) if not in_break else detrain
            p = 1 - math.exp(-age / 340)
            day_form = rnd.gauss(0, 0.022) - (0.02 if cutting(d) else 0) - detrain - (0.03 if madrid else 0)
            bw_lb = bw_trend(d) / LB
            wid = uuid7(int(start.timestamp() * 1000))
            cursor = start + timedelta(minutes=4)
            vol = 0.0; nsets = 0; nreps = 0
            for pos, e in enumerate(ROUTINES[name]):
                ex = e["ex"]; weid = uuid7(int(cursor.timestamp() * 1000))
                ssid = None
                if e["ss"]: ssid = str(uuid.uuid5(uuid.UUID(wid), e["ss"]))
                WE.append(dict(id=weid, workout_id=wid, user_id=uid, exercise_id=ex, position=pos, superset_id=ssid, superset_rest_mode=0,
                               superset_rest_sec=None, rest_sec=e["rest"], rep_min=e["rmin"], rep_max=max(e["rmin"], e["rmax"]),
                               notes="Subir peso la próxima" if rnd.random() < 0.01 else None, deleted=False, set_plan=None))
                sets = []
                nwork = max(1, e["sets"] - (1 if deload else 0))
                if ex in DURATION:
                    lo, hi = DURATION[ex]
                    target = lo + (hi - lo) * p * (1 + day_form)
                    for k in range(nwork):
                        sets.append((0, None, None, int(max(15, round((target - k * 8) / 5) * 5))))
                elif ex in REPS_ONLY:
                    lo, hi = REPS_ONLY[ex]
                    cap = lo + (hi - lo) * p * (1 + day_form)
                    for k in range(nwork): sets.append((0, None, max(1, int(cap - k * 1.5 - rnd.random() * 2)), None))
                else:
                    a, b, inc = S[ex]
                    cap = (a + (b - a) * p) * (1 + day_form)                  # 1RM estimado de hoy (lb)
                    bw_ex = ex in BODYWEIGHT
                    if ex not in state:
                        load = cap / (1 + (e["rmin"] + 1) / 30) * 0.92
                        state[ex] = max(0, math.floor((load - (bw_lb if bw_ex else 0)) / inc) * inc) if bw_ex else max(inc, math.floor(load / inc) * inc)
                    w = state[ex] * (0.85 if deload else 1)
                    w = (math.floor(w / inc) * inc) if bw_ex else max(inc, math.floor(w / inc) * inc)
                    total = w + (bw_lb if bw_ex else 0)
                    possible = 30 * (cap / total - 1)
                    # calentamientos en el primer ejercicio de barra/máquina pesada
                    if pos == 0 and not bw_ex:
                        for frac, r in ((0.5, 8), (0.75, 4)):
                            sets.append((1, max(inc, math.floor(w * frac / inc) * inc), r, None))
                    elif pos == 1 and e["rmax"] <= 10 and not bw_ex:
                        sets.append((1, max(inc, math.floor(w * 0.6 / inc) * inc), 6, None))
                    reps_done = []
                    for k in range(nwork):
                        rir = 0 if (k == nwork - 1 and rnd.random() < 0.25) else rnd.choice([1, 1, 2])
                        r = int(possible - rir - k * rnd.uniform(0.5, 1.3))
                        r = max(1, min(e["rmax"] + (1 if rir == 0 else 0), r))
                        st = 3 if (rir == 0 and e["rmax"] >= 12) else 0
                        reps_done.append(r); sets.append((st, w, r, None))
                    if e["drop"] and not deload:
                        sets.append((2, max(inc, math.floor(w * 0.7 / inc) * inc), rnd.randint(8, 12), None))
                    # doble progresión
                    if not deload:
                        if min(reps_done) >= e["rmax"]: state[ex] = w + inc
                        elif reps_done[0] < e["rmin"] - 1: state[ex] = max(0 if bw_ex else inc, w - inc)
                for k, (st, wlb, reps, dur) in enumerate(sets):
                    cursor += timedelta(seconds=(35 if st == 1 else 45) + (e["rest"] if k else 20) + rnd.randrange(-10, 25))
                    kg = None if wlb is None else round(wlb * LB, 4)
                    if ex in BODYWEIGHT and wlb == 0: kg = None
                    rpe = round(rnd.choice([7.5, 8, 8, 8.5, 9, 9.5]), 1) if (name in CURRENT and pos == 0 and st == 0 and k == 2) else None
                    sid = uuid7(int(cursor.timestamp() * 1000))
                    WS.append(dict(id=sid, workout_exercise_id=weid, workout_id=wid, user_id=uid, exercise_id=ex, position=k, set_type=st,
                                   weight_kg=kg, reps=reps, duration_sec=dur, rpe=rpe, completed=True, completed_at=iso(cursor),
                                   performed_on=start.date().isoformat(), deleted=False))
                    if st != 1:
                        nsets += 1; nreps += reps or 0; vol += max(0.0, kg or 0) * (reps or 0)
                cursor += timedelta(seconds=60)
            end = cursor + timedelta(minutes=rnd.randrange(1, 6))
            if d == TODAY and end > datetime.now(timezone.utc): break
            W.append(dict(id=wid, user_id=uid, routine_id=rid[name], name=name, started_at=iso(start), ended_at=iso(end), time_zone=tzname,
                          local_date=start.date().isoformat(), duration_sec=int((end - start).total_seconds()), total_volume_kg=round(vol, 3),
                          total_sets=nsets, total_reps=nreps, notes=(rnd.choice(NOTES) if rnd.random() < 0.06 else ("Gym en Madrid 🇪🇸" if madrid else None)),
                          deleted=False))
        d += timedelta(days=1)

    # Peso corporal casi diario (por la mañana)
    BWS = []
    d = PHASES[0][0]
    while d <= TODAY:
        if rnd.random() < 0.86 or d == TODAY:
            v = bw_trend(d) + rnd.gauss(0, 0.42) + 0.3 * math.sin(d.toordinal() * 2 * math.pi / 7)
            BWS.append(dict(user_id=uid, measured_on=d.isoformat(), weight_kg=round(v, 1), deleted=False))
        d += timedelta(days=1)
    return routines, routine_ex, W, WE, WS, BWS


def records(uid, WS):
    """Mismas reglas que WorkoutDao.recordMaxes / recordHolder (la app)."""
    tracking = {**{k: "weight_reps" for k in S}, **{k: "weighted_bodyweight" for k in BODYWEIGHT}, **{k: "reps_only" for k in REPS_ONLY},
                **{k: "duration" for k in DURATION}}
    def e1rm(w, r):
        if r == 1: return w
        if 2 <= r <= 10: return (w * (1 + r / 30.0) + w * 36.0 / (37 - r)) / 2
        if 11 <= r <= 12: return w * (1 + r / 30.0)
        return None
    out = []
    by = {}
    for s in WS:
        if s["set_type"] != 1: by.setdefault(s["exercise_id"], []).append(s)
    for ex, sets in by.items():
        sets.sort(key=lambda s: s["completed_at"])
        tr = tracking[ex]
        def val(kind, s):
            w = s["weight_kg"] or 0; r = s["reps"] or 0
            if kind == 1: return w if w > 0 and r > 0 else None
            if kind == 2: return e1rm(w, r) if w > 0 else None
            if kind == 3: return w * r if w > 0 and r > 0 else None
            if kind == 4: return r if r > 0 else None
            return s["duration_sec"] if (s["duration_sec"] or 0) > 0 else None
        kinds = [1, 2, 3, 4] if tr in ("weight_reps", "weighted_bodyweight") else [4] if tr in ("reps_only", "assisted_bodyweight") else [5]
        for k in kinds:
            vals = [(val(k, s), s) for s in sets]; vals = [(v, s) for v, s in vals if v]
            if not vals: continue
            best = max(v for v, _ in vals)
            holder = next(s for v, s in vals if v >= best - 1e-6)
            out.append(dict(user_id=uid, exercise_id=ex, kind=k, value=float(best), set_id=holder["id"], achieved_at=holder["completed_at"]))
    return out


def main():
    uid, jwt, created = login()
    H = {"Authorization": f"Bearer {jwt}"}
    print(("Cuenta creada" if created else "Cuenta existente"), uid)
    if not created:
        for t in ["workout_sets", "workout_exercises", "workouts", "routine_exercises", "routines", "personal_records", "body_weights"]:
            s, b, _ = http("DELETE", f"{API}/{t}?user_id=eq.{uid}", headers=H); assert s in (200, 204), (t, s, b)
    routines, routine_ex, W, WE, WS, BWS = build(uid)
    PR = records(uid, WS)
    profile = [dict(user_id=uid, display_name=NAME, weight_unit="lb", time_zone=MX, week_start=1, default_rest_sec=90, tutorial_done=True)]
    plan = [("profiles", "user_id", profile), ("routines", "id", routines), ("routine_exercises", "id", routine_ex), ("workouts", "id", W),
            ("workout_exercises", "id", WE), ("workout_sets", "id", WS), ("personal_records", "user_id,exercise_id,kind", PR),
            ("body_weights", "user_id,measured_on", BWS)]
    t0 = time.perf_counter(); lat = []
    for table, conflict, rows in plan:
        for i in range(0, len(rows), 400):
            t = time.perf_counter()
            s, b, _ = http("POST", f"{API}/{table}?on_conflict={conflict}", rows[i:i + 400], {**H, "Prefer": "resolution=merge-duplicates,return=minimal"})
            assert s in (200, 201, 204), (table, i, s, b); lat.append((time.perf_counter() - t) * 1000)
        print(f"  {table:18s} {len(rows):>7,} filas")
    total = sum(len(r) for _, _, r in plan)
    print(f"Subido: {total:,} filas en {time.perf_counter() - t0:.1f} s (lote p50 {statistics.median(lat):.0f} ms)")
    summary = dict(uid=uid, workouts=len(W), sets=len(WS), exercises=len(WE), routines=len(routines), records=len(PR), body_weights=len(BWS),
                   first=W[0]["local_date"], last=W[-1]["local_date"], total_rows=total,
                   bench_e1rm_lb=round(max(r["value"] for r in PR if r["exercise_id"] == "press_de_banca_con_barra" and r["kind"] == 2) / LB))
    print(json.dumps(summary, ensure_ascii=False, indent=1))
    json.dump(summary, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo_summary.json"), "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
