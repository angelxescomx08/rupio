package com.rupio.app

import androidx.test.core.app.ApplicationProvider
import com.rupio.app.data.remote.DataApiClient
import com.rupio.app.data.remote.PendingDeletes
import com.rupio.app.data.remote.SyncEngine
import com.rupio.app.data.repo.Period
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * El código de sincronización REAL de la app contra el servidor REAL (Neon) con la cuenta demo de 8 años.
 * Solo corre si se le pasa un JWT: RUPIO_DEMO_JWT=… RUPIO_DEMO_UID=… gradle testDebugUnitTest --tests '*QaRealServer*'
 * (lo genera db/demo/demo_extremo.py). Sin esas variables se omite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QaRealServerTest {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val zone = ZoneId.of("America/Mexico_City")

    @Test fun primeraSincronizacionDeLaCuentaDemoContraNeon() = runBlocking {
        val jwt = System.getenv("RUPIO_DEMO_JWT"); val uid = System.getenv("RUPIO_DEMO_UID")
        assumeTrue("sin JWT de la cuenta demo", !jwt.isNullOrBlank() && !uid.isNullOrBlank())
        repeat(200) { if (c.db.exercises().catalogCount() >= 250) return@repeat; Thread.sleep(50) }
        c.auth.useLocalMode("Demo Extremo")
        c.wipeLocalData()
        val proxy = System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let { URI(it) }
            ?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress(it.host, it.port)) } ?: Proxy.NO_PROXY
        val http = OkHttpClient.Builder().proxy(proxy).connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        val engine = SyncEngine(app, c.db, DataApiClient(http), c.auth, c.settings, PendingDeletes(app),
            jwtProvider = { jwt }, uidProvider = { uid }, localOnly = { false }).also { it.resetCursors() }

        val first = measureTimeMillis { assertTrue("primera sincronización: ${engine.status.value.lastError}", engine.sync()) }
        val again = measureTimeMillis { assertTrue(engine.sync()) }

        val expected = System.getenv("RUPIO_DEMO_EXPECT")?.split(",")?.map { it.toInt() }   // entrenos,series,pesos,récords
        val tot = c.db.stats().workoutTotals(0, Int.MAX_VALUE)
        val sets = c.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM workout_sets WHERE deleted = 0").use { it.moveToFirst(); it.getInt(0) }
        val weights = c.db.bodyWeights().seriesOnce(uid, 0, Int.MAX_VALUE, 1).sumOf { it.n }
        val records = c.db.records().allExerciseIds().size
        println("Cuenta demo contra Neon: primera sincronización $first ms, la siguiente $again ms")
        println("  ${tot.n} entrenos · $sets series · $weights pesos · récords en $records ejercicios")
        if (expected != null) {
            assertEquals("entrenos", expected[0], tot.n); assertEquals("series", expected[1], sets); assertEquals("pesos", expected[2], weights)
        }

        val times = linkedMapOf<String, Long>()
        fun <T> time(label: String, block: suspend () -> T): T { var r: T? = null; times[label] = measureTimeMillis { r = runBlocking { block() } }; return r!! }
        for (p in Period.entries) time("Progreso · ${p.label}") { c.stats.snapshot(p, zone) }
        val story = time("Tu progreso") { c.stats.progressStory(zone, emptyList()) }
        time("Peso corporal · tendencia completa") { c.bodyWeight.trendAll(java.time.LocalDate.now(zone)) }
        time("Historial completo por páginas de 30") {
            var day = Int.MAX_VALUE; var start = Long.MAX_VALUE
            while (true) {
                val p = c.workouts.historyPage(day, start, 30); if (p.isEmpty()) break
                day = p.last().localDate; start = p.last().startedAt
                c.workouts.exerciseSummaries(p.map { it.id })
            }
        }
        times.forEach { (k, v) -> println("  $v ms  $k") }
        println("  Tu progreso: ${story.exercises.size} ejercicios")
        assertTrue(story.exercises.isNotEmpty())
        c.wipeLocalData()
    }
}
