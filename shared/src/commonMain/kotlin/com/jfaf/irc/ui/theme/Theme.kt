package com.jfaf.irc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Import specific colors from our Color.kt
import com.jfaf.irc.ui.theme.PurpleStart
import com.jfaf.irc.ui.theme.BlueEnd
import com.jfaf.irc.ui.theme.IRCWhite
import com.jfaf.irc.ui.theme.IRCWhite08 // For specific TextField container

// Si el tema activo es el oscuro
val LocalDarkTheme = staticCompositionLocalOf { true }

// CompositionLocal for the gradient properties
val LocalGradientBrush = staticCompositionLocalOf<Brush> {
    error("No GradientBrush provided")
}
val LocalGradientColors = staticCompositionLocalOf<List<Color>> {
    error("No GradientColors provided")
}

// Esquema claro (UI-2): misma marca (morado en la barra y las selecciones) sobre fondos claros.
private val LightColorScheme = lightColorScheme(
    primary = PurpleStart,
    onPrimary = IRCWhite,
    secondary = BlueEnd,
    onSecondary = IRCWhite,

    background = LightBackground,
    onBackground = LightOnSurface,

    surface = LightSurface,
    onSurface = LightOnSurface,

    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

// Esquema oscuro: fondo neutro y marca (morado) sólo en barra, selecciones, botón de enviar e insignias (UI-1).
private val DarkColorScheme = darkColorScheme(
    primary = PurpleStart,
    onPrimary = IRCWhite,       // Texto e iconos sobre la marca (8,2:1)
    secondary = BlueEnd,
    onSecondary = IRCWhite,

    background = NeutralBackground,
    onBackground = NeutralOnSurface,

    surface = NeutralSurface,   // Cajón, menús y diálogos
    onSurface = NeutralOnSurface,

    surfaceVariant = NeutralSurfaceVariant,
    onSurfaceVariant = NeutralOnSurfaceMuted,

    outline = NeutralOutline,
    outlineVariant = NeutralOutlineVariant,
)

@Composable
fun IRCAppTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val currentColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val gradientColorsList = listOf(PurpleStart, BlueEnd)
    val gradientBrushInstance = Brush.verticalGradient(colors = gradientColorsList)

    CompositionLocalProvider(
        LocalGradientBrush provides gradientBrushInstance,
        LocalGradientColors provides gradientColorsList,
        LocalDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = currentColorScheme,
            typography = Typography, // From Type.kt
            shapes = Shapes,         // From Shapes.kt
            content = content
        )
    }
}

// Helper object to easily access our custom theme properties, like the gradient or very specific colors.
object IRCTheme {
    val gradientBrush: Brush
        @Composable
        @ReadOnlyComposable
        get() = LocalGradientBrush.current

    val gradientColors: List<Color>
        @Composable
        @ReadOnlyComposable
        get() = LocalGradientColors.current

    // Example of a specific color not directly mapped or needing a unique alpha:
    val outlinedTextFieldContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = IRCWhite08 // As defined in Color.kt

    // Diálogos, menús y cajón: superficie neutra (antes, el morado de la marca con transparencia)
    val dialogContainerOpaque: Color
      @Composable
      @ReadOnlyComposable
      get() = MaterialTheme.colorScheme.surface

    val dropdownMenuContainerOpaque: Color
      @Composable
      @ReadOnlyComposable
      get() = MaterialTheme.colorScheme.surface

    val drawerContainerOpaque: Color
      @Composable
      @ReadOnlyComposable
      get() = MaterialTheme.colorScheme.surface

    // Color de los enlaces de los mensajes
    val linkColor: Color
      @Composable
      @ReadOnlyComposable
      get() = if (LocalDarkTheme.current) LinkBlue else LinkBlueLight
}
