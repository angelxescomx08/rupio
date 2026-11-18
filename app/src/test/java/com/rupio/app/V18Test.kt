package com.rupio.app

import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.data.billing.SupportRepository
import com.rupio.app.data.billing.WallError
import com.rupio.app.data.billing.WallException
import com.rupio.app.data.billing.WallMe
import com.rupio.app.data.billing.WallRepository
import com.rupio.app.data.billing.WallState
import com.rupio.app.data.local.WorkoutEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.data.prefs.RestAlertMode
import com.rupio.app.data.remote.DataApiClient
import com.rupio.app.domain.Accent
import com.rupio.app.domain.CalorieEstimate
import com.rupio.app.domain.MuscleSplit
import com.rupio.app.domain.SupportTier
import com.rupio.app.domain.WallBoost
import com.rupio.app.domain.WallName
import com.rupio.app.timer.RestAlert
import com.rupio.app.timer.RestAlertPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.time.ZoneId

/** v1.8: nivel Mecenas y muro, aviso de descanso, calorías, reparto de músculos y gráfica dentro del entreno. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V18Test {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val zone = ZoneId.of("America/Mexico_City")

    @Before fun setUp() = runBlocking {
        c.auth.useLocalMode("QA")
        repeat(200) { if (c.db.exercises().catalogCount() >= 250) return@repeat; Thread.sleep(50) }
        c.wipeLocalData()
    }

    // ------------------------------------------------------------------ calorías

    @Test fun calorias_metodoMet() {
        // 3.5 MET × 80 kg × 1 h = 280 kcal
        val r = CalorieEstimate.estimate(durationSec = 3600, effectiveSets = 20, bodyKg = 80.0)!!
        assertEquals(280, r.kcal); assertEquals(60, r.minutesCounted); assertFalse(r.assumedWeight); assertFalse(r.capped)
        // Sin peso registrado: 70 kg y se dice
        CalorieEstimate.estimate(3600, 20, null)!!.let { assertEquals(245, it.kcal); assertTrue(it.assumedWeight) }
        // Entreno que se quedó abierto 5 h con 10 series: solo 50 min (5 por serie)
        CalorieEstimate.estimate(5 * 3600, 10, 80.0)!!.let { assertEquals(50, it.minutesCounted); assertTrue(it.capped); assertEquals(235, it.kcal) }
        // Redondeo a 5 kcal
        assertEquals(0, CalorieEstimate.estimate(3600, 20, 81.3)!!.kcal % 5)
        assertNull("sin series no hay estimación", CalorieEstimate.estimate(3600, 0, 80.0))
        assertNull(CalorieEstimate.estimate(0, 10, 80.0))
        assertTrue("peso absurdo → 70 kg", CalorieEstimate.estimate(3600, 20, 9999.0)!!.assumedWeight)
    }

    // ------------------------------------------------------------------ reparto de músculos

    @Test fun repartoDeMusculos_fraccional() {
        val split = MuscleSplit.compute(listOf(
            MuscleSplit.Item("pecho", listOf("triceps", "hombros"), 3),
            MuscleSplit.Item("espalda_media", listOf("biceps"), 3),
            MuscleSplit.Item("cuadriceps", emptyList(), 0),                // sin series marcadas: no cuenta
        ))
        val by = split.associate { it.muscle to it.sets }
        assertEquals(3.0, by["pecho"]!!, 1e-9); assertEquals(3.0, by["espalda_media"]!!, 1e-9)
        assertEquals(1.5, by["triceps"]!!, 1e-9); assertEquals(1.5, by["hombros"]!!, 1e-9); assertEquals(1.5, by["biceps"]!!, 1e-9)
        assertNull(by["cuadriceps"])
        assertEquals(1.0, split.sumOf { it.share }, 1e-9)
        assertEquals(3.0 / 10.5, split.first().share, 1e-9)
        // Más de 6 músculos → el resto junto en "Otros" (código vacío)
        val many = MuscleSplit.compute((1..9).map { MuscleSplit.Item("m$it", emptyList(), 10 - it) })
        assertEquals(7, many.size); assertEquals("", many.last().muscle); assertEquals(3.0 + 2 + 1, many.last().sets, 1e-9)
        assertTrue(MuscleSplit.compute(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------------ gráfica dentro del entreno

    @Test fun graficaDelEjercicio_unaFilaPorSesionSinCalentamientos() = runBlocking {
        val user = c.auth.currentUserId!!
        val bench = "press_de_banca_con_barra"
        val today = Dates.today(zone).toEpochDay().toInt()
        listOf(today - 14 to 60.0, today - 7 to 62.5, today to 65.0).forEach { (day, kg) ->
            val start = day * 86_400_000L
            val w = WorkoutEntity(Ids.uuidV7(start), user, null, "Pecho", start, start + 3_600_000, zone.id, day, 3600, updatedAt = start, dirty = false)
            val we = WorkoutExerciseEntity(Ids.uuidV7(start), w.id, user, bench, 0, updatedAt = start, dirty = false)
            val sets = listOf(
                WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, user, bench, 0, setType = 1, weightKg = 100.0, reps = 5, completed = true, performedOn = day, updatedAt = start, dirty = false),
                WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, user, bench, 1, weightKg = kg, reps = 8, completed = true, performedOn = day, updatedAt = start, dirty = false),
                WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, user, bench, 2, weightKg = kg, reps = 6, completed = true, performedOn = day, updatedAt = start, dirty = false),
                WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, user, bench, 3, weightKg = 200.0, reps = 8, completed = false, performedOn = day, updatedAt = start, dirty = false),
            )
            c.db.workouts().insertFull(w, listOf(we), sets)
        }
        val rows = c.stats.sessionProgress(bench)
        assertEquals(listOf(today - 14, today - 7, today), rows.map { it.performedOn })
        assertEquals("el calentamiento de 100 y la serie sin marcar no cuentan", 65.0, rows.last().topWeight!!, 1e-9)
        assertEquals(65.0 * 8 + 65.0 * 6, rows.last().volume, 1e-9)
        val e1rm8 = (65.0 * (1 + 8 / 30.0) + 65.0 * 36.0 / (37 - 8)) / 2
        assertEquals(e1rm8, rows.last().e1rm!!, 1e-9)
        assertEquals(8, rows.last().maxReps)
    }

    // ------------------------------------------------------------------ aviso de descanso

    @Test fun avisoDeDescanso_v18() {
        val m = RestAlertMode.MEDIA
        assertEquals("si nunca elegiste, ahora es con el volumen de la música", RestAlertMode.MEDIA, RestAlertMode.of(null))
        assertTrue("en vibración suena (el caso del gimnasio)", RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_VIBRATE, false, false).sound)
        assertFalse("silencio total, sin audífonos y sin nada sonando: solo vibra",
            RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_SILENT, false, false, mediaPlaying = false).sound)
        assertTrue("silencio total pero viendo un video: suena encima del video",
            RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_SILENT, false, false, mediaPlaying = true).sound)
        assertTrue(RestAlertPolicy.decide(m, AudioManager.RINGER_MODE_SILENT, false, false).vibrate)
        assertFalse("«Como notificación» sigue respetando la vibración", RestAlertPolicy.decide(RestAlertMode.SOUND, AudioManager.RINGER_MODE_VIBRATE, false, false).sound)

        // La pantalla del entreno dice por qué no se oiría
        val am = app.getSystemService(AudioManager::class.java)

        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        assertTrue(RestAlert.inaudibleReason(app, m)!!.contains("volumen multimedia"))
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0)
        assertNull(RestAlert.inaudibleReason(app, m))
        am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertTrue(RestAlert.inaudibleReason(app, RestAlertMode.SOUND)!!.contains("vibración"))
        assertNull("solo vibración: lo eligió la persona", RestAlert.inaudibleReason(app, RestAlertMode.VIBRATE))
    }

    // ------------------------------------------------------------------ muro: dominio

    @Test fun muro_aportesYNombre() {
        assertEquals(WallBoost.SMALL, WallBoost.toPass(1))
        assertEquals(WallBoost.SMALL, WallBoost.toPass(99))
        assertEquals(WallBoost.MEDIUM, WallBoost.toPass(100))
        assertEquals(WallBoost.BIG, WallBoost.toPass(300))
        assertEquals("ninguno alcanza: el más grande", WallBoost.BIG, WallBoost.toPass(5000))
        assertNull(WallBoost.toPass(null)); assertNull(WallBoost.toPass(0))
        assertTrue(WallBoost.isWallProduct("rupio_mecenas")); assertFalse(WallBoost.isWallProduct("rupio_apoyo_fan"))
        assertEquals("Mecenas = entrar y también aporte grande", SupportTier.MECENAS.productId, WallBoost.BIG.productId)

        assertNotNull(WallName.problem("A")); assertNotNull(WallName.problem("visita www.algo.com")); assertNotNull(WallName.problem("yo@correo"))
        assertNotNull(WallName.problem("x".repeat(31)))
        assertNull(WallName.problem("Ángel del gym"))
        assertEquals("Beto R", WallName.clean("  Beto   R  "))
    }

    @Test fun muro_nivelMecenasDaTodoLoDeFan() {
        assertTrue(SupportTier.MECENAS.rank > SupportTier.FAN.rank)
        assertTrue(SupportTier.MECENAS.hasGoldPerks); assertTrue(SupportTier.FAN.hasGoldPerks); assertFalse(SupportTier.LICUADO.hasGoldPerks)
        assertTrue(Accent.DORADO.unlockedBy(SupportTier.MECENAS))
    }

    @Test fun muro_hashDeCuenta_igualQueElServidor() {
        val uid = "5B614C54-0000-4000-8000-000000000001"
        val expected = MessageDigest.getInstance("SHA-256").digest(uid.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, WallRepository.accountHash(uid))
        assertEquals(64, WallRepository.accountHash(uid).length)
    }

    // ------------------------------------------------------------------ muro: servidor simulado

    private class FakeWall {
        val calls = mutableListOf<Pair<String, String>>()
        var next: (String) -> Pair<Int, String> = { 200 to "null" }
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val body = req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            val fn = req.url.encodedPath.substringAfterLast("/")
            calls += fn to body
            val (code, text) = next(fn)
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(text.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun wallRepo(server: FakeWall, uid: String? = "u-1", jwt: String? = "jwt") =
        WallRepository(app, DataApiClient(OkHttpClient.Builder().addInterceptor(server.interceptor).build()), { "https://api.test/rest/v1" }, { jwt }, { uid })

    private val meJson = """{"name":"Ana G.","points":748,"since":"2026-11-17T20:54:43.118957+00:00","hidden":false,"blocked":false,"rank":2,"to_next":100,"total":5}"""

    @Test fun muro_cargarLista() = runBlocking {
        app.getSharedPreferences("rupio_wall", 0).edit().clear().commit()
        val server = FakeWall()
        server.next = { 200 to """{"entries":[{"rank":1,"name":"Beto","since":"2026-11-01T10:00:00+00:00","me":false},{"rank":2,"name":"Ana G.","since":"2026-11-17T20:54:43.118957+00:00","me":true}],"me":$meJson}""" }
        val repo = wallRepo(server)
        repo.load()
        val s = repo.state.value
        assertEquals(listOf("Beto", "Ana G."), s.entries.map { it.name }); assertTrue(s.entries[1].me)
        assertEquals(2, s.me!!.rank); assertEquals(100, s.me!!.toNext); assertTrue(repo.isPatron)
        assertEquals(java.time.OffsetDateTime.parse("2026-11-01T10:00:00Z").toInstant().toEpochMilli(), s.entries[0].sinceMs)
        assertEquals("rupio_wall", server.calls.single().first)
        // Sin internet, el nivel sigue: se guardó tu lugar (por cuenta)
        assertTrue(wallRepo(FakeWall()).isPatron)
        assertFalse("otra cuenta no hereda el lugar", wallRepo(FakeWall(), uid = "u-2").isPatron)
    }

    @Test fun muro_registrarCompra() = runBlocking {
        val server = FakeWall()
        server.next = { 200 to meJson }
        val repo = wallRepo(server)
        val me = repo.register("""{"purchaseToken":"t"}""", "c2ln", "Ana G.")
        assertEquals(748, me.points)
        val (fn, body) = server.calls.single()
        assertEquals("rupio_wall_register", fn)
        val j = Json.parseToJsonElement(body).jsonObject
        assertEquals("""{"purchaseToken":"t"}""", j["purchase_json"]!!.jsonPrimitive.content)
        assertEquals("c2ln", j["signature"]!!.jsonPrimitive.content)
        assertEquals("Ana G.", j["display_name"]!!.jsonPrimitive.content)
        // Aporte extra: sin nombre (null), el servidor conserva el que tenías
        repo.register("{}", "x", null)
        assertTrue(Json.parseToJsonElement(server.calls.last().second).jsonObject["display_name"] is JsonNull)
    }

    @Test fun muro_erroresDelServidor() = runBlocking {
        val server = FakeWall()
        val repo = wallRepo(server)
        fun failWith(code: Int, body: String): WallError = runBlocking {
            server.next = { code to body }
            try { repo.register("{}", "x", "Ana"); fail("debió fallar"); WallError.SERVER } catch (e: WallException) { e.error }
        }
        val notConf = failWith(400, """{"code":"P0001","message":"rupio:not_configured"}""")
        assertEquals(WallError.NOT_CONFIGURED, notConf); assertTrue("se reintenta después, sin consumir", notConf.retryLater)
        assertEquals(WallError.BAD_SIGNATURE, failWith(400, """{"code":"P0001","message":"rupio:bad_signature"}""")); assertFalse(WallError.BAD_SIGNATURE.retryLater)
        assertEquals(WallError.NAME_REQUIRED, failWith(400, """{"message":"rupio:name_required"}"""))
        assertEquals(WallError.OTHER_ACCOUNT, failWith(400, """{"message":"rupio:other_account"}"""))
        assertEquals(WallError.NO_ACCOUNT, failWith(401, """{"message":"JWT expired"}"""))
        assertEquals(WallError.SERVER, failWith(500, "caído"))
        // Sin cuenta (modo sin cuenta): ni se llama al servidor
        val before = server.calls.size
        try { wallRepo(server, jwt = null).register("{}", "x", "Ana"); fail() } catch (e: WallException) { assertEquals(WallError.NO_ACCOUNT, e.error) }
        assertEquals(before, server.calls.size)
    }

    @Test fun muro_elLugarDaElNivelMecenas() = runBlocking {
        app.getSharedPreferences("rupio_wall", 0).edit().clear().commit()
        app.getSharedPreferences("rupio_supporter", 0).edit().clear().commit()
        val wall = wallRepo(FakeWall())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val support = SupportRepository(app, scope, wall) { "u-1" }
        assertNull(support.state.value.best)
        wall.setStateForTest(WallState(me = WallMe("Ana", 499, 1, null, hidden = false, blocked = false, sinceMs = 0, total = 1)))
        repeat(50) { if (support.state.value.best != null) return@repeat; Thread.sleep(20) }
        assertEquals(SupportTier.MECENAS, support.state.value.best)
        assertTrue(support.state.value.isPatron); assertFalse("el mensaje de apoyo ya no sale", !support.state.value.isSupporter)
        support.setAccent(Accent.DORADO)
        assertEquals(Accent.DORADO, support.state.value.effectiveAccent)
        // Si el servidor ya no lo tiene (reembolso revisado), se quitan los adornos
        wall.setStateForTest(WallState(me = null))
        repeat(50) { if (support.state.value.best == null) return@repeat; Thread.sleep(20) }
        assertNull(support.state.value.best)
        assertEquals(Accent.AZUL, support.state.value.effectiveAccent)
    }
}
