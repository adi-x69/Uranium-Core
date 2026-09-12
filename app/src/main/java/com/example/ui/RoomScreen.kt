package com.example

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    var ytInputUrl by remember { mutableStateOf("") }
    var webInputUrl by remember { mutableStateOf("") }
    
    var isYouTubeMode by remember { mutableStateOf(false) }
    var currentYtId by remember { mutableStateOf("") }
    var youtubePlayer: YouTubePlayer? by remember { mutableStateOf(null) }
    var ytCurrentTimeMs by remember { mutableStateOf(0L) }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Referer" to AppConfig.VIDEO_REFERER))
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = false
            }
    }

    var isApplyingRemoteState by remember { mutableStateOf(false) }

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
            
            // Local immediate update
            isApplyingRemoteState = true
            isYouTubeMode = true
            currentYtId = selectedVideoIdFromSearch
            youtubePlayer?.loadVideo(selectedVideoIdFromSearch, 0f)
            isApplyingRemoteState = false
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
                val position = snapshot.child("position").getValue(Long::class.java) ?: 0L
                if (videoUrl.isNotEmpty()) hasVideo = true

                isApplyingRemoteState = true

                val ytId = getYoutubeVideoId(videoUrl)
                if (ytId != null) {
                    isYouTubeMode = true
                    if (currentYtId != ytId) {
                        currentYtId = ytId
                        if (isPlaying) {
                            youtubePlayer?.loadVideo(ytId, position / 1000f)
                        } else {
                            youtubePlayer?.cueVideo(ytId, position / 1000f)
                        }
                    } else {
                        if (abs(ytCurrentTimeMs - position) > 1500L) {
                            youtubePlayer?.seekTo(position / 1000f)
                        }
                        if (isPlaying) {
                            youtubePlayer?.play()
                        } else {
                            youtubePlayer?.pause()
                        }
                    }
                } else if (videoUrl.isNotEmpty()) {
                    isYouTubeMode = false
                    val currentMediaUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                    if (currentMediaUri != videoUrl) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
                        exoPlayer.prepare()
                    }
    
                    if (exoPlayer.playWhenReady != isPlaying) {
                        exoPlayer.playWhenReady = isPlaying
                    }
    
                    val currentPos = exoPlayer.currentPosition
                    if (abs(currentPos - position) > 1500L) {
                        exoPlayer.seekTo(position)
                    }
                } else {
                    isYouTubeMode = false
                }

                isApplyingRemoteState = false
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        
        onDispose {
            db.removeEventListener(listener)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Setup Player listener and Lifecycle
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isApplyingRemoteState) return
                
                val updates = mapOf(
                    "isPlaying" to isPlaying,
                    "position" to exoPlayer.currentPosition,
                    "lastUpdatedBy" to uid,
                    "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                )
                db.updateChildren(updates)
                exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()?.let { url ->
                    recordContinueWatching(usersRef, uid, roomCode, url, exoPlayer.currentPosition, isYouTube = false)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (isApplyingRemoteState) return
                
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    val updates = mapOf(
                        "position" to newPosition.positionMs,
                        "isPlaying" to exoPlayer.playWhenReady,
                        "lastUpdatedBy" to uid,
                        "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                    )
                    db.updateChildren(updates)
                }
            }
        }
        exoPlayer.addListener(playerListener)
        
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Let Firebase state decide if it should play, or stay paused
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room: $roomCode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onNavigateToSearch,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("Search YouTube")
                }
                OutlinedButton(
                    onClick = onInviteFriends,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("Invite Friends")
                }
                if (hasVideo) {
                    Button(
                        onClick = onNavigateToWatch,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text("Watch Fullscreen")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ytInputUrl,
                        onValueChange = { ytInputUrl = it },
                        label = { Text("Paste YouTube Link") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
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
                                
                                // Local immediate update
                                isApplyingRemoteState = true
                                isYouTubeMode = true
                                if (ytId != null) {
                                    currentYtId = ytId
                                    youtubePlayer?.loadVideo(ytId, 0f)
                                }
                                isApplyingRemoteState = false
                                hasVideo = true
                                onNavigateToWatch()
                            }
                        }
                    ) {
                        Text("Play YT")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = webInputUrl,
                        onValueChange = { webInputUrl = it },
                        label = { Text("Paste Web Video Link (.mp4)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
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
                                
                                // Local immediate update
                                isApplyingRemoteState = true
                                isYouTubeMode = false
                                exoPlayer.setMediaItem(MediaItem.fromUri(webInputUrl))
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                isApplyingRemoteState = false
                                hasVideo = true
                                onNavigateToWatch()
                            }
                        }
                    ) {
                        Text("Play Web")
                    }
                }
            }

            // Video Player
            if (isYouTubeMode) {
                AndroidView(
                    factory = { ctx ->
                        YouTubePlayerView(ctx).apply {
                            lifecycleOwner.lifecycle.addObserver(this)
                            addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    youtubePlayer = youTubePlayer
                                    if (currentYtId.isNotEmpty()) {
                                        youTubePlayer.loadVideo(currentYtId, ytCurrentTimeMs / 1000f)
                                    }
                                }

                                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                    if (isApplyingRemoteState) return
                                    val isPlaying = state == PlayerConstants.PlayerState.PLAYING
                                    if (state == PlayerConstants.PlayerState.PLAYING || state == PlayerConstants.PlayerState.PAUSED) {
                                        val updates = mapOf(
                                            "isPlaying" to isPlaying,
                                            "position" to ytCurrentTimeMs,
                                            "lastUpdatedBy" to uid,
                                            "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                                        )
                                        db.updateChildren(updates)
                                    }
                                }

                                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                                    val currentMs = (second * 1000).toLong()
                                    if (!isApplyingRemoteState) {
                                        if (abs(currentMs - ytCurrentTimeMs) > 1500L && ytCurrentTimeMs != 0L) {
                                            val updates = mapOf(
                                                "position" to currentMs,
                                                "lastUpdatedBy" to uid,
                                                "lastUpdatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                                            )
                                            db.updateChildren(updates)
                                        }
                                    }
                                    ytCurrentTimeMs = currentMs
                                }
                            })
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            }
        }
    }
}
