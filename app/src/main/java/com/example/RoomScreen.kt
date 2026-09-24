package com.example

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import androidx.compose.foundation.shape.RoundedCornerShape

fun getYoutubeVideoId(url: String): String? {
    val clean = url.trim()
    if (clean.length == 11 && !clean.contains("http") && !clean.contains("www") && !clean.contains("/") && !clean.contains("?")) return clean
    val regex = Regex("(?:youtube(?:-nocookie)?\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\\.be/|youtube\\.com/shorts/)([^\"&?/\\s]{11})")
    val match = regex.find(clean)
    return match?.groupValues?.getOrNull(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
    onInviteFriends: () -> Unit,
    onNavigateToWatch: () -> Unit,
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
                    onNavigateToWatch()
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
                            onNavigateToWatch()
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
                            onNavigateToWatch()
                        }
                    }
                )

                // ========== NETMIRROR BUTTON ==========
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onNavigateToNetMirror,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00C853)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(52.dp)
                ) {
                    Text(
                        text = "Browse NetMirror",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                if (hasVideo) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FuturisticHazardButton(
                        text = "ENTER WATCH ROOM",
                        onClick = onNavigateToWatch,
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

        // Room code
        Text(
            text = "Room: $roomCode",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier
                .offset(x = w * 0.1258f, y = h * 0.0186f)
                .size(w * 0.2814f, h * 0.0345f)
                .wrapContentHeight(Alignment.CenterVertically)
        )

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
