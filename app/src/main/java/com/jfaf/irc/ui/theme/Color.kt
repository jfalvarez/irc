package com.jfaf.irc.ui.theme

import androidx.compose.ui.graphics.Color

// Gradient Colors
val PurpleStart = Color(0xFF6A11CB)
val BlueEnd = Color(0xFF2575FC)

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
