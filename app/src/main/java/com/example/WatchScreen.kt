package com.example

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.bouncyClick
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/** In-app popup notification data for incoming chat messages in fullscreen watch mode. */
private data class ChatNotification(
    val id: String,
    val senderUid: String,
    val senderUsername: String,
    val avatarId: String,
    val text: String,
    val timestamp: Long
)

private data class FloatingReaction(val key: String, val emoji: String, val startX: Float)

/** Someone currently present in the room, from rooms/{roomCode}/participants/{uid}. */
private data class ParticipantInfo(val uid: String, val username: String, val avatarId: String)

/** A transient "X joined" / "X left" banner shown over the video. */
private data class BannerEntry(val key: String, val text: String)

/** A transient play/pause notification banner shown over the video. */
private data class PlaybackBannerEntry(val key: String, val text: String, val isPlaying: Boolean)

private fun watchDbRef(roomCode: String) = FirebaseDatabase
    .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
    .reference.child("rooms").child(roomCode)

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
}

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

    var myUsername by remember { mutableStateOf(UserProfileStorage.getCachedUsername(context, uid).ifEmpty { "someone" }) }
    var myAvatarId by remember { mutableStateOf(UserProfileStorage.getCachedAvatar(context, uid)) }
    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        val userRef = FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
            .reference.child("users").child(uid)
        userRef.child("username").get()
            .addOnSuccessListener { 
                val name = it.getValue(String::class.java)
                if (!name.isNullOrBlank()) myUsername = name
            }
        userRef.child("avatarId").get()
            .addOnSuccessListener { 
                val av = it.getValue(String::class.java)
                if (!av.isNullOrBlank()) myAvatarId = av
            }
    }

    // ---- Presence: mark this user as "in the room" so others see a join banner +
    // pulsing avatar, and clean up (deliberate leave AND dropped connection) so
    // everyone sees a leave banner and the glow disappears. ----
    val participantRef = remember(roomCode, uid) { db.child("participants").child(uid) }
    DisposableEffect(roomCode, uid) {
        if (uid.isNotEmpty()) participantRef.onDisconnect().removeValue()
        onDispose { participantRef.removeValue() }
    }
    LaunchedEffect(roomCode, uid, myUsername, myAvatarId) {
        if (uid.isNotEmpty() && myUsername.isNotEmpty()) {
            participantRef.setValue(mapOf("username" to myUsername, "avatarId" to myAvatarId.ifEmpty { "iron_man" }))
        }
    }

    var participants by remember { mutableStateOf<List<ParticipantInfo>>(emptyList()) }
    val joinLeaveBanners = remember { mutableStateListOf<BannerEntry>() }
    val playbackBanners = remember { mutableStateListOf<PlaybackBannerEntry>() }

    DisposableEffect(roomCode) {
        val participantsRef = db.child("participants")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val pid = snapshot.key ?: return
                val username = snapshot.child("username").getValue(String::class.java) ?: "Someone"
                val avatarId = snapshot.child("avatarId").getValue(String::class.java) ?: "iron_man"
                participants = participants.filter { it.uid != pid } + ParticipantInfo(pid, username, avatarId)
                if (pid != uid) {
                    joinLeaveBanners.add(BannerEntry("join-$pid-${System.nanoTime()}", "$username joined"))
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val pid = snapshot.key ?: return
                val username = snapshot.child("username").getValue(String::class.java) ?: "Someone"
                val avatarId = snapshot.child("avatarId").getValue(String::class.java) ?: "iron_man"
                participants = participants.filter { it.uid != pid } + ParticipantInfo(pid, username, avatarId)
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val pid = snapshot.key ?: return
                val username = snapshot.child("username").getValue(String::class.java) ?: "Someone"
                participants = participants.filter { it.uid != pid }
                if (pid != uid) {
                    joinLeaveBanners.add(BannerEntry("leave-$pid-${System.nanoTime()}", "$username left"))
                }
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        participantsRef.addChildEventListener(listener)
        onDispose { participantsRef.removeEventListener(listener) }
    }

    // ---- Video sync state (mirrors RoomScreen's model) ----
    var isYouTubeMode by remember { mutableStateOf(false) }
    var currentYtId by remember { mutableStateOf("") }
    var youtubePlayer: YouTubePlayer? by remember { mutableStateOf(null) }
    var ytCurrentTimeMs by remember { mutableStateOf(0L) }
    var ytDurationMs by remember { mutableStateOf(0L) }
    var isYtBuffering by remember { mutableStateOf(false) }
    var isPlayingState by remember { mutableStateOf(false) }
    var isApplyingRemoteState by remember { mutableStateOf(false) }
    var hostUid by remember { mutableStateOf("") }
    var controlsUnlocked by remember { mutableStateOf(false) }
    val canControl = uid.isNotEmpty() && (uid == hostUid || hostUid.isEmpty() || controlsUnlocked)
    var isUserSeeking by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Referer" to AppConfig.VIDEO_REFERER))
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .build().apply { playWhenReady = false }
    }

    // ---- UI interaction state ----
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var isLandscape by remember { mutableStateOf(false) }
    var wasLandscapeBeforeChat by remember { mutableStateOf(false) }
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
    }

    // ---- Server time offset for precision clock sync across devices ----
    var serverTimeOffsetMs by remember { mutableStateOf(0L) }
    DisposableEffect(Unit) {
        val offsetRef = FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
            .getReference(".info/serverTimeOffset")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffsetMs = snapshot.getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        offsetRef.addValueEventListener(listener)
        onDispose { offsetRef.removeEventListener(listener) }
    }

    fun currentServerTimeMs(): Long = System.currentTimeMillis() + serverTimeOffsetMs

    fun calculateExpectedPosition(pos: Long, playing: Boolean, updatedAt: Long, duration: Long = 0L): Long {
        if (!playing || updatedAt <= 0L) return pos
        val nowServer = currentServerTimeMs()
        val elapsed = (nowServer - updatedAt).coerceAtLeast(0L)
        val result = pos + elapsed
        return if (duration > 0L) result.coerceIn(0L, duration) else result.coerceAtLeast(0L)
    }

    // ---- Buffering & Sync State ----
    var localPlaybackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var othersBuffering by remember { mutableStateOf<String?>(null) }
    var showSyncNowButton by remember { mutableStateOf(false) }
    var lastKnownPosition by remember { mutableStateOf(0L) }
    var lastUpdatedAtSnapshot by remember { mutableStateOf(0L) }
    val bufferingRef = remember(roomCode, uid) { db.child("buffering").child(uid) }

    // Live position tracking for ExoPlayer (smooth UI slider & timestamps)
    var exoCurrentPositionMs by remember { mutableStateOf(0L) }
    var exoDurationMs by remember { mutableStateOf(0L) }

    LaunchedEffect(isYouTubeMode) {
        if (isYouTubeMode) return@LaunchedEffect
        while (true) {
            exoCurrentPositionMs = exoPlayer.currentPosition
            exoDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(300)
        }
    }

    DisposableEffect(roomCode, uid) {
        if (uid.isNotEmpty()) bufferingRef.onDisconnect().removeValue()
        onDispose { bufferingRef.removeValue() }
    }

    // Broadcast my own buffering state, debounced (800ms) so quick seek-loads don't flicker.
    val isLocalBuffering = if (isYouTubeMode) isYtBuffering else (localPlaybackState == Player.STATE_BUFFERING)
    LaunchedEffect(isLocalBuffering) {
        if (isLocalBuffering) {
            delay(800)
            if (isLocalBuffering) {
                bufferingRef.setValue(
                    mapOf("username" to myUsername.ifEmpty { "Your friend" }, "at" to ServerValue.TIMESTAMP)
                )
            }
        } else {
            bufferingRef.removeValue()
        }
    }

    // Watch for other participants' buffering entry
    DisposableEffect(roomCode, uid) {
        val bufferingRootRef = db.child("buffering")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val otherEntry = snapshot.children.firstOrNull { it.key != uid }
                othersBuffering = otherEntry?.child("username")?.getValue(String::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        bufferingRootRef.addValueEventListener(listener)
        onDispose { bufferingRootRef.removeEventListener(listener) }
    }

    // When this device finishes buffering, smoothly catch up if drifted.
    LaunchedEffect(localPlaybackState) {
        if (isYouTubeMode) return@LaunchedEffect
        if (localPlaybackState == Player.STATE_READY && isPlayingState && !isUserSeeking) {
            val expected = calculateExpectedPosition(lastKnownPosition, isPlayingState, lastUpdatedAtSnapshot, exoDurationMs)
            val drift = abs(exoPlayer.currentPosition - expected)
            if (drift in 1500L..5000L) {
                exoPlayer.seekTo(expected)
                showSyncNowButton = false
            } else if (drift > 5000L) {
                showSyncNowButton = true
            } else {
                showSyncNowButton = false
            }
        }
    }

    // Background drift monitor (checks every 3s, auto-catches up without micro-stutter)
    LaunchedEffect(isPlayingState, isYouTubeMode, lastKnownPosition, lastUpdatedAtSnapshot, isUserSeeking) {
        if (!isPlayingState || isUserSeeking) {
            showSyncNowButton = false
            return@LaunchedEffect
        }
        while (true) {
            delay(3000)
            if (!isPlayingState || isUserSeeking) break
            val isBuffering = if (isYouTubeMode) isYtBuffering else (localPlaybackState == Player.STATE_BUFFERING)
            if (!isBuffering) {
                val duration = if (isYouTubeMode) ytDurationMs else exoDurationMs
                val expected = calculateExpectedPosition(lastKnownPosition, isPlayingState, lastUpdatedAtSnapshot, duration)
                val current = if (isYouTubeMode) ytCurrentTimeMs else exoPlayer.currentPosition
                val drift = abs(current - expected)
                if (drift in 1500L..5000L) {
                    if (isYouTubeMode) {
                        youtubePlayer?.seekTo(expected / 1000f)
                        ytCurrentTimeMs = expected
                    } else {
                        exoPlayer.seekTo(expected)
                    }
                    showSyncNowButton = false
                } else if (drift > 5000L) {
                    showSyncNowButton = true
                } else {
                    showSyncNowButton = false
                }
            }
        }
    }

    fun syncNow() {
        val duration = if (isYouTubeMode) ytDurationMs else exoDurationMs
        val target = calculateExpectedPosition(lastKnownPosition, isPlayingState, lastUpdatedAtSnapshot, duration)
        if (isYouTubeMode) {
            youtubePlayer?.seekTo(target / 1000f)
            ytCurrentTimeMs = target
        } else {
            exoPlayer.seekTo(target)
        }
        showSyncNowButton = false
        bumpInteraction()
    }

    // ---- Network status icon: shows if EITHER person has a connection issue -
    // my phone's actual network state (wifi/data lost or unvalidated), OR
    // either side's video buffering. Hidden entirely when everything's fine. ----
    var hasNetworkIssue by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun isValidated(network: Network?): Boolean {
            val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { hasNetworkIssue = true }
            override fun onUnavailable() { hasNetworkIssue = true }
            override fun onAvailable(network: Network) { hasNetworkIssue = !isValidated(network) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                hasNetworkIssue = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        hasNetworkIssue = !isValidated(connectivityManager.activeNetwork)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    val videoBufferIssue = isLocalBuffering || othersBuffering != null
    val connectionIssue = hasNetworkIssue || videoBufferIssue

    fun pushPlaybackUpdate(playing: Boolean, positionMs: Long) {
        lastKnownPosition = positionMs
        lastUpdatedAtSnapshot = currentServerTimeMs()

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
    var prevRoomPlaying by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(roomCode) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                hostUid = snapshot.child("hostUid").getValue(String::class.java) ?: hostUid
                controlsUnlocked = snapshot.child("controlsUnlocked").getValue(Boolean::class.java) ?: false

                val lastUpdatedBy = snapshot.child("lastUpdatedBy").getValue(String::class.java) ?: ""
                val isSelfEcho = lastUpdatedBy == uid

                val videoUrl = snapshot.child("videoUrl").getValue(String::class.java) ?: ""
                val isPlaying = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: false
                val position = snapshot.child("position").getValue(Long::class.java) ?: 0L
                val lastUpdatedAt = snapshot.child("lastUpdatedAt").getValue(Long::class.java) ?: 0L

                if (!isSelfEcho && prevRoomPlaying != null && prevRoomPlaying != isPlaying && lastUpdatedBy.isNotEmpty()) {
                    val actorName = participants.find { it.uid == lastUpdatedBy }?.username ?: "Someone"
                    val actionText = if (isPlaying) "$actorName played the video" else "$actorName paused the video"
                    playbackBanners.add(PlaybackBannerEntry("playstate-${System.nanoTime()}", actionText, isPlaying))
                }
                prevRoomPlaying = isPlaying

                lastKnownPosition = position
                lastUpdatedAtSnapshot = lastUpdatedAt
                isPlayingState = isPlaying

                val targetPosition = calculateExpectedPosition(position, isPlaying, lastUpdatedAt, if (isYouTubeMode) ytDurationMs else exoDurationMs)

                isApplyingRemoteState = true
                val ytId = getYoutubeVideoId(videoUrl)
                if (ytId != null) {
                    isYouTubeMode = true
                    if (currentYtId != ytId) {
                        currentYtId = ytId
                        ytCurrentTimeMs = targetPosition
                        if (isPlaying) youtubePlayer?.loadVideo(ytId, targetPosition / 1000f)
                        else youtubePlayer?.cueVideo(ytId, targetPosition / 1000f)
                    } else if (!isSelfEcho) {
                        if (abs(ytCurrentTimeMs - targetPosition) > 1500L && !isUserSeeking) {
                            youtubePlayer?.seekTo(targetPosition / 1000f)
                            ytCurrentTimeMs = targetPosition
                        }
                        if (isPlaying) youtubePlayer?.play() else youtubePlayer?.pause()
                    }
                } else if (videoUrl.isNotEmpty()) {
                    isYouTubeMode = false
                    val currentMediaUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                    if (currentMediaUri != videoUrl) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = isPlaying
                        if (targetPosition > 0L) exoPlayer.seekTo(targetPosition)
                    } else if (!isSelfEcho) {
                        if (exoPlayer.playWhenReady != isPlaying) {
                            exoPlayer.playWhenReady = isPlaying
                        }
                        if (abs(exoPlayer.currentPosition - targetPosition) > 1500L && !isUserSeeking) {
                            exoPlayer.seekTo(targetPosition)
                        }
                    }
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
                // NOTE: Do not auto-push to Firebase here.
                // Intentional user actions (Play/Pause/Seek/Skip) push directly.
                // Auto-pushing here was triggering the buffer/seek pause feedback loop!
            }
            override fun onPlaybackStateChanged(state: Int) {
                localPlaybackState = state
                if (state == Player.STATE_ENDED && canControl) {
                    isPlayingState = false
                    pushPlaybackUpdate(false, exoPlayer.duration.coerceAtLeast(0L))
                }
            }
        }
        exoPlayer.addListener(playerListener)
        onDispose {
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
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
        wasLandscapeBeforeChat = isLandscape
        isChatMode = true
        isLandscape = false
        applyOrientation(false)
    }

    fun closeChat() {
        isChatMode = false
        if (wasLandscapeBeforeChat) {
            isLandscape = true
            applyOrientation(true)
            wasLandscapeBeforeChat = false
        }
        bumpInteraction()
    }

    // ---- Chat ----
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val chatListState = rememberLazyListState()
    var chatInput by remember { mutableStateOf("") }
    var activeChatNotification by remember { mutableStateOf<ChatNotification?>(null) }
    val screenOpenedAt = remember { System.currentTimeMillis() }
    var isInitialChatSyncComplete by remember { mutableStateOf(false) }

    // Auto-dismiss the chat popup notification after ~4 seconds
    LaunchedEffect(activeChatNotification?.id) {
        if (activeChatNotification != null) {
            delay(4000)
            activeChatNotification = null
        }
    }

    // Dismiss the notification immediately if chat mode is opened
    LaunchedEffect(isChatMode) {
        if (isChatMode) {
            activeChatNotification = null
        }
    }

    DisposableEffect(roomCode) {
        val chatRef = db.child("chat")

        // Single value event listener fires once existing historical children have finished loading
        chatRef.limitToLast(1).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isInitialChatSyncComplete = true
            }
            override fun onCancelled(error: DatabaseError) {
                isInitialChatSyncComplete = true
            }
        })

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val id = snapshot.key ?: return
                val senderUid = snapshot.child("senderUid").getValue(String::class.java) ?: ""
                val senderUsername = snapshot.child("senderUsername").getValue(String::class.java) ?: "unknown"
                val text = snapshot.child("text").getValue(String::class.java) ?: ""
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val msgAvatarId = snapshot.child("avatarId").getValue(String::class.java)
                    ?: participants.firstOrNull { it.uid == senderUid }?.avatarId
                    ?: UserProfileStorage.getCachedAvatar(context, senderUid).ifEmpty { "iron_man" }

                val msg = ChatMessage(
                    id = id,
                    senderUid = senderUid,
                    senderUsername = senderUsername,
                    text = text,
                    timestamp = timestamp
                )
                if (chatMessages.none { it.id == id }) chatMessages.add(msg)

                // Trigger in-app popup notification for new incoming messages:
                // - Only when initial sync is complete or timestamp is recent
                // - Sender is not current user
                // - Chat mode is currently closed (isChatMode == false)
                val isLiveMessage = isInitialChatSyncComplete && (timestamp <= 0L || timestamp >= screenOpenedAt - 5000L)
                if (isLiveMessage && senderUid != uid && !isChatMode && text.isNotBlank()) {
                    activeChatNotification = ChatNotification(
                        id = id,
                        senderUid = senderUid,
                        senderUsername = senderUsername,
                        avatarId = msgAvatarId,
                        text = text,
                        timestamp = timestamp
                    )
                }
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
                "avatarId" to myAvatarId.ifEmpty { "iron_man" },
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

    // ---- Skip back/forward: shared by the overlay buttons AND double-tap-to-seek,
    // so both paths stay in sync with each other and with Firebase. No-ops (silently)
    // if this user doesn't have control - same gating as every other playback action. ----
    fun skip(deltaMs: Long) {
        if (!canControl) return
        if (isYouTubeMode) {
            val ceiling = if (ytDurationMs > 0) ytDurationMs else Long.MAX_VALUE
            val newPos = (ytCurrentTimeMs + deltaMs).coerceIn(0L, ceiling)
            youtubePlayer?.seekTo(newPos / 1000f)
            ytCurrentTimeMs = newPos
            pushPlaybackUpdate(isPlayingState, newPos)
        } else {
            val ceiling = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            val newPos = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, ceiling)
            exoPlayer.seekTo(newPos)
            pushPlaybackUpdate(isPlayingState, newPos)
        }
        bumpInteraction()
    }

    // "left"/"right" + a nonce (so repeated taps on the same side still restart the fade)
    var seekFlash by remember { mutableStateOf<Pair<String, Long>?>(null) }
    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            delay(500)
            seekFlash = null
        }
    }

    // ---- Video area (shared between fullscreen and chat-mode layouts) ----
    val videoAspect = 16f / 9f

    val videoContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier
                .background(Color.Black)
                .pointerInput(canControl) {
                    detectTapGestures(
                        onTap = {
                            if (isEmojiPickerOpen) isEmojiPickerOpen = false
                            bumpInteraction()
                        },
                        onDoubleTap = { offset ->
                            if (!canControl) return@detectTapGestures
                            val third = size.width / 3
                            when {
                                offset.x < third -> {
                                    skip(-10000L)
                                    seekFlash = "left" to System.nanoTime()
                                }
                                offset.x > size.width - third -> {
                                    skip(10000L)
                                    seekFlash = "right" to System.nanoTime()
                                }
                                else -> bumpInteraction()
                            }
                        }
                    )
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
                                        val expected = calculateExpectedPosition(lastKnownPosition, isPlayingState, lastUpdatedAtSnapshot, ytDurationMs)
                                        val startSec = (expected / 1000f).coerceAtLeast(0f)
                                        if (isPlayingState) {
                                            youTubePlayer.loadVideo(currentYtId, startSec)
                                        } else {
                                            youTubePlayer.cueVideo(currentYtId, startSec)
                                        }
                                    }
                                }

                                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                    when (state) {
                                        PlayerConstants.PlayerState.PLAYING -> {
                                            isPlayingState = true
                                            isYtBuffering = false
                                            val expected = calculateExpectedPosition(lastKnownPosition, true, lastUpdatedAtSnapshot, ytDurationMs)
                                            val drift = abs(ytCurrentTimeMs - expected)
                                            if (drift > 1500L && !isUserSeeking) {
                                                youTubePlayer.seekTo(expected / 1000f)
                                                ytCurrentTimeMs = expected
                                            }
                                        }
                                        PlayerConstants.PlayerState.PAUSED -> {
                                            isPlayingState = false
                                            isYtBuffering = false
                                        }
                                        PlayerConstants.PlayerState.BUFFERING -> {
                                            isYtBuffering = true
                                        }
                                        PlayerConstants.PlayerState.ENDED -> {
                                            isPlayingState = false
                                            isYtBuffering = false
                                            if (canControl) {
                                                pushPlaybackUpdate(false, ytDurationMs.coerceAtLeast(0L))
                                            }
                                        }
                                        else -> {}
                                    }
                                }

                                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                                    ytCurrentTimeMs = (second * 1000).toLong()
                                }

                                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                                    ytDurationMs = (duration * 1000).toLong()
                                }
                            }, IFramePlayerOptions.Builder().controls(0).build())
                        }
                    },
                    onRelease = { playerView ->
                        lifecycleOwner.lifecycle.removeObserver(playerView)
                        playerView.release()
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
                    onRelease = { playerView ->
                        playerView.player = null
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

            // Double-tap-to-seek flash ("<<10" / "10>>") on whichever side was tapped
            seekFlash?.let { (side, _) ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.35f)
                        .align(if (side == "left") Alignment.CenterStart else Alignment.CenterEnd)
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (side == "left") "\u25c0\u25c0 10" else "10 \u25b6\u25b6",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Join/leave and playback banners, stacked at the top, auto-dismissing
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                joinLeaveBanners.forEach { entry ->
                    key(entry.key) {
                        JoinLeaveBanner(entry) { joinLeaveBanners.remove(entry) }
                    }
                }
                playbackBanners.forEach { entry ->
                    key(entry.key) {
                        PlaybackNotificationBanner(entry) { playbackBanners.remove(entry) }
                    }
                }
            }

            // Persistent "friend is buffering" banner - stays up the whole time,
            // not a quick toast, and isn't tied to the controls auto-hide timer.
            othersBuffering?.let { name ->
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$name's connection is buffering — paused",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Small always-visible network/connection issue icon near the back
            // button - shows for EITHER person's issue, independent of the
            // controls auto-hide timer, disappears the moment things are fine.
            if (connectionIssue) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 56.dp, top = 12.dp)
                        .size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SignalWifiOff,
                            contentDescription = "Connection issue",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // In-app incoming chat notification popup for fullscreen watch mode
            AnimatedVisibility(
                visible = activeChatNotification != null && !isChatMode,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = 380f
                    )
                ) + fadeIn(
                    animationSpec = tween(200)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(220)
                ) + fadeOut(
                    animationSpec = tween(180)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (othersBuffering != null) 98.dp else 56.dp, start = 16.dp, end = 16.dp)
            ) {
                activeChatNotification?.let { notif ->
                    IncomingChatNotificationBanner(
                        notification = notif,
                        onTap = {
                            activeChatNotification = null
                            openChat()
                        }
                    )
                }
            }

            // Shown when playback is drifted/out of sync; tap to snap back in sync.
            if (showSyncNowButton) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { syncNow() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "⚠️ Playback out of sync — Tap to sync with room",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium
                        )
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
                    currentMs = if (isYouTubeMode) ytCurrentTimeMs else exoCurrentPositionMs,
                    durationMs = if (isYouTubeMode) ytDurationMs else exoDurationMs,
                    participants = participants,
                    myUid = uid,
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
                    onSkipBack = { skip(-10000L) },
                    onSkipForward = { skip(10000L) },
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
                    isLandscape = isLandscape,
                    onToggleLandscape = { toggleLandscape() },
                    onOpenChat = { openChat() },
                    onToggleEmojiPicker = { isEmojiPickerOpen = !isEmojiPickerOpen; bumpInteraction() },
                    onToggleUnlock = {
                        db.child("controlsUnlocked").setValue(!controlsUnlocked)
                        bumpInteraction()
                    },
                    onSeekingStateChanged = { isUserSeeking = it }
                )
            }

            AnimatedVisibility(
                visible = isEmojiPickerOpen && !isChatMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)
            ) {
                EmojiPickerRow(
                    onPick = { emoji ->
                        sendReaction(emoji)
                        isEmojiPickerOpen = false
                    },
                    onClose = { isEmojiPickerOpen = false }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = if (isChatMode) Modifier.fillMaxWidth().aspectRatio(videoAspect)
                       else Modifier.fillMaxSize()
        ) {
            videoContent(Modifier.fillMaxSize())
        }
        if (isChatMode) {
            ChatPanel(
                messages = chatMessages,
                listState = chatListState,
                myUid = uid,
                input = chatInput,
                onInputChange = { chatInput = it },
                onSend = { sendChat() },
                onClose = { closeChat() },
                typingUsers = othersTyping,
                // imePadding here ONLY - the video above keeps its exact fixed size and
                // position always; just the message list + input compress/slide to clear
                // the keyboard.
                modifier = Modifier.fillMaxWidth().weight(1f).imePadding()
            )
        }
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
private fun JoinLeaveBanner(entry: BannerEntry, onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(entry.key) {
        alpha.animateTo(1f, animationSpec = tween(200))
        delay(2000)
        alpha.animateTo(0f, animationSpec = tween(400))
        onDone()
    }
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .alpha(alpha.value)
    ) {
        Text(
            text = entry.text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PlaybackNotificationBanner(entry: PlaybackBannerEntry, onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(entry.key) {
        alpha.animateTo(1f, animationSpec = tween(200))
        delay(2500)
        alpha.animateTo(0f, animationSpec = tween(400))
        onDone()
    }
    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .alpha(alpha.value)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (entry.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null,
                tint = if (entry.isPlaying) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = entry.text,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * In-app incoming chat notification banner displayed during fullscreen watching
 * when chat mode is closed. Styled consistently with the buffering and network
 * indicators with dark translucent background, crimson accent border, and rounded corners.
 */
@Composable
private fun IncomingChatNotificationBanner(
    notification: ChatNotification,
    onTap: () -> Unit
) {
    Surface(
        color = Color(0xF00D1117),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, Color(0xFFFF3344).copy(alpha = 0.85f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .bouncyClick(onTap)
            .widthIn(min = 200.dp, max = 380.dp)
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Sender's avatar
            AvatarCircle(
                avatar = avatarById(notification.avatarId),
                size = 30.dp,
                pulsing = false
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = notification.senderUsername,
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "• now",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                val previewText = remember(notification.text) {
                    val singleLine = notification.text.replace("\n", " ").trim()
                    if (singleLine.length > 40) {
                        singleLine.take(40).trimEnd() + "…"
                    } else {
                        singleLine
                    }
                }

                Text(
                    text = previewText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "Open chat to reply",
                tint = Color(0xFFFF5252).copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Small row of who's currently in the room, each avatar pulsing to show they're active. */
@Composable
private fun ParticipantAvatarsRow(participants: List<ParticipantInfo>, excludeUid: String) {
    val others = participants.filter { it.uid != excludeUid }
    if (others.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        others.take(5).forEach { p ->
            AvatarCircle(avatar = avatarById(p.avatarId), size = 28.dp, pulsing = true)
        }
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
    participants: List<ParticipantInfo>,
    myUid: String,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSeek: (Long) -> Unit,
    isLandscape: Boolean,
    onToggleLandscape: () -> Unit,
    onOpenChat: () -> Unit,
    onToggleEmojiPicker: () -> Unit,
    onToggleUnlock: () -> Unit,
    onSeekingStateChanged: (Boolean) -> Unit = {}
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
            ParticipantAvatarsRow(participants = participants, excludeUid = myUid)
            Spacer(modifier = Modifier.width(8.dp))
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
            IconButton(onClick = onSkipForward, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
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
                val validDuration = durationMs > 0
                val maxMs = if (validDuration) durationMs.toFloat() else 1000f
                Slider(
                    value = (dragValue ?: currentMs.toFloat()).coerceIn(0f, maxMs),
                    onValueChange = { 
                        if (canControl && validDuration) {
                            dragValue = it
                            onSeekingStateChanged(true)
                        }
                    },
                    onValueChangeFinished = {
                        dragValue?.let { onSeek(it.toLong()) }
                        dragValue = null
                        onSeekingStateChanged(false)
                    },
                    valueRange = 0f..maxMs,
                    enabled = canControl && validDuration,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(formatMillis(if (validDuration) durationMs else 0L), color = Color.White, style = MaterialTheme.typography.labelSmall)
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
                    Icon(
                        if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isLandscape) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiPickerRow(onPick: (String) -> Unit, onClose: () -> Unit = {}) {
    Surface(color = Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                modifier = Modifier.padding(8.dp).weight(1f, fill = false),
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
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close reactions",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
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
                    if (!isMine) {
                        Text(
                            text = msg.senderUsername,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                        )
                    }
                    Surface(
                        color = if (isMine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMine) 16.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 16.dp
                        ),
                        modifier = Modifier.widthIn(max = 260.dp)
                    ) {
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Text(
                        text = formatTimestamp(msg.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
                    )
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
