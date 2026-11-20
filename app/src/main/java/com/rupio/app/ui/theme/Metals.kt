package com.rupio.app.ui.theme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.rupio.app.core.tr
import com.rupio.app.domain.SupportTier
/**
 * Metales de los niveles de apoyo (paleta v2): Café = Bronce, Licuado = Plata, Fan = Oro, Mecenas = Aurora.
 * Solo adornan: el nombre del metal y el emoji siempre van junto al color, así que nunca dependen solo de él.
 *
 * Medido (el contraste se revisa en QaUiTest.contrasteDeMetales y contrasteDelTemaClaro):
 *  - [ink] (texto e íconos sobre las tarjetas) ≥ 7:1 sobre todas las tarjetas y sobre su propio [container], en los dos
 *    temas: en oscuro la tinta es clara ([inkDark]) y en claro es el mismo metal oscurecido ([inkLight]).
 *  - [on] (texto encima del degradado) ≥ 6:1 sobre cada color del degradado (el degradado es igual en los dos temas).
 *  - Entre metales y contra los colores con significado (verde, naranja, lila, rojo) ΔE ≥ 8 con protanopía,
 *    deuteranopía y tritanopía simuladas (Machado 2009), el mismo umbral que los colores de superseries.
 *
 * Los [Brush] se crean una sola vez (no en cada recomposición).
 */
@Immutable
class Metal(private val nameEs: String, private val nameEn: String, val inkDark: Color, val inkLight: Color, val stops: List<Color>, val on: Color) {
    val name: String get() = tr(nameEs, nameEn)
    val ink: Color get() = if (Appearance.isDark) inkDark else inkLight
    private val containerDark = lerp(DarkPalette.background, inkDark, 0.14f)
    private val containerLight = lerp(LightPalette.background, inkLight, 0.10f)
    /** Fondo de chips y filas: la tinta mezclada con el fondo de la app (sin transparencia que pintar). */
    val container: Color get() = if (Appearance.isDark) containerDark else containerLight
    val brush: Brush = Brush.linearGradient(stops)
}

object Metals {
    val Bronce = Metal("Bronce", "Bronze", Color(0xFFE2A47A), Color(0xFF6B3612), listOf(Color(0xFFF2BC93), Color(0xFFC7865A)), Color(0xFF1E1007))
    val Plata = Metal("Plata", "Silver", Color(0xFFC3CEDD), Color(0xFF394454), listOf(Color(0xFFEEF2F7), Color(0xFFA7B3C3)), Color(0xFF10151C))
    val Oro = Metal("Oro", "Gold", Color(0xFFF4C542), Color(0xFF5C4200), listOf(Color(0xFFFFE28A), Color(0xFFE0A82A)), Color(0xFF241800))
    /** Aurora boreal: menta → azul lavanda → orquídea. */
    val Aurora = Metal("Aurora", "Aurora", Color(0xFFF0A5E8), Color(0xFF6E2A70), listOf(Color(0xFF72E6D0), Color(0xFF93A2FF), Color(0xFFF0A5E8)), Color(0xFF120F1E))
    val all = listOf(Bronce, Plata, Oro, Aurora)
    fun of(tier: SupportTier): Metal = when (tier) {
        SupportTier.CAFE -> Bronce
        SupportTier.LICUADO -> Plata
        SupportTier.FAN -> Oro
        SupportTier.MECENAS -> Aurora
    }
    /** Insignia que manda el servidor en las ligas: 0 nada · 1 Bronce · 2 Plata · 3 Oro · 4 Aurora. */
    fun ofBadge(badge: Int): Metal? = all.getOrNull(badge - 1)
}
