package com.rupio.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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

    fun tierName(tier: Int): String = names.getOrElse(tier - 1) { "Liga $tier" }

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
        val month = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-MX"))
        val m1 = monday.format(month).trimEnd('.'); val m2 = sunday.format(month).trimEnd('.')
        return if (m1 == m2) "${monday.dayOfMonth} – ${sunday.dayOfMonth} $m2" else "${monday.dayOfMonth} $m1 – ${sunday.dayOfMonth} $m2"
    }

    /** Días que le quedan a la semana que empezó el lunes [monday], contando hoy (1 = termina hoy). */
    fun daysLeft(today: LocalDate, monday: LocalDate): Int = (7 - ChronoUnit.DAYS.between(monday, today)).toInt().coerceIn(1, 7)

    fun daysLeftText(today: LocalDate, monday: LocalDate): String = when (val d = daysLeft(today, monday)) {
        1 -> "Termina hoy"
        else -> "Quedan $d días"
    }
}

enum class LeagueZone { UP, STAY, DOWN }

/** Textos de las ligas. Mismo tono que el muro: invitar, sin presionar. */
object LeagueCopy {
    const val TITLE = "Ligas semanales"
    const val INTRO = "Cada semana compites por volumen con hasta 30 personas de tu mismo nivel. Los 5 primeros suben de liga " +
        "y los 5 últimos bajan. Es opcional y puedes salirte cuando quieras."
    const val SCORE = "Cuenta el volumen de tus entrenos terminados de lunes a domingo (máximo 50,000 kg por entreno)."
    const val PAY = "Pagar no da puntos: la insignia de apoyo solo se ve junto al nombre."
    const val CONSENT = "Acepto que este apodo, mi liga y mi volumen de la semana se muestren a las demás personas que participan " +
        "en las ligas. Puedo cambiar el apodo o salirme cuando quiera."
    const val NEEDS_ACCOUNT = "Para competir en las ligas necesitas una cuenta de Rupio: tu volumen se suma en el servidor cuando se suben tus entrenos."
    const val NO_WORKOUT = "Termina un entreno esta semana para entrar a un grupo de tu liga."
    const val SYNC_NOTE = "Tus entrenos cuentan cuando se suben a la nube."
    const val SMALL_GROUP = "Con 10 o más personas en el grupo, los 5 primeros suben; con 15 o más, los 5 últimos bajan."
    const val HIDDEN = "No estás participando. Tu historial de entrenos no cambia."
}
