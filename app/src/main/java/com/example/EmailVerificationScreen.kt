package com.example

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.bouncyClick
import kotlin.math.*
import kotlin.random.Random

// High-voltage sci-fi color palette
private val DangerCrimson = Color(0xFFFF1744)
private val DangerNeonRed = Color(0xFFFF5252)
private val ToxicAcidGreen = Color(0xFF39FF14)
private val CyberNeonCyan = Color(0xFF00E5FF)
private val ElectricGold = Color(0xFFFFD600)
private val HazardAmber = Color(0xFFFF9100)
private val VoidDeepSpace = Color(0xFF05060A)
private val DarkGlass = Color(0xCC0D1019)
private val MutedSilver = Color(0xFF8A92A6)

@Composable
fun EmailVerificationScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    otpInput: String,
    onOtpInputChange: (String) -> Unit,
    otpSent: Boolean,
    isSendingOtp: Boolean,
    isVerifying: Boolean,
    otpGeneratedAt: Long,
    otpValidityMs: Long,
    onRequestOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onChangeEmail: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    // Infinite transitions for reactor core animations
    val transition = rememberInfiniteTransition(label = "reactorLoop")
    val coreRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "coreRotation"
    )
    val counterRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "counterRotation"
    )
    val alarmPulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alarmPulse"
    )
    val laserSweep by transition.animateFloat(
        initialValue = -100f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "laserSweep"
    )
    val gradientShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradientShift"
    )

    // Dynamic timer countdown for OTP validity
    var timeLeftMs by remember { mutableLongStateOf(otpValidityMs) }
    LaunchedEffect(otpSent, otpGeneratedAt) {
        if (otpSent && otpGeneratedAt > 0) {
            while (true) {
                val elapsed = System.currentTimeMillis() - otpGeneratedAt
                val remaining = (otpValidityMs - elapsed).coerceAtLeast(0L)
                timeLeftMs = remaining
                if (remaining <= 0L) break
                kotlinx.coroutines.delay(500L)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidDeepSpace)
    ) {
        // 1. Dangerous animated nuclear background canvas
        DangerousReactorBackground(
            coreRotation = coreRotation,
            counterRotation = counterRotation,
            alarmPulse = alarmPulse,
            gradientShift = gradientShift
        )

        // 2. High-tech scanline & grid overlay
        CyberGridOverlay(laserSweep = laserSweep)

        // 3. Main interactive content card
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
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
                            .border(1.dp, DangerCrimson.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Live Radiation Hazard Strobe Pill
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        DangerCrimson.copy(alpha = 0.25f * alarmPulse),
                                        HazardAmber.copy(alpha = 0.25f * alarmPulse)
                                    )
                                ),
                                RoundedCornerShape(100.dp)
                            )
                            .border(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        DangerCrimson.copy(alpha = 0.8f * alarmPulse),
                                        HazardAmber.copy(alpha = 0.8f * alarmPulse)
                                    )
                                ),
                                RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(DangerCrimson, CircleShape)
                                .shadow(6.dp, CircleShape, spotColor = DangerCrimson)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (otpSent) "DECRYPTING OTP" else "CLEARANCE REQ",
                            color = Color(0xFFFFE082),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Central Animated Reactor Warning Core
                AnimatedReactorCoreIcon(
                    rotation = coreRotation,
                    pulse = alarmPulse,
                    isVerifying = isVerifying || isSendingOtp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Cinematic Dangerous Header Title
                Text(
                    text = if (otpSent) "QUANTUM SECURITY LINK" else "URANIUM CORE ACCESS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle with radioactive glow styling
                Text(
                    text = if (otpSent) {
                        "AUTHENTICATION CIPHER TRANSMITTED TO\n${email.trim().lowercase()}"
                    } else {
                        "ENTER PROTOCOL EMAIL FOR LEVEL 4\nIDENTITY VERIFICATION & ENCRYPTION KEY"
                    },
                    color = Color(0xFF9EABB8),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // High-Tech Sci-Fi Glass Container Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkGlass)
                        .border(
                            1.5.dp,
                            Brush.linearGradient(
                                listOf(
                                    DangerCrimson.copy(alpha = 0.9f),
                                    CyberNeonCyan.copy(alpha = 0.7f),
                                    ToxicAcidGreen.copy(alpha = 0.8f),
                                    DangerCrimson.copy(alpha = 0.9f)
                                )
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    if (!otpSent) {
                        // PHASE 1: EMAIL INPUT SCREEN
                        EmailInputSection(
                            email = email,
                            onEmailChange = onEmailChange,
                            isSendingOtp = isSendingOtp,
                            onRequestOtp = {
                                keyboardController?.hide()
                                onRequestOtp()
                            },
                            pulse = alarmPulse
                        )
                    } else {
                        // PHASE 2: 6-DIGIT OTP DANGEROUS FUEL CELLS SCREEN
                        OtpVerificationSection(
                            otpInput = otpInput,
                            onOtpInputChange = onOtpInputChange,
                            isVerifying = isVerifying,
                            timeLeftMs = timeLeftMs,
                            onVerifyOtp = {
                                keyboardController?.hide()
                                onVerifyOtp()
                            },
                            onResendOtp = {
                                onRequestOtp()
                            },
                            onChangeEmail = onChangeEmail,
                            pulse = alarmPulse
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Security Notice Footer with atomic icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = CyberNeonCyan.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ENCRYPTED VIA 256-BIT QUANTUM SIGNALS",
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 1: DANGEROUS EMAIL INPUT
// -------------------------------------------------------------
@Composable
private fun EmailInputSection(
    email: String,
    onEmailChange: (String) -> Unit,
    isSendingOtp: Boolean,
    onRequestOtp: () -> Unit,
    pulse: Float
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Label with glowing icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = DangerNeonRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "COMMUNICATION TERMINAL",
                color = DangerNeonRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Cybernetic Styled Email Input Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(Color(0xFF080B12), RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (email.isNotBlank()) CyberNeonCyan else Color(0xFF263045),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = email,
                onValueChange = onEmailChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (!isSendingOtp) onRequestOtp() }),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(CyberNeonCyan),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (email.isEmpty()) {
                        Text(
                            text = "operator@uraniumtv.com",
                            color = Color(0xFF4A5568),
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dangerous Glowing Multi-Gradient "AUTHORIZE & TRANSMIT" Button
        DangerousGradientButton(
            text = if (isSendingOtp) "TRANSMITTING CIPHER..." else "TRANSMIT ACCESS CODE",
            isLoading = isSendingOtp,
            enabled = !isSendingOtp && email.isNotBlank(),
            pulse = pulse,
            gradient = listOf(DangerCrimson, HazardAmber, CyberNeonCyan),
            onClick = onRequestOtp
        )
    }
}

// -------------------------------------------------------------
// SECTION 2: 6-DIGIT INTERACTIVE FUEL CELL OTP
// -------------------------------------------------------------
@Composable
private fun OtpVerificationSection(
    otpInput: String,
    onOtpInputChange: (String) -> Unit,
    isVerifying: Boolean,
    timeLeftMs: Long,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onChangeEmail: () -> Unit,
    pulse: Float
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Auto-request focus for quick fluid typing
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {}
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Countdown timer & status HUD
        val secondsLeft = (timeLeftMs / 1000).toInt()
        val minutes = secondsLeft / 60
        val remainingSec = secondsLeft % 60
        val isExpired = timeLeftMs <= 0

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isExpired) DangerCrimson else ToxicAcidGreen, CircleShape)
                        .shadow(6.dp, CircleShape, spotColor = if (isExpired) DangerCrimson else ToxicAcidGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isExpired) "CODE EXPIRED" else "COOL-DOWN TIMER",
                    color = if (isExpired) DangerCrimson else ToxicAcidGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = String.format("%02d:%02d", minutes, remainingSec),
                color = if (secondsLeft < 60) DangerCrimson else Color(0xFFFFD54F),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Invisible text field capturing numeric keyboard inputs
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = otpInput,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(6)
                    onOtpInputChange(filtered)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (otpInput.length == 6 && !isVerifying && !isExpired) {
                            onVerifyOtp()
                        }
                    }
                ),
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusRequester)
            )

            // 6 Dangerous Reactor Fuel Cell Digit Boxes
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                }
            ) {
                for (i in 0 until 6) {
                    val digit = otpInput.getOrNull(i)?.toString() ?: ""
                    val isFocused = otpInput.length == i
                    val isFilled = digit.isNotEmpty()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.82f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isFilled -> Color(0x3300E5FF)
                                    isFocused -> Color(0x22FF1744)
                                    else -> Color(0xFF090D17)
                                }
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.2.dp,
                                brush = when {
                                    isFilled -> Brush.verticalGradient(listOf(CyberNeonCyan, Color(0xFF007799)))
                                    isFocused -> Brush.verticalGradient(listOf(DangerCrimson, HazardAmber))
                                    else -> SolidColor(Color(0xFF222B3D))
                                },
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFilled) {
                            Text(
                                text = digit,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        } else if (isFocused) {
                            // Flashing radioactive cursor bar
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(3.dp)
                                    .background(DangerCrimson.copy(alpha = pulse), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Verify and Enter Button
        DangerousGradientButton(
            text = if (isVerifying) "VERIFYING CODE..." else "VERIFY & ENTER",
            isLoading = isVerifying,
            enabled = !isVerifying && otpInput.length == 6 && !isExpired,
            pulse = pulse,
            gradient = listOf(ToxicAcidGreen, CyberNeonCyan, DangerCrimson),
            onClick = onVerifyOtp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Resend and Switch Email controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onResendOtp,
                modifier = Modifier.bouncyClick(onClick = onResendOtp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = CyberNeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RESEND CIPHER",
                    color = CyberNeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            TextButton(
                onClick = onChangeEmail,
                modifier = Modifier.bouncyClick(onClick = onChangeEmail)
            ) {
                Text(
                    text = "RE-ENTER EMAIL",
                    color = Color(0xFF8A92A6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// -------------------------------------------------------------
// DANGEROUS HIGH-VOLTAGE BUTTON
// -------------------------------------------------------------
@Composable
private fun DangerousGradientButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    pulse: Float,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    val buttonBrush = if (enabled) {
        Brush.horizontalGradient(gradient)
    } else {
        SolidColor(Color(0xFF222834))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(buttonBrush)
            .drawBehind {
                if (enabled) {
                    // Futuristic hazard diagonal striping
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
                RoundedCornerShape(12.dp)
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
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Text(
                text = text,
                color = if (enabled) Color.White else Color(0xFF636E80),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// -------------------------------------------------------------
// ANIMATED REACTOR CORE ICON
// -------------------------------------------------------------
@Composable
private fun AnimatedReactorCoreIcon(
    rotation: Float,
    pulse: Float,
    isVerifying: Boolean
) {
    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radioactive aura bloom
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f

            // Outer radioactive diffuse halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DangerCrimson.copy(alpha = 0.45f * pulse),
                        HazardAmber.copy(alpha = 0.25f * pulse),
                        CyberNeonCyan.copy(alpha = 0.15f * pulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius
                )
            )

            // Outer Hazard Stator Ring
            rotate(rotation, center) {
                drawCircle(
                    color = DangerCrimson.copy(alpha = 0.8f),
                    radius = baseRadius * 0.82f,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
                    )
                )
            }

            // Inner Counter-Rotating Magnetic Shield
            rotate(-rotation * 1.5f, center) {
                drawCircle(
                    color = CyberNeonCyan.copy(alpha = 0.9f),
                    radius = baseRadius * 0.62f,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }

            // Nuclear Tri-Foil Hazard Blades
            rotate(rotation * 0.5f, center) {
                for (i in 0..2) {
                    val angle = (i * 120f) * (PI / 180f)
                    val arcStartAngle = (i * 120f) - 30f
                    drawArc(
                        color = if (isVerifying) ToxicAcidGreen else HazardAmber,
                        startAngle = arcStartAngle,
                        sweepAngle = 60f,
                        useCenter = true,
                        topLeft = Offset(center.x - baseRadius * 0.45f, center.y - baseRadius * 0.45f),
                        size = Size(baseRadius * 0.9f, baseRadius * 0.9f)
                    )
                }
            }

            // Core Energy Eye
            drawCircle(
                color = VoidDeepSpace,
                radius = baseRadius * 0.22f
            )
            drawCircle(
                color = if (isVerifying) ToxicAcidGreen else Color.White,
                radius = baseRadius * 0.14f * pulse
            )
        }
    }
}

// -------------------------------------------------------------
// DANGEROUS REACTOR BACKGROUND CANVAS
// -------------------------------------------------------------
@Composable
private fun DangerousReactorBackground(
    coreRotation: Float,
    counterRotation: Float,
    alarmPulse: Float,
    gradientShift: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h * 0.35f)

        // 1. Radiant pulsing plasma gradients
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    DangerCrimson.copy(alpha = 0.35f * alarmPulse),
                    Color(0xFF550015).copy(alpha = 0.30f),
                    Color(0xFF001A33).copy(alpha = 0.45f),
                    VoidDeepSpace
                ),
                center = center,
                radius = w * 1.1f
            )
        )

        // 2. Secondary toxic green radioactive core glow from bottom
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    ToxicAcidGreen.copy(alpha = 0.15f * alarmPulse),
                    CyberNeonCyan.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(w * 0.8f, h * 0.85f),
                radius = w * 0.75f
            )
        )

        // 3. Huge cosmic orbital hazard rings
        rotate(coreRotation, center) {
            drawCircle(
                color = DangerCrimson.copy(alpha = 0.18f),
                radius = w * 0.65f,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 30f), 0f)
                )
            )
        }

        rotate(counterRotation, center) {
            drawCircle(
                color = CyberNeonCyan.copy(alpha = 0.14f),
                radius = w * 0.85f,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(60f, 40f), 0f)
                )
            )
        }
    }
}

// -------------------------------------------------------------
// CYBER SCANLINE & GRID OVERLAY
// -------------------------------------------------------------
@Composable
private fun CyberGridOverlay(laserSweep: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Subtle tech grid lines
        val gridStep = 48.dp.toPx()
        var y = 0f
        while (y < h) {
            drawLine(
                color = Color(0x0A00E5FF),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
            y += gridStep
        }

        // Horizontal laser beam sweeping down like a radar scan
        val beamY = (laserSweep % (h + 200f)) - 100f
        if (beamY in 0f..h) {
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        CyberNeonCyan.copy(alpha = 0.6f),
                        DangerCrimson.copy(alpha = 0.8f),
                        CyberNeonCyan.copy(alpha = 0.6f),
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
