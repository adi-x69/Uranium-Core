package com.example

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.theme.UraniumMotion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import com.example.ui.theme.bouncyClick

/**
 * Account screen reachable from the profile icon in Home's top bar.
 * Shows the signed-in user's avatar (tap to change from the preset set),
 * their display name (editable), their username (fixed - it's tied to
 * login and to how friends find them), and Log Out at the very bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""
    val db = remember {
        FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
            .reference.child("users").child(uid)
    }

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var avatarId by remember { mutableStateOf(PRESET_AVATARS[0].id) }

    var isEditingName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var isPickingAvatar by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                name = snapshot.child("name").getValue(String::class.java) ?: ""
                username = snapshot.child("username").getValue(String::class.java) ?: ""
                avatarId = snapshot.child("avatarId").getValue(String::class.java) ?: PRESET_AVATARS[0].id
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        onDispose { db.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            AvatarCircle(
                avatar = avatarById(avatarId),
                size = 100.dp,
                modifier = Modifier.bouncyClick { isPickingAvatar = !isPickingAvatar }
            )
            TextButton(onClick = { isPickingAvatar = !isPickingAvatar }) {
                Text(if (isPickingAvatar) "Cancel" else "Change profile picture")
            }

            AnimatedVisibility(
                visible = isPickingAvatar,
                enter = fadeIn(UraniumMotion.fade()),
                exit = fadeOut(UraniumMotion.fade())
            ) {
                AvatarPickerGrid(
                    selectedId = avatarId,
                    onSelect = { newId ->
                        avatarId = newId
                        isPickingAvatar = false
                        db.child("avatarId").setValue(newId)
                    },
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display name - editable
            if (isEditingName) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val cleanName = nameDraft.trim()
                        if (cleanName.isBlank()) {
                            Toast.makeText(context, "Name can't be empty", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        name = cleanName
                        db.child("name").setValue(cleanName)
                        isEditingName = false
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save name")
                    }
                }
            } else {
                ProfileInfoRow(
                    label = "Name",
                    value = name.ifBlank { "Add your name" },
                    trailingIcon = Icons.Default.Edit,
                    onTrailingClick = {
                        nameDraft = name
                        isEditingName = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ProfileInfoRow(label = "Username", value = if (username.isBlank()) "" else "@$username")
            Text(
                text = "Your username is how friends find you and can't be changed here.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Log Out")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
        if (trailingIcon != null && onTrailingClick != null) {
            IconButton(onClick = onTrailingClick) {
                Icon(trailingIcon, contentDescription = "Edit $label")
            }
        }
    }
}

/** Small helper so the avatar circle itself is tappable, matching the "tap to change" pattern. */
private fun Modifier.clickableAvatar(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
