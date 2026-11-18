package com.rupio.app.ui.learn

/*
 * Tutoriales: lecciones cortas, con un ejemplo que puedes tocar y un mini quiz al final.
 * Se pueden repetir cuando quieras desde Aprende → Tutoriales.
 */

enum class Demo { SETS, TECHNIQUE, PROGRESSION, DROP, VOLUME_CHART, MUSCLE_CHART, HEATMAP, E1RM_CHART, WEIGHT_CHART, ENERGY }

enum class TutorialAction(val label: String) {
    REPLAY_TOUR("Repetir el recorrido guiado"), OPEN_STATS("Ver mi Progreso"), LOG_WEIGHT("Registrar mi peso"), OPEN_GUIDE("Armar mi rutina con la guía")
}

data class Lesson(
    val title: String,
    val body: String,
    val important: String? = null,
    val demo: Demo? = null,
    val concepts: List<String> = emptyList(),
    val papers: List<Paper> = emptyList(),
)

data class Quiz(val question: String, val options: List<String>, val correct: Int, val why: String)

data class Tutorial(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val lessons: List<Lesson>,
    val quiz: List<Quiz> = emptyList(),
    val action: TutorialAction? = null,
) {
    val minutes get() = (lessons.size + quiz.size + 1) / 2
}

object Tutorials {
    val all = listOf(
        Tutorial("app", "📱", "Cómo usar Rupio", "Rutinas, marcar series, descanso y terminar",
            listOf(
                Lesson("1. Crea tu rutina",
                    "En Entrenar toca \"Nueva rutina\". Ponle un nombre (por ejemplo, los días que la haces), agrega ejercicios de la biblioteca " +
                        "y para cada uno define sus series, el rango de repeticiones y el descanso.",
                    concepts = listOf("rutina", "rango_reps", "descanso")),
                Lesson("2. Empieza el entrenamiento",
                    "Toca \"Empezar rutina\". Verás cada ejercicio con una fila por serie. La columna ANTERIOR muestra lo que hiciste la última vez " +
                        "y los números en gris son lo que te toca hoy.",
                    concepts = listOf("anterior")),
                Lesson("3. Marca cada serie",
                    "Haz la serie, escribe el peso y las reps (o déjalos vacíos para usar los grises) y toca ✓. " +
                        "Toca el número de la serie para marcarla como calentamiento, drop set o al fallo, o para anotar qué tan difícil fue.",
                    demo = Demo.SETS, concepts = listOf("serie_rep", "calentamiento", "rir_rpe")),
                Lesson("4. Descansa con el temporizador",
                    "Al marcar ✓ arranca el descanso. Cuando termina suena una vez y te llega una notificación, aunque tengas el teléfono bloqueado. " +
                        "Puedes sumar o restar 15 segundos u omitirlo.",
                    important = "Permite las notificaciones de Rupio: así suena el aviso de fin de descanso con la pantalla apagada.",
                    concepts = listOf("descanso")),
                Lesson("5. Termina y revisa",
                    "Al final toca \"Terminar\". Verás el resumen: duración, volumen y tus récords nuevos 🏆. " +
                        "La próxima vez, Rupio te dirá en cada ejercicio si toca subir el peso o sumar una repetición.",
                    concepts = listOf("volumen", "record", "subir_peso")),
            ),
            quiz = listOf(
                Quiz("¿Qué pasa si marcas ✓ sin escribir nada?", listOf("No se guarda", "Se guardan los números en gris", "Se guarda en cero"), 1,
                    "Los números en gris son la sugerencia de hoy; si no escribes nada, se usan esos."),
                Quiz("¿Dónde marcas una serie como calentamiento?", listOf("Tocando el número de la serie", "En Perfil", "No se puede"), 0,
                    "Toca el número de la serie para elegir su tipo: calentamiento, drop set o al fallo. Para sumar un calentamiento antes de la primera, usa \"Agregar calentamiento\"."),
            ),
            action = TutorialAction.REPLAY_TOUR),

        Tutorial("basicos", "🏋️", "Lo básico del gym", "Si nunca has entrenado, empieza aquí",
            listOf(
                Lesson("Serie y repetición",
                    "Una repetición es hacer el movimiento una vez completo. Una serie son varias repeticiones seguidas. " +
                        "\"3 × 10\" = 3 series de 10 repeticiones, descansando entre cada serie.",
                    demo = Demo.SETS, concepts = listOf("serie_rep")),
                Lesson("El peso: libras y kilos",
                    "Muchas máquinas marcan libras (lb). 1 kg = 2.2 lb. Rupio te muestra siempre las dos unidades para que no tengas que convertir.",
                    concepts = listOf("peso")),
                Lesson("Elige un peso que puedas controlar",
                    "Empieza con un peso con el que hagas todas las reps con buena técnica y sientas que te quedaban 2 o 3 más. " +
                        "La primera semana es para aprender el movimiento, no para cargar mucho.",
                    concepts = listOf("rir_rpe", "rango_reps")),
                Lesson("Calienta y descansa",
                    "Antes de las series pesadas haz una serie ligera de calentamiento. Entre series descansa 1–3 minutos: " +
                        "más en ejercicios grandes (sentadilla, press) y menos en los pequeños (curl, elevaciones).",
                    concepts = listOf("calentamiento", "descanso"), papers = listOf(Papers.SINGER)),
                Lesson("La técnica primero",
                    "Haz el recorrido completo, sin rebotes ni impulso, y a una velocidad controlada. Si la técnica se rompe, termina la serie.",
                    important = "Haz siempre el ejercicio igual: es la única forma de saber si estás progresando.",
                    concepts = listOf("tecnica")),
                Lesson("Sé constante",
                    "La OMS recomienda ejercicios de fuerza 2 o más días por semana. Los resultados vienen de semanas y meses de práctica.",
                    concepts = listOf("constancia", "frecuencia"), papers = listOf(Papers.WHO)),
            ),
            quiz = listOf(
                Quiz("\"4 × 8\" significa…", listOf("4 repeticiones de 8 kilos", "4 series de 8 repeticiones", "8 series de 4 repeticiones"), 1,
                    "El primer número son las series y el segundo las repeticiones de cada serie."),
                Quiz("100 lb son más o menos…", listOf("45 kg", "100 kg", "220 kg"), 0, "1 kg = 2.2 lb, así que 100 lb ≈ 45 kg."),
                Quiz("La primera semana conviene…", listOf("Cargar lo máximo posible", "Aprender el movimiento con un peso cómodo", "No calentar"), 1,
                    "Primero la técnica; el peso sube después."),
            )),

        Tutorial("rutina", "🗓️", "Cómo armar tu rutina", "Full body, torso/pierna y cuántos días",
            listOf(
                Lesson("Empieza por tus días y tu tiempo",
                    "La mejor rutina es la que puedes repetir semana tras semana. Primero decide cuántos días y cuánto tiempo tienes de verdad; " +
                        "después se elige cómo repartir los músculos.",
                    important = "Con 2–3 días por semana y una hora por sesión se consiguen muy buenos resultados.",
                    concepts = listOf("poco_tiempo", "frecuencia"), papers = listOf(Papers.IVERSEN, Papers.RALSTON_FREQ)),
                Lesson("Cuerpo completo (full body)",
                    "Cada día trabajas todo el cuerpo con 1–2 ejercicios por zona. Ideal con 2–3 días: cada músculo se entrena 2–3 veces por semana " +
                        "aunque vayas poco. Es la opción más recomendada para empezar."),
                Lesson("Torso / Pierna",
                    "Un día torso (pecho, espalda, hombros y brazos) y otro pierna. Ideal con 4 días: cada músculo 2 veces por semana " +
                        "con sesiones más cortas que un full body."),
                Lesson("Empuje / Jalón / Pierna",
                    "Empuje: pecho, hombros y tríceps. Jalón: espalda y bíceps. Pierna: lo de abajo. Ideal con 6 días; con 3 días cada músculo " +
                        "solo se entrena una vez por semana, que funciona pero suele rendir menos que un full body.",
                    concepts = listOf("frecuencia"), papers = listOf(Papers.GRGIC_FREQ)),
                Lesson("Cómo ordenar cada día",
                    "Primero los ejercicios grandes (prensa o sentadilla, press, jalón o remo), cuando estás fresco; al final los pequeños " +
                        "(bíceps, tríceps, laterales, pantorrillas). 2–4 series por ejercicio y unas 10 series por músculo a la semana es un buen objetivo.",
                    concepts = listOf("series_semana", "rendimientos"), papers = listOf(Papers.ACSM, Papers.SCHOENFELD)),
                Lesson("Máquinas: más fáciles al empezar",
                    "Las máquinas tienen apoyo para la espalda o el pecho y el recorrido está guiado: aprendes más rápido, haces siempre la misma técnica " +
                        "y es más fácil medir tu progreso. Las pesas libres también funcionan.",
                    important = "Elige ejercicios que puedas repetir igual cada semana. Cambiar de ejercicio a cada rato hace imposible saber si progresas.",
                    concepts = listOf("maquinas", "tecnica")),
            ),
            quiz = listOf(
                Quiz("Puedes ir 2 días por semana. ¿Qué tipo de rutina conviene?", listOf("Empuje / Jalón / Pierna", "Cuerpo completo", "Un músculo por día"), 1,
                    "Con full body cada músculo se entrena 2 veces por semana aunque solo vayas 2 días."),
                Quiz("¿Qué va primero en la sesión?", listOf("Curl de bíceps", "Prensa o press (ejercicios grandes)", "Abdominales"), 1,
                    "Los ejercicios grandes van primero, cuando tienes más energía."),
            ),
            action = TutorialAction.OPEN_GUIDE),

        Tutorial("cuanto", "⏱️", "Cuánto entrenar (sin matarte)", "Volumen, días, repeticiones y tus primeros años",
            listOf(
                Lesson("Más ayuda… pero cada vez menos",
                    "Hacer más series por semana da más músculo, pero cada serie extra suma menos que la anterior. " +
                        "Pasar de 4 a 10 series por músculo suma mucho; pasar de 20 a 26, poquito.",
                    important = "No necesitas entrenar 5 horas al día, 7 días. Más tiempo no multiplica los resultados: aumenta el cansancio y el riesgo de dejarlo.",
                    demo = Demo.MUSCLE_CHART, concepts = listOf("rendimientos", "series_semana"), papers = listOf(Papers.SCHOENFELD, Papers.PELLAND)),
                Lesson("Pocos días también funcionan",
                    "Con el mismo número de series por semana, entrenar 1, 2 o 3 días dio resultados parecidos en fuerza. Lo que importa es el total semanal. " +
                        "Para la salud, el mayor beneficio apareció con 30–60 minutos de fuerza a la semana.",
                    concepts = listOf("poco_tiempo", "frecuencia"), papers = listOf(Papers.RALSTON_FREQ, Papers.GRGIC_FREQ, Papers.MOMMA)),
                Lesson("Rutinas cortas que funcionan",
                    "Si tienes poco tiempo: prioriza ejercicios grandes (una de pierna, un empuje y un jalón), haz al menos 4 series por músculo a la semana " +
                        "con 6–15 repeticiones y usa superseries para ahorrar tiempo.",
                    concepts = listOf("superserie"), papers = listOf(Papers.IVERSEN)),
                Lesson("¿Pocas o muchas repeticiones?",
                    "El músculo crece parecido con pesos pesados o ligeros si terminas cerca del fallo. La fuerza máxima mejora más con pesos pesados. " +
                        "Un rango de 6–15 repeticiones es práctico para casi todos.",
                    concepts = listOf("repeticiones_ciencia", "rango_reps"), papers = listOf(Papers.SCHOENFELD_LOAD, Papers.LOPEZ)),
                Lesson("Tus primeros años valen oro",
                    "Las personas que empiezan ganan más músculo y fuerza que las que ya entrenan, con el mismo programa. Con los años el progreso se vuelve más lento.",
                    important = "Empieza con buenas bases: técnica estable, ejercicios que puedas mantener y constancia. Lo que construyas al principio te sirve para siempre.",
                    concepts = listOf("principiante", "tecnica"), papers = listOf(Papers.LOPEZ, Papers.RHEA)),
            ),
            quiz = listOf(
                Quiz("Pasar de 20 a 26 series de pecho por semana…", listOf("Duplica tus resultados", "Suma poco: los resultados crecen cada vez menos", "Es obligatorio"), 1,
                    "Más volumen ayuda, pero con rendimientos decrecientes."),
                Quiz("Con el mismo número de series por semana, ¿2 días o 4 días?", listOf("4 días siempre gana", "Resultados parecidos", "2 días no sirve"), 1,
                    "Con el volumen igualado, la frecuencia casi no cambia los resultados."),
            )),

        Tutorial("progresar", "📈", "Cómo progresar", "Sobrecarga, técnica igual y cuándo subir peso",
            listOf(
                Lesson("Sobrecarga progresiva",
                    "El cuerpo se adapta a lo que le pides. Para seguir mejorando, con el tiempo haz un poco más: más repeticiones o más peso. " +
                        "Las dos formas funcionan.",
                    important = "Sin sobrecarga progresiva no hay progreso: si siempre haces el mismo peso y las mismas reps, tu cuerpo deja de cambiar.",
                    concepts = listOf("sobrecarga"), papers = listOf(Papers.ACSM, Papers.PLOTKIN)),
                Lesson("Estandariza tu técnica",
                    "Tus números solo dicen la verdad si comparas lo mismo contra lo mismo: el mismo ejercicio, la misma máquina, " +
                        "el mismo asiento y agarre, el mismo recorrido, la misma velocidad y un descanso parecido.",
                    important = "Si cambias de máquina, bajas a medias o rebotas, harás \"más\" pero no serás más fuerte. Mantén todo igual y solo cambia el peso o las reps.",
                    demo = Demo.TECHNIQUE, concepts = listOf("tecnica")),
                Lesson("Cuándo subir el peso",
                    "Trabaja dentro de un rango (por ejemplo, 8–12). Sube repeticiones con el mismo peso; cuando llegues al máximo del rango " +
                        "en todas las series, sube el peso un poquito y vuelve al mínimo. Prueba el ejemplo:",
                    demo = Demo.PROGRESSION, concepts = listOf("subir_peso", "rango_reps"), papers = listOf(Papers.ACSM)),
                Lesson("Qué tan cerca del fallo",
                    "Quedarte a 1–3 repeticiones del fallo es una buena regla. No hace falta llegar al fallo en cada serie.",
                    concepts = listOf("rir_rpe", "fallo"), papers = listOf(Papers.ROBINSON, Papers.ZOURDOS)),
                Lesson("Drop sets y técnicas avanzadas",
                    "Los drop sets ahorran tiempo y dan un crecimiento parecido al de las series normales. Pero el peso y las reps del drop " +
                        "dependen de qué tan cansado quedaste, y eso cambia cada día.",
                    important = "Las técnicas avanzadas (drop sets, al fallo en todo, superseries del mismo músculo) hacen difícil medir si progresas. " +
                        "Si las usas, hazlas siempre igual y solo al final, y mide tu progreso con tus series normales. Rupio no usa los drop sets para decidir cuándo subir.",
                    demo = Demo.DROP, concepts = listOf("drop_set", "superserie"), papers = listOf(Papers.COLEMAN, Papers.SODAL)),
                Lesson("Si te estancas",
                    "Si llevas dos sesiones sin llegar al mínimo de tu rango, baja alrededor de 10 % y vuelve a subir con buena técnica. " +
                        "Duerme bien y come suficiente: también cuentan.",
                    concepts = listOf("descarga")),
            ),
            quiz = listOf(
                Quiz("Rango 8–12. Hiciste 12, 12 y 12 con 100 lb. ¿Qué toca?", listOf("Repetir 100 lb", "Subir un poco el peso y buscar 8", "Bajar el peso"), 1,
                    "Completaste el máximo del rango en todas las series: sube el peso y vuelve al mínimo."),
                Quiz("Hoy usaste otra máquina de press y moviste 20 lb más. ¿Progresaste?", listOf("Sí, seguro", "No se puede saber: cambiaste el ejercicio", "Sí, si sudaste más"), 1,
                    "Cada máquina pesa distinto. Para comparar, usa siempre la misma."),
                Quiz("¿Por qué los drop sets complican medir el progreso?", listOf("Porque no sirven", "Porque su peso y reps dependen del cansancio de la serie anterior", "Porque se olvidan"), 1,
                    "Funcionan para crecer, pero sus números varían mucho; mide el progreso con tus series normales."),
            )),

        Tutorial("graficas", "📊", "Entender tus gráficas", "Qué significa cada gráfica de Progreso",
            listOf(
                Lesson("Volumen por semana o mes",
                    "Cada barra es la suma de peso × repeticiones de tus series (sin calentamiento). Toca una barra para ver el número exacto, " +
                        "cuántos entrenos y cuántas series hubo. Arriba eliges el periodo: semana, mes, 3 meses, año o histórico.",
                    important = "Compara periodos parecidos y con la misma rutina. Si cambias ejercicios, el volumen cambia aunque no seas más fuerte.",
                    demo = Demo.VOLUME_CHART, concepts = listOf("volumen", "tecnica")),
                Lesson("Series por músculo a la semana",
                    "Cada barra dice cuántas series fuertes le diste a ese músculo por semana. La rayita marca 10 series: a partir de ahí se suele crecer más. " +
                        "Los músculos que ayudan (secundarios) cuentan como media serie.",
                    demo = Demo.MUSCLE_CHART, concepts = listOf("series_semana", "musculos"), papers = listOf(Papers.SCHOENFELD, Papers.PELLAND)),
                Lesson("Constancia",
                    "Cada cuadrito es un día; más azul = más series ese día. Las columnas son semanas. Arriba ves tu racha de semanas seguidas entrenando.",
                    demo = Demo.HEATMAP, concepts = listOf("constancia")),
                Lesson("Fuerza estimada (1RM)",
                    "La línea muestra lo máximo que podrías levantar una vez, calculado con tu mejor serie de cada sesión. " +
                        "Sirve para ver si te haces más fuerte aunque cambies el peso y las reps. Arrastra el dedo para ver cada día.",
                    important = "Es una estimación: es más confiable con series de menos de 10 repeticiones y con la misma técnica.",
                    demo = Demo.E1RM_CHART, concepts = listOf("1rm"), papers = listOf(Papers.LESUER)),
                Lesson("Peso corporal",
                    "Los puntos son el peso de cada día; la línea azul es la tendencia. Fíjate en la línea, no en los puntos: " +
                        "un día alto por agua o sal casi no la mueve. Abajo ves tu ritmo en % por semana.",
                    demo = Demo.WEIGHT_CHART, concepts = listOf("peso_corporal")),
            ),
            quiz = listOf(
                Quiz("En la gráfica de peso, ¿qué miras para saber si bajas?", listOf("El punto de hoy", "La línea de tendencia", "El punto más bajo"), 1,
                    "El peso de un día varía por agua; la tendencia muestra el cambio real."),
                Quiz("La rayita en \"Series por músculo\" marca…", listOf("10 series por semana", "El máximo permitido", "Tu récord"), 0,
                    "10 o más series por semana se asocian con más crecimiento."),
            ),
            action = TutorialAction.OPEN_STATS),

        Tutorial("nutricion", "🥗", "Nutrición y peso corporal", "Déficit, superávit y por qué la dieta importa",
            listOf(
                Lesson("Calorías y mantenimiento",
                    "Las calorías son la energía de la comida. Tu cuerpo gasta energía todo el día. Si comes lo mismo que gastas, tu peso se queda igual: " +
                        "eso es comer en mantenimiento. Prueba el ejemplo:",
                    demo = Demo.ENERGY, concepts = listOf("calorias")),
                Lesson("Déficit y superávit",
                    "Déficit calórico: comer menos de lo que gastas → bajas de peso. Superávit calórico: comer más de lo que gastas → subes de peso. " +
                        "Con un superávit pequeño y entrenando fuerza, parte de lo que subes es músculo.",
                    concepts = listOf("deficit", "superavit"), papers = listOf(Papers.HELMS_DIET, Papers.IRAKI)),
                Lesson("Bajar de peso solo con ejercicio es difícil",
                    "Entrenar quema menos calorías de lo que parece, y es fácil compensarlas comiendo un poco más. " +
                        "El Colegio Americano de Medicina del Deporte encontró que menos de 150 minutos de actividad por semana casi no bajan el peso, " +
                        "y más de 150 minutos solo bajan unos 2–3 kg. Las pérdidas importantes se ven al combinar dieta y ejercicio.",
                    important = "Si tu objetivo es bajar de peso, necesitas cuidar tu alimentación (déficit calórico). El ejercicio ayuda, pero solo no basta.",
                    concepts = listOf("deficit"), papers = listOf(Papers.DONNELLY)),
                Lesson("Para verte definido: déficit",
                    "Para que se noten los músculos (definición), normalmente hay que bajar grasa, y eso requiere un déficit. " +
                        "Entrenar fuerza mientras estás en déficit ayuda a conservar el músculo. Un ritmo de 0.5–1 % de tu peso por semana ayuda a no perder músculo ni fuerza.",
                    important = "En temas de estética, a la mayoría le conviene más un déficit calórico para definir. El superávit es para cuando el objetivo es ganar tamaño.",
                    concepts = listOf("deficit", "superavit"), papers = listOf(Papers.HELMS_DIET)),
                Lesson("Proteína",
                    "Comer suficiente proteína ayuda a conservar y ganar músculo. Para la mayoría, unos 1.6 g por kilo de peso al día es suficiente; " +
                        "comer más no mostró beneficio extra.",
                    concepts = listOf("proteina"), papers = listOf(Papers.MORTON)),
                Lesson("Mide con tu tendencia de peso",
                    "No necesitas contar todo para saber dónde estás: registra tu peso en Rupio y mira la tendencia. Si baja, estás en déficit; " +
                        "si está estable, en mantenimiento; si sube, en superávit. Ajusta tu comida poco a poco según el ritmo que buscas.",
                    important = "Esto es información general, no un plan médico. Si tienes alguna condición de salud, estás embarazada o has tenido problemas con la comida, consulta a un profesional.",
                    demo = Demo.WEIGHT_CHART, concepts = listOf("peso_corporal")),
            ),
            quiz = listOf(
                Quiz("Quieres bajar de peso. ¿Qué es lo más importante?", listOf("Entrenar más horas", "Comer menos de lo que gastas (déficit)", "Hacer drop sets"), 1,
                    "Sin déficit calórico no se baja de peso; el ejercicio ayuda, pero solo no basta."),
                Quiz("Tu tendencia de peso lleva 3 semanas igual. Estás en…", listOf("Déficit", "Mantenimiento", "Superávit"), 1,
                    "Si el peso no cambia, comes lo mismo que gastas."),
                Quiz("Para definir sin perder músculo, un ritmo razonable es…", listOf("0.5–1 % de tu peso por semana", "3 kg por semana", "No comer"), 0,
                    "Bajar despacio y entrenar fuerza ayuda a conservar el músculo."),
            ),
            action = TutorialAction.LOG_WEIGHT),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
