package com.example

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ServerValue
import com.google.firebase.database.FirebaseDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRoom: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var joinRoomCode by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }

    val db = remember { FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com").reference }
    val auth = remember { FirebaseAuth.getInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uranium TV") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Log Out")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    isCreating = true
                    val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                    val roomCode = (1..6).map { allowedChars.random() }.joinToString("")
                    val uid = auth.currentUser?.uid ?: ""

                    val roomData = mapOf(
                        "videoUrl" to "",
                        "isPlaying" to false,
                        "position" to 0L,
                        "lastUpdatedBy" to uid,
                        "lastUpdatedAt" to ServerValue.TIMESTAMP
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isCreating && !isJoining
            ) {
                Text(if (isCreating) "Creating..." else "Create Room")
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text("OR", style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(48.dp))

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isCreating && !isJoining
            ) {
                Text(if (isJoining) "Joining..." else "Join Room")
            }

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedButton(
                onClick = onNavigateToFriends,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Friends")
            }
        }
    }
}
