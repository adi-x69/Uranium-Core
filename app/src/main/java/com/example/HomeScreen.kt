package com.example

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import com.example.ui.theme.bouncyClick
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.UraniumMotion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.Canvas
import kotlin.math.sin

data class WatchInvite(
    val id: String,
    val fromUsername: String,
    val roomCode: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRoom: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToWatch: (String) -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    var joinRoomCode by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var invites by remember { mutableStateOf<List<WatchInvite>>(emptyList()) }
    var continueWatching by remember { mutableStateOf<List<ContinueWatchingEntry>>(emptyList()) }
    var onlineFriends by remember { mutableStateOf<List<FriendProfile>>(emptyList()) }
    var myAvatarId by remember { mutableStateOf(PRESET_AVATARS[0].id) }

    val db = remember { FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""

    // So the profile button in the top bar shows this user's own avatar.
    DisposableEffect(uid) {
        val avatarRef = db.child("users").child(uid).child("avatarId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                myAvatarId = snapshot.getValue(String::class.java) ?: PRESET_AVATARS[0].id
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        avatarRef.addValueEventListener(listener)
        onDispose { avatarRef.removeEventListener(listener) }
    }

    // Listen for incoming "watch together" invites from friends
    DisposableEffect(uid) {
        val invitesRef = db.child("users").child(uid).child("watchInvites")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                invites = snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    val fromUsername = child.child("fromUsername").getValue(String::class.java) ?: "Someone"
                    val roomCode = child.child("roomCode").getValue(String::class.java) ?: return@mapNotNull null
                    WatchInvite(id, fromUsername, roomCode)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        invitesRef.addValueEventListener(listener)
        onDispose { invitesRef.removeEventListener(listener) }
    }

    // Continue Watching: per-room resume points for this user, most recent first.
    DisposableEffect(uid) {
        val cwRef = db.child("users").child(uid).child("continueWatching")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                continueWatching = snapshot.children
                    .mapNotNull { child -> child.key?.let { code -> child.toContinueWatchingEntry(code) } }
                    .sortedByDescending { it.updatedAt }
                    .take(10)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        cwRef.addValueEventListener(listener)
        onDispose { cwRef.removeEventListener(listener) }
    }

    // Friends + presence, for the online-avatars row.
    DisposableEffect(uid) {
        val friendsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val friendIds = snapshot.children.mapNotNull { it.key }
                if (friendIds.isEmpty()) {
                    onlineFriends = emptyList()
                    return
                }
                val resolved = mutableListOf<FriendProfile>()
                var remaining = friendIds.size
                friendIds.forEach { fid ->
                    fetchFriendProfile(db, fid) { profile ->
                        resolved.add(profile)
                        remaining--
                        if (remaining == 0) {
                            onlineFriends = resolved
                                .filter { it.presenceStatus != "offline" }
                                .sortedBy { it.username }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("users").child(uid).child("friends").addValueEventListener(friendsListener)
        onDispose { db.child("users").child(uid).child("friends").removeEventListener(friendsListener) }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ReactorHomeHero(
                myAvatarId = myAvatarId,
                joinRoomCode = joinRoomCode,
                onJoinRoomCodeChange = { joinRoomCode = it },
                isCreating = isCreating,
                isJoining = isJoining,
                onNavigateToProfile = onNavigateToProfile,
                onCreateRoom = {
                    isCreating = true
                    val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                    val roomCode = (1..6).map { allowedChars.random() }.joinToString("")

                    val roomData = mapOf(
                        "videoUrl" to "",
                        "isPlaying" to false,
                        "position" to 0L,
                        "lastUpdatedBy" to uid,
                        "lastUpdatedAt" to ServerValue.TIMESTAMP,
                        "hostUid" to uid,
                        "controlsUnlocked" to false,
                        "vibe" to com.example.ui.theme.RoomVibe.Default.name
                    )

                    db.child("rooms").child(roomCode).setValue(roomData)
                        .addOnSuccessListener {
                            isCreating = false
                            onNavigateToRoom(roomCode)
                        }
                        .addOnFailureListener { exception ->
                            isCreating = false
                            Toast.makeText(context, "Failed: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                },
                onJoinRoom = {
                    if (joinRoomCode.isBlank()) {
                        Toast.makeText(context, "Enter a room code", Toast.LENGTH_SHORT).show()
                        return@ReactorHomeHero
                    }
                    isJoining = true
                    db.child("rooms").child(joinRoomCode).get()
                        .addOnSuccessListener { snapshot ->
                            isJoining = false
                            if (snapshot.exists()) {
                                onNavigateToRoom(joinRoomCode)
                            } else {
                                Toast.makeText(context, "Room not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            isJoining = false
                            Toast.makeText(context, "Error checking room", Toast.LENGTH_SHORT).show()
                        }
                },
                onNavigateToFriends = onNavigateToFriends
            )
            } // Close the scrollable Column

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = onlineFriends.isNotEmpty(),
                    enter = fadeIn(UraniumMotion.fade()),
                    exit = fadeOut(UraniumMotion.fade())
                ) {
                    OnlineFriendsRow(friends = onlineFriends, onClick = { onNavigateToFriends() })
                }

                AnimatedVisibility(
                    visible = continueWatching.isNotEmpty(),
                    enter = fadeIn(UraniumMotion.fade()),
                    exit = fadeOut(UraniumMotion.fade())
                ) {
                    ContinueWatchingRow(
                        entries = continueWatching,
                        onResume = { entry -> onNavigateToWatch(entry.roomCode) }
                    )
                }

                if (invites.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        invites.forEach { invite ->
                            key(invite.id) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = scaleIn(
                                        initialScale = 0.6f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    ) + fadeIn(animationSpec = tween(180)),
                                    exit = fadeOut(UraniumMotion.fade())
                                ) {
                                    Surface(
                                        color = com.example.ui.theme.AbyssSurfaceElevated,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.5.dp, com.example.ui.theme.CrimsonCore),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(com.example.ui.theme.CrimsonCore.copy(alpha = 0.18f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("🎬", fontSize = 18.sp)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = invite.fromUsername,
                                                    color = com.example.ui.theme.MistText,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = "invited you to watch · Room ${invite.roomCode}",
                                                    color = com.example.ui.theme.MistTextMuted,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            TextButton(onClick = {
                                                db.child("users").child(uid).child("watchInvites")
                                                    .child(invite.id).removeValue()
                                                onNavigateToRoom(invite.roomCode)
                                            }) {
                                                Text(
                                                    "Join",
                                                    color = com.example.ui.theme.CrimsonCore,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            IconButton(onClick = {
                                                db.child("users").child(uid).child("watchInvites")
                                                    .child(invite.id).removeValue()
                                            }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Dismiss",
                                                    tint = com.example.ui.theme.MistTextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The reactor-skinned hero: a single background image (frame, logo, dial, decorative
 * readouts) with real interactive elements positioned on top of it by fraction of the
 * hero's own width/height. Fractions were measured directly against the background
 * artwork's pixel dimensions, so this holds its layout regardless of screen size -
 * the hero box itself is locked to the artwork's aspect ratio via .aspectRatio(),
 * meaning nothing stretches or distorts, it just scales up/down as one unit.
 */
@Composable
private fun ReactorHomeHero(
    myAvatarId: String,
    joinRoomCode: String,
    onJoinRoomCodeChange: (String) -> Unit,
    isCreating: Boolean,
    isJoining: Boolean,
    onNavigateToProfile: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onNavigateToFriends: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(835f / 1884f)
    ) {
        val w = maxWidth
        val h = maxHeight
        val busy = isCreating || isJoining

        Image(
            painter = painterResource(R.drawable.home_reactor_bg),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // Continuous breathing animation for primary action buttons and glow
        val infiniteTransition = rememberInfiniteTransition(label = "heroBreathing")
        
        // Ambient Plasma Glow
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.0f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF3300).copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(size.width * 0.43f, size.height * 0.35f),
                    radius = size.width * 0.6f
                )
            )
        }

        // Live Spinning Reactor Core
        AnimatedReactorCore(
            modifier = Modifier
                .offset(x = w * 0.335f, y = h * 0.204f)
                .size(w * 0.33f, w * 0.33f)
        )

        // Live Dynamic Waveforms (Left and Right of "STAY FUSION")
        AnimatedWaveform(
            modifier = Modifier
                .offset(x = w * 0.190f, y = h * 0.454f)
                .size(w * 0.15f, h * 0.018f),
            isReversed = false
        )
        AnimatedWaveform(
            modifier = Modifier
                .offset(x = w * 0.665f, y = h * 0.454f)
                .size(w * 0.15f, h * 0.018f),
            isReversed = true
        )

        // Profile avatar, sitting where the radioactive icon panel is in the artwork.
        Box(
            modifier = Modifier
                .offset(x = w * 0.728f, y = h * 0.072f)
                .size(w * 0.150f, h * 0.066f)
                .bouncyClick(onClick = onNavigateToProfile),
            contentAlignment = Alignment.Center
        ) {
            AvatarCircle(avatar = avatarById(myAvatarId), size = w * 0.11f)
        }

        // Continuous breathing animation for primary action buttons
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        Image(
            painter = painterResource(R.drawable.btn_create_room),
            contentDescription = "Create Room",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(x = w * 0.2168f, y = h * 0.4688f)
                .size(w * 0.5677f, h * 0.0844f)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .bouncyClick { if (!busy) onCreateRoom() }
        )

        Box(
            modifier = Modifier
                .offset(x = w * 0.2383f, y = h * 0.6242f)
                .size(w * 0.5210f, h * 0.0913f)
        ) {
            Image(
                painter = painterResource(R.drawable.room_code_panel),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            BasicTextField(
                value = joinRoomCode,
                onValueChange = onJoinRoomCodeChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFFFFC7C7),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 4.sp
                ),
                cursorBrush = SolidColor(Color(0xFFFF5A5A)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.88f)
                    .fillMaxHeight(0.42f)
                    .padding(bottom = h * 0.01f)
            )
        }

        Image(
            painter = painterResource(R.drawable.btn_join_room),
            contentDescription = "Join Room",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(x = w * 0.2371f, y = h * 0.7208f)
                .size(w * 0.5329f, h * 0.0785f)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .bouncyClick { if (!busy) onJoinRoom() }
        )

        Image(
            painter = painterResource(R.drawable.btn_friends),
            contentDescription = "Friends",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(x = w * 0.3401f, y = h * 0.8333f)
                .size(w * 0.3198f, h * 0.0531f)
                .bouncyClick(onClick = onNavigateToFriends)
        )
    }
}

@Composable
private fun OnlineFriendsRow(friends: List<FriendProfile>, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            "Online now",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(friends, key = { it.uid }) { friend ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Box(modifier = Modifier.bouncyClick { onClick() }) {
                        AvatarCircle(avatar = avatarById(friend.avatarId), size = 52.dp)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(14.dp)
                                .background(
                                    color = if (friend.presenceStatus == "watching")
                                        com.example.ui.theme.SeenBlue else com.example.ui.theme.OnlineGreen,
                                    shape = CircleShape
                                )
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        friend.username,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(entries: List<ContinueWatchingEntry>, onResume: (ContinueWatchingEntry) -> Unit) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            "Continue Watching",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.roomCode }) { entry ->
                Card(
                    modifier = Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(com.example.ui.theme.UraniumShapes.cardRadius.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(com.example.ui.theme.UraniumShapes.cardRadius.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(Modifier)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { onResume(entry) },
                                modifier = Modifier.bouncyClick { onResume(entry) }
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Room ${entry.roomCode}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            Text(
                                if (entry.isYouTube) "YouTube \u00b7 resume" else "Resume where you left off",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedReactorCore(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "reactorSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.minDimension / 2f
            
            // Solid dark core mask perfectly sized to hide the static lines 
            // without bleeding outside the bounds of the PNG image
            drawCircle(
                color = Color(0xFF100202), // Very dark red/black matching the background
                radius = outerRadius * 0.96f,
                center = center
            )
        }
        
        Image(
            painter = painterResource(R.drawable.nuclear_radiation),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation
                }
        )
    }
}

@Composable
private fun AnimatedWaveform(modifier: Modifier = Modifier, isReversed: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "time"
    )

    Canvas(modifier = modifier) {
        val barCount = 15
        val barWidth = size.width / (barCount * 2)
        val maxBarHeight = size.height

        // Soft mask to hide static waveform
        drawRoundRect(
            color = Color(0xFF150202).copy(alpha = 0.85f),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
        )

        // Draw sine wave
        val wavePath = Path().apply {
            moveTo(0f, size.height / 2f)
            for (x in 0..size.width.toInt() step 2) {
                val normalizedX = x / size.width
                val dir = if (isReversed) -1f else 1f
                val waveY = sin(normalizedX * 4 * Math.PI + (time * dir)) * (size.height * 0.3)
                lineTo(x.toFloat(), size.height / 2f + waveY.toFloat())
            }
        }
        drawPath(
            path = wavePath,
            color = Color(0xFFFF5A5A),
            style = Stroke(width = 2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            alpha = 0.6f
        )
        
        // Draw equalizer bars
        for (i in 0 until barCount) {
            val x = i * (barWidth * 2) + barWidth / 2
            val phase = i * 0.6f
            val dir = if (isReversed) -1f else 1f
            val heightMult = (sin(time * 3 * dir + phase) + 1f) / 2f // 0 to 1
            val height = maxBarHeight * heightMult * 0.6f + maxBarHeight * 0.2f
            
            drawLine(
                color = Color(0xFFFF3300),
                start = Offset(x, size.height / 2f - height / 2f),
                end = Offset(x, size.height / 2f + height / 2f),
                strokeWidth = barWidth * 0.8f,
                cap = StrokeCap.Round,
                alpha = 0.8f
            )
        }
    }
}