package com.example

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

private fun dbRef() = FirebaseDatabase
    .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
    .reference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    onInviteToWatch: (FriendProfile) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val myUid = auth.currentUser?.uid ?: ""
    val db = remember { dbRef() }

    var selectedTab by remember { mutableStateOf(0) }
    var friends by remember { mutableStateOf<List<FriendProfile>>(emptyList()) }
    var requests by remember { mutableStateOf<List<IncomingRequest>>(emptyList()) }

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
                    db.child("users").child(fid).get().addOnSuccessListener { snap ->
                        val username = snap.child("username").getValue(String::class.java) ?: "unknown"
                        val avatarId = snap.child("avatarId").getValue(String::class.java) ?: "avatar_1"
                        val status = snap.child("presence").child("status").getValue(String::class.java) ?: "offline"
                        resolved.add(FriendProfile(fid, username, avatarId, status))
                        remaining--
                        if (remaining == 0) friends = resolved.sortedBy { it.username }
                    }.addOnFailureListener {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Friends") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Add") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(if (requests.isEmpty()) "Requests" else "Requests (${requests.size})")
                    }
                )
            }

            when (selectedTab) {
                0 -> FriendsListTab(
                    friends = friends,
                    onInviteToWatch = onInviteToWatch,
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

@Composable
private fun FriendsListTab(
    friends: List<FriendProfile>,
    onInviteToWatch: (FriendProfile) -> Unit,
    onUnfriend: (String) -> Unit
) {
    if (friends.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No friends yet. Add someone from the Add tab.", color = Color.Gray)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(friends) { friend ->
            ListItem(
                leadingContent = { AvatarCircle(avatar = avatarById(friend.avatarId), size = 44.dp) },
                headlineContent = { Text(friend.username) },
                supportingContent = {
                    val label = when (friend.presenceStatus) {
                        "online" -> "Online"
                        "watching" -> "Watching something"
                        else -> "Offline"
                    }
                    Text(label, color = if (friend.presenceStatus == "offline") Color.Gray else Color(0xFF4CAF50))
                },
                trailingContent = {
                    Row {
                        TextButton(onClick = { onInviteToWatch(friend) }) { Text("Invite") }
                        TextButton(onClick = { onUnfriend(friend.uid) }) { Text("Remove") }
                    }
                }
            )
            Divider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFriendTab(
    myUid: String,
    myFriends: List<FriendProfile>,
    db: com.google.firebase.database.DatabaseReference,
    context: android.content.Context
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // uid to username
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        val friendUids = myFriends.map { it.uid }.toSet()
        val listToShow = if (query.isBlank()) {
            myFriends.sortedByDescending { it.presenceStatus != "offline" }.take(5)
                .map { it.uid to it.username }
        } else results

        if (query.isBlank() && listToShow.isNotEmpty()) {
            Text("Suggestions", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn {
            items(listToShow) { (uid, uname) ->
                val alreadyFriend = uid in friendUids
                val alreadySent = uid in sentTo
                ListItem(
                    headlineContent = { Text(uname) },
                    trailingContent = {
                        when {
                            alreadyFriend -> Text("Friends", color = Color.Gray)
                            alreadySent -> Text("Sent", color = Color.Gray)
                            else -> TextButton(onClick = {
                                sendFriendRequest(db, myUid, uid, uname, context)
                                sentTo = sentTo + uid
                            }) { Text("Add") }
                        }
                    }
                )
                Divider()
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
            Toast.makeText(context, "Failed to send request", Toast.LENGTH_SHORT).show()
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
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No pending requests.", color = Color.Gray)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(requests) { req ->
            ListItem(
                headlineContent = { Text(req.fromUsername) },
                trailingContent = {
                    Row {
                        TextButton(onClick = { onAccept(req) }) { Text("Accept") }
                        TextButton(onClick = { onDecline(req) }) { Text("Decline") }
                    }
                }
            )
            Divider()
        }
    }
}
