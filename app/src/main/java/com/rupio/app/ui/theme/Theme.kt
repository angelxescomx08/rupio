package com.rupio.app.ui.theme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

/** Tema elegido en Perfil. [SYSTEM] sigue al modo oscuro del teléfono. */
enum class ThemeMode(val code: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object { fun of(code: String?): ThemeMode? = entries.firstOrNull { it.code == code } }
}

/**
 * Tema actual (estado de Compose: al cambiarlo, toda la app se repinta). [systemDark] lo actualiza MainActivity con
 * el modo del teléfono; si el teléfono cambia de modo, Android recrea la pantalla y se vuelve a leer.
 */
object Appearance {
    private val mode = mutableStateOf(ThemeMode.DARK)
    private val systemDark = mutableStateOf(true)
    val themeMode: ThemeMode get() = mode.value
    val isDark: Boolean get() = when (mode.value) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark.value }
    fun setMode(m: ThemeMode) { if (mode.value != m) mode.value = m }
    fun setSystemDark(dark: Boolean) { if (systemDark.value != dark) systemDark.value = dark }
}

/** Colores neutros y con significado de un tema. */
@Immutable
class Palette(
    val background: Color, val surface: Color, val surface2: Color, val surface3: Color,
    val outline: Color, val controlOutline: Color,
    val text: Color, val textDim: Color, val textFaint: Color,
    val success: Color, val warning: Color, val danger: Color, val rest: Color,
    val chart: Color, val heat: List<Color>, val supersets: List<Color>,
    /** Texto e íconos encima de un relleno de color (verde, rojo, superseries, píldoras llenas). */
    val onFill: Color,
)

/**
 * Paleta oscura (v2). Fondo casi negro (#0A0A0C, no #000): en OLED sigue ahorrando batería, pero evita el "smearing"
 * (los píxeles apagados tardan en encender al desplazar) y el halo del texto blanco puro sobre negro puro. Texto
 * #ECECEF (no blanco) por la misma razón. Todo medido con WCAG 2.2 (4.5:1 texto, 3:1 bordes de controles) en QaUiTest.
 */
val DarkPalette = Palette(
    background = Color(0xFF0A0A0C), surface = Color(0xFF131317), surface2 = Color(0xFF1B1B21), surface3 = Color(0xFF25252D),
    outline = Color(0xFF30303A), controlOutline = Color(0xFF6E6E7A),
    text = Color(0xFFECECEF), textDim = Color(0xFFA6A6B0), textFaint = Color(0xFF94949E),
    success = Color(0xFF3DDC97), warning = Color(0xFFFF9A3D), danger = Color(0xFFFF5C6C), rest = Color(0xFFB3A6FF),
    chart = Color(0xFF3987E5),
    heat = listOf(Color(0xFF184F95), Color(0xFF256ABF), Color(0xFF3987E5), Color(0xFF6DA7EC), Color(0xFF9EC5F4)),
    supersets = listOf(Color(0xFF9085E9), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181), Color(0xFF0CA30C)),
    onFill = Color.Black,
)

/**
 * Paleta clara (v2.0). Material 3 pone las tarjetas sobre un fondo apenas gris (superficie tono 98 y contenedores más
 * oscuros); aquí el fondo es gris azulado muy claro (#F4F5F8) y las tarjetas blancas, para que se distingan sin
 * sombras. Texto casi negro (#16171C, 14:1). Los colores con significado son los mismos tonos que en oscuro pero
 * oscurecidos: en claro el color va sobre blanco y necesita ser oscuro para leerse (todos ≥ 5:1 sobre la tarjeta más
 * oscura, #E6E8ED). Con fondo claro el texto se lee mejor con luz ambiental intensa, como la de un gimnasio
 * (Piepenbrock et al. 2013; Dobres et al. 2017). Ver docs/TEMA_IDIOMA_APOYO.md.
 */
val LightPalette = Palette(
    background = Color(0xFFF4F5F8), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEDEEF2), surface3 = Color(0xFFE6E8ED),
    outline = Color(0xFFD3D6DD), controlOutline = Color(0xFF767986),
    text = Color(0xFF16171C), textDim = Color(0xFF4A4C57), textFaint = Color(0xFF5C5E69),
    success = Color(0xFF0A6B41), warning = Color(0xFF954500), danger = Color(0xFFB01F2D), rest = Color(0xFF5B40C9),
    chart = Color(0xFF1E55BF),
    // Mapa de calor en claro: más entrenos = más oscuro (en oscuro es al revés: más claro).
    heat = listOf(Color(0xFFC9DCF7), Color(0xFF93B8EE), Color(0xFF5B8FE0), Color(0xFF2E66C8), Color(0xFF1A4596)),
    // Los mismos tonos de las superseries, oscurecidos: letra blanca encima ≥ 5:1 y ≥ 5:1 sobre la tarjeta blanca.
    supersets = listOf(Color(0xFF5B4FCF), Color(0xFFB24515), Color(0xFF0F7A57), Color(0xFF8F5F00), Color(0xFFB23A68), Color(0xFF2E7D18)),
    onFill = Color.White,
)

/**
 * Colores de la app. Cada uno se lee del tema actual (claro u oscuro) y del acento elegido: son estado de Compose, así
 * que al cambiar el tema o el acento toda la app se repinta.
 */
object RupioColors {
    private val p: Palette get() = if (Appearance.isDark) DarkPalette else LightPalette
    val Background: Color get() = p.background
    val Surface: Color get() = p.surface
    val Surface2: Color get() = p.surface2
    val Surface3: Color get() = p.surface3
    /** Solo para divisores y bordes decorativos de tarjetas (no identifica controles). */
    val Outline: Color get() = p.outline
    /** Borde de controles (campos de texto, botones con borde, chips): ≥ 3:1 sobre todas las superficies (WCAG 1.4.11). */
    val ControlOutline: Color get() = p.controlOutline
    val Text: Color get() = p.text
    val TextDim: Color get() = p.textDim
    val TextFaint: Color get() = p.textFaint   // ≥ 5:1 sobre todas las tarjetas (WCAG AA pide 4.5:1)
    /** Color de acento (acciones). Azul por defecto; quien apoya Rupio puede elegir otro (ver Accent). Cada acento tiene
     *  su versión para fondo oscuro (clara, con texto oscuro encima) y para fondo claro (oscura, con texto blanco). */
    private val accentDark = mutableStateOf(Color(0xFF5B8CFF))
    private val accentLight = mutableStateOf(Color(0xFF2850C4))
    val Primary: Color get() = if (Appearance.isDark) accentDark.value else accentLight.value
    fun applyAccent(dark: Color, light: Color) {
        if (accentDark.value != dark) accentDark.value = dark
        if (accentLight.value != light) accentLight.value = light
    }
    val Success: Color get() = p.success     // verde: serie completada
    val Warning: Color get() = p.warning     // naranja: sube peso / récords (más separado del dorado de los metales)
    val Danger: Color get() = p.danger
    val Rest: Color get() = p.rest           // lila: descansos
    /** Colores de superseries (A, B, C…), validados para daltonismo (ΔE CVD ≥ 8). Cada grupo lleva además letra +
     *  corchete → nunca depende solo del color. */
    val Supersets: List<Color> get() = p.supersets
    /** Gráficas: una sola serie en azul; mapa de calor = rampa secuencial azul. */
    val Chart: Color get() = p.chart
    val Heat: List<Color> get() = p.heat
    /** Colores de avatar al estilo Google (iniciales blancas ≥ 4.5:1 en los dos temas). */
    val Avatars = listOf(Color(0xFF1A73E8), Color(0xFFD93025), Color(0xFF188038), Color(0xFFB45309), Color(0xFF9334E6),
        Color(0xFF0E7490), Color(0xFFBE185D), Color(0xFF5F6368), Color(0xFF00796B), Color(0xFF6D4C41))
    /** Texto sobre los botones de acento: oscuro en el tema oscuro (≥ 6:1 con los acentos claros) y blanco en el claro. */
    val OnPrimary: Color get() = if (Appearance.isDark) Color(0xFF0B1020) else Color.White
    /** Texto e íconos sobre un relleno verde, rojo, naranja o de superserie. */
    val OnFill: Color get() = p.onFill
    val YouTube = Color(0xFFC62828)
}

/** Esquema de Material para el tema y el acento actuales (el contenedor se deriva del acento mezclado con el fondo). */
fun rupioScheme(primary: Color, dark: Boolean = Appearance.isDark): ColorScheme {
    val p = if (dark) DarkPalette else LightPalette
    val onPrimary = if (dark) Color(0xFF0B1020) else Color.White
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = lerp(p.background, primary, 0.28f), onPrimaryContainer = lerp(Color.White, primary, 0.2f),
        secondary = p.success, onSecondary = Color.Black,
        // v2.1: chips, segmentos y filtros elegidos con el acento (como la barra de abajo y las pestañas). Antes eran
        // verdes, el color de "serie completada", y no seguían el color que eligió quien apoya.
        secondaryContainer = lerp(p.background, primary, 0.28f), onSecondaryContainer = lerp(Color.White, primary, 0.2f),
        tertiary = p.warning, onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF3D2C10), onTertiaryContainer = Color(0xFFFFE2B5),
        background = p.background, onBackground = p.text,
        surface = p.background, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.textDim,
        surfaceContainerLowest = p.background, surfaceContainerLow = p.surface,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface2, surfaceContainerHighest = p.surface3,
        // Los componentes de Material usan `outline` para el borde de campos, botones con borde, chips y switches.
        outline = p.controlOutline, outlineVariant = p.outline,
        error = p.danger, onError = Color.Black,
        inverseSurface = p.text, inverseOnSurface = p.background,
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = lerp(Color.White, primary, 0.16f), onPrimaryContainer = lerp(Color.Black, primary, 0.55f),
        secondary = p.success, onSecondary = Color.White,
        secondaryContainer = lerp(Color.White, primary, 0.16f), onSecondaryContainer = lerp(Color.Black, primary, 0.55f),
        tertiary = p.warning, onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE3CC), onTertiaryContainer = Color(0xFF4A2200),
        background = p.background, onBackground = p.text,
        // Los diálogos y menús de Material usan las superficies: blancas, como las tarjetas.
        surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.textDim,
        surfaceContainerLowest = Color.White, surfaceContainerLow = p.background,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surface3,
        outline = p.controlOutline, outlineVariant = p.outline,
        error = p.danger, onError = Color.White,
        inverseSurface = Color(0xFF2B2C33), inverseOnSurface = Color(0xFFF1F1F4),
    )
}

/** Esquema con el acento por defecto (azul) en el tema actual. */
val RupioScheme: ColorScheme get() = rupioScheme(RupioColors.Primary)

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
    val dark = Appearance.isDark
    val scheme = remember(primary, dark) { rupioScheme(primary, dark) }
    MaterialTheme(
        colorScheme = scheme, typography = Typo,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
