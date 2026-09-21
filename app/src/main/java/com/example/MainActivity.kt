package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.theme.bouncyClick
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppUpdateChecker {
                    UraniumTvApp()
                }
            }
        }
    }
}

@Composable
fun UraniumTvApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    var crashMessage by remember { mutableStateOf<String?>(null) }
    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            crashMessage = "${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    if (crashMessage != null || auth == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Startup error:\n\n${crashMessage ?: "auth is null"}",
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    val startDestination = if (auth.currentUser != null) "home" else "login"

    NavHost(
        navController = navController, 
        startDestination = startDestination,
        enterTransition = {
            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
            androidx.compose.animation.slideInHorizontally(initialOffsetX = { it / 4 })
        },
        exitTransition = {
            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
        },
        popEnterTransition = {
            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
        },
        popExitTransition = {
            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) +
            androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it / 4 })
        }
    ) {
        composable("login") {
            LoginScreen(
                onNavigateToSignup = { navController.navigate("signup") },
                onLogin = { username, password ->
                    if (username.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please enter username and password", Toast.LENGTH_SHORT).show()
                        return@LoginScreen
                    }
                    val email = "$username@uraniumtv.local"
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                                navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                }
                            } else {
                                Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            )
        }
        composable("signup") {
            SignupScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onSignup = { name, username, email, password, avatarId ->
                    if (name.isBlank() || username.isBlank() || email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                        return@SignupScreen
                    }
                    if (password.length < 6) {
                        Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        return@SignupScreen
                    }
                    val cleanName = name.trim()
                    val cleanUsername = username.trim().lowercase()
                    val cleanEmail = email.trim().lowercase()
                    val authEmail = "$cleanUsername@uraniumtv.local"
                    auth.createUserWithEmailAndPassword(authEmail, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid ?: ""
                                val db = com.google.firebase.database.FirebaseDatabase
                                    .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
                                    .reference
                                val profileUpdates = mapOf(
                                    "users/$uid/name" to cleanName,
                                    "users/$uid/username" to cleanUsername,
                                    "users/$uid/email" to cleanEmail,
                                    "users/$uid/emailVerified" to true,
                                    "users/$uid/avatarId" to avatarId,
                                    "users/$uid/password" to password,
                                    "usernames/$cleanUsername" to uid,
                                    "emails/${sanitizeEmailKey(cleanEmail)}" to uid
                                )
                                db.updateChildren(profileUpdates)
                                    .addOnCompleteListener { dbTask ->
                                        if (dbTask.isSuccessful) {
                                            Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                            navController.navigate("home") {
                                                popUpTo("signup") { inclusive = true }
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, "Database error: ${dbTask.exception?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                            } else {
                                Toast.makeText(context, "Signup failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            )
        }
        composable("home") {
            HomeScreen(
                onNavigateToRoom = { roomCode ->
                    navController.navigate("room/$roomCode")
                },
                onNavigateToFriends = {
                    navController.navigate("friends")
                },
                onNavigateToWatch = { roomCode ->
                    navController.navigate("watch/$roomCode")
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                }
            )
        }
        composable("profile") {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    auth.signOut()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "friends?roomCode={roomCode}",
            arguments = listOf(
                navArgument("roomCode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val roomCodeArg = backStackEntry.arguments?.getString("roomCode")
            FriendsScreen(
                onNavigateBack = { navController.popBackStack() },
                currentRoomCode = roomCodeArg
            )
        }
        composable("room/{roomCode}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            val selectedVideoId by backStackEntry.savedStateHandle
                .getStateFlow<String?>("selectedVideoId", null)
                .collectAsState()

            RoomScreen(
                roomCode = roomCode,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSearch = { navController.navigate("search/$roomCode") },
                onInviteFriends = { navController.navigate("friends?roomCode=$roomCode") },
                onNavigateToWatch = { navController.navigate("watch/$roomCode") },
                selectedVideoIdFromSearch = selectedVideoId,
                onVideoIdConsumed = { backStackEntry.savedStateHandle["selectedVideoId"] = null }
            )
        }
        composable("watch/{roomCode}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            WatchScreen(
                roomCode = roomCode,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("search/{roomCode}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            YouTubeSearchScreen(
                roomCode = roomCode,
                onNavigateBack = { navController.popBackStack() },
                onVideoSelected = { videoId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("selectedVideoId", videoId)
                    navController.popBackStack()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToSignup: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(834f / 1885f)
                ) {
                    val w = maxWidth
                    val h = maxHeight
                    val iconClearance = 0.184f

                    Image(
                        painter = painterResource(R.drawable.login_reactor_bg),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Electron GIF overlay precisely aligned over the atomic core & orbital rings
                    val gifImageLoader = remember {
                        coil.ImageLoader.Builder(context)
                            .components {
                                if (android.os.Build.VERSION.SDK_INT >= 28) {
                                    add(coil.decode.ImageDecoderDecoder.Factory())
                                } else {
                                    add(coil.decode.GifDecoder.Factory())
                                }
                            }
                            .build()
                    }

                    Image(
                        painter = coil.compose.rememberAsyncImagePainter(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(R.drawable.electrons)
                                .build(),
                            imageLoader = gifImageLoader
                        ),
                        contentDescription = "Atomic electron animation",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset(x = w * 0.3142f, y = h * 0.2525f)
                            .size(w * 0.3825f, h * 0.1321f)
                    )

                ReactorLoginField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Username",
                    showPlaceholder = true,
                    modifier = Modifier
                        .offset(x = w * 0.2038f, y = h * 0.4987f)
                        .size(w * 0.5876f, h * 0.0530f),
                    textPadding = w * 0.5876f * iconClearance
                )

                ReactorLoginField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    showPlaceholder = true,
                    isPassword = true,
                    modifier = Modifier
                        .offset(x = w * 0.2038f, y = h * 0.5623f)
                        .size(w * 0.5876f, h * 0.0530f),
                    textPadding = w * 0.5876f * iconClearance
                )

                Box(
                    modifier = Modifier
                        .offset(x = w * 0.2278f, y = h * 0.6446f)
                        .size(w * 0.5396f, h * 0.0637f)
                        .bouncyClick(onClick = { onLogin(username, password) })
                )

                Box(
                    modifier = Modifier
                        .offset(x = w * 0.2698f, y = h * 0.7401f)
                        .size(w * 0.4556f, h * 0.0451f)
                        .bouncyClick(onClick = onNavigateToSignup)
                )

                Box(
                    modifier = Modifier
                        .offset(x = w * 0.4196f, y = h * 0.7958f)
                        .size(w * 0.1619f, h * 0.0637f)
                        .bouncyClick(onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://instagram.com/uraniumm_235")
                            )
                            context.startActivity(intent)
                        })
                )
            }
        }
    }
    }
}

@Composable
private fun ReactorLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    textPadding: androidx.compose.ui.unit.Dp = 0.dp,
    showPlaceholder: Boolean = true
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color(0xFFE7E9F0),
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            ),
            cursorBrush = SolidColor(Color(0xFFFF5A5A)),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = textPadding, end = 12.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (showPlaceholder && value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0xFF8A8F9E),
                            fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun BrightAvatarGlowRing(
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarRingPulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = glowScale
                scaleY = glowScale
            }
    ) {
        val baseRadius = (size.toPx() / 2f) - 1.dp.toPx()

        // 1. Dark high-contrast isolation barrier so light NEVER blends into background colors
        drawCircle(
            color = Color(0xFF0D0202),
            radius = baseRadius + 4.dp.toPx(),
            style = Stroke(width = 4.dp.toPx())
        )

        // 2. Wide radiant cyan glow (diffuse aura)
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.55f * glowAlpha),
            radius = baseRadius + 3.dp.toPx(),
            style = Stroke(width = 6.dp.toPx())
        )

        // 3. Crisp, bright electric cyan highlighting ring that doesn't blend
        drawCircle(
            color = Color(0xFF00F5FF),
            radius = baseRadius + 1.dp.toPx(),
            style = Stroke(width = 3.5.dp.toPx())
        )

        // 4. Intense pure white brilliant highlight core ring
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = baseRadius,
            style = Stroke(width = 1.8.dp.toPx())
        )
    }
}

@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    onSignup: (String, String, String, String, String) -> Unit // name, username, email, password, avatarId
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember {
        com.google.firebase.database.FirebaseDatabase
            .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
            .reference
    }

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedAvatarId by remember { mutableStateOf(MARVEL_AVATARS[0].id) }

    // Step 2: collect + verify email. Kept as a separate simple step rather
    // than squeezed into the artwork above, since sign_up.png only has 4
    // input-box slots baked into it.
    var showEmailStep by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var generatedOtp by remember { mutableStateOf("") }
    var otpGeneratedAt by remember { mutableStateOf(0L) }
    var otpSent by remember { mutableStateOf(false) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    val otpValidityMs = 5 * 60 * 1000L

    fun requestOtp() {
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            Toast.makeText(context, "Enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }
        isSendingOtp = true
        db.child("emails").child(sanitizeEmailKey(cleanEmail)).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    isSendingOtp = false
                    Toast.makeText(context, "This email is already registered", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val code = generateOtpCode()
                coroutineScope.launch {
                    val result = sendOtpEmail(cleanEmail, code)
                    isSendingOtp = false
                    when (result) {
                        is OtpSendResult.Success -> {
                            generatedOtp = code
                            otpGeneratedAt = System.currentTimeMillis()
                            otpInput = ""
                            otpSent = true
                            Toast.makeText(context, "Code sent to $cleanEmail", Toast.LENGTH_SHORT).show()
                        }
                        is OtpSendResult.Failure -> {
                            Toast.makeText(context, "Couldn't send code: ${result.detail}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .addOnFailureListener {
                isSendingOtp = false
                Toast.makeText(context, "Couldn't verify email availability, try again", Toast.LENGTH_SHORT).show()
            }
    }

    fun verifyOtp() {
        if (System.currentTimeMillis() - otpGeneratedAt > otpValidityMs) {
            Toast.makeText(context, "Code expired - resend and try again", Toast.LENGTH_SHORT).show()
            return
        }
        if (otpInput.trim() != generatedOtp) {
            Toast.makeText(context, "Incorrect code", Toast.LENGTH_SHORT).show()
            return
        }
        isVerifying = true
        onSignup(name, username, email.trim().lowercase(), password, selectedAvatarId)
    }

    if (showEmailStep) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0F))
                    .padding(innerPadding)
                    .imePadding()
                    .padding(horizontal = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (otpSent) "Verify Your Email" else "One Last Step",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color(0xFFFF5A5A),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (!otpSent) {
                    Text(
                        text = "Enter your email - we'll send a 6-digit code to verify it's yours.",
                        color = Color(0xFF8A8F9E),
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )

                    Button(
                        onClick = { requestOtp() },
                        enabled = !isSendingOtp,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (isSendingOtp) "Sending code…" else "Send OTP")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { showEmailStep = false }) {
                        Text("Back", color = Color(0xFF8A8F9E))
                    }
                } else {
                    Text(
                        text = "Enter the 6-digit code sent to ${email.trim().lowercase()}",
                        color = Color(0xFF8A8F9E),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpInput = it },
                        label = { Text("6-digit code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )

                    Button(
                        onClick = { verifyOtp() },
                        enabled = !isVerifying && otpInput.length == 6,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (isVerifying) "Creating account…" else "Verify & Create Account")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { requestOtp() }, enabled = !isSendingOtp) {
                        Text(if (isSendingOtp) "Resending…" else "Resend code", color = Color(0xFF8A8F9E))
                    }
                    TextButton(onClick = { otpSent = false }) {
                        Text("Change email", color = Color(0xFF8A8F9E))
                    }
                }
            }
        }
        return
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(853f / 1843f)
                ) {
                    val w = maxWidth
                    val h = maxHeight
                    val iconClearance = 0.130f
                    val avatarSize = w * 0.1571f

                    // 1. Under-layer: 8 Marvel superhero avatars precisely positioned in the slots
                    val avatarSlots = listOf(
                        // Row 1 (cy = 536.7px / 1843)
                        Triple(MARVEL_AVATARS[0], 0.1132f, 0.2548f), // Iron Man
                        Triple(MARVEL_AVATARS[1], 0.3098f, 0.2549f), // Spider-Man
                        Triple(MARVEL_AVATARS[2], 0.5067f, 0.2549f), // Deadpool
                        Triple(MARVEL_AVATARS[3], 0.7036f, 0.2548f), // Wolverine
                        // Row 2 (cy = 756.4px / 1843)
                        Triple(MARVEL_AVATARS[4], 0.1132f, 0.3740f), // Hulk
                        Triple(MARVEL_AVATARS[5], 0.3098f, 0.3740f), // She-Hulk
                        Triple(MARVEL_AVATARS[6], 0.5066f, 0.3741f), // Groot
                        Triple(MARVEL_AVATARS[7], 0.7036f, 0.3740f)  // Wanda
                    )

                    avatarSlots.forEach { (avatar, xRatio, yRatio) ->
                        val isSelected = avatar.id == selectedAvatarId
                        Box(
                            modifier = Modifier
                                .offset(x = w * xRatio, y = h * yRatio)
                                .size(avatarSize)
                                .bouncyClick { selectedAvatarId = avatar.id },
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarCircle(
                                avatar = avatar,
                                size = avatarSize,
                                selected = isSelected
                            )
                        }
                    }

                    // 2. High-fidelity Sign Up frame overlay with transparent avatar cutouts and clean input boxes
                    Image(
                        painter = painterResource(R.drawable.sign_up),
                        contentDescription = "Sign Up Background Frame",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 3. Highlighted, non-blending bright glowing ring placed OVER the frame for the selected avatar
                    avatarSlots.forEach { (avatar, xRatio, yRatio) ->
                        val isSelected = avatar.id == selectedAvatarId
                        Box(
                            modifier = Modifier
                                .offset(x = w * xRatio, y = h * yRatio)
                                .size(avatarSize)
                                .bouncyClick { selectedAvatarId = avatar.id },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                BrightAvatarGlowRing(size = avatarSize)
                            }
                        }
                    }

                    // 4. Form inputs precisely aligned to sign_up.png input boxes
                    ReactorLoginField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Full Name",
                        showPlaceholder = true,
                        modifier = Modifier
                            .offset(x = w * 0.1700f, y = h * 0.5230f)
                            .size(w * 0.6741f, h * 0.0608f),
                        textPadding = w * 0.6741f * iconClearance
                    )

                    ReactorLoginField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "Username",
                        showPlaceholder = true,
                        modifier = Modifier
                            .offset(x = w * 0.1700f, y = h * 0.5968f)
                            .size(w * 0.6741f, h * 0.0624f),
                        textPadding = w * 0.6741f * iconClearance
                    )

                    ReactorLoginField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Password",
                        showPlaceholder = true,
                        isPassword = true,
                        modifier = Modifier
                            .offset(x = w * 0.1700f, y = h * 0.6728f)
                            .size(w * 0.6741f, h * 0.0586f),
                        textPadding = w * 0.6741f * iconClearance
                    )

                    ReactorLoginField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = "Confirm Password",
                        showPlaceholder = true,
                        isPassword = true,
                        modifier = Modifier
                            .offset(x = w * 0.1700f, y = h * 0.7461f)
                            .size(w * 0.6741f, h * 0.0591f),
                        textPadding = w * 0.6741f * iconClearance
                    )

                    // 5. Animated Create Account Button (pulsing animation matching Create Room button)
                    val btnTransition = rememberInfiniteTransition(label = "btnPulse")
                    val pulseScale by btnTransition.animateFloat(
                        initialValue = 0.98f,
                        targetValue = 1.04f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )
                    val pulseAlpha by btnTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )

                    Image(
                        painter = painterResource(R.drawable.btn_create_account),
                        contentDescription = "Create Account",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset(x = w * 0.1676f, y = h * 0.8265f)
                            .size(w * 0.6577f, h * 0.0813f)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }
                            .bouncyClick {
                                if (name.isBlank() || username.isBlank() || password.isBlank()) {
                                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                                    return@bouncyClick
                                }
                                if (password != confirmPassword) {
                                    Toast.makeText(context, "Passwords don't match", Toast.LENGTH_SHORT).show()
                                    return@bouncyClick
                                }
                                if (password.length < 6) {
                                    Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                    return@bouncyClick
                                }
                                showEmailStep = true
                            }
                    )

                    // 6. Already have an account? Log In Link
                    Box(
                        modifier = Modifier
                            .offset(x = w * 0.1747f, y = h * 0.9284f)
                            .size(w * 0.6506f, h * 0.0472f)
                            .bouncyClick(onClick = onNavigateToLogin)
                    )

                    // 7. Top-left back arrow button
                    Box(
                        modifier = Modifier
                            .offset(x = w * 0.045f, y = h * 0.038f)
                            .size(w * 0.13f, h * 0.055f)
                            .bouncyClick(onClick = onNavigateToLogin)
                    )
                }
            }
        }
    }
}