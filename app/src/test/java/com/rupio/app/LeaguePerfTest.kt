package com.rupio.app

import com.rupio.app.data.league.LeagueRepository
import com.rupio.app.data.league.LeagueRow
import com.rupio.app.domain.LeagueRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.9: cuánto cuesta leer las respuestas de las ligas en el teléfono. Compara la forma nueva (decodificar directo a
 * clases @Serializable) con la del muro (armar el árbol JsonElement y leerlo campo por campo).
 * Mediana de 9 rondas después de calentar el JIT; memoria = bytes reservados por llamada (ThreadMXBean).
 * En un teléfono medio cuenta ~3–5× más lento que en esta máquina.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeaguePerfTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun group(n: Int) = buildString {
        append("""{"member":true,"week":"2026-11-16","tier":3,"size":$n,"up":5,"down":5,"rows":[""")
        for (r in 1..n) { if (r > 1) append(','); append("""{"rank":$r,"name":"Persona número $r","badge":${r % 5},"at":"2026-11-16T10:00:00.${100000 + r}+00:00","kg":${40000 - r * 700},"workouts":${r % 6},"me":${r == 7}}""") }
        append("]}")
    }
    private fun top(n: Int) = (1..n).joinToString(",", "[", "]") { i ->
        """{"name":"Persona número $i","badge":${i % 5},"tier":${1 + i % 6},"kg":${90000 - i * 7},"k":${90000 - i * 7}.375,"at":"2026-11-16T10:00:00.${100000 + i}+00:00"}"""
    }

    /** Lo que hace el muro: árbol JsonElement → campos. */
    private fun treeLeague(text: String): List<LeagueRow> {
        val o = json.parseToJsonElement(text).jsonObject
        val size = o.int("size") ?: 0; val up = o.int("up") ?: 0; val down = o.int("down") ?: 0
        return o["rows"]!!.jsonArray.map { e ->
            val r = e.jsonObject; val rank = r.int("rank") ?: 0; val name = r.str("name").orEmpty(); val at = r.str("at").orEmpty()
            LeagueRow("$name|$at", rank, name, r.int("badge") ?: 0, r["kg"]!!.jsonPrimitive.doubleOrNull ?: 0.0, r.int("workouts") ?: 0,
                r["me"]?.jsonPrimitive?.booleanOrNull == true, LeagueRules.zone(rank, size, up, down))
        }
    }
    private fun JsonObject.str(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.int(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull

    // java.lang.management no está en android.jar (con lo que compilan estas pruebas), pero sí en la JVM que las corre.
    private val mx: Any = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
    private val allocated = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    private fun bytes(tid: Long) = allocated.invoke(mx, tid) as Long
    private class Result(val usPerOp: Double, val bytesPerOp: Long)
    private var sink = 0

    private fun measure(ops: Int = 2000, block: () -> Int): Result {
        repeat(4000) { sink += block() }                                          // calentar el JIT
        val tid = Thread.currentThread().id
        val times = DoubleArray(9); var allocatedPerOp = 0L
        for (round in 0 until 9) {
            val b0 = bytes(tid); val t0 = System.nanoTime()
            repeat(ops) { sink += block() }
            times[round] = (System.nanoTime() - t0) / 1000.0 / ops
            allocatedPerOp = (bytes(tid) - b0) / ops
        }
        times.sort()
        return Result(times[4], allocatedPerOp)
    }

    @Test fun leer_respuestas() {
        val g30 = group(30); val t50 = top(50); val t100 = top(100)
        // Mismo resultado por los dos caminos.
        assertEquals(treeLeague(g30), LeagueRepository.parseLeague(g30).rows)

        val lines = mutableListOf<String>()
        fun row(name: String, bytesIn: Int, new: Result, old: Result?) {
            lines += String.format(java.util.Locale.US, "| %s | %.1f KB | %.1f µs · %.1f KB | %s |", name, bytesIn / 1024.0, new.usPerOp, new.bytesPerOp / 1024.0,
                old?.let { String.format(java.util.Locale.US, "%.1f µs · %.1f KB", it.usPerOp, it.bytesPerOp / 1024.0) } ?: "—")
        }
        val newGroup = measure { LeagueRepository.parseLeague(g30).rows.size }
        val oldGroup = measure { treeLeague(g30).size }
        row("Tu grupo (30 filas)", g30.length, newGroup, oldGroup)
        val repo = LeagueRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            com.rupio.app.data.remote.DataApiClient(okhttp3.OkHttpClient()), { "" }, { null }, { null })
        row("Top global, página de 50", t50.length, measure { repo.appendTop(emptyList(), t50, reset = true).first.size }, null)
        row("Top global, página de 100", t100.length, measure { repo.appendTop(emptyList(), t100, reset = true).first.size }, null)
        // Sumar la página 21 a una lista de 1,000 ya cargadas (copiar + quitar repetidos).
        val thousand = (0 until 10).fold(emptyList<com.rupio.app.data.league.TopRow>()) { acc, p ->
            repo.appendTop(acc, top(100).replace("número ", "n$p "), reset = p == 0).first }
        assertEquals(1000, thousand.size)
        row("Página 11 sobre 1,000 filas", t100.length, measure(500) { repo.appendTop(thousand, t100.replace("número ", "nueva "), reset = false).first.size.also {
            // se deshace para medir siempre lo mismo
            repo.appendTop(emptyList(), "[]", reset = true) } }, null)

        println("| Respuesta | Tamaño | Ahora (directo a clases) | Como el muro (árbol JSON) |\n|---|---|---|---|\n" + lines.joinToString("\n"))
        println("Mejora en tu grupo: ${"%.1f".format(oldGroup.usPerOp / newGroup.usPerOp)}× más rápido, ${"%.1f".format(oldGroup.bytesPerOp.toDouble() / newGroup.bytesPerOp)}× menos memoria")
        assertTrue("leer tu grupo tarda menos de 1 ms (${newGroup.usPerOp} µs)", newGroup.usPerOp < 1000)
        assertTrue(sink != 42)
    }
}
