package com.greenify.reborn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.greenify.reborn.model.ThemeMode

// Miuix / HyperOS-inspired surface & contrast hierarchy:
// Light: cool #F7F7F7 canvas with crisp #FFFFFF flat squircle cards
// Dark: pure #000000 OLED canvas with #1C1C1E flat squircle cards
private val MiuixLightColors = lightColorScheme(
    primary = Color(0xFF0D8450),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7F4E3),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4A6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EFEA),
    onSecondaryContainer = Color(0xFF14271D),
    tertiary = Color(0xFF31628D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E9FA),
    onTertiaryContainer = Color(0xFF001D34),
    error = Color(0xFFD93025),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE8E7),
    onErrorContainer = Color(0xFF601410),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF111111),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFEFF1),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    surfaceVariant = Color(0xFFEFEFF1),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFE5E5EA),
)

private val MiuixDarkColors = darkColorScheme(
    primary = Color(0xFF4CD985),
    onPrimary = Color(0xFF00381E),
    primaryContainer = Color(0xFF0D4A2E),
    onPrimaryContainer = Color(0xFFC5F7D9),
    secondary = Color(0xFFA7C4B2),
    onSecondary = Color(0xFF1B3326),
    secondaryContainer = Color(0xFF26332C),
    onSecondaryContainer = Color(0xFFD8EBE0),
    tertiary = Color(0xFF8ECAFF),
    onTertiary = Color(0xFF003356),
    tertiaryContainer = Color(0xFF163B59),
    onTertiaryContainer = Color(0xFFD6E9FA),
    error = Color(0xFFFF6961),
    onError = Color(0xFF4D0B07),
    errorContainer = Color(0xFF3B1515),
    onErrorContainer = Color(0xFFFFD8D6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF5F5F7),
    surfaceContainerLowest = Color(0xFF121214),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF2C2C2E),
)

// Tinted pair 1: Sage Light & Forest Dark (soft botanical emerald tint following Miuix hierarchy)
private val MiuixSageLightColors = MiuixLightColors.copy(
    background = Color(0xFFEFF5F1),
    surface = Color(0xFFEFF5F1),
    onBackground = Color(0xFF0E1B13),
    onSurface = Color(0xFF0E1B13),
    surfaceContainerLowest = Color(0xFFFAFDFA),
    surfaceContainerLow = Color(0xFFF8FCF9),
    surfaceContainer = Color(0xFFF8FCF9),
    surfaceContainerHigh = Color(0xFFE1EDE5),
    surfaceContainerHighest = Color(0xFFD5E5DA),
    surfaceVariant = Color(0xFFE1EDE5),
    onSurfaceVariant = Color(0xFF566B5E),
    outline = Color(0xFFC2D5C8),
    outlineVariant = Color(0xFFD6E5DB),
)

private val MiuixForestDarkColors = MiuixDarkColors.copy(
    background = Color(0xFF07100B),
    surface = Color(0xFF07100B),
    onBackground = Color(0xFFEEF7F1),
    onSurface = Color(0xFFEEF7F1),
    surfaceContainerLowest = Color(0xFF0D1712),
    surfaceContainerLow = Color(0xFF122018),
    surfaceContainer = Color(0xFF122018),
    surfaceContainerHigh = Color(0xFF1B2E23),
    surfaceContainerHighest = Color(0xFF253C2E),
    surfaceVariant = Color(0xFF1B2E23),
    onSurfaceVariant = Color(0xFF8AA394),
    outline = Color(0xFF294032),
    outlineVariant = Color(0xFF1E3226),
)

// Tinted pair 2: Mist Light & Midnight Dark (cool slate-blue tint following Miuix hierarchy)
private val MiuixMistLightColors = MiuixLightColors.copy(
    primary = Color(0xFF255EA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E6FB),
    onPrimaryContainer = Color(0xFF082040),
    secondary = Color(0xFF506075),
    secondaryContainer = Color(0xFFE4ECF6),
    onSecondaryContainer = Color(0xFF162233),
    background = Color(0xFFEEF3FA),
    surface = Color(0xFFEEF3FA),
    onBackground = Color(0xFF0F1622),
    onSurface = Color(0xFF0F1622),
    surfaceContainerLowest = Color(0xFFFAFBFD),
    surfaceContainerLow = Color(0xFFF8FAFE),
    surfaceContainer = Color(0xFFF8FAFE),
    surfaceContainerHigh = Color(0xFFDFE8F4),
    surfaceContainerHighest = Color(0xFFD3DFEE),
    surfaceVariant = Color(0xFFDFE8F4),
    onSurfaceVariant = Color(0xFF5A687A),
    outline = Color(0xFFC3D0E2),
    outlineVariant = Color(0xFFD6E0EE),
)

private val MiuixMidnightDarkColors = MiuixDarkColors.copy(
    primary = Color(0xFF76B5FF),
    onPrimary = Color(0xFF002E5C),
    primaryContainer = Color(0xFF134074),
    onPrimaryContainer = Color(0xFFD4E6FF),
    secondary = Color(0xFF9FB4CC),
    secondaryContainer = Color(0xFF202B3D),
    onSecondaryContainer = Color(0xFFD4E1F2),
    background = Color(0xFF080D16),
    surface = Color(0xFF080D16),
    onBackground = Color(0xFFEEF3FA),
    onSurface = Color(0xFFEEF3FA),
    surfaceContainerLowest = Color(0xFF0E1522),
    surfaceContainerLow = Color(0xFF131D2D),
    surfaceContainer = Color(0xFF131D2D),
    surfaceContainerHigh = Color(0xFF1D2B40),
    surfaceContainerHighest = Color(0xFF273852),
    surfaceVariant = Color(0xFF1D2B40),
    onSurfaceVariant = Color(0xFF8D9CB2),
    outline = Color(0xFF2B3C55),
    outlineVariant = Color(0xFF202F45),
)

private val MiuixTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun GreenifyRebornTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())

    val context = LocalContext.current
    val baseScheme = when (themeMode) {
        ThemeMode.SYSTEM -> if (darkTheme) MiuixDarkColors else MiuixLightColors
        ThemeMode.LIGHT -> MiuixLightColors
        ThemeMode.DARK -> MiuixDarkColors
        ThemeMode.SAGE_LIGHT -> MiuixSageLightColors
        ThemeMode.FOREST_DARK -> MiuixForestDarkColors
        ThemeMode.MIST_LIGHT -> MiuixMistLightColors
        ThemeMode.MIDNIGHT_DARK -> MiuixMidnightDarkColors
    }
    val colorScheme = if (dynamicColor) {
        val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // Keep Miuix's signature high-contrast canvas & flat card hierarchy even with Monet accent
        baseScheme.copy(
            primary = dyn.primary,
            onPrimary = dyn.onPrimary,
            primaryContainer = dyn.primaryContainer,
            onPrimaryContainer = dyn.onPrimaryContainer,
            secondaryContainer = dyn.secondaryContainer,
            onSecondaryContainer = dyn.onSecondaryContainer,
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiuixTypography,
        content = content,
    )
}
