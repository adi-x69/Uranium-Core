package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Base cinematic surfaces (near-black, not pure black so cards/elevation read) ----
val VoidBlack = Color(0xFF06070A)
val AbyssSurface = Color(0xFF0E1016)
val AbyssSurfaceElevated = Color(0xFF161923)
val AbyssOutline = Color(0xFF2A2E3B)
val MistText = Color(0xFFE7E9F0)
val MistTextMuted = Color(0xFFA0A5B8)

// ---- Default app accent: crimson + cyan (the "signature" Uranium TV look) ----
val CrimsonCore = Color(0xFFFF2E4D)
val CrimsonGlow = Color(0xFFFF6B7F)
val CyanCore = Color(0xFF19E8E0)
val CyanGlow = Color(0xFF7BFFF7)

// ---- Alternate accent family: violet + purple (used by room vibes) ----
val VioletCore = Color(0xFF8B5CF6)
val VioletGlow = Color(0xFFB794FF)
val PurpleCore = Color(0xFF6D28D9)
val PurpleGlow = Color(0xFF9B6BFF)

// Status colors reused across chat / presence
val OnlineGreen = Color(0xFF3DDC84)
val SeenBlue = Color(0xFF4FA8FF)
val ErrorRed = Color(0xFFFF5470)

/**
 * A "Room Vibe" is a selectable accent pairing the host picks for a room; everyone
 * inside sees it applied to the Room + Watch screens (buttons, slider, chat bubbles,
 * glow accents) while the underlying dark surfaces stay constant for legibility.
 */
enum class RoomVibe(
    val displayName: String,
    val core: Color,
    val glow: Color,
    val secondary: Color,
) {
    Crimson(displayName = "Crimson", core = CrimsonCore, glow = CrimsonGlow, secondary = CyanCore),
    CyanFrost(displayName = "Cyan Frost", core = CyanCore, glow = CyanGlow, secondary = CrimsonCore),
    VioletDream(displayName = "Violet Dream", core = VioletCore, glow = VioletGlow, secondary = CyanCore),
    UltraViolet(displayName = "Ultraviolet", core = PurpleCore, glow = PurpleGlow, secondary = CrimsonGlow);

    companion object {
        val Default = Crimson
        fun fromId(id: String?): RoomVibe = entries.find { it.name == id } ?: Default
    }
}
