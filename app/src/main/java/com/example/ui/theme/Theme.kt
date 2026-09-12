package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The room vibe currently in effect. Defaults to the app-wide signature accent
 * (Crimson) outside of a room; RoomScreen/WatchScreen override this with
 * CompositionLocalProvider(LocalRoomVibe provides hostPickedVibe) so every
 * themed surface below them (buttons, slider, chat bubbles, glows) re-colors
 * automatically without threading a color through every composable.
 */
val LocalRoomVibe = compositionLocalOf { RoomVibe.Default }

/** Non-color design tokens: corner radii used for the cinematic card language. */
object UraniumShapes {
    val cardRadius = 20
    val chipRadius = 100 // fully rounded pills
}

private fun cinematicDarkScheme(vibe: RoomVibe) = darkColorScheme(
    primary = vibe.core,
    onPrimary = VoidBlack,
    secondary = vibe.secondary,
    onSecondary = VoidBlack,
    tertiary = vibe.glow,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = MistText,
    surface = AbyssSurface,
    onSurface = MistText,
    surfaceVariant = AbyssSurfaceElevated,
    onSurfaceVariant = MistTextMuted,
    outline = AbyssOutline,
    error = ErrorRed,
    onError = VoidBlack,
)

/**
 * Uranium TV is dark-cinematic only by design (streaming-app mood) - it does not
 * switch to a light theme or follow system dynamic color. [roomVibe] lets a
 * screen inside a room recolor the whole accent system to the host's pick while
 * keeping the same dark surfaces everywhere else.
 */
@Composable
fun MyApplicationTheme(
    roomVibe: RoomVibe = RoomVibe.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRoomVibe provides roomVibe) {
        MaterialTheme(
            colorScheme = cinematicDarkScheme(roomVibe),
            typography = Typography,
            content = content,
        )
    }
}
