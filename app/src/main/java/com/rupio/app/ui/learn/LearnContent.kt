package com.rupio.app.ui.learn

/*
 * Contenido de "Aprende": explicado para alguien que nunca ha pisado un gimnasio.
 * Cada concepto dice qué es, un ejemplo, cómo lo usa Rupio y el estudio que lo respalda (con enlace).
 */

data class Paper(val title: String, val url: String)

object Papers {
    val ACSM = Paper("ACSM 2009 · Modelos de progresión en el entrenamiento de fuerza", "https://pubmed.ncbi.nlm.nih.gov/19204579/")
    val PLOTKIN = Paper("Plotkin y col. 2022 · Progresar subiendo repeticiones o subiendo peso", "https://peerj.com/articles/14142")
    val ROBINSON = Paper("Robinson y col. 2024 · Qué tan cerca del fallo: fuerza y músculo", "https://doi.org/10.1007/s40279-024-02069-2")
    val ZOURDOS = Paper("Zourdos y col. 2016 · Escala RPE basada en repeticiones en reserva", "https://doi.org/10.1519/JSC.0000000000001049")
    val SINGER = Paper("Singer y col. 2024 · Descanso entre series y crecimiento muscular", "https://doi.org/10.3389/fspor.2024.1429789")
    val SCHOENFELD = Paper("Schoenfeld y col. 2017 · Series por semana y masa muscular", "https://doi.org/10.1080/02640414.2016.1210197")
    val PELLAND = Paper("Pelland y col. 2026 · Volumen y frecuencia: cuánto es suficiente", "https://sportrxiv.org/index.php/server/preprint/view/460")
    val ZHANG = Paper("Zhang y col. 2025 · Superseries vs. series tradicionales", "https://doi.org/10.1007/s40279-025-02176-8")
    val LESUER = Paper("LeSuer y col. 1997 · Qué tan precisas son las fórmulas de 1RM", "https://doi.org/10.1519/00124278-199711000-00001")
    val MOMMA = Paper("Momma y col. 2022 · Ejercicios de fuerza y menor riesgo de enfermedades", "https://pubmed.ncbi.nlm.nih.gov/35228201/")
    val WHO = Paper("OMS 2020 · Directrices sobre actividad física", "https://www.ncbi.nlm.nih.gov/books/NBK566048/")
    val GORDON_DEP = Paper("Gordon y col. 2018 · Entrenamiento de fuerza y depresión", "https://doi.org/10.1001/jamapsychiatry.2018.0572")
    val GORDON_ANX = Paper("Gordon y col. 2017 · Entrenamiento de fuerza y ansiedad", "https://pubmed.ncbi.nlm.nih.gov/28819746/")
    val WATSON = Paper("Watson y col. 2018 · Fuerza y densidad de los huesos (LIFTMOR)", "https://pubmed.ncbi.nlm.nih.gov/28975661/")
    val WESTCOTT = Paper("Westcott 2012 · El entrenamiento de fuerza es medicina", "https://doi.org/10.1249/JSR.0b013e31825dabb8")
    val DONNELLY = Paper("ACSM 2009 (Donnelly y col.) · Actividad física para bajar de peso", "https://pubmed.ncbi.nlm.nih.gov/19127177/")
    val HELMS_DIET = Paper("Helms y col. 2014 · Nutrición para definir sin perder músculo", "https://doi.org/10.1186/1550-2783-11-20")
    val IRAKI = Paper("Iraki y col. 2019 · Nutrición para ganar músculo (superávit)", "https://doi.org/10.3390/sports7070154")
    val MORTON = Paper("Morton y col. 2018 · Proteína y entrenamiento de fuerza", "https://pubmed.ncbi.nlm.nih.gov/28698222/")
    val COLEMAN = Paper("Coleman y col. 2022 · Drop sets vs. series tradicionales", "https://vuir.vu.edu.au/46296/")
    val LOPEZ = Paper("Lopez y col. 2021 · Peso ligero, medio o pesado: músculo y fuerza", "https://doi.org/10.1249/MSS.0000000000002585")
    val SCHOENFELD_LOAD = Paper("Schoenfeld y col. 2017 · Cargas bajas vs. altas: fuerza y músculo", "https://pubmed.ncbi.nlm.nih.gov/28834797/")
    val RHEA = Paper("Rhea y col. 2003 · Dosis-respuesta del entrenamiento de fuerza", "https://pubmed.ncbi.nlm.nih.gov/12618576/")
    val IVERSEN = Paper("Iversen y col. 2021 · ¿Sin tiempo? Rutinas cortas que funcionan", "https://doi.org/10.1007/s40279-021-01490-1")
    val HAUGEN = Paper("Haugen y col. 2023 · Máquinas vs. pesos libres: fuerza y músculo", "https://doi.org/10.1186/s13102-023-00713-4")
    val GRGIC_FREQ = Paper("Grgic y col. 2018 · Frecuencia semanal y fuerza", "https://doi.org/10.1007/s40279-018-0872-x")
    val RALSTON_FREQ = Paper("Ralston y col. 2018 · 1 vs. 3 días por semana con el mismo volumen", "https://doi.org/10.1186/s40798-018-0149-9")
    val SODAL = Paper("Sødal y col. 2023 · Drop sets y crecimiento muscular", "https://doi.org/10.1186/s40798-023-00620-5")
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
    val all = listOf(
        Concept("rutina", "Rutina y entrenamiento",
            "La rutina es tu plan; el entrenamiento es lo que haces hoy.",
            "Una rutina es la lista de ejercicios que piensas hacer, con cuántas series, cuánto peso y cuánto descanso. " +
                "Un entrenamiento es cada vez que vas al gimnasio y sigues esa rutina (o haces algo libre).",
            "Rutina \"Lunes y jueves\": sentadilla, extensión de pierna, press de pecho… Cada lunes que la haces es un entrenamiento.",
            "En Entrenar ves tus rutinas. Al tocar \"Empezar rutina\" se crea el entrenamiento con todo listo para marcar."),
        Concept("serie_rep", "Serie y repetición",
            "Repetición = un movimiento completo. Serie = varias repeticiones seguidas.",
            "Una repetición (rep) es hacer el movimiento una vez completo: bajar y subir. " +
                "Una serie es un grupo de repeticiones seguidas, sin parar. Entre serie y serie descansas.",
            "\"3 × 10\" significa 3 series de 10 repeticiones: haces 10, descansas, 10, descansas, 10.",
            "Cada fila de la tabla es una serie. Escribes el peso y las reps y tocas ✓ al terminarla."),
        Concept("peso", "Peso (carga): libras y kilos",
            "Cuánto peso mueves. 1 kg = 2.2 lb.",
            "La carga es el peso que levantas: la barra, las mancuernas o la placa de la máquina. " +
                "En México muchas máquinas marcan libras (lb) y los discos a veces kilos (kg).",
            "50 lb son 22.7 kg. 100 kg son 220 lb.",
            "Eliges tu unidad en Perfil (por defecto libras) y siempre ves la otra unidad en pequeño debajo."),
        Concept("rango_reps", "Rango de repeticiones",
            "Un mínimo y un máximo de reps, por ejemplo de 8 a 12.",
            "En lugar de una cifra exacta, trabajas dentro de un rango. Con un peso que te permite de 8 a 12 reps " +
                "bien hechas, vas subiendo repeticiones sesión a sesión hasta llegar al máximo; entonces subes el peso.",
            "Rango 8–12: hoy hiciste 9, 9 y 8. La próxima intentas 10, 10, 9… hasta 12, 12, 12.",
            "Cada ejercicio tiene su rango (lo cambias con el chip \"8–12 reps\"). Rupio lo usa para decirte cuándo subir.",
            listOf(Papers.ACSM, Papers.SCHOENFELD_LOAD)),
        Concept("repeticiones_ciencia", "¿Pocas o muchas repeticiones?",
            "Para el músculo funcionan casi todas; para la fuerza máxima, mejor pesado.",
            "Los estudios encontraron que el músculo crece parecido con pesos pesados (pocas reps) o ligeros (muchas reps) " +
                "siempre que llegues cerca del fallo. La fuerza máxima sí mejora más con pesos pesados. " +
                "Por practicidad, 6–15 reps es un buen rango: con muy pocas el riesgo y la técnica pesan más, y con muchas la serie se vuelve muy larga e incómoda.",
            "Press con 8–12 reps o con 15–20 reps: crecimiento parecido si terminas cerca del fallo. Para levantar más en 1 rep, conviene 3–8.",
            "La guía para armar tu rutina elige el rango según tu objetivo (fuerza, músculo o salud).",
            listOf(Papers.SCHOENFELD_LOAD, Papers.LOPEZ)),
        Concept("descanso", "Descanso entre series",
            "El tiempo que esperas antes de la siguiente serie.",
            "Descansar deja que tus músculos recuperen fuerza para la siguiente serie. Si descansas muy poco, haces menos reps. " +
                "Descansar más de un minuto ayuda un poco al crecimiento; pasar de minuto y medio no mostró diferencias claras.",
            "Ejercicios grandes y pesados (sentadilla, press): 2–3 min. Ejercicios pequeños (curl, elevaciones): 1–1:30 min.",
            "Al marcar ✓ arranca el temporizador. Al terminar suena una sola vez y te llega una notificación.",
            listOf(Papers.SINGER, Papers.ACSM)),
        Concept("sobrecarga", "Sobrecarga progresiva",
            "Para seguir mejorando, poco a poco tienes que hacer un poco más.",
            "El cuerpo se adapta a lo que le pides. Si siempre haces lo mismo, deja de cambiar. " +
                "Por eso, con el tiempo, subes repeticiones o peso. Las dos formas funcionan.",
            "Semana 1: 100 lb × 10. Semana 3: 100 lb × 12. Semana 4: 105 lb × 8.",
            "El aviso de cada ejercicio (\"Sube a…\", \"Suma 1 repetición\") es la sobrecarga progresiva hecha por ti.",
            listOf(Papers.ACSM, Papers.PLOTKIN)),
        Concept("subir_peso", "Cuándo subir el peso",
            "Cuando completas todas tus series en el máximo de tu rango.",
            "Se llama doble progresión: primero subes repeticiones con el mismo peso; cuando llegas al máximo del rango en todas las series, " +
                "subes el peso un poquito (2.5–5 %, o el disco más pequeño) y vuelves al mínimo del rango. " +
                "La guía del Colegio Americano de Medicina del Deporte recomienda subir 2–10 % cuando puedes hacer 1–2 reps más que tu objetivo.",
            "Rango 8–12 con 100 lb: hiciste 12, 12 y 12 → la próxima usa 105 lb y busca 8.",
            "Rupio revisa tu sesión anterior y te dice \"Sube a…\", \"Mantén y suma 1 rep\" o, si llevas dos sesiones sin llegar al mínimo, \"Baja ~10 %\".",
            listOf(Papers.ACSM, Papers.PLOTKIN)),
        Concept("rir_rpe", "RPE y RIR: qué tan difícil fue",
            "RIR = repeticiones que te quedaban. RPE 10 = no podías ni una más.",
            "Al terminar una serie, pregúntate: \"¿cuántas más podía hacer con buena técnica?\". Eso son tus repeticiones en reserva (RIR). " +
                "El RPE es lo mismo en una escala de 1 a 10: RPE 10 = 0 en reserva, RPE 9 = te quedaba 1, RPE 8 = te quedaban 2.",
            "Hiciste 10 reps y sentías que podías 2 más → RPE 8.",
            "Es opcional: toca el número de la serie para anotarlo. Si anotas RPE 9.5–10 sin llegar a tu rango, Rupio no te sube el peso.",
            listOf(Papers.ZOURDOS)),
        Concept("fallo", "Fallo muscular",
            "Cuando ya no puedes hacer otra repetición bien hecha.",
            "Llegar cerca del fallo hace que el músculo crezca más; para la fuerza importa más el peso que llegar al fallo. " +
                "No hace falta llegar al fallo en cada serie: quedarte a 1–3 reps es una buena regla.",
            "Si en la serie 3 de press sientes que la siguiente rep no saldría, llegaste al fallo.",
            "Puedes marcar una serie como \"Al fallo\" (F) tocando su número.",
            listOf(Papers.ROBINSON)),
        Concept("calentamiento", "Serie de calentamiento",
            "Una serie ligera antes de las pesadas, para preparar el cuerpo.",
            "Con más o menos la mitad del peso, preparas músculos y articulaciones y practicas el movimiento.",
            "Si tu serie es de 165 lb, calienta con 80 lb × 12.",
            "Toca \"Agregar calentamiento\" debajo de las series del ejercicio: va antes de la primera y no te quita una serie de trabajo (también puedes tocar el número de una serie y elegir \"Calentamiento\"). No cuenta para tu volumen ni tus récords, y después descansas poco.",
            emptyList()),
        Concept("drop_set", "Drop set (y otras técnicas avanzadas)",
            "Terminas una serie, bajas el peso y sigues sin descansar.",
            "Ahorra tiempo: en los estudios da un crecimiento parecido al de las series normales en mucho menos tiempo. " +
                "El problema: el peso y las reps del drop dependen de qué tan cansado quedaste, así que cambian mucho de un día a otro " +
                "y es difícil saber si de verdad progresaste. Si los usas, hazlos siempre igual y solo en la última serie, " +
                "y mide tu progreso con las series normales.",
            "100 lb × 10, sin descanso 70 lb × 8.",
            "Marca la serie como \"Drop set\" (D): Rupio no arranca el descanso antes de ella y NO la usa para decidir cuándo subir el peso.",
            listOf(Papers.COLEMAN, Papers.SODAL)),
        Concept("volumen", "Volumen",
            "Peso × repeticiones. Una medida de cuánto trabajo hiciste.",
            "Sumas el peso por las repeticiones de cada serie. Sirve para ver si con el tiempo estás haciendo más trabajo.",
            "3 series de 100 lb × 10 = 3 000 lb de volumen.",
            "Lo ves en cada entrenamiento y en Progreso por semana, mes, 3 meses, año o histórico. El calentamiento no cuenta.",
            listOf(Papers.SCHOENFELD)),
        Concept("series_semana", "Series por músculo a la semana",
            "Cuántas series fuertes le das a cada músculo en 7 días.",
            "Es la forma más usada de medir cuánto entrenas cada músculo. Hacer 10 o más series por semana se asocia con más crecimiento; " +
                "más series siguen sumando, pero cada vez menos.",
            "Si haces 3 series de press de pecho lunes y jueves, más 2 de aperturas cada día, son 10 series de pecho a la semana.",
            "En Progreso, la gráfica \"Series por músculo a la semana\" tiene una rayita en 10.",
            listOf(Papers.SCHOENFELD, Papers.PELLAND)),
        Concept("rendimientos", "Más volumen ayuda… pero cada vez menos",
            "Hacer más series da más resultados, hasta un punto. No hace falta vivir en el gym.",
            "Los estudios muestran que más series por semana dan más músculo, pero cada serie extra suma menos que la anterior " +
                "(rendimientos decrecientes). Pasar 5 horas diarias, 7 días, no multiplica tus resultados: aumenta el cansancio y el riesgo de dejarlo. " +
                "Con 2–4 sesiones de alrededor de una hora y unas 10 series por músculo a la semana la mayoría logra muy buenos resultados.",
            "Pasar de 4 a 10 series por semana de pecho suma mucho; pasar de 20 a 26 suma poquito.",
            "La gráfica \"Series por músculo a la semana\" te ayuda a no quedarte corto ni pasarte.",
            listOf(Papers.SCHOENFELD, Papers.PELLAND, Papers.RHEA)),
        Concept("poco_tiempo", "Buenos resultados con poco tiempo",
            "2–3 días por semana y sesiones de una hora (o menos) funcionan muy bien.",
            "Lo que más importa es el total de series por semana, no cuántos días vas: con el mismo volumen, 1, 2 o 3 días por semana dieron resultados parecidos. " +
                "Una revisión sobre rutinas cortas propone priorizar ejercicios grandes (piernas, empuje y jalón), hacer al menos 4 series por músculo a la semana " +
                "y usar superseries para ahorrar tiempo. Para la salud, el mayor beneficio apareció con 30–60 minutos de fuerza a la semana.",
            "Lunes y jueves, 1 hora cada día, full body: es una rutina completa.",
            "La guía para armar tu rutina ajusta los ejercicios a tus días y a tu tiempo.",
            listOf(Papers.IVERSEN, Papers.RALSTON_FREQ, Papers.GRGIC_FREQ, Papers.MOMMA)),
        Concept("principiante", "Tus primeros años valen oro",
            "Al empezar se progresa rápido; con los años, más lento.",
            "Las personas sin entrenamiento ganan más músculo y fuerza que las entrenadas con el mismo programa. " +
                "Por eso los primeros meses y años son tu mejor momento: aprovéchalos aprendiendo buena técnica, siendo constante y usando ejercicios estables " +
                "que puedas mantener. Esas bases te sirven toda la vida.",
            "Es común subir el peso casi cada semana al inicio; después de un par de años, subir 2.5 kg puede tardar meses.",
            "\"Tu progreso\" te muestra cuánto has cambiado desde el principio.",
            listOf(Papers.LOPEZ, Papers.RHEA)),
        Concept("maquinas", "Máquinas o pesas libres",
            "Las máquinas son más fáciles de aprender y de medir; las pesas libres también funcionan.",
            "Las máquinas tienen apoyo para la espalda o el pecho y el recorrido está guiado: es más fácil hacer siempre la misma técnica, " +
                "concentrarte en el músculo y medir tu progreso. Las barras y mancuernas piden más equilibrio y práctica. " +
                "En los estudios, ambas dan resultados parecidos en fuerza y músculo; te vuelves más fuerte en lo que practicas.",
            "Press de pecho en máquina con respaldo: solo empujas. Press con barra: además equilibras la barra.",
            "La guía para armar tu rutina te recomienda máquinas, pero puedes elegir mezcla, pesas libres, mancuernas o sin equipo.",
            listOf(Papers.HAUGEN)),
        Concept("musculos", "Músculo principal y secundario",
            "Cada ejercicio trabaja sobre todo un músculo, y un poco otros.",
            "En el press de pecho trabaja sobre todo el pecho (principal), pero también hombros y tríceps (secundarios). " +
                "Por eso, al contar series, los músculos secundarios cuentan como media serie.",
            "1 serie de press de pecho = 1 serie de pecho + ½ de tríceps + ½ de hombros.",
            "En el detalle de cada ejercicio ves los músculos; Progreso usa esta forma de contar.",
            listOf(Papers.PELLAND)),
        Concept("frecuencia", "Frecuencia",
            "Cuántos días a la semana entrenas un músculo.",
            "Lo que más importa es el total de series de la semana. Con el mismo total, entrenar un músculo 1, 2 o 3 días dio resultados parecidos; " +
                "ir más días sirve sobre todo para repartir el trabajo y que cada sesión sea más corta y de mejor calidad.",
            "Pierna lunes y jueves = frecuencia 2.",
            "El mapa de constancia en Progreso te muestra qué días entrenaste; la guía para armar tu rutina reparte los músculos según tus días.",
            listOf(Papers.GRGIC_FREQ, Papers.RALSTON_FREQ, Papers.PELLAND)),
        Concept("superserie", "Superserie",
            "Dos o más ejercicios seguidos, sin descanso entre ellos.",
            "Haces una serie del ejercicio A1, inmediatamente una del A2, y luego descansas. Ahorra tiempo sin perder resultados. " +
                "Funciona mejor con músculos opuestos (pecho y espalda); con el mismo músculo harás menos peso.",
            "A1 press de pecho → A2 remo → descanso → repetir.",
            "En el menú ⋮ de un ejercicio: \"Unir en superserie con el siguiente\". Se ven en un recuadro con letra (A1, A2) y líneas que los unen.",
            listOf(Papers.ZHANG)),
        Concept("descanso_compartido", "Descanso compartido o individual",
            "En superserie: un descanso por ronda, o uno por ejercicio.",
            "Compartido: haces todos los ejercicios de la superserie y descansas una vez al final de la ronda. " +
                "Individual: cada ejercicio usa su propio descanso después de su serie.",
            "Compartido 1:30 → A1, A2, descanso 1:30.",
            "Lo eliges al crear la superserie o tocando el reloj en su cabecera."),
        Concept("1rm", "1RM estimado",
            "Lo máximo que podrías levantar una sola vez, calculado sin probarlo.",
            "Probar tu máximo real es pesado y arriesgado. Se puede estimar con tus series normales usando fórmulas (Epley y Brzycki). " +
                "Son más confiables con menos de 10 repeticiones.",
            "100 lb × 10 ≈ 133 lb de 1RM estimado.",
            "La gráfica \"Fuerza estimada (1RM)\" muestra si te estás haciendo más fuerte aunque cambies peso y reps.",
            listOf(Papers.LESUER)),
        Concept("record", "Récord personal (PR)",
            "Tu mejor marca en un ejercicio.",
            "Rupio guarda tus récords: más peso, mejor 1RM estimado, mejor serie (peso × reps), más reps y más tiempo.",
            "Si tu máximo en press era 120 lb y hoy hiciste 125 lb, es un nuevo récord 🏆.",
            "Al romper un récord te avisa en el momento y lo ves en el historial con 🏆."),
        Concept("descarga", "Bajar el peso (descarga)",
            "A veces bajar un poco ayuda a volver a subir.",
            "Si llevas varias sesiones sin mejorar o sin llegar al mínimo de tu rango, bajar alrededor de 10 % y reconstruir con buena técnica " +
                "suele destrabar el progreso. Variar la carga con el tiempo es parte de progresar.",
            "Llevabas 2 sesiones con 200 lb haciendo 6 y 5 reps (rango 8–12) → baja a 180 lb.",
            "Rupio te lo sugiere con \"Baja a…\" cuando lo detecta.",
            listOf(Papers.ACSM)),
        Concept("tipos_ejercicio", "Peso corporal, lastre y asistido",
            "No todos los ejercicios se registran igual.",
            "Algunos solo cuentan repeticiones (lagartijas, abdominales). En otros puedes añadir peso extra, llamado lastre (dominadas con cinturón). " +
                "En los asistidos una máquina o banda te ayuda: menos ayuda = más difícil. Otros se miden en tiempo (plancha).",
            "Dominada asistida con 40 lb de ayuda → la próxima, 30 lb de ayuda.",
            "Cada ejercicio muestra cómo se registra; la tabla cambia sus columnas (LB, +LB, AYUDA, REPS o SEGUNDOS)."),
        Concept("anterior", "\"Anterior\" y valores en gris",
            "Lo que hiciste la última vez y lo que te toca hoy.",
            "La columna ANTERIOR muestra la misma serie de tu sesión pasada. Los números en gris son la sugerencia de hoy " +
                "(tu plan de la rutina, tu última vez o lo que pide la progresión).",
            "Si ves \"100 lb × 12\" en anterior y \"105\" en gris, toca ✓ sin escribir y se guarda 105.",
            "Si dejas una celda vacía y marcas ✓, se usa el valor gris."),
        Concept("tecnica", "Técnica siempre igual (estandarizar)",
            "Para saber si progresas, cada vez haz el ejercicio de la misma forma.",
            "Tus números solo se pueden comparar si el ejercicio es el mismo: la misma máquina (100 lb en una no es igual que en otra), " +
                "el mismo asiento y agarre, el mismo recorrido completo, la misma velocidad y un descanso parecido. " +
                "Si un día bajas menos o rebotas, haces más reps, pero no porque seas más fuerte.",
            "Si hoy hiciste 12 reps bajando a medias y la semana pasada 10 completas, no mejoraste: cambiaste el ejercicio.",
            "Usa siempre el mismo ejercicio en tu rutina y anota en sus notas la altura del asiento o la máquina. " +
                "Rupio compara contra tu sesión anterior: si la técnica cambia, sus recomendaciones pierden sentido.",
            listOf(Papers.ACSM)),
        Concept("calorias", "Calorías y mantenimiento",
            "Las calorías son la energía de la comida. Mantenimiento = comer lo mismo que gastas.",
            "Tu cuerpo gasta energía todo el día (respirar, moverte, entrenar). Si comes la misma energía que gastas, tu peso se queda igual: " +
                "eso es comer en mantenimiento.",
            "Si gastas unas 2 300 kcal al día y comes unas 2 300, tu peso casi no cambia.",
            "Tu tendencia de peso te dice dónde estás: estable = mantenimiento."),
        Concept("deficit", "Déficit calórico",
            "Comer menos energía de la que gastas. Así se baja de peso.",
            "Si comes menos de lo que gastas, el cuerpo usa sus reservas (sobre todo grasa) y bajas de peso. " +
                "Bajar despacio, alrededor de 0.5–1 % de tu peso por semana, y seguir entrenando fuerza ayuda a conservar el músculo. " +
                "Para verte \"marcado\" (definición), normalmente hace falta un déficit.",
            "Si pesas 80 kg, bajar 0.4–0.8 kg por semana es un ritmo razonable para definir.",
            "En Peso corporal ves tu ritmo semanal en %.",
            listOf(Papers.HELMS_DIET, Papers.DONNELLY)),
        Concept("superavit", "Superávit calórico",
            "Comer más energía de la que gastas. Así se sube de peso.",
            "Con un superávit pequeño y entrenando fuerza, parte de lo que subes es músculo. Si el superávit es grande, la mayor parte es grasa. " +
                "Para ganar músculo con poca grasa se sugiere un 10–20 % más que tu mantenimiento y subir 0.25–0.5 % de tu peso por semana.",
            "Si pesas 70 kg, subir 0.2–0.35 kg por semana.",
            "Tu tendencia de peso te dice si de verdad estás en superávit.",
            listOf(Papers.IRAKI)),
        Concept("proteina", "Proteína",
            "El nutriente que ayuda a reparar y construir músculo.",
            "Comer suficiente proteína ayuda a ganar músculo con el entrenamiento. Para la mayoría, unos 1.6 g por kilo de peso al día es suficiente; " +
                "comer más no mostró beneficio extra para el músculo. Puede venir de comida normal o de suplemento: da igual la fuente.",
            "Una persona de 70 kg: unos 110 g de proteína al día, repartidos en varias comidas.",
            "Rupio no lleva tu dieta; esto es para que entiendas por qué importa.",
            listOf(Papers.MORTON)),
        Concept("peso_corporal", "Peso corporal y tendencia",
            "Tu peso cambia de un día a otro; lo que importa es la tendencia.",
            "El peso de un día puede subir o bajar 1–2 kg por agua, sal, comida o ir al baño. Por eso Rupio calcula una tendencia " +
                "(un promedio que se mueve poco a poco): un día raro casi no la mueve. Pésate siempre en las mismas condiciones: " +
                "al despertar, después del baño y antes de comer.",
            "Lunes 80.4, martes 81.3 (cenaste salado), miércoles 80.2 → la tendencia sigue en ~80.4.",
            "En Progreso → Peso corporal registras tu peso (una vez por día) y ves la tendencia y el ritmo por semana.",
            listOf(Papers.HELMS_DIET)),
        Concept("constancia", "Constancia",
            "Entrenar seguido importa más que un día perfecto.",
            "Los resultados vienen de semanas y meses de práctica. Mejor un entrenamiento corto que ninguno.",
            "2 días por semana durante un año > 6 días una semana y luego nada.",
            "En Progreso ves tu mapa de constancia y tu racha de semanas seguidas.",
            listOf(Papers.WHO)),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Beneficios del ejercicio de fuerza, con su respaldo. */
data class Benefit(val icon: String, val title: String, val body: String, val papers: List<Paper>)

object Benefits {
    val all = listOf(
        Benefit("❤️", "Vivir más y con menos enfermedades",
            "Un análisis de 16 estudios encontró que hacer ejercicios de fuerza se asocia con 10–17 % menos riesgo de morir por cualquier causa, " +
                "enfermedad del corazón, cáncer y diabetes. El mayor beneficio apareció con unos 30–60 minutos a la semana, y combinarlo con ejercicio " +
                "aeróbico (caminar, correr, bici) dio aún más beneficio.", listOf(Papers.MOMMA)),
        Benefit("🌎", "Lo recomienda la Organización Mundial de la Salud",
            "La OMS recomienda a los adultos hacer ejercicios de fuerza que trabajen todos los grupos musculares grandes 2 o más días a la semana, " +
                "además de actividad aeróbica, porque dan beneficios extra para la salud.", listOf(Papers.WHO)),
        Benefit("🙂", "Menos síntomas de depresión",
            "Un análisis de 33 ensayos con 1 877 personas encontró que el entrenamiento de fuerza redujo los síntomas de depresión de forma moderada, " +
                "sin importar la edad, el estado de salud o cuánto se fortalecieron. No sustituye a un tratamiento médico, pero puede sumarse.",
            listOf(Papers.GORDON_DEP)),
        Benefit("😌", "Menos ansiedad",
            "En 16 estudios con 922 personas, el entrenamiento de fuerza redujo los síntomas de ansiedad, tanto en personas sanas como en personas con alguna enfermedad.",
            listOf(Papers.GORDON_ANX)),
        Benefit("🦴", "Huesos más fuertes",
            "En mujeres posmenopáusicas con huesos débiles, 8 meses de entrenamiento de fuerza supervisado, 2 veces por semana y 30 minutos, " +
                "mejoraron la densidad de los huesos de la columna y la cadera y su capacidad física, sin lesiones importantes.",
            listOf(Papers.WATSON)),
        Benefit("⚡", "Mejor metabolismo, azúcar y presión",
            "Los adultos inactivos pierden 3–8 % de músculo por década. El entrenamiento de fuerza ayuda a recuperarlo, reduce la grasa visceral (la del abdomen), " +
                "mejora el manejo del azúcar en la sangre y puede bajar la presión arterial en reposo.", listOf(Papers.WESTCOTT)),
        Benefit("💪", "Más fuerza para la vida diaria, a cualquier edad",
            "Cargar el súper, subir escaleras, jugar con tus hijos o levantarte del piso. El músculo se puede ganar a cualquier edad y ayuda a mantener tu independencia.",
            listOf(Papers.WESTCOTT, Papers.WHO)),
    )
}
