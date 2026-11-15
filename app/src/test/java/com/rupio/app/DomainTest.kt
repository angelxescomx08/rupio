package com.rupio.app

import com.rupio.app.core.Dates
import com.rupio.app.core.Ids
import com.rupio.app.core.WeightUnit
import com.rupio.app.core.searchKey
import com.rupio.app.domain.*
import com.rupio.app.timer.RestTimerController
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DomainTest {
    private val lb = WeightUnit.LB
    private fun lbKg(v: Double) = lb.toKg(v)
    private fun s(w: Double, r: Int, t: SetType = SetType.NORMAL, rpe: Double? = null) = SetData(lbKg(w), r, rpe = rpe, type = t)
    private fun input(vararg sessions: SessionData, lower: Boolean = false, tracking: Tracking = Tracking.WEIGHT_REPS) =
        ProgressionInput(tracking, 8, 12, lower, "barra", lb, sessions.toList())

    @Test fun subePesoCuandoTodasLasSeriesLleganAlMaximo() {
        val r = Progression.recommend(input(SessionData(1, listOf(s(135.0, 10, SetType.WARMUP), s(225.0, 12), s(225.0, 12), s(225.0, 12)))))
        assertEquals(RecKind.INCREASE_LOAD, r.kind)
        assertEquals(230.0, lb.fromKg(r.targetWeightKg!!), 0.01) // +2.5 % redondeado a 5 lb
        assertEquals(8, r.targetReps)
    }

    @Test fun trenInferiorSubeMas() {
        val r = Progression.recommend(input(SessionData(1, listOf(s(315.0, 12), s(315.0, 12), s(315.0, 12))), lower = true))
        assertEquals(330.0, lb.fromKg(r.targetWeightKg!!), 0.01) // +5 % → 330 lb
    }

    @Test fun reglaDosPorDos() {
        val a = SessionData(2, listOf(s(200.0, 12), s(200.0, 11), s(200.0, 14)))
        val b = SessionData(1, listOf(s(200.0, 12), s(200.0, 10), s(200.0, 14)))
        assertEquals(RecKind.INCREASE_LOAD, Progression.recommend(input(a, b)).kind)
    }

    @Test fun mantienePesoYSumaReps() {
        val r = Progression.recommend(input(SessionData(1, listOf(s(225.0, 12), s(225.0, 10), s(225.0, 9)))))
        assertEquals(RecKind.ADD_REPS, r.kind)
        assertEquals(10, r.targetReps)
    }

    @Test fun rpeMaximoNoSube() {
        val r = Progression.recommend(input(SessionData(1, listOf(s(225.0, 12), s(225.0, 11), s(225.0, 10, rpe = 10.0)))))
        assertNotEquals(RecKind.INCREASE_LOAD, r.kind)
    }

    @Test fun bajaPesoTrasDosSesionesFallidas() {
        val a = SessionData(2, listOf(s(245.0, 7), s(245.0, 6)))
        val b = SessionData(1, listOf(s(245.0, 7), s(245.0, 5)))
        val r = Progression.recommend(input(a, b))
        assertEquals(RecKind.DELOAD, r.kind)
        assertTrue(lb.fromKg(r.targetWeightKg!!) < 245.0)
    }

    @Test fun soloRepeticiones() {
        val r = Progression.recommend(ProgressionInput(Tracking.REPS_ONLY, 8, 15, false, "peso_corporal", lb,
            listOf(SessionData(1, listOf(SetData(null, 15), SetData(null, 15))))))
        assertEquals(RecKind.HARDER_VARIATION, r.kind)
        val r2 = Progression.recommend(ProgressionInput(Tracking.REPS_ONLY, 8, 15, false, "peso_corporal", lb,
            listOf(SessionData(1, listOf(SetData(null, 12), SetData(null, 10))))))
        assertEquals(RecKind.ADD_REPS, r2.kind); assertEquals(11, r2.targetReps)
    }

    @Test fun primeraVez() = assertEquals(RecKind.FIRST_TIME, Progression.recommend(input()).kind)

    @Test fun unoRmEstimado() {
        assertEquals(100.0, OneRepMax.estimate(100.0, 1)!!, 0.001)
        assertNull(OneRepMax.estimate(50.0, 20))
        val e = OneRepMax.estimate(100.0, 5)!!
        assertTrue(e in 112.0..117.0)
    }

    @Test fun descansoSuperserieCompartido() {
        val g = listOf(
            PlanItem("a1", 3, 30, "ss", SupersetRestMode.SHARED, 120),
            PlanItem("a2", 3, 30, "ss", SupersetRestMode.SHARED, 120),
        )
        assertEquals(0, RestPlanner.restAfterSet(g[0], g, 0, completedInRound = 1))
        assertEquals(120, RestPlanner.restAfterSet(g[1], g, 0, completedInRound = 2))
        val sum = RestPlanner.summarize(g + PlanItem("b", 3, 90, null, SupersetRestMode.INDIVIDUAL, null))
        // 3 rondas de superserie (3×120) + 3 series de b con 90 → 360 + 270 − último descanso (90) = 540
        assertEquals(540, sum.totalRestSec)
        assertEquals(9, sum.totalSets)
    }

    @Test fun descansoSuperserieIndividual() {
        val g = listOf(
            PlanItem("a1", 2, 15, "ss", SupersetRestMode.INDIVIDUAL, null),
            PlanItem("a2", 2, 90, "ss", SupersetRestMode.INDIVIDUAL, null),
        )
        assertEquals(15, RestPlanner.restAfterSet(g[0], g, 0, 1))
        assertEquals((15 + 90) * 2 - 90, RestPlanner.summarize(g).totalRestSec)
    }

    @Test fun temporizadorNoSuenaDobleAlMarcarYDesmarcar() {
        var now = 0L
        val t = RestTimerController { now }
        val g1 = t.start(90, "serie-1", "Descanso")
        t.cancelIfSource("serie-1")            // desmarca
        val g2 = t.start(90, "serie-1", "Descanso") // vuelve a marcar
        now = 90_000
        assertFalse("el temporizador viejo no debe sonar", t.tryConsumeFinish(g1))
        assertTrue(t.tryConsumeFinish(g2))
        assertFalse("no debe sonar dos veces", t.tryConsumeFinish(g2))
    }

    @Test fun temporizadorNoSuenaAntesDeTiempo() {
        var now = 0L
        val t = RestTimerController { now }
        val g = t.start(60, "x", "Descanso")
        now = 59_000
        assertFalse(t.tryConsumeFinish(g))
        t.adjust(+15); now = 61_000
        assertFalse(t.tryConsumeFinish(g))
        now = 75_000
        assertTrue(t.tryConsumeFinish(g))
    }

    @Test fun desmarcarOtraSerieNoCancela() {
        val t = RestTimerController { 0L }
        t.start(60, "a", "Descanso")
        t.cancelIfSource("b")
        assertNotNull(t.state.value)
    }

    @Test fun records() {
        val cur = mapOf(RecordKind.MAX_WEIGHT to lbKg(225.0), RecordKind.E1RM to 1000.0)
        val beaten = Records.beaten(Tracking.WEIGHT_REPS, s(230.0, 3), cur)
        assertTrue(RecordKind.MAX_WEIGHT in beaten)
        assertFalse(RecordKind.E1RM in beaten)
        assertTrue(Records.beaten(Tracking.WEIGHT_REPS, s(300.0, 3, SetType.WARMUP), cur).isEmpty())
    }

    @Test fun conversionLbKg() {
        assertEquals(225.0, lb.fromKg(lb.toKg(225.0)), 1e-9)
        assertEquals(102.06, lb.toKg(225.0), 0.01)
    }

    @Test fun fechaLocalDependeDeLaZona() {
        // 2026-11-15 05:30 UTC = 14 nov 23:30 en CDMX, 15 nov en Madrid
        val ms = java.time.Instant.parse("2026-11-15T05:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 14).toEpochDay().toInt(), Dates.localEpochDay(ms, ZoneId.of("America/Mexico_City")))
        assertEquals(LocalDate.of(2026, 11, 15).toEpochDay().toInt(), Dates.localEpochDay(ms, ZoneId.of("Europe/Madrid")))
    }

    @Test fun inicioDeSemanaConfigurable() {
        val sat = LocalDate.of(2026, 11, 14)
        assertEquals(LocalDate.of(2026, 11, 9), Dates.weekStart(sat, 1))
        assertEquals(LocalDate.of(2026, 11, 8), Dates.weekStart(sat, 7))
        // Aritmética usada en SQL: epochDay - ((epochDay + shift) % 7)
        for (iso in listOf(1, 7)) {
            val d = sat.toEpochDay().toInt()
            assertEquals(Dates.weekStart(sat, iso).toEpochDay().toInt(), d - ((d + Dates.weekShift(iso)) % 7))
        }
    }

    @Test fun uuidV7Ordenado() {
        val a = Ids.uuidV7(1_000); val b = Ids.uuidV7(2_000)
        assertTrue(a < b); assertEquals('7', a[14])
    }

    @Test fun busquedaSinAcentos() = assertEquals("press de banca", "Press de Bánca".searchKey())
}
