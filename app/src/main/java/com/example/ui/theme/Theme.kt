package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DinoNeonGreen,
    secondary = DinoTextMuted,
    tertiary = DinoLavaOrange,
    background = DinoDarkBg,
    surface = DinoDarkSurface,
    onPrimary = DinoDarkBg,
    onSecondary = DinoTextLight,
    onTertiary = DinoDarkBg,
    onBackground = DinoTextLight,
    onSurface = DinoTextLight,
    surfaceVariant = DinoCardBg,
    onSurfaceVariant = DinoTextLight,
    outline = DinoBorder,
    error = DinoVolcanicRed,
    onError = DinoTextLight
)

private val LightColorScheme = lightColorScheme(
    primary = DinoNeonGreen,
    secondary = DinoTextMuted,
    tertiary = DinoLavaOrange,
    background = DinoDarkBg, // Keep deep green dark-mode vibe as standard for Dino.AI
    surface = DinoDarkSurface,
    onPrimary = DinoDarkBg,
    onSecondary = DinoTextLight,
    onTertiary = DinoDarkBg,
    onBackground = DinoTextLight,
    onSurface = DinoTextLight,
    surfaceVariant = DinoCardBg,
    onSurfaceVariant = DinoTextLight,
    outline = DinoBorder,
    error = DinoVolcanicRed,
    onError = DinoTextLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set default dynamicColor to false to maintain the custom prehistoric cyber-punk aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
