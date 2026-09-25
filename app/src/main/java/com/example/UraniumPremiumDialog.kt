package com.example

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object PremiumManager {
    val ADMIN_EMAILS = setOf(
        "adityamaurya2020june2009@gmail.com"
    )

    fun isHardcodedAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return ADMIN_EMAILS.contains(email.trim().lowercase())
    }

    fun sanitizeEmail(email: String): String {
        return email.trim().lowercase().replace(".", "_")
    }
}

/**
 * Ultra-aesthetic sci-fi animated dialog shown when non-premium users tap
 * the "Search Movies and Web Series" bar.
 *
 * Features:
 * 1. Room reactor background blurred behind the entire dialog.
 * 2. Rotating cyber laser perimeter border.
 * 3. Animated nuclear core lock with dual orbital electron rings.
 * 4. Premium perks showcasing exclusive access.
 * 5. Aesthetic gradient acknowledge/close button.
 */
@Composable
fun UraniumPremiumDialog(
    userUid: String,
    userEmail: String,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "premiumLoop")

    // Ambient plasma pulsation
    val plasmaGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "plasmaGlow"
    )

    // Rotating perimeter laser beam around card
    val laserAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserAngle"
    )

    // Dual counter-rotating atomic rings around the lock icon
    val orbitalAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitalAngle"
    )

    // Shimmer sweep across the header
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            // ================= 1. BLURRY ROOM REACTOR BACKGROUND =================
            Image(
                painter = painterResource(R.drawable.room_reactor_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= 31) {
                            Modifier.blur(22.dp)
                        } else {
                            Modifier
                        }
                    )
            )

            // Dark semi-transparent frosted vignette scrim over the blurred background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xC0060914),
                                Color(0xEB03050B)
                            )
                        )
                    )
            )

            // ================= 2. MAIN MODAL CARD =================
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(enabled = false) {} // Consume clicks inside card
            ) {
                // Layer 1: Animated Outer Laser Perimeter & Glass Card Background
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val corner = CornerRadius(28.dp.toPx(), 28.dp.toPx())

                    // Ambient radiant plasma behind card
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF1744).copy(alpha = 0.38f * plasmaGlow),
                                Color(0xFFFF9100).copy(alpha = 0.16f * plasmaGlow),
                                Color.Transparent
                            ),
                            center = Offset(w / 2f, h * 0.18f),
                            radius = w * 0.75f
                        ),
                        cornerRadius = corner
                    )

                    // Card dark obsidian glass background
                    drawRoundRect(
                        color = Color(0xF00D111C),
                        cornerRadius = corner
                    )

                    // Rotating cyber laser beam along the card perimeter
                    rotate(degrees = laserAngle, pivot = Offset(w / 2f, h / 2f)) {
                        drawRoundRect(
                            brush = Brush.sweepGradient(
                                0.0f to Color(0xFFFF1744).copy(alpha = 0.05f),
                                0.55f to Color(0xFFFF5252).copy(alpha = 0.25f),
                                0.82f to Color(0xFFFF1744),
                                0.95f to Color(0xFFFFD600), // Plasma gold flare
                                1.0f to Color.White // Hot white leading tip
                            ),
                            style = Stroke(width = 2.2.dp.toPx()),
                            cornerRadius = corner
                        )
                    }

                    // Inner neon accent border
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x33FF5252),
                                Color(0x1AFFFFFF),
                                Color(0x33FF9100)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        style = Stroke(width = 1.dp.toPx()),
                        cornerRadius = corner
                    )
                }

                // Layer 2: Card Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar: Clearance Tag & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Restricted clearance badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x2BFF1744),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF1744))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF1744))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "RESTRICTED CLEARANCE",
                                    color = Color(0xFFFF5252),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        // Close Icon Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Holographic Orbital Reactor Lock Animation
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val center = Offset(w / 2f, h / 2f)

                            // Outer pulsing plasma orb
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x66FF1744),
                                        Color(0x22FF9100),
                                        Color.Transparent
                                    )
                                ),
                                radius = (w / 2f) * plasmaGlow,
                                center = center
                            )

                            // Orbital electron ring 1 (clockwise)
                            rotate(degrees = orbitalAngle, pivot = center) {
                                drawOval(
                                    brush = Brush.sweepGradient(
                                        0.0f to Color.Transparent,
                                        0.7f to Color(0xFFFF5252).copy(alpha = 0.4f),
                                        1.0f to Color(0xFFFFD600)
                                    ),
                                    topLeft = Offset(w * 0.1f, h * 0.28f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.44f),
                                    style = Stroke(width = 1.8.dp.toPx())
                                )
                            }

                            // Orbital electron ring 2 (counter-clockwise)
                            rotate(degrees = -orbitalAngle * 1.3f, pivot = center) {
                                drawOval(
                                    brush = Brush.sweepGradient(
                                        0.0f to Color.Transparent,
                                        0.7f to Color(0xFFFF1744).copy(alpha = 0.5f),
                                        1.0f to Color.White
                                    ),
                                    topLeft = Offset(w * 0.12f, h * 0.24f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.52f),
                                    style = Stroke(width = 1.6.dp.toPx())
                                )
                            }
                        }

                        // Glowing Lock Icon
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0x44FF1744),
                                            Color(0xFF141926)
                                        )
                                    )
                                )
                                .border(1.dp, Color(0x55FF5252), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFFFD600),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = "Uranium TV Premium",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Movie and Web Series search is an exclusive privilege reserved for authorized Premium accounts.",
                        color = Color(0xFFAAB2C8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Perks Card (Cleaned & Streamlined)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF121624),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF1744))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            PremiumPerkRow(
                                title = "Browse Movies & Web Series",
                                subtitle = "Full access to streaming library & search engine"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PremiumPerkRow(
                                title = "VIP Server Pipeline",
                                subtitle = "Ultra low-latency, ad-shielded streaming routes"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Aesthetic Action / Close Button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onDismiss),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFFF1744),
                                            Color(0xFFFF5252),
                                            Color(0xFFFF9100)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ACKNOWLEDGE & CLOSE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumPerkRow(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0x33FFD600)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD600),
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = subtitle,
                color = Color(0xFF8A92A6),
                fontSize = 11.sp
            )
        }
    }
}
