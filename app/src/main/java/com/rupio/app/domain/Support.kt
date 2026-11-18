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
        listOf("Se quita el mensaje de apoyo", "Insignia 🏆 animada", "Todos los colores, con dorado exclusivo", "Ícono dorado de la app")),
    /**
     * v1.8: el nivel más alto. Tu nombre en el muro de agradecimientos (orden por aportes) y todo lo de Fan.
     * Es CONSUMIBLE (se puede volver a comprar para sumar puntos): Google Play no lo recuerda, lo recuerda el servidor
     * (tabla wall_entries). Ver db/migrations/007_supporters_wall.sql.
     */
    MECENAS("rupio_mecenas", 4, "🌟", "Mecenas", "$499 MXN", "Mecenas de Rupio",
        listOf("Todo lo de Fan de Rupio", "Tu nombre en el muro de agradecimientos", "Subes de lugar con cada aporte extra"));

    /** Insignia animada, dorado e ícono dorado: desde Fan. */
    val hasGoldPerks: Boolean get() = rank >= FAN.rank

    companion object {
        fun of(productId: String): SupportTier? = entries.firstOrNull { it.productId == productId }
        /** El nivel más alto que tiene (se pueden comprar varios: el mayor decide los adornos). */
        fun best(owned: Collection<SupportTier>): SupportTier? = owned.maxByOrNull { it.rank }
        /** El de en medio se recomienda (efecto compromiso, Simonson 1989). */
        val RECOMMENDED = LICUADO
    }
}

/**
 * Compras del muro (consumibles). Los puntos son el precio de lista en pesos y los decide el SERVIDOR (tabla
 * wall_products); aquí solo se usan para mostrar. "rupio_mecenas" sirve para entrar y también como aporte grande.
 */
enum class WallBoost(val productId: String, val points: Int, val fallbackPrice: String) {
    SMALL("rupio_muro_99", 99, "$99 MXN"),
    MEDIUM("rupio_muro_249", 249, "$249 MXN"),
    BIG(SupportTier.MECENAS.productId, 499, "$499 MXN");

    companion object {
        fun of(productId: String): WallBoost? = entries.firstOrNull { it.productId == productId }
        /** Productos que se consumen (Mecenas incluido). */
        val productIds: Set<String> = entries.map { it.productId }.toSet()
        fun isWallProduct(productId: String) = productId in productIds

        /** El aporte más chico que te sube al lugar de arriba (o el más grande si ninguno alcanza). */
        fun toPass(toNext: Int?): WallBoost? = when {
            toNext == null || toNext <= 0 -> null
            else -> entries.sortedBy { it.points }.firstOrNull { it.points >= toNext } ?: BIG
        }
    }
}

/** Cómo se presenta el muro. Mismo tono que el resto: agradecer, sin presionar. */
object WallCopy {
    const val TITLE = "Muro de agradecimientos"
    const val INTRO = "Estas personas hacen posible que Rupio siga gratis y sin anuncios. Gracias, de verdad."
    const val ORDER = "El orden va por la suma de aportes de cada quien; si hay empate, primero quien llegó antes."
    const val CONSENT = "Acepto que este nombre se muestre a todas las personas que usan Rupio, en el muro de agradecimientos, " +
        "con mi lugar y la fecha en que llegué. Puedo cambiarlo o quitarme cuando quiera."
    const val NEEDS_ACCOUNT = "Para aparecer en el muro necesitas una cuenta de Rupio: así tu lugar se guarda aunque cambies de teléfono."
    fun rank(rank: Int) = "Lugar #$rank"
    fun toPass(points: Int, rank: Int) = "Te faltan $points ${if (points == 1) "punto" else "puntos"} para pasar al lugar #$rank."
    const val POINTS_NOTE = "1 punto = 1 peso del precio de lista, aunque pagues en otra moneda."
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

/** Nombre público del muro: mismas reglas que el servidor (rupio_wall_clean_name). Puro Kotlin: tiene pruebas. */
object WallName {
    const val MIN = 2
    const val MAX = 30
    private val LINK = Regex("(https?:|www\\.|\\.com|\\.mx|\\.net|@)", RegexOption.IGNORE_CASE)

    /** Espacios de más fuera (así se guarda). */
    fun clean(s: String): String = s.trim().replace(Regex("\\s+"), " ")

    /** Qué tiene de malo el nombre, o null si sirve. */
    fun problem(s: String): String? {
        val v = clean(s)
        return when {
            v.length < MIN -> "Escribe al menos $MIN caracteres."
            v.length > MAX -> "Máximo $MAX caracteres."
            LINK.containsMatchIn(v) -> "Sin enlaces ni correos."
            v.any { it.isISOControl() } -> "Hay caracteres no válidos."
            else -> null
        }
    }
}
