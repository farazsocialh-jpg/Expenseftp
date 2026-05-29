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
    primary = SleekPrimaryDark,
    onPrimary = SleekOnPrimaryDark,
    primaryContainer = SleekPrimaryContainerDark,
    onPrimaryContainer = SleekOnPrimaryContainerDark,
    secondary = SleekSecondaryDark,
    onSecondary = SleekOnSecondaryDark,
    secondaryContainer = SleekSecondaryContainerDark,
    onSecondaryContainer = SleekOnSecondaryContainerDark,
    background = SleekBgDark,
    onBackground = SleekTextLight,
    surface = SleekSurfaceDark,
    onSurface = SleekOnSurfaceDark,
    surfaceVariant = SleekSurfaceVariantDark,
    onSurfaceVariant = SleekOnSurfaceVariantDark,
    outline = SleekOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = SleekPrimaryLight,
    onPrimary = SleekOnPrimaryLight,
    primaryContainer = SleekPrimaryContainerLight,
    onPrimaryContainer = SleekOnPrimaryContainerLight,
    secondary = SleekSecondaryLight,
    onSecondary = SleekOnSecondaryLight,
    secondaryContainer = SleekSecondaryContainerLight,
    onSecondaryContainer = SleekOnSecondaryContainerLight,
    background = SleekBgLight,
    onBackground = SleekTextDark,
    surface = SleekSurfaceLight,
    onSurface = SleekOnSurfaceLight,
    surfaceVariant = SleekSurfaceVariantLight,
    onSurfaceVariant = SleekOnSurfaceVariantLight,
    outline = SleekOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set to false by default to show our custom Sleek Theme palette faithfully
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
