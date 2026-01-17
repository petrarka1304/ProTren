package com.example.protren.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Ciemna paleta
private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    outline = Outline,
    error = Error,
    onError = OnError
)

// Jasna paleta – dopasuj nazwy kolorów do swoich stałych, jeśli masz inne.
private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    // zakładam, że masz odpowiednie jasne odpowiedniki
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    outline = Outline,
    error = Error,
    onError = OnError
)

@Composable
fun ProTrenTheme(
    useDynamic: Boolean = true,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicEnabled = useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Możesz tu w przyszłości dodać dynamic colors, na razie wybór dark/light
    val colorScheme: ColorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
