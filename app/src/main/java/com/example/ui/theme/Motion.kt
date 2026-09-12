package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

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
