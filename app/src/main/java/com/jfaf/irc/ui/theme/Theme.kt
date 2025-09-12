package com.jfaf.irc.ui.theme

import androidx.compose.material3.MaterialTheme
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
import com.jfaf.irc.ui.theme.IRCWhite20 // For surfaceVariant, outlineVariant (dividers)
import com.jfaf.irc.ui.theme.IRCWhite60 // For outline (borders)

// CompositionLocal for the gradient properties
val LocalGradientBrush = staticCompositionLocalOf<Brush> {
    error("No GradientBrush provided")
}
val LocalGradientColors = staticCompositionLocalOf<List<Color>> {
    error("No GradientColors provided")
}

// Define the Light ColorScheme for the application
private val LightColorScheme = lightColorScheme(
    primary = PurpleStart,
    onPrimary = IRCWhite,       // Text/icons on top of primary color
    secondary = BlueEnd,
    onSecondary = IRCWhite,     // Text/icons on top of secondary color

    background = PurpleStart,   // Default background for screens (can be overridden by a gradient)
    onBackground = IRCWhite,    // Text/icons on top of background color

    surface = PurpleStart.copy(alpha = 0.95f), // General surface color for components like Dialogs, Menus, Drawer sheets
    onSurface = IRCWhite,                      // Text/icons on top of surface color

    surfaceVariant = IRCWhite20, // Used for elements like TextField containers (non-outlined), Chips
    onSurfaceVariant = IRCWhite, // Text/icons on top of surfaceVariant elements (e.g., TextField labels)

    outline = IRCWhite60,        // Default border color (e.g., OutlinedTextField's unfocused border)
    outlineVariant = IRCWhite20, // Subtle borders or dividers

    // Other colors like error, tertiary, etc., will use Material 3 defaults.
    // error = Color(0xFFB00020), // Default error color
    // onError = Color.White,
)

@Composable
fun IRCAppTheme(
    // darkTheme: Boolean = isSystemInDarkTheme(), // Future: add dark theme support
    content: @Composable () -> Unit
) {
    val currentColorScheme = LightColorScheme // Can be switched with a darkColorScheme later

    val gradientColorsList = listOf(PurpleStart, BlueEnd)
    val gradientBrushInstance = Brush.verticalGradient(colors = gradientColorsList)

    CompositionLocalProvider(
        LocalGradientBrush provides gradientBrushInstance,
        LocalGradientColors provides gradientColorsList
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

// If specific alpha values for dialogs/menus are still needed beyond the general 'surface'
    val dialogContainerOpaque: Color
      @Composable
      @ReadOnlyComposable
      get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)

    val dropdownMenuContainerOpaque: Color
      @Composable
      @ReadOnlyComposable
      get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.98f)

    val drawerContainerOpaque: Color
      @Composable
      @ReadOnlyComposable
      get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.96f)
}
