package com.rupio.app.domain

/**
 * Apoyo voluntario: tres niveles de PAGO ÚNICO (de por vida, sin suscripción). Todos quitan el mensaje de apoyo;
 * cambian solo los adornos. Nadie que no pague pierde funciones.
 *
 * Los productos se crean en Play Console → Monetizar → Productos → Productos integrados en la app, con estos IDs.
 * Los precios reales los pone Google Play (en la moneda del usuario); [fallbackPrice] solo se muestra si Play no
 * respondió (sin internet o app instalada fuera de Play).
 */
enum class SupportTier(
    val productId: String,
    val rank: Int,
    val emoji: String,
    val title: String,
    val fallbackPrice: String,
    val badge: String,
    val perks: List<String>,
) {
    CAFE("rupio_apoyo_cafe", 1, "☕", "Un café", "$49 MXN", "Apoyo · Café",
        listOf("Se quita el mensaje de apoyo", "Insignia ☕ en tu perfil")),
    LICUADO("rupio_apoyo_licuado", 2, "💪", "Un licuado", "$99 MXN", "Apoyo · Licuado",
        listOf("Se quita el mensaje de apoyo", "Insignia 💪 en tu perfil", "4 colores para la app")),
    FAN("rupio_apoyo_fan", 3, "🏆", "Fan de Rupio", "$199 MXN", "Fan de Rupio",
        listOf("Se quita el mensaje de apoyo", "Insignia 🏆 animada", "Todos los colores, con dorado exclusivo", "Ícono dorado de la app"));

    companion object {
        fun of(productId: String): SupportTier? = entries.firstOrNull { it.productId == productId }
        /** El nivel más alto que tiene (se pueden comprar varios: el mayor decide los adornos). */
        fun best(owned: Collection<SupportTier>): SupportTier? = owned.maxByOrNull { it.rank }
        /** El de en medio se recomienda (efecto compromiso, Simonson 1989). */
        val RECOMMENDED = LICUADO
    }
}

/**
 * Colores de acento. Todos claros para que el texto oscuro de los botones (OnPrimary) cumpla WCAG AA (≥ 4.5:1;
 * el azul da 6:1 y los demás más de 7:1) y se vean bien sobre negro (se verifica en las pruebas). Se evitan el verde (= serie completada), el ámbar (= sube peso) y el rojo (= peligro).
 */
enum class Accent(val id: String, val label: String, val argb: Long, val minTier: SupportTier?) {
    AZUL("azul", "Azul", 0xFF5B8CFF, null),
    TURQUESA("turquesa", "Turquesa", 0xFF22C3D6, SupportTier.LICUADO),
    ROSA("rosa", "Rosa", 0xFFFF7AB4, SupportTier.LICUADO),
    LILA("lila", "Lila", 0xFFC59BFF, SupportTier.LICUADO),
    DORADO("dorado", "Dorado", 0xFFF2C14E, SupportTier.FAN);

    fun unlockedBy(best: SupportTier?): Boolean = minTier == null || (best != null && best.rank >= minTier.rank)

    companion object {
        fun of(id: String?): Accent = entries.firstOrNull { it.id == id } ?: AZUL
        /** El acento elegido solo se aplica si su nivel sigue activo (p. ej. tras un reembolso vuelve al azul). */
        fun effective(chosen: Accent, best: SupportTier?): Accent = if (chosen.unlockedBy(best)) chosen else AZUL
    }
}

/**
 * Cuándo mostrar el mensaje de apoyo al terminar un entrenamiento. Puro Kotlin: tiene pruebas.
 *
 * Antes salía después de CADA entreno desde el 3.º: quien no apoya lo habría visto 1 720 veces con la historia de la
 * cuenta demo, y la FTC clasifica las peticiones repetidas ("nagging") como patrón engañoso. Ahora:
 *  - solo en hitos (entreno 3, 10, 25, 50, 100 y luego cada 100),
 *  - como máximo una vez cada 30 días,
 *  - y nunca más después de 3 "Ahora no". La fila "Apoya Rupio" de Perfil sigue siempre disponible.
 */
object SupportPolicy {
    /** Los primeros entrenos no se interrumpen: primero que la app les sirva (reciprocidad). */
    const val FIRST_PROMPT_AT_WORKOUT = 3
    /** Se espera a que vean su resumen y récords (buen ánimo → más disposición a ayudar; Isen & Levin 1972). */
    const val DELAY_AFTER_SUMMARY_MS = 1_400L
    const val MIN_DAYS_BETWEEN = 30
    const val MAX_DISMISSALS = 3
    private val MILESTONES = listOf(3, 10, 25, 50, 100)

    /** El hito más alto alcanzado con [finished] entrenos (0 si ninguno). */
    fun milestone(finished: Int): Int = when {
        finished < FIRST_PROMPT_AT_WORKOUT -> 0
        finished < 100 -> MILESTONES.last { it <= finished }
        else -> finished / 100 * 100
    }

    fun shouldPrompt(
        isSupporter: Boolean, finishedWorkouts: Int, workoutId: String, lastPromptedWorkoutId: String?,
        lastPromptedMilestone: Int = 0, lastPromptAtMs: Long = 0, dismissals: Int = 0, nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (isSupporter || dismissals >= MAX_DISMISSALS || workoutId == lastPromptedWorkoutId) return false
        val m = milestone(finishedWorkouts)
        if (m == 0 || m <= lastPromptedMilestone) return false
        return lastPromptAtMs == 0L || nowMs - lastPromptAtMs >= MIN_DAYS_BETWEEN * 86_400_000L
    }
}

/**
 * Textos del mensaje. Cada decisión tiene su estudio en docs/APOYO_CIENCIA.md:
 *  - Persona concreta que habla en primera persona (efecto de la víctima identificable, Kogut & Ritov 2005).
 *  - Recordar lo que la app ya le dio (reciprocidad: Regner & Barria 2009; Regner 2015).
 *  - Libertad explícita: "tú decides" (técnica "pero eres libre", Carpenter 2013), sin culpa ni vergüenza
 *    (el "confirmshaming" daña la confianza: Which? 2026).
 *  - Nada de "el más elegido" inventado: solo "Recomendado".
 */
object SupportCopy {
    /** Cámbialo si quieres firmar con otro nombre. */
    const val DEV_NAME = "Ángel"

    const val TITLE = "Hola, soy $DEV_NAME 👋"
    fun story(workouts: Int, lifted: String): String =
        "Hago Rupio yo solo, en mis ratos libres. Ya llevas $workouts ${if (workouts == 1) "entrenamiento" else "entrenamientos"} " +
            "y $lifted levantados con la app, y eso me alegra muchísimo."
    const val ASK = "Rupio no tiene anuncios ni vende tus datos. Si te está sirviendo, puedes apoyarlo con un pago único: una sola vez, sin suscripción."
    const val FREEDOM = "Tú decides: Rupio seguirá completo y gratis aunque no pagues."
    const val NOT_NOW = "Ahora no"
    fun cta(price: String) = "Apoyar con $price"

    const val THANKS_TITLE = "¡Gracias, de verdad! 🙌"
    const val THANKS_BODY = "Con tu apoyo Rupio sigue siendo gratis y sin anuncios para todos. Me ayudas a seguir mejorándolo."
    const val THANKS_SIGN = "— $DEV_NAME"
}
