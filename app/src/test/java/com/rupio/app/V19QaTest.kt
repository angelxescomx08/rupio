package com.rupio.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.Ids
import com.rupio.app.core.searchKey
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.Muscles
import com.rupio.app.data.local.RoutineEntity
import com.rupio.app.data.local.RoutineExerciseEntity
import com.rupio.app.data.local.WorkoutExerciseEntity
import com.rupio.app.data.local.WorkoutSetEntity
import com.rupio.app.data.repo.Period
import com.rupio.app.domain.*
import com.rupio.app.timer.WorkoutService
import com.rupio.app.ui.auth.AuthValidation
import com.rupio.app.ui.exercises.techniqueSteps
import com.rupio.app.ui.home.HomeScreen
import com.rupio.app.ui.home.toPlan
import com.rupio.app.ui.stats.rangeText
import com.rupio.app.ui.theme.RupioTheme
import com.rupio.app.ui.workout.ActiveWorkoutVM
import com.rupio.app.ui.workout.ExUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** v1.9 · auditoría de QA (docs/QA_V19_HALLAZGOS.md): cada prueba dice qué hallazgo cubre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V19QaTest {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container
    private val bench = "press_de_banca_con_barra"

    @Before fun setUp() = runBlocking {
        c.auth.useLocalMode("QA")
        repeat(200) { if (c.db.exercises().catalogCount() >= 262) return@repeat; Thread.sleep(50) }
        c.wipeLocalData()
    }

    /** Un entreno de hoy con [sets] series (kg × reps) de [ex], marcadas y terminado. Devuelve (id, récords). */
    private suspend fun session(ex: String, vararg sets: Pair<Double, Int>): Pair<String, Int> {
        val w = c.workouts.startEmpty()
        c.workouts.addExercises(w, listOf(ex))
        val ids = c.db.workouts().sets(w).map { it.id }
        sets.forEachIndexed { i, (kg, r) -> c.workouts.editSet(ids[i]) { it.copy(weightKg = kg, reps = r) }; c.workouts.toggleComplete(ids[i], Tracking.WEIGHT_REPS, null, null, null) }
        return w to c.workouts.finish(w, null, null)
    }

    // ------------------------------------------------------------------ #8 récords

    @Test fun h8_la_primera_vez_no_es_record_y_la_segunda_si() = runBlocking {
        val w = c.workouts.startEmpty()
        c.workouts.addExercises(w, listOf(bench))
        val s = c.db.workouts().sets(w)
        c.workouts.editSet(s[0].id) { it.copy(weightKg = 60.0, reps = 8) }
        assertTrue("la primera serie de tu vida no es récord", c.workouts.toggleComplete(s[0].id, Tracking.WEIGHT_REPS, null, null, null).newRecords.isEmpty())
        c.workouts.editSet(s[1].id) { it.copy(weightKg = 65.0, reps = 8) }
        assertTrue("ni la segunda del mismo primer día", c.workouts.toggleComplete(s[1].id, Tracking.WEIGHT_REPS, null, null, null).newRecords.isEmpty())
        assertEquals("«Rompiste 0 récords»: no se celebra", 0, c.workouts.finish(w, null, null))
        assertEquals("pero la marca se guarda para compararte", 65.0, c.db.records().forExercise(bench).first { it.kind == RecordKind.MAX_WEIGHT.code }.value, 0.0)
        assertFalse("sin trofeos", c.db.workouts().sets(w).any { it.isPr })
        assertEquals("Progreso: 0 récords el primer día", 0, c.db.stats().recordsSince(0))
        assertTrue("ni en «Récords recientes»", c.db.records().observeRecent(8).first().isEmpty())

        Thread.sleep(5)
        val w2 = c.workouts.startEmpty()
        c.workouts.addExercises(w2, listOf(bench))
        val s2 = c.db.workouts().sets(w2)
        c.workouts.editSet(s2[0].id) { it.copy(weightKg = 70.0, reps = 8) }
        val live = c.workouts.toggleComplete(s2[0].id, Tracking.WEIGHT_REPS, null, null, null).newRecords
        assertTrue("70 > 65: ahora sí, al momento", RecordKind.MAX_WEIGHT in live)
        assertEquals("peso máximo y 1RM estimado (8 reps no supera las 8 de antes)", 2, c.workouts.finish(w2, null, null))
        assertTrue(c.db.workouts().getSet(s2[0].id)!!.isPr)
        assertEquals(2, c.db.stats().recordsSince(0))
        assertEquals(2, c.db.records().observeRecent(8).first().size)
    }

    // ------------------------------------------------------------------ #2, #4, #5 entreno

    private fun set(id: String, type: Int = 0, kg: Double? = null, reps: Int? = null, done: Boolean = false) =
        WorkoutSetEntity(id, "we", "w", "u", bench, 0, setType = type, weightKg = kg, reps = reps, completed = done, performedOn = 0, updatedAt = 0)

    @Test fun h2_series_con_datos_sin_marcar_se_distinguen_de_las_vacias() {
        val vm = ActiveWorkoutVM(c)
        val we = WorkoutExerciseEntity("we", "w", "u", bench, 0, updatedAt = 0)
        val items = listOf(ExUi(we, null, listOf(set("a", kg = 50.0, reps = 8, done = true), set("b", kg = 50.0), set("c"), set("d"))))
        assertEquals("solo la que tiene 110 lb escritas", listOf("b"), vm.unmarkedWithData(items).map { it.second.id })
        assertTrue(vm.hasAnyData(items))
        assertFalse("un entreno vacío se descarta sin preguntar", vm.hasAnyData(listOf(ExUi(we, null, listOf(set("x"), set("y"))))))
    }

    @Test fun h4_agregar_calentamiento_no_quita_series_de_trabajo() = runBlocking {
        val w = c.workouts.startEmpty()
        c.workouts.addExercises(w, listOf(bench))
        val we = c.db.workouts().exercises(w).first()
        c.workouts.addWarmup(we.id)
        var sets = c.db.workouts().sets(w)
        assertEquals("C, 1, 2, 3", listOf(1, 0, 0, 0), sets.map { it.setType })
        assertEquals(listOf(0, 1, 2, 3), sets.map { it.position })
        c.workouts.addWarmup(we.id)
        sets = c.db.workouts().sets(w)
        assertEquals("el segundo va después del primero", listOf(1, 1, 0, 0, 0), sets.map { it.setType })
        // "Agregar serie" ya no copia el peso como si lo hubieras escrito (sale en gris como sugerencia).
        c.workouts.editSet(sets.last().id) { it.copy(weightKg = 60.0) }
        c.workouts.addSet(we.id)
        assertNull(c.db.workouts().sets(w).last().weightKg)
        c.workouts.discard(w)
    }

    @Test fun h4_calentamiento_conserva_la_sugerencia_de_reps_y_el_plan_no_se_recorre() {
        val vm = ActiveWorkoutVM(c)
        val we = WorkoutExerciseEntity("we", "w", "u", bench, 0, repMin = 8, repMax = 12, updatedAt = 0,
            setPlan = SetPlans.encode(listOf(PlanSet(w = 60.0, r = 10), PlanSet(w = 60.0, r = 10))))
        val ex = c.db.exercises().let { runBlocking { it.get(bench) } }
        val item = ExUi(we, ex, listOf(set("c", type = SetType.WARMUP.code), set("1"), set("2")))
        assertEquals("el calentamiento sugiere reps (antes la ✓ pedía escribirlas)", 8, vm.suggestion(item, item.sets[0]).reps)
        assertEquals("la serie 1 sigue con el plan de la serie 1", 60.0, vm.suggestion(item, item.sets[1]).weightKg!!, 0.0)
        assertEquals(10, vm.suggestion(item, item.sets[2]).reps)
        assertEquals(60, SetNumbering.WARMUP_REST_SEC)
    }

    @Test fun h5_la_numeracion_es_la_que_se_ve() {
        val types = listOf(SetType.WARMUP.code, 0, 0)
        assertEquals(listOf(null, 1, 2), types.indices.map { SetNumbering.working(types, it) })
        assertEquals(listOf("calentamiento", "serie 1", "serie 2"), types.indices.map { SetNumbering.spoken(types, it) })
        val two = listOf(1, 1, 0, SetType.DROP.code)
        assertEquals(listOf("calentamiento 1", "calentamiento 2", "serie 1", "serie 2"), two.indices.map { SetNumbering.spoken(two, it) })
    }

    @Test fun h7_la_notificacion_dice_lo_que_falta() {
        assertEquals("Quedan 1:24 de descanso", WorkoutService.restTitle(84))
        assertEquals("Quedan 0:00 de descanso", WorkoutService.restTitle(-3))
    }

    // ------------------------------------------------------------------ #11, #14 Inicio y rutinas

    @Test fun h11_te_toca_la_siguiente_de_la_rotacion() {
        val order = listOf("A", "B", "C")
        assertEquals("sin historial, la primera", "A", RoutineRotation.next(order, emptyList()))
        assertEquals("B", RoutineRotation.next(order, listOf(RoutineRotation.Done("A", 10, 100))))
        assertEquals("después de la última, vuelve a la primera", "A", RoutineRotation.next(order, listOf(RoutineRotation.Done("A", 10, 100), RoutineRotation.Done("C", 12, 300))))
        assertEquals("rutinas borradas no cuentan", "A", RoutineRotation.next(order, listOf(RoutineRotation.Done("X", 12, 900))))
        assertEquals(listOf("B", "A", "C"), RoutineRotation.withNextFirst(order, "B") { it })
        assertEquals("Hoy", Dates.daysAgo(100, 100)); assertEquals("Ayer", Dates.daysAgo(99, 100)); assertEquals("Hace 2 días", Dates.daysAgo(98, 100))
    }

    @Test fun h11_ultima_vez_de_cada_rutina() = runBlocking {
        val uid = c.auth.currentUserId!!; val t = System.currentTimeMillis()
        val r = RoutineEntity(Ids.uuidV7(t), uid, "Full body A", null, 0, t, t)
        c.routines.save(r, listOf(RoutineExerciseEntity(Ids.uuidV7(t), r.id, uid, bench, 0, targetSets = 2, updatedAt = t)))
        val w = c.workouts.startFromRoutine(r.id)
        val s = c.db.workouts().sets(w).first()
        c.workouts.editSet(s.id) { it.copy(weightKg = 60.0, reps = 8) }
        c.workouts.toggleComplete(s.id, Tracking.WEIGHT_REPS, null, null, null)
        c.workouts.finish(w, null, null)
        val done = c.workouts.routineLastDone.first()
        assertEquals(listOf(r.id), done.map { it.routineId })
        assertEquals(Dates.today(c.zone.value).toEpochDay().toInt(), done.single().lastDay)
    }

    @Test fun h14_la_guia_y_inicio_dicen_la_misma_duracion() {
        for (equip in RoutineGuide.Equip.entries) {
            val plan = RoutineGuide.build(RoutineGuide.Input(3, 60, equip, RoutineGuide.Level.NEW, RoutineGuide.Goal.MUSCLE))
            for (s in plan.sessions) {
                // Lo que guarda "Crear rutinas" y lo que calcula la tarjeta de Inicio.
                val saved = s.items.map { RoutineExerciseEntity("x", "r", "u", it.exerciseId, 0, restSec = it.restSec, targetSets = it.sets, updatedAt = 0) }
                val home = RestPlanner.summarize(saved.map { it.toPlan() }).estimatedSec
                assertEquals("${s.name} ($equip)", Fmt.durationHuman(home.toLong()), Fmt.durationHuman(s.estimatedSec.toLong()))
            }
        }
    }

    // ------------------------------------------------------------------ #15, #16, #17 ejercicios

    @Test fun h15_busqueda_con_sinonimos_y_cardio() = runBlocking {
        val catalog = c.db.exercises().observeAll().first()
        fun search(q: String) = catalog.filter { ExerciseSearch.matches(it.searchKey, Muscles.label(it.primaryMuscle).searchKey(), ExerciseSearch.tokens(q)) }
        assertTrue("desplante → zancadas", search("desplante").any { it.id == "zancadas_caminando" })
        assertTrue("gemelos → pantorrillas", search("gemelos").isNotEmpty() && search("gemelos").all { it.primaryMuscle == "pantorrillas" || "talones" in it.searchKey })
        assertTrue("flexiones → lagartijas", search("flexiones").any { it.id == "lagartijas" })
        for ((q, id) in listOf("caminadora" to "caminadora", "cinta" to "caminadora", "bicicleta" to "bicicleta_fija", "elíptica" to "eliptica", "correr" to "caminadora"))
            assertTrue("$q → $id", search(q).any { it.id == id })
        assertTrue("cardio como grupo", search("cardio").count { it.primaryMuscle == "cardio" } >= 6)
        assertTrue("espalda incluye dorsales", search("espalda").any { it.primaryMuscle == "dorsales" })
        assertTrue("las palabras sueltas siguen funcionando", search("banca manc").any { it.id == "press_de_banca_con_mancuernas" })
        assertTrue(catalog.first { it.id == "caminadora" }.image0 != null)
    }

    @Test fun h15_cardio_no_cuenta_como_musculo() = runBlocking {
        val split = MuscleSplit.compute(listOf(MuscleSplit.Item("cardio", emptyList(), 3), MuscleSplit.Item("pecho", listOf("triceps"), 3)))
        assertEquals(listOf("pecho", "triceps"), split.map { it.muscle })
        val w = c.workouts.startEmpty()
        c.workouts.addExercises(w, listOf("caminadora"))
        val s = c.db.workouts().sets(w).first()
        c.workouts.editSet(s.id) { it.copy(durationSec = 1200) }
        c.workouts.toggleComplete(s.id, Tracking.DURATION, null, null, null)
        c.workouts.finish(w, null, null)
        assertTrue("una caminadora no infla los cuádriceps", c.stats.snapshot(Period.WEEK, c.zone.value).muscles.isEmpty())
    }

    @Test fun h16_tecnica_en_pasos_para_los_ejercicios_de_la_guia() = runBlocking {
        val ids = RoutineGuide.Equip.entries.flatMap { e -> RoutineGuide.Level.entries.flatMap { l -> RoutineGuide.Goal.entries.flatMap { g ->
            RoutineGuide.Split.entries.flatMap { sp -> RoutineGuide.build(RoutineGuide.Input(3, 90, e, l, g), sp).sessions.flatMap { s -> s.items.map { it.exerciseId } } } } } }.toSet()
        assertTrue(ids.size >= 30)
        for (id in ids) {
            val steps = techniqueSteps(c.db.exercises().get(id)!!.cue)
            assertTrue("$id: ${steps.size} pasos", steps.size in 3..5)
            assertTrue("$id termina con el error común", steps.last().startsWith("Error común:"))
        }
    }

    // ------------------------------------------------------------------ #19, #20 Progreso

    @Test fun h19_la_primera_semana_no_divide_entre_todo_el_mes() = runBlocking {
        session(bench, 60.0 to 8, 60.0 to 8, 60.0 to 8)
        val s = c.stats.snapshot(Period.MONTH, c.zone.value)
        assertTrue("primera semana: sin «poco»", s.firstWeek)
        assertTrue("se divide entre los días desde tu primer entreno, no entre 4 semanas: ${s.weeksInPeriod}", s.weeksInPeriod < 1.0)
        assertEquals(3.0, s.muscles.first { it.muscle == "pecho" }.sets / s.weeksInPeriod.coerceAtLeast(1.0), 1e-9)
    }

    @Test fun h20_rango_con_el_ano_en_las_dos_fechas() {
        val a = LocalDate.of(2025, 12, 1); val b = LocalDate.of(2026, 11, 18)
        assertEquals("${Dates.formatDayShort(a)} 2025 – ${Dates.formatDayShort(b)} 2026", rangeText(a, b))
        assertEquals("${Dates.formatDayShort(LocalDate.of(2026, 10, 1))} – ${Dates.formatDayShort(b)} 2026", rangeText(LocalDate.of(2026, 10, 1), b))
    }

    // ------------------------------------------------------------------ #24 y pulido

    @Test fun h24_todos_los_errores_del_registro_a_la_vez() {
        val f = AuthValidation.check(signUp = true, name = "", email = "ana@", pass = "123")
        assertNotNull(f.name); assertNotNull(f.email); assertNotNull(f.password); assertFalse(f.ok)
        assertTrue(AuthValidation.check(false, "", "ana@ejemplo.com", "12345678").ok)
    }

    @Test fun pulido_plurales_y_zona_horaria() {
        assertEquals("1 entreno", Fmt.plural(1, "entreno", "entrenos")); assertEquals("2 entrenos", Fmt.plural(2, "entreno", "entrenos"))
        assertEquals("0 semanas", Fmt.plural(0, "semana", "semanas"))
        val t = System.currentTimeMillis()
        assertFalse(Dates.isOtherZone(t, ZoneId.of("America/Mexico_City"), "America/Mexico_City"))
        assertTrue(Dates.isOtherZone(t, ZoneId.of("America/Mexico_City"), "Europe/Madrid"))
        assertFalse("zona desconocida: no se menciona", Dates.isOtherZone(t, ZoneId.of("UTC"), "no/existe"))
    }

    // ------------------------------------------------------------------ segunda vuelta (docs/QA_V19_VERIFICACION.md)

    @Test fun n1_cardio_en_minutos_una_serie_sin_descanso_y_consejo_propio() = runBlocking {
        assertEquals(1200, Cardio.parseMinutes("20")); assertEquals(750, Cardio.parseMinutes("12,5")); assertNull(Cardio.parseMinutes("x"))
        assertEquals("20", Cardio.minutesText(1200)); assertEquals("12.5", Cardio.minutesText(750)); assertEquals("", Cardio.minutesText(null))
        val w = c.workouts.startEmpty()
        c.workouts.addExercises(w, listOf("caminadora", bench))
        val ex = c.db.workouts().exercises(w); val sets = c.db.workouts().sets(w)
        val walk = ex.first { it.exerciseId == "caminadora" }; val press = ex.first { it.exerciseId == bench }
        assertEquals("sin descanso automático", 0, walk.restSec)
        assertEquals("1 serie", 1, sets.count { it.workoutExerciseId == walk.id })
        assertEquals("las pesas siguen con 3", 3, sets.count { it.workoutExerciseId == press.id })
        val first = c.workouts.recommendation(c.db.exercises().get("caminadora")!!, 1, 1, w)
        assertTrue(first.detail, first.detail.contains("minutos") && !first.detail.contains("Aguanta"))
        val next = Progression.recommend(ProgressionInput(Tracking.DURATION, 1, 1, false, "maquina", com.rupio.app.core.WeightUnit.KG,
            listOf(SessionData(1, listOf(SetData(null, null, 1200)))), cardio = true))
        assertEquals("Intenta 22 min", next.title); assertEquals(22 * 60, next.targetDurationSec)
        c.workouts.discard(w)
    }

    @Test fun n2_el_cardio_cuenta_en_la_duracion_y_en_las_calorias() = runBlocking {
        val w = c.workouts.startEmpty()
        c.workouts.addExercises(w, listOf("caminadora"))
        val s = c.db.workouts().sets(w).single()
        c.workouts.editSet(s.id) { it.copy(durationSec = 1200) }
        c.workouts.toggleComplete(s.id, Tracking.DURATION, null, null, null)
        c.workouts.finish(w, null, null)
        assertTrue("20 min anotados: el entreno dura al menos eso (antes «2 min»)", c.db.workouts().get(w)!!.durationSec!! >= 1200)
        // 3.8 MET × 80 kg × 20 min = 101 → 100 kcal (antes «≈ 10 kcal» con el reloj y 3.5 MET).
        CalorieEstimate.estimate(120, 0, 80.0, listOf(CalorieEstimate.CardioPart(Cardio.met("caminadora"), 1200)))!!.let {
            assertEquals(100, it.kcal); assertEquals(20, it.cardioMinutes); assertEquals(0, it.strengthMinutes)
        }
        // Mixto: 60 min de reloj, 20 de cardio y 10 series → 40 min de pesas (187) + cardio (101) = 290.
        assertEquals(290, CalorieEstimate.estimate(3600, 10, 80.0, listOf(CalorieEstimate.CardioPart(3.8, 1200)))!!.kcal)
        assertEquals("sin cardio, igual que antes", 280, CalorieEstimate.estimate(3600, 20, 80.0)!!.kcal)
    }

    @Test fun n4_se_sabe_si_el_dia_ya_tiene_peso() = runBlocking {
        val today = Dates.today(c.zone.value)
        c.bodyWeight.log(today.minusDays(1), 80.5)
        assertEquals(80.5, c.bodyWeight.on(today.minusDays(1))!!.weightKg, 1e-9)
        assertNull(c.bodyWeight.on(today))
    }

    @Test fun n5_trofeos_de_antes_de_actualizar_se_recalculan() = runBlocking {
        val user = c.auth.currentUserId!!; val zone = c.zone.value
        suspend fun seed(daysAgo: Int, kg: Double, pr: Boolean, prCount: Int): Pair<String, String> {
            val start = System.currentTimeMillis() - daysAgo * 86_400_000L; val day = Dates.localEpochDay(start, zone)
            val w = com.rupio.app.data.local.WorkoutEntity(Ids.uuidV7(start), user, null, "Pecho", start, start + 3_000_000, zone.id, day, 3000,
                totalVolumeKg = kg * 8, totalSets = 1, totalReps = 8, prCount = prCount, updatedAt = start, dirty = false)
            val we = WorkoutExerciseEntity(Ids.uuidV7(start), w.id, user, bench, 0, updatedAt = start, dirty = false)
            val s = WorkoutSetEntity(Ids.uuidV7(start), we.id, w.id, user, bench, 0, weightKg = kg, reps = 8, completed = true, completedAt = start,
                performedOn = day, isPr = pr, updatedAt = start, dirty = false)
            c.db.workouts().insertFull(w, listOf(we), listOf(s)); return w.id to s.id
        }
        // Como los dejaba la versión anterior: la primera vez con 🏆 3, la segunda sin trofeo.
        val (w1, s1) = seed(3, 60.0, pr = true, prCount = 3)
        val (w2, s2) = seed(1, 70.0, pr = false, prCount = 0)
        assertEquals("cambian los dos entrenos", 2, c.workouts.recomputeTrophies())
        assertEquals(0, c.db.workouts().get(w1)!!.prCount); assertFalse(c.db.workouts().getSet(s1)!!.isPr)
        assertEquals("peso máximo y 1RM", 2, c.db.workouts().get(w2)!!.prCount); assertTrue(c.db.workouts().getSet(s2)!!.isPr)
        assertTrue("se suben a la nube", c.db.workouts().get(w1)!!.dirty && c.db.workouts().getSet(s1)!!.dirty)
        assertEquals("la segunda vez no hay nada que cambiar", 0, c.workouts.recomputeTrophies())
    }

    @Test fun n6_primero_el_nombre_exacto_y_luego_los_sinonimos() = runBlocking {
        val catalog = c.db.exercises().observeAll().first()
        fun search(q: String) = catalog.filter { ExerciseSearch.matches(it.searchKey, Muscles.label(it.primaryMuscle).searchKey(), ExerciseSearch.tokens(q)) }
            .sortedWith(compareBy({ ExerciseSearch.rank(it.searchKey, q) }, { !it.isCustom }, { it.searchKey }))
        assertEquals("lagartijas", search("lagartijas").first().id)
        assertTrue("los sinónimos siguen saliendo, después", search("lagartijas").any { it.searchKey.startsWith("flexion") })
        assertEquals(0, ExerciseSearch.rank("lagartijas con pies elevados", "lagartijas"))
        assertEquals(1, ExerciseSearch.rank("press de banca con mancuernas", "banca manc"))
        assertEquals(2, ExerciseSearch.rank("flexiones de pino", "lagartijas"))
    }

    @Test fun n7_guardar_como_rutina_no_repite_el_nombre() {
        assertEquals("Pierna casa", com.rupio.app.data.repo.uniqueName("Pierna casa", listOf("Torso")))
        assertEquals("Pierna casa (2)", com.rupio.app.data.repo.uniqueName("Pierna casa", listOf("Pierna casa")))
        assertEquals("sin distinguir mayúsculas", "pierna CASA (3)", com.rupio.app.data.repo.uniqueName("pierna CASA", listOf("Pierna casa", "Pierna casa (2)")))
        assertEquals("Plan (2)", com.rupio.app.data.repo.uniqueName("Plan (2)", listOf("Plan")))
    }

    @Test fun pulido_la_recomendacion_habla_de_la_vez_pasada() {
        val r = Progression.recommend(ProgressionInput(Tracking.WEIGHT_REPS, 8, 12, false, "barra", com.rupio.app.core.WeightUnit.KG,
            listOf(SessionData(1, listOf(SetData(60.0, 9), SetData(60.0, 8))))))
        assertEquals(RecKind.ADD_REPS, r.kind)
        assertTrue(r.detail, r.detail.contains("la vez pasada"))
    }
}

/** v1.9 · #11 y #13 en la pantalla real: "Te toca hoy" va primero y la tarjeta entera abre la rutina. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class V19QaUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()
    private val c get() = app.container

    private fun settle(ms: Long = 800) {
        repeat(4) { ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(ms / 4); compose.waitForIdle() }
    }

    @Test fun inicio_dice_que_rutina_toca() = runBlocking<Unit> {
        CatalogSeeder.seedIfNeeded(app, c.db)
        c.settings.setOnboardingDone(true); c.settings.setTutorialHome(true); c.settings.setTutorialWorkout(true)
        c.auth.useLocalMode("José Ángel Hernández")
        c.wipeLocalData()
        val uid = c.auth.currentUserId!!; val t = System.currentTimeMillis()
        val ids = listOf("Full body A", "Full body B").mapIndexed { k, name ->
            val r = RoutineEntity(Ids.uuidV7(t + k), uid, name, null, k, t, t)
            c.routines.save(r, listOf(RoutineExerciseEntity(Ids.uuidV7(t + k), r.id, uid, "press_de_banca_con_barra", 0, targetSets = 2, updatedAt = t)))
            r.id
        }
        // Hizo la A hace 2 días (la guía: "la semana siguiente sigue donde te quedaste").
        val w = c.workouts.startFromRoutine(ids[0])
        val s = c.db.workouts().sets(w).first()
        c.workouts.editSet(s.id) { it.copy(weightKg = 60.0, reps = 8) }
        c.workouts.toggleComplete(s.id, Tracking.WEIGHT_REPS, null, null, null)
        c.workouts.finish(w, null, null)
        val day = Dates.today(c.zone.value).minusDays(2).toEpochDay().toInt()
        c.db.workouts().upsertWorkout(c.db.workouts().get(w)!!.copy(localDate = day))

        compose.setContent { RupioTheme { Surface(color = MaterialTheme.colorScheme.background) { HomeScreen(rememberNavController()) } } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Te toca hoy", substring = true).fetchSemanticsNodes().isNotEmpty() }
        settle()
        val b = compose.onNodeWithText("Full body B").fetchSemanticsNode().boundsInRoot.top
        val a = compose.onNodeWithText("Full body A").fetchSemanticsNode().boundsInRoot.top
        assertTrue("la B (la que toca) va primero", b < a)
        compose.onNodeWithText("Última vez: hace 2 días").assertExists()
        compose.onNodeWithText("Aún no la haces").assertExists()
        // La tarjeta entera se puede tocar para abrir la rutina.
        compose.onNode(hasClickAction() and hasText("Full body B")).assertExists()
    }
}
