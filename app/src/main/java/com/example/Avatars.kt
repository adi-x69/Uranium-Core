package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A simple preset avatar: an id (persisted per-user), an emoji glyph, and a background color.
 * No image assets are required since the emoji is drawn directly.
 */
data class Avatar(
    val id: String,
    val emoji: String,
    val color: Color
)

val PRESET_AVATARS: List<Avatar> = listOf(
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

@Composable
fun AvatarCircle(
    avatar: Avatar,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = avatar.color, shape = CircleShape)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = avatar.emoji,
            fontSize = (size.value / 2).sp
        )
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
                        modifier = Modifier.clickable { onSelect(avatar.id) }
                    )
                }
            }
        }
    }
}
