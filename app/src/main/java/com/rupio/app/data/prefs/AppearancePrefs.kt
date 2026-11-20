package com.rupio.app.data.prefs

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.I18n
import com.rupio.app.ui.theme.Appearance
import com.rupio.app.ui.theme.ThemeMode

/**
 * v2.0: idioma y tema. Se guardan en SharedPreferences (no en DataStore) porque se leen al arrancar, antes de pintar
 * la primera pantalla: así no hay parpadeo de idioma ni de colores.
 *
 * Idioma: en Android 13+ la elección vive en el ajuste del sistema "Idiomas de la app" (LocaleManager, recomendado por
 * Android): elegirlo en Rupio o en Ajustes da lo mismo. En versiones anteriores se guarda aquí.
 */
object AppearancePrefs {
    private const val FILE = "rupio_appearance"
    private const val K_LANGUAGE = "language"     // "es" | "en" | (sin valor) = automático
    private const val K_THEME = "theme"           // "system" | "light" | "dark"
    private const val K_MIGRATED = "migrated_v20"

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun localeManager(c: Context): LocaleManager? =
        if (Build.VERSION.SDK_INT >= 33) runCatching { c.getSystemService(LocaleManager::class.java) }.getOrNull() else null

    /** El idioma elegido, o null = automático (el del teléfono). */
    fun languageChoice(c: Context): AppLanguage? {
        if (Build.VERSION.SDK_INT >= 33) {
            val lm = localeManager(c)
            if (lm != null) runCatching { lm.applicationLocales }.getOrNull()?.let { list ->
                return if (list.isEmpty) null else AppLanguage.of(list[0].language)
            }
        }
        return AppLanguage.of(prefs(c).getString(K_LANGUAGE, null))
    }

    /** Guarda la elección. En Android 13+ el sistema recrea las pantallas solo; antes hay que llamar a recreate(). */
    fun setLanguageChoice(c: Context, l: AppLanguage?) {
        prefs(c).edit().apply { if (l == null) remove(K_LANGUAGE) else putString(K_LANGUAGE, l.code) }.apply()
        if (Build.VERSION.SDK_INT >= 33) localeManager(c)?.let { lm ->
            runCatching { lm.applicationLocales = if (l == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(l.code) }
        }
        I18n.set(resolvedLanguage(c))
    }

    /** Idioma del teléfono (no el de la app). */
    fun deviceLanguage(): String? = runCatching { Resources.getSystem().configuration.locales[0].language }.getOrNull()

    fun resolvedLanguage(c: Context): AppLanguage = AppLanguage.resolve(languageChoice(c), deviceLanguage())

    fun themeChoice(c: Context): ThemeMode = ThemeMode.of(prefs(c).getString(K_THEME, null)) ?: ThemeMode.SYSTEM

    fun setThemeChoice(c: Context, m: ThemeMode) {
        prefs(c).edit().putString(K_THEME, m.code).apply()
        Appearance.setMode(m)
    }

    /** ¿El teléfono está en modo oscuro? */
    fun systemDark(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * Antes de la v2.0 Rupio solo existía en español y con tema oscuro. Quien ya la usaba se queda así (lo puede cambiar
     * en Perfil); las instalaciones nuevas siguen el idioma y el modo del teléfono. Se llama antes de sembrar el
     * catálogo: "catalog_v" existe solo si la app ya había abierto antes.
     */
    fun migrateLegacy(c: Context) {
        val p = prefs(c)
        if (p.getBoolean(K_MIGRATED, false)) return
        val legacy = c.getSharedPreferences("rupio_app", Context.MODE_PRIVATE).contains("catalog_v")
        if (legacy) {
            if (languageChoice(c) == null) setLanguageChoice(c, AppLanguage.ES)
            if (!p.contains(K_THEME)) p.edit().putString(K_THEME, ThemeMode.DARK.code).apply()
        }
        p.edit().putBoolean(K_MIGRATED, true).apply()
    }

    /** Al arrancar: idioma y tema guardados → estado de la app. */
    fun apply(c: Context) {
        I18n.set(resolvedLanguage(c))
        Appearance.setMode(themeChoice(c))
        Appearance.setSystemDark(systemDark(c))
    }
}
