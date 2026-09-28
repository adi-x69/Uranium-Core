package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.bouncyClick
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomHistoryScreen(
    onNavigateBack: () -> Unit,
    onEnterRoom: (String) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""

    var roomsList by remember { mutableStateOf<List<CreatedRoomRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRoomForDetail by remember { mutableStateOf<CreatedRoomRecord?>(null) }

    fun refreshHistory() {
        isLoading = true
        AppBufferingController.show("SYNCING ROOM ARCHIVES...")
        RoomHistoryManager.syncWithCloud(context, uid) { synced ->
            roomsList = synced
            isLoading = false
            AppBufferingController.hide()
        }
    }

    LaunchedEffect(uid) {
        // First load from local storage instantly
        roomsList = RoomHistoryManager.getCreatedRooms(context, uid)
        if (roomsList.isEmpty()) {
            refreshHistory()
        } else {
            isLoading = false
            // Sync background
            RoomHistoryManager.syncWithCloud(context, uid) { synced ->
                roomsList = synced
            }
        }
    }

    BackHandler(onBack = onNavigateBack)

    FuturisticCyberBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                RoomHistoryTopBar(
                    roomsCount = roomsList.size,
                    isLoading = isLoading,
                    onBack = onNavigateBack,
                    onRefresh = { refreshHistory() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (roomsList.isEmpty() && !isLoading) {
                    EmptyRoomHistoryView(
                        onNavigateBack = onNavigateBack
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            RoomHistoryTelemetryBanner(roomsCount = roomsList.size)
                        }

                        items(roomsList, key = { it.roomCode }) { record ->
                            CreatedRoomCard(
                                record = record,
                                onClick = { selectedRoomForDetail = record },
                                onEnter = { onEnterRoom(record.roomCode) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    // Comprehensive Room Details Modal Inspection Dialog
    selectedRoomForDetail?.let { record ->
        RoomDetailsInspectionDialog(
            record = record,
            onDismiss = { selectedRoomForDetail = null },
            onEnterRoom = {
                selectedRoomForDetail = null
                onEnterRoom(record.roomCode)
            }
        )
    }
}

@Composable
private fun RoomHistoryTopBar(
    roomsCount: Int,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        color = Color(0xF20B101C),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF151C2C), CircleShape)
                    .border(1.dp, NeonCyberCyan.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ROOM ARCHIVE",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "MY CREATED ROOMS & TELEMETRY",
                    color = NeonCyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp
                )
            }

            // Glowing count badge
            Surface(
                color = Color(0xFF10192A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NeonToxicGreen.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(NeonToxicGreen, CircleShape)
                            .shadow(4.dp, CircleShape, spotColor = NeonToxicGreen)
                    )
                    Text(
                        text = "$roomsCount ROOMS",
                        color = NeonToxicGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(36.dp)
            ) {
                if (isLoading) {
                    NuclearRadiationBufferingIndicator(
                        size = 18.dp,
                        color = NeonCyberCyan,
                        glowColor = NeonToxicGreen
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = NeonCyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomHistoryTelemetryBanner(roomsCount: Int) {
    Surface(
        color = Color(0xCC0E1526),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, NeonCyberCyan.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NuclearRadiationBufferingIndicator(
                size = 32.dp,
                color = NeonCyberCyan,
                glowColor = NeonToxicGreen
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "REACTOR ROOM ARCHIVE ACTIVE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Permanent records of all rooms you've launched. Tap any room card to inspect joined members, stream links, and creation logs.",
                    color = Color(0xFF8E9CB2),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CreatedRoomCard(
    record: CreatedRoomRecord,
    onClick: () -> Unit,
    onEnter: () -> Unit
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xF2101626),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(NeonCyberCyan.copy(alpha = 0.6f), NeonToxicGreen.copy(alpha = 0.3f)))),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Room Header: Code, Copy Button, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF080D18),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonCyberCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "#${record.roomCode}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Room Code", record.roomCode))
                                isCopied = true
                                Toast.makeText(context, "Room code #${record.roomCode} copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = if (isCopied) NeonToxicGreen else NeonCyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Creation Time Pill
                Text(
                    text = formatFriendlyTimestamp(record.createdAt),
                    color = Color(0xFF7F8C9E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Currently Playing / Stream Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0F1D), RoundedCornerShape(10.dp))
                    .border(0.8.dp, Color(0xFF1E283C), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = if (record.videoUrl.isNotBlank()) NeonToxicGreen else Color(0xFF5A667E),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (record.videoUrl.isNotBlank()) {
                        if (record.videoUrl.contains("youtube.com") || record.videoUrl.contains("youtu.be")) "YouTube Stream Loaded"
                        else "Direct Video Stream Loaded"
                    } else "No stream active",
                    color = if (record.videoUrl.isNotBlank()) Color.White else Color(0xFF728096),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row: Joined Participants Summary & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Participants Avatars Preview
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    if (record.participants.isNotEmpty()) {
                        record.participants.take(4).forEach { p ->
                            AvatarCircle(avatar = avatarById(p.avatarId), size = 26.dp)
                        }
                        if (record.participants.size > 4) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color(0xFF1E293B), CircleShape)
                                    .border(1.dp, NeonCyberCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${record.participants.size - 4}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${record.participants.size} joined",
                            color = NeonToxicGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            text = "Created by you (Host)",
                            color = Color(0xFF7F8C9E),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Enter Room Action Pill
                Surface(
                    color = Color(0x3300E5FF),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonCyberCyan),
                    modifier = Modifier.clickable { onEnter() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ENTER",
                            color = NeonCyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = NeonCyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRoomHistoryView(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NuclearRadiationBufferingIndicator(
                size = 72.dp,
                color = NeonCyberCyan,
                glowColor = NeonToxicGreen
            )

            Text(
                text = "NO CREATED ROOMS ARCHIVED",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Rooms created by you from the home screen will be automatically tracked here with participant history and stream logs.",
                color = Color(0xFF8A95A5),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            FuturisticHazardButton(
                text = "RETURN TO HOME DECK",
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(0.85f),
                height = 44.dp,
                gradient = listOf(NeonCyberCyan, NeonToxicGreen)
            )
        }
    }
}

/**
 * Detailed Comprehensive Inspection Dialog for a single created room.
 * Displays room code, creation time, host, active video link, and list of joined participants.
 */
@Composable
private fun RoomDetailsInspectionDialog(
    record: CreatedRoomRecord,
    onDismiss: () -> Unit,
    onEnterRoom: () -> Unit
) {
    val context = LocalContext.current
    var isCodeCopied by remember { mutableStateOf(false) }
    var isLinkCopied by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable { onDismiss() }
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color(0xF70C1322),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, Brush.verticalGradient(listOf(NeonCyberCyan, NeonHazardAmber))),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {} // Prevent backdrop dismiss
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NuclearRadiationBufferingIndicator(
                            size = 28.dp,
                            color = NeonHazardAmber,
                            glowColor = NeonToxicGreen
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ROOM TELEMETRY",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "DEEP SESSION INSPECTION",
                                color = NeonHazardAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF1E283C), CircleShape)
                        ) {
                            Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = Color(0xFF1E293B)
                    )

                    // 1. Room Code Block
                    Text(
                        text = "ROOM CODE",
                        color = Color(0xFF8A92A6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF070B14),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NeonCyberCyan)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = record.roomCode,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Room Code", record.roomCode))
                                    isCodeCopied = true
                                    Toast.makeText(context, "Room code #${record.roomCode} copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCodeCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = if (isCodeCopied) NeonToxicGreen else NeonCyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Creator & Creation Timestamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Creator Box
                        Surface(
                            color = Color(0xFF0E1626),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1F2B42)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "CREATED BY",
                                    color = Color(0xFF7F8C9E),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (record.creatorName.isNotBlank()) record.creatorName else "You (Host)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (record.creatorUsername.isNotBlank()) {
                                    Text(
                                        text = "@${record.creatorUsername}",
                                        color = NeonCyberCyan,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Created When Box
                        Surface(
                            color = Color(0xFF0E1626),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1F2B42)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "CREATED AT",
                                    color = Color(0xFF7F8C9E),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = formatFullTimestamp(record.createdAt),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Stream Link Information
                    Text(
                        text = "STREAM LINK / VIDEO ACTIVE",
                        color = Color(0xFF8A92A6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF070B14),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (record.videoUrl.isNotBlank()) NeonToxicGreen.copy(alpha = 0.5f) else Color(0xFF223048))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (record.videoUrl.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (record.videoUrl.contains("youtu")) Color(0x33FF0000) else Color(0x3300E5FF),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (record.videoUrl.contains("youtu")) "YOUTUBE" else "DIRECT LINK",
                                            color = if (record.videoUrl.contains("youtu")) Color(0xFFFF5252) else NeonCyberCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Stream Link", record.videoUrl))
                                            isLinkCopied = true
                                            Toast.makeText(context, "Stream link copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLinkCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy link",
                                            tint = if (isLinkCopied) NeonToxicGreen else NeonCyberCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = record.videoUrl,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "No stream playing in this room.",
                                    color = Color(0xFF7F8C9E),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Joined Participants Log
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "JOINED PARTICIPANTS LOG",
                            color = Color(0xFF8A92A6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${record.participants.size} MEMBERS",
                            color = NeonToxicGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (record.participants.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            record.participants.forEach { participant ->
                                Surface(
                                    color = Color(0xFF090E1A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E283C)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarCircle(avatar = avatarById(participant.avatarId), size = 34.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (participant.name.isNotBlank()) participant.name else participant.username.ifBlank { "User" },
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (participant.username.isNotBlank()) {
                                                Text(
                                                    text = "@${participant.username}",
                                                    color = NeonCyberCyan,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                        if (participant.joinedAt > 0) {
                                            Text(
                                                text = formatTimeOnly(participant.joinedAt),
                                                color = Color(0xFF6B7280),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0xFF090E1A),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1B2334)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No other users have joined this room session yet.",
                                color = Color(0xFF728096),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 5. Actions Footer
                    FuturisticHazardButton(
                        text = "LAUNCH & ENTER ROOM",
                        onClick = onEnterRoom,
                        modifier = Modifier.fillMaxWidth(),
                        height = 46.dp,
                        gradient = listOf(NeonCyberCyan, NeonToxicGreen)
                    )
                }
            }
        }
    }
}

private fun formatFriendlyTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Recently"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "$days days ago"
        hours > 0 -> "$hours hrs ago"
        minutes > 0 -> "$minutes mins ago"
        else -> "Just now"
    }
}

private fun formatFullTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown date"
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTimeOnly(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
