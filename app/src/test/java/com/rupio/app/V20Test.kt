package com.rupio.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import com.rupio.app.core.AppLanguage
import com.rupio.app.core.Dates
import com.rupio.app.core.Fmt
import com.rupio.app.core.I18n
import com.rupio.app.core.WeightUnit
import com.rupio.app.core.tr
import com.rupio.app.data.local.CatalogSeeder
import com.rupio.app.data.local.Muscles
import com.rupio.app.data.prefs.AppearancePrefs
import com.rupio.app.domain.Accent
import com.rupio.app.domain.AppIconStyle
import com.rupio.app.domain.RewardCopy
import com.rupio.app.domain.SetType
import com.rupio.app.domain.SupportPolicy
import com.rupio.app.domain.SupportTier
import com.rupio.app.domain.WallCopy
import com.rupio.app.ui.learn.Concepts
import com.rupio.app.ui.learn.Tutorials
import com.rupio.app.ui.theme.Appearance
import com.rupio.app.ui.theme.DarkPalette
import com.rupio.app.ui.theme.LightPalette
import com.rupio.app.ui.theme.Metals
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** v2.0: inglés, tema claro (con contraste medido), más recompensas y el muro a la vista. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V20Test {
    private val app get() = ApplicationProvider.getApplicationContext<RupioApp>()

    @After fun restore() { I18n.set(AppLanguage.ES); Appearance.setMode(ThemeMode.DARK) }

    // ------------------------------------------------------------------ idioma

    @Test fun idioma_automatico_sigue_al_telefono() {
        assertEquals(AppLanguage.ES, AppLanguage.resolve(null, "es"))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(null, "en"))
        assertEquals("un teléfono en portugués ve inglés (más útil que el español)", AppLanguage.EN, AppLanguage.resolve(null, "pt"))
        assertEquals("lo elegido manda", AppLanguage.ES, AppLanguage.resolve(AppLanguage.ES, "en"))
        assertEquals(AppLanguage.EN, AppLanguage.of("en-US"))
    }

    @Test fun textos_y_formatos_cambian_con_el_idioma() {
        val day = LocalDate.of(2026, 11, 17).toEpochDay().toInt()
        assertEquals("Guardar", tr("Guardar", "Save"))
        assertEquals("12.5 mil lb", Fmt.volume(WeightUnit.LB.toKg(12_500.0), WeightUnit.LB))
        assertTrue(Dates.formatDay(day).startsWith("Mar 17 de nov"))
        assertEquals("Ayer", Dates.daysAgo(day - 1, day))
        I18n.set(AppLanguage.EN)
        assertEquals("Save", tr("Guardar", "Save"))
        assertEquals("12.5k lb", Fmt.volume(WeightUnit.LB.toKg(12_500.0), WeightUnit.LB))
        assertEquals("Tue, Nov 17, 2026", Dates.formatDay(day))
        assertEquals("Yesterday", Dates.daysAgo(day - 1, day))
        assertEquals("No rest", Fmt.restLabel(0))
        assertEquals("W", SetType.WARMUP.short)
        assertEquals("Chest", Muscles.label("pecho"))
        assertEquals("Dumbbells", Muscles.equipmentLabel("mancuernas"))
    }

    @Test fun contenido_de_aprende_en_los_dos_idiomas() {
        val es = Concepts.byId("sobrecarga")!!.title
        val ids = Concepts.all.map { it.id } to Tutorials.all.map { it.id }
        I18n.set(AppLanguage.EN)
        assertEquals("Progressive overload", Concepts.byId("sobrecarga")!!.title)
        assertEquals("los mismos conceptos y tutoriales en inglés", ids, Concepts.all.map { it.id } to Tutorials.all.map { it.id })
        assertTrue(Tutorials.byId("basicos")!!.title == "Gym basics")
        assertTrue(Concepts.byId("record")!!.papers.isEmpty() || Concepts.byId("record")!!.papers.all { !it.title.contains(" y col.") })
        I18n.set(AppLanguage.ES)
        assertEquals("cada idioma se arma aparte (no se queda pegado el primero)", es, Concepts.byId("sobrecarga")!!.title)
    }

    @Test fun catalogo_en_ingles_y_busqueda_en_los_dos_idiomas() = runBlocking {
        val c = app.container
        CatalogSeeder.seedIfNeeded(app, c.db, AppLanguage.EN)
        val bench = c.db.exercises().get("press_de_banca_con_barra")!!
        assertEquals("Barbell Bench Press", bench.name)
        assertTrue("se puede buscar en español", "press de banca" in bench.searchKey)
        assertTrue("y en inglés", "bench press" in bench.searchKey)
        assertTrue(bench.cue.startsWith("Lie down"))
        I18n.set(AppLanguage.EN)
        assertEquals("The image shows dumbbells; here you use cables.", CatalogSeeder.imageNote(app, "aperturas_con_polea_en_banco_plano"))
        CatalogSeeder.seedIfNeeded(app, c.db, AppLanguage.ES)
        assertEquals("Press de banca con barra", c.db.exercises().get("press_de_banca_con_barra")!!.name)
    }

    @Test fun catalogo_ingles_completo() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun ids(file: String) = json.parseToJsonElement(app.assets.open(file).bufferedReader().readText())
            .let { it as kotlinx.serialization.json.JsonArray }.map { (it as kotlinx.serialization.json.JsonObject)["id"].toString() }
        assertEquals(ids("exercises_es.json").toSet(), ids("exercises_en.json").toSet())
    }

    @Test fun preferencias_de_idioma_y_tema() {
        AppearancePrefs.setThemeChoice(app, ThemeMode.LIGHT)
        assertFalse(Appearance.isDark); assertEquals(ThemeMode.LIGHT, AppearancePrefs.themeChoice(app))
        assertEquals(LightPalette.background, RupioColors.Background)
        AppearancePrefs.setThemeChoice(app, ThemeMode.DARK)
        assertTrue(Appearance.isDark); assertEquals(DarkPalette.background, RupioColors.Background)
        AppearancePrefs.setLanguageChoice(app, AppLanguage.EN)
        assertEquals(AppLanguage.EN, I18n.language)
        AppearancePrefs.setLanguageChoice(app, null)
        assertEquals("automático = el idioma del teléfono (español en estas pruebas)", AppLanguage.ES, I18n.language)
    }

    @Test fun quien_ya_usaba_rupio_se_queda_en_espanol_y_oscuro() {
        app.getSharedPreferences("rupio_appearance", 0).edit().clear().commit()
        app.getSharedPreferences("rupio_app", 0).edit().putInt("catalog_v", 6).commit()
        AppearancePrefs.migrateLegacy(app)
        assertEquals(AppLanguage.ES, AppearancePrefs.languageChoice(app))
        assertEquals(ThemeMode.DARK, AppearancePrefs.themeChoice(app))
        // Una instalación nueva (sin catálogo previo) sigue al teléfono.
        app.getSharedPreferences("rupio_appearance", 0).edit().clear().commit()
        AppearancePrefs.setLanguageChoice(app, null)
        app.getSharedPreferences("rupio_app", 0).edit().remove("catalog_v").commit()
        AppearancePrefs.migrateLegacy(app)
        assertNull(AppearancePrefs.languageChoice(app))
        assertEquals(ThemeMode.SYSTEM, AppearancePrefs.themeChoice(app))
    }

    // ------------------------------------------------------------------ recompensas

    @Test fun mas_recompensas_en_cada_nivel_y_dicen_de_donde_vienen() {
        SupportTier.entries.forEach { assertTrue("${it.title}: al menos 4 recompensas", it.perks.size >= 4) }
        SupportTier.entries.drop(1).forEach { assertTrue(it.perks.first().startsWith("Todo lo de")) }
        // Cada nivel trae algo propio de colores o íconos.
        assertEquals(listOf(Accent.ROSA), Accent.entries.filter { it.minTier == SupportTier.CAFE })
        assertEquals(3, Accent.entries.count { it.minTier == SupportTier.LICUADO })
        assertEquals(AppIconStyle.PLATA, AppIconStyle.entries.single { it.minTier == SupportTier.LICUADO })
        assertEquals(AppIconStyle.AURORA, AppIconStyle.entries.single { it.minTier == SupportTier.MECENAS })
        assertFalse(AppIconStyle.AURORA.unlockedBy(SupportTier.FAN))
        // Se entiende que son por pagar.
        assertTrue("apoyar" in RewardCopy.yours(SupportTier.CAFE) && "Un café" in RewardCopy.yours(SupportTier.CAFE))
        assertTrue("gratis" in RewardCopy.EXPLAIN)
        I18n.set(AppLanguage.EN)
        assertEquals("A coffee", SupportTier.CAFE.title)
        assertTrue("supporting" in RewardCopy.yours(SupportTier.CAFE))
        assertEquals("Patron · Aurora", SupportTier.MECENAS.badge)
        assertEquals("Gold", Metals.Oro.name)
    }

    @Test fun el_muro_se_asoma_en_inicio_sin_estorbar() {
        assertFalse("no en los primeros entrenos", SupportPolicy.showWallTeaserOnHome(2, dismissed = false))
        assertTrue(SupportPolicy.showWallTeaserOnHome(3, dismissed = false))
        assertFalse("se puede ocultar", SupportPolicy.showWallTeaserOnHome(80, dismissed = true))
        assertTrue("12" in WallCopy.teaser(12))
        assertTrue("esta persona" in WallCopy.teaser(1))
    }

    @Test fun el_icono_dorado_de_antes_se_conserva() {
        val prefs = app.getSharedPreferences("rupio_supporter", 0)
        prefs.edit().clear().putBoolean("gold_icon", true).commit()
        val repo = com.rupio.app.data.billing.SupportRepository(app, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        assertEquals(AppIconStyle.ORO, repo.state.value.appIcon)
        prefs.edit().clear().commit()
    }

    // ------------------------------------------------------------------ contraste del tema claro (WCAG 2.2)

    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance() + 0.05; val y = b.luminance() + 0.05
        return maxOf(x, y) / minOf(x, y).toDouble()
    }
    private fun over(fg: Color, bg: Color) = Color(fg.red * fg.alpha + bg.red * (1 - fg.alpha), fg.green * fg.alpha + bg.green * (1 - fg.alpha), fg.blue * fg.alpha + bg.blue * (1 - fg.alpha))

    @Test fun contraste_del_tema_claro() {
        Appearance.setMode(ThemeMode.LIGHT)
        val p = LightPalette
        val fails = mutableListOf<String>()
        val bgs = mapOf("fondo" to p.background, "tarjeta" to p.surface, "tarjeta 2" to p.surface2, "tarjeta 3" to p.surface3,
            "aviso ámbar" to over(p.warning.copy(alpha = 0.10f), p.background), "chip azul" to over(RupioColors.Primary.copy(alpha = 0.18f), p.surface))
        val text = mapOf("texto" to p.text, "secundario" to p.textDim, "tenue" to p.textFaint, "acento" to RupioColors.Primary,
            "verde" to p.success, "naranja" to p.warning, "rojo" to p.danger, "lila" to p.rest, "gráfica" to p.chart)
        for ((tn, t) in text) for ((bn, b) in bgs) contrast(t, b).let { if (it < 4.5) fails += "$tn sobre $bn: ${"%.2f".format(it)}" }
        // Rellenos con texto encima: blanco sobre verde, rojo, superseries y el botón de acento.
        (listOf(p.success, p.danger, p.warning, RupioColors.Primary) + p.supersets).forEach { f ->
            contrast(p.onFill, f).let { if (it < 4.5) fails += "texto sobre relleno $f: ${"%.2f".format(it)}" }
        }
        listOf(p.background, p.surface, p.surface2, p.surface3).forEach { b ->
            contrast(p.controlOutline, b).let { if (it < 3.0) fails += "borde de control: ${"%.2f".format(it)}" }
            p.supersets.forEach { s -> contrast(s, b).let { if (it < 3.0) fails += "superserie sobre tarjeta: ${"%.2f".format(it)}" } }
        }
        for (m in Metals.all) {
            (listOf(p.background, p.surface, p.surface2, p.surface3) + m.container).forEach { b ->
                contrast(m.ink, b).let { if (it < 6.5) fails += "${m.name} (tinta) sobre $b: ${"%.2f".format(it)}" }
            }
        }
        assertTrue("Contraste insuficiente en el tema claro:\n" + fails.joinToString("\n"), fails.isEmpty())
    }

    @Test fun el_acento_cambia_de_tono_con_el_tema() {
        val c = app.container
        c.support.debugReset(); c.support.debugGrant(SupportTier.LICUADO); c.support.setAccent(Accent.ROSA)
        Appearance.setMode(ThemeMode.DARK); assertEquals(Color(0xFFFF7AB4), RupioColors.Primary)
        Appearance.setMode(ThemeMode.LIGHT); assertEquals(Color(0xFFAE1450), RupioColors.Primary)
        assertEquals(Color.White, RupioColors.OnPrimary)
        c.support.debugReset()
    }
}
