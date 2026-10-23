package com.zenradio.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zenradio.app.R

/**
 * Todos los colores de un tema de ZenRadio. Los componentes los leen con
 * [ZenColors], así que cambiar de tema en Ajustes repinta la app al momento.
 */
@Immutable
data class ZenPalette(
    val id: String,
    val name: String,
    val isLight: Boolean,
    // Fondo: degradado vertical y dos focos de luz que flotan.
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    /** Intensidad de halos y focos de color de cada emisora (1 = normal). */
    val glowStrength: Float,
    // Acento principal y sus variantes.
    val accent: Color,
    val accentSoft: Color,
    val accentDeep: Color,
    val onAccent: Color,
    /** Textos e iconos en color de acento sobre cristal. */
    val accentText: Color,
    /** Texto de estado "EN VIVO". */
    val live: Color,
    /** Punto de estado cuando la radio está en pausa. */
    val idle: Color,
    // Texto.
    val textPrimary: Color,
    val textSecondary: Color,
    // Cristal: relleno y borde, ambos en degradado diagonal.
    val glassTop: Color,
    val glassBottom: Color,
    val glassEdgeTop: Color,
    val glassEdgeBottom: Color,
    // Superficies y controles.
    val sheet: Color,
    val miniPlayer: Color,
    val chip: Color,
    val outline: Color,
    val switchOff: Color,
    val shadow: Color,
    val error: Color,
    // Efectos del fondo.
    val particle: Color,
    val wave: Color,
    val waveFill: Color,
    val grainAlpha: Float,
    // Color propio de cada emisora y colores del logotipo.
    val stationAccents: List<Color>,
    val brandMoon: Color,
    val brandBars: List<Color>,
)

object ZenPalettes {

    /** A · Océano profundo: azul marino con acentos turquesa (tema por defecto). */
    val Oceano = ZenPalette(
        id = "oceano",
        name = "Océano",
        isLight = false,
        backgroundTop = Color(0xFF0B2238),
        backgroundMid = Color(0xFF071829),
        backgroundBottom = Color(0xFF040E1A),
        glowPrimary = Color(0x801E6FD9),
        glowSecondary = Color(0x6B2A4BAA),
        glowStrength = 1f,
        accent = Color(0xFF5CE1E6),
        accentSoft = Color(0xFFA5F3FC),
        accentDeep = Color(0xFF2F80ED),
        onAccent = Color(0xFF04121F),
        accentText = Color(0xFFA5F3FC),
        live = Color(0xFF5CE1E6),
        idle = Color(0xFF56718A),
        textPrimary = Color(0xFFEAF6FF),
        textSecondary = Color(0xFF93AFC8),
        glassTop = Color.White.copy(alpha = 0.10f),
        glassBottom = Color.White.copy(alpha = 0.03f),
        glassEdgeTop = Color.White.copy(alpha = 0.22f),
        glassEdgeBottom = Color.White.copy(alpha = 0.05f),
        sheet = Color(0xFF0A1F33),
        miniPlayer = Color(0xCC081C2E),
        chip = Color.White.copy(alpha = 0.05f),
        outline = Color.White.copy(alpha = 0.12f),
        switchOff = Color.White.copy(alpha = 0.18f),
        shadow = Color(0x8C020A14),
        error = Color(0xFFFF9DB0),
        particle = Color(0xFFCFF6FF),
        wave = Color(0xFFA5F3FC),
        waveFill = Color(0x1F5CE1E6),
        grainAlpha = 0.07f,
        stationAccents = listOf(
            Color(0xFF5CE1E6), Color(0xFF7AA7FF), Color(0xFF4FD1C5),
            Color(0xFF38BDF8), Color(0xFFA5B4FC), Color(0xFF6EE7B7),
        ),
        brandMoon = Color(0xFFDDF3FF),
        brandBars = listOf(Color(0xFF5CE1E6), Color(0xFF2F80ED), Color(0xFF7AA7FF)),
    )

    /** B · Medianoche: índigo profundo con acentos lavanda azulada. */
    val Medianoche = ZenPalette(
        id = "medianoche",
        name = "Medianoche",
        isLight = false,
        backgroundTop = Color(0xFF151C42),
        backgroundMid = Color(0xFF0B1030),
        backgroundBottom = Color(0xFF070A1F),
        glowPrimary = Color(0x804654C8),
        glowSecondary = Color(0x7A2D3A8C),
        glowStrength = 0.95f,
        accent = Color(0xFF8FA2FF),
        accentSoft = Color(0xFFC7D0FF),
        accentDeep = Color(0xFF5B6CF0),
        onAccent = Color(0xFF0B1030),
        accentText = Color(0xFFC7D0FF),
        live = Color(0xFFB4C0FF),
        idle = Color(0xFF5E6690),
        textPrimary = Color(0xFFEEF0FF),
        textSecondary = Color(0xFFA3ABD6),
        glassTop = Color.White.copy(alpha = 0.10f),
        glassBottom = Color.White.copy(alpha = 0.03f),
        glassEdgeTop = Color.White.copy(alpha = 0.22f),
        glassEdgeBottom = Color.White.copy(alpha = 0.05f),
        sheet = Color(0xFF10163A),
        miniPlayer = Color(0xD10E1334),
        chip = Color.White.copy(alpha = 0.05f),
        outline = Color.White.copy(alpha = 0.12f),
        switchOff = Color.White.copy(alpha = 0.18f),
        shadow = Color(0x8C040614),
        error = Color(0xFFFF9DB0),
        particle = Color(0xFFDDE3FF),
        wave = Color(0xFFC7D0FF),
        waveFill = Color(0x1F8FA2FF),
        grainAlpha = 0.07f,
        stationAccents = listOf(
            Color(0xFF8FA2FF), Color(0xFFB4C0FF), Color(0xFF7FB6FF),
            Color(0xFFA78BFA), Color(0xFF7DD3FC), Color(0xFFC4B5FD),
        ),
        brandMoon = Color(0xFFE3E8FF),
        brandBars = listOf(Color(0xFF8FA2FF), Color(0xFF5B6CF0), Color(0xFFB4C0FF)),
    )

    /** C · Glaciar: modo claro, blanco hielo con azul cobalto. */
    val Glaciar = ZenPalette(
        id = "glaciar",
        name = "Glaciar",
        isLight = true,
        backgroundTop = Color(0xFFF4F8FC),
        backgroundMid = Color(0xFFE6EFF8),
        backgroundBottom = Color(0xFFD9E6F4),
        glowPrimary = Color(0x8C7CB4FF),
        glowSecondary = Color(0x80A5C8FF),
        glowStrength = 0.8f,
        accent = Color(0xFF2563EB),
        accentSoft = Color(0xFF60A5FA),
        accentDeep = Color(0xFF1D4ED8),
        onAccent = Color.White,
        accentText = Color(0xFF1D4ED8),
        live = Color(0xFF1D4ED8),
        idle = Color(0xFF94A3B8),
        textPrimary = Color(0xFF0B1B2E),
        textSecondary = Color(0xFF4A607A),
        glassTop = Color.White.copy(alpha = 0.78f),
        glassBottom = Color.White.copy(alpha = 0.46f),
        glassEdgeTop = Color.White.copy(alpha = 0.95f),
        glassEdgeBottom = Color(0x170B1B2E),
        sheet = Color(0xFFF4F8FC),
        miniPlayer = Color(0xE0FFFFFF),
        chip = Color.White.copy(alpha = 0.6f),
        outline = Color(0x1F0B1B2E),
        switchOff = Color(0x2E0B1B2E),
        shadow = Color(0x2E1E4078),
        error = Color(0xFFC0264A),
        particle = Color(0xFF3B82F6),
        wave = Color(0xFF2563EB),
        waveFill = Color(0x2960A5FA),
        grainAlpha = 0.05f,
        stationAccents = listOf(
            Color(0xFF0EA5E9), Color(0xFF2563EB), Color(0xFF0891B2),
            Color(0xFF4F46E5), Color(0xFF0D9488), Color(0xFF3B82F6),
        ),
        brandMoon = Color(0xFF1D4ED8),
        brandBars = listOf(Color(0xFF3B82F6), Color(0xFF0EA5E9), Color(0xFF60A5FA)),
    )

    /** El morado original (v1), para quien lo prefiera. */
    val Morado = ZenPalette(
        id = "morado",
        name = "Morado",
        isLight = false,
        backgroundTop = Color(0xFF1A0B2E),
        backgroundMid = Color(0xFF0D0518),
        backgroundBottom = Color(0xFF08030F),
        glowPrimary = Color(0xA64B1D7A),
        glowSecondary = Color(0x5CE056FD),
        glowStrength = 1f,
        accent = Color(0xFFB24BF3),
        accentSoft = Color(0xFFD9A8FF),
        accentDeep = Color(0xFF6A23B5),
        onAccent = Color.White,
        accentText = Color(0xFFD9A8FF),
        live = Color(0xFFD9A8FF),
        idle = Color(0xFF7D6A99),
        textPrimary = Color(0xFFF3E9FF),
        textSecondary = Color(0xFFB9A6D3),
        glassTop = Color.White.copy(alpha = 0.10f),
        glassBottom = Color.White.copy(alpha = 0.03f),
        glassEdgeTop = Color.White.copy(alpha = 0.22f),
        glassEdgeBottom = Color.White.copy(alpha = 0.05f),
        sheet = Color(0xFF1A0B2E),
        miniPlayer = Color(0xD11A0B2E),
        chip = Color.White.copy(alpha = 0.05f),
        outline = Color.White.copy(alpha = 0.12f),
        switchOff = Color.White.copy(alpha = 0.18f),
        shadow = Color(0x8C08030F),
        error = Color(0xFFFF8FB1),
        particle = Color(0xFFEBD7FF),
        wave = Color(0xFFD9A8FF),
        waveFill = Color(0x1FB24BF3),
        grainAlpha = 0.07f,
        stationAccents = listOf(
            Color(0xFFB24BF3), Color(0xFFE056FD), Color(0xFF8A7CFF),
            Color(0xFFF472B6), Color(0xFFC084FC), Color(0xFF818CF8),
        ),
        brandMoon = Color(0xFFD9A8FF),
        brandBars = listOf(Color(0xFFB24BF3), Color(0xFFE056FD), Color(0xFF8A3FD1)),
    )

    val all = listOf(Oceano, Medianoche, Glaciar, Morado)

    val Default = Oceano

    fun byId(id: String?): ZenPalette = all.firstOrNull { it.id == id } ?: Default
}

val LocalZenPalette = staticCompositionLocalOf { ZenPalettes.Default }

/** Paleta activa. Uso: `ZenColors.accent`, `ZenColors.textSecondary`… */
val ZenColors: ZenPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalZenPalette.current

/** Manrope: geométrica y redondeada, la misma del prototipo visual. */
private val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

private val ZenTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.3.sp,
    ),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp,
    ),
)

/** Traducción de la paleta a Material 3 para diálogos, campos de texto y hojas. */
private fun ZenPalette.toColorScheme(): ColorScheme = if (isLight) {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = accentSoft,
        onSecondary = onAccent,
        background = backgroundMid,
        onBackground = textPrimary,
        surface = sheet,
        onSurface = textPrimary,
        surfaceTint = Color.Transparent,
        surfaceVariant = backgroundMid,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = sheet,
        surfaceContainerLow = sheet,
        surfaceContainer = sheet,
        surfaceContainerHigh = sheet,
        surfaceContainerHighest = sheet,
        outline = textSecondary.copy(alpha = 0.5f),
        outlineVariant = outline,
        error = error,
    )
} else {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = accentSoft,
        onSecondary = onAccent,
        background = backgroundMid,
        onBackground = textPrimary,
        surface = sheet,
        onSurface = textPrimary,
        surfaceTint = Color.Transparent,
        surfaceVariant = backgroundTop,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = sheet,
        surfaceContainerLow = sheet,
        surfaceContainer = sheet,
        surfaceContainerHigh = sheet,
        surfaceContainerHighest = sheet,
        outline = textSecondary.copy(alpha = 0.5f),
        outlineVariant = outline,
        error = error,
    )
}

@Composable
fun ZenRadioTheme(
    palette: ZenPalette = ZenPalettes.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(palette) { palette.toColorScheme() }
    CompositionLocalProvider(LocalZenPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = ZenTypography) {
            CompositionLocalProvider(LocalContentColor provides palette.textPrimary, content = content)
        }
    }
}
