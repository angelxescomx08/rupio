package com.rupio.app.ui.learn


import com.rupio.app.core.tr
import com.rupio.app.core.PerLanguage

/*
 * Contenido de "Aprende": explicado para alguien que nunca ha pisado un gimnasio.
 * Cada concepto dice qué es, un ejemplo, cómo lo usa Rupio y el estudio que lo respalda (con enlace).
 */

data class Paper(val title: String, val url: String)

object Papers {
    // v2.0: getters (no vals) para que el título siga el idioma de la app.
    val ACSM get() = Paper(tr("ACSM 2009 · Modelos de progresión en el entrenamiento de fuerza", "ACSM 2009 · Progression models in resistance training"), "https://pubmed.ncbi.nlm.nih.gov/19204579/")
    val PLOTKIN get() = Paper(tr("Plotkin y col. 2022 · Progresar subiendo repeticiones o subiendo peso", "Plotkin et al. 2022 · Progressing by adding reps or adding weight"), "https://peerj.com/articles/14142")
    val ROBINSON get() = Paper(tr("Robinson y col. 2024 · Qué tan cerca del fallo: fuerza y músculo", "Robinson et al. 2024 · How close to failure: strength and muscle"), "https://doi.org/10.1007/s40279-024-02069-2")
    val ZOURDOS get() = Paper(tr("Zourdos y col. 2016 · Escala RPE basada en repeticiones en reserva", "Zourdos et al. 2016 · RPE scale based on reps in reserve"), "https://doi.org/10.1519/JSC.0000000000001049")
    val SINGER get() = Paper(tr("Singer y col. 2024 · Descanso entre series y crecimiento muscular", "Singer et al. 2024 · Rest between sets and muscle growth"), "https://doi.org/10.3389/fspor.2024.1429789")
    val SCHOENFELD get() = Paper(tr("Schoenfeld y col. 2017 · Series por semana y masa muscular", "Schoenfeld et al. 2017 · Weekly sets and muscle mass"), "https://doi.org/10.1080/02640414.2016.1210197")
    val PELLAND get() = Paper(tr("Pelland y col. 2026 · Volumen y frecuencia: cuánto es suficiente", "Pelland et al. 2026 · Volume and frequency: how much is enough"), "https://sportrxiv.org/index.php/server/preprint/view/460")
    val ZHANG get() = Paper(tr("Zhang y col. 2025 · Superseries vs. series tradicionales", "Zhang et al. 2025 · Supersets vs. traditional sets"), "https://doi.org/10.1007/s40279-025-02176-8")
    val LESUER get() = Paper(tr("LeSuer y col. 1997 · Qué tan precisas son las fórmulas de 1RM", "LeSuer et al. 1997 · How accurate 1RM formulas are"), "https://doi.org/10.1519/00124278-199711000-00001")
    val MOMMA get() = Paper(tr("Momma y col. 2022 · Ejercicios de fuerza y menor riesgo de enfermedades", "Momma et al. 2022 · Strength training and lower disease risk"), "https://pubmed.ncbi.nlm.nih.gov/35228201/")
    val WHO get() = Paper(tr("OMS 2020 · Directrices sobre actividad física", "WHO 2020 · Guidelines on physical activity"), "https://www.ncbi.nlm.nih.gov/books/NBK566048/")
    val GORDON_DEP get() = Paper(tr("Gordon y col. 2018 · Entrenamiento de fuerza y depresión", "Gordon et al. 2018 · Resistance training and depression"), "https://doi.org/10.1001/jamapsychiatry.2018.0572")
    val GORDON_ANX get() = Paper(tr("Gordon y col. 2017 · Entrenamiento de fuerza y ansiedad", "Gordon et al. 2017 · Resistance training and anxiety"), "https://pubmed.ncbi.nlm.nih.gov/28819746/")
    val WATSON get() = Paper(tr("Watson y col. 2018 · Fuerza y densidad de los huesos (LIFTMOR)", "Watson et al. 2018 · Strength and bone density (LIFTMOR)"), "https://pubmed.ncbi.nlm.nih.gov/28975661/")
    val WESTCOTT get() = Paper(tr("Westcott 2012 · El entrenamiento de fuerza es medicina", "Westcott 2012 · Resistance training is medicine"), "https://doi.org/10.1249/JSR.0b013e31825dabb8")
    val DONNELLY get() = Paper(tr("ACSM 2009 (Donnelly y col.) · Actividad física para bajar de peso", "ACSM 2009 (Donnelly et al.) · Physical activity for weight loss"), "https://pubmed.ncbi.nlm.nih.gov/19127177/")
    val HELMS_DIET get() = Paper(tr("Helms y col. 2014 · Nutrición para definir sin perder músculo", "Helms et al. 2014 · Nutrition for cutting without losing muscle"), "https://doi.org/10.1186/1550-2783-11-20")
    val IRAKI get() = Paper(tr("Iraki y col. 2019 · Nutrición para ganar músculo (superávit)", "Iraki et al. 2019 · Nutrition for building muscle (surplus)"), "https://doi.org/10.3390/sports7070154")
    val MORTON get() = Paper(tr("Morton y col. 2018 · Proteína y entrenamiento de fuerza", "Morton et al. 2018 · Protein and resistance training"), "https://pubmed.ncbi.nlm.nih.gov/28698222/")
    val COLEMAN get() = Paper(tr("Coleman y col. 2022 · Drop sets vs. series tradicionales", "Coleman et al. 2022 · Drop sets vs. traditional sets"), "https://vuir.vu.edu.au/46296/")
    val LOPEZ get() = Paper(tr("Lopez y col. 2021 · Peso ligero, medio o pesado: músculo y fuerza", "Lopez et al. 2021 · Light, moderate or heavy loads: muscle and strength"), "https://doi.org/10.1249/MSS.0000000000002585")
    val SCHOENFELD_LOAD get() = Paper(tr("Schoenfeld y col. 2017 · Cargas bajas vs. altas: fuerza y músculo", "Schoenfeld et al. 2017 · Low vs. high loads: strength and muscle"), "https://pubmed.ncbi.nlm.nih.gov/28834797/")
    val RHEA get() = Paper(tr("Rhea y col. 2003 · Dosis-respuesta del entrenamiento de fuerza", "Rhea et al. 2003 · Dose-response of strength training"), "https://pubmed.ncbi.nlm.nih.gov/12618576/")
    val IVERSEN get() = Paper(tr("Iversen y col. 2021 · ¿Sin tiempo? Rutinas cortas que funcionan", "Iversen et al. 2021 · No time? Short routines that work"), "https://doi.org/10.1007/s40279-021-01490-1")
    val HAUGEN get() = Paper(tr("Haugen y col. 2023 · Máquinas vs. pesos libres: fuerza y músculo", "Haugen et al. 2023 · Machines vs. free weights: strength and muscle"), "https://doi.org/10.1186/s13102-023-00713-4")
    val GRGIC_FREQ get() = Paper(tr("Grgic y col. 2018 · Frecuencia semanal y fuerza", "Grgic et al. 2018 · Weekly frequency and strength"), "https://doi.org/10.1007/s40279-018-0872-x")
    val RALSTON_FREQ get() = Paper(tr("Ralston y col. 2018 · 1 vs. 3 días por semana con el mismo volumen", "Ralston et al. 2018 · 1 vs. 3 days per week with the same volume"), "https://doi.org/10.1186/s40798-018-0149-9")
    val SODAL get() = Paper(tr("Sødal y col. 2023 · Drop sets y crecimiento muscular", "Sødal et al. 2023 · Drop sets and muscle growth"), "https://doi.org/10.1186/s40798-023-00620-5")
}

data class Concept(
    val id: String,
    val title: String,
    val oneLiner: String,
    val simple: String,
    val example: String,
    val inRupio: String,
    val papers: List<Paper> = emptyList(),
)

object Concepts {
    /** v2.0: se arma una vez por idioma (ver PerLanguage). */
    private val cached = PerLanguage { listOf(
        Concept("rutina", tr("Rutina y entrenamiento", "Routine and workout"),
            tr("La rutina es tu plan; el entrenamiento es lo que haces hoy.", "The routine is your plan; the workout is what you do today."),
            tr("Una rutina es la lista de ejercicios que piensas hacer, con cuántas series, cuánto peso y cuánto descanso. ", "A routine is the list of exercises you plan to do, with how many sets, how much weight and how much rest. ") +
                tr("Un entrenamiento es cada vez que vas al gimnasio y sigues esa rutina (o haces algo libre).", "A workout is each time you go to the gym and follow that routine (or do something freestyle)."),
            tr("Rutina \"Lunes y jueves\": sentadilla, extensión de pierna, press de pecho… Cada lunes que la haces es un entrenamiento.", "Routine “Monday and Thursday”: squat, leg extension, chest press… Every Monday you do it is a workout."),
            tr("En Entrenar ves tus rutinas. Al tocar \"Empezar rutina\" se crea el entrenamiento con todo listo para marcar.", "You'll see your routines in Workout. Tapping “Start routine” creates the workout with everything ready to check off.")),
        Concept("serie_rep", tr("Serie y repetición", "Set and rep"),
            tr("Repetición = un movimiento completo. Serie = varias repeticiones seguidas.", "Rep = one complete movement. Set = several reps in a row."),
            tr("Una repetición (rep) es hacer el movimiento una vez completo: bajar y subir. ", "A repetition (rep) is doing the movement once, all the way: down and up. ") +
                tr("Una serie es un grupo de repeticiones seguidas, sin parar. Entre serie y serie descansas.", "A set is a group of reps in a row, without stopping. You rest between sets."),
            tr("\"3 × 10\" significa 3 series de 10 repeticiones: haces 10, descansas, 10, descansas, 10.", "“3 × 10” means 3 sets of 10 reps: you do 10, rest, 10, rest, 10."),
            tr("Cada fila de la tabla es una serie. Escribes el peso y las reps y tocas ✓ al terminarla.", "Each row of the table is a set. You enter the weight and reps and tap ✓ when you finish it.")),
        Concept("peso", tr("Peso (carga): libras y kilos", "Weight (load): pounds and kilos"),
            tr("Cuánto peso mueves. 1 kg = 2.2 lb.", "How much weight you move. 1 kg = 2.2 lb."),
            tr("La carga es el peso que levantas: la barra, las mancuernas o la placa de la máquina. ", "The load is the weight you lift: the barbell, the dumbbells or the machine's weight stack. ") +
                tr("En México muchas máquinas marcan libras (lb) y los discos a veces kilos (kg).", "Many machines show pounds (lb) and plates are sometimes in kilos (kg)."),
            tr("50 lb son 22.7 kg. 100 kg son 220 lb.", "50 lb is 22.7 kg. 100 kg is 220 lb."),
            tr("Eliges tu unidad en Perfil (por defecto libras) y siempre ves la otra unidad en pequeño debajo.", "You pick your unit in Profile (pounds by default) and always see the other unit in small print below.")),
        Concept("rango_reps", tr("Rango de repeticiones", "Rep range"),
            tr("Un mínimo y un máximo de reps, por ejemplo de 8 a 12.", "A minimum and maximum number of reps, for example 8 to 12."),
            tr("En lugar de una cifra exacta, trabajas dentro de un rango. Con un peso que te permite de 8 a 12 reps ", "Instead of an exact number, you work within a range. With a weight that lets you do 8 to 12 ") +
                tr("bien hechas, vas subiendo repeticiones sesión a sesión hasta llegar al máximo; entonces subes el peso.", "good reps, you add reps session by session until you reach the top; then you add weight."),
            tr("Rango 8–12: hoy hiciste 9, 9 y 8. La próxima intentas 10, 10, 9… hasta 12, 12, 12.", "Range 8–12: today you did 9, 9 and 8. Next time you try 10, 10, 9… up to 12, 12, 12."),
            tr("Cada ejercicio tiene su rango (lo cambias con el chip \"8–12 reps\"). Rupio lo usa para decirte cuándo subir.", "Each exercise has its range (change it with the “8–12 reps” chip). Rupio uses it to tell you when to go up."),
            listOf(Papers.ACSM, Papers.SCHOENFELD_LOAD)),
        Concept("repeticiones_ciencia", tr("¿Pocas o muchas repeticiones?", "Few or many reps?"),
            tr("Para el músculo funcionan casi todas; para la fuerza máxima, mejor pesado.", "For muscle almost any range works; for max strength, heavier is better."),
            tr("Los estudios encontraron que el músculo crece parecido con pesos pesados (pocas reps) o ligeros (muchas reps) ", "Studies found that muscle grows similarly with heavy weights (few reps) or light ones (many reps) ") +
                tr("siempre que llegues cerca del fallo. La fuerza máxima sí mejora más con pesos pesados. ", "as long as you get close to failure. Max strength does improve more with heavy weights. ") +
                tr("Por practicidad, 6–15 reps es un buen rango: con muy pocas el riesgo y la técnica pesan más, y con muchas la serie se vuelve muy larga e incómoda.", "For practicality, 6–15 reps is a good range: with very few, risk and technique matter more, and with many the set gets very long and uncomfortable."),
            tr("Press con 8–12 reps o con 15–20 reps: crecimiento parecido si terminas cerca del fallo. Para levantar más en 1 rep, conviene 3–8.", "Press with 8–12 reps or 15–20 reps: similar growth if you finish close to failure. To lift more for 1 rep, 3–8 is better."),
            tr("La guía para armar tu rutina elige el rango según tu objetivo (fuerza, músculo o salud).", "The routine guide picks the range based on your goal (strength, muscle or health)."),
            listOf(Papers.SCHOENFELD_LOAD, Papers.LOPEZ)),
        Concept("descanso", tr("Descanso entre series", "Rest between sets"),
            tr("El tiempo que esperas antes de la siguiente serie.", "The time you wait before the next set."),
            tr("Descansar deja que tus músculos recuperen fuerza para la siguiente serie. Si descansas muy poco, haces menos reps. ", "Resting lets your muscles recover strength for the next set. If you rest too little, you do fewer reps. ") +
                tr("Descansar más de un minuto ayuda un poco al crecimiento; pasar de minuto y medio no mostró diferencias claras.", "Resting more than a minute helps growth a little; going past a minute and a half showed no clear difference."),
            tr("Ejercicios grandes y pesados (sentadilla, press): 2–3 min. Ejercicios pequeños (curl, elevaciones): 1–1:30 min.", "Big, heavy lifts (squat, press): 2–3 min. Small exercises (curls, raises): 1–1:30 min."),
            tr("Al marcar ✓ arranca el temporizador. Al terminar suena una sola vez y te llega una notificación.", "Tapping ✓ starts the timer. When it ends it plays once and you get a notification."),
            listOf(Papers.SINGER, Papers.ACSM)),
        Concept("sobrecarga", tr("Sobrecarga progresiva", "Progressive overload"),
            tr("Para seguir mejorando, poco a poco tienes que hacer un poco más.", "To keep improving, little by little you have to do a bit more."),
            tr("El cuerpo se adapta a lo que le pides. Si siempre haces lo mismo, deja de cambiar. ", "Your body adapts to what you ask of it. If you always do the same, it stops changing. ") +
                tr("Por eso, con el tiempo, subes repeticiones o peso. Las dos formas funcionan.", "That's why, over time, you add reps or weight. Both ways work."),
            tr("Semana 1: 100 lb × 10. Semana 3: 100 lb × 12. Semana 4: 105 lb × 8.", "Week 1: 100 lb × 10. Week 3: 100 lb × 12. Week 4: 105 lb × 8."),
            tr("El aviso de cada ejercicio (\"Sube a…\", \"Suma 1 repetición\") es la sobrecarga progresiva hecha por ti.", "Each exercise's tip (“Go up to…”, “Add 1 rep”) is progressive overload worked out for you."),
            listOf(Papers.ACSM, Papers.PLOTKIN)),
        Concept("subir_peso", tr("Cuándo subir el peso", "When to add weight"),
            tr("Cuando completas todas tus series en el máximo de tu rango.", "When you complete all your sets at the top of your range."),
            tr("Se llama doble progresión: primero subes repeticiones con el mismo peso; cuando llegas al máximo del rango en todas las series, ", "It's called double progression: first you add reps with the same weight; when you reach the top of the range on every set, ") +
                tr("subes el peso un poquito (2.5–5 %, o el disco más pequeño) y vuelves al mínimo del rango. ", "you add a little weight (2.5–5%, or the smallest plate) and go back to the bottom of the range. ") +
                tr("La guía del Colegio Americano de Medicina del Deporte recomienda subir 2–10 % cuando puedes hacer 1–2 reps más que tu objetivo.", "The American College of Sports Medicine guideline recommends adding 2–10% when you can do 1–2 reps more than your target."),
            tr("Rango 8–12 con 100 lb: hiciste 12, 12 y 12 → la próxima usa 105 lb y busca 8.", "Range 8–12 with 100 lb: you did 12, 12 and 12 → next time use 105 lb and aim for 8."),
            tr("Rupio revisa tu sesión anterior y te dice \"Sube a…\", \"Mantén y suma 1 rep\" o, si llevas dos sesiones sin llegar al mínimo, \"Baja ~10 %\".", "Rupio checks your previous session and tells you “Go up to…”, “Stay and add 1 rep” or, if you've missed the minimum for two sessions, “Drop ~10%”."),
            listOf(Papers.ACSM, Papers.PLOTKIN)),
        Concept("rir_rpe", tr("RPE y RIR: qué tan difícil fue", "RPE and RIR: how hard it was"),
            tr("RIR = repeticiones que te quedaban. RPE 10 = no podías ni una más.", "RIR = reps you had left. RPE 10 = you couldn't do one more."),
            tr("Al terminar una serie, pregúntate: \"¿cuántas más podía hacer con buena técnica?\". Eso son tus repeticiones en reserva (RIR). ", "When you finish a set, ask yourself: “how many more could I do with good form?”. Those are your reps in reserve (RIR). ") +
                tr("El RPE es lo mismo en una escala de 1 a 10: RPE 10 = 0 en reserva, RPE 9 = te quedaba 1, RPE 8 = te quedaban 2.", "RPE is the same thing on a 1 to 10 scale: RPE 10 = 0 in reserve, RPE 9 = 1 left, RPE 8 = 2 left."),
            tr("Hiciste 10 reps y sentías que podías 2 más → RPE 8.", "You did 10 reps and felt you could do 2 more → RPE 8."),
            tr("Es opcional: toca el número de la serie para anotarlo. Si anotas RPE 9.5–10 sin llegar a tu rango, Rupio no te sube el peso.", "It's optional: tap the set number to log it. If you log RPE 9.5–10 without reaching your range, Rupio won't raise the weight."),
            listOf(Papers.ZOURDOS)),
        Concept("fallo", tr("Fallo muscular", "Muscle failure"),
            tr("Cuando ya no puedes hacer otra repetición bien hecha.", "When you can't do another good rep."),
            tr("Llegar cerca del fallo hace que el músculo crezca más; para la fuerza importa más el peso que llegar al fallo. ", "Getting close to failure makes muscle grow more; for strength, the weight matters more than reaching failure. ") +
                tr("No hace falta llegar al fallo en cada serie: quedarte a 1–3 reps es una buena regla.", "You don't need to reach failure on every set: stopping 1–3 reps short is a good rule."),
            tr("Si en la serie 3 de press sientes que la siguiente rep no saldría, llegaste al fallo.", "If on set 3 of the press you feel the next rep wouldn't go up, you reached failure."),
            tr("Puedes marcar una serie como \"Al fallo\" (F) tocando su número.", "You can mark a set as “Failure” (F) by tapping its number."),
            listOf(Papers.ROBINSON)),
        Concept("calentamiento", tr("Serie de calentamiento", "Warm-up set"),
            tr("Una serie ligera antes de las pesadas, para preparar el cuerpo.", "A light set before the heavy ones, to get your body ready."),
            tr("Con más o menos la mitad del peso, preparas músculos y articulaciones y practicas el movimiento.", "With about half the weight, you prepare muscles and joints and practice the movement."),
            tr("Si tu serie es de 165 lb, calienta con 80 lb × 12.", "If your set is 165 lb, warm up with 80 lb × 12."),
            tr("Toca \"Agregar calentamiento\" debajo de las series del ejercicio: va antes de la primera y no te quita una serie de trabajo (también puedes tocar el número de una serie y elegir \"Calentamiento\"). No cuenta para tu volumen ni tus récords, y después descansas poco.", "Tap “Add warm-up” below the exercise's sets: it goes before the first one and doesn't take away a working set (you can also tap a set's number and choose “Warm-up”). It doesn't count toward your volume or PRs, and you rest briefly after it."),
            emptyList()),
        Concept("drop_set", tr("Drop set (y otras técnicas avanzadas)", "Drop set (and other advanced techniques)"),
            tr("Terminas una serie, bajas el peso y sigues sin descansar.", "You finish a set, lower the weight and keep going without resting."),
            tr("Ahorra tiempo: en los estudios da un crecimiento parecido al de las series normales en mucho menos tiempo. ", "It saves time: in studies it gives growth similar to normal sets in much less time. ") +
                tr("El problema: el peso y las reps del drop dependen de qué tan cansado quedaste, así que cambian mucho de un día a otro ", "The catch: the drop's weight and reps depend on how tired you were, so they change a lot from one day to the next ") +
                tr("y es difícil saber si de verdad progresaste. Si los usas, hazlos siempre igual y solo en la última serie, ", "and it's hard to know whether you really progressed. If you use them, always do them the same way and only on the last set, ") +
                tr("y mide tu progreso con las series normales.", "and measure your progress with your normal sets."),
            tr("100 lb × 10, sin descanso 70 lb × 8.", "100 lb × 10, then with no rest 70 lb × 8."),
            tr("Marca la serie como \"Drop set\" (D): Rupio no arranca el descanso antes de ella y NO la usa para decidir cuándo subir el peso.", "Mark the set as “Drop set” (D): Rupio doesn't start a rest before it and does NOT use it to decide when to add weight."),
            listOf(Papers.COLEMAN, Papers.SODAL)),
        Concept("volumen", tr("Volumen", "Volume"),
            tr("Peso × repeticiones. Una medida de cuánto trabajo hiciste.", "Weight × reps. A measure of how much work you did."),
            tr("Sumas el peso por las repeticiones de cada serie. Sirve para ver si con el tiempo estás haciendo más trabajo.", "You add up weight times reps for each set. It shows whether you're doing more work over time."),
            tr("3 series de 100 lb × 10 = 3 000 lb de volumen.", "3 sets of 100 lb × 10 = 3,000 lb of volume."),
            tr("Lo ves en cada entrenamiento y en Progreso por semana, mes, 3 meses, año o histórico. El calentamiento no cuenta.", "You see it in each workout and in Progress by week, month, 3 months, year or all time. Warm-ups don't count."),
            listOf(Papers.SCHOENFELD)),
        Concept("series_semana", tr("Series por músculo a la semana", "Sets per muscle per week"),
            tr("Cuántas series fuertes le das a cada músculo en 7 días.", "How many hard sets you give each muscle in 7 days."),
            tr("Es la forma más usada de medir cuánto entrenas cada músculo. Hacer 10 o más series por semana se asocia con más crecimiento; ", "It's the most common way to measure how much you train each muscle. Doing 10 or more sets per week is linked to more growth; ") +
                tr("más series siguen sumando, pero cada vez menos.", "more sets keep adding, but less each time."),
            tr("Si haces 3 series de press de pecho lunes y jueves, más 2 de aperturas cada día, son 10 series de pecho a la semana.", "If you do 3 sets of chest press on Monday and Thursday, plus 2 of flyes each day, that's 10 chest sets per week."),
            tr("En Progreso, la gráfica \"Series por músculo a la semana\" tiene una rayita en 10.", "In Progress, the “Sets per muscle per week” chart has a line at 10."),
            listOf(Papers.SCHOENFELD, Papers.PELLAND)),
        Concept("rendimientos", tr("Más volumen ayuda… pero cada vez menos", "More volume helps… but less each time"),
            tr("Hacer más series da más resultados, hasta un punto. No hace falta vivir en el gym.", "More sets give more results, up to a point. You don't need to live at the gym."),
            tr("Los estudios muestran que más series por semana dan más músculo, pero cada serie extra suma menos que la anterior ", "Studies show that more sets per week give more muscle, but each extra set adds less than the one before ") +
                tr("(rendimientos decrecientes). Pasar 5 horas diarias, 7 días, no multiplica tus resultados: aumenta el cansancio y el riesgo de dejarlo. ", "(diminishing returns). Spending 5 hours a day, 7 days a week, doesn't multiply your results: it adds fatigue and the risk of quitting. ") +
                tr("Con 2–4 sesiones de alrededor de una hora y unas 10 series por músculo a la semana la mayoría logra muy buenos resultados.", "With 2–4 sessions of about an hour and around 10 sets per muscle per week, most people get very good results."),
            tr("Pasar de 4 a 10 series por semana de pecho suma mucho; pasar de 20 a 26 suma poquito.", "Going from 4 to 10 chest sets per week adds a lot; going from 20 to 26 adds very little."),
            tr("La gráfica \"Series por músculo a la semana\" te ayuda a no quedarte corto ni pasarte.", "The “Sets per muscle per week” chart helps you avoid doing too little or too much."),
            listOf(Papers.SCHOENFELD, Papers.PELLAND, Papers.RHEA)),
        Concept("poco_tiempo", tr("Buenos resultados con poco tiempo", "Good results with little time"),
            tr("2–3 días por semana y sesiones de una hora (o menos) funcionan muy bien.", "2–3 days a week and sessions of an hour (or less) work very well."),
            tr("Lo que más importa es el total de series por semana, no cuántos días vas: con el mismo volumen, 1, 2 o 3 días por semana dieron resultados parecidos. ", "What matters most is total sets per week, not how many days you go: with the same volume, 1, 2 or 3 days a week gave similar results. ") +
                tr("Una revisión sobre rutinas cortas propone priorizar ejercicios grandes (piernas, empuje y jalón), hacer al menos 4 series por músculo a la semana ", "A review on short routines suggests prioritizing big lifts (legs, push and pull), doing at least 4 sets per muscle per week ") +
                tr("y usar superseries para ahorrar tiempo. Para la salud, el mayor beneficio apareció con 30–60 minutos de fuerza a la semana.", "and using supersets to save time. For health, the biggest benefit showed up with 30–60 minutes of strength training per week."),
            tr("Lunes y jueves, 1 hora cada día, full body: es una rutina completa.", "Monday and Thursday, 1 hour each day, full body: that's a complete routine."),
            tr("La guía para armar tu rutina ajusta los ejercicios a tus días y a tu tiempo.", "The routine guide fits the exercises to your days and your time."),
            listOf(Papers.IVERSEN, Papers.RALSTON_FREQ, Papers.GRGIC_FREQ, Papers.MOMMA)),
        Concept("principiante", tr("Tus primeros años valen oro", "Your first years are gold"),
            tr("Al empezar se progresa rápido; con los años, más lento.", "When you start you progress fast; over the years, slower."),
            tr("Las personas sin entrenamiento ganan más músculo y fuerza que las entrenadas con el mismo programa. ", "Untrained people gain more muscle and strength than trained people on the same program. ") +
                tr("Por eso los primeros meses y años son tu mejor momento: aprovéchalos aprendiendo buena técnica, siendo constante y usando ejercicios estables ", "That's why your first months and years are your best moment: make the most of them by learning good form, being consistent and using stable exercises ") +
                tr("que puedas mantener. Esas bases te sirven toda la vida.", "you can stick with. Those foundations serve you for life."),
            tr("Es común subir el peso casi cada semana al inicio; después de un par de años, subir 2.5 kg puede tardar meses.", "It's common to add weight almost every week at first; after a couple of years, adding 2.5 kg can take months."),
            tr("\"Tu progreso\" te muestra cuánto has cambiado desde el principio.", "“Your progress” shows you how much you've changed since the beginning."),
            listOf(Papers.LOPEZ, Papers.RHEA)),
        Concept("maquinas", tr("Máquinas o pesas libres", "Machines or free weights"),
            tr("Las máquinas son más fáciles de aprender y de medir; las pesas libres también funcionan.", "Machines are easier to learn and to measure; free weights work too."),
            tr("Las máquinas tienen apoyo para la espalda o el pecho y el recorrido está guiado: es más fácil hacer siempre la misma técnica, ", "Machines support your back or chest and guide the movement: it's easier to keep the same form every time, ") +
                tr("concentrarte en el músculo y medir tu progreso. Las barras y mancuernas piden más equilibrio y práctica. ", "focus on the muscle and measure your progress. Barbells and dumbbells take more balance and practice. ") +
                tr("En los estudios, ambas dan resultados parecidos en fuerza y músculo; te vuelves más fuerte en lo que practicas.", "In studies, both give similar results for strength and muscle; you get stronger at what you practice."),
            tr("Press de pecho en máquina con respaldo: solo empujas. Press con barra: además equilibras la barra.", "Machine chest press with a backrest: you just push. Barbell press: you also balance the bar."),
            tr("La guía para armar tu rutina te recomienda máquinas, pero puedes elegir mezcla, pesas libres, mancuernas o sin equipo.", "The routine guide recommends machines, but you can choose mixed, free weights, dumbbells or no equipment."),
            listOf(Papers.HAUGEN)),
        Concept("musculos", tr("Músculo principal y secundario", "Main and secondary muscle"),
            tr("Cada ejercicio trabaja sobre todo un músculo, y un poco otros.", "Each exercise mainly works one muscle, and a bit of others."),
            tr("En el press de pecho trabaja sobre todo el pecho (principal), pero también hombros y tríceps (secundarios). ", "The chest press mainly works your chest (main), but also your shoulders and triceps (secondary). ") +
                tr("Por eso, al contar series, los músculos secundarios cuentan como media serie.", "That's why, when counting sets, secondary muscles count as half a set."),
            tr("1 serie de press de pecho = 1 serie de pecho + ½ de tríceps + ½ de hombros.", "1 set of chest press = 1 chest set + ½ triceps + ½ shoulders."),
            tr("En el detalle de cada ejercicio ves los músculos; Progreso usa esta forma de contar.", "Each exercise's details show the muscles; Progress counts this way."),
            listOf(Papers.PELLAND)),
        Concept("frecuencia", tr("Frecuencia", "Frequency"),
            tr("Cuántos días a la semana entrenas un músculo.", "How many days a week you train a muscle."),
            tr("Lo que más importa es el total de series de la semana. Con el mismo total, entrenar un músculo 1, 2 o 3 días dio resultados parecidos; ", "What matters most is the week's total sets. With the same total, training a muscle 1, 2 or 3 days gave similar results; ") +
                tr("ir más días sirve sobre todo para repartir el trabajo y que cada sesión sea más corta y de mejor calidad.", "going more days mainly helps spread the work so each session is shorter and of better quality."),
            tr("Pierna lunes y jueves = frecuencia 2.", "Legs on Monday and Thursday = frequency 2."),
            tr("El mapa de constancia en Progreso te muestra qué días entrenaste; la guía para armar tu rutina reparte los músculos según tus días.", "The consistency map in Progress shows which days you trained; the routine guide spreads the muscles across your days."),
            listOf(Papers.GRGIC_FREQ, Papers.RALSTON_FREQ, Papers.PELLAND)),
        Concept("superserie", tr("Superserie", "Superset"),
            tr("Dos o más ejercicios seguidos, sin descanso entre ellos.", "Two or more exercises back to back, with no rest between them."),
            tr("Haces una serie del ejercicio A1, inmediatamente una del A2, y luego descansas. Ahorra tiempo sin perder resultados. ", "You do a set of exercise A1, right away a set of A2, and then rest. It saves time without losing results. ") +
                tr("Funciona mejor con músculos opuestos (pecho y espalda); con el mismo músculo harás menos peso.", "It works best with opposing muscles (chest and back); with the same muscle you'll lift less."),
            tr("A1 press de pecho → A2 remo → descanso → repetir.", "A1 chest press → A2 row → rest → repeat."),
            tr("En el menú ⋮ de un ejercicio: \"Unir en superserie con el siguiente\". Se ven en un recuadro con letra (A1, A2) y líneas que los unen.", "In an exercise's ⋮ menu: “Superset with the next one”. They show in a box with a letter (A1, A2) and lines joining them."),
            listOf(Papers.ZHANG)),
        Concept("descanso_compartido", tr("Descanso compartido o individual", "Shared or individual rest"),
            tr("En superserie: un descanso por ronda, o uno por ejercicio.", "In a superset: one rest per round, or one per exercise."),
            tr("Compartido: haces todos los ejercicios de la superserie y descansas una vez al final de la ronda. ", "Shared: you do all the exercises in the superset and rest once at the end of the round. ") +
                tr("Individual: cada ejercicio usa su propio descanso después de su serie.", "Individual: each exercise uses its own rest after its set."),
            tr("Compartido 1:30 → A1, A2, descanso 1:30.", "Shared 1:30 → A1, A2, rest 1:30."),
            tr("Lo eliges al crear la superserie o tocando el reloj en su cabecera.", "You choose it when creating the superset or by tapping the clock in its header.")),
        Concept("1rm", tr("1RM estimado", "Estimated 1RM"),
            tr("Lo máximo que podrías levantar una sola vez, calculado sin probarlo.", "The most you could lift once, calculated without testing it."),
            tr("Probar tu máximo real es pesado y arriesgado. Se puede estimar con tus series normales usando fórmulas (Epley y Brzycki). ", "Testing your real max is hard and risky. It can be estimated from your normal sets using formulas (Epley and Brzycki). ") +
                tr("Son más confiables con menos de 10 repeticiones.", "They're more reliable under 10 reps."),
            tr("100 lb × 10 ≈ 133 lb de 1RM estimado.", "100 lb × 10 ≈ 133 lb estimated 1RM."),
            tr("La gráfica \"Fuerza estimada (1RM)\" muestra si te estás haciendo más fuerte aunque cambies peso y reps.", "The “Estimated strength (1RM)” chart shows whether you're getting stronger even if you change weight and reps."),
            listOf(Papers.LESUER)),
        Concept("record", tr("Récord personal (PR)", "Personal record (PR)"),
            tr("Tu mejor marca en un ejercicio.", "Your best mark on an exercise."),
            tr("Rupio guarda tus récords: más peso, mejor 1RM estimado, mejor serie (peso × reps), más reps y más tiempo.", "Rupio keeps your PRs: heaviest weight, best estimated 1RM, best set (weight × reps), most reps and longest time."),
            tr("Si tu máximo en press era 120 lb y hoy hiciste 125 lb, es un nuevo récord 🏆.", "If your press max was 120 lb and today you did 125 lb, that's a new PR 🏆."),
            tr("Al romper un récord te avisa en el momento y lo ves en el historial con 🏆.", "When you set a PR it tells you right away, and you'll see it in your history with 🏆.")),
        Concept("descarga", tr("Bajar el peso (descarga)", "Lowering the weight (deload)"),
            tr("A veces bajar un poco ayuda a volver a subir.", "Sometimes going down a little helps you go back up."),
            tr("Si llevas varias sesiones sin mejorar o sin llegar al mínimo de tu rango, bajar alrededor de 10 % y reconstruir con buena técnica ", "If you've gone several sessions without improving or without reaching the bottom of your range, dropping about 10% and rebuilding with good form ") +
                tr("suele destrabar el progreso. Variar la carga con el tiempo es parte de progresar.", "usually gets progress moving again. Varying the load over time is part of progressing."),
            tr("Llevabas 2 sesiones con 200 lb haciendo 6 y 5 reps (rango 8–12) → baja a 180 lb.", "2 sessions at 200 lb doing 6 and 5 reps (range 8–12) → drop to 180 lb."),
            tr("Rupio te lo sugiere con \"Baja a…\" cuando lo detecta.", "Rupio suggests it with “Drop to…” when it detects it."),
            listOf(Papers.ACSM)),
        Concept("tipos_ejercicio", tr("Peso corporal, lastre y asistido", "Bodyweight, added weight and assisted"),
            tr("No todos los ejercicios se registran igual.", "Not every exercise is logged the same way."),
            tr("Algunos solo cuentan repeticiones (lagartijas, abdominales). En otros puedes añadir peso extra, llamado lastre (dominadas con cinturón). ", "Some only count reps (push-ups, crunches). On others you can add extra weight (pull-ups with a dip belt). ") +
                tr("En los asistidos una máquina o banda te ayuda: menos ayuda = más difícil. Otros se miden en tiempo (plancha).", "On assisted ones a machine or band helps you: less help = harder. Others are measured in time (plank)."),
            tr("Dominada asistida con 40 lb de ayuda → la próxima, 30 lb de ayuda.", "Assisted pull-up with 40 lb of help → next time, 30 lb of help."),
            tr("Cada ejercicio muestra cómo se registra; la tabla cambia sus columnas (LB, +LB, AYUDA, REPS o SEGUNDOS).", "Each exercise shows how it's logged; the table changes its columns (LB, +LB, ASSIST, REPS or SECONDS).")),
        Concept("anterior", tr("\"Anterior\" y valores en gris", "“Previous” and gray values"),
            tr("Lo que hiciste la última vez y lo que te toca hoy.", "What you did last time and what's up today."),
            tr("La columna ANTERIOR muestra la misma serie de tu sesión pasada. Los números en gris son la sugerencia de hoy ", "The PREVIOUS column shows the same set from your last session. The gray numbers are today's suggestion ") +
                tr("(tu plan de la rutina, tu última vez o lo que pide la progresión).", "(your routine plan, your last time or what the progression calls for)."),
            tr("Si ves \"100 lb × 12\" en anterior y \"105\" en gris, toca ✓ sin escribir y se guarda 105.", "If you see “100 lb × 12” under previous and “105” in gray, tap ✓ without typing and 105 is saved."),
            tr("Si dejas una celda vacía y marcas ✓, se usa el valor gris.", "If you leave a cell empty and tap ✓, the gray value is used.")),
        Concept("tecnica", tr("Técnica siempre igual (estandarizar)", "Same form every time (standardize)"),
            tr("Para saber si progresas, cada vez haz el ejercicio de la misma forma.", "To know whether you're progressing, do the exercise the same way every time."),
            tr("Tus números solo se pueden comparar si el ejercicio es el mismo: la misma máquina (100 lb en una no es igual que en otra), ", "Your numbers can only be compared if the exercise is the same: the same machine (100 lb on one isn't the same as on another), ") +
                tr("el mismo asiento y agarre, el mismo recorrido completo, la misma velocidad y un descanso parecido. ", "the same seat and grip, the same full range of motion, the same speed and a similar rest. ") +
                tr("Si un día bajas menos o rebotas, haces más reps, pero no porque seas más fuerte.", "If one day you go less deep or bounce, you do more reps, but not because you're stronger."),
            tr("Si hoy hiciste 12 reps bajando a medias y la semana pasada 10 completas, no mejoraste: cambiaste el ejercicio.", "If today you did 12 half reps and last week 10 full ones, you didn't improve: you changed the exercise."),
            tr("Usa siempre el mismo ejercicio en tu rutina y anota en sus notas la altura del asiento o la máquina. ", "Always use the same exercise in your routine and write the seat height or the machine in its notes. ") +
                tr("Rupio compara contra tu sesión anterior: si la técnica cambia, sus recomendaciones pierden sentido.", "Rupio compares against your previous session: if your form changes, its recommendations lose their meaning."),
            listOf(Papers.ACSM)),
        Concept("calorias", tr("Calorías y mantenimiento", "Calories and maintenance"),
            tr("Las calorías son la energía de la comida. Mantenimiento = comer lo mismo que gastas.", "Calories are the energy in food. Maintenance = eating the same as you burn."),
            tr("Tu cuerpo gasta energía todo el día (respirar, moverte, entrenar). Si comes la misma energía que gastas, tu peso se queda igual: ", "Your body burns energy all day (breathing, moving, training). If you eat the same energy you burn, your weight stays the same: ") +
                tr("eso es comer en mantenimiento.", "that's eating at maintenance."),
            tr("Si gastas unas 2 300 kcal al día y comes unas 2 300, tu peso casi no cambia.", "If you burn about 2,300 kcal a day and eat about 2,300, your weight barely changes."),
            tr("Tu tendencia de peso te dice dónde estás: estable = mantenimiento.", "Your weight trend tells you where you are: stable = maintenance.")),
        Concept("deficit", tr("Déficit calórico", "Calorie deficit"),
            tr("Comer menos energía de la que gastas. Así se baja de peso.", "Eating less energy than you burn. That's how you lose weight."),
            tr("Si comes menos de lo que gastas, el cuerpo usa sus reservas (sobre todo grasa) y bajas de peso. ", "If you eat less than you burn, your body uses its reserves (mostly fat) and you lose weight. ") +
                tr("Bajar despacio, alrededor de 0.5–1 % de tu peso por semana, y seguir entrenando fuerza ayuda a conservar el músculo. ", "Losing slowly, about 0.5–1% of your weight per week, and keeping up strength training helps preserve muscle. ") +
                tr("Para verte \"marcado\" (definición), normalmente hace falta un déficit.", "To look “shredded” (a cut), you usually need a deficit."),
            tr("Si pesas 80 kg, bajar 0.4–0.8 kg por semana es un ritmo razonable para definir.", "If you weigh 80 kg, losing 0.4–0.8 kg per week is a reasonable pace for a cut."),
            tr("En Peso corporal ves tu ritmo semanal en %.", "In Body weight you see your weekly pace in %."),
            listOf(Papers.HELMS_DIET, Papers.DONNELLY)),
        Concept("superavit", tr("Superávit calórico", "Calorie surplus"),
            tr("Comer más energía de la que gastas. Así se sube de peso.", "Eating more energy than you burn. That's how you gain weight."),
            tr("Con un superávit pequeño y entrenando fuerza, parte de lo que subes es músculo. Si el superávit es grande, la mayor parte es grasa. ", "With a small surplus and strength training, part of what you gain is muscle. If the surplus is large, most of it is fat. ") +
                tr("Para ganar músculo con poca grasa se sugiere un 10–20 % más que tu mantenimiento y subir 0.25–0.5 % de tu peso por semana.", "To build muscle with little fat, eating 10–20% above maintenance and gaining 0.25–0.5% of your weight per week is suggested."),
            tr("Si pesas 70 kg, subir 0.2–0.35 kg por semana.", "If you weigh 70 kg, gain 0.2–0.35 kg per week."),
            tr("Tu tendencia de peso te dice si de verdad estás en superávit.", "Your weight trend tells you whether you're really in a surplus."),
            listOf(Papers.IRAKI)),
        Concept("proteina", tr("Proteína", "Protein"),
            tr("El nutriente que ayuda a reparar y construir músculo.", "The nutrient that helps repair and build muscle."),
            tr("Comer suficiente proteína ayuda a ganar músculo con el entrenamiento. Para la mayoría, unos 1.6 g por kilo de peso al día es suficiente; ", "Eating enough protein helps you build muscle with training. For most people, about 1.6 g per kilo of body weight per day is enough; ") +
                tr("comer más no mostró beneficio extra para el músculo. Puede venir de comida normal o de suplemento: da igual la fuente.", "eating more showed no extra benefit for muscle. It can come from regular food or supplements: the source doesn't matter."),
            tr("Una persona de 70 kg: unos 110 g de proteína al día, repartidos en varias comidas.", "A 70 kg person: about 110 g of protein a day, spread over several meals."),
            tr("Rupio no lleva tu dieta; esto es para que entiendas por qué importa.", "Rupio doesn't track your diet; this is so you understand why it matters."),
            listOf(Papers.MORTON)),
        Concept("peso_corporal", tr("Peso corporal y tendencia", "Body weight and trend"),
            tr("Tu peso cambia de un día a otro; lo que importa es la tendencia.", "Your weight changes from day to day; what matters is the trend."),
            tr("El peso de un día puede subir o bajar 1–2 kg por agua, sal, comida o ir al baño. Por eso Rupio calcula una tendencia ", "One day's weight can go up or down 1–2 kg from water, salt, food or going to the bathroom. That's why Rupio calculates a trend ") +
                tr("(un promedio que se mueve poco a poco): un día raro casi no la mueve. Pésate siempre en las mismas condiciones: ", "(an average that moves little by little): an odd day barely moves it. Always weigh yourself under the same conditions: ") +
                tr("al despertar, después del baño y antes de comer.", "when you wake up, after the bathroom and before eating."),
            tr("Lunes 80.4, martes 81.3 (cenaste salado), miércoles 80.2 → la tendencia sigue en ~80.4.", "Monday 80.4, Tuesday 81.3 (salty dinner), Wednesday 80.2 → the trend stays at ~80.4."),
            tr("En Progreso → Peso corporal registras tu peso (una vez por día) y ves la tendencia y el ritmo por semana.", "In the Weight tab you log your weight (once a day) and see the trend and weekly pace."),
            listOf(Papers.HELMS_DIET)),
        Concept("constancia", tr("Constancia", "Consistency"),
            tr("Entrenar seguido importa más que un día perfecto.", "Training regularly matters more than one perfect day."),
            tr("Los resultados vienen de semanas y meses de práctica. Mejor un entrenamiento corto que ninguno.", "Results come from weeks and months of practice. A short workout beats none."),
            tr("2 días por semana durante un año > 6 días una semana y luego nada.", "2 days a week for a year > 6 days one week and then nothing."),
            tr("En Progreso ves tu mapa de constancia y tu racha de semanas seguidas.", "In Progress you see your consistency map and your streak of weeks in a row."),
            listOf(Papers.WHO)),
    ) }
    val all: List<Concept> get() = cached.get()

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Beneficios del ejercicio de fuerza, con su respaldo. */
data class Benefit(val icon: String, val title: String, val body: String, val papers: List<Paper>)

object Benefits {
    private val cached = PerLanguage { listOf(
        Benefit("❤️", tr("Vivir más y con menos enfermedades", "Live longer with less disease"),
            tr("Un análisis de 16 estudios encontró que hacer ejercicios de fuerza se asocia con 10–17 % menos riesgo de morir por cualquier causa, ", "An analysis of 16 studies found that strength training is linked to a 10–17% lower risk of death from any cause, ") +
                tr("enfermedad del corazón, cáncer y diabetes. El mayor beneficio apareció con unos 30–60 minutos a la semana, y combinarlo con ejercicio ", "heart disease, cancer and diabetes. The biggest benefit appeared with about 30–60 minutes a week, and combining it with aerobic ") +
                tr("aeróbico (caminar, correr, bici) dio aún más beneficio.", "exercise (walking, running, cycling) gave even more benefit."), listOf(Papers.MOMMA)),
        Benefit("🌎", tr("Lo recomienda la Organización Mundial de la Salud", "The World Health Organization recommends it"),
            tr("La OMS recomienda a los adultos hacer ejercicios de fuerza que trabajen todos los grupos musculares grandes 2 o más días a la semana, ", "The WHO recommends that adults do strength exercises working all major muscle groups on 2 or more days a week, ") +
                tr("además de actividad aeróbica, porque dan beneficios extra para la salud.", "in addition to aerobic activity, because they bring extra health benefits."), listOf(Papers.WHO)),
        Benefit("🙂", tr("Menos síntomas de depresión", "Fewer symptoms of depression"),
            tr("Un análisis de 33 ensayos con 1 877 personas encontró que el entrenamiento de fuerza redujo los síntomas de depresión de forma moderada, ", "An analysis of 33 trials with 1,877 people found that strength training moderately reduced symptoms of depression, ") +
                tr("sin importar la edad, el estado de salud o cuánto se fortalecieron. No sustituye a un tratamiento médico, pero puede sumarse.", "regardless of age, health status or how much stronger people got. It doesn't replace medical treatment, but it can be added to it."),
            listOf(Papers.GORDON_DEP)),
        Benefit("😌", tr("Menos ansiedad", "Less anxiety"),
            tr("En 16 estudios con 922 personas, el entrenamiento de fuerza redujo los síntomas de ansiedad, tanto en personas sanas como en personas con alguna enfermedad.", "In 16 studies with 922 people, strength training reduced anxiety symptoms, both in healthy people and in people with an illness."),
            listOf(Papers.GORDON_ANX)),
        Benefit("🦴", tr("Huesos más fuertes", "Stronger bones"),
            tr("En mujeres posmenopáusicas con huesos débiles, 8 meses de entrenamiento de fuerza supervisado, 2 veces por semana y 30 minutos, ", "In postmenopausal women with weak bones, 8 months of supervised strength training, twice a week for 30 minutes, ") +
                tr("mejoraron la densidad de los huesos de la columna y la cadera y su capacidad física, sin lesiones importantes.", "improved bone density in the spine and hip and their physical ability, with no major injuries."),
            listOf(Papers.WATSON)),
        Benefit("⚡", tr("Mejor metabolismo, azúcar y presión", "Better metabolism, blood sugar and blood pressure"),
            tr("Los adultos inactivos pierden 3–8 % de músculo por década. El entrenamiento de fuerza ayuda a recuperarlo, reduce la grasa visceral (la del abdomen), ", "Inactive adults lose 3–8% of their muscle per decade. Strength training helps get it back, reduces visceral fat (belly fat), ") +
                tr("mejora el manejo del azúcar en la sangre y puede bajar la presión arterial en reposo.", "improves blood sugar control and can lower resting blood pressure."), listOf(Papers.WESTCOTT)),
        Benefit("💪", tr("Más fuerza para la vida diaria, a cualquier edad", "More strength for daily life, at any age"),
            tr("Cargar el súper, subir escaleras, jugar con tus hijos o levantarte del piso. El músculo se puede ganar a cualquier edad y ayuda a mantener tu independencia.", "Carrying groceries, climbing stairs, playing with your kids or getting up off the floor. Muscle can be built at any age and helps you stay independent."),
            listOf(Papers.WESTCOTT, Papers.WHO)),
    ) }
    val all: List<Benefit> get() = cached.get()
}
