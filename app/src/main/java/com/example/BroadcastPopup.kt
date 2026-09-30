package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AbyssOutline
import com.example.ui.theme.AbyssSurfaceElevated
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.CelebrateGlow
import com.example.ui.theme.CelebrateGreen
import com.example.ui.theme.CyanCore
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.MistText
import com.example.ui.theme.MistTextMuted
import com.example.ui.theme.UrgentGlow
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningGlow
import com.example.ui.theme.bouncyClick

private data class PopupAccent(
    val core: Color,
    val glow: Color
)

private fun resolveAccent(accent: String?): PopupAccent {
    return when (accent?.trim()?.lowercase()) {
        "celebrate" -> PopupAccent(core = CelebrateGreen, glow = CelebrateGlow)
        "warning" -> PopupAccent(core = WarningAmber, glow = WarningGlow)
        "urgent" -> PopupAccent(core = UrgentRed, glow = UrgentGlow)
        else -> PopupAccent(core = CyanCore, glow = CyanGlow)
    }
}

/**
 * Announcement popup modal rendered on the Home screen.
 * Follows the UraniumTV design system (dark cinematic surfaces, futuristic neon accents,
 * bouncy spring entrance, full accessibility touch targets, scrollable content).
 */
@Composable
fun BroadcastPopup(
    broadcast: Broadcast,
    myReaction: String?,
    onReact: (String?) -> Unit,
    onClose: () -> Unit
) {
    val accentColors = resolveAccent(broadcast.accent)
    val cardShape = RoundedCornerShape(28.dp)

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = scaleIn(
                    initialScale = 0.72f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .clip(cardShape)
                        .background(AbyssSurfaceElevated)
                        .border(1.5.dp, accentColors.core.copy(alpha = 0.75f), cardShape)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar: Dismiss X button (48.dp touch target)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close message",
                                tint = MistTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Optional Large Decorative Emoji Badge
                    if (broadcast.emoji.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(accentColors.glow.copy(alpha = 0.35f), Color.Transparent)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(66.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                accentColors.core.copy(alpha = 0.22f),
                                                accentColors.glow.copy(alpha = 0.12f)
                                            )
                                        ),
                                        CircleShape
                                    )
                                    .border(1.2.dp, accentColors.core.copy(alpha = 0.55f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = broadcast.emoji,
                                    fontSize = 38.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Title
                    Text(
                        text = broadcast.title,
                        color = MistText,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable Body
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = broadcast.body,
                            color = MistTextMuted,
                            fontFamily = BodyFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Optional Reactions Section
                    if (broadcast.reactionsEnabled) {
                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "React",
                            style = MaterialTheme.typography.labelSmall,
                            color = MistTextMuted,
                            fontFamily = BodyFontFamily,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val row1 = BROADCAST_REACTIONS.take(4)
                        val row2 = BROADCAST_REACTIONS.drop(4)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row1.forEach { emoji ->
                                    ReactionEmojiItem(
                                        emoji = emoji,
                                        isSelected = myReaction == emoji,
                                        accent = accentColors.core,
                                        onClick = {
                                            if (myReaction == emoji) onReact(null)
                                            else onReact(emoji)
                                        }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row2.forEach { emoji ->
                                    ReactionEmojiItem(
                                        emoji = emoji,
                                        isSelected = myReaction == emoji,
                                        accent = accentColors.core,
                                        onClick = {
                                            if (myReaction == emoji) onReact(null)
                                            else onReact(emoji)
                                        }
                                    )
                                }
                            }
                        }

                        // Reserved height for status confirmation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            this@Column.AnimatedVisibility(
                                visible = myReaction != null,
                                enter = fadeIn(animationSpec = tween(200)),
                                exit = fadeOut(animationSpec = tween(150))
                            ) {
                                Text(
                                    text = "Reaction sent ✓",
                                    color = accentColors.core,
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Full-width Primary Close Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(CyanCore, VioletGlow)))
                            .bouncyClick(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Close",
                            color = VoidBlack,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ReactionEmojiItem(
    emoji: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "emojiScale"
    )

    Box(
        modifier = Modifier
            .size(48.dp) // Minimum 48.dp touch target
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accent else AbyssOutline.copy(alpha = 0.6f),
                shape = CircleShape
            )
            .clickable(
                onClickLabel = "React with $emoji",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}
