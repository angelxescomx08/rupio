package com.rupio.app.core

import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/** Idiomas de la app. El español es el original; el inglés va junto a cada texto (ver [tr]). */
enum class AppLanguage(val code: String, val nativeName: String) {
    ES("es", "Español"), EN("en", "English");

    companion object {
        fun of(code: String?): AppLanguage? = entries.firstOrNull { code != null && code.startsWith(it.code) }

        /**
         * Sin elección (automático) se sigue el idioma del teléfono: español si el teléfono está en español y, si no,
         * inglés (para quien no habla español, el inglés es más útil que el español).
         */
        fun resolve(choice: AppLanguage?, deviceLanguage: String?): AppLanguage =
            choice ?: if (deviceLanguage.orEmpty().lowercase(Locale.ROOT).startsWith("es")) ES else EN
    }
}

/**
 * Idioma actual de la app. Es estado de Compose: al cambiarlo se repinta todo lo que lo lee. Fuera de Compose
 * (notificaciones, mensajes de los repositorios) se lee el valor del momento.
 *
 * Por qué no strings.xml: casi todos los textos ya vivían en el código (también en `domain`, que no tiene Context), con
 * plurales y valores en medio. Con [tr] cada texto lleva sus dos idiomas juntos: nunca queda uno sin traducir, se
 * revisan lado a lado y las pruebas de `domain` siguen sin Android.
 */
object I18n {
    private val current = mutableStateOf(AppLanguage.ES)
    val language: AppLanguage get() = current.value
    val isEnglish: Boolean get() = current.value == AppLanguage.EN
    fun set(l: AppLanguage) { if (current.value != l) current.value = l }

    val SPANISH_MX: Locale = Locale.forLanguageTag("es-MX")
    /** Fechas, meses y números con el formato del idioma (es-MX o en-US). */
    val locale: Locale get() = if (isEnglish) Locale.US else SPANISH_MX
}

/** Texto en el idioma de la app: `tr("Guardar", "Save")`. */
fun tr(es: String, en: String): String = if (I18n.isEnglish) en else es

/**
 * Contenido que se arma con [tr] y se guarda (listas de conceptos, tutoriales…): se construye una vez por idioma. Un
 * `val` normal se calcularía una sola vez y se quedaría en el idioma con el que arrancó la app.
 */
class PerLanguage<T>(private val build: () -> T) {
    private val cache = arrayOfNulls<Any>(AppLanguage.entries.size)

    @Suppress("UNCHECKED_CAST")
    fun get(): T {
        val i = I18n.language.ordinal
        return (cache[i] ?: build().also { cache[i] = it }) as T
    }
}
