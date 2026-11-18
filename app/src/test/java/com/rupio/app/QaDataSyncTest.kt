package com.rupio.app

import androidx.test.core.app.ApplicationProvider
import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.data.local.*
import com.rupio.app.data.remote.DataApiClient
import com.rupio.app.data.remote.PendingDeletes
import com.rupio.app.data.remote.SyncEngine
import com.rupio.app.data.repo.Period
import com.rupio.app.domain.SupersetRestMode
import com.rupio.app.domain.Tracking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

/**
 * QA de datos: sincronización contra un servidor falso que se comporta como PostgREST + Postgres
 * (mismas reglas CHECK, misma marca de tiempo para todo un lote, fallos a voluntad), usuario extremo
 * y operaciones absurdas en el flujo de entrenamiento.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QaDataSyncTest {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val zone = ZoneId.of("America/Mexico_City")
    private lateinit var uid: String

    @Before fun setUp() = runBlocking {
        c.auth.useLocalMode("QA")
        uid = c.auth.currentUserId!!
        // Espera a que se cargue el catálogo (se siembra al iniciar la app).
        repeat(200) { if (c.db.exercises().catalogCount() >= 250) return@runBlocking; Thread.sleep(50) }
    }

    // ------------------------------------------------------------------ servidor falso

    /** PostgREST en memoria: upsert por on_conflict, GET con eq/gt/or/order/limit, DELETE, y reglas CHECK del esquema real. */
    class FakeServer {
        val tables = HashMap<String, LinkedHashMap<String, JsonObject>>()
        var clock: Instant = Instant.parse("2026-11-15T03:00:00Z")
        var failNext = 0                    // próximas N peticiones → 500
        var ioFailNext = 0                  // próximas N → sin red
        var garbageGet = false              // GET responde JSON roto
        var failWhen: ((Request) -> Boolean)? = null   // peticiones que cumplen esto → sin red
        var unauthorizedNext = 0            // próximas N → 401 (JWT vencido)
        val tokens = mutableListOf<String>()
        var requests = 0
        var serverNanos = 0L                // tiempo gastado DENTRO del servidor falso (no cuenta como tiempo de la app)
        private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx").withZone(ZoneOffset.UTC)
        private val rejectExercise = "rechazado_por_servidor"

        fun rows(t: String) = tables[t]?.values?.toList().orEmpty()

        private fun violates(table: String, r: JsonObject): Boolean {
            fun i(k: String) = r[k]?.jsonPrimitive?.intOrNull
            fun d(k: String) = r[k]?.jsonPrimitive?.doubleOrNull
            fun s(k: String) = r[k]?.jsonPrimitive?.contentOrNull
            return when (table) {
                "routines", "workouts", "exercises" -> (s("name")?.length ?: 0) !in 1..120 ||
                    (table == "workouts" && s("ended_at") != null && Instant.parse(s("ended_at")!!) < Instant.parse(s("started_at")!!))
                "routine_exercises" -> i("target_sets")!! !in 1..20 || i("rep_min")!! !in 1..100 || i("rep_max")!! !in 1..100 ||
                    i("rep_min")!! > i("rep_max")!! || i("rest_sec")!! !in 0..1800 || s("exercise_id") == rejectExercise
                "workout_exercises" -> i("rest_sec")!! !in 0..1800 || s("exercise_id") == rejectExercise
                "workout_sets" -> (i("reps")?.let { it !in 0..1000 } ?: false) || (d("rpe")?.let { it !in 1.0..10.0 } ?: false) ||
                    i("set_type")!! !in 0..3 || (i("duration_sec")?.let { it < 0 } ?: false) || s("exercise_id") == rejectExercise
                "body_weights" -> d("weight_kg")!! !in 20.0..400.0
                "personal_records" -> i("kind")!! !in 1..5
                "profiles" -> (s("display_name")?.length ?: 0) !in 1..80
                else -> false
            }
        }

        private fun resp(req: Request, code: Int, body: String) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()

        val interceptor = Interceptor { chain ->
            val req = chain.request(); requests++
            tokens += req.header("Authorization").orEmpty()
            if (ioFailNext > 0) { ioFailNext--; throw IOException("sin red") }
            if (failWhen?.invoke(req) == true) throw IOException("sin red")
            if (unauthorizedNext > 0) { unauthorizedNext--; return@Interceptor resp(req, 401, "{\"message\":\"JWT expired\"}") }
            if (failNext > 0) { failNext--; return@Interceptor resp(req, 500, "{\"message\":\"caído\"}") }
            val table = req.url.pathSegments.last()
            val t0 = System.nanoTime()
            try { synchronized(this) {
                when (req.method) {
                    "POST" -> {
                        val keys = req.url.queryParameter("on_conflict")!!.split(",")
                        val body = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
                        val batch = Json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
                        if (batch.any { violates(table, it) }) return@Interceptor resp(req, 400, "{\"code\":\"23514\",\"message\":\"check\"}")
                        clock = clock.plusMillis(1)                // Postgres: now() es el mismo para todo el lote
                        val ts = fmt.format(clock)
                        val t = tables.getOrPut(table) { LinkedHashMap() }
                        // Como Postgres (merge-duplicates): solo cambian las columnas que vienen; las demás se conservan.
                        batch.forEach { r -> val k = keys.joinToString("|") { k -> r[k]!!.jsonPrimitive.content }
                            t[k] = JsonObject((t[k] ?: JsonObject(emptyMap())) + r + ("updated_at" to JsonPrimitive(ts))) }
                        resp(req, 201, "")
                    }
                    "PATCH" -> {
                        val f = req.url.queryParameterNames.associateWith { req.url.queryParameter(it)!!.removePrefix("eq.") }
                        val body = Json.parseToJsonElement(Buffer().also { req.body!!.writeTo(it) }.readUtf8()).jsonObject
                        tables[table]?.entries?.forEach { e -> if (f.all { (k, v) -> e.value[k]?.jsonPrimitive?.content == v }) e.setValue(JsonObject(e.value + body)) }
                        resp(req, 204, "")
                    }
                    "DELETE" -> {
                        val f = req.url.queryParameterNames.associateWith { req.url.queryParameter(it)!!.removePrefix("eq.") }
                        tables[table]?.entries?.removeIf { (_, r) -> f.all { (k, v) -> r[k]?.jsonPrimitive?.content == v } }
                        resp(req, 204, "")
                    }
                    else -> {
                        if (garbageGet) return@Interceptor resp(req, 200, "[{\"id\": \"roto\", ")
                        var list = rows(table)
                        for (name in req.url.queryParameterNames) {
                            val v = req.url.queryParameter(name)!!
                            list = when {
                                name == "or" -> list.filter { evalOr(it, v) }
                                name in setOf("select", "order", "limit") -> list
                                v.startsWith("eq.") -> list.filter { it[name]?.jsonPrimitive?.content == v.removePrefix("eq.") }
                                v.startsWith("gt.") -> list.filter { cmp(it[name]!!.jsonPrimitive.content, v.removePrefix("gt."), name) > 0 }
                                else -> list
                            }
                        }
                        val order = req.url.queryParameter("order")?.split(",")?.map { it.removeSuffix(".asc") }.orEmpty()
                        list = list.sortedWith { a, b -> order.asSequence().map { k -> cmp(a[k]!!.jsonPrimitive.content, b[k]!!.jsonPrimitive.content, k) }.firstOrNull { it != 0 } ?: 0 }
                        val limit = req.url.queryParameter("limit")?.toInt() ?: Int.MAX_VALUE
                        resp(req, 200, JsonArray(list.take(limit)).toString())
                    }
                }
            } } finally { serverNanos += System.nanoTime() - t0 }
        }

        private fun cmp(a: String, b: String, col: String): Int = when (col) {
            "updated_at" -> Instant.parse(a.replace("+00:00", "Z")).compareTo(Instant.parse(b.replace("+00:00", "Z")))
            "kind" -> a.toInt().compareTo(b.toInt())
            else -> a.compareTo(b)
        }

        /** Evalúa el filtro or=(a.gt."x",and(a.eq."x",b.gt."y"),…) que genera la app. */
        private fun evalOr(r: JsonObject, expr: String): Boolean = split(expr.removePrefix("(").removeSuffix(")")).any { part ->
            if (part.startsWith("and(")) split(part.removePrefix("and(").removeSuffix(")")).all { cond(r, it) } else cond(r, part)
        }
        private fun cond(r: JsonObject, c: String): Boolean {
            val (col, op, raw) = c.split(".", limit = 3)
            val v = raw.removeSurrounding("\"")
            val x = cmp(r[col]!!.jsonPrimitive.content, v, col)
            return if (op == "gt") x > 0 else x == 0
        }
        private fun split(s: String): List<String> {
            val out = mutableListOf<String>(); var depth = 0; var q = false; val cur = StringBuilder()
            for (ch in s) {
                when {
                    ch == '"' -> { q = !q; cur.append(ch) }
                    !q && ch == '(' -> { depth++; cur.append(ch) }
                    !q && ch == ')' -> { depth--; cur.append(ch) }
                    !q && depth == 0 && ch == ',' -> { out += cur.toString(); cur.clear() }
                    else -> cur.append(ch)
                }
            }
            if (cur.isNotEmpty()) out += cur.toString()
            return out
        }
    }

    private fun engine(server: FakeServer, prefsUser: String = uid) = SyncEngine(
        app, c.db, DataApiClient(OkHttpClient.Builder().addInterceptor(server.interceptor).build()), c.auth, c.settings, PendingDeletes(app),
        jwtProvider = { "jwt-de-prueba" }, uidProvider = { prefsUser }, localOnly = { false },
    ).also { it.resetCursors() }

    // ------------------------------------------------------------------ datos de prueba

    private suspend fun seed(workouts: Int, perWorkout: Int = 6, setsPer: Int = 4): Int {
        val exs = c.db.exercises().getMany(listOf("press_de_banca_con_barra", "sentadilla_con_barra", "remo_con_barra", "curl_martillo",
            "prensa_de_piernas", "jalon_al_pecho_agarre_ancho", "peso_muerto_rumano", "elevaciones_laterales_con_mancuernas", "lagartijas", "plancha"))
        assertTrue("catálogo cargado", exs.size >= 8)
        val now = System.currentTimeMillis()
        var n = 0
        for (k in 0 until workouts) {
            val start = now - (workouts - k) * 26L * 3600_000L
            val day = Dates.localEpochDay(start, zone)
            val w = WorkoutEntity(Ids.uuidV7(start), uid, null, "Sesión $k", start, start + 3_000_000, zone.id, day, 3000, updatedAt = start)
            val wes = (0 until perWorkout).map { i -> WorkoutExerciseEntity(Ids.uuidV7(start), w.id, uid, exs[(k + i) % exs.size].id, i, updatedAt = start) }
            val sets = wes.flatMap { we -> (0 until setsPer).map { s ->
                WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, uid, we.exerciseId, s, setType = if (s == 0) 1 else 0, weightKg = 40.0 + k * 0.05 + s,
                    reps = 8 + (k + s) % 5, durationSec = if (we.exerciseId == "plancha") 30 + k % 30 else null, completed = true,
                    completedAt = start + s * 120_000L, performedOn = day, updatedAt = start)
            } }
            c.db.workouts().insertFull(w, wes, sets); n += sets.size
            c.db.workouts().upsertWorkout(w.copy(totalVolumeKg = sets.filter { it.setType != 1 }.sumOf { it.weightKg!! * it.reps!! },
                totalSets = sets.count { it.setType != 1 }, totalReps = sets.filter { it.setType != 1 }.sumOf { it.reps!! }))
        }
        return n
    }

    private suspend fun dirtyCount() = c.db.workouts().dirtySets(100_000).size + c.db.workouts().dirtyWorkouts(100_000).size +
        c.db.workouts().dirtyExercises(100_000).size + c.db.routines().dirtyRoutines(100_000).size + c.db.routines().dirtyExercises(100_000).size

    private suspend fun wipeLocal() {
        c.db.admin().clearWorkouts(); c.db.admin().clearRoutines(); c.db.admin().clearRecords()
    }

    // ------------------------------------------------------------------ pruebas de sincronización

    /** El error encontrado: lotes grandes subidos en pocos segundos → otro teléfono se quedaba sin parte de los datos. */
    @Test fun sincronizaSinHuecosConLotesGrandesYMismaMarcaDeTiempo() = runBlocking {
        val sets = seed(300)                                   // 7 200 series, 1 800 ejercicios, 300 entrenos
        val server = FakeServer()
        val e = engine(server)
        assertTrue("subida", e.sync())
        assertEquals(sets, server.rows("workout_sets").size)
        assertEquals(0, dirtyCount())

        // "Otro teléfono": base vacía, cursores en cero, baja todo.
        wipeLocal()
        val e2 = engine(server)
        val ms = measureTimeMillis { assertTrue("bajada", e2.sync()) }
        assertEquals("entrenos", 300, c.db.stats().workoutTotals(0, Int.MAX_VALUE).n)
        assertEquals("series completas tras bajar", sets, server.rows("workout_sets").count { r -> c.db.workouts().getSet(r["id"]!!.jsonPrimitive.content) != null })
        assertEquals("ejercicios completos tras bajar", 1_800, server.rows("workout_exercises").count { r -> c.db.workouts().workoutIdOf(r["id"]!!.jsonPrimitive.content) != null })
        println("Bajada de $sets series: $ms ms, ${server.requests} peticiones")
        // Una segunda sincronización no vuelve a bajar todo (solo la ventana de 5 s) y no duplica nada.
        val before = server.requests
        assertTrue(e2.sync())
        assertTrue("la segunda sync es incremental (${server.requests - before} peticiones)", server.requests - before < 40)
    }

    @Test fun datosQueElServidorRechazariaSeCorrigenAntesDeSubir() = runBlocking {
        val t = System.currentTimeMillis()
        val r1 = RoutineEntity(Ids.uuidV7(t), uid, "   ", null, 0, t, t)                       // nombre vacío
        val r2 = RoutineEntity(Ids.uuidV7(t), uid, "x".repeat(300), null, 1, t, t)              // 300 letras
        c.db.routines().saveRoutine(r1, listOf(RoutineExerciseEntity(Ids.uuidV7(t), r1.id, uid, "curl_martillo", 0, targetSets = 25, repMin = 50, repMax = 10,
            restSec = 5000, updatedAt = t)), t)
        c.db.routines().saveRoutine(r2, emptyList(), t)
        val day = Dates.localEpochDay(t, zone)
        val w = WorkoutEntity(Ids.uuidV7(t), uid, null, "", t, t - 60_000, zone.id, day, -60, updatedAt = t)   // reloj atrasado
        val we = WorkoutExerciseEntity(Ids.uuidV7(t), w.id, uid, "curl_martillo", 0, restSec = 99_999, updatedAt = t)
        val s = WorkoutSetEntity(Ids.uuidV7(t), we.id, w.id, uid, "curl_martillo", 0, setType = 9, weightKg = Double.NaN, reps = 50_000,
            durationSec = -5, rpe = 42.0, completed = true, performedOn = day, updatedAt = t)
        c.db.workouts().insertFull(w, listOf(we), listOf(s))
        val server = FakeServer()
        assertTrue(engine(server).sync())
        assertEquals(0, dirtyCount())
        assertEquals("Rutina", server.rows("routines").first { it["id"]!!.jsonPrimitive.content == r1.id }["name"]!!.jsonPrimitive.content)
        assertEquals(120, server.rows("routines").first { it["id"]!!.jsonPrimitive.content == r2.id }["name"]!!.jsonPrimitive.content.length)
        val re = server.rows("routine_exercises").single()
        assertEquals(20, re["target_sets"]!!.jsonPrimitive.int); assertTrue(re["rep_min"]!!.jsonPrimitive.int <= re["rep_max"]!!.jsonPrimitive.int)
        val ss = server.rows("workout_sets").single()
        assertEquals(JsonNull, ss["weight_kg"]); assertEquals(JsonNull, ss["reps"]); assertEquals(JsonNull, ss["rpe"])
    }

    @Test fun unaFilaRechazadaNoBloqueaLaSincronizacion() = runBlocking {
        seed(20)
        val t = System.currentTimeMillis(); val day = Dates.localEpochDay(t, zone)
        val w = WorkoutEntity(Ids.uuidV7(t), uid, null, "Con fila mala", t, t + 1000, zone.id, day, 1, updatedAt = t)
        val bad = WorkoutExerciseEntity(Ids.uuidV7(t), w.id, uid, "rechazado_por_servidor", 0, updatedAt = t)
        c.db.workouts().insertFull(w, listOf(bad), emptyList())
        val server = FakeServer()
        val e = engine(server)
        assertTrue("la sync termina aunque una fila sea rechazada", e.sync())
        assertEquals(20 * 6 * 4, server.rows("workout_sets").size)
        assertEquals("solo la fila mala queda pendiente", listOf(bad.id), c.db.workouts().dirtyExercises(100).map { it.id })
        assertEquals(com.rupio.app.data.remote.SyncProblem.REJECTED, e.status.value.problem)
        assertTrue(e.status.value.lastError!!.contains("no se pudo subir"))
        // v1.6: lo rechazado ya no se reintenta en cada sincronización (antes "para siempre")…
        var before = server.requests
        assertTrue(e.sync())
        val withRejected = server.requests - before
        // …hasta que la persona elige "Descartar": deja de estar pendiente.
        val r = e.describeRejected().single()
        assertTrue(r.title, r.title.startsWith("Ejercicio en «Con fila mala»"))
        e.discardRejected(r.table, r.key)
        assertTrue(c.db.workouts().dirtyExercises(100).isEmpty())
        assertEquals(null, e.status.value.problem)
        before = server.requests
        assertTrue(e.sync())
        assertEquals("la fila rechazada no se volvió a mandar (misma cantidad de peticiones que sin ella)", server.requests - before, withRejected)
    }

    /**
     * v1.6 (R1): la primera descarga guarda su avance en CADA página (si la señal se corta a media tabla, no empieza de
     * cero) y, si el JWT vence a media descarga (401), pide otro y repite esa petición una vez.
     */
    @Test fun primeraDescargaGuardaAvanceYRenuevaElJwt() = runBlocking {
        val sets = seed(100)                                   // 2 400 series = 2 páginas de 2 000
        val server = FakeServer()
        assertTrue(engine(server).sync())
        wipeLocal()
        var refreshed = 0
        val e2 = SyncEngine(app, c.db, DataApiClient(OkHttpClient.Builder().addInterceptor(server.interceptor).build()), c.auth, c.settings, PendingDeletes(app),
            jwtProvider = { "jwt-viejo" }, uidProvider = { uid }, localOnly = { false }, jwtRefresher = { refreshed++; "jwt-nuevo" }).also { it.resetCursors() }
        // La señal se corta al pedir la 2.ª página de series (la que lleva el filtro por llave "or").
        server.failWhen = { it.url.pathSegments.last() == "workout_sets" && it.method == "GET" && it.url.queryParameter("or") != null }
        assertFalse(e2.sync())
        assertEquals(com.rupio.app.data.remote.SyncProblem.OFFLINE, e2.status.value.problem)
        val prefs = app.getSharedPreferences("rupio_sync", android.content.Context.MODE_PRIVATE)
        assertTrue("el avance de la tabla a medias quedó guardado", prefs.getString("cursor_workout_sets", null) != null)
        assertEquals("la 1.ª página ya está en el teléfono", 2_000, server.rows("workout_sets").count { r -> c.db.workouts().getSet(r["id"]!!.jsonPrimitive.content) != null })
        // Vuelve la señal, pero el JWT ya venció: el servidor responde 401 una vez.
        server.failWhen = null; server.unauthorizedNext = 1
        assertTrue(e2.sync())
        assertEquals("se pidió un JWT nuevo una sola vez", 1, refreshed)
        assertEquals("Bearer jwt-nuevo", server.tokens.last())
        assertEquals(sets, server.rows("workout_sets").count { r -> c.db.workouts().getSet(r["id"]!!.jsonPrimitive.content) != null })
        assertEquals(null, e2.status.value.problem)
    }

    /** Tamaño de la cuenta demo (8 años): primera sincronización en un teléfono vacío. */
    @Test fun primeraSincronizacionDeUnaCuentaDe8Anios() = runBlocking {
        val sets = seed(1_700)                                 // 40 800 series, 10 200 ejercicios, 1 700 entrenos
        val server = FakeServer()
        assertTrue(engine(server).sync())
        wipeLocal()
        val e2 = engine(server)
        val before = server.requests; server.serverNanos = 0
        val ms = measureTimeMillis { assertTrue(e2.sync()) }
        val appMs = ms - server.serverNanos / 1_000_000
        val tot = c.db.stats().workoutTotals(0, Int.MAX_VALUE)
        assertEquals(1_700, tot.n); assertEquals("series efectivas", 1_700 * 6 * 3, tot.sets)
        assertEquals("todas las series", sets, c.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM workout_sets WHERE deleted = 0").use { it.moveToFirst(); it.getInt(0) })
        println("Primera bajada de ${sets + 10_200 + 1_700} filas: app $appMs ms (+ servidor falso ${server.serverNanos / 1_000_000} ms), ${server.requests - before} peticiones")
    }

    /** Cambiar de cuenta en el mismo teléfono: nada de la cuenta anterior se ve ni se sube a la nueva. */
    @Test fun cambiarDeCuentaNoMezclaDatos() = runBlocking {
        seed(3)
        c.bodyWeight.log(java.time.LocalDate.now(zone), 80.0)
        c.claimLocalData(uid)                                          // mismo dueño: no se borra nada
        assertEquals(3, c.db.stats().workoutTotals(0, Int.MAX_VALUE).n)
        assertEquals(1, c.db.bodyWeights().dirty(uid, 10).size)

        // Defensa en la subida: aunque quedaran pesos de A en el teléfono, la cuenta B no los sube.
        val server = FakeServer()
        val otra = "0193f0aa-0000-7000-8000-000000000b0b"
        c.db.admin().clearWorkouts()
        assertTrue(engine(server, prefsUser = otra).sync())
        assertTrue("no se sube el peso de otra cuenta", server.rows("body_weights").isEmpty())

        // Entra otra cuenta sin haber limpiado (la app se cerró a medio "Cerrar sesión") → se borra todo lo local.
        seed(2)
        c.claimLocalData(otra)
        assertEquals("entrenos de la cuenta anterior", 0, c.db.stats().workoutTotals(0, Int.MAX_VALUE).n)
        assertEquals("pesos de la cuenta anterior", null, c.db.bodyWeights().firstDay(uid))
        c.claimLocalData(uid)                                          // deja el dueño como estaba para las demás pruebas
    }

    @Test fun caidasDeRedYServidorNoPierdenNada() = runBlocking {
        val sets = seed(40)
        val server = FakeServer()
        val e = engine(server)
        server.failNext = 1_000; assertFalse(e.sync()); assertEquals("nada se marca como subido si falla", 40 * 6 * 4 + 40 + 40 * 6, dirtyCount())
        server.failNext = 0; server.ioFailNext = 3; assertFalse(e.sync())
        // Falla a la mitad (después de algunos lotes): lo subido queda subido y lo demás pendiente.
        server.ioFailNext = 0; server.failNext = 0
        assertTrue(e.sync()); assertEquals(sets, server.rows("workout_sets").size); assertEquals(0, dirtyCount())
        // JSON roto al bajar: no truena, no avanza cursores; al volver el servidor, todo bien y sin duplicados.
        wipeLocal(); val e2 = engine(server)
        server.garbageGet = true; assertFalse(e2.sync())
        server.garbageGet = false; assertTrue(e2.sync())
        assertEquals(40, c.db.stats().workoutTotals(0, Int.MAX_VALUE).n)
        assertEquals(sets, server.rows("workout_sets").count { r -> c.db.workouts().getSet(r["id"]!!.jsonPrimitive.content) != null })
    }

    // ------------------------------------------------------------------ usuario extremo: rendimiento y exactitud

    @Test fun usuarioExtremoRapidoYExacto() = runBlocking {
        val n: Int
        val insertMs = measureTimeMillis { n = seed(1_500) }        // 36 000 series
        val times = linkedMapOf<String, Long>()
        fun <T> time(label: String, block: suspend () -> T): T { var r: T? = null; times[label] = measureTimeMillis { r = runBlocking { block() } }; return r!! }
        for (p in Period.entries) time("Progreso · ${p.label}") { c.stats.snapshot(p, zone) }
        val snap = c.stats.snapshot(Period.ALL, zone)
        val story = time("Tu progreso") { c.stats.progressStory(zone, emptyList()) }
        time("Recomendación (motor de progresión)") {
            c.workouts.recommendation(c.db.exercises().get("press_de_banca_con_barra")!!, 8, 12, "-")
        }
        var pages = 0; var seen = 0
        time("Historial completo por páginas de 30") {
            var day = Int.MAX_VALUE; var start = Long.MAX_VALUE
            while (true) {
                val p = c.workouts.historyPage(day, start, 30); if (p.isEmpty()) break
                pages++; seen += p.size; day = p.last().localDate; start = p.last().startedAt
                c.workouts.exerciseSummaries(p.map { it.id })
            }
        }
        time("Recalcular TODOS los récords") { c.workouts.rebuildAllRecords() }
        val victim = c.workouts.historyPage(Int.MAX_VALUE, Long.MAX_VALUE, 1).single()
        time("Borrar un entrenamiento (y recalcular sus récords)") { c.workouts.deleteWorkout(victim.id) }
        println("Usuario extremo: $n series insertadas en $insertMs ms"); times.forEach { (k, v) -> println("  $v ms  $k") }

        // Exactitud
        assertEquals(1_500, seen); assertEquals(50, pages)
        assertEquals(1_500, snap.totals.workouts)
        assertTrue("Tu progreso encontró ejercicios", story.exercises.isNotEmpty())
        assertEquals(1_500 - 1, c.db.stats().workoutTotals(0, Int.MAX_VALUE).n)
        // Presupuestos (esta máquina; un teléfono medio ≈ 3–5× más lento → siempre < 1 s en segundo plano).
        times.forEach { (k, v) ->
            val budget = when { k.startsWith("Recalcular") -> 3_000L; k.startsWith("Historial completo") -> 2_000L; else -> 250L }
            assertTrue("$k tardó $v ms (máx. $budget)", v <= budget)
        }
    }

    // ------------------------------------------------------------------ flujo de entreno con operaciones absurdas

    @Test fun flujoDeEntrenoTolerante() = runBlocking {
        val t = System.currentTimeMillis()
        // Rutina con plan corrupto y un ejercicio que no existe en el catálogo.
        val r = RoutineEntity(Ids.uuidV7(t), uid, "Rara", null, 0, t, t)
        c.db.routines().saveRoutine(r, listOf(
            RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, "press_de_banca_con_barra", 0, targetSets = 3, updatedAt = t, setPlan = "{{{ no es json"),
            RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, "ejercicio_que_no_existe", 1, targetSets = 2, updatedAt = t),
            RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, "curl_martillo", 2, targetSets = 2, updatedAt = t, setPlan = "[{\"w\":10,\"r\":12,\"t\":1},{\"w\":12,\"r\":10}]"),
        ), t)
        val wid = c.workouts.startFromRoutine(r.id)
        val sets = c.db.workouts().sets(wid)
        assertEquals("3 del plan roto (usa series objetivo) + 2 + 2 del plan", 7, sets.size)
        assertEquals("el calentamiento del plan se respeta", 1, sets.first { it.exerciseId == "curl_martillo" && it.position == 0 }.setType)

        // 200 toques simultáneos sobre la misma serie: estado final coherente y sin duplicados.
        val target = sets.first { it.exerciseId == "press_de_banca_con_barra" }
        (1..200).map { async(Dispatchers.Default) { c.workouts.toggleComplete(target.id, Tracking.WEIGHT_REPS, 60.0, 10, null) } }.awaitAll()
        assertEquals(7, c.db.workouts().sets(wid).size)
        assertNotNull(c.db.workouts().getSet(target.id))

        // Operaciones con ids que no existen o fuera de rango: no truenan.
        c.workouts.removeSet("no-existe"); c.workouts.ungroup(wid, "no-existe"); c.workouts.removeFromSuperset(wid, "no-existe")
        val wes = c.db.workouts().exercises(wid)
        c.workouts.move(wid, wes.first().id, -100); c.workouts.move(wid, wes.first().id, +100)
        c.workouts.createSuperset(wid, listOf(wes.first().id), SupersetRestMode.SHARED, 90)              // superserie de 1
        c.workouts.createSuperset(wid, listOf(wes[0].id, wes[1].id, "no-existe"), SupersetRestMode.SHARED, 90)
        repeat(100) { c.workouts.addSet(wes.last().id) }
        c.workouts.renameActive(wid, "💪 " + "Nombre larguísimo ".repeat(60))
        assertEquals(wes.map { it.position }.sorted(), c.db.workouts().exercises(wid).map { it.position }.sorted())

        // Terminar y borrar dos veces; terminar algo que ya no existe.
        c.workouts.finish(wid, null, null)
        val done = c.db.workouts().get(wid)!!
        assertTrue(done.endedAt!! >= done.startedAt && (done.durationSec ?: 0) >= 0)
        c.workouts.deleteWorkout(wid); c.workouts.deleteWorkout(wid)
        assertEquals(0, c.workouts.finish("no-existe", null, null))

        // Peso corporal con valores imposibles: se ignoran o se ajustan al rango válido.
        val today = Dates.today(zone)
        for (kg in listOf(Double.NaN, Double.POSITIVE_INFINITY, -5.0, 0.0, 1e9, 80.123456)) c.bodyWeight.log(today, kg)
        val bw = c.db.bodyWeights().get(uid, today.toEpochDay().toInt())!!
        assertEquals(80.12, bw.weightKg, 1e-9)
        c.bodyWeight.log(today.minusDays(1), 1e9)
        assertEquals(400.0, c.db.bodyWeights().get(uid, today.minusDays(1).toEpochDay().toInt())!!.weightKg, 1e-9)
    }
}
