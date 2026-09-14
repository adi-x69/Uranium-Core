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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.ui.Alignment
import kotlin.math.abs
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants

fun getYoutubeVideoId(url: String): String? {
    if (url.length == 11 && !url.contains("http") && !url.contains("www")) return url
    val regex = Regex("(?:youtube(?:-nocookie)?\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\\.be/|youtube\\.com/shorts/)([^\"&?/\\s]{11})")
    val match = regex.find(url)
    return match?.groupValues?.getOrNull(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onInviteFriends: () -> Unit,
    onNavigateToWatch: () -> Unit,
    selectedVideoIdFromSearch: String?,
    onVideoIdConsumed: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember(roomCode) { FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference.child("rooms").child(roomCode) }
    
    val uid = auth.currentUser?.uid ?: ""
    val usersRef = remember { FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference.child("users") }
    var hasVideo by remember { mutableStateOf(false) }
    var hasAutoNavigatedRemote by remember { mutableStateOf(false) }

    var ytInputUrl by remember { mutableStateOf("") }
    var webInputUrl by remember { mutableStateOf("") }

    LaunchedEffect(selectedVideoIdFromSearch) {
        if (selectedVideoIdFromSearch != null) {
            val finalUrl = "https://www.youtube.com/watch?v=$selectedVideoIdFromSearch"
            val updates = mapOf(
                "videoUrl" to finalUrl,
                "position" to 0L,
                "isPlaying" to true,
                "lastUpdatedBy" to uid,
                "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
            )
            db.updateChildren(updates)
            recordContinueWatching(usersRef, uid, roomCode, finalUrl, 0L, isYouTube = true)
            
            hasVideo = true
            
            onVideoIdConsumed()
            onNavigateToWatch()
        }
    }

    // Setup Firebase listener
    DisposableEffect(roomCode) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val lastUpdatedBy = snapshot.child("lastUpdatedBy").getValue(String::class.java) ?: ""
                
                // If we updated this ourselves, don't loop back our own state
                if (lastUpdatedBy == uid) return

                val videoUrl = snapshot.child("videoUrl").getValue(String::class.java) ?: ""
                val isPlaying = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: false
                if (videoUrl.isNotEmpty()) hasVideo = true

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
                ReactorRoomHero(
                    roomCode = roomCode,
                    ytInputUrl = ytInputUrl,
                    onYtInputChange = { ytInputUrl = it },
                    webInputUrl = webInputUrl,
                    onWebInputChange = { webInputUrl = it },
                    onNavigateBack = onNavigateBack,
                    onNavigateToSearch = onNavigateToSearch,
                    onInviteFriends = onInviteFriends,
                    onPlayYt = {
                        if (ytInputUrl.isNotBlank()) {
                            val ytId = getYoutubeVideoId(ytInputUrl)
                            val finalUrl = if (ytId != null) "https://www.youtube.com/watch?v=$ytId" else ytInputUrl
                            val updates = mapOf(
                                "videoUrl" to finalUrl,
                                "position" to 0L,
                                "isPlaying" to true,
                                "lastUpdatedBy" to uid,
                                "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                            db.updateChildren(updates)
                            recordContinueWatching(usersRef, uid, roomCode, finalUrl, 0L, isYouTube = true)

                            hasVideo = true
                            onNavigateToWatch()
                        }
                    },
                    onPlayWeb = {
                        if (webInputUrl.isNotBlank()) {
                            val updates = mapOf(
                                "videoUrl" to webInputUrl,
                                "position" to 0L,
                                "isPlaying" to true,
                                "lastUpdatedBy" to uid,
                                "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                            db.updateChildren(updates)
                            recordContinueWatching(usersRef, uid, roomCode, webInputUrl, 0L, isYouTube = false)

                            hasVideo = true
                            onNavigateToWatch()
                        }
                    }
                )

                if (hasVideo) {
                    TextButton(
                        onClick = onNavigateToWatch,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Watch Fullscreen")
                    }
                }
            } // Close Column
        }
    }
}

/**
 * The reactor-skinned room screen: one flat mockup image used as the background
 * (frame, glowing pills, decorative DNA/decay-chain art all baked in), with real
 * interactive elements positioned on top by fraction of the hero's own width/height
 * - same approach as the Home screen hero. The one difference: this mockup had the
 * room code ("EGY4U6") baked directly into the art as example text, so that patch of
 * background was painted over with matching stone texture and is replaced here by a
 * real "Room: $roomCode" Text so it's correct for every room, not just the example.
 */
@Composable
private fun ReactorRoomHero(
    roomCode: String,
    ytInputUrl: String,
    onYtInputChange: (String) -> Unit,
    webInputUrl: String,
    onWebInputChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
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

        // DNA Animation Overlay
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

        // Real room code, replacing the patched-out example text
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
                .offset(x = w * 0.0240f, y = h * 0.0664f)
                .size(w * 0.9521f, h * 0.0611f)
                .clickable(onClick = onNavigateToSearch)
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
            textStyle = TextStyle(color = Color(0xFFE7E9F0), fontSize = 15.sp),
            cursorBrush = SolidColor(Color(0xFFFF5A5A)),
            modifier = Modifier
                .offset(x = w * 0.0240f, y = h * 0.1992f)
                .size(w * 0.6826f, h * 0.0611f)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 16.dp)
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
            textStyle = TextStyle(color = Color(0xFFE7E9F0), fontSize = 15.sp),
            cursorBrush = SolidColor(Color(0xFFFF5A5A)),
            modifier = Modifier
                .offset(x = w * 0.0240f, y = h * 0.2709f)
                .size(w * 0.6826f, h * 0.0744f)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 16.dp)
        )

        Box(
            modifier = Modifier
                .offset(x = w * 0.7126f, y = h * 0.2762f)
                .size(w * 0.2635f, h * 0.0558f)
                .clickable(onClick = onPlayWeb)
        )
    }
}
