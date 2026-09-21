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

import com.example.ui.theme.bouncyClick

/**
 * A superhero avatar: unique id, background aura color, drawable resource, and hero name.
 * All avatars are high-fidelity superhero character graphics.
 */
data class Avatar(
    val id: String,
    val color: Color = Color(0xFFEF5350),
    val drawableRes: Int,
    val name: String = ""
)

val SUPERHERO_AVATARS: List<Avatar> = listOf(
    Avatar("iron_man", Color(0xFFD32F2F), R.drawable.iron_man, "Iron Man"),
    Avatar("spiderman", Color(0xFFE53935), R.drawable.spiderman, "Spider-Man"),
    Avatar("deadpool", Color(0xFFC62828), R.drawable.deadpool, "Deadpool"),
    Avatar("wolverine", Color(0xFFFBC02D), R.drawable.wolverine, "Wolverine"),
    Avatar("hulk", Color(0xFF388E3C), R.drawable.hulk, "Hulk"),
    Avatar("she_hulk", Color(0xFF43A047), R.drawable.she_hulk, "She-Hulk"),
    Avatar("groot", Color(0xFF6D4C41), R.drawable.groot, "Groot"),
    Avatar("wanda", Color(0xFF8E24AA), R.drawable.wanda, "Scarlet Witch")
)

// Alias references for project-wide backwards compatibility
val MARVEL_AVATARS: List<Avatar> = SUPERHERO_AVATARS
val PRESET_AVATARS: List<Avatar> = SUPERHERO_AVATARS

/**
 * Looks up a superhero avatar by id.
 * Any legacy emoji avatar id (e.g. "avatar_1") or invalid id automatically falls back to Iron Man.
 */
fun avatarById(id: String?): Avatar =
    SUPERHERO_AVATARS.firstOrNull { it.id == id } ?: SUPERHERO_AVATARS[0]

/**
 * Performance-optimized pulsing glow ring that only runs infinite animations when active.
 */
@Composable
private fun PulsingGlowRing(size: Dp, color: Color) {
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

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = pulseAlpha
            }
            .border(width = 2.5.dp, color = color, shape = CircleShape)
    )
}

/**
 * Renders a circular superhero avatar image with optional active-presence glow or selection border.
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

    Box(contentAlignment = Alignment.Center) {
        // Optimized: Only allocate and tick infinite animation when pulsing is true
        if (pulsing) {
            PulsingGlowRing(size = size, color = glowColor)
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
                                width = 3.dp,
                                color = Color(0xFF00F5FF),
                                shape = CircleShape
                            )
                            .padding(1.5.dp)
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
            Image(
                painter = painterResource(avatar.drawableRes),
                contentDescription = avatar.name.ifEmpty { avatar.id },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Clean superhero avatar selection grid (4 avatars per row, 2 rows total).
 */
@Composable
fun AvatarPickerGrid(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SUPERHERO_AVATARS.chunked(columns).forEach { rowAvatars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowAvatars.forEach { avatar ->
                    AvatarCircle(
                        avatar = avatar,
                        size = 58.dp,
                        selected = avatar.id == selectedId,
                        modifier = Modifier.bouncyClick { onSelect(avatar.id) }
                    )
                }
            }
        }
    }
}

