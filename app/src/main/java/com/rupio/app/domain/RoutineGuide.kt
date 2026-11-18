package com.rupio.app.domain

/**
 * Guía para armar una rutina a tu medida (días, tiempo, equipo, experiencia y objetivo).
 *
 * Reglas (con su respaldo):
 *  - Cada músculo ~2 veces por semana cuando se puede; lo que más importa es el total de series por semana
 *    (Schoenfeld 2017; Grgic 2018; Pelland 2026). Mínimo útil ≈ 4 series por músculo a la semana (Iversen 2021).
 *  - Ejercicios grandes primero (multiarticulares), luego los pequeños (ACSM 2009).
 *  - Repeticiones: músculo crece parecido de 6 a 30 reps cerca del fallo; la fuerza máxima mejora más con pesos altos
 *    (Schoenfeld 2017; Lopez 2021). Por eso el objetivo cambia el rango.
 *  - Descanso: > 60 s ayuda; más de ~90 s no mostró beneficio extra para el músculo (Singer 2024); más en los ejercicios pesados.
 */
object RoutineGuide {

    enum class Equip(val label: String, val sub: String) {
        MACHINES("Gimnasio · máquinas (recomendado)", "Más fácil: tienen apoyo para la espalda o el pecho y el recorrido está guiado. Así haces siempre la misma técnica y es más fácil medir tu progreso."),
        MIX("Gimnasio · mezcla", "Los básicos con barra o mancuerna y el resto en máquinas."),
        FREE("Gimnasio · pesas libres", "Barras y mancuernas. Funcionan igual de bien, pero piden más técnica y equilibrio."),
        DUMBBELLS("Mancuernas y banco", "En casa o en un gimnasio pequeño."),
        BODYWEIGHT("Sin equipo", "Solo tu cuerpo (y una mesa firme o una barra)."),
    }
    enum class Level(val label: String, val sub: String) {
        NEW("Estoy empezando", "Nunca he entrenado o llevo menos de 6 meses"),
        INTER("Intermedio", "De 6 meses a 2 años entrenando con constancia"),
        ADVANCED("Avanzado", "Más de 2 años; ya progreso lento y conozco la técnica"),
    }
    enum class Goal(val label: String, val sub: String) {
        MUSCLE("Ganar músculo", "Verme más grande o marcado"),
        STRENGTH("Ganar fuerza", "Levantar más peso"),
        HEALTH("Salud y forma", "Sentirme bien y fuerte para mi día a día"),
    }
    enum class Split(val label: String, val what: String, val why: String) {
        FULL_BODY("Cuerpo completo (full body)",
            "Cada día entrenas todo el cuerpo con pocos ejercicios por músculo.",
            "Ideal con 2–3 días: cada músculo se entrena 2–3 veces por semana aunque vayas poco. La mejor opción para empezar."),
        UPPER_LOWER("Torso / Pierna",
            "Un día torso (pecho, espalda, hombros, brazos) y otro pierna (cuádriceps, femorales, glúteos, pantorrillas).",
            "Ideal con 4 días: cada músculo 2 veces por semana con más series por sesión y sesiones más cortas que un full body."),
        PPL("Empuje / Jalón / Pierna",
            "Empuje: pecho, hombros y tríceps. Jalón: espalda y bíceps. Pierna: todo lo de abajo.",
            "Ideal con 6 días (cada músculo 2 veces). Con 3 días cada músculo se entrena solo 1 vez por semana: funciona, pero full body suele rendir más."),
    }

    data class Input(val days: Int = 3, val minutes: Int = 60, val equip: Equip = Equip.MACHINES, val level: Level = Level.NEW, val goal: Goal = Goal.MUSCLE)

    data class Item(val exerciseId: String, val sets: Int, val repMin: Int, val repMax: Int, val restSec: Int, val compound: Boolean)
    data class Session(val name: String, val items: List<Item>) {
        /**
         * v1.9: la MISMA cuenta que la tarjeta de la rutina en Inicio (RestPlanner.summarize): ~40 s por serie más todos
         * los descansos, también entre ejercicios. Antes la guía decía 34 min y la misma rutina en Inicio 39 min.
         */
        val estimatedSec get() = RestPlanner.summarize(items.map { PlanItem(it.exerciseId, it.sets, it.restSec, null, SupersetRestMode.INDIVIDUAL, null) }).estimatedSec
        val minutes get() = estimatedSec / 60
    }
    data class Plan(val split: Split, val sessions: List<Session>, val schedule: List<String>)

    fun recommendedSplit(days: Int, level: Level = Level.NEW): Split = when {
        days <= 3 -> Split.FULL_BODY
        days == 4 || (days == 5 && level != Level.ADVANCED) -> Split.UPPER_LOWER
        else -> Split.PPL
    }

    // Patrones de movimiento → ejercicio del catálogo según el equipo (null = no hay buena opción; se omite).
    private enum class P(val compound: Boolean) {
        SQUAT(true), QUAD2(false), HINGE(true), HAM(false), GLUTE(true), PUSH(true), INCLINE(true), VPULL(true), ROW(true),
        OHP(true), LATERAL(false), REAR(false), BICEPS(false), TRICEPS(false), CALVES(false), ABS(false)
    }

    private fun pick(p: P, i: Input): String? {
        val new = i.level == Level.NEW; val adv = i.level == Level.ADVANCED
        return when (i.equip) {
            Equip.DUMBBELLS -> when (p) {
                P.SQUAT -> "sentadilla_goblet"; P.QUAD2 -> "sentadilla_bulgara_con_mancuernas"; P.HINGE -> "peso_muerto_rumano_con_mancuernas"
                P.HAM -> null; P.GLUTE -> "puente_de_gluteo"; P.PUSH -> "press_de_banca_con_mancuernas"; P.INCLINE -> "press_inclinado_con_mancuernas"
                P.VPULL -> "remo_con_dos_mancuernas"; P.ROW -> "remo_con_mancuerna_a_una_mano"; P.OHP -> "press_sentado_con_mancuernas"
                P.LATERAL -> "elevaciones_laterales_con_mancuernas"; P.REAR -> "pajaros_con_mancuernas"; P.BICEPS -> "curl_de_biceps_con_mancuernas"
                P.TRICEPS -> "extension_de_triceps_con_mancuerna_sobre_la_cabeza"; P.CALVES -> "elevacion_de_talones_con_mancuerna"; P.ABS -> "abdominales_crunch"
            }
            Equip.BODYWEIGHT -> when (p) {
                P.SQUAT -> "sentadilla_libre"; P.QUAD2 -> "zancadas_caminando"; P.HINGE -> "puente_de_gluteo_a_una_pierna"; P.HAM -> if (adv) "curl_nordico" else null
                P.GLUTE -> "puente_de_gluteo"; P.PUSH -> "lagartijas"; P.INCLINE -> "lagartijas_con_pies_elevados"; P.VPULL -> if (new) "remo_invertido" else "dominadas"
                P.ROW -> "remo_invertido"; P.OHP -> if (new) "lagartijas_declinadas" else "flexiones_de_pino"; P.LATERAL -> null; P.REAR -> null
                P.BICEPS -> null; P.TRICEPS -> "fondos_en_banco"; P.CALVES -> null; P.ABS -> "abdominales_crunch"
            }
            // Gimnasio: las máquinas con respaldo y apoyos son más fáciles de aprender y de repetir siempre igual.
            Equip.MACHINES, Equip.MIX, Equip.FREE -> when (i.equip) {
                Equip.MACHINES -> when (p) {
                    P.SQUAT -> if (adv) "sentadilla_hack_en_maquina" else "prensa_de_piernas"; P.QUAD2 -> "extension_de_piernas"
                    P.HINGE -> "peso_muerto_rumano_en_smith"; P.HAM -> "curl_femoral_sentado"; P.GLUTE -> "hip_thrust_con_barra"
                    P.PUSH -> "press_de_pecho_en_maquina"; P.INCLINE -> "press_inclinado_en_maquina"; P.VPULL -> "jalon_al_pecho_agarre_ancho"
                    P.ROW -> if (new) "remo_en_maquina" else "remo_en_t_con_apoyo_de_pecho"; P.OHP -> "press_de_hombros_en_maquina"
                    P.LATERAL -> "elevacion_lateral_en_polea"; P.REAR -> "aperturas_invertidas_en_maquina"; P.BICEPS -> "curl_predicador_en_maquina"
                    P.TRICEPS -> "extension_de_triceps_en_maquina"; P.CALVES -> "elevacion_de_talones_de_pie_en_maquina"; P.ABS -> "crunch_en_maquina"
                }
                Equip.MIX -> when (p) {
                    P.SQUAT -> if (new) "prensa_de_piernas" else "sentadilla_con_barra"; P.QUAD2 -> "extension_de_piernas"
                    P.HINGE -> if (new) "peso_muerto_rumano_con_mancuernas" else "peso_muerto_rumano"; P.HAM -> "curl_femoral_sentado"; P.GLUTE -> "hip_thrust_con_barra"
                    P.PUSH -> if (new) "press_de_banca_con_mancuernas" else "press_de_banca_con_barra"; P.INCLINE -> "press_inclinado_en_maquina"
                    P.VPULL -> "jalon_al_pecho_agarre_ancho"; P.ROW -> "remo_en_maquina"; P.OHP -> "press_sentado_con_mancuernas"
                    P.LATERAL -> "elevaciones_laterales_con_mancuernas"; P.REAR -> "aperturas_invertidas_en_maquina"; P.BICEPS -> "curl_de_biceps_con_mancuernas"
                    P.TRICEPS -> "extension_de_triceps_con_cuerda"; P.CALVES -> "elevacion_de_talones_de_pie_en_maquina"; P.ABS -> "crunch_en_polea"
                }
                else -> when (p) {
                    P.SQUAT -> if (new) "sentadilla_goblet" else "sentadilla_con_barra"; P.QUAD2 -> if (new) "zancadas_con_mancuernas" else "sentadilla_bulgara_con_mancuernas"
                    P.HINGE -> if (new) "peso_muerto_rumano_con_mancuernas" else "peso_muerto_rumano"; P.HAM -> "curl_femoral_sentado"; P.GLUTE -> "hip_thrust_con_barra"
                    P.PUSH -> if (new) "press_de_banca_con_mancuernas" else "press_de_banca_con_barra"; P.INCLINE -> "press_inclinado_con_mancuernas"
                    P.VPULL -> if (new) "jalon_al_pecho_agarre_ancho" else "dominadas"; P.ROW -> if (new) "remo_con_mancuerna_a_una_mano" else "remo_con_barra"
                    P.OHP -> if (adv) "press_militar_con_barra" else "press_sentado_con_mancuernas"; P.LATERAL -> "elevaciones_laterales_con_mancuernas"
                    P.REAR -> "pajaros_con_mancuernas"; P.BICEPS -> if (new) "curl_de_biceps_con_mancuernas" else "curl_de_biceps_con_barra"
                    P.TRICEPS -> if (new) "extension_de_triceps_con_cuerda" else "press_frances_con_barra_z"; P.CALVES -> "elevacion_de_talones_con_mancuerna"; P.ABS -> "crunch_en_polea"
                }
            }
        }
    }

    // Plantillas: (patrón, series). El orden es la prioridad: si no cabe en tu tiempo, se quita desde el final.
    private val FB_A = listOf(P.SQUAT to 3, P.PUSH to 3, P.VPULL to 3, P.HAM to 2, P.LATERAL to 2, P.BICEPS to 2, P.ABS to 2)
    private val FB_B = listOf(P.HINGE to 3, P.INCLINE to 3, P.ROW to 3, P.QUAD2 to 2, P.OHP to 2, P.TRICEPS to 2, P.CALVES to 2)
    private val FB_C = listOf(P.GLUTE to 3, P.OHP to 3, P.ROW to 3, P.QUAD2 to 2, P.REAR to 2, P.BICEPS to 2, P.TRICEPS to 2)
    private val UP_A = listOf(P.PUSH to 3, P.VPULL to 3, P.INCLINE to 2, P.ROW to 2, P.LATERAL to 3, P.BICEPS to 2, P.TRICEPS to 2)
    private val LO_A = listOf(P.SQUAT to 3, P.HAM to 3, P.QUAD2 to 2, P.GLUTE to 2, P.CALVES to 3, P.ABS to 2)
    private val UP_B = listOf(P.INCLINE to 3, P.ROW to 3, P.OHP to 3, P.VPULL to 2, P.REAR to 2, P.TRICEPS to 2, P.BICEPS to 2)
    private val LO_B = listOf(P.HINGE to 3, P.QUAD2 to 3, P.HAM to 2, P.GLUTE to 2, P.CALVES to 3, P.ABS to 2)
    private val PUSH = listOf(P.PUSH to 3, P.INCLINE to 3, P.OHP to 3, P.LATERAL to 3, P.TRICEPS to 3)
    private val PULL = listOf(P.VPULL to 3, P.ROW to 3, P.REAR to 3, P.BICEPS to 3, P.ABS to 2)
    private val LEGS = listOf(P.SQUAT to 3, P.HINGE to 3, P.QUAD2 to 2, P.HAM to 2, P.CALVES to 3)

    fun build(i: Input, split: Split = recommendedSplit(i.days, i.level)): Plan {
        val templates: List<Pair<String, List<Pair<P, Int>>>> = when (split) {
            Split.FULL_BODY -> if (i.days >= 3 && i.level != Level.NEW) listOf("Full body A" to FB_A, "Full body B" to FB_B, "Full body C" to FB_C)
                else listOf("Full body A" to FB_A, "Full body B" to FB_B)
            Split.UPPER_LOWER -> listOf("Torso A" to UP_A, "Pierna A" to LO_A, "Torso B" to UP_B, "Pierna B" to LO_B)
            Split.PPL -> listOf("Empuje" to PUSH, "Jalón" to PULL, "Pierna" to LEGS)
        }
        val sessions = templates.map { (name, tpl) -> Session(name, fit(tpl, i)) }
        return Plan(split, sessions, schedule(split, i.days, sessions.map { it.name }))
    }

    private fun fit(tpl: List<Pair<P, Int>>, i: Input): List<Item> {
        val (cMin, cMax, cRest) = when (i.goal) { Goal.STRENGTH -> Triple(5, 8, 180); Goal.MUSCLE -> Triple(8, 12, 120); Goal.HEALTH -> Triple(8, 12, 90) }
        val (sMin, sMax, sRest) = when (i.goal) { Goal.STRENGTH -> Triple(8, 12, 90); Goal.MUSCLE -> Triple(10, 15, 90); Goal.HEALTH -> Triple(12, 15, 60) }
        var items = tpl.mapNotNull { (p, sets) ->
            val id = pick(p, i) ?: return@mapNotNull null
            // Principiantes: 2–3 series bastan al inicio. Avanzados: más volumen (progresan con más series).
            val s = when (i.level) {
                Level.NEW -> sets.coerceAtMost(3)
                Level.INTER -> if (p.compound && i.minutes >= 75) sets + 1 else sets
                Level.ADVANCED -> if (p.compound) sets + 1 else (sets + 1).coerceAtMost(3)
            }
            if (p.compound) Item(id, s, cMin, cMax, cRest, true) else Item(id, s, sMin, sMax, sRest, false)
        }.distinctBy { it.exerciseId }
            // Grandes primero (más pesados y técnicos, con energía); el orden estable respeta la prioridad de la plantilla.
            .sortedByDescending { it.compound }
        // Sin equipo pueden faltar opciones: completa con ejercicios de peso corporal para tener al menos 3.
        if (items.size < 3) items = items + listOf("elevacion_de_piernas_acostado" to false, "abdominales_crunch" to false, "puente_de_gluteo" to true, "lagartijas" to true)
            .filter { (id, _) -> items.none { it.exerciseId == id } }.take(3 - items.size)
            .map { (id, comp) -> if (comp) Item(id, 3, cMin, cMax, cRest, true) else Item(id, 2, sMin, sMax, sRest, false) }
        // Ajusta al tiempo: primero baja accesorios a 2 series, luego quita desde el final (menor prioridad).
        fun mins(l: List<Item>) = Session("", l).minutes
        if (mins(items) > i.minutes) items = items.map { if (!it.compound && it.sets > 2) it.copy(sets = 2) else it }
        while (mins(items) > i.minutes && items.size > 3) items = items.dropLast(1)
        return items
    }

    private fun schedule(split: Split, days: Int, names: List<String>): List<String> {
        val week = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
        val slots: List<Int> = when (days) { 2 -> listOf(0, 3); 3 -> listOf(0, 2, 4); 4 -> listOf(0, 1, 3, 4); 5 -> listOf(0, 1, 2, 3, 4); else -> listOf(0, 1, 2, 3, 4, 5) }
        return slots.mapIndexed { k, d -> "${week[d]}: ${names[k % names.size]}" } +
            if (split == Split.FULL_BODY && days % names.size != 0) listOf("La semana siguiente sigue donde te quedaste (alternando).") else emptyList()
    }

    /** Series por semana de cada músculo (método fraccional: principal 1, secundario 0.5). */
    fun weeklySets(plan: Plan, days: Int, muscles: (String) -> Pair<String, List<String>>?): Map<String, Double> {
        val perWeek = HashMap<String, Double>()
        val times = DoubleArray(plan.sessions.size)
        repeat(days) { k -> times[k % plan.sessions.size] += 1.0 }
        plan.sessions.forEachIndexed { idx, s ->
            for (it in s.items) {
                val (p, sec) = muscles(it.exerciseId) ?: continue
                perWeek[p] = (perWeek[p] ?: 0.0) + it.sets * times[idx]
                sec.forEach { m -> perWeek[m] = (perWeek[m] ?: 0.0) + 0.5 * it.sets * times[idx] }
            }
        }
        return perWeek
    }
}
