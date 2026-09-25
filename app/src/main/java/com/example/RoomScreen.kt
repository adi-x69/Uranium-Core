package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

fun getYoutubeVideoId(url: String): String? {
    val clean = url.trim()
    if (clean.length == 11 && !clean.contains("http") && !clean.contains("www") && !clean.contains("/") && !clean.contains("?") && !clean.contains("&")) return clean
    val patterns = listOf(
        Regex("""(?:youtu\.be/|youtube(?:-nocookie)?\.com/(?:.*[?&]v=|embed/|v/|shorts/|live/))([a-zA-Z0-9_-]{11})"""),
        Regex("""(?:youtube\.com/live/)([a-zA-Z0-9_-]{11})"""),
        Regex("""(?:youtu\.be/)([a-zA-Z0-9_-]{11})"""),
        Regex("""[?&]v=([a-zA-Z0-9_-]{11})""")
    )
    for (p in patterns) {
        val m = p.find(clean)
        if (m != null) return m.groupValues[1]
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
    onInviteFriends: () -> Unit,
    onNavigateToWatch: (isYouTube: Boolean) -> Unit,
    onNavigateToNetMirror: () -> Unit,          // ← New parameter
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember(roomCode) { FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference.child("rooms").child(roomCode) }
    
    val uid = auth.currentUser?.uid ?: ""
    val usersRef = remember { FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference.child("users") }
    var hasVideo by remember { mutableStateOf(false) }
    var currentKnownUrl by remember { mutableStateOf("") }
    var hasAutoNavigatedRemote by remember { mutableStateOf(false) }

    var ytInputUrl by remember { mutableStateOf("") }
    var webInputUrl by remember { mutableStateOf("") }

    val currentUser = auth.currentUser
    val userEmail = currentUser?.email?.lowercase()?.trim() ?: ""
    var isPremiumUser by remember { mutableStateOf(PremiumManager.isHardcodedAdmin(userEmail)) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uid, userEmail) {
        if (PremiumManager.isHardcodedAdmin(userEmail)) {
            isPremiumUser = true
            return@LaunchedEffect
        }
        if (uid.isEmpty()) return@LaunchedEffect

        val rootRef = FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference

        // 1. Check user profile: users/{uid}/isPremium
        rootRef.child("users").child(uid).child("isPremium").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isPrem = snapshot.getValue(Boolean::class.java) == true || snapshot.getValue(Long::class.java) == 1L
                if (isPrem) isPremiumUser = true
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 2. Comprehensive check on the entire whitelist node (handles keys, values, and Name: uid formats)
        rootRef.child("whitelist").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val emailKey = if (userEmail.isNotEmpty()) PremiumManager.sanitizeEmail(userEmail) else ""

                // Direct key check
                if (snapshot.hasChild(uid) || (emailKey.isNotEmpty() && snapshot.hasChild(emailKey))) {
                    isPremiumUser = true
                    return
                }

                // Check all children: matches if uid/email is a key OR if it was entered as a value (e.g. Name: "ReO4...")
                for (child in snapshot.children) {
                    val childKey = child.key ?: ""
                    val childVal = child.value?.toString()?.trim() ?: ""

                    // Matches if child key is the UID or Email
                    if (childKey.equals(uid, ignoreCase = true) ||
                        (emailKey.isNotEmpty() && childKey.equals(emailKey, ignoreCase = true)) ||
                        (userEmail.isNotEmpty() && childKey.equals(userEmail, ignoreCase = true))
                    ) {
                        isPremiumUser = true
                        return
                    }

                    // Matches if child value is the UID or Email (e.g. Name = "ReO4O5io7cRMVxG5S5WnWSk8sqm2")
                    if (childVal.equals(uid, ignoreCase = true) ||
                        (userEmail.isNotEmpty() && childVal.equals(userEmail, ignoreCase = true))
                    ) {
                        isPremiumUser = true
                        return
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    var myUsername by remember { mutableStateOf(UserProfileStorage.getCachedUsername(context, uid).ifEmpty { "Someone" }) }
    var hostUid by remember { mutableStateOf("") }
    var hostUsername by remember { mutableStateOf("") }
    var hasShownSelfJoinToast by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        usersRef.child(uid).child("username").get()
            .addOnSuccessListener { 
                val name = it.getValue(String::class.java)
                if (!name.isNullOrBlank()) myUsername = name
            }
    }

    LaunchedEffect(roomCode) {
        db.child("hostUid").get().addOnSuccessListener { snap ->
            val hUid = snap.getValue(String::class.java) ?: ""
            hostUid = hUid
            if (hUid.isNotEmpty()) {
                usersRef.child(hUid).child("username").get()
                    .addOnSuccessListener { hostUsername = it.getValue(String::class.java) ?: "" }
            }
        }
    }

    val presenceRef = remember(roomCode, uid) { db.child("roomPresence").child(uid) }
    DisposableEffect(roomCode, uid) {
        if (uid.isNotEmpty()) presenceRef.onDisconnect().removeValue()
        onDispose {
            presenceRef.removeValue()
            recordRoomLeave(usersRef, uid, roomCode)
        }
    }
    LaunchedEffect(roomCode, uid, myUsername) {
        if (uid.isNotEmpty() && myUsername.isNotEmpty()) {
            presenceRef.setValue(mapOf("username" to myUsername))
        }
    }

    LaunchedEffect(uid, hostUid, hostUsername) {
        if (uid.isNotEmpty() && hostUid.isNotEmpty() && uid != hostUid && !hasShownSelfJoinToast) {
            hasShownSelfJoinToast = true
            snackbarHostState.showSnackbar("You joined ${hostUsername.ifEmpty { "the host" }}'s room")
        }
    }

    DisposableEffect(roomCode, uid) {
        val presenceRootRef = db.child("roomPresence")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val pid = snapshot.key ?: return
                if (pid == uid) return
                val username = snapshot.child("username").getValue(String::class.java) ?: "Someone"
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("$username has joined the room")
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val pid = snapshot.key ?: return
                if (pid == uid) return
                val username = snapshot.child("username").getValue(String::class.java) ?: "Someone"
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("$username has left the room")
                }
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        presenceRootRef.addChildEventListener(listener)
        onDispose { presenceRootRef.removeEventListener(listener) }
    }

    DisposableEffect(roomCode) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val lastUpdatedBy = snapshot.child("lastUpdatedBy").getValue(String::class.java) ?: ""
                val videoUrl = snapshot.child("videoUrl").getValue(String::class.java) ?: ""
                val isPlaying = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: false
                
                if (videoUrl.isNotEmpty()) {
                    hasVideo = true
                    if (videoUrl != currentKnownUrl) {
                        currentKnownUrl = videoUrl
                        hasAutoNavigatedRemote = false
                    }
                }

                if (lastUpdatedBy == uid) return

                if (videoUrl.isNotEmpty() && isPlaying && !hasAutoNavigatedRemote) {
                    hasAutoNavigatedRemote = true
                    onNavigateToWatch(getYoutubeVideoId(videoUrl) != null)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        
        onDispose {
            db.removeEventListener(listener)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
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
                ReactorRoomHero(
                    roomCode = roomCode,
                    ytInputUrl = ytInputUrl,
                    onYtInputChange = { ytInputUrl = it },
                    webInputUrl = webInputUrl,
                    onWebInputChange = { webInputUrl = it },
                    onNavigateBack = onNavigateBack,
                    onInviteFriends = onInviteFriends,
                    onPlayYt = {
                        val clean = ytInputUrl.trim()
                        if (clean.isNotBlank()) {
                            val ytId = getYoutubeVideoId(clean)
                            val finalUrl = if (ytId != null) "https://www.youtube.com/watch?v=$ytId" else clean
                            val isYt = ytId != null
                            val updates = mapOf(
                                "videoUrl" to finalUrl,
                                "position" to 0L,
                                "isPlaying" to true,
                                "lastUpdatedBy" to uid,
                                "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                            db.updateChildren(updates)
                            recordContinueWatching(usersRef, uid, roomCode, finalUrl, 0L, isYouTube = isYt, isNewVideo = true)

                            hasVideo = true
                            onNavigateToWatch(isYt)
                        }
                    },
                    onPlayWeb = {
                        val clean = webInputUrl.trim()
                        if (clean.isNotBlank()) {
                            val ytId = getYoutubeVideoId(clean)
                            val finalUrl = if (ytId != null) "https://www.youtube.com/watch?v=$ytId" else clean
                            val isYt = ytId != null
                            val updates = mapOf(
                                "videoUrl" to finalUrl,
                                "position" to 0L,
                                "isPlaying" to true,
                                "lastUpdatedBy" to uid,
                                "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                            db.updateChildren(updates)
                            recordContinueWatching(usersRef, uid, roomCode, finalUrl, 0L, isYouTube = isYt, isNewVideo = true)

                            hasVideo = true
                            onNavigateToWatch(isYt)
                        }
                    },
                    onSearchMovies = {
                        if (isPremiumUser) {
                            onNavigateToNetMirror()
                        } else {
                            showPremiumDialog = true
                        }
                    }
                )

                if (hasVideo) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FuturisticHazardButton(
                        text = "ENTER WATCH ROOM",
                        onClick = { onNavigateToWatch(getYoutubeVideoId(currentKnownUrl) != null) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        gradient = listOf(NeonCrimson, NeonHazardAmber, NeonCyberCyan)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    if (showPremiumDialog) {
        UraniumPremiumDialog(
            userUid = uid,
            userEmail = userEmail,
            onDismiss = { showPremiumDialog = false }
        )
    }
}

@Composable
private fun ReactorRoomHero(
    roomCode: String,
    ytInputUrl: String,
    onYtInputChange: (String) -> Unit,
    webInputUrl: String,
    onWebInputChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onInviteFriends: () -> Unit,
    onPlayYt: () -> Unit,
    onPlayWeb: () -> Unit,
    onSearchMovies: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(835f / 1883f)
    ) {
        val w = maxWidth
        val h = maxHeight

        Image(
            painter = painterResource(R.drawable.room_reactor_bg),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        FuturisticScannerOverlay()

        val context = LocalContext.current
        val imageLoader = remember {
            coil.ImageLoader.Builder(context)
                .components {
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        add(coil.decode.ImageDecoderDecoder.Factory())
                    } else {
                        add(coil.decode.GifDecoder.Factory())
                    }
                }
                .build()
        }

        Image(
            painter = coil.compose.rememberAsyncImagePainter(
                model = coil.request.ImageRequest.Builder(context)
                    .data(R.drawable.dna_animation)
                    .build(),
                imageLoader = imageLoader
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .offset(x = 0.dp, y = h * 0.355f)
                .size(w, h * 0.645f)
        )

        // Back button
        Box(
            modifier = Modifier
                .offset(x = w * 0.0120f, y = h * 0.0106f)
                .size(w * 0.1138f, h * 0.0478f)
                .clickable(onClick = onNavigateBack)
        )

        // Room code: clearly visible on top-left right after the back button, single line, glowing cyan code with tap-to-copy
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .offset(x = w * 0.128f, y = h * 0.0106f)
                .height(h * 0.0478f)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Room Code", roomCode))
                    Toast.makeText(context, "Room code $roomCode copied!", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = "Room: ",
                color = Color.White.copy(alpha = 0.90f),
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = roomCode,
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy room code",
                tint = Color(0xFF00E5FF).copy(alpha = 0.85f),
                modifier = Modifier.size(15.dp)
            )
        }

        // Search Movies and Web Series bar with premium aesthetic aura & liquid laser animation
        ReactorSearchBarAestheticAura(
            modifier = Modifier
                .offset(x = w * 0.0240f, y = h * 0.0640f)
                .size(w * 0.9521f, h * 0.0650f),
            onClick = onSearchMovies
        )

        // Invite Friends button
        Box(
            modifier = Modifier
                .offset(x = w * 0.0240f, y = h * 0.1328f)
                .size(w * 0.9521f, h * 0.0451f)
                .clickable(onClick = onInviteFriends)
        )

        BasicTextField(
            value = ytInputUrl,
            onValueChange = onYtInputChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color(0xFFF0F2F8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(Color(0xFFFF5A5A)),
            modifier = Modifier
                .offset(x = w * 0.0240f, y = h * 0.1992f)
                .size(w * 0.6826f, h * 0.0611f),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (ytInputUrl.isEmpty()) {
                            Text(
                                text = "Paste YouTube Link",
                                color = Color(0xFF8E95A5),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                    if (ytInputUrl.isNotEmpty()) {
                        IconButton(
                            onClick = { onYtInputChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear YouTube link",
                                tint = Color(0xFF8E95A5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .offset(x = w * 0.7126f, y = h * 0.2018f)
                .size(w * 0.2635f, h * 0.0520f)
                .clickable(onClick = onPlayYt)
        )

        BasicTextField(
            value = webInputUrl,
            onValueChange = onWebInputChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color(0xFFF0F2F8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(Color(0xFFFF5A5A)),
            modifier = Modifier
                .offset(x = w * 0.0240f, y = h * 0.2709f)
                .size(w * 0.6826f, h * 0.0744f),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (webInputUrl.isEmpty()) {
                            Text(
                                text = "Paste Web Video Link (.mp4)",
                                color = Color(0xFF8E95A5),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                    if (webInputUrl.isNotEmpty()) {
                        IconButton(
                            onClick = { onWebInputChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Web link",
                                tint = Color(0xFF8E95A5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .offset(x = w * 0.7126f, y = h * 0.2762f)
                .size(w * 0.2635f, h * 0.0558f)
                .clickable(onClick = onPlayWeb)
        )
    }
}

/**
 * Ultra-premium aesthetic animation for the "Search Movies and Web Series" bar.
 * Layers:
 * 1. Pulsing cyber-plasma ambient glow (neon crimson / ruby aura).
 * 2. Continuous rotating laser beam sweeping around the rounded pill border.
 * 3. Prismatic glass shimmer flare gliding across the capsule surface.
 * 4. Nuclear hazard node energy pulses at both ends.
 */
@Composable
private fun ReactorSearchBarAestheticAura(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "searchBarAura")

    // 1. Ambient plasma breathing pulse
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraPulse"
    )

    // 2. Liquid laser beam orbiting continuously around the perimeter
    val laserSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserSweepAngle"
    )

    // 3. Luxurious light shimmer gliding across the bar
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, delayMillis = 350, easing = CubicBezierEasing(0.35f, 0.0f, 0.25f, 1.0f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    // 4. Subtle hazard node energy breathing
    val nodeGlow by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nodeGlow"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pillRadius = CornerRadius(h / 2f, h / 2f)

            // Layer 1: Ambient Plasma Outer Halo (Crimson / Neon Danger Red)
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF1744).copy(alpha = 0.40f * auraPulse),
                        Color(0xFFFF0055).copy(alpha = 0.20f * auraPulse),
                        Color.Transparent
                    ),
                    center = Offset(w / 2f, h / 2f),
                    radius = w * 0.55f
                ),
                cornerRadius = pillRadius
            )

            // Layer 2: Rotating Liquid Laser Orbit along the perimeter
            rotate(degrees = laserSweepAngle, pivot = Offset(w / 2f, h / 2f)) {
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        0.0f to Color(0xFFFF1744).copy(alpha = 0.05f),
                        0.55f to Color(0xFFFF5252).copy(alpha = 0.25f),
                        0.80f to Color(0xFFFF1744).copy(alpha = 0.85f),
                        0.94f to Color(0xFFFFD700).copy(alpha = 0.95f), // Gold plasma spark
                        1.0f to Color.White // Hot white leading beam
                    ),
                    style = Stroke(width = 2.5.dp.toPx()),
                    cornerRadius = pillRadius
                )
            }

            // Layer 3: Neon Inner Edge Accent Ring
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF8A80).copy(alpha = 0.45f * auraPulse),
                        Color(0xFFFF1744).copy(alpha = 0.20f),
                        Color(0xFFFF5252).copy(alpha = 0.45f * auraPulse)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                style = Stroke(width = 1.2.dp.toPx()),
                cornerRadius = pillRadius
            )

            // Layer 4: Glass Shimmer Reflection sweeping across the bar
            val shimmerX = w * shimmerProgress
            val shimmerWidth = w * 0.32f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.15f),
                        Color(0xFFFFE082).copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    start = Offset(shimmerX, 0f),
                    end = Offset(shimmerX + shimmerWidth, h)
                ),
                cornerRadius = pillRadius
            )

            // Layer 5: Radioactive Hazard Icons Energy Nodes (Left and Right)
            val nodeRadius = h * 0.36f * nodeGlow
            // Left hazard node
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD700).copy(alpha = 0.40f * auraPulse),
                        Color(0xFFFF1744).copy(alpha = 0.15f * auraPulse),
                        Color.Transparent
                    )
                ),
                radius = nodeRadius,
                center = Offset(h * 0.58f, h * 0.50f)
            )
            // Right hazard node
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD700).copy(alpha = 0.40f * auraPulse),
                        Color(0xFFFF1744).copy(alpha = 0.15f * auraPulse),
                        Color.Transparent
                    )
                ),
                radius = nodeRadius,
                center = Offset(w - h * 0.58f, h * 0.50f)
            )
        }
    }
}
