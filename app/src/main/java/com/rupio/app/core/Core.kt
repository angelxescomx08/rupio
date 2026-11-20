package com.rupio.app.core

import java.security.SecureRandom
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * UUIDv7 (RFC 9562): 48 bits de milisegundos + aleatorio.
 * Ordenados en el tiempo → las inserciones caen al final del índice B-tree
 * (en SQLite y en Postgres), con menos fragmentación que UUIDv4.
 */
object Ids {
    private val rnd = SecureRandom()
    private var lastMs = Long.MIN_VALUE
    private var seq = 0

    /**
     * UUIDv7 MONÓTONO (RFC 9562, método 1): si se generan varios en el mismo milisegundo (al empezar una rutina se crean
     * decenas), los 12 bits "rand_a" son un contador que sube; si se agota, se avanza 1 ms. Así siempre quedan en orden.
     */
    @Synchronized
    fun uuidV7(nowMs: Long = System.currentTimeMillis()): String {
        var ms = nowMs
        if (ms <= lastMs) { ms = lastMs; seq++; if (seq > 0xFFF) { ms++; seq = 0 } } else seq = 0
        lastMs = ms
        val rand = ByteArray(10).also { rnd.nextBytes(it) }
        var msb = (ms and 0xFFFFFFFFFFFFL) shl 16
        msb = msb or (0x7L shl 12) or seq.toLong()
        var lsb = 0L
        for (i in 2 until 10) lsb = (lsb shl 8) or (rand[i].toLong() and 0xFF)
        lsb = (lsb and 0x3FFFFFFFFFFFFFFFL) or (0x2L shl 62) // variante RFC
        return UUID(msb, lsb).toString()
    }
}

/** Unidad de peso. Internamente TODO se guarda en kg; se muestra en la unidad del usuario. */
enum class WeightUnit(val code: String, val label: String) {
    LB("lb", "lb"), KG("kg", "kg");

    val other: WeightUnit get() = if (this == LB) KG else LB

    fun fromKg(kg: Double): Double = if (this == KG) kg else kg * KG_TO_LB
    fun toKg(value: Double): Double = if (this == KG) value else value / KG_TO_LB

    companion object {
        const val KG_TO_LB = 2.2046226218
        fun of(code: String?) = if (code == "kg") KG else LB
    }
}

object Fmt {
    /** es-MX o en-US según el idioma de la app. */
    val locale: Locale get() = I18n.locale

    /** 225 → "225", 102.06 → "102.1" */
    fun num(v: Double, decimals: Int = 1): String {
        // Datos imposibles (infinito, NaN o billones) nunca se pintan como basura.
        if (!v.isFinite() || abs(v) >= 1e12) return "—"
        val r = Math.round(v * 10.0.pow(decimals)) / 10.0.pow(decimals)
        return if (abs(r - r.roundToInt()) < 1e-9) r.roundToInt().toString()
        else String.format(Locale.US, "%.${decimals}f", r)
    }

    fun weight(kg: Double?, unit: WeightUnit): String =
        if (kg == null || !kg.isFinite() || abs(kg) > MAX_KG) "—" else "${num(unit.fromKg(kg))} ${unit.label}"

    /** Ningún peso real pasa de esto (≈ 3 300 lb); lo que venga mayor es un error de captura o de datos. */
    const val MAX_KG = 1500.0

    /** "225 lb · 102.1 kg" — siempre en ambas unidades. */
    fun weightBoth(kg: Double?, unit: WeightUnit): String =
        if (kg == null || !kg.isFinite() || abs(kg) > MAX_KG) "—" else "${weight(kg, unit)} · ${weight(kg, unit.other)}"

    /** Volumen grande compacto: 12 450 lb → "12.5 mil lb" */
    fun volume(kg: Double, unit: WeightUnit): String {
        if (!kg.isFinite() || kg < 0 || kg >= 1e12) return "— ${unit.label}"
        val v = unit.fromKg(kg)
        return when {
            v >= 1_000_000 -> "${num(v / 1_000_000, 2)} M ${unit.label}"
            v >= 10_000 -> "${num(v / 1000, 1)}${tr(" mil", "k")} ${unit.label}"
            else -> "${String.format(locale, "%,d", v.roundToInt())} ${unit.label}"
        }
    }

    fun duration(sec0: Long): String {
        val sec = sec0.coerceIn(0L, 359_999L * 100)
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun durationHuman(sec0: Long): String {
        val sec = sec0.coerceIn(0L, 359_999L * 100)
        val h = sec / 3600; val m = (sec % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h} h ${m} min"
            h > 0 -> "${h} h"
            m > 0 -> "$m min"
            else -> "${sec} s"
        }
    }

    fun restLabel(sec: Int): String = when {
        sec <= 0 -> tr("Sin descanso", "No rest")
        sec % 60 == 0 -> "${sec / 60} min"
        sec > 60 -> "${sec / 60} min ${sec % 60} s"
        else -> "$sec s"
    }

    /** Versión corta para chips: "Sin", "45 s", "2 min", "1:30". */
    fun restShort(sec: Int): String = when {
        sec <= 0 -> tr("Sin", "Off")
        sec < 60 -> "$sec s"
        sec % 60 == 0 -> "${sec / 60} min"
        else -> String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
    }

    /** "1 entreno", "3 entrenos": nunca "1 entrenos". Las palabras llegan ya traducidas (tr). */
    fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

    private fun Double.pow(n: Int): Double { var r = 1.0; repeat(n) { r *= this }; return r }
}

/** Texto sin acentos y en minúsculas para buscar "press banca" == "Press de bánca". */
fun String.searchKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "").lowercase(Locale.ROOT)

/**
 * Manejo de fechas y zonas horarias.
 * - Instantes en UTC (epoch ms) para todo lo que es "un momento".
 * - El día del entrenamiento (localDate, epochDay) se fija con la zona del usuario AL EMPEZAR,
 *   así un entrenamiento de las 23:30 en CDMX cuenta para ese día aunque luego viaje.
 * - Para mostrar horas se usa la zona ACTUAL del teléfono (se actualiza si la cambia).
 */
object Dates {
    /** Formatos de cada idioma (se crean una vez): "mar 11 de nov 2026" / "Tue, Nov 11, 2026"; 24 h en español, 12 h en inglés. */
    private class Formats(val day: DateTimeFormatter, val dayShort: DateTimeFormatter, val time: DateTimeFormatter,
                          val month: DateTimeFormatter, val spoken: DateTimeFormatter)
    private val es = Formats(
        DateTimeFormatter.ofPattern("EEE d 'de' MMM yyyy", I18n.SPANISH_MX), DateTimeFormatter.ofPattern("d MMM", I18n.SPANISH_MX),
        DateTimeFormatter.ofPattern("HH:mm", I18n.SPANISH_MX), DateTimeFormatter.ofPattern("MMM yy", I18n.SPANISH_MX),
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", I18n.SPANISH_MX))
    private val en = Formats(
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US), DateTimeFormatter.ofPattern("MMM d", Locale.US),
        DateTimeFormatter.ofPattern("h:mm a", Locale.US), DateTimeFormatter.ofPattern("MMM yy", Locale.US),
        DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
    private val f: Formats get() = if (I18n.isEnglish) en else es
    private val dayFmt get() = f.day
    private val dayShort get() = f.dayShort
    private val timeFmt get() = f.time
    private val monthFmt get() = f.month

    fun localEpochDay(epochMs: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toEpochDay().toInt()

    fun today(zone: ZoneId): LocalDate = LocalDate.now(zone)

    fun weekStart(date: LocalDate, weekStartIso: Int): LocalDate {
        val dow = if (weekStartIso == 7) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        return date.with(TemporalAdjusters.previousOrSame(dow))
    }

    /** Desplazamiento para agrupar semanas en SQL con aritmética de epochDay (1970-01-01 fue jueves). */
    fun weekShift(weekStartIso: Int): Int = if (weekStartIso == 7) 4 else 3

    fun formatDay(epochDay: Int): String = LocalDate.ofEpochDay(epochDay.toLong()).format(dayFmt).replaceFirstChar { it.uppercase() }
    fun formatDayShort(date: LocalDate): String = date.format(dayShort)
    fun formatMonth(date: LocalDate): String = date.format(monthFmt).replaceFirstChar { it.uppercase() }
    fun formatTime(epochMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(epochMs).atZone(zone).format(timeFmt)

    /**
     * Cuándo pasó algo, como lo diría una persona: "hace un momento", "hace 5 min", "hoy 14:05", "ayer 14:05",
     * "14 nov" o "14 nov 2025". Antes solo se mostraba la hora y "14:05" de hace 3 días se leía como de hoy.
     */
    fun relative(epochMs: Long, nowMs: Long, zone: ZoneId): String {
        val diff = nowMs - epochMs
        if (diff in 0 until 60_000) return tr("hace un momento", "just now")
        if (diff in 60_000 until 3_600_000) return tr("hace ${diff / 60_000} min", "${diff / 60_000} min ago")
        val then = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return when (then.toLocalDate()) {
            today -> tr("hoy ", "today ") + then.format(timeFmt)
            today.minusDays(1) -> tr("ayer ", "yesterday ") + then.format(timeFmt)
            else -> if (then.year == today.year) then.toLocalDate().format(dayShort) else then.toLocalDate().format(dayShort) + " " + then.year
        }
    }

    /** "martes 11 de noviembre" (para lectores de pantalla). */
    fun spokenDay(date: LocalDate): String = date.format(f.spoken)

    /** ¿Ese momento se vivió en otra hora que la de [current]? (entrenaste de viaje). */
    fun isOtherZone(epochMs: Long, current: ZoneId, origin: String): Boolean {
        val originZone = runCatching { ZoneId.of(origin) }.getOrNull() ?: return false
        return Instant.ofEpochMilli(epochMs).atZone(current).offset != Instant.ofEpochMilli(epochMs).atZone(originZone).offset
    }

    /** "Hoy", "Ayer", "Hace 3 días", "Hace 2 semanas" (para "Última vez" de una rutina). */
    fun daysAgo(day: Int, today: Int): String = when (val d = today - day) {
        in Int.MIN_VALUE..0 -> tr("Hoy", "Today")
        1 -> tr("Ayer", "Yesterday")
        in 2..13 -> tr("Hace $d días", "$d days ago")
        in 14..59 -> tr("Hace ${d / 7} semanas", "${d / 7} weeks ago")
        else -> tr("Hace ${d / 30} meses", "${d / 30} months ago")
    }

    /** "19:05" o "03:05 (19:05 hora de Ciudad de México)" si se entrenó en otra zona. */
    fun formatTimeWithOrigin(epochMs: Long, current: ZoneId, origin: String): String {
        val now = formatTime(epochMs, current)
        val originZone = runCatching { ZoneId.of(origin) }.getOrNull() ?: return now
        if (!isOtherZone(epochMs, current, origin)) return now
        val city = origin.substringAfterLast('/').replace('_', ' ')
        return tr("$now (${formatTime(epochMs, originZone)} hora de $city)", "$now (${formatTime(epochMs, originZone)} $city time)")
    }
}
