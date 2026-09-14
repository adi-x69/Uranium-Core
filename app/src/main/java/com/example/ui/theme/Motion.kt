package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared motion tokens so every screen's micro-animations feel like one system:
 * "subtle & premium" - smooth fades/slides, nothing bouncy or flashy. Prefer
 * these over ad-hoc tween() calls when adding new animations.
 */
object UraniumMotion {
    // A gentle deceleration curve - quick start, soft landing. No overshoot/bounce.
    val PremiumEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    const val Fast = 180
    const val Medium = 320
    const val Slow = 520

    fun <T> fade(durationMs: Int = Medium): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = PremiumEasing)

    fun <T> slide(durationMs: Int = Medium): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = PremiumEasing)
}

/**
 * Option 3: Playful & Micro-Interactive.
 * Applies a bouncy scale down effect when the component is pressed.
 */
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "bouncyClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Disable default ripple for a cleaner bounce
            onClick = onClick
        )
}
