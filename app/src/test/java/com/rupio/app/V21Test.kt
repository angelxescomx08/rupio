package com.rupio.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.rupio.app.domain.Accent
import com.rupio.app.domain.SupportTier as T
import com.rupio.app.domain.SupportUpgrade as U
import com.rupio.app.ui.theme.Appearance
import com.rupio.app.ui.theme.DarkPalette
import com.rupio.app.ui.theme.LightPalette
import com.rupio.app.ui.theme.Metals
import com.rupio.app.ui.theme.RupioColors
import com.rupio.app.ui.theme.ThemeMode
import com.rupio.app.ui.theme.rupioScheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.1 · QA de contraste con TODOS los acentos (los de apoyo incluidos) en los dos temas. Las pruebas de antes medían
 * solo el azul: quien paga y elige rosa, plata o Aurora ve los mismos botones, pestañas y chips con su color.
 */
class V21Test {
    @After fun restore() { Appearance.setMode(ThemeMode.DARK); RupioColors.applyAccent(Color(Accent.AZUL.argb), Color(Accent.AZUL.lightArgb)) }

    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance() + 0.05; val y = b.luminance() + 0.05
        return (maxOf(x, y) / minOf(x, y)).toDouble()
    }
    private fun over(fg: Color, bg: Color) = Color(fg.red * fg.alpha + bg.red * (1 - fg.alpha), fg.green * fg.alpha + bg.green * (1 - fg.alpha), fg.blue * fg.alpha + bg.blue * (1 - fg.alpha))

    @Test fun duracion_al_minuto_mas_cercano_y_miles_con_separador() {
        val f = com.rupio.app.core.Fmt
        org.junit.Assert.assertEquals("2 min", f.durationHuman(118))      // 1:58 ya no dice "1 min"
        org.junit.Assert.assertEquals("1 min", f.durationHuman(89))
        org.junit.Assert.assertEquals("45 s", f.durationHuman(45))
        org.junit.Assert.assertEquals("1 h", f.durationHuman(59 * 60 + 40))
        org.junit.Assert.assertEquals("1 h 1 min", f.durationHuman(3_660))
        org.junit.Assert.assertEquals("1,508", f.count(1_508))
    }

    /** Propuesta B: subir de nivel pagando solo la diferencia. */
    @Test fun mejora_de_nivel_cobra_la_diferencia() {
        org.junit.Assert.assertEquals(U.LICUADO_FAN, U.between(T.LICUADO, T.FAN))
        org.junit.Assert.assertNull("Mecenas no tiene mejora (sus puntos son su precio)", U.between(T.FAN, T.MECENAS))
        org.junit.Assert.assertNull("sin nivel no hay mejora", U.between(null, T.LICUADO))
        // Café + Café→Licuado + Licuado→Fan = Fan; una mejora sin el nivel de abajo no da nada (reembolso).
        org.junit.Assert.assertEquals(setOf(T.CAFE, T.LICUADO, T.FAN), U.effective(setOf(T.CAFE), setOf(U.CAFE_LICUADO, U.LICUADO_FAN)))
        org.junit.Assert.assertEquals(emptySet<com.rupio.app.domain.SupportTier>(), U.effective(emptySet(), setOf(U.LICUADO_FAN)))
        // En pantalla: Fan cuesta la diferencia a quien tiene Licuado (en pruebas, con el precio sugerido).
        val s = com.rupio.app.data.billing.SupportState(owned = setOf(T.LICUADO))
        org.junit.Assert.assertEquals("$100 MXN", s.priceFor(T.FAN))
        org.junit.Assert.assertEquals("$499 MXN", s.priceFor(T.MECENAS))
        org.junit.Assert.assertEquals("sin nivel, el precio completo", "$199 MXN", com.rupio.app.data.billing.SupportState().priceFor(T.FAN))
    }

    /** Reporte: "no suena la notificación; tiene que sonar aunque solo esté activado el timbre". */
    @Test fun aviso_automatico_suena_con_el_timbre() {
        val auto = com.rupio.app.data.prefs.RestAlertMode.AUTO
        val p = com.rupio.app.timer.RestAlertPolicy
        val normal = android.media.AudioManager.RINGER_MODE_NORMAL
        val vibrate = android.media.AudioManager.RINGER_MODE_VIBRATE
        val silent = android.media.AudioManager.RINGER_MODE_SILENT
        org.junit.Assert.assertEquals("por defecto", auto, com.rupio.app.data.prefs.RestAlertMode.of(null))
        // Timbre prendido y la música en 0: suena por el timbre (antes, con MEDIA, no sonaba).
        p.decide(auto, normal, privateOutput = false, force = false, mediaMuted = true).let { assertTrue(it.sound); assertTrue(!it.media) }
        p.decide(com.rupio.app.data.prefs.RestAlertMode.MEDIA, normal, false, false).let { assertTrue("MEDIA no cambia", it.media) }
        // Con audífonos o con música sonando: por la música (se oye en los audífonos y la baja un momento).
        assertTrue(p.decide(auto, normal, privateOutput = true, force = false).media)
        assertTrue(p.decide(auto, silent, privateOutput = false, force = false, mediaPlaying = true).let { it.sound && it.media })
        // En vibración: por la música si no está en 0; si está en 0, solo vibra.
        assertTrue(p.decide(auto, vibrate, false, false, mediaMuted = false).let { it.sound && it.media })
        p.decide(auto, vibrate, false, false, mediaMuted = true).let { assertTrue(!it.sound); assertTrue(it.vibrate) }
        // En silencio total respeta el silencio.
        assertTrue(!p.decide(auto, silent, false, false).sound)
        // Timbre prendido pero notificaciones en 0: por la música.
        assertTrue(p.decide(auto, normal, false, false, notifMuted = true, mediaMuted = false).media)
        // "Probar" siempre suena.
        assertTrue(p.decide(auto, silent, false, force = true).sound)
    }

    @Test fun contraste_de_cada_acento_en_los_dos_temas() {
        val fails = mutableListOf<String>()
        for (dark in listOf(true, false)) for (a in Accent.entries) {
            Appearance.setMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
            RupioColors.applyAccent(Color(a.argb), Color(a.lightArgb))
            val p = if (dark) DarkPalette else LightPalette
            val s = rupioScheme(RupioColors.Primary, dark)
            val pr = RupioColors.Primary
            val tag = "${if (dark) "oscuro" else "claro"} · ${a.id}"
            fun need(name: String, fg: Color, bg: Color, min: Double = 4.5) {
                val r = contrast(fg, bg); if (r < min) fails += "$tag · $name: ${"%.2f".format(r)}"
            }
            // Texto y botones con el acento.
            listOf(p.background, p.surface, p.surface2, p.surface3).forEach { need("acento sobre superficie", pr, it) }
            need("texto del botón", RupioColors.OnPrimary, pr)
            // Tarjeta de nivel elegida (acento al 10 % sobre la tarjeta) con la píldora "Recomendado" encima.
            val tierBg = over(pr.copy(alpha = 0.10f), p.surface)
            need("texto en el nivel elegido", p.text, tierBg)
            need("secundario en el nivel elegido", p.textDim, tierBg)
            Metals.all.forEach { m -> need("${m.name} en el nivel elegido", if (dark) m.inkDark else m.inkLight, tierBg) }
            // "Recomendado" va lleno (antes, acento sobre acento al 16 %: 4.07 con el azul en oscuro).
            need("píldora Recomendado", p.onFill, pr)
            // Tu liga en la escalera (acento al 10 %, antes 14 %: 4.44) y el número de tu liga (acento al 16 %).
            need("chip de tu liga", pr, over(pr.copy(alpha = 0.10f), p.surface2))
            need("número de tu liga", pr, over(pr.copy(alpha = 0.16f), p.surface))
            // Píldoras de color (aviso, éxito, peligro) sobre su tinte al 16 %.
            listOf("verde" to p.success, "naranja" to p.warning, "rojo" to p.danger, "lila" to p.rest).forEach { (n, col) ->
                need("píldora $n", col, over(col.copy(alpha = 0.16f), p.surface))
            }
            // Mensaje de apoyo al terminar (acento al 8 % sobre el fondo) y nav inferior (acento al 18 %).
            val prompt = over(pr.copy(alpha = 0.08f), p.background)
            need("secundario en el mensaje de apoyo", p.textDim, prompt)
            need("botón 'Ver cómo apoyar'", s.onSecondaryContainer, over(s.secondaryContainer, prompt))
            // Muro: "Llegas al #2" en el aporte resaltado (tinta Aurora sobre Aurora al 16 %).
            val aurora = if (dark) Metals.Aurora.inkDark else Metals.Aurora.inkLight
            need("aporte que te sube", aurora, over(aurora.copy(alpha = 0.16f), p.surface2))
            // Chips, segmentos y la pestaña elegida de Material (contenedor secundario).
            need("chip o segmento elegido", s.onSecondaryContainer, s.secondaryContainer)
            need("contenedor primario", s.onPrimaryContainer, s.primaryContainer)
        }
        assertTrue("Contraste insuficiente con algún acento:\n" + fails.joinToString("\n"), fails.isEmpty())
    }
}
