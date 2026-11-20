package com.rupio.app.domain

import com.rupio.app.core.tr

/**
 * Apoyo voluntario: niveles de PAGO ÚNICO (de por vida, sin suscripción). Todos quitan el mensaje de apoyo;
 * cambian solo los adornos. Nadie que no pague pierde funciones: todo Rupio es gratis.
 *
 * Los productos se crean en Play Console → Monetizar → Productos → Productos integrados en la app, con estos IDs.
 * Los precios reales los pone Google Play (en la moneda del usuario); [fallbackPrice] solo se muestra si Play no
 * respondió (sin internet o app instalada fuera de Play).
 *
 * v2.0: más recompensas en cada nivel (marco del avatar, más colores, íconos plateado y Aurora, confeti al terminar).
 * Cada recompensa dice de qué nivel viene, para que se entienda que es un "gracias" por pagar (ver [RewardCopy]).
 */
enum class SupportTier(val productId: String, val rank: Int, val emoji: String, val fallbackPrice: String) {
    CAFE("rupio_apoyo_cafe", 1, "☕", "$49 MXN"),
    LICUADO("rupio_apoyo_licuado", 2, "💪", "$99 MXN"),
    FAN("rupio_apoyo_fan", 3, "🏆", "$199 MXN"),
    /**
     * v1.8: el nivel más alto. Tu nombre en el muro de agradecimientos (orden por aportes) y todo lo de Fan.
     * Es CONSUMIBLE (se puede volver a comprar para sumar puntos): Google Play no lo recuerda, lo recuerda el servidor
     * (tabla wall_entries). Ver db/migrations/007_supporters_wall.sql.
     */
    MECENAS("rupio_mecenas", 4, "🌟", "$499 MXN");

    val title: String get() = when (this) {
        CAFE -> tr("Un café", "A coffee")
        LICUADO -> tr("Un licuado", "A protein shake")
        FAN -> tr("Fan de Rupio", "Rupio fan")
        MECENAS -> tr("Mecenas", "Patron")
    }

    /** Texto de la insignia: nivel · metal. */
    val badge: String get() = when (this) {
        CAFE -> tr("Café · Bronce", "Coffee · Bronze")
        LICUADO -> tr("Licuado · Plata", "Shake · Silver")
        FAN -> tr("Fan · Oro", "Fan · Gold")
        MECENAS -> tr("Mecenas · Aurora", "Patron · Aurora")
    }

    /** La insignia y el marco del avatar de este nivel (cada nivel reemplaza los del anterior). */
    val badgePerks: List<String> get() = when (this) {
        CAFE -> listOf(tr("Insignia Bronce ☕ en tu perfil y en las ligas", "Bronze ☕ badge on your profile and in leagues"),
            tr("Marco Bronce en tu avatar", "Bronze ring around your avatar"))
        LICUADO -> listOf(tr("Insignia y marco Plata 💪", "Silver 💪 badge and avatar ring"))
        FAN -> listOf(tr("Insignia y marco Oro 🏆 animados", "Animated gold 🏆 badge and avatar ring"))
        MECENAS -> listOf(tr("Insignia y marco Aurora 🌟 animados", "Animated Aurora 🌟 badge and avatar ring"))
    }

    /** Lo demás que suma este nivel (se acumula con los de abajo). */
    val extraPerks: List<String> get() = when (this) {
        CAFE -> listOf(tr("Color rosa para la app", "Pink color for the app"))
        LICUADO -> listOf(tr("3 colores más: turquesa, lila y plata", "3 more colors: teal, lilac and silver"),
            tr("Ícono plateado de la app", "Silver app icon"))
        FAN -> listOf(tr("Color dorado exclusivo e ícono dorado", "Exclusive gold color and gold app icon"),
            tr("Confeti al terminar cada entreno", "Confetti after every workout"))
        MECENAS -> listOf(tr("Color e ícono Aurora exclusivos", "Exclusive Aurora color and app icon"),
            tr("Tu nombre en el muro de agradecimientos", "Your name on the thank-you wall"),
            tr("Subes de lugar con cada aporte extra", "Move up the wall with every extra contribution"))
    }

    /** Lo que da este nivel, como se muestra al elegirlo ("Todo lo de…" resume los de abajo). */
    val perks: List<String> get() = (if (this == CAFE) listOf(NO_PROMPT_PERK) else {
        val prev = entries[ordinal - 1]
        listOf(tr("Todo lo de ${prev.emoji} ${prev.title}", "Everything in ${prev.emoji} ${prev.title}"))
    }) + badgePerks + extraPerks

    /** Todo lo que tiene quien llegó a este nivel, sin repetir insignias de niveles de abajo (para "Tus recompensas"). */
    val allPerks: List<String> get() = listOf(NO_PROMPT_PERK) + badgePerks + entries.filter { it.rank <= rank }.flatMap { it.extraPerks }

    /** Insignia y marco animados, dorado, ícono dorado y confeti: desde Fan. */
    val hasGoldPerks: Boolean get() = rank >= FAN.rank

    companion object {
        fun of(productId: String): SupportTier? = entries.firstOrNull { it.productId == productId }
        /** El nivel más alto que tiene (se pueden comprar varios: el mayor decide los adornos). */
        fun best(owned: Collection<SupportTier>): SupportTier? = owned.maxByOrNull { it.rank }
        /** El de en medio se recomienda (efecto compromiso, Simonson 1989). */
        val RECOMMENDED = LICUADO
        private val NO_PROMPT_PERK: String get() = tr("Se quita el mensaje de apoyo", "The support message goes away")
    }
}

/**
 * v2.1 · Subir de nivel pagando solo la diferencia. Google Play no cobra diferencias entre productos únicos, así que
 * cada mejora es su propio producto (pago único, no consumible) y solo se ofrece a quien ya tiene el nivel [from].
 * Comprarla da el nivel [to] mientras [from] siga activo (si se reembolsa el nivel de abajo, la mejora sola no cuenta).
 * Mecenas no tiene mejora: es la entrada al muro y sus puntos son su precio de lista (los decide el servidor).
 * Los productos se crean en Play Console con estos IDs (docs/PAGOS_PLAY.md); si aún no existen, se cobra el nivel completo.
 */
enum class SupportUpgrade(val productId: String, val from: SupportTier, val to: SupportTier, val fallbackPrice: String) {
    CAFE_LICUADO("rupio_mejora_cafe_licuado", SupportTier.CAFE, SupportTier.LICUADO, "$50 MXN"),
    CAFE_FAN("rupio_mejora_cafe_fan", SupportTier.CAFE, SupportTier.FAN, "$150 MXN"),
    LICUADO_FAN("rupio_mejora_licuado_fan", SupportTier.LICUADO, SupportTier.FAN, "$100 MXN");

    companion object {
        fun of(productId: String): SupportUpgrade? = entries.firstOrNull { it.productId == productId }
        val productIds: Set<String> = entries.map { it.productId }.toSet()

        /** La mejora para ir de tu mejor nivel [best] a [target], si existe. */
        fun between(best: SupportTier?, target: SupportTier): SupportUpgrade? = entries.firstOrNull { it.from == best && it.to == target }

        /**
         * Niveles que tienes con tus compras: los niveles comprados más los que abren tus mejoras, siempre que tengas el
         * nivel de abajo de cada una (se repite hasta que no cambie: Café + Café→Licuado + Licuado→Fan = Fan).
         */
        fun effective(tiers: Set<SupportTier>, upgrades: Set<SupportUpgrade>): Set<SupportTier> {
            val out = tiers.toMutableSet()
            var changed = true
            while (changed) {
                changed = false
                for (u in upgrades) if (u.from in out && out.add(u.to)) changed = true
            }
            return out
        }
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
    val TITLE: String get() = tr("Muro de agradecimientos", "Thank-you wall")
    val INTRO: String get() = tr("Estas personas hacen posible que Rupio siga gratis y sin anuncios. Gracias, de verdad.",
        "These people keep Rupio free and ad-free. Thank you, truly.")
    /** v2.1: el muro se baja con la cuenta; sin ella no hay lista que mostrar. */
    val INTRO_NO_LIST: String get() = tr("Aquí aparecen, con su nombre, las personas que apoyan Rupio como Mecenas. Gracias a ellas sigue gratis y sin anuncios.",
        "People who support Rupio as patrons appear here by name. Thanks to them it stays free and ad-free.")
    val ORDER: String get() = tr("El orden va por la suma de aportes de cada quien; si hay empate, primero quien llegó antes.",
        "Order is by each person's total contributions; on a tie, whoever joined first goes first.")
    val CONSENT: String get() = tr("Acepto que este nombre se muestre a todas las personas que usan Rupio, en el muro de agradecimientos, " +
        "con mi lugar y la fecha en que llegué. Puedo cambiarlo o quitarme cuando quiera.",
        "I agree that this name is shown to everyone who uses Rupio on the thank-you wall, with my place and the date I joined. " +
            "I can change it or remove myself at any time.")
    val NEEDS_ACCOUNT: String get() = tr("Para aparecer en el muro necesitas una cuenta de Rupio: así tu lugar se guarda aunque cambies de teléfono.",
        "To appear on the wall you need a Rupio account, so your place is kept even if you change phones.")
    fun rank(rank: Int) = tr("Lugar #$rank", "Place #$rank")
    fun toPass(points: Int, rank: Int) = tr("Te faltan $points ${if (points == 1) "punto" else "puntos"} para pasar al lugar #$rank.",
        "$points more ${if (points == 1) "point" else "points"} to reach place #$rank.")
    val POINTS_NOTE: String get() = tr("1 punto = 1 peso del precio de lista, aunque pagues en otra moneda.",
        "1 point = 1 Mexican peso of the list price, even if you pay in another currency.")
    /** v2.0: la vista previa del muro en Inicio, Progreso y Perfil (lo hace visible sin abrir Apoya Rupio). */
    fun teaser(total: Int) = if (total <= 0) tr("Rupio es gratis y sin anuncios gracias a quienes lo apoyan.",
        "Rupio is free and ad-free thanks to the people who support it.")
        else tr("Rupio es gratis y sin anuncios gracias a ${if (total == 1) "esta persona" else "estas $total personas"}.",
        "Rupio is free and ad-free thanks to ${if (total == 1) "this person" else "these $total people"}.")
}

/**
 * Colores de acento. En el tema oscuro son claros (texto oscuro encima, ≥ 4.5:1; el azul da 6:1 y los demás más de
 * 7:1); en el claro son el mismo tono oscurecido (texto blanco encima y ≥ 5:1 sobre todas las tarjetas). Se verifica en
 * las pruebas. Se evitan el verde (= serie completada), el ámbar (= sube peso) y el rojo (= peligro).
 */
enum class Accent(val id: String, val argb: Long, val lightArgb: Long, val minTier: SupportTier?) {
    AZUL("azul", 0xFF5B8CFF, 0xFF2850C4, null),
    ROSA("rosa", 0xFFFF7AB4, 0xFFAE1450, SupportTier.CAFE),
    TURQUESA("turquesa", 0xFF22C3D6, 0xFF006A7A, SupportTier.LICUADO),
    LILA("lila", 0xFFC59BFF, 0xFF7036C2, SupportTier.LICUADO),
    /** v2.0: el metal del nivel Licuado. */
    PLATA("plata", 0xFFC3CEDD, 0xFF4F5D70, SupportTier.LICUADO),
    DORADO("dorado", 0xFFF2C14E, 0xFF765300, SupportTier.FAN),
    /** v2.0: la orquídea del metal Aurora, exclusivo de Mecenas. */
    AURORA("aurora", 0xFFF0A5E8, 0xFF7F3592, SupportTier.MECENAS);

    val label: String get() = when (this) {
        AZUL -> tr("Azul", "Blue"); ROSA -> tr("Rosa", "Pink"); TURQUESA -> tr("Turquesa", "Teal"); LILA -> tr("Lila", "Lilac")
        PLATA -> tr("Plata", "Silver"); DORADO -> tr("Dorado", "Gold"); AURORA -> "Aurora"
    }

    fun unlockedBy(best: SupportTier?): Boolean = minTier == null || (best != null && best.rank >= minTier.rank)

    companion object {
        fun of(id: String?): Accent = entries.firstOrNull { it.id == id } ?: AZUL
        /** El acento elegido solo se aplica si su nivel sigue activo (p. ej. tras un reembolso vuelve al azul). */
        fun effective(chosen: Accent, best: SupportTier?): Accent = if (chosen.unlockedBy(best)) chosen else AZUL
    }
}

/**
 * v2.0: íconos de la app. Cada uno es un "alias" de la pantalla principal en el manifiesto (ver AppIcon); antes solo
 * existía el dorado. [alias] es el nombre de CLASE del componente: sale del namespace (com.rupio.app), no del id de la
 * app (mx.rupio.app desde v2.1). ComponentName(context, alias) pone el id de la app por su cuenta.
 */
enum class AppIconStyle(val id: String, val alias: String, val minTier: SupportTier?) {
    NORMAL("normal", "com.rupio.app.LauncherDefault", null),
    PLATA("plata", "com.rupio.app.LauncherSilver", SupportTier.LICUADO),
    ORO("oro", "com.rupio.app.LauncherGold", SupportTier.FAN),
    AURORA("aurora", "com.rupio.app.LauncherAurora", SupportTier.MECENAS);

    val label: String get() = when (this) {
        NORMAL -> tr("Normal", "Default"); PLATA -> tr("Plateado", "Silver"); ORO -> tr("Dorado", "Gold"); AURORA -> "Aurora"
    }

    fun unlockedBy(best: SupportTier?): Boolean = minTier == null || (best != null && best.rank >= minTier.rank)

    companion object {
        fun of(id: String?): AppIconStyle = entries.firstOrNull { it.id == id } ?: NORMAL
        fun effective(chosen: AppIconStyle, best: SupportTier?): AppIconStyle = if (chosen.unlockedBy(best)) chosen else NORMAL
    }
}

/**
 * v2.0: textos que explican de dónde salen las recompensas. Antes la insignia y los colores aparecían sin decir que
 * eran por pagar; ahora cada uno dice su nivel ("Lo tienes por apoyar con ☕ Un café") y lo bloqueado dice qué nivel lo
 * abre. Se repite la idea clave: todo Rupio es gratis, esto solo adorna.
 */
object RewardCopy {
    val TITLE: String get() = tr("Recompensas por apoyar", "Supporter rewards")
    val EXPLAIN: String get() = tr("Todo Rupio es gratis. Estas recompensas son adornos para dar las gracias a quien paga: no desbloquean funciones.",
        "All of Rupio is free. These rewards are small extras to thank people who pay; they don't unlock any features.")
    fun yours(tier: SupportTier) = tr("Lo tienes por apoyar Rupio con ${tier.emoji} ${tier.title}. ¡Gracias!",
        "You have this for supporting Rupio with ${tier.emoji} ${tier.title}. Thank you!")
    fun unlocks(tier: SupportTier) = tr("Se desbloquea con ${tier.emoji} ${tier.title}", "Unlocks with ${tier.emoji} ${tier.title}")
    /** v2.1: cada nivel es un producto aparte en Google Play (se paga completo) y lo que ya tienes se queda. */
    val UPGRADE_NOTE: String get() = tr("Cada nivel es otro pago único, por su precio completo. Lo que ya tienes se queda.",
        "Each level is a separate one-time payment at its full price. You keep what you already have.")
    /** v2.1: con los productos de mejora (SupportUpgrade) se paga solo la diferencia. */
    fun upgradeDiff(best: SupportTier) = tr("Ya tienes ${best.emoji} ${best.title}: pagas solo la diferencia. Es otro pago único y lo que ya tienes se queda.",
        "You already have ${best.emoji} ${best.title}: you only pay the difference. It's another one-time payment and you keep what you have.")
    fun badgeTitle(tier: SupportTier) = tr("Insignia ${tier.badge}", "${tier.badge} badge")
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

    /**
     * v2.0: la vista previa del muro en Inicio sale cuando ya hay costumbre (desde el 3.er entreno, como el mensaje) y
     * hasta que la persona la cierre. No interrumpe: va al final de la lista, debajo de las rutinas.
     */
    fun showWallTeaserOnHome(finishedWorkouts: Int, dismissed: Boolean): Boolean =
        !dismissed && finishedWorkouts >= FIRST_PROMPT_AT_WORKOUT
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

    val TITLE: String get() = tr("Hola, soy $DEV_NAME 👋", "Hi, I'm $DEV_NAME 👋")
    fun story(workouts: Int, lifted: String): String = tr(
        "Hago Rupio yo solo, en mis ratos libres. Ya llevas $workouts ${if (workouts == 1) "entrenamiento" else "entrenamientos"} " +
            "y $lifted levantados con la app, y eso me alegra muchísimo.",
        "I make Rupio on my own, in my spare time. You've logged $workouts ${if (workouts == 1) "workout" else "workouts"} " +
            "and lifted $lifted with the app, and that makes me really happy.")
    val ABOUT: String get() = tr("Hago Rupio yo solo, en mis ratos libres.", "I make Rupio on my own, in my spare time.")
    val ASK: String get() = tr("Rupio no tiene anuncios ni vende tus datos. Si te está sirviendo, puedes apoyarlo con un pago único: una sola vez, sin suscripción.",
        "Rupio has no ads and doesn't sell your data. If it's helping you, you can support it with a one-time payment: just once, no subscription.")
    val FREEDOM: String get() = tr("Tú decides: Rupio seguirá completo y gratis aunque no pagues.",
        "It's your call: Rupio stays complete and free even if you don't pay.")
    val NOT_NOW: String get() = tr("Ahora no", "Not now")
    fun cta(price: String) = tr("Apoyar con $price", "Support with $price")

    val THANKS_TITLE: String get() = tr("¡Gracias, de verdad! 🙌", "Thank you, truly! 🙌")
    val THANKS_BODY: String get() = tr("Con tu apoyo Rupio sigue siendo gratis y sin anuncios para todos. Me ayudas a seguir mejorándolo.",
        "Your support keeps Rupio free and ad-free for everyone, and helps me keep improving it.")
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
            v.length < MIN -> tr("Escribe al menos $MIN caracteres.", "Use at least $MIN characters.")
            v.length > MAX -> tr("Máximo $MAX caracteres.", "$MAX characters max.")
            LINK.containsMatchIn(v) -> tr("Sin enlaces ni correos.", "No links or emails.")
            v.any { it.isISOControl() } -> tr("Hay caracteres no válidos.", "It has characters that aren't allowed.")
            else -> null
        }
    }
}
