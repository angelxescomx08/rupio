package com.rupio.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.rupio.app.core.I18n
import com.rupio.app.core.tr

/**
 * Ligas semanales por volumen (servidor: db/migrations/008_ligas.sql). Puro Kotlin: tiene pruebas.
 *
 *  - Opcional: con cuenta, casilla de permiso y apodo (mismas reglas que el muro, [WallName]).
 *  - Cada semana (lunes a domingo) compites en un grupo de hasta 30 personas de tu misma liga.
 *  - Puntaje = volumen de la semana (lo suma el servidor), con tope de 50,000 kg por entreno.
 *  - Los 5 primeros suben si el grupo tiene 10 o más; los 5 últimos bajan si tiene 15 o más.
 *  - Pagar NO da puntos: la insignia de apoyo solo se muestra junto al nombre.
 */
object LeagueRules {
    const val GROUP_MAX = 30
    const val MOVE = 5
    const val UP_MIN_SIZE = 10
    const val DOWN_MIN_SIZE = 15
    const val CAP_KG = 50_000
    const val TIERS = 6

    private val names = listOf("Calentamiento", "Constante", "Sólido", "Fuerte", "Bestia", "Leyenda")
    private val namesEn = listOf("Warm-up", "Steady", "Solid", "Strong", "Beast", "Legend")

    fun tierName(tier: Int): String = (if (I18n.isEnglish) namesEn else names).getOrElse(tier - 1) { tr("Liga $tier", "League $tier") }

    /** ¿En qué zona va el lugar [rank] de un grupo de [size] con [up] lugares que suben y [down] que bajan? */
    fun zone(rank: Int, size: Int, up: Int, down: Int): LeagueZone = when {
        up > 0 && rank <= up -> LeagueZone.UP
        down > 0 && rank > size - down -> LeagueZone.DOWN
        else -> LeagueZone.STAY
    }

    /**
     * Kilos que te faltan para pasar al lugar [target] (1 = primero), con los kilos [kgByRank] del grupo en orden.
     * Con el mismo volumen gana quien empezó la semana antes, así que hay que superarlo por 1 kg. Null si ya vas ahí.
     */
    fun kgToReach(target: Int, myRank: Int, myKg: Double, kgByRank: List<Double>): Double? {
        if (target < 1 || myRank <= target) return null
        val theirs = kgByRank.getOrNull(target - 1) ?: return null
        return (theirs - myKg + 1).coerceAtLeast(1.0)
    }

    /** "16 – 22 nov" (o "28 dic – 3 ene" si cambia de mes). */
    fun weekLabel(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        val month = DateTimeFormatter.ofPattern("MMM", I18n.locale)
        val m1 = monday.format(month).trimEnd('.'); val m2 = sunday.format(month).trimEnd('.')
        // En inglés el mes va antes del día: "Nov 16 – 22", "Dec 28 – Jan 3".
        if (I18n.isEnglish) return if (m1 == m2) "$m1 ${monday.dayOfMonth} – ${sunday.dayOfMonth}" else "$m1 ${monday.dayOfMonth} – $m2 ${sunday.dayOfMonth}"
        return if (m1 == m2) "${monday.dayOfMonth} – ${sunday.dayOfMonth} $m2" else "${monday.dayOfMonth} $m1 – ${sunday.dayOfMonth} $m2"
    }

    /** Días que le quedan a la semana que empezó el lunes [monday], contando hoy (1 = termina hoy). */
    fun daysLeft(today: LocalDate, monday: LocalDate): Int = (7 - ChronoUnit.DAYS.between(monday, today)).toInt().coerceIn(1, 7)

    fun daysLeftText(today: LocalDate, monday: LocalDate): String = when (val d = daysLeft(today, monday)) {
        1 -> tr("Termina hoy", "Ends today")
        else -> tr("Quedan $d días", "$d days left")
    }
}

enum class LeagueZone { UP, STAY, DOWN }

/** Textos de las ligas. Mismo tono que el muro: invitar, sin presionar. */
object LeagueCopy {
    val TITLE: String get() = tr("Ligas semanales", "Weekly leagues")
    val INTRO: String get() = tr("Cada semana compites por volumen con hasta 30 personas de tu mismo nivel. Los 5 primeros suben de liga " +
        "y los 5 últimos bajan. Es opcional y puedes salirte cuando quieras.",
        "Each week you compete on volume with up to 30 people at your level. The top 5 move up a league " +
            "and the bottom 5 move down. It's optional and you can leave whenever you want.")
    val SCORE: String get() = tr("Cuenta el volumen de tus entrenos terminados de lunes a domingo (máximo 50,000 kg por entreno).",
        "It counts the volume of your finished workouts from Monday to Sunday (50,000 kg max per workout).")
    val PAY: String get() = tr("Pagar no da puntos: la insignia de apoyo solo se ve junto al nombre.",
        "Paying doesn't give points: the supporter badge only shows next to your name.")
    val CONSENT: String get() = tr("Acepto que este apodo, mi liga y mi volumen de la semana se muestren a las demás personas que participan " +
        "en las ligas. Puedo cambiar el apodo o salirme cuando quiera.",
        "I agree that this nickname, my league and my weekly volume are shown to the other people taking part " +
            "in the leagues. I can change the nickname or leave whenever I want.")
    val NEEDS_ACCOUNT: String get() = tr("Para competir en las ligas necesitas una cuenta de Rupio: tu volumen se suma en el servidor cuando se suben tus entrenos.",
        "To compete in leagues you need a Rupio account: your volume is added up on the server when your workouts are uploaded.")
    val NO_WORKOUT: String get() = tr("Termina un entreno esta semana para entrar a un grupo de tu liga.", "Finish a workout this week to join a group in your league.")
    val SYNC_NOTE: String get() = tr("Tus entrenos cuentan cuando se suben a la nube.", "Your workouts count once they're uploaded to the cloud.")
    val SMALL_GROUP: String get() = tr("Con 10 o más personas en el grupo, los 5 primeros suben; con 15 o más, los 5 últimos bajan.",
        "With 10 or more people in the group, the top 5 move up; with 15 or more, the bottom 5 move down.")
    val HIDDEN: String get() = tr("No estás participando. Tu historial de entrenos no cambia.", "You're not taking part. Your workout history doesn't change.")
}
