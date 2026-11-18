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
import com.greenify.reborn.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6D3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA3F5B5),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF4F6353),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF0D1F13),
    tertiary = Color(0xFF3A6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF7),
    onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D99B),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFFA3F5B5),
    secondary = Color(0xFFB6CCB8),
    onSecondary = Color(0xFF223527),
    secondaryContainer = Color(0xFF384B3C),
    onSecondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFFA2CDDB),
    onTertiary = Color(0xFF023640),
    tertiaryContainer = Color(0xFF214C58),
    onTertiaryContainer = Color(0xFFBEEAF7),
    background = Color(0xFF101510),
    onBackground = Color(0xFFDFE4DC),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFDFE4DC),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8B938B),
)

@Composable
fun GreenifyRebornTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = if (dynamicColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
