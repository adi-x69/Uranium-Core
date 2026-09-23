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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""
    val cachedAvatar = remember(uid) { UserProfileStorage.getCachedAvatar(context, uid) }
    var myAvatarId by remember(uid) { mutableStateOf(cachedAvatar) }

    val db = remember { FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference }

    // So the profile button in the top bar shows this user's own avatar.
    DisposableEffect(uid) {
        val avatarRef = db.child("users").child(uid).child("avatarId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val raw = snapshot.getValue(String::class.java)
                val clean = if (raw.isNullOrBlank() || raw.startsWith("avatar_")) "iron_man" else raw.trim()
                myAvatarId = clean
                UserProfileStorage.saveAvatarLocally(context, uid, clean)
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
    // Automatically filters out and purges rooms where video was created > 2 hours ago
    // or user left for > 2 hours continuously.
    DisposableEffect(uid) {
        val cwRef = db.child("users").child(uid).child("continueWatching")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val active = mutableListOf<ContinueWatchingEntry>()
                val expiredCodes = mutableListOf<String>()

                snapshot.children.forEach { child ->
                    val code = child.key ?: return@forEach
                    val entry = child.toContinueWatchingEntry(code) ?: return@forEach
                    if (entry.isExpired(now)) {
                        expiredCodes.add(code)
                    } else {
                        active.add(entry)
                    }
                }

                // Clean up expired history from database
                expiredCodes.forEach { code ->
                    cwRef.child(code).removeValue()
                }

                continueWatching = active
                    .sortedByDescending { it.updatedAt }
                    .take(10)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        cwRef.addValueEventListener(listener)
        onDispose { cwRef.removeEventListener(listener) }
    }

    // Periodic check every minute to auto-remove entries once they reach 2 hours
    LaunchedEffect(uid, continueWatching) {
        if (continueWatching.isNotEmpty()) {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                val now = System.currentTimeMillis()
                val (expired, active) = continueWatching.partition { it.isExpired(now) }
                if (expired.isNotEmpty()) {
                    continueWatching = active
                    val cwRef = db.child("users").child(uid).child("continueWatching")
                    expired.forEach { cwRef.child(it.roomCode).removeValue() }
                }
            }
        }
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
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        onResume = { entry -> onNavigateToWatch(entry.roomCode) },
                        onDismiss = { entry ->
                            continueWatching = continueWatching.filter { it.roomCode != entry.roomCode }
                            removeContinueWatching(db.child("users"), uid, entry.roomCode)
                        }
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

        // Sweeping cyber laser scanner beam across the reactor deck
        FuturisticScannerOverlay()

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

        // Profile avatar inside the metal box with rotating and blinking circular ring
        val avatarContainerSize = w * 0.138f
        RotatingBlinkingAvatar(
            avatarId = myAvatarId,
            size = avatarContainerSize,
            onClick = onNavigateToProfile,
            modifier = Modifier.offset(
                x = w * 0.7832f - (avatarContainerSize / 2),
                y = h * 0.1128f - (avatarContainerSize / 2)
            )
        )

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

        val panelW = w * 0.5210f
        val panelH = h * 0.0913f
        Box(
            modifier = Modifier
                .offset(x = w * 0.2383f, y = h * 0.6242f)
                .size(panelW, panelH)
        ) {
            Image(
                painter = painterResource(R.drawable.room_code_panel),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            // Perfectly centered inside the dark input slot of room_code_panel
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(panelH * 0.36f)
                    .align(Alignment.TopCenter)
                    .offset(y = panelH * 0.43f),
                contentAlignment = Alignment.Center
            ) {
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(NeonToxicGreen, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "FRIENDS ONLINE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(friends, key = { it.uid }) { friend ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(52.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .bouncyClick { onClick() }
                            .border(1.2.dp, NeonCyberCyan.copy(alpha = 0.6f), CircleShape)
                            .padding(1.5.dp)
                    ) {
                        AvatarCircle(avatar = avatarById(friend.avatarId), size = 38.dp)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(11.dp)
                                .background(
                                    color = if (friend.presenceStatus == "watching")
                                        NeonCyberCyan else NeonToxicGreen,
                                    shape = CircleShape
                                )
                                .border(1.5.dp, Color(0xFF0D1019), CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        friend.username,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    entries: List<ContinueWatchingEntry>,
    onResume: (ContinueWatchingEntry) -> Unit,
    onDismiss: ((ContinueWatchingEntry) -> Unit)? = null
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(NeonHazardAmber, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "CONTINUE WATCHING",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.roomCode }) { entry ->
                Surface(
                    modifier = Modifier
                        .width(136.dp)
                        .height(46.dp)
                        .bouncyClick { onResume(entry) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xEE0B0E18),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                NeonCrimson.copy(alpha = 0.8f),
                                NeonCyberCyan.copy(alpha = 0.55f)
                            )
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Compact glowing play button circle
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0x33FF1744), CircleShape)
                                .border(1.dp, NeonCrimson, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = NeonCrimson,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        // Compact Room code & source
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "ROOM: ${entry.roomCode}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = if (entry.isYouTube) "YOUTUBE" else "WEB VIDEO",
                                fontSize = 8.5.sp,
                                color = NeonCyberCyan,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1
                            )
                        }

                        if (onDismiss != null) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .bouncyClick { onDismiss(entry) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
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

@Composable
private fun RotatingBlinkingAvatar(
    avatarId: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarRingAnim")

    // Smooth continuous 360-degree rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    // Pulsing/blinking glow effect
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringBlinkAlpha"
    )

    // Counter-rotation for micro HUD elements
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counterRotation"
    )

    Box(
        modifier = modifier
            .size(size)
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Rotating tactical HUD circular rings with blinking pulse
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val ringRadius = (this.size.minDimension / 2f) - (strokeWidth + 2.dp.toPx())

            // Outer soft ambient glow ring
            drawCircle(
                color = NeonCrimson.copy(alpha = blinkAlpha * 0.35f),
                radius = ringRadius + 2.5.dp.toPx(),
                style = Stroke(width = 3.dp.toPx())
            )

            // Primary segmented high-tech rotating ring (glowing & blinking)
            rotate(rotation) {
                // Arc segment 1 (Crimson to Amber gradient)
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(NeonCrimson, NeonHazardAmber, NeonCrimson)
                    ),
                    startAngle = 10f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    alpha = blinkAlpha
                )

                // Arc segment 2 (Cyber Cyan to Crimson gradient)
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(NeonCyberCyan, NeonCrimson, NeonCyberCyan)
                    ),
                    startAngle = 190f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    alpha = blinkAlpha
                )

                // Tactical radar dots on ring perimeter
                val tickRadius = ringRadius - 3.dp.toPx()
                for (angle in listOf(45f, 135f, 225f, 315f)) {
                    val rad = Math.toRadians(angle.toDouble())
                    val dotCenter = Offset(
                        (center.x + tickRadius * Math.cos(rad)).toFloat(),
                        (center.y + tickRadius * Math.sin(rad)).toFloat()
                    )
                    drawCircle(
                        color = NeonCyberCyan.copy(alpha = blinkAlpha),
                        radius = 1.6.dp.toPx(),
                        center = dotCenter
                    )
                }
            }

            // Counter-rotating inner micro-accent arcs
            rotate(counterRotation) {
                val innerRadius = ringRadius - 4.dp.toPx()
                drawArc(
                    color = NeonHazardAmber.copy(alpha = (1.2f - blinkAlpha).coerceIn(0.2f, 0.9f)),
                    startAngle = 60f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = NeonHazardAmber.copy(alpha = (1.2f - blinkAlpha).coerceIn(0.2f, 0.9f)),
                    startAngle = 240f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // The Profile Avatar neatly centered inside the metal socket & rotating ring
        AvatarCircle(
            avatar = avatarById(avatarId),
            size = size * 0.72f
        )
    }
}