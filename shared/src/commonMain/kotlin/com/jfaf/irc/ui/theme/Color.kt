package com.jfaf.irc.ui.theme

import androidx.compose.ui.graphics.Color

// Gradient Colors
val PurpleStart = Color(0xFF6A11CB)
val BlueEnd = Color(0xFF1E6AF0) // antes #2575FC: el blanco sobre él daba 4,2:1; ahora 4,8:1 (WCAG AA)

// Neutros del modo oscuro (UI-1): el chat va sobre un fondo plano y la marca queda para los acentos
val NeutralBackground = Color(0xFF1B1C20)       // fondo del chat
val NeutralSurface = Color(0xFF26272D)           // cajón, menús y diálogos
val NeutralSurfaceVariant = Color(0xFF33343B)    // campos de texto y contenedores
val NeutralOnSurface = Color(0xFFE8E8EC)         // texto principal (≈ 14:1 sobre el fondo)
val NeutralOnSurfaceMuted = Color(0xFFB4B6BE)    // avisos del sistema y texto secundario (≈ 8:1)
val NeutralOutline = Color(0xFF8D8F99)           // bordes (≥ 3:1, componentes)
val NeutralOutlineVariant = Color(0xFF3F4048)    // divisores
val LinkBlue = Color(0xFF8AB4FF)                 // enlaces (≈ 8:1 sobre el fondo)

// Tu propio nick en el modo oscuro (UI-8): violeta claro de la marca, ≈ 7:1 sobre el fondo
val OwnNickDark = Color(0xFFC4A5FF)

// Acento de las menciones de tu nick (UI-5): ámbar, más oscuro en el modo claro para que el borde se vea
val MentionAccentDark = Color(0xFFFFB300)
val MentionAccentLight = Color(0xFFB26A00)

// Neutros del modo claro (UI-2)
val LightBackground = Color(0xFFF6F6F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE6E7EC)
val LightOnSurface = Color(0xFF1B1C20)
val LightOnSurfaceMuted = Color(0xFF54565F)
val LightOutline = Color(0xFF767883)
val LightOutlineVariant = Color(0xFFD0D1D8)
val LinkBlueLight = Color(0xFF0B57D0)

// Base White
val IRCWhite = Color.White

// White variations with alpha for specific UI elements
// (Naming can be adjusted based on semantic usage in the theme)

// For text and icons that are slightly dimmed
val IRCWhite85 = Color.White.copy(alpha = 0.85f) // e.g., drawer item text (unselected)
val IRCWhite75 = Color.White.copy(alpha = 0.75f) // e.g., outlined text field label (unfocused)
val IRCWhite70 = Color.White.copy(alpha = 0.7f)  // e.g., checkbox unchecked, leading icons

// For borders or dividers
val IRCWhite60 = Color.White.copy(alpha = 0.6f)  // e.g., outlined text field border (unfocused)
val IRCWhite20 = Color.White.copy(alpha = 0.2f)  // e.g., drawer divider, text field container (non-outlined)

// For subtle backgrounds or containers
val IRCWhite08 = Color.White.copy(alpha = 0.08f) // e.g., outlined text field container
