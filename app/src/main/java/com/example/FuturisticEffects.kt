package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.bouncyClick

// Sci-fi Uranium TV color constants
val NeonCrimson = Color(0xFFFF1744)
val NeonDangerRed = Color(0xFFFF5252)
val NeonToxicGreen = Color(0xFF39FF14)
val NeonCyberCyan = Color(0xFF00E5FF)
val NeonElectricGold = Color(0xFFFFD600)
val NeonHazardAmber = Color(0xFFFF9100)
val VoidSpaceBlack = Color(0xFF05060A)
val CyberDarkGlass = Color(0xCC0D1019)
val CyberMutedText = Color(0xFF8A92A6)

/**
 * Fullscreen animated reactor background with rotating rings, pulsing plasma,
 * grid lines, and sweeping laser scanner beam.
 */
@Composable
fun FuturisticCyberBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "cyberLoop")
    val coreRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "coreRotation"
    )
    val counterRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart),
        label = "counterRotation"
    )
    val alarmPulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alarmPulse"
    )
    val laserSweep by transition.animateFloat(
        initialValue = -100f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "laserSweep"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidSpaceBlack)
    ) {
        // 1. Radiant pulsing plasma & rotating orbital rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h * 0.28f)

            // Radiant plasma nebula
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonCrimson.copy(alpha = 0.32f * alarmPulse),
                        Color(0xFF550015).copy(alpha = 0.25f),
                        Color(0xFF001A33).copy(alpha = 0.35f),
                        VoidSpaceBlack
                    ),
                    center = center,
                    radius = w * 1.1f
                )
            )

            // Secondary cyan-acid bloom from bottom
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonToxicGreen.copy(alpha = 0.12f * alarmPulse),
                        NeonCyberCyan.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.85f),
                    radius = w * 0.75f
                )
            )

            // Rotating dashed orbital rings
            rotate(coreRotation, center) {
                drawCircle(
                    color = NeonCrimson.copy(alpha = 0.16f),
                    radius = w * 0.62f,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 30f), 0f)
                    )
                )
            }

            rotate(counterRotation, center) {
                drawCircle(
                    color = NeonCyberCyan.copy(alpha = 0.12f),
                    radius = w * 0.82f,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(60f, 40f), 0f)
                    )
                )
            }
        }

        // 2. Subtle cyber grid & sweeping laser radar
        FuturisticScannerOverlay(laserSweep = laserSweep)

        // 3. Child content
        content()
    }
}

/**
 * Transparent overlay containing cybernetic grid lines and an animated sweeping laser beam.
 * Can be placed over any existing artwork/screen to give it an instant high-tech holographic feel.
 */
@Composable
fun FuturisticScannerOverlay(
    modifier: Modifier = Modifier,
    laserSweep: Float? = null
) {
    val localSweep = if (laserSweep != null) {
        laserSweep
    } else {
        val transition = rememberInfiniteTransition(label = "scannerLoop")
        val sweep by transition.animateFloat(
            initialValue = -100f,
            targetValue = 1400f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
            label = "sweep"
        )
        sweep
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Horizontal laser radar scanline
        val beamY = (localSweep % (h + 200f)) - 100f
        if (beamY in 0f..h) {
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        NeonCyberCyan.copy(alpha = 0.5f),
                        NeonCrimson.copy(alpha = 0.8f),
                        NeonCyberCyan.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, beamY),
                end = Offset(w, beamY),
                strokeWidth = 2.5.dp.toPx()
            )
        }
    }
}

/**
 * High-tech glassmorphic card with quad-color neon gradient borders.
 */
@Composable
fun FuturisticGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.5.dp,
    borderColors: List<Color> = listOf(
        NeonCrimson.copy(alpha = 0.85f),
        NeonCyberCyan.copy(alpha = 0.70f),
        NeonToxicGreen.copy(alpha = 0.75f),
        NeonCrimson.copy(alpha = 0.85f)
    ),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(CyberDarkGlass)
            .border(
                borderWidth,
                Brush.linearGradient(borderColors),
                RoundedCornerShape(cornerRadius)
            )
    ) {
        content()
    }
}

/**
 * High-voltage button with animated diagonal hazard stripes and multi-stop gradients.
 */
@Composable
fun FuturisticHazardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    gradient: List<Color> = listOf(NeonCrimson, NeonHazardAmber, NeonCyberCyan),
    height: Dp = 52.dp,
    cornerRadius: Dp = 12.dp
) {
    val transition = rememberInfiniteTransition(label = "hazardPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hazardPulse"
    )

    val buttonBrush = if (enabled) {
        Brush.horizontalGradient(gradient)
    } else {
        SolidColor(Color(0xFF1E2330))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(buttonBrush)
            .drawBehind {
                if (enabled) {
                    val stripeWidth = 14.dp.toPx()
                    val totalWidth = size.width + size.height
                    var x = -size.height
                    while (x < totalWidth) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.12f),
                            start = Offset(x, 0f),
                            end = Offset(x + size.height, size.height),
                            strokeWidth = stripeWidth
                        )
                        x += stripeWidth * 2
                    }
                }
            }
            .border(
                1.5.dp,
                if (enabled) Color.White.copy(alpha = 0.35f * pulse) else Color.Transparent,
                RoundedCornerShape(cornerRadius)
            )
            .bouncyClick {
                if (enabled && !isLoading) onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Text(
                text = text,
                color = if (enabled) Color.White else Color(0xFF5A6475),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Live status strobe indicator pill (e.g. "QUANTUM LINK", "ONLINE", "SECTOR SECURE").
 */
@Composable
fun FuturisticStatusPill(
    statusText: String,
    modifier: Modifier = Modifier,
    dotColor: Color = NeonToxicGreen,
    textColor: Color = Color(0xFFFFE082)
) {
    val transition = rememberInfiniteTransition(label = "pillPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pillPulse"
    )

    Row(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    listOf(
                        dotColor.copy(alpha = 0.20f * pulse),
                        Color(0xFF101524)
                    )
                ),
                RoundedCornerShape(100.dp)
            )
            .border(
                1.dp,
                dotColor.copy(alpha = 0.7f * pulse),
                RoundedCornerShape(100.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, CircleShape)
                .shadow(6.dp, CircleShape, spotColor = dotColor)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = statusText,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Top bar header with high-tech back button and status indicator.
 */
@Composable
fun FuturisticTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: () -> Unit,
    statusText: String = "SECTOR SECURE",
    statusColor: Color = NeonToxicGreen
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .size(44.dp)
                .background(Color(0x33FF1744), CircleShape)
                .border(1.dp, NeonCrimson.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go Back",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.SansSerif
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = CyberMutedText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        FuturisticStatusPill(
            statusText = statusText,
            dotColor = statusColor
        )
    }
}

/**
 * Cybernetic input field with neon glowing border on focus and monospace typography.
 */
@Composable
fun FuturisticTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF080B12), RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (value.isNotBlank()) NeonCyberCyan else Color(0xFF263045),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(10.dp))
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(NeonCyberCyan),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Color(0xFF4A5568),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        innerTextField()
                    }
                )
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailingIcon()
            }
        }
    }
}
