package com.rupio.app

import com.rupio.app.core.WeightUnit
import com.rupio.app.data.local.DayBest
import com.rupio.app.data.local.Totals2
import com.rupio.app.data.local.WeightPoint
import com.rupio.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class NewFeaturesTest {
    private val lb = WeightUnit.LB

    // ---------------------------------------------------------------- peso corporal
    @Test fun tendenciaIgnoraUnDiaRaro() {
        val pts = (0 until 20).map { WeightPoint(it, 80.0, 1) } + WeightPoint(20, 82.0, 1)
        val t = WeightTrend.smooth(pts)
        assertEquals(80.0, t[19], 1e-9)
        assertTrue("un día +2 kg mueve la tendencia solo ~0.2 kg", t.last() < 80.25)
    }

    @Test fun huecoDeVariosDiasPesaMas() {
        val t1 = WeightTrend.smooth(listOf(WeightPoint(0, 80.0, 1), WeightPoint(1, 78.0, 1))).last()
        val t7 = WeightTrend.smooth(listOf(WeightPoint(0, 80.0, 1), WeightPoint(7, 78.0, 1))).last()
        assertTrue(80.0 - t7 > 80.0 - t1)
    }

    @Test fun ritmoSemanalEnPorcentaje() {
        // Baja 0.1 kg por día durante 4 semanas desde 80 kg ≈ −0.8 %/sem (con el retraso de la media)
        val pts = (0..28).map { WeightPoint(it, 80.0 - it * 0.1, 1) }
        val r = WeightTrend.ratePerWeek(pts)!!
        assertTrue("ritmo $r", r < -0.5 && r > -1.0)
        assertEquals(WeightTrend.Pace.LOSING_GOOD, WeightTrend.pace(r))
        assertNull("menos de 7 días: aún no se puede saber", WeightTrend.ratePerWeek(pts.take(5)))
    }

    @Test fun cubetaSegunRango() {
        assertEquals(1, WeightTrend.bucketFor(90))
        assertEquals(7, WeightTrend.bucketFor(365))
        assertTrue(7300 / WeightTrend.bucketFor(7300) <= 160)
    }

    // ---------------------------------------------------------------- progresión ignora drop sets
    @Test fun dropSetsNoCuentanParaSubirPeso() {
        val k = lb.toKg(100.0)
        val sessions = listOf(SessionData(1, listOf(SetData(k, 12), SetData(k, 12), SetData(k, 12), SetData(lb.toKg(70.0), 6, type = SetType.DROP))))
        val r = Progression.recommend(ProgressionInput(Tracking.WEIGHT_REPS, 8, 12, false, "maquina", lb, sessions))
        assertEquals(RecKind.INCREASE_LOAD, r.kind)
    }

    // ---------------------------------------------------------------- tu progreso
    private fun b(ex: String, day: Int, kg: Double, reps: Int) = DayBest(ex, day, OneRepMax.estimate(kg, reps), kg, reps)
    private val zero = Totals2(0, 0.0, 0, null)

    @Test fun historiaDeProgresoCalculaAntesYAhora() {
        val bests = listOf(b("press", 100, 40.0, 10), b("press", 107, 42.5, 10), b("press", 114, 45.0, 10), b("press", 121, 47.5, 10),
            b("remo", 100, 50.0, 10), b("remo", 110, 50.0, 10))
        val s = ProgressStoryBuilder.build(130, bests, emptySet(), mapOf("press" to "Press", "remo" to "Remo"),
            Totals2(4, 4000.0, 40, 100), zero, zero, 3, 1, listOf(100, 107, 114, 121), 3, emptyList())
        val press = s.exercises.first { it.exerciseId == "press" }
        assertEquals(18.75, press.changePct, 0.01)
        assertEquals(47.5, press.now.weight!!, 1e-9)
        assertEquals("Press", s.exercises.first().name) // ordenado por mejora
        assertTrue(ProgressStoryBuilder.headline(s).contains("%"))
    }

    @Test fun detectaEstancamiento() {
        val bests = (0 until 8).map { b("sentadilla", 200 + it * 4, 100.0, 8) }
        val s = ProgressStoryBuilder.build(232, bests, emptySet(), emptyMap(), Totals2(8, 1.0, 1, 200), zero, zero, 0, 0, emptyList(), 3, emptyList())
        assertTrue(s.exercises.single().stalled)
    }

    @Test fun rachaDeSemanas() {
        // Lunes 2026-11-09 = epoch 20766. Entrenó un día en cada una de las 4 semanas hasta la actual; hoy es sábado 14.
        val days = listOf(20766 - 21, 20766 - 14 + 2, 20766 - 7 + 4, 20766)
        val s = ProgressStoryBuilder.build(20771, emptyList(), emptySet(), emptyMap(), Totals2(4, 1.0, 1, days.first()), zero, zero, 0, 0, days, 3, emptyList())
        assertEquals(4, s.streakWeeks)
        assertEquals(4, s.bestStreakWeeks)
    }

    // ---------------------------------------------------------------- guía de rutinas
    @Test fun guiaEligeDivisionSegunDias() {
        assertEquals(RoutineGuide.Split.FULL_BODY, RoutineGuide.recommendedSplit(2))
        assertEquals(RoutineGuide.Split.FULL_BODY, RoutineGuide.recommendedSplit(3))
        assertEquals(RoutineGuide.Split.UPPER_LOWER, RoutineGuide.recommendedSplit(4))
        assertEquals(RoutineGuide.Split.PPL, RoutineGuide.recommendedSplit(6))
    }

    @Test fun guiaRespetaElTiempo() {
        for (equip in RoutineGuide.Equip.entries) for (lvl in RoutineGuide.Level.entries) for (min in listOf(45, 60, 90)) for (d in 2..6) {
            val plan = RoutineGuide.build(RoutineGuide.Input(d, min, equip, lvl, RoutineGuide.Goal.MUSCLE))
            plan.sessions.forEach { s ->
                assertTrue("${s.name} $equip $lvl $min min → ${s.minutes}", s.minutes <= min || s.items.size <= 3)
                assertTrue("${s.name} $equip $lvl $d días: ${s.items.size} ejercicios", s.items.size >= 3)
                assertEquals(s.items.size, s.items.distinctBy { it.exerciseId }.size)
            }
        }
    }

    @Test fun guiaPrefiereMaquinas() {
        val plan = RoutineGuide.build(RoutineGuide.Input(3, 60, RoutineGuide.Equip.MACHINES, RoutineGuide.Level.NEW, RoutineGuide.Goal.MUSCLE))
        val ids = plan.sessions.flatMap { s -> s.items.map { it.exerciseId } }
        assertTrue(ids.contains("prensa_de_piernas"))
        assertTrue(ids.contains("press_de_pecho_en_maquina"))
        assertFalse(ids.any { it.contains("con_barra") })
    }

    @Test fun guiaFuerzaUsaMenosReps() {
        val plan = RoutineGuide.build(RoutineGuide.Input(4, 75, RoutineGuide.Equip.FREE, RoutineGuide.Level.ADVANCED, RoutineGuide.Goal.STRENGTH))
        val big = plan.sessions.flatMap { it.items }.filter { it.compound }
        assertTrue(big.all { it.repMax <= 8 && it.restSec >= 150 })
    }

    @Test fun seriesSemanalesConMetodoFraccional() {
        val plan = RoutineGuide.Plan(RoutineGuide.Split.FULL_BODY,
            listOf(RoutineGuide.Session("A", listOf(RoutineGuide.Item("press", 3, 8, 12, 120, true)))), emptyList())
        val w = RoutineGuide.weeklySets(plan, 2) { if (it == "press") "pecho" to listOf("triceps") else null }
        assertEquals(6.0, w["pecho"]!!, 1e-9)
        assertEquals(3.0, w["triceps"]!!, 1e-9)
    }

    // ---------------------------------------------------------------- planes por serie
    @Test fun planIdaYVuelta() {
        val p = listOf(PlanSet(36.3, 12, t = 1), PlanSet(74.8, 12), PlanSet(74.8, 11))
        assertEquals(p, SetPlans.decode(SetPlans.encode(p)))
        assertEquals(8 to 12, SetPlans.repRange(p, 1 to 1))
        assertEquals(4, SetPlans.resize(p, 4).size)
        assertEquals(SetType.NORMAL.code, SetPlans.resize(p, 4)[3].t)
    }
}
