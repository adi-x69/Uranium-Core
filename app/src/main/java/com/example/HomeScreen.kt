package com.example

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.theme.UraniumMotion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uranium TV", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = onNavigateToProfile, modifier = Modifier.padding(end = 8.dp)) {
                        AvatarCircle(avatar = avatarById(myAvatarId), size = 36.dp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
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
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        "Watch Invites",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    invites.forEach { invite ->
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${invite.fromUsername} invited you to Room ${invite.roomCode}",
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    db.child("users").child(uid).child("watchInvites")
                                        .child(invite.id).removeValue()
                                    onNavigateToRoom(invite.roomCode)
                                }) { Text("Join") }
                                TextButton(onClick = {
                                    db.child("users").child(uid).child("watchInvites")
                                        .child(invite.id).removeValue()
                                }) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
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
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isCreating && !isJoining
                ) {
                    Text(if (isCreating) "Creating..." else "Create Room")
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text("OR", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = joinRoomCode,
                    onValueChange = { joinRoomCode = it.uppercase() },
                    label = { Text("Room Code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (joinRoomCode.isBlank()) {
                            Toast.makeText(context, "Enter a room code", Toast.LENGTH_SHORT).show()
                            return@Button
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
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isCreating && !isJoining
                ) {
                    Text(if (isJoining) "Joining..." else "Join Room")
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = onNavigateToFriends,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Friends")
                }
            }
        }
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
                    Box {
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
                            IconButton(onClick = { onResume(entry) }) {
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
