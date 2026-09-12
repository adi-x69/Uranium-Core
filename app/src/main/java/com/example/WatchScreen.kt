package com.example

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// The reaction set, arranged roughly by mood: laughing -> crying -> neutral/sly ->
// shocked/disgusted -> love/romantic -> misc/animals.
val REACTION_EMOJIS = listOf(
    "😁", "😅", "😂", "🤣", "😭",
    "🙂", "🙃", "😏", "😎", "🙄", "😑", "😒",
    "😋", "🤤", "🤪", "😝", "😜", "🤭",
    "😱", "😣", "🤧", "🤮", "🤢",
    "❤️", "❤️‍🩹", "💔", "🫀", "💦", "💋", "🫦", "👄", "🫶🏻",
    "🐷", "🐻", "🤡", "💩", "💀", "🌚", "🖕🏻"
)

private data class ChatMessage(
    val id: String,
    val senderUid: String,
    val senderUsername: String,
    val text: String,
    val timestamp: Long
)

private data class FloatingReaction(val key: String, val emoji: String, val startX: Float)

private fun watchDbRef(roomCode: String) = FirebaseDatabase
    .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
    .reference.child("rooms").child(roomCode)

@Composable
fun WatchScreen(
    roomCode: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""
    val db = remember(roomCode) { watchDbRef(roomCode) }
    val usersRef = remember { FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference.child("users") }

    var myUsername by remember { mutableStateOf("") }
    LaunchedEffect(uid) {
        FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
            .reference.child("users").child(uid).child("username").get()
            .addOnSuccessListener { myUsername = it.getValue(String::class.java) ?: "someone" }
    }

    // ---- Video sync state (mirrors RoomScreen's model) ----
    var isYouTubeMode by remember { mutableStateOf(false) }
    var currentYtId by remember { mutableStateOf("") }
    var youtubePlayer: YouTubePlayer? by remember { mutableStateOf(null) }
    var ytCurrentTimeMs by remember { mutableStateOf(0L) }
    var ytDurationMs by remember { mutableStateOf(0L) }
    var isPlayingState by remember { mutableStateOf(false) }
    var isApplyingRemoteState by remember { mutableStateOf(false) }
    var hostUid by remember { mutableStateOf("") }
    var controlsUnlocked by remember { mutableStateOf(false) }
    val canControl = uid.isNotEmpty() && (uid == hostUid || controlsUnlocked)

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Referer" to AppConfig.VIDEO_REFERER))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply { playWhenReady = false }
    }

    fun pushPlaybackUpdate(playing: Boolean, positionMs: Long) {
        db.updateChildren(
            mapOf(
                "isPlaying" to playing,
                "position" to positionMs,
                "lastUpdatedBy" to uid,
                "lastUpdatedAt" to ServerValue.TIMESTAMP
            )
        )
        val currentUrl = if (isYouTubeMode) "https://www.youtube.com/watch?v=$currentYtId"
            else exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUrl != null) {
            recordContinueWatching(usersRef, uid, roomCode, currentUrl, positionMs, isYouTube = isYouTubeMode)
        }
    }

    // Mirror room state
    DisposableEffect(roomCode) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                hostUid = snapshot.child("hostUid").getValue(String::class.java) ?: hostUid
                controlsUnlocked = snapshot.child("controlsUnlocked").getValue(Boolean::class.java) ?: false

                val lastUpdatedBy = snapshot.child("lastUpdatedBy").getValue(String::class.java) ?: ""
                if (lastUpdatedBy == uid) return

                val videoUrl = snapshot.child("videoUrl").getValue(String::class.java) ?: ""
                val isPlaying = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: false
                val position = snapshot.child("position").getValue(Long::class.java) ?: 0L
                isPlayingState = isPlaying

                isApplyingRemoteState = true
                val ytId = getYoutubeVideoId(videoUrl)
                if (ytId != null) {
                    isYouTubeMode = true
                    if (currentYtId != ytId) {
                        currentYtId = ytId
                        if (isPlaying) youtubePlayer?.loadVideo(ytId, position / 1000f)
                        else youtubePlayer?.cueVideo(ytId, position / 1000f)
                    } else {
                        if (abs(ytCurrentTimeMs - position) > 1500L) youtubePlayer?.seekTo(position / 1000f)
                        if (isPlaying) youtubePlayer?.play() else youtubePlayer?.pause()
                    }
                } else if (videoUrl.isNotEmpty()) {
                    isYouTubeMode = false
                    val currentMediaUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                    if (currentMediaUri != videoUrl) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
                        exoPlayer.prepare()
                    }
                    if (exoPlayer.playWhenReady != isPlaying) exoPlayer.playWhenReady = isPlaying
                    if (abs(exoPlayer.currentPosition - position) > 1500L) exoPlayer.seekTo(position)
                }
                isApplyingRemoteState = false
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        onDispose { db.removeEventListener(listener) }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                if (isApplyingRemoteState || !canControl) return
                pushPlaybackUpdate(isPlaying, exoPlayer.currentPosition)
            }
        }
        exoPlayer.addListener(playerListener)
        onDispose {
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
    }

    // ---- UI interaction state ----
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var isLandscape by remember { mutableStateOf(false) }
    var isChatMode by remember { mutableStateOf(false) }
    var isEmojiPickerOpen by remember { mutableStateOf(false) }

    fun bumpInteraction() {
        controlsVisible = true
        interactionTick++
    }

    LaunchedEffect(interactionTick, isChatMode) {
        if (isChatMode) return@LaunchedEffect // controls stay visible in chat mode
        delay(3000)
        controlsVisible = false
        isEmojiPickerOpen = false
    }

    fun applyOrientation(landscape: Boolean) {
        activity?.requestedOrientation = if (landscape)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (landscape) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun toggleLandscape() {
        val next = !isLandscape
        isLandscape = next
        if (next) isChatMode = false
        applyOrientation(next)
        bumpInteraction()
    }

    fun openChat() {
        isChatMode = true
        isLandscape = false
        applyOrientation(false)
    }

    fun closeChat() {
        isChatMode = false
        bumpInteraction()
    }

    // ---- Chat ----
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val chatListState = rememberLazyListState()
    var chatInput by remember { mutableStateOf("") }

    DisposableEffect(roomCode) {
        val chatRef = db.child("chat")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val id = snapshot.key ?: return
                val msg = ChatMessage(
                    id = id,
                    senderUid = snapshot.child("senderUid").getValue(String::class.java) ?: "",
                    senderUsername = snapshot.child("senderUsername").getValue(String::class.java) ?: "unknown",
                    text = snapshot.child("text").getValue(String::class.java) ?: "",
                    timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                )
                if (chatMessages.none { it.id == id }) chatMessages.add(msg)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        chatRef.limitToLast(200).addChildEventListener(listener)
        onDispose { chatRef.removeEventListener(listener) }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) chatListState.animateScrollToItem(chatMessages.size - 1)
    }

    fun sendChat() {
        val text = chatInput.trim()
        if (text.isEmpty()) return
        db.child("chat").push().setValue(
            mapOf(
                "senderUid" to uid,
                "senderUsername" to myUsername,
                "text" to text,
                "timestamp" to ServerValue.TIMESTAMP
            )
        )
        chatInput = ""
        db.child("typing").child(uid).removeValue()
    }

    // ---- Typing indicator ----
    // Own typing state: as soon as there's text, publish a timestamped "typing" node;
    // after 2.5s with no further keystrokes (debounce keyed on chatInput), clear it.
    // Other participants' typing nodes are read live and shown as "X is typing...".
    var othersTyping by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(chatInput) {
        val typingRef = db.child("typing").child(uid)
        if (chatInput.isNotBlank()) {
            typingRef.setValue(mapOf("username" to myUsername, "at" to ServerValue.TIMESTAMP))
            delay(2500)
            typingRef.removeValue()
        } else {
            typingRef.removeValue()
        }
    }

    DisposableEffect(roomCode) {
        val typingRef = db.child("typing")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                othersTyping = snapshot.children.mapNotNull { child ->
                    if (child.key == uid) return@mapNotNull null
                    child.child("username").getValue(String::class.java)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        typingRef.addValueEventListener(listener)
        onDispose {
            typingRef.removeEventListener(listener)
            typingRef.child(uid).removeValue()
        }
    }

    // ---- Emoji reactions ----
    val floatingReactions = remember { mutableStateListOf<FloatingReaction>() }

    DisposableEffect(roomCode) {
        val reactionsRef = db.child("reactions")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val key = snapshot.key ?: return
                val emoji = snapshot.child("emoji").getValue(String::class.java) ?: return
                floatingReactions.add(FloatingReaction(key, emoji, Random.nextFloat()))
                // Best-effort cleanup: whichever client processes it first removes the node
                // a moment later, after the pop-up animation would have finished.
                reactionsRef.child(key).removeValue()
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        reactionsRef.addChildEventListener(listener)
        onDispose { reactionsRef.removeEventListener(listener) }
    }

    fun sendReaction(emoji: String) {
        db.child("reactions").push().setValue(
            mapOf("emoji" to emoji, "senderUid" to uid, "timestamp" to ServerValue.TIMESTAMP)
        )
        bumpInteraction()
    }

    // ---- Video area (shared between fullscreen and chat-mode layouts) ----
    val videoAspect = 16f / 9f

    val videoContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { bumpInteraction() }
                }
        ) {
            if (isYouTubeMode) {
                AndroidView(
                    factory = { ctx ->
                        YouTubePlayerView(ctx).apply {
                            enableAutomaticInitialization = false
                            lifecycleOwner.lifecycle.addObserver(this)
                            initialize(object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    youtubePlayer = youTubePlayer
                                    if (currentYtId.isNotEmpty()) {
                                        youTubePlayer.loadVideo(currentYtId, ytCurrentTimeMs / 1000f)
                                    }
                                }

                                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                    val playing = state == PlayerConstants.PlayerState.PLAYING
                                    if (state == PlayerConstants.PlayerState.PLAYING || state == PlayerConstants.PlayerState.PAUSED) {
                                        isPlayingState = playing
                                        if (!isApplyingRemoteState && canControl) {
                                            pushPlaybackUpdate(playing, ytCurrentTimeMs)
                                        }
                                    }
                                }

                                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                                    val currentMs = (second * 1000).toLong()
                                    if (!isApplyingRemoteState && canControl) {
                                        if (abs(currentMs - ytCurrentTimeMs) > 1500L && ytCurrentTimeMs != 0L) {
                                            pushPlaybackUpdate(isPlayingState, currentMs)
                                        }
                                    }
                                    ytCurrentTimeMs = currentMs
                                }

                                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                                    ytDurationMs = (duration * 1000).toLong()
                                }
                            }, IFramePlayerOptions.Builder().controls(0).build())
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Floating emoji reactions
            floatingReactions.forEach { reaction ->
                key(reaction.key) {
                    FloatingEmoji(reaction) {
                        floatingReactions.remove(reaction)
                    }
                }
            }

            // Auto-hiding controls overlay (hidden entirely while in chat mode)
            AnimatedVisibility(
                visible = controlsVisible && !isChatMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerControlsOverlay(
                    isHost = uid == hostUid,
                    canControl = canControl,
                    controlsUnlocked = controlsUnlocked,
                    isPlaying = isPlayingState,
                    currentMs = if (isYouTubeMode) ytCurrentTimeMs else exoPlayer.currentPosition,
                    durationMs = if (isYouTubeMode) ytDurationMs else exoPlayer.duration.coerceAtLeast(0L),
                    onBack = onNavigateBack,
                    onTogglePlay = {
                        if (!canControl) return@PlayerControlsOverlay
                        val newPlaying = !isPlayingState
                        isPlayingState = newPlaying
                        if (isYouTubeMode) {
                            if (newPlaying) youtubePlayer?.play() else youtubePlayer?.pause()
                            pushPlaybackUpdate(newPlaying, ytCurrentTimeMs)
                        } else {
                            exoPlayer.playWhenReady = newPlaying
                            pushPlaybackUpdate(newPlaying, exoPlayer.currentPosition)
                        }
                        bumpInteraction()
                    },
                    onSkipBack = {
                        if (!canControl) return@PlayerControlsOverlay
                        if (isYouTubeMode) {
                            val newPos = (ytCurrentTimeMs - 10000L).coerceAtLeast(0L)
                            youtubePlayer?.seekTo(newPos / 1000f)
                            pushPlaybackUpdate(isPlayingState, newPos)
                        } else {
                            val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            pushPlaybackUpdate(isPlayingState, newPos)
                        }
                        bumpInteraction()
                    },
                    onSeek = { targetMs ->
                        if (!canControl) return@PlayerControlsOverlay
                        if (isYouTubeMode) {
                            youtubePlayer?.seekTo(targetMs / 1000f)
                            ytCurrentTimeMs = targetMs
                        } else {
                            exoPlayer.seekTo(targetMs)
                        }
                        pushPlaybackUpdate(isPlayingState, targetMs)
                        bumpInteraction()
                    },
                    onToggleLandscape = { toggleLandscape() },
                    onOpenChat = { openChat() },
                    onToggleEmojiPicker = { isEmojiPickerOpen = !isEmojiPickerOpen; bumpInteraction() },
                    onToggleUnlock = {
                        db.child("controlsUnlocked").setValue(!controlsUnlocked)
                        bumpInteraction()
                    }
                )
            }

            AnimatedVisibility(
                visible = isEmojiPickerOpen && !isChatMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)
            ) {
                EmojiPickerRow(onPick = { emoji ->
                    sendReaction(emoji)
                    isEmojiPickerOpen = false
                })
            }
        }
    }

    if (isChatMode) {
        Column(modifier = Modifier.fillMaxSize()) {
            videoContent(Modifier.fillMaxWidth().aspectRatio(videoAspect))
            ChatPanel(
                messages = chatMessages,
                listState = chatListState,
                myUid = uid,
                input = chatInput,
                onInputChange = { chatInput = it },
                onSend = { sendChat() },
                onClose = { closeChat() },
                typingUsers = othersTyping,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    } else {
        videoContent(Modifier.fillMaxSize())
    }
}

@Composable
private fun FloatingEmoji(reaction: FloatingReaction, onDone: () -> Unit) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(reaction.key) {
        offsetY.animateTo(-300f, animationSpec = tween(2200, easing = LinearEasing))
    }
    LaunchedEffect(reaction.key) {
        delay(1600)
        alpha.animateTo(0f, animationSpec = tween(600))
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = reaction.emoji,
            fontSize = TextUnit(40f, TextUnitType.Sp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = offsetY.value.dp)
                .alpha(alpha.value)
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlayerControlsOverlay(
    isHost: Boolean,
    canControl: Boolean,
    controlsUnlocked: Boolean,
    isPlaying: Boolean,
    currentMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLandscape: () -> Unit,
    onOpenChat: () -> Unit,
    onToggleEmojiPicker: () -> Unit,
    onToggleUnlock: () -> Unit
) {
    // Local drag state so the slider follows the finger smoothly instead of
    // jumping back to the synced position on every Firebase update mid-drag.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isHost) {
                Text(
                    text = if (controlsUnlocked) "Unlocked" else "Locked",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Switch(checked = controlsUnlocked, onCheckedChange = { onToggleUnlock() })
            } else if (!canControl) {
                Text("Host controls playback", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Center play controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            IconButton(onClick = onSkipBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Replay10, contentDescription = "Back 10s", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Bottom bar: progress + action buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMillis((dragValue ?: currentMs.toFloat()).toLong()), color = Color.White, style = MaterialTheme.typography.labelSmall)
                val maxMs = if (durationMs > 0) durationMs.toFloat() else 1f
                Slider(
                    value = (dragValue ?: currentMs.toFloat()).coerceIn(0f, maxMs),
                    onValueChange = { if (canControl) dragValue = it },
                    onValueChangeFinished = {
                        dragValue?.let { onSeek(it.toLong()) }
                        dragValue = null
                    },
                    valueRange = 0f..maxMs,
                    enabled = canControl,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(formatMillis(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleEmojiPicker) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Reactions", tint = Color.White)
                }
                IconButton(onClick = onOpenChat) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat", tint = Color.White)
                }
                IconButton(onClick = onToggleLandscape) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EmojiPickerRow(onPick: (String) -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium) {
        LazyRow(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(REACTION_EMOJIS) { emoji ->
                Text(
                    text = emoji,
                    fontSize = TextUnit(26f, TextUnitType.Sp),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPick(emoji) }
                        .padding(6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPanel(
    messages: List<ChatMessage>,
    listState: LazyListState,
    myUid: String,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    typingUsers: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Live Chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to video")
            }
        }
        Divider()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderUid == myUid
                Column(
                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg.senderUsername,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        AnimatedVisibility(
            visible = typingUsers.isNotEmpty(),
            enter = fadeIn(animationSpec = com.example.ui.theme.UraniumMotion.fade()),
            exit = fadeOut(animationSpec = com.example.ui.theme.UraniumMotion.fade())
        ) {
            Text(
                text = typingIndicatorText(typingUsers),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                singleLine = true
            )
            IconButton(onClick = onSend) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

private fun typingIndicatorText(typingUsers: List<String>): String = when (typingUsers.size) {
    0 -> ""
    1 -> "${typingUsers[0]} is typing…"
    2 -> "${typingUsers[0]} and ${typingUsers[1]} are typing…"
    else -> "Several people are typing…"
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
