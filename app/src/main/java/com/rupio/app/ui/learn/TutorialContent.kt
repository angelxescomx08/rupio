package com.rupio.app.ui.learn

import com.rupio.app.core.PerLanguage
import com.rupio.app.core.tr

/*
 * Tutoriales: lecciones cortas, con un ejemplo que puedes tocar y un mini quiz al final.
 * Se pueden repetir cuando quieras desde Aprende → Tutoriales.
 */

enum class Demo { SETS, TECHNIQUE, PROGRESSION, DROP, VOLUME_CHART, MUSCLE_CHART, HEATMAP, E1RM_CHART, WEIGHT_CHART, ENERGY }

enum class TutorialAction {
    REPLAY_TOUR, OPEN_STATS, LOG_WEIGHT, OPEN_GUIDE;
    val label: String get() = when (this) {
        REPLAY_TOUR -> tr("Repetir el recorrido guiado", "Replay the guided tour"); OPEN_STATS -> tr("Ver mi Progreso", "See my Progress")
        LOG_WEIGHT -> tr("Registrar mi peso", "Log my weight"); OPEN_GUIDE -> tr("Armar mi rutina con la guía", "Build my routine with the guide")
    }
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
    /** v2.0: se arma una vez por idioma (ver PerLanguage). */
    private val cached = PerLanguage { listOf(
        Tutorial("app", "📱", tr("Cómo usar Rupio", "How to use Rupio"), tr("Rutinas, marcar series, descanso y terminar", "Routines, checking off sets, rest and finishing"),
            listOf(
                Lesson(tr("1. Crea tu rutina", "1. Create your routine"),
                    tr("En Entrenar toca \"Nueva rutina\". Ponle un nombre (por ejemplo, los días que la haces), agrega ejercicios de la biblioteca ", "In Workout, tap “New”. Give it a name (for example, the days you do it), add exercises from the library ") +
                        tr("y para cada uno define sus series, el rango de repeticiones y el descanso.", "and set each one's sets, rep range and rest."),
                    concepts = listOf("rutina", "rango_reps", "descanso")),
                Lesson(tr("2. Empieza el entrenamiento", "2. Start the workout"),
                    tr("Toca \"Empezar rutina\". Verás cada ejercicio con una fila por serie. La columna ANTERIOR muestra lo que hiciste la última vez ", "Tap “Start routine”. You'll see each exercise with one row per set. The PREVIOUS column shows what you did last time ") +
                        tr("y los números en gris son lo que te toca hoy.", "and the gray numbers are what's up today."),
                    concepts = listOf("anterior")),
                Lesson(tr("3. Marca cada serie", "3. Check off each set"),
                    tr("Haz la serie, escribe el peso y las reps (o déjalos vacíos para usar los grises) y toca ✓. ", "Do the set, enter the weight and reps (or leave them empty to use the gray ones) and tap ✓. ") +
                        tr("Toca el número de la serie para marcarla como calentamiento, drop set o al fallo, o para anotar qué tan difícil fue.", "Tap the set number to mark it as a warm-up, drop set or failure set, or to log how hard it was."),
                    demo = Demo.SETS, concepts = listOf("serie_rep", "calentamiento", "rir_rpe")),
                Lesson(tr("4. Descansa con el temporizador", "4. Rest with the timer"),
                    tr("Al marcar ✓ arranca el descanso. Cuando termina suena una vez y te llega una notificación, aunque tengas el teléfono bloqueado. ", "Tapping ✓ starts the rest. When it ends it plays once and you get a notification, even with your phone locked. ") +
                        tr("Puedes sumar o restar 15 segundos u omitirlo.", "You can add or subtract 15 seconds or skip it."),
                    important = tr("Permite las notificaciones de Rupio: así suena el aviso de fin de descanso con la pantalla apagada.", "Allow Rupio's notifications: that's how the rest alert plays with the screen off."),
                    concepts = listOf("descanso")),
                Lesson(tr("5. Termina y revisa", "5. Finish and review"),
                    tr("Al final toca \"Terminar\". Verás el resumen: duración, volumen y tus récords nuevos 🏆. ", "At the end, tap “Finish”. You'll see the summary: duration, volume and your new PRs 🏆. ") +
                        tr("La próxima vez, Rupio te dirá en cada ejercicio si toca subir el peso o sumar una repetición.", "Next time, Rupio will tell you on each exercise whether to add weight or add a rep."),
                    concepts = listOf("volumen", "record", "subir_peso")),
            ),
            quiz = listOf(
                Quiz(tr("¿Qué pasa si marcas ✓ sin escribir nada?", "What happens if you tap ✓ without typing anything?"), listOf(tr("No se guarda", "It isn't saved"), tr("Se guardan los números en gris", "The gray numbers are saved"), tr("Se guarda en cero", "It's saved as zero")), 1,
                    tr("Los números en gris son la sugerencia de hoy; si no escribes nada, se usan esos.", "The gray numbers are today's suggestion; if you don't type anything, those are used.")),
                Quiz(tr("¿Dónde marcas una serie como calentamiento?", "Where do you mark a set as a warm-up?"), listOf(tr("Tocando el número de la serie", "By tapping the set number"), tr("En Perfil", "In Profile"), tr("No se puede", "You can't")), 0,
                    tr("Toca el número de la serie para elegir su tipo: calentamiento, drop set o al fallo. Para sumar un calentamiento antes de la primera, usa \"Agregar calentamiento\".", "Tap the set number to choose its type: warm-up, drop set or failure. To add a warm-up before the first one, use “Add warm-up”.")),
            ),
            action = TutorialAction.REPLAY_TOUR),

        Tutorial("basicos", "🏋️", tr("Lo básico del gym", "Gym basics"), tr("Si nunca has entrenado, empieza aquí", "If you've never trained, start here"),
            listOf(
                Lesson(tr("Serie y repetición", "Set and rep"),
                    tr("Una repetición es hacer el movimiento una vez completo. Una serie son varias repeticiones seguidas. ", "A rep is doing the movement once, all the way. A set is several reps in a row. ") +
                        tr("\"3 × 10\" = 3 series de 10 repeticiones, descansando entre cada serie.", "“3 × 10” = 3 sets of 10 reps, resting between each set."),
                    demo = Demo.SETS, concepts = listOf("serie_rep")),
                Lesson(tr("El peso: libras y kilos", "Weight: pounds and kilos"),
                    tr("Muchas máquinas marcan libras (lb). 1 kg = 2.2 lb. Rupio te muestra siempre las dos unidades para que no tengas que convertir.", "Many machines show pounds (lb). 1 kg = 2.2 lb. Rupio always shows both units so you don't have to convert."),
                    concepts = listOf("peso")),
                Lesson(tr("Elige un peso que puedas controlar", "Pick a weight you can control"),
                    tr("Empieza con un peso con el que hagas todas las reps con buena técnica y sientas que te quedaban 2 o 3 más. ", "Start with a weight you can do every rep with in good form, feeling like you had 2 or 3 more left. ") +
                        tr("La primera semana es para aprender el movimiento, no para cargar mucho.", "The first week is for learning the movement, not for lifting heavy."),
                    concepts = listOf("rir_rpe", "rango_reps")),
                Lesson(tr("Calienta y descansa", "Warm up and rest"),
                    tr("Antes de las series pesadas haz una serie ligera de calentamiento. Entre series descansa 1–3 minutos: ", "Before your heavy sets, do a light warm-up set. Rest 1–3 minutes between sets: ") +
                        tr("más en ejercicios grandes (sentadilla, press) y menos en los pequeños (curl, elevaciones).", "more on big lifts (squat, press) and less on small ones (curls, raises)."),
                    concepts = listOf("calentamiento", "descanso"), papers = listOf(Papers.SINGER)),
                Lesson(tr("La técnica primero", "Form first"),
                    tr("Haz el recorrido completo, sin rebotes ni impulso, y a una velocidad controlada. Si la técnica se rompe, termina la serie.", "Use the full range of motion, with no bouncing or momentum, at a controlled speed. If your form breaks down, end the set."),
                    important = tr("Haz siempre el ejercicio igual: es la única forma de saber si estás progresando.", "Always do the exercise the same way: it's the only way to know if you're progressing."),
                    concepts = listOf("tecnica")),
                Lesson(tr("Sé constante", "Be consistent"),
                    tr("La OMS recomienda ejercicios de fuerza 2 o más días por semana. Los resultados vienen de semanas y meses de práctica.", "The WHO recommends strength exercises 2 or more days a week. Results come from weeks and months of practice."),
                    concepts = listOf("constancia", "frecuencia"), papers = listOf(Papers.WHO)),
            ),
            quiz = listOf(
                Quiz(tr("\"4 × 8\" significa…", "“4 × 8” means…"), listOf(tr("4 repeticiones de 8 kilos", "4 reps of 8 kilos"), tr("4 series de 8 repeticiones", "4 sets of 8 reps"), tr("8 series de 4 repeticiones", "8 sets of 4 reps")), 1,
                    tr("El primer número son las series y el segundo las repeticiones de cada serie.", "The first number is the sets and the second is the reps in each set.")),
                Quiz(tr("100 lb son más o menos…", "100 lb is about…"), listOf("45 kg", "100 kg", "220 kg"), 0, tr("1 kg = 2.2 lb, así que 100 lb ≈ 45 kg.", "1 kg = 2.2 lb, so 100 lb ≈ 45 kg.")),
                Quiz(tr("La primera semana conviene…", "In the first week it's best to…"), listOf(tr("Cargar lo máximo posible", "Lift as much as possible"), tr("Aprender el movimiento con un peso cómodo", "Learn the movement with a comfortable weight"), tr("No calentar", "Skip the warm-up")), 1,
                    tr("Primero la técnica; el peso sube después.", "Form first; the weight goes up later.")),
            )),

        Tutorial("rutina", "🗓️", tr("Cómo armar tu rutina", "How to build your routine"), tr("Full body, torso/pierna y cuántos días", "Full body, upper/lower and how many days"),
            listOf(
                Lesson(tr("Empieza por tus días y tu tiempo", "Start with your days and your time"),
                    tr("La mejor rutina es la que puedes repetir semana tras semana. Primero decide cuántos días y cuánto tiempo tienes de verdad; ", "The best routine is the one you can repeat week after week. First decide how many days and how much time you really have; ") +
                        tr("después se elige cómo repartir los músculos.", "then you choose how to split the muscles."),
                    important = tr("Con 2–3 días por semana y una hora por sesión se consiguen muy buenos resultados.", "With 2–3 days a week and an hour per session, you get very good results."),
                    concepts = listOf("poco_tiempo", "frecuencia"), papers = listOf(Papers.IVERSEN, Papers.RALSTON_FREQ)),
                Lesson(tr("Cuerpo completo (full body)", "Full body"),
                    tr("Cada día trabajas todo el cuerpo con 1–2 ejercicios por zona. Ideal con 2–3 días: cada músculo se entrena 2–3 veces por semana ", "Each day you work your whole body with 1–2 exercises per area. Ideal with 2–3 days: each muscle is trained 2–3 times a week ") +
                        tr("aunque vayas poco. Es la opción más recomendada para empezar.", "even if you don't go often. It's the most recommended option to start.")),
                Lesson(tr("Torso / Pierna", "Upper / Lower"),
                    tr("Un día torso (pecho, espalda, hombros y brazos) y otro pierna. Ideal con 4 días: cada músculo 2 veces por semana ", "One day upper body (chest, back, shoulders and arms) and another lower body. Ideal with 4 days: each muscle twice a week ") +
                        tr("con sesiones más cortas que un full body.", "with shorter sessions than full body.")),
                Lesson(tr("Empuje / Jalón / Pierna", "Push / Pull / Legs"),
                    tr("Empuje: pecho, hombros y tríceps. Jalón: espalda y bíceps. Pierna: lo de abajo. Ideal con 6 días; con 3 días cada músculo ", "Push: chest, shoulders and triceps. Pull: back and biceps. Legs: everything below. Ideal with 6 days; with 3 days each muscle ") +
                        tr("solo se entrena una vez por semana, que funciona pero suele rendir menos que un full body.", "is trained only once a week, which works but usually pays off less than full body."),
                    concepts = listOf("frecuencia"), papers = listOf(Papers.GRGIC_FREQ)),
                Lesson(tr("Cómo ordenar cada día", "How to order each day"),
                    tr("Primero los ejercicios grandes (prensa o sentadilla, press, jalón o remo), cuando estás fresco; al final los pequeños ", "Big lifts first (leg press or squat, press, pulldown or row), while you're fresh; small ones at the end ") +
                        tr("(bíceps, tríceps, laterales, pantorrillas). 2–4 series por ejercicio y unas 10 series por músculo a la semana es un buen objetivo.", "(biceps, triceps, laterals, calves). 2–4 sets per exercise and about 10 sets per muscle per week is a good target."),
                    concepts = listOf("series_semana", "rendimientos"), papers = listOf(Papers.ACSM, Papers.SCHOENFELD)),
                Lesson(tr("Máquinas: más fáciles al empezar", "Machines: easier when starting out"),
                    tr("Las máquinas tienen apoyo para la espalda o el pecho y el recorrido está guiado: aprendes más rápido, haces siempre la misma técnica ", "Machines support your back or chest and guide the movement: you learn faster, keep the same form every time ") +
                        tr("y es más fácil medir tu progreso. Las pesas libres también funcionan.", "and your progress is easier to measure. Free weights work too."),
                    important = tr("Elige ejercicios que puedas repetir igual cada semana. Cambiar de ejercicio a cada rato hace imposible saber si progresas.", "Choose exercises you can repeat the same way every week. Switching exercises all the time makes it impossible to know if you're progressing."),
                    concepts = listOf("maquinas", "tecnica")),
            ),
            quiz = listOf(
                Quiz(tr("Puedes ir 2 días por semana. ¿Qué tipo de rutina conviene?", "You can go 2 days a week. Which kind of routine fits best?"), listOf(tr("Empuje / Jalón / Pierna", "Push / Pull / Legs"), tr("Cuerpo completo", "Full body"), tr("Un músculo por día", "One muscle per day")), 1,
                    tr("Con full body cada músculo se entrena 2 veces por semana aunque solo vayas 2 días.", "With full body, each muscle is trained twice a week even if you only go 2 days.")),
                Quiz(tr("¿Qué va primero en la sesión?", "What goes first in the session?"), listOf(tr("Curl de bíceps", "Biceps curl"), tr("Prensa o press (ejercicios grandes)", "Leg press or press (big lifts)"), tr("Abdominales", "Abs")), 1,
                    tr("Los ejercicios grandes van primero, cuando tienes más energía.", "Big lifts go first, when you have the most energy.")),
            ),
            action = TutorialAction.OPEN_GUIDE),

        Tutorial("cuanto", "⏱️", tr("Cuánto entrenar (sin matarte)", "How much to train (without wrecking yourself)"), tr("Volumen, días, repeticiones y tus primeros años", "Volume, days, reps and your first years"),
            listOf(
                Lesson(tr("Más ayuda… pero cada vez menos", "More helps… but less each time"),
                    tr("Hacer más series por semana da más músculo, pero cada serie extra suma menos que la anterior. ", "More sets per week give more muscle, but each extra set adds less than the one before. ") +
                        tr("Pasar de 4 a 10 series por músculo suma mucho; pasar de 20 a 26, poquito.", "Going from 4 to 10 sets per muscle adds a lot; going from 20 to 26, very little."),
                    important = tr("No necesitas entrenar 5 horas al día, 7 días. Más tiempo no multiplica los resultados: aumenta el cansancio y el riesgo de dejarlo.", "You don't need to train 5 hours a day, 7 days a week. More time doesn't multiply results: it adds fatigue and the risk of quitting."),
                    demo = Demo.MUSCLE_CHART, concepts = listOf("rendimientos", "series_semana"), papers = listOf(Papers.SCHOENFELD, Papers.PELLAND)),
                Lesson(tr("Pocos días también funcionan", "Few days work too"),
                    tr("Con el mismo número de series por semana, entrenar 1, 2 o 3 días dio resultados parecidos en fuerza. Lo que importa es el total semanal. ", "With the same number of sets per week, training 1, 2 or 3 days gave similar strength results. What matters is the weekly total. ") +
                        tr("Para la salud, el mayor beneficio apareció con 30–60 minutos de fuerza a la semana.", "For health, the biggest benefit showed up with 30–60 minutes of strength training per week."),
                    concepts = listOf("poco_tiempo", "frecuencia"), papers = listOf(Papers.RALSTON_FREQ, Papers.GRGIC_FREQ, Papers.MOMMA)),
                Lesson(tr("Rutinas cortas que funcionan", "Short routines that work"),
                    tr("Si tienes poco tiempo: prioriza ejercicios grandes (una de pierna, un empuje y un jalón), haz al menos 4 series por músculo a la semana ", "If you're short on time: prioritize big lifts (one leg exercise, one push and one pull), do at least 4 sets per muscle per week ") +
                        tr("con 6–15 repeticiones y usa superseries para ahorrar tiempo.", "with 6–15 reps and use supersets to save time."),
                    concepts = listOf("superserie"), papers = listOf(Papers.IVERSEN)),
                Lesson(tr("¿Pocas o muchas repeticiones?", "Few or many reps?"),
                    tr("El músculo crece parecido con pesos pesados o ligeros si terminas cerca del fallo. La fuerza máxima mejora más con pesos pesados. ", "Muscle grows similarly with heavy or light weights if you finish close to failure. Max strength improves more with heavy weights. ") +
                        tr("Un rango de 6–15 repeticiones es práctico para casi todos.", "A 6–15 rep range is practical for almost everyone."),
                    concepts = listOf("repeticiones_ciencia", "rango_reps"), papers = listOf(Papers.SCHOENFELD_LOAD, Papers.LOPEZ)),
                Lesson(tr("Tus primeros años valen oro", "Your first years are gold"),
                    tr("Las personas que empiezan ganan más músculo y fuerza que las que ya entrenan, con el mismo programa. Con los años el progreso se vuelve más lento.", "Beginners gain more muscle and strength than people who already train, on the same program. Over the years, progress gets slower."),
                    important = tr("Empieza con buenas bases: técnica estable, ejercicios que puedas mantener y constancia. Lo que construyas al principio te sirve para siempre.", "Start with good foundations: stable form, exercises you can stick with and consistency. What you build at the start lasts forever."),
                    concepts = listOf("principiante", "tecnica"), papers = listOf(Papers.LOPEZ, Papers.RHEA)),
            ),
            quiz = listOf(
                Quiz(tr("Pasar de 20 a 26 series de pecho por semana…", "Going from 20 to 26 chest sets per week…"), listOf(tr("Duplica tus resultados", "Doubles your results"), tr("Suma poco: los resultados crecen cada vez menos", "Adds little: results grow less each time"), tr("Es obligatorio", "Is mandatory")), 1,
                    tr("Más volumen ayuda, pero con rendimientos decrecientes.", "More volume helps, but with diminishing returns.")),
                Quiz(tr("Con el mismo número de series por semana, ¿2 días o 4 días?", "With the same number of sets per week, 2 days or 4 days?"), listOf(tr("4 días siempre gana", "4 days always wins"), tr("Resultados parecidos", "Similar results"), tr("2 días no sirve", "2 days doesn't work")), 1,
                    tr("Con el volumen igualado, la frecuencia casi no cambia los resultados.", "With volume matched, frequency barely changes the results.")),
            )),

        Tutorial("progresar", "📈", tr("Cómo progresar", "How to progress"), tr("Sobrecarga, técnica igual y cuándo subir peso", "Overload, consistent form and when to add weight"),
            listOf(
                Lesson(tr("Sobrecarga progresiva", "Progressive overload"),
                    tr("El cuerpo se adapta a lo que le pides. Para seguir mejorando, con el tiempo haz un poco más: más repeticiones o más peso. ", "Your body adapts to what you ask of it. To keep improving, over time do a bit more: more reps or more weight. ") +
                        tr("Las dos formas funcionan.", "Both ways work."),
                    important = tr("Sin sobrecarga progresiva no hay progreso: si siempre haces el mismo peso y las mismas reps, tu cuerpo deja de cambiar.", "Without progressive overload there's no progress: if you always do the same weight and reps, your body stops changing."),
                    concepts = listOf("sobrecarga"), papers = listOf(Papers.ACSM, Papers.PLOTKIN)),
                Lesson(tr("Estandariza tu técnica", "Standardize your form"),
                    tr("Tus números solo dicen la verdad si comparas lo mismo contra lo mismo: el mismo ejercicio, la misma máquina, ", "Your numbers only tell the truth if you compare like with like: the same exercise, the same machine, ") +
                        tr("el mismo asiento y agarre, el mismo recorrido, la misma velocidad y un descanso parecido.", "the same seat and grip, the same range of motion, the same speed and a similar rest."),
                    important = tr("Si cambias de máquina, bajas a medias o rebotas, harás \"más\" pero no serás más fuerte. Mantén todo igual y solo cambia el peso o las reps.", "If you switch machines, do half reps or bounce, you'll do “more” but you won't be stronger. Keep everything the same and only change the weight or reps."),
                    demo = Demo.TECHNIQUE, concepts = listOf("tecnica")),
                Lesson(tr("Cuándo subir el peso", "When to add weight"),
                    tr("Trabaja dentro de un rango (por ejemplo, 8–12). Sube repeticiones con el mismo peso; cuando llegues al máximo del rango ", "Work within a range (for example, 8–12). Add reps with the same weight; when you reach the top of the range ") +
                        tr("en todas las series, sube el peso un poquito y vuelve al mínimo. Prueba el ejemplo:", "on every set, add a little weight and go back to the bottom. Try the example:"),
                    demo = Demo.PROGRESSION, concepts = listOf("subir_peso", "rango_reps"), papers = listOf(Papers.ACSM)),
                Lesson(tr("Qué tan cerca del fallo", "How close to failure"),
                    tr("Quedarte a 1–3 repeticiones del fallo es una buena regla. No hace falta llegar al fallo en cada serie.", "Stopping 1–3 reps short of failure is a good rule. You don't need to reach failure on every set."),
                    concepts = listOf("rir_rpe", "fallo"), papers = listOf(Papers.ROBINSON, Papers.ZOURDOS)),
                Lesson(tr("Drop sets y técnicas avanzadas", "Drop sets and advanced techniques"),
                    tr("Los drop sets ahorran tiempo y dan un crecimiento parecido al de las series normales. Pero el peso y las reps del drop ", "Drop sets save time and give growth similar to normal sets. But the drop's weight and reps ") +
                        tr("dependen de qué tan cansado quedaste, y eso cambia cada día.", "depend on how tired you were, and that changes every day."),
                    important = tr("Las técnicas avanzadas (drop sets, al fallo en todo, superseries del mismo músculo) hacen difícil medir si progresas. ", "Advanced techniques (drop sets, everything to failure, same-muscle supersets) make it hard to measure whether you're progressing. ") +
                        tr("Si las usas, hazlas siempre igual y solo al final, y mide tu progreso con tus series normales. Rupio no usa los drop sets para decidir cuándo subir.", "If you use them, always do them the same way and only at the end, and measure your progress with your normal sets. Rupio doesn't use drop sets to decide when to go up."),
                    demo = Demo.DROP, concepts = listOf("drop_set", "superserie"), papers = listOf(Papers.COLEMAN, Papers.SODAL)),
                Lesson(tr("Si te estancas", "If you stall"),
                    tr("Si llevas dos sesiones sin llegar al mínimo de tu rango, baja alrededor de 10 % y vuelve a subir con buena técnica. ", "If you've missed the bottom of your range for two sessions, drop about 10% and build back up with good form. ") +
                        tr("Duerme bien y come suficiente: también cuentan.", "Sleep well and eat enough: they count too."),
                    concepts = listOf("descarga")),
            ),
            quiz = listOf(
                Quiz(tr("Rango 8–12. Hiciste 12, 12 y 12 con 100 lb. ¿Qué toca?", "Range 8–12. You did 12, 12 and 12 with 100 lb. What's next?"), listOf(tr("Repetir 100 lb", "Repeat 100 lb"), tr("Subir un poco el peso y buscar 8", "Add a little weight and aim for 8"), tr("Bajar el peso", "Lower the weight")), 1,
                    tr("Completaste el máximo del rango en todas las series: sube el peso y vuelve al mínimo.", "You hit the top of the range on every set: add weight and go back to the bottom.")),
                Quiz(tr("Hoy usaste otra máquina de press y moviste 20 lb más. ¿Progresaste?", "Today you used a different press machine and moved 20 lb more. Did you progress?"), listOf(tr("Sí, seguro", "Yes, for sure"), tr("No se puede saber: cambiaste el ejercicio", "There's no way to know: you changed the exercise"), tr("Sí, si sudaste más", "Yes, if you sweated more")), 1,
                    tr("Cada máquina pesa distinto. Para comparar, usa siempre la misma.", "Every machine weighs differently. To compare, always use the same one.")),
                Quiz(tr("¿Por qué los drop sets complican medir el progreso?", "Why do drop sets make progress hard to measure?"), listOf(tr("Porque no sirven", "Because they don't work"), tr("Porque su peso y reps dependen del cansancio de la serie anterior", "Because their weight and reps depend on how tired the previous set left you"), tr("Porque se olvidan", "Because people forget them")), 1,
                    tr("Funcionan para crecer, pero sus números varían mucho; mide el progreso con tus series normales.", "They work for growth, but their numbers vary a lot; measure progress with your normal sets.")),
            )),

        Tutorial("graficas", "📊", tr("Entender tus gráficas", "Understanding your charts"), tr("Qué significa cada gráfica de Progreso", "What each chart in Progress means"),
            listOf(
                Lesson(tr("Volumen por semana o mes", "Volume per week or month"),
                    tr("Cada barra es la suma de peso × repeticiones de tus series (sin calentamiento). Toca una barra para ver el número exacto, ", "Each bar is the sum of weight × reps of your sets (no warm-ups). Tap a bar to see the exact number, ") +
                        tr("cuántos entrenos y cuántas series hubo. Arriba eliges el periodo: semana, mes, 3 meses, año o histórico.", "how many workouts and how many sets there were. At the top you choose the period: week, month, 3 months, year or all time."),
                    important = tr("Compara periodos parecidos y con la misma rutina. Si cambias ejercicios, el volumen cambia aunque no seas más fuerte.", "Compare similar periods with the same routine. If you change exercises, volume changes even if you're not stronger."),
                    demo = Demo.VOLUME_CHART, concepts = listOf("volumen", "tecnica")),
                Lesson(tr("Series por músculo a la semana", "Sets per muscle per week"),
                    tr("Cada barra dice cuántas series fuertes le diste a ese músculo por semana. La rayita marca 10 series: a partir de ahí se suele crecer más. ", "Each bar tells how many hard sets you gave that muscle per week. The line marks 10 sets: from there you usually grow more. ") +
                        tr("Los músculos que ayudan (secundarios) cuentan como media serie.", "Helper (secondary) muscles count as half a set."),
                    demo = Demo.MUSCLE_CHART, concepts = listOf("series_semana", "musculos"), papers = listOf(Papers.SCHOENFELD, Papers.PELLAND)),
                Lesson(tr("Constancia", "Consistency"),
                    tr("Cada cuadrito es un día; más azul = más series ese día. Las columnas son semanas. Arriba ves tu racha de semanas seguidas entrenando.", "Each square is a day; more blue = more sets that day. The columns are weeks. At the top you see your streak of weeks in a row training."),
                    demo = Demo.HEATMAP, concepts = listOf("constancia")),
                Lesson(tr("Fuerza estimada (1RM)", "Estimated strength (1RM)"),
                    tr("La línea muestra lo máximo que podrías levantar una vez, calculado con tu mejor serie de cada sesión. ", "The line shows the most you could lift once, calculated from your best set each session. ") +
                        tr("Sirve para ver si te haces más fuerte aunque cambies el peso y las reps. Arrastra el dedo para ver cada día.", "It shows whether you're getting stronger even if you change the weight and reps. Drag your finger to see each day."),
                    important = tr("Es una estimación: es más confiable con series de menos de 10 repeticiones y con la misma técnica.", "It's an estimate: it's more reliable with sets under 10 reps and the same form."),
                    demo = Demo.E1RM_CHART, concepts = listOf("1rm"), papers = listOf(Papers.LESUER)),
                Lesson(tr("Peso corporal", "Body weight"),
                    tr("Los puntos son el peso de cada día; la línea azul es la tendencia. Fíjate en la línea, no en los puntos: ", "The dots are each day's weight; the blue line is the trend. Look at the line, not the dots: ") +
                        tr("un día alto por agua o sal casi no la mueve. Abajo ves tu ritmo en % por semana.", "a high day from water or salt barely moves it. Below you see your pace in % per week."),
                    demo = Demo.WEIGHT_CHART, concepts = listOf("peso_corporal")),
            ),
            quiz = listOf(
                Quiz(tr("En la gráfica de peso, ¿qué miras para saber si bajas?", "On the weight chart, what do you look at to know if you're losing?"), listOf(tr("El punto de hoy", "Today's dot"), tr("La línea de tendencia", "The trend line"), tr("El punto más bajo", "The lowest dot")), 1,
                    tr("El peso de un día varía por agua; la tendencia muestra el cambio real.", "One day's weight varies with water; the trend shows the real change.")),
                Quiz(tr("La rayita en \"Series por músculo\" marca…", "The line on “Sets per muscle” marks…"), listOf(tr("10 series por semana", "10 sets per week"), tr("El máximo permitido", "The maximum allowed"), tr("Tu récord", "Your PR")), 0,
                    tr("10 o más series por semana se asocian con más crecimiento.", "10 or more sets per week are linked to more growth.")),
            ),
            action = TutorialAction.OPEN_STATS),

        Tutorial("nutricion", "🥗", tr("Nutrición y peso corporal", "Nutrition and body weight"), tr("Déficit, superávit y por qué la dieta importa", "Deficit, surplus and why diet matters"),
            listOf(
                Lesson(tr("Calorías y mantenimiento", "Calories and maintenance"),
                    tr("Las calorías son la energía de la comida. Tu cuerpo gasta energía todo el día. Si comes lo mismo que gastas, tu peso se queda igual: ", "Calories are the energy in food. Your body burns energy all day. If you eat the same as you burn, your weight stays the same: ") +
                        tr("eso es comer en mantenimiento. Prueba el ejemplo:", "that's eating at maintenance. Try the example:"),
                    demo = Demo.ENERGY, concepts = listOf("calorias")),
                Lesson(tr("Déficit y superávit", "Deficit and surplus"),
                    tr("Déficit calórico: comer menos de lo que gastas → bajas de peso. Superávit calórico: comer más de lo que gastas → subes de peso. ", "Calorie deficit: eating less than you burn → you lose weight. Calorie surplus: eating more than you burn → you gain weight. ") +
                        tr("Con un superávit pequeño y entrenando fuerza, parte de lo que subes es músculo.", "With a small surplus and strength training, part of what you gain is muscle."),
                    concepts = listOf("deficit", "superavit"), papers = listOf(Papers.HELMS_DIET, Papers.IRAKI)),
                Lesson(tr("Bajar de peso solo con ejercicio es difícil", "Losing weight with exercise alone is hard"),
                    tr("Entrenar quema menos calorías de lo que parece, y es fácil compensarlas comiendo un poco más. ", "Training burns fewer calories than it seems, and it's easy to make up for them by eating a bit more. ") +
                        tr("El Colegio Americano de Medicina del Deporte encontró que menos de 150 minutos de actividad por semana casi no bajan el peso, ", "The American College of Sports Medicine found that less than 150 minutes of activity per week barely lowers weight, ") +
                        tr("y más de 150 minutos solo bajan unos 2–3 kg. Las pérdidas importantes se ven al combinar dieta y ejercicio.", "and more than 150 minutes only lowers it by about 2–3 kg. Significant losses show up when you combine diet and exercise."),
                    important = tr("Si tu objetivo es bajar de peso, necesitas cuidar tu alimentación (déficit calórico). El ejercicio ayuda, pero solo no basta.", "If your goal is to lose weight, you need to watch what you eat (calorie deficit). Exercise helps, but on its own it isn't enough."),
                    concepts = listOf("deficit"), papers = listOf(Papers.DONNELLY)),
                Lesson(tr("Para verte definido: déficit", "To look lean: deficit"),
                    tr("Para que se noten los músculos (definición), normalmente hay que bajar grasa, y eso requiere un déficit. ", "For your muscles to show (definition), you usually need to lose fat, and that requires a deficit. ") +
                        tr("Entrenar fuerza mientras estás en déficit ayuda a conservar el músculo. Un ritmo de 0.5–1 % de tu peso por semana ayuda a no perder músculo ni fuerza.", "Strength training while in a deficit helps preserve muscle. A pace of 0.5–1% of your weight per week helps you keep muscle and strength."),
                    important = tr("En temas de estética, a la mayoría le conviene más un déficit calórico para definir. El superávit es para cuando el objetivo es ganar tamaño.", "For looks, most people benefit more from a calorie deficit to get lean. A surplus is for when the goal is to gain size."),
                    concepts = listOf("deficit", "superavit"), papers = listOf(Papers.HELMS_DIET)),
                Lesson(tr("Proteína", "Protein"),
                    tr("Comer suficiente proteína ayuda a conservar y ganar músculo. Para la mayoría, unos 1.6 g por kilo de peso al día es suficiente; ", "Eating enough protein helps you keep and build muscle. For most people, about 1.6 g per kilo of body weight per day is enough; ") +
                        tr("comer más no mostró beneficio extra.", "eating more showed no extra benefit."),
                    concepts = listOf("proteina"), papers = listOf(Papers.MORTON)),
                Lesson(tr("Mide con tu tendencia de peso", "Measure with your weight trend"),
                    tr("No necesitas contar todo para saber dónde estás: registra tu peso en Rupio y mira la tendencia. Si baja, estás en déficit; ", "You don't need to count everything to know where you are: log your weight in Rupio and look at the trend. If it goes down, you're in a deficit; ") +
                        tr("si está estable, en mantenimiento; si sube, en superávit. Ajusta tu comida poco a poco según el ritmo que buscas.", "if it's stable, at maintenance; if it goes up, in a surplus. Adjust your food little by little based on the pace you want."),
                    important = tr("Esto es información general, no un plan médico. Si tienes alguna condición de salud, estás embarazada o has tenido problemas con la comida, consulta a un profesional.", "This is general information, not a medical plan. If you have a health condition, are pregnant or have had problems with food, see a professional."),
                    demo = Demo.WEIGHT_CHART, concepts = listOf("peso_corporal")),
            ),
            quiz = listOf(
                Quiz(tr("Quieres bajar de peso. ¿Qué es lo más importante?", "You want to lose weight. What matters most?"), listOf(tr("Entrenar más horas", "Training more hours"), tr("Comer menos de lo que gastas (déficit)", "Eating less than you burn (deficit)"), tr("Hacer drop sets", "Doing drop sets")), 1,
                    tr("Sin déficit calórico no se baja de peso; el ejercicio ayuda, pero solo no basta.", "Without a calorie deficit you don't lose weight; exercise helps, but on its own it isn't enough.")),
                Quiz(tr("Tu tendencia de peso lleva 3 semanas igual. Estás en…", "Your weight trend has been flat for 3 weeks. You're at…"), listOf(tr("Déficit", "Deficit"), tr("Mantenimiento", "Maintenance"), tr("Superávit", "Surplus")), 1,
                    tr("Si el peso no cambia, comes lo mismo que gastas.", "If your weight doesn't change, you're eating the same as you burn.")),
                Quiz(tr("Para definir sin perder músculo, un ritmo razonable es…", "To get lean without losing muscle, a reasonable pace is…"), listOf(tr("0.5–1 % de tu peso por semana", "0.5–1% of your weight per week"), tr("3 kg por semana", "3 kg per week"), tr("No comer", "Not eating")), 0,
                    tr("Bajar despacio y entrenar fuerza ayuda a conservar el músculo.", "Losing slowly and strength training helps preserve muscle.")),
            ),
            action = TutorialAction.LOG_WEIGHT),
    ) }
    val all: List<Tutorial> get() = cached.get()

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
