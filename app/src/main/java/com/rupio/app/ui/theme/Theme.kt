package com.rupio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta v2. Fondo casi negro (#0A0A0C, no #000): en OLED sigue ahorrando batería, pero evita el "smearing" (los
 * píxeles apagados tardan en encender al desplazar) y el halo del texto blanco puro sobre negro puro. Texto #ECECEF
 * (no blanco) por la misma razón. Todo medido con WCAG 2.2 (4.5:1 texto, 3:1 bordes de controles) en QaUiTest.
 */
object RupioColors {
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF131317)
    val Surface2 = Color(0xFF1B1B21)
    val Surface3 = Color(0xFF25252D)
    /** Solo para divisores y bordes decorativos de tarjetas (no identifica controles). */
    val Outline = Color(0xFF30303A)
    /** Borde de controles (campos de texto, botones con borde, chips): ≥ 3:1 sobre todas las superficies (WCAG 1.4.11). */
    val ControlOutline = Color(0xFF6E6E7A)
    val Text = Color(0xFFECECEF)
    val TextDim = Color(0xFFA6A6B0)
    val TextFaint = Color(0xFF94949E)   // ≥ 5:1 sobre todas las tarjetas (WCAG AA pide 4.5:1)
    /** Color de acento (acciones). Azul eléctrico por defecto; quien apoya Rupio puede elegir otro (ver Accent).
     *  Es estado de Compose: al cambiarlo, toda la app se repinta con el nuevo color. */
    private val accent = mutableStateOf(Color(0xFF5B8CFF))
    val Primary: Color get() = accent.value
    fun applyAccent(c: Color) { if (accent.value != c) accent.value = c }
    val Success = Color(0xFF3DDC97)     // verde: serie completada
    val Warning = Color(0xFFFF9A3D)     // naranja: sube peso / récords (más separado del dorado de los metales)
    val Danger = Color(0xFFFF5C6C)
    val Rest = Color(0xFFB3A6FF)        // lila: descansos (7:1 sobre las tarjetas; el anterior daba 4.6:1)
    /** Colores de superseries (A, B, C…), validados para daltonismo sobre la superficie oscura (ΔE CVD ≥ 8).
     *  Cada grupo lleva además letra + corchete → nunca depende solo del color. */
    val Supersets = listOf(Color(0xFF9085E9), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181), Color(0xFF0CA30C))
    /** Gráficas: una sola serie en azul; mapa de calor = rampa secuencial azul (más = más claro sobre negro). */
    val Chart = Color(0xFF3987E5)
    val Heat = listOf(Color(0xFF184F95), Color(0xFF256ABF), Color(0xFF3987E5), Color(0xFF6DA7EC), Color(0xFF9EC5F4))
    /** Colores de avatar al estilo Google. */
    val Avatars = listOf(Color(0xFF1A73E8), Color(0xFFD93025), Color(0xFF188038), Color(0xFFB45309), Color(0xFF9334E6),
        Color(0xFF0E7490), Color(0xFFBE185D), Color(0xFF5F6368), Color(0xFF00796B), Color(0xFF6D4C41))   // iniciales blancas ≥ 4.5:1
    /** Texto sobre los botones de acento: oscuro, para ≥ 6:1 de contraste con todos los acentos (el blanco sobre el azul solo daba 3.2:1). */
    val OnPrimary = Color(0xFF0B1020)
    val YouTube = Color(0xFFC62828)
}

/** Esquema de color para un acento dado (el contenedor se deriva del acento mezclado con negro). */
fun rupioScheme(primary: Color) = darkColorScheme(
    primary = primary, onPrimary = RupioColors.OnPrimary,
    primaryContainer = lerp(RupioColors.Background, primary, 0.28f), onPrimaryContainer = lerp(Color.White, primary, 0.2f),
    secondary = RupioColors.Success, onSecondary = Color.Black,
    secondaryContainer = Color(0xFF123A2B), onSecondaryContainer = Color(0xFFB8F5DA),
    tertiary = RupioColors.Warning, onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3D2C10), onTertiaryContainer = Color(0xFFFFE2B5),
    background = RupioColors.Background, onBackground = RupioColors.Text,
    surface = RupioColors.Background, onSurface = RupioColors.Text,
    surfaceVariant = RupioColors.Surface2, onSurfaceVariant = RupioColors.TextDim,
    surfaceContainerLowest = RupioColors.Background, surfaceContainerLow = RupioColors.Surface,
    surfaceContainer = RupioColors.Surface, surfaceContainerHigh = RupioColors.Surface2, surfaceContainerHighest = RupioColors.Surface3,
    // Los componentes de Material usan `outline` para el borde de campos, botones con borde, chips y switches.
    outline = RupioColors.ControlOutline, outlineVariant = RupioColors.Outline,
    error = RupioColors.Danger, onError = Color.Black,
    inverseSurface = RupioColors.Text, inverseOnSurface = RupioColors.Background,
)

/** Esquema con el acento por defecto (azul). */
val RupioScheme = rupioScheme(Color(0xFF5B8CFF))

private val base = Typography()
private fun TextStyle.tight() = copy(fontFamily = FontFamily.SansSerif)
val Typo = Typography(
    displaySmall = base.displaySmall.tight().copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.tight().copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.tight().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.tight().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.tight().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.tight().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.tight(), bodyMedium = base.bodyMedium.tight(), bodySmall = base.bodySmall.tight(),
    labelLarge = base.labelLarge.tight().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.tight(), labelSmall = base.labelSmall.tight(),
)

/** Números tabulares: los dígitos no "bailan" en cronómetros y tablas. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun RupioTheme(content: @Composable () -> Unit) {
    val primary = RupioColors.Primary
    val scheme = remember(primary) { rupioScheme(primary) }
    MaterialTheme(
        colorScheme = scheme, typography = Typo,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
