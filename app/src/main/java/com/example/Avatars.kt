package com.example

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.ui.theme.bouncyClick

/**
 * A preset avatar: an id (persisted per-user), an optional drawable resource (for custom Marvel avatars),
 * or an emoji glyph fallback, and a background/glow color.
 */
data class Avatar(
    val id: String,
    val emoji: String = "⚡",
    val color: Color = Color(0xFFEF5350),
    val drawableRes: Int? = null,
    val name: String = ""
)

val MARVEL_AVATARS: List<Avatar> = listOf(
    Avatar("iron_man", "🤖", Color(0xFFD32F2F), R.drawable.iron_man, "Iron Man"),
    Avatar("spiderman", "🕷️", Color(0xFFE53935), R.drawable.spiderman, "Spider-Man"),
    Avatar("deadpool", "⚔️", Color(0xFFC62828), R.drawable.deadpool, "Deadpool"),
    Avatar("wolverine", "🐺", Color(0xFFFBC02D), R.drawable.wolverine, "Wolverine"),
    Avatar("hulk", "💪", Color(0xFF388E3C), R.drawable.hulk, "Hulk"),
    Avatar("she_hulk", "⚖️", Color(0xFF43A047), R.drawable.she_hulk, "She-Hulk"),
    Avatar("groot", "🌳", Color(0xFF6D4C41), R.drawable.groot, "Groot"),
    Avatar("wanda", "🔮", Color(0xFF8E24AA), R.drawable.wanda, "Scarlet Witch")
)

val PRESET_AVATARS: List<Avatar> = MARVEL_AVATARS + listOf(
    Avatar("avatar_1", "🦄", Color(0xFF7C4DFF)),
    Avatar("avatar_2", "🐱", Color(0xFFFF7043)),
    Avatar("avatar_3", "🐶", Color(0xFF26A69A)),
    Avatar("avatar_4", "🦊", Color(0xFFFFA726)),
    Avatar("avatar_5", "🐼", Color(0xFF42A5F5)),
    Avatar("avatar_6", "🐸", Color(0xFF66BB6A)),
    Avatar("avatar_7", "🐵", Color(0xFF8D6E63)),
    Avatar("avatar_8", "🐧", Color(0xFF5C6BC0)),
    Avatar("avatar_9", "🦁", Color(0xFFEF5350)),
    Avatar("avatar_10", "🐨", Color(0xFF78909C)),
    Avatar("avatar_11", "🐯", Color(0xFFFFCA28)),
    Avatar("avatar_12", "🐙", Color(0xFFAB47BC))
)

/** Looks up a preset avatar by id, falling back to the first preset if not found. */
fun avatarById(id: String?): Avatar =
    PRESET_AVATARS.firstOrNull { it.id == id } ?: PRESET_AVATARS[0]

/**
 * @param pulsing When true, draws an animated glow ring around the avatar to indicate
 * the person is actively present (e.g. currently in the room/watch screen). Pulses
 * opacity + scale on a loop using [MaterialTheme]'s primary (room-vibe) color, so it
 * automatically matches whatever [com.example.ui.theme.RoomVibe] is in effect.
 */
@Composable
fun AvatarCircle(
    avatar: Avatar,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    pulsing: Boolean = false
) {
    val glowColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "avatarPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .border(width = 2.5.dp, color = glowColor, shape = CircleShape)
            )
        }
        Box(
            modifier = modifier
                .size(size)
                .background(color = avatar.color, shape = CircleShape)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier
                            .border(
                                width = 3.5.dp,
                                color = Color(0xFF00F5FF),
                                shape = CircleShape
                            )
                            .padding(1.dp)
                            .border(
                                width = 1.5.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (avatar.drawableRes != null) {
                Image(
                    painter = painterResource(avatar.drawableRes),
                    contentDescription = avatar.name.ifEmpty { avatar.id },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = avatar.emoji,
                    fontSize = (size.value / 2).sp
                )
            }
        }
    }
}

@Composable
fun AvatarPickerGrid(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4
) {
    // Plain chunked rows instead of LazyVerticalGrid: with only a dozen presets there's
    // no need for laziness, and a Lazy grid can't be nested inside another scrollable
    // container (like SignupScreen's verticalScroll Column) without a fixed height -
    // doing so throws "measured with an infinity maximum height constraints" at runtime.
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PRESET_AVATARS.chunked(columns).forEach { rowAvatars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowAvatars.forEach { avatar ->
                    AvatarCircle(
                        avatar = avatar,
                        size = 56.dp,
                        selected = avatar.id == selectedId,
                        modifier = Modifier.bouncyClick { onSelect(avatar.id) }
                    )
                }
            }
        }
    }
}
