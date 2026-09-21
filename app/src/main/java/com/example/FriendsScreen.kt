package com.example

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.bouncyClick
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

data class FriendProfile(
    val uid: String,
    val username: String,
    val avatarId: String,
    val presenceStatus: String
)

data class IncomingRequest(val fromUid: String, val fromUsername: String)

fun dbRef() = FirebaseDatabase
    .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
    .reference

/**
 * Fetches one friend's public profile individually.
 */
fun fetchFriendProfile(
    db: com.google.firebase.database.DatabaseReference,
    fid: String,
    onResult: (FriendProfile) -> Unit
) {
    var username: String? = null
    var avatarId: String? = null
    var status: String? = null
    var completed = 0

    fun maybeFinish() {
        completed++
        if (completed == 3) {
            onResult(
                FriendProfile(
                    uid = fid,
                    username = username ?: "unknown",
                    avatarId = avatarId ?: "iron_man",
                    presenceStatus = status ?: "offline"
                )
            )
        }
    }

    db.child("users").child(fid).child("username").get()
        .addOnSuccessListener { username = it.getValue(String::class.java); maybeFinish() }
        .addOnFailureListener { maybeFinish() }

    db.child("users").child(fid).child("avatarId").get()
        .addOnSuccessListener { avatarId = it.getValue(String::class.java); maybeFinish() }
        .addOnFailureListener { maybeFinish() }

    db.child("users").child(fid).child("presence").child("status").get()
        .addOnSuccessListener { status = it.getValue(String::class.java); maybeFinish() }
        .addOnFailureListener { maybeFinish() }
}

@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    currentRoomCode: String? = null
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val myUid = auth.currentUser?.uid ?: ""
    val db = remember { dbRef() }

    var selectedTab by remember { mutableStateOf(0) }
    var friends by remember { mutableStateOf<List<FriendProfile>>(emptyList()) }
    var requests by remember { mutableStateOf<List<IncomingRequest>>(emptyList()) }
    var invitedUids by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Listen to friends list (uid -> true), then resolve each friend's public profile
    DisposableEffect(myUid) {
        val friendsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val friendIds = snapshot.children.mapNotNull { it.key }
                if (friendIds.isEmpty()) {
                    friends = emptyList()
                    return
                }
                val resolved = mutableListOf<FriendProfile>()
                var remaining = friendIds.size
                friendIds.forEach { fid ->
                    fetchFriendProfile(db, fid) { profile ->
                        resolved.add(profile)
                        remaining--
                        if (remaining == 0) friends = resolved.sortedBy { it.username }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("users").child(myUid).child("friends").addValueEventListener(friendsListener)

        val requestsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                requests = snapshot.children.mapNotNull { child ->
                    val fromUid = child.key ?: return@mapNotNull null
                    val fromUsername = child.child("username").getValue(String::class.java) ?: "unknown"
                    IncomingRequest(fromUid, fromUsername)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("users").child(myUid).child("friendRequests").child("incoming")
            .addValueEventListener(requestsListener)

        onDispose {
            db.child("users").child(myUid).child("friends").removeEventListener(friendsListener)
            db.child("users").child(myUid).child("friendRequests").child("incoming")
                .removeEventListener(requestsListener)
        }
    }

    FuturisticCyberBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                FuturisticTopBar(
                    title = "FRIENDS",
                    subtitle = if (currentRoomCode != null) "INVITE TO ROOM: $currentRoomCode" else "CONNECT & WATCH TOGETHER",
                    onNavigateBack = onNavigateBack,
                    statusText = if (requests.isNotEmpty()) "REQUESTS (${requests.size})" else "ONLINE",
                    statusColor = if (requests.isNotEmpty()) NeonHazardAmber else NeonToxicGreen
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Room transmission alert banner
                if (currentRoomCode != null) {
                    FuturisticGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        borderColors = listOf(NeonCrimson, NeonHazardAmber, NeonCyberCyan)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(NeonHazardAmber, CircleShape)
                                    .shadow(8.dp, CircleShape, spotColor = NeonHazardAmber)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "BROADCASTING ROOM CODE: $currentRoomCode",
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Futuristic Cyber Tabs (OPERATIVES, UPLINK, REQUESTS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(Color(0xFF090D18), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "FRIENDS",
                        "ADD FRIEND",
                        if (requests.isEmpty()) "REQUESTS" else "REQUESTS (${requests.size})"
                    )

                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.horizontalGradient(listOf(NeonCrimson, NeonHazardAmber))
                                    } else {
                                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                    }
                                )
                                .bouncyClick { selectedTab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else Color(0xFF7E8A9E),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> FriendsListTab(
                        friends = friends,
                        currentRoomCode = currentRoomCode,
                        invitedUids = invitedUids,
                        onInviteToWatch = { friend ->
                            val roomCode = currentRoomCode
                            if (roomCode != null) {
                                sendWatchInvite(db, myUid, friend.uid, roomCode, context) {
                                    invitedUids = invitedUids + friend.uid
                                }
                            }
                        },
                        onUnfriend = { friendUid ->
                            val updates = mapOf(
                                "users/$myUid/friends/$friendUid" to null,
                                "users/$friendUid/friends/$myUid" to null
                            )
                            db.updateChildren(updates)
                        }
                    )
                    1 -> AddFriendTab(myUid = myUid, myFriends = friends, db = db, context = context)
                    2 -> RequestsTab(
                        requests = requests,
                        onAccept = { req ->
                            val updates = mapOf(
                                "users/$myUid/friends/${req.fromUid}" to true,
                                "users/${req.fromUid}/friends/$myUid" to true,
                                "users/$myUid/friendRequests/incoming/${req.fromUid}" to null,
                                "users/${req.fromUid}/friendRequests/outgoing/$myUid" to null
                            )
                            db.updateChildren(updates)
                        },
                        onDecline = { req ->
                            val updates = mapOf(
                                "users/$myUid/friendRequests/incoming/${req.fromUid}" to null,
                                "users/${req.fromUid}/friendRequests/outgoing/$myUid" to null
                            )
                            db.updateChildren(updates)
                        }
                    )
                }
            }
        }
    }
}

private fun sendWatchInvite(
    db: com.google.firebase.database.DatabaseReference,
    myUid: String,
    toUid: String,
    roomCode: String,
    context: android.content.Context,
    onSent: () -> Unit
) {
    db.child("users").child(myUid).child("username").get().addOnSuccessListener { mySnap ->
        val myUsername = mySnap.getValue(String::class.java) ?: "unknown"
        val inviteRef = db.child("users").child(toUid).child("watchInvites").push()
        val invite = mapOf(
            "fromId" to myUid,
            "fromUsername" to myUsername,
            "roomCode" to roomCode,
            "timestamp" to ServerValue.TIMESTAMP
        )
        inviteRef.setValue(invite)
            .addOnSuccessListener {
                Toast.makeText(context, "Watch invite sent!", Toast.LENGTH_SHORT).show()
                onSent()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to send invite", Toast.LENGTH_SHORT).show()
            }
    }.addOnFailureListener {
        Toast.makeText(context, "Failed to send invite", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun FriendsListTab(
    friends: List<FriendProfile>,
    currentRoomCode: String?,
    invitedUids: Set<String>,
    onInviteToWatch: (FriendProfile) -> Unit,
    onUnfriend: (String) -> Unit
) {
    if (friends.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF3E485E),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "NO FRIENDS YET",
                    color = Color(0xFF6B7892),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Go to 'Add Friend' tab to search and add friends!",
                    color = Color(0xFF4A5568),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        items(friends) { friend ->
            FuturisticGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with glowing ring
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .border(1.5.dp, NeonCyberCyan.copy(alpha = 0.6f), CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarCircle(avatar = avatarById(friend.avatarId), size = 44.dp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = friend.username,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (dotColor, statusLabel) = when (friend.presenceStatus) {
                                "online" -> NeonToxicGreen to "ONLINE"
                                "watching" -> NeonCyberCyan to "WATCHING"
                                else -> Color(0xFF6B7280) to "OFFLINE"
                            }
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(dotColor, CircleShape)
                                    .shadow(4.dp, CircleShape, spotColor = dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusLabel,
                                color = dotColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (currentRoomCode != null) {
                            val alreadyInvited = friend.uid in invitedUids
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (alreadyInvited) Color(0xFF1E2638) else Color(0x3300E5FF)
                                    )
                                    .border(
                                        1.dp,
                                        if (alreadyInvited) Color(0xFF334057) else NeonCyberCyan,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .bouncyClick { if (!alreadyInvited) onInviteToWatch(friend) }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (alreadyInvited) "INVITED" else "INVITE",
                                    color = if (alreadyInvited) Color(0xFF7B879C) else NeonCyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FF1744))
                                .border(1.dp, NeonCrimson.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .bouncyClick { onUnfriend(friend.uid) }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "REMOVE",
                                color = NeonCrimson,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFriendTab(
    myUid: String,
    myFriends: List<FriendProfile>,
    db: com.google.firebase.database.DatabaseReference,
    context: android.content.Context
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var sentTo by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        val q = query.trim().lowercase()
        db.child("usernames").orderByKey().startAt(q).endAt(q + "\uf8ff").limitToFirst(15)
            .get().addOnSuccessListener { snap ->
                results = snap.children.mapNotNull { child ->
                    val uname = child.key ?: return@mapNotNull null
                    val uid = child.getValue(String::class.java) ?: return@mapNotNull null
                    if (uid == myUid) null else uid to uname
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FuturisticTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search by username...",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = NeonCyberCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val friendUids = myFriends.map { it.uid }.toSet()
        val listToShow = if (query.isBlank()) {
            myFriends.sortedByDescending { it.presenceStatus != "offline" }.take(5)
                .map { it.uid to it.username }
        } else results

        if (query.isBlank() && listToShow.isNotEmpty()) {
            Text(
                text = "SUGGESTED FRIENDS",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8A92A6),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listToShow) { (uid, uname) ->
                val alreadyFriend = uid in friendUids
                val alreadySent = uid in sentTo

                FuturisticGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uname,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )

                        when {
                            alreadyFriend -> {
                                Text(
                                    text = "FRIENDS",
                                    color = NeonToxicGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            alreadySent -> {
                                Text(
                                    text = "REQUEST SENT",
                                    color = NeonHazardAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Brush.horizontalGradient(listOf(NeonCrimson, NeonHazardAmber)))
                                        .bouncyClick {
                                            sendFriendRequest(db, myUid, uid, uname, context)
                                            sentTo = sentTo + uid
                                        }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ADD FRIEND",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
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

private fun sendFriendRequest(
    db: com.google.firebase.database.DatabaseReference,
    myUid: String,
    toUid: String,
    toUsername: String,
    context: android.content.Context
) {
    db.child("users").child(myUid).child("username").get().addOnSuccessListener { mySnap ->
        val myUsername = mySnap.getValue(String::class.java) ?: "unknown"
        val updates = mapOf(
            "users/$toUid/friendRequests/incoming/$myUid" to mapOf(
                "username" to myUsername,
                "timestamp" to ServerValue.TIMESTAMP
            ),
            "users/$myUid/friendRequests/outgoing/$toUid" to mapOf(
                "username" to toUsername,
                "timestamp" to ServerValue.TIMESTAMP
            )
        )
        db.updateChildren(updates).addOnFailureListener {
            Toast.makeText(context, "Failed to send friend request", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun RequestsTab(
    requests: List<IncomingRequest>,
    onAccept: (IncomingRequest) -> Unit,
    onDecline: (IncomingRequest) -> Unit
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "NO PENDING FRIEND REQUESTS",
                color = Color(0xFF6B7892),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(requests) { req ->
            FuturisticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColors = listOf(NeonCrimson, NeonHazardAmber)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = req.fromUsername,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Sent you a friend request",
                            color = NeonHazardAmber,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.horizontalGradient(listOf(NeonToxicGreen, NeonCyberCyan)))
                                .bouncyClick { onAccept(req) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ACCEPT",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33FF1744))
                                .border(1.dp, NeonCrimson.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .bouncyClick { onDecline(req) }
                            .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "DECLINE",
                                color = NeonCrimson,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
