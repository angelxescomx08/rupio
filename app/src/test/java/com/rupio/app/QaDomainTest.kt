package com.rupio.app

import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.Ids
import com.rupio.app.core.WeightUnit
import com.rupio.app.core.searchKey
import com.rupio.app.data.local.DayBest
import com.rupio.app.data.local.Muscles
import com.rupio.app.data.local.Totals2
import com.rupio.app.data.local.WeightPoint
import com.rupio.app.domain.*
import com.rupio.app.ui.learn.Benefits
import com.rupio.app.ui.learn.Concepts
import com.rupio.app.ui.learn.Tutorials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * QA extremo del dominio: miles de entradas al azar y valores absurdos (0, negativos, NaN, infinito, enormes).
 * Regla: NUNCA truena y NUNCA muestra "NaN", "Infinity" ni números negativos donde no tienen sentido.
 */
class QaDomainTest {
    private val rnd = Random(20261114)
    private val weird = listOf(0.0, -0.0, -1.0, -500.0, 1e-9, 0.5, 2.5, 999.0, 1e6, 1e12, Double.MAX_VALUE, Double.MIN_VALUE,
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
    private val weirdInt = listOf(Int.MIN_VALUE, -100, -1, 0, 1, 2, 10, 12, 13, 30, 100, 1000, Int.MAX_VALUE)

    private fun d(): Double? = when (rnd.nextInt(6)) { 0 -> null; 1 -> weird.random(rnd); else -> rnd.nextDouble(0.0, 300.0) }
    private fun i(): Int? = when (rnd.nextInt(6)) { 0 -> null; 1 -> weirdInt.random(rnd); else -> rnd.nextInt(0, 30) }
    private fun set() = SetData(d(), i(), i(), if (rnd.nextBoolean()) null else rnd.nextDouble(-5.0, 15.0), SetType.entries.random(rnd))
    private fun okText(s: String) = !s.contains("NaN") && !s.contains("Infinity") && !s.contains("null")

    // ------------------------------------------------------------------ cálculos

    @Test fun unoRmNuncaDaBasura() {
        for (w in weird + (1..200).map { rnd.nextDouble(-50.0, 500.0) }) for (r in weirdInt + (0..40)) {
            val e = OneRepMax.estimate(w, r) ?: continue
            assertTrue("1RM($w, $r) = $e", e.isFinite() && e > 0 && e >= w - 1e-9)
        }
        // Más reps con el mismo peso → 1RM igual o mayor (1..12).
        for (r in 1 until 12) assertTrue(OneRepMax.estimate(100.0, r + 1)!! >= OneRepMax.estimate(100.0, r)!!)
    }

    @Test fun motorDeProgresionResisteHistorialesAbsurdos() {
        repeat(20_000) {
            val tr = Tracking.entries.random(rnd)
            val hist = List(rnd.nextInt(0, 12)) { k -> SessionData(20_000 - k, List(rnd.nextInt(0, 8)) { set() }) }
            val input = ProgressionInput(tr, weirdInt.random(rnd).coerceIn(-5, 50), weirdInt.random(rnd).coerceIn(-5, 50), rnd.nextBoolean(),
                listOf("maquina", "barra", "mancuernas", "peso_corporal", "polea", "").random(rnd), WeightUnit.entries.random(rnd), hist)
            val rec = Progression.recommend(input)
            assertTrue("título: ${rec.title}", rec.title.isNotBlank() && okText(rec.title) && okText(rec.detail))
            // En asistidos el peso es la ayuda (negativo); en el resto, positivo. Siempre dentro de lo posible.
            rec.targetWeightKg?.let { assertTrue("peso objetivo $it en $input", it.isFinite() && kotlin.math.abs(it) <= 1500.0 && (tr == Tracking.ASSISTED_BODYWEIGHT || it >= 0)) }
            rec.targetReps?.let { assertTrue("reps objetivo $it", it in 1..1000) }
            rec.targetDurationSec?.let { assertTrue("tiempo objetivo $it", it in 1..86_400) }
        }
    }

    @Test fun recordsNuncaNegativosNiNaN() {
        repeat(20_000) {
            for ((_, v) in Records.candidates(Tracking.entries.random(rnd), set())) assertTrue("récord $v", v.isFinite() && v > 0)
        }
    }

    @Test fun descansosConSuperseriesRaras() {
        repeat(5_000) {
            val items = List(rnd.nextInt(0, 15)) { k ->
                PlanItem("k$k", rnd.nextInt(0, 10), listOf(0, 30, 90, 600, -10).random(rnd), if (rnd.nextInt(3) == 0) "ss${rnd.nextInt(3)}" else null,
                    SupersetRestMode.entries.random(rnd), listOf(null, 0, 60, 120).random(rnd))
            }
            val s = RestPlanner.summarize(items)
            assertTrue(s.totalSets >= 0 && s.estimatedSec >= s.totalRestSec - 1)
            for (g in RestPlanner.groups(items)) for (it in g) for (idx in 0 until it.sets) {
                RestPlanner.restAfterSet(it, g, idx, rnd.nextInt(0, g.size + 1))
            }
        }
    }

    // ------------------------------------------------------------------ planes por serie

    @Test fun planesCorruptosNoTruenan() {
        val basura = listOf(null, "", " ", "null", "{", "[", "[]", "[{}]", "{\"w\":1}", "[1,2,3]", "[{\"w\":\"abc\"}]", "[{\"r\":1.5}]",
            "[{\"w\":-5,\"r\":-3,\"t\":99}]", "[{\"w\":1e309}]", "x".repeat(100_000), "[" + "{\"w\":10,\"r\":10},".repeat(5000) + "{}]",
            "[{\"w\":10,\"r\":10,\"extra\":true}]", "\u0000\u0001", "[{\"t\":2147483648}]")
        for (b in basura) {
            val p = SetPlans.decode(b)
            p.forEach { s -> assertNotNull(s.type) }
            SetPlans.repRange(p, 8 to 12).let { (lo, hi) -> assertTrue("rango $lo-$hi de '$b'", lo in 1..hi && hi <= 1000) }
            assertTrue(okText(SetPlans.summary(p, WeightUnit.LB, Tracking.WEIGHT_REPS)) || p.isEmpty())
            for (n in listOf(-1, 0, 1, 3, 50)) assertEquals(n.coerceAtLeast(0), SetPlans.resize(p, n).size)
        }
    }

    @Test fun planIdaYVueltaConValoresAlAzar() {
        repeat(2_000) {
            val plan = List(rnd.nextInt(1, 12)) { PlanSet(rnd.nextDouble(0.0, 500.0), rnd.nextInt(0, 50), null, rnd.nextInt(0, 4)) }
            assertEquals(plan, SetPlans.decode(SetPlans.encode(plan)))
        }
    }

    // ------------------------------------------------------------------ peso corporal y "Tu progreso"

    @Test fun tendenciaDePesoConDatosExtremos() {
        assertTrue(WeightTrend.smooth(emptyList()).isEmpty())
        assertEquals(listOf(80.0), WeightTrend.smooth(listOf(WeightPoint(1, 80.0, 1))))
        assertNull(WeightTrend.ratePerWeek(listOf(WeightPoint(1, 80.0, 1))))
        repeat(2_000) {
            var day = rnd.nextInt(0, 30_000)
            val pts = List(rnd.nextInt(1, 400)) {
                day += listOf(0, 1, 1, 2, 7, 90, 3650).random(rnd)   // mismos días, huecos de años
                WeightPoint(day, rnd.nextDouble(20.0, 400.0), 1)
            }
            val t = WeightTrend.smooth(pts)
            assertEquals(pts.size, t.size)
            assertTrue(t.all { it.isFinite() && it in 20.0..400.0 })
            WeightTrend.ratePerWeek(pts, t)?.let { r -> assertTrue("ritmo $r", r.isFinite()); assertTrue(okText(WeightTrend.explain(r))) }
        }
        for (days in listOf(Int.MIN_VALUE, -1, 0, 1, 150, 151, 1000, 1001, 7300, Int.MAX_VALUE)) {
            val b = WeightTrend.bucketFor(days)
            assertTrue("cubeta $b para $days días", b >= 1 && (days <= 0 || days / b <= 160))
        }
    }

    @Test fun tuProgresoConDatosRaros() {
        val empty = Totals2(0, 0.0, 0, null)
        val s0 = ProgressStoryBuilder.build(20_000, emptyList(), emptySet(), emptyMap(), empty, empty, empty, 0, 0, emptyList(), 3, emptyList())
        assertTrue(s0.isEmpty); assertTrue(okText(ProgressStoryBuilder.headline(s0))); assertTrue(ProgressStoryBuilder.meaning(s0).isNotEmpty())
        repeat(3_000) {
            val ids = List(rnd.nextInt(0, 6)) { "e$it" }
            val bests = ids.flatMap { id -> List(rnd.nextInt(0, 12)) { k ->
                DayBest(id, 20_000 - rnd.nextInt(0, 2000), listOf(null, 0.0, -1.0, rnd.nextDouble(0.0, 300.0), Double.NaN).random(rnd), d(), i())
            }.distinctBy { it.day } }
            val tot = Totals2(rnd.nextInt(0, 5000), rnd.nextDouble(0.0, 1e8), rnd.nextInt(0, 100_000), listOf(null, 19_000, 20_001).random(rnd))
            val s = ProgressStoryBuilder.build(20_000, bests, ids.filter { rnd.nextBoolean() }.toSet(), ids.associateWith { "Ejercicio $it" },
                tot, tot, tot, rnd.nextInt(0, 500), rnd.nextInt(0, 50), List(rnd.nextInt(0, 300)) { rnd.nextInt(18_000, 20_001) }, 3, List(rnd.nextInt(0, 3)) { 80.0 })
            assertTrue(okText(ProgressStoryBuilder.headline(s)))
            ProgressStoryBuilder.meaning(s).forEach { assertTrue(okText(it)) }
            s.exercises.forEach { e -> assertTrue("cambio ${e.changePct}", e.changePct.isFinite()) }
            assertTrue(s.streakWeeks in 0..s.bestStreakWeeks.coerceAtLeast(s.streakWeeks))
            s.medianStrengthPct?.let { assertTrue(it.isFinite()) }
        }
        for (kg in weird) ProgressStoryBuilder.equivalence(kg)?.let { assertTrue("equivalencia de $kg: $it", okText(it)) }
    }

    // ------------------------------------------------------------------ formatos, fechas e ids

    @Test fun formatosNuncaMuestranBasura() {
        for (v in weird + weird.map { -it }) for (u in WeightUnit.entries) {
            listOf(Fmt.num(v), Fmt.num(v, 0), Fmt.num(v, 2), Fmt.weight(v, u), Fmt.weightBoth(v, u), Fmt.volume(v, u)).forEach {
                assertTrue("formato de $v: '$it'", okText(it) && it.length < 40)
            }
        }
        for (s in listOf(Long.MIN_VALUE, -3600L, -1L, 0L, 59L, 3599L, 3600L, 86_399L, 360_000L, Long.MAX_VALUE)) {
            listOf(Fmt.duration(s), Fmt.durationHuman(s)).forEach { assertTrue("duración de $s: '$it'", !it.contains('-') && it.length < 40) }
        }
        for (s in weirdInt) listOf(Fmt.restLabel(s), Fmt.restShort(s)).forEach { assertTrue("descanso de $s: '$it'", !it.contains('-')) }
        assertEquals("1:30", Fmt.restShort(90)); assertEquals("Sin descanso", Fmt.restLabel(0))
    }

    @Test fun conversionLbKgIdaYVuelta() {
        repeat(10_000) {
            val kg = rnd.nextDouble(0.0, 1500.0)
            for (u in WeightUnit.entries) assertEquals(kg, u.toKg(u.fromKg(kg)), 1e-9)
        }
    }

    @Test fun fechasEnZonasYCambiosDeHorario() {
        val zones = listOf("America/Mexico_City", "America/Tijuana", "America/New_York", "Europe/Madrid", "Pacific/Kiritimati", "Pacific/Pago_Pago",
            "Asia/Kathmandu", "Australia/Lord_Howe", "UTC").map(ZoneId::of)
        // Días con cambio de horario (2021 en México todavía había horario de verano) y fin de año.
        val instants = listOf("2021-04-04T07:59:59Z", "2021-04-04T08:00:00Z", "2021-10-31T06:59:59Z", "2021-10-31T07:00:00Z",
            "2026-03-08T06:59:59Z", "2026-03-08T07:00:00Z", "2026-12-31T23:59:59Z", "2027-01-01T00:00:00Z", "1970-01-01T00:00:00Z").map(Instant::parse)
        for (z in zones) for (ins in instants) {
            val day = Dates.localEpochDay(ins.toEpochMilli(), z)
            assertEquals(ins.atZone(z).toLocalDate().toEpochDay().toInt(), day)
            assertTrue(Dates.formatDay(day).isNotBlank())
            assertTrue(Dates.formatTime(ins.toEpochMilli(), z).isNotBlank())
        }
        // Inicio de semana lunes/domingo en todo un año.
        var d = LocalDate.of(2026, 1, 1)
        repeat(400) {
            assertEquals(java.time.DayOfWeek.MONDAY, Dates.weekStart(d, 1).dayOfWeek)
            assertEquals(java.time.DayOfWeek.SUNDAY, Dates.weekStart(d, 7).dayOfWeek)
            assertEquals(Math.floorDiv(Dates.weekStart(d, 1).toEpochDay().toInt() + Dates.weekShift(1), 7), Math.floorDiv(d.toEpochDay().toInt() + Dates.weekShift(1), 7))
            d = d.plusDays(1)
        }
    }

    @Test fun uuidV7UnicosYOrdenadosAunqueSeanDelMismoMilisegundo() {
        val ms = 1_794_713_903_716L
        val ids = List(200_000) { Ids.uuidV7(ms + it / 50_000) }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids, ids.sorted())
    }

    @Test fun busquedaSinAcentosNiMayusculas() {
        assertEquals("press de banca", "PRÉSS  de Bánca".searchKey().replace(Regex("\\s+"), " "))
        assertEquals("pantorrilla nino", "Pantorrilla Niño".searchKey())
        listOf("", "   ", "💪🏋️", "x".repeat(10_000), "Ä Ö Ü ß Ç").forEach { it.searchKey() }
    }

    // ------------------------------------------------------------------ catálogo

    private val catalog by lazy {
        Json.parseToJsonElement(File("src/main/assets/exercises_es.json").readText()).jsonArray.map { it.jsonObject }
    }
    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.content.orEmpty()

    @Test fun catalogoIntegro() {
        assertTrue(catalog.size >= 250)
        val ids = catalog.map { it.s("id") }
        assertEquals("ids repetidos", ids.size, ids.toSet().size)
        val names = catalog.map { it.s("name").lowercase().searchKey() }
        assertEquals("nombres repetidos: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}", names.size, names.toSet().size)
        val tracks = Tracking.entries.map { it.code }.toSet()
        for (e in catalog) {
            val id = e.s("id")
            assertTrue("id $id", id.matches(Regex("[a-z0-9_]+")))
            assertTrue("nombre de $id", e.s("name").length in 3..70)
            assertTrue("tracking de $id", e.s("tracking") in tracks)
            assertTrue("músculo de $id: ${e.s("primary")}", e.s("primary") in Muscles.names)
            e["secondary"]!!.jsonArray.forEach { m -> assertTrue("secundario de $id: $m", m.jsonPrimitive.content in Muscles.names) }
            assertTrue("consejo de $id", e.s("cue").length >= 10)
            assertFalse("inglés en $id: ${e.s("name")}", Regex("\\b(Press|Curl|Row|Squat|Raise|Fly|Pulldown|Extension|Lunge|Deadlift)\\b").containsMatchIn(e.s("name"))
                && Regex("\\b(with|the|and|on|machine|barbell|dumbbell)\\b", RegexOption.IGNORE_CASE).containsMatchIn(e.s("name")))
            assertTrue("imágenes de $id", e["images"]!!.jsonArray.size in 1..2)
        }
    }

    // ------------------------------------------------------------------ guía de rutinas: TODAS las combinaciones

    @Test fun guiaTodasLasCombinaciones() {
        val ids = catalog.associate { it.s("id") to it }
        val muscles: (String) -> Pair<String, List<String>>? = { id -> ids[id]?.let { it.s("primary") to it["secondary"]!!.jsonArray.map { m -> m.jsonPrimitive.content } } }
        var n = 0
        for (days in 2..6) for (min in listOf(30, 45, 60, 75, 90, 120)) for (eq in RoutineGuide.Equip.entries) for (lv in RoutineGuide.Level.entries)
            for (goal in RoutineGuide.Goal.entries) for (split in RoutineGuide.Split.entries) {
                val input = RoutineGuide.Input(days, min, eq, lv, goal)
                val plan = RoutineGuide.build(input, split)
                val tag = "$days d · $min min · $eq · $lv · $goal · $split"
                assertTrue(tag, plan.sessions.isNotEmpty())
                assertEquals("horario $tag", days, plan.schedule.count { ':' in it && !it.startsWith("La semana") })
                for (s in plan.sessions) {
                    assertTrue("pocos ejercicios en ${s.name} ($tag)", s.items.size >= 3)
                    assertEquals("repetidos en ${s.name} ($tag)", s.items.size, s.items.map { it.exerciseId }.toSet().size)
                    for (it in s.items) {
                        assertTrue("${it.exerciseId} no existe ($tag)", it.exerciseId in ids)
                        assertTrue("series ${it.sets} ($tag)", it.sets in 1..6)
                        assertTrue("rango ${it.repMin}-${it.repMax} ($tag)", it.repMin in 1 until it.repMax && it.repMax <= 30)
                        assertTrue("descanso ${it.restSec} ($tag)", it.restSec in 30..300)
                        val tr = ids.getValue(it.exerciseId).s("tracking")
                        if (eq == RoutineGuide.Equip.MACHINES) assertTrue("${it.exerciseId} no es de gimnasio ($tag)", ids.getValue(it.exerciseId).s("equipment") != "peso_corporal" || tr != "weight_reps")
                    }
                    // Respeta el tiempo cuando se puede (con 3 ejercicios mínimos puede pasarse un poco).
                    if (s.items.size > 3) assertTrue("${s.name} dura ${s.minutes} min con $min disponibles ($tag)", s.minutes <= min)
                    // Grandes primero: ningún aislado antes del primer multiarticular.
                    val firstIso = s.items.indexOfFirst { !it.compound }; val lastComp = s.items.indexOfLast { it.compound }
                    if (firstIso >= 0 && lastComp >= 0) assertTrue("orden en ${s.name} ($tag)", s.items.indexOfFirst { it.compound } < firstIso)
                }
                // Volumen semanal mínimo útil (≈ 4 series) para los grupos grandes en gimnasio, con 45+ min y 3+ días.
                if (eq != RoutineGuide.Equip.BODYWEIGHT && min >= 45 && days >= 3 && !(split == RoutineGuide.Split.PPL && days < 6)) {
                    val w = RoutineGuide.weeklySets(plan, days, muscles)
                    for (m in listOf("pecho", "cuadriceps")) assertTrue("$m con ${w[m]} series/sem ($tag)", (w[m] ?: 0.0) >= 4.0)
                    assertTrue("espalda con ${(w["dorsales"] ?: 0.0) + (w["espalda_media"] ?: 0.0)} ($tag)", (w["dorsales"] ?: 0.0) + (w["espalda_media"] ?: 0.0) >= 4.0)
                }
                n++
            }
        assertEquals(5 * 6 * RoutineGuide.Equip.entries.size * 3 * 3 * 3, n)
    }

    // ------------------------------------------------------------------ contenido de Aprende

    @Test fun contenidoDeAprendeCompletoYConEnlacesValidos() {
        val ids = Concepts.all.map { it.id }
        assertEquals("conceptos repetidos", ids.size, ids.toSet().size)
        for (c in Concepts.all) {
            assertTrue(c.id, c.title.isNotBlank() && c.oneLiner.length in 10..140 && c.simple.length >= 60 && c.example.isNotBlank() && c.inRupio.isNotBlank())
            c.papers.forEach { p -> assertTrue("enlace de ${c.id}: ${p.url}", p.url.startsWith("https://") && ' ' !in p.url) }
        }
        for (t in Tutorials.all) {
            assertTrue(t.id, t.lessons.isNotEmpty())
            for (l in t.lessons) l.concepts.forEach { assertTrue("${t.id}: concepto '$it' no existe", it in ids) }
            for (q in t.quiz) assertTrue("${t.id}: '${q.question}'", q.correct in q.options.indices && q.options.size >= 2 && q.why.isNotBlank())
        }
        Benefits.all.forEach { b -> assertTrue(b.papers.isNotEmpty() && b.papers.all { it.url.startsWith("https://") }) }
        // Cada "?" y cada enlace a un concepto en la app apunta a uno que existe.
        val src = File("src/main/java").walk().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val refs = Regex("""(?:HelpIcon|ConceptSheet|Routes\.concept)\(\s*"([a-z0-9_]+)"""").findAll(src).map { it.groupValues[1] }.toSet() +
            Regex("""TitleWithHelp\([^\n]*?,\s*"([a-z0-9_]+)"""").findAll(src).map { it.groupValues[1] }.toSet()
        assertTrue("se encontraron ${refs.size} referencias", refs.size >= 5)
        refs.forEach { assertTrue("la app enlaza al concepto '$it' que no existe", it in ids) }
    }

    @Test fun textosSinRestosTecnicosNiPendientes() {
        val ui = File("src/main/java/com/rupio/app/ui").walk().filter { it.extension == "kt" }
        val strings = ui.flatMap { f -> Regex(""""((?:[^"\\\n]++|\\.)*+)"""").findAll(f.readText()).map { f.name to it.groupValues[1] } }.toList()
        val bad = Regex("""\b(TODO|FIXME|Lorem|lorem|Supabase|Neon|PostgREST|Data API|JWT|null|undefined|Loading|Error:|Cancel|Settings)\b""")
        val hits = strings.filter { (_, s) -> bad.containsMatchIn(s) && !s.startsWith("http") && !s.contains("\${") }
        assertEquals("Textos con restos técnicos o en inglés:\n" + hits.joinToString("\n"), emptyList<Pair<String, String>>(), hits)
    }
}
