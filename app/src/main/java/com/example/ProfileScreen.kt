package com.example

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.UraniumMotion
import com.example.ui.theme.bouncyClick
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    val uid = currentUser?.uid ?: ""

    // Instant zero-latency local cache retrieval so name is never missing upon entry
    val cachedInitialName = remember(uid) {
        val fromStorage = UserProfileStorage.getCachedName(context, uid)
        if (fromStorage.isNotBlank()) fromStorage else currentUser?.displayName ?: ""
    }
    val cachedInitialUsername = remember(uid) {
        UserProfileStorage.getCachedUsername(context, uid)
    }
    val cachedInitialAvatar = remember(uid) {
        UserProfileStorage.getCachedAvatar(context, uid)
    }

    var name by remember(uid) { mutableStateOf(cachedInitialName) }
    var nameInput by remember(uid) { mutableStateOf(cachedInitialName) }
    var username by remember(uid) { mutableStateOf(cachedInitialUsername) }
    var avatarId by remember(uid) { mutableStateOf(cachedInitialAvatar) }

    var isPickingAvatar by remember { mutableStateOf(false) }
    var isSavingName by remember { mutableStateOf(false) }
    var saveSuccessTime by remember { mutableStateOf(0L) }

    fun performSaveName(onComplete: (() -> Unit)? = null) {
        val cleanName = nameInput.trim()
        if (cleanName.isBlank()) {
            Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        isSavingName = true
        name = cleanName

        UserProfileStorage.saveNameEverywhere(
            context = context,
            name = cleanName,
            onSuccess = {
                isSavingName = false
                saveSuccessTime = System.currentTimeMillis()
                Toast.makeText(context, "Name saved successfully!", Toast.LENGTH_SHORT).show()
                onComplete?.invoke()
            },
            onFailure = { err ->
                isSavingName = false
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                onComplete?.invoke()
            }
        )
    }

    // Auto-save on navigate back if the user typed something new and hits back
    val safeNavigateBack: () -> Unit = {
        val cleanDraft = nameInput.trim()
        if (cleanDraft.isNotBlank() && cleanDraft != name.trim()) {
            UserProfileStorage.saveNameEverywhere(context, cleanDraft)
        }
        onNavigateBack()
    }

    BackHandler(onBack = safeNavigateBack)

    // Listen to Firebase RTDB for cloud sync
    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose {}

        val userRef = FirebaseDatabase
            .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
            .reference.child("users").child(uid)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cloudName = snapshot.child("name").getValue(String::class.java)
                val cloudUsername = snapshot.child("username").getValue(String::class.java)
                val cloudAvatar = snapshot.child("avatarId").getValue(String::class.java)

                if (!cloudName.isNullOrBlank()) {
                    name = cloudName.trim()
                    if (nameInput.isBlank() || nameInput == cachedInitialName) {
                        nameInput = cloudName.trim()
                    }
                    UserProfileStorage.saveNameLocally(context, uid, cloudName.trim())
                } else if (name.isNotBlank()) {
                    userRef.child("name").setValue(name)
                    userRef.child("displayName").setValue(name)
                }

                if (!cloudUsername.isNullOrBlank()) {
                    username = cloudUsername.trim()
                    UserProfileStorage.saveUsernameLocally(context, uid, cloudUsername.trim())
                }

                if (!cloudAvatar.isNullOrBlank()) {
                    val cleanAvatar = if (cloudAvatar.startsWith("avatar_")) "iron_man" else cloudAvatar.trim()
                    avatarId = cleanAvatar
                    UserProfileStorage.saveAvatarLocally(context, uid, cleanAvatar)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        userRef.addValueEventListener(listener)
        onDispose { userRef.removeEventListener(listener) }
    }

    val transition = rememberInfiniteTransition(label = "avatarPedestal")
    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "ringRotation"
    )
    val ringPulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringPulse"
    )

    FuturisticCyberBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                FuturisticTopBar(
                    title = "MY PROFILE",
                    subtitle = "ACCOUNT & SETTINGS",
                    onNavigateBack = safeNavigateBack,
                    statusText = "ONLINE",
                    statusColor = NeonToxicGreen
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Sci-Fi Avatar Hologram Pedestal
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .bouncyClick { isPickingAvatar = !isPickingAvatar },
                    contentAlignment = Alignment.Center
                ) {
                    // Rotating atomic orbital rings (Simple high-school physics theme)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f

                        // Outer orbital glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeonCrimson.copy(alpha = 0.35f * ringPulse),
                                    NeonCyberCyan.copy(alpha = 0.20f * ringPulse),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = r
                            )
                        )

                        // Rotating electron orbit ring
                        rotate(ringRotation, center) {
                            drawCircle(
                                color = NeonCrimson.copy(alpha = 0.85f),
                                radius = r * 0.90f,
                                style = Stroke(
                                    width = 2.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(25f, 20f), 0f)
                                )
                            )
                            drawCircle(
                                color = NeonCyberCyan.copy(alpha = 0.70f),
                                radius = r * 0.76f,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 10f)
                                )
                            )
                        }

                        // Inner energy core
                        drawCircle(
                            color = NeonHazardAmber.copy(alpha = 0.6f * ringPulse),
                            radius = r * 0.68f,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    AvatarCircle(
                        avatar = avatarById(avatarId),
                        size = 88.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Avatar change trigger
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0x2200E5FF))
                        .border(1.dp, NeonCyberCyan.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                        .bouncyClick { isPickingAvatar = !isPickingAvatar }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isPickingAvatar) "CLOSE AVATARS" else "CHANGE AVATAR",
                        color = NeonCyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                AnimatedVisibility(
                    visible = isPickingAvatar,
                    enter = fadeIn(UraniumMotion.fade()),
                    exit = fadeOut(UraniumMotion.fade())
                ) {
                    FuturisticGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        borderColors = listOf(NeonCyberCyan, NeonCrimson, NeonHazardAmber)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "CHOOSE YOUR AVATAR",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            AvatarPickerGrid(
                                selectedId = avatarId,
                                onSelect = { newId ->
                                    avatarId = newId
                                    isPickingAvatar = false
                                    UserProfileStorage.saveAvatarLocally(context, uid, newId)
                                    if (uid.isNotBlank()) {
                                        FirebaseDatabase
                                            .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
                                            .reference.child("users").child(uid).child("avatarId").setValue(newId)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Profile Information Card
                FuturisticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColors = listOf(NeonCyberCyan, NeonCrimson.copy(alpha = 0.5f), Color(0xFF1E2838))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Header and status pill
                        val isDraftModified = nameInput.trim() != name.trim()
                        val isRecentSave = System.currentTimeMillis() - saveSuccessTime < 3500

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(NeonCyberCyan, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PERSONAL DETAILS",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }

                            // Dynamic sync status pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isRecentSave -> Color(0x3300E676)
                                            isDraftModified -> Color(0x33FF9100)
                                            else -> Color(0x2200E5FF)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isRecentSave -> NeonToxicGreen
                                            isDraftModified -> NeonHazardAmber
                                            else -> NeonCyberCyan.copy(alpha = 0.6f)
                                        },
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = when {
                                        isRecentSave -> "SAVED"
                                        isDraftModified -> "UNSAVED"
                                        else -> "SAVED"
                                    },
                                    color = when {
                                        isRecentSave -> NeonToxicGreen
                                        isDraftModified -> NeonHazardAmber
                                        else -> NeonCyberCyan
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Current Name Display
                        Text(
                            text = "CURRENT NAME",
                            color = Color(0xFF8A92A6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (name.isNotBlank()) name else if (username.isNotBlank()) username else "Guest",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Proper Name Input Field
                        Text(
                            text = "YOUR NAME",
                            color = NeonCyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This name is shown to your friends when you watch videos together.",
                            color = Color(0xFF8A95A5),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FuturisticTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            placeholder = "Enter your name...",
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = NeonCyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (nameInput.isNotBlank()) {
                                    IconButton(
                                        onClick = { nameInput = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear name field",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                capitalization = KeyboardCapitalization.Words
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    performSaveName()
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dedicated Save Button
                        val buttonText = when {
                            isRecentSave -> "✓ NAME SAVED"
                            isDraftModified -> "SAVE NAME"
                            else -> "SAVE NAME"
                        }
                        val buttonGradient = when {
                            isRecentSave -> listOf(NeonToxicGreen, Color(0xFF00E5FF))
                            isDraftModified -> listOf(NeonCyberCyan, NeonCrimson, NeonHazardAmber)
                            else -> listOf(Color(0xFF1E283C), Color(0xFF121B2A))
                        }

                        FuturisticHazardButton(
                            text = buttonText,
                            onClick = { performSaveName() },
                            isLoading = isSavingName,
                            gradient = buttonGradient,
                            height = 46.dp,
                            cornerRadius = 10.dp
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 18.dp),
                            color = Color(0xFF1E2638)
                        )

                        // Fixed Username
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "USERNAME",
                                    color = Color(0xFF8A92A6),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (username.isBlank()) "—" else "@$username",
                                    color = NeonCyberCyan,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF5A667E),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "Your unique username that friends use to find and add you.",
                            color = Color(0xFF8A95A5),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reactor & System Status Card (Simple high school physics theme)
                FuturisticGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColors = listOf(Color(0xFF223048), Color(0xFF121B2A))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = NeonToxicGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SYSTEM & REACTOR STATUS",
                                color = NeonToxicGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "• Core Connection: Online (Realtime Sync)\n• Energy State: Stable (Room Ready)\n• Video Sync: Ready for watch party",
                            color = Color(0xFF9EABB8),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Logout Button
                FuturisticHazardButton(
                    text = "LOG OUT OF ACCOUNT",
                    onClick = onLogout,
                    gradient = listOf(NeonCrimson, Color(0xFF88001B), NeonHazardAmber),
                    height = 52.dp
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
