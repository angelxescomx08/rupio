package com.rupio.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.rupio.app.domain.SupportTier

/**
 * Metales de los niveles de apoyo (paleta v2): Café = Bronce, Licuado = Plata, Fan = Oro, Mecenas = Aurora.
 * Solo adornan: el nombre del metal y el emoji siempre van junto al color, así que nunca dependen solo de él.
 *
 * Medido (el contraste se revisa en QaUiTest.contrasteDeMetales):
 *  - [ink] (texto e íconos sobre lo oscuro) ≥ 7:1 sobre todas las tarjetas y sobre su propio [container].
 *  - [on] (texto encima del degradado) ≥ 6:1 sobre cada color del degradado.
 *  - Entre metales y contra los colores con significado (verde, naranja, lila, rojo) ΔE ≥ 8 con protanopía,
 *    deuteranopía y tritanopía simuladas (Machado 2009), el mismo umbral que los colores de superseries.
 *
 * Los [Brush] se crean una sola vez (no en cada recomposición).
 */
@Immutable
class Metal(val name: String, val ink: Color, val stops: List<Color>, val on: Color) {
    /** Fondo de chips y filas: la tinta al 14 % sobre el fondo de la app (ya mezclado: sin transparencia que pintar). */
    val container: Color = lerp(RupioColors.Background, ink, 0.14f)
    val brush: Brush = Brush.linearGradient(stops)
}

object Metals {
    val Bronce = Metal("Bronce", Color(0xFFE2A47A), listOf(Color(0xFFF2BC93), Color(0xFFC7865A)), Color(0xFF1E1007))
    val Plata = Metal("Plata", Color(0xFFC3CEDD), listOf(Color(0xFFEEF2F7), Color(0xFFA7B3C3)), Color(0xFF10151C))
    val Oro = Metal("Oro", Color(0xFFF4C542), listOf(Color(0xFFFFE28A), Color(0xFFE0A82A)), Color(0xFF241800))
    /** Aurora boreal: menta → azul lavanda → orquídea. */
    val Aurora = Metal("Aurora", Color(0xFFF0A5E8), listOf(Color(0xFF72E6D0), Color(0xFF93A2FF), Color(0xFFF0A5E8)), Color(0xFF120F1E))

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
