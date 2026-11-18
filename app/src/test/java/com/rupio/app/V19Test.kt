package com.rupio.app

import androidx.test.core.app.ApplicationProvider
import com.rupio.app.data.league.LeagueException
import com.rupio.app.data.league.LeagueRepository
import com.rupio.app.data.remote.DataApiClient
import com.rupio.app.domain.LeagueRules
import com.rupio.app.domain.LeagueZone
import com.rupio.app.domain.SupportTier
import com.rupio.app.ui.support.SupportTab
import com.rupio.app.ui.support.leagueKg
import com.rupio.app.ui.support.stableKeys
import com.rupio.app.ui.theme.Metals
import com.rupio.app.core.WeightUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Collections

/** v1.9: ligas semanales (reglas, lectura de las respuestas del servidor, caché, páginas) y metales. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V19Test {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()

    @Before fun clean() { app.getSharedPreferences("rupio_league", 0).edit().clear().commit() }

    // ------------------------------------------------------------------ reglas

    @Test fun zonas_de_subir_y_bajar() {
        // 28 personas: 1–5 suben, 24–28 bajan.
        assertEquals(LeagueZone.UP, LeagueRules.zone(5, 28, 5, 5))
        assertEquals(LeagueZone.STAY, LeagueRules.zone(6, 28, 5, 5))
        assertEquals(LeagueZone.STAY, LeagueRules.zone(23, 28, 5, 5))
        assertEquals(LeagueZone.DOWN, LeagueRules.zone(24, 28, 5, 5))
        // 12 personas: suben 5, nadie baja (hace falta 15). 8 personas: nadie sube ni baja.
        assertEquals(LeagueZone.STAY, LeagueRules.zone(12, 12, 5, 0))
        assertEquals(LeagueZone.STAY, LeagueRules.zone(1, 8, 0, 0))
        assertEquals("Calentamiento", LeagueRules.tierName(1)); assertEquals("Leyenda", LeagueRules.tierName(6)); assertEquals("Liga 9", LeagueRules.tierName(9))
    }

    @Test fun kilos_que_faltan() {
        val kgs = listOf(9000.0, 8000.0, 7000.0, 6000.0, 5000.0, 4000.0)
        assertEquals("al 5.º lugar desde el 6.º: superar por 1 kg (con empate gana quien empezó antes)", 1001.0, LeagueRules.kgToReach(5, 6, 4000.0, kgs)!!, 0.0)
        assertNull("ya vas ahí", LeagueRules.kgToReach(5, 3, 7000.0, kgs))
        assertNull("lugar que no existe", LeagueRules.kgToReach(9, 12, 0.0, kgs))
        assertEquals("empate exacto → 1 kg", 1.0, LeagueRules.kgToReach(1, 2, 9000.0, listOf(9000.0, 9000.0))!!, 0.0)
    }

    @Test fun semana_y_dias() {
        assertEquals("16 – 22 nov", LeagueRules.weekLabel(LocalDate.parse("2026-11-16")))
        assertEquals("28 dic – 3 ene", LeagueRules.weekLabel(LocalDate.parse("2026-12-28")))
        val monday = LocalDate.parse("2026-11-16")
        assertEquals("Quedan 7 días", LeagueRules.daysLeftText(monday, monday))
        assertEquals("Termina hoy", LeagueRules.daysLeftText(monday.plusDays(6), monday))
        // El servidor usa la fecha UTC: el domingo en la noche de CDMX ya manda la semana siguiente.
        assertEquals("nunca más de 7", 7, LeagueRules.daysLeft(monday.minusDays(1), monday))
    }

    @Test fun kilos_completos_con_miles() {
        assertEquals("12,450 kg", leagueKg(12_450.0, WeightUnit.KG))
        assertEquals("27,448 lb", leagueKg(12_450.0, WeightUnit.LB))
        assertEquals("0 kg", leagueKg(0.0, WeightUnit.KG))
        assertEquals("— kg", leagueKg(Double.NaN, WeightUnit.KG))
    }

    @Test fun pestanas_y_metales() {
        assertEquals(SupportTab.MURO, SupportTab.of("muro")); assertEquals(SupportTab.LIGAS, SupportTab.of("ligas"))
        assertEquals(SupportTab.APOYAR, SupportTab.of(null)); assertEquals(SupportTab.APOYAR, SupportTab.of("otra"))
        assertEquals(listOf("Bronce", "Plata", "Oro", "Aurora"), SupportTier.entries.map { Metals.of(it).name })
        assertNull(Metals.ofBadge(0)); assertEquals("Oro", Metals.ofBadge(3)!!.name); assertNull(Metals.ofBadge(5))
        assertEquals(listOf("Café · Bronce", "Licuado · Plata", "Fan · Oro", "Mecenas · Aurora"), SupportTier.entries.map { it.badge })
        assertEquals(listOf("a", "b", "a#2", "a#3"), stableKeys(listOf("a", "b", "a", "a")))
    }

    // ------------------------------------------------------------------ respuestas del servidor

    private fun groupJson(size: Int = 28, me: Int = 7, week: String = "2026-11-16") = buildString {
        val up = if (size >= 10) 5 else 0; val down = if (size >= 15) 5 else 0
        append("""{"member":true,"week":"$week","tier":3,"size":$size,"up":$up,"down":$down,"rows":[""")
        for (r in 1..size) {
            if (r > 1) append(',')
            append("""{"rank":$r,"name":"Persona $r","badge":${r % 5},"at":"2026-11-16T1${r % 10}:00:00.${100000 + r}+00:00","kg":${30000 - r * 900},"workouts":${1 + r % 4},"me":${r == me}}""")
        }
        append("]}")
    }

    @Test fun leer_tu_grupo() {
        val l = LeagueRepository.parseLeague(groupJson())
        assertTrue(l.member && l.inGroup); assertEquals(3, l.tier); assertEquals("Sólido", l.tierName); assertEquals(LocalDate.parse("2026-11-16"), l.week)
        assertEquals(28, l.rows.size); assertEquals(7, l.me!!.rank); assertEquals(30000.0 - 7 * 900, l.me!!.kg, 0.0)
        assertEquals(LeagueZone.UP, l.rows[4].zone); assertEquals(LeagueZone.STAY, l.rows[5].zone); assertEquals(LeagueZone.DOWN, l.rows[23].zone)
        assertEquals("Persona 1|2026-11-16T11:00:00.100001+00:00", l.rows[0].key)
        // No inscrito, inscrito sin entrenar esta semana, y keys repetidas (mismo apodo y mismo instante).
        LeagueRepository.parseLeague("""{"member":false,"week":"2026-11-16"}""").let { assertFalse(it.member); assertFalse(it.inGroup) }
        LeagueRepository.parseLeague("""{"member":true,"week":"2026-11-16","tier":2,"hidden":false}""").let { assertTrue(it.member); assertFalse(it.inGroup); assertEquals(2, it.tier) }
        val dup = LeagueRepository.parseLeague("""{"member":true,"week":"2026-11-16","tier":1,"size":2,"up":0,"down":0,"rows":[
            {"rank":1,"name":"Ana","badge":0,"at":"2026-11-16T10:00:00+00:00","kg":100,"workouts":1,"me":false},
            {"rank":2,"name":"Ana","badge":0,"at":"2026-11-16T10:00:00+00:00","kg":90,"workouts":1,"me":true}]}""")
        assertEquals(2, dup.rows.map { it.key }.toSet().size)
        // Lugar global; null = sin puntaje esta semana.
        assertNull(LeagueRepository.parseGlobal("null"))
        LeagueRepository.parseGlobal("""{"rank":1234,"total":60000,"top_pct":3}""")!!.let { assertEquals(1234L, it.rank); assertEquals(60000L, it.total); assertEquals(3, it.topPct) }
    }

    // ------------------------------------------------------------------ servidor simulado

    private class FakeLeague {
        val calls: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())
        var next: (String, String) -> Pair<Int, String> = { _, _ -> 200 to "null" }
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val body = req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            val fn = req.url.encodedPath.substringAfterLast("/")
            calls += fn to body
            val (code, text) = next(fn, body)
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(text.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private var now = 1_000_000L
    private fun repo(server: FakeLeague, uid: String? = "u-1", jwt: String? = "jwt") =
        LeagueRepository(app, DataApiClient(OkHttpClient.Builder().addInterceptor(server.interceptor).build()), { "https://api.test/rest/v1" }, { jwt }, { uid }, clock = { now })

    @Test fun cargar_en_paralelo_guardar_y_no_repetir() = runBlocking {
        val server = FakeLeague()
        server.next = { fn, _ -> 200 to if (fn == "rupio_league") groupJson() else """{"rank":40,"total":900,"top_pct":5}""" }
        val r = repo(server)
        r.load()
        assertEquals(setOf("rupio_league", "rupio_league_me_global"), server.calls.map { it.first }.toSet())
        assertEquals(7, r.state.value.league!!.me!!.rank); assertEquals(40L, r.state.value.global!!.rank)
        // Antes de 30 s no se vuelve a pedir (cambiar de pestaña no gasta red); con "Actualizar" sí.
        now += 10_000; r.load(); assertEquals(2, server.calls.size)
        r.load(force = true); assertEquals(4, server.calls.size)
        now += 31_000; r.load(); assertEquals(6, server.calls.size)
        // Sin internet se ve lo último de ESA cuenta, al instante.
        val offline = repo(FakeLeague().apply { next = { _, _ -> throw java.io.IOException("sin red") } })
        offline.warmUp(); assertEquals(7, offline.state.value.league!!.me!!.rank)
        offline.load(); assertEquals("Sin conexión. Revisa tu internet e inténtalo de nuevo.", offline.state.value.error); assertNotNull(offline.state.value.league)
        val other = repo(FakeLeague(), uid = "u-2"); other.warmUp(); assertNull("otra cuenta no hereda la liga", other.state.value.league)
    }

    @Test fun sin_grupo_no_pide_lugar_global() = runBlocking {
        val server = FakeLeague()
        server.next = { _, _ -> 200 to """{"member":false,"week":"2026-11-16"}""" }
        val r = repo(server)
        r.load()                                                    // primera vez no sabe: pide las dos en paralelo
        now += 60_000; server.calls.clear(); r.load()
        assertEquals("ya sabe que no participa: solo una llamada", listOf("rupio_league"), server.calls.map { it.first })
        assertNull(r.state.value.global)
    }

    @Test fun sin_cuenta_no_llama() = runBlocking {
        val server = FakeLeague()
        val r = repo(server, jwt = null)
        r.load()
        assertTrue(server.calls.isEmpty()); assertEquals(LeagueRepository.NO_ACCOUNT, r.state.value.error)
        try { r.join("Ana"); fail() } catch (e: LeagueException) { assertEquals(LeagueRepository.NO_ACCOUNT, e.message) }
    }

    private fun topPage(from: Int, n: Int) = (from until from + n).joinToString(",", "[", "]") { i ->
        """{"name":"P$i","badge":0,"tier":${1 + i % 6},"kg":${100000 - i * 10},"k":${100000 - i * 10}.25,"at":"2026-11-16T10:00:00.${100000 + i}+00:00"}"""
    }

    @Test fun top_global_por_paginas_sin_repetidos() = runBlocking {
        val server = FakeLeague()
        var page = 0
        server.next = { _, _ ->
            page++
            200 to when (page) {
                1 -> topPage(0, 50)
                2 -> topPage(49, 50)                                 // P49 se repite (bajó de volumen entre páginas)
                else -> "[]"
            }
        }
        val r = repo(server)
        r.loadTop(reset = true)
        assertEquals(50, r.state.value.top.size); assertFalse(r.state.value.topEnd)
        assertFalse("la primera página no manda keyset", server.calls[0].second.contains("after_kg"))
        r.loadTop()
        val body = Json.parseToJsonElement(server.calls[1].second).jsonObject
        assertEquals("se manda k exacto (no el redondeado)", "99510.25", body["after_kg"]!!.jsonPrimitive.content)
        assertEquals("y la fecha con microsegundos", "2026-11-16T10:00:00.100049+00:00", body["after_at"]!!.jsonPrimitive.content)
        assertEquals(99, r.state.value.top.size)
        assertEquals("sin keys repetidas", 99, r.state.value.top.map { it.key }.toSet().size)
        assertEquals((1..99).toList(), r.state.value.top.map { it.rank })
        r.loadTop(); assertTrue("página vacía = fin", r.state.value.topEnd)
        r.loadTop(); assertEquals("ya no pide más", 3, server.calls.size)
    }

    @Test fun entrar_errores_y_salir() = runBlocking {
        val server = FakeLeague()
        server.next = { fn, body ->
            when {
                fn == "rupio_league_join" && body.contains("\"x\"") -> 400 to """{"code":"P0001","message":"rupio:name_length"}"""
                fn == "rupio_league_join" -> 200 to """{"member":true,"week":"2026-11-16","tier":1,"hidden":false}"""
                fn == "rupio_league_set_hidden" -> 400 to """{"code":"P0001","message":"rupio:no_entry"}"""
                else -> 200 to "null"
            }
        }
        val r = repo(server)
        try { r.join("x"); fail() } catch (e: LeagueException) { assertEquals("El nombre debe tener de 2 a 30 caracteres.", e.message) }
        r.join("Toño del gym")
        assertEquals("""{"nickname":"Toño del gym"}""", server.calls.last { it.first == "rupio_league_join" }.second)
        assertTrue(r.state.value.league!!.member); assertEquals("Toño del gym", r.state.value.nickname)
        assertEquals("el apodo se recuerda en el teléfono", "Toño del gym", repo(FakeLeague()).also { it.warmUp() }.state.value.nickname)
        try { r.setHidden(true); fail() } catch (e: LeagueException) { assertEquals("Todavía no participas en las ligas.", e.message) }
    }
}
