package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                UraniumTvApp()
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

    NavHost(navController = navController, startDestination = startDestination) {
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
                onSignup = { name, username, password, avatarId ->
                    if (name.isBlank() || username.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please enter your name, username and password", Toast.LENGTH_SHORT).show()
                        return@SignupScreen
                    }
                    if (password.length < 6) {
                        Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        return@SignupScreen
                    }
                    val cleanName = name.trim()
                    val cleanUsername = username.trim().lowercase()
                    val email = "$cleanUsername@uraniumtv.local"
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid ?: ""
                                val db = com.google.firebase.database.FirebaseDatabase
                                    .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
                                    .reference
                                val profileUpdates = mapOf(
                                    "users/$uid/name" to cleanName,
                                    "users/$uid/username" to cleanUsername,
                                    "users/$uid/avatarId" to avatarId,
                                    "users/$uid/password" to password,
                                    "usernames/$cleanUsername" to uid
                                )
                                db.updateChildren(profileUpdates)
                                    .addOnCompleteListener {
                                        Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                        navController.navigate("home") {
                                            popUpTo("signup") { inclusive = true }
                                            popUpTo("login") { inclusive = true }
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
            // Must be observed reactively (getStateFlow + collectAsState), not read once with
            // .get(): RoomScreen stays alive on the back stack while the search screen is open,
            // so a plain one-time .get() here never notices the value change written after
            // popping back from search - the video would silently never start playing.
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(834f / 1885f)
            ) {
                val w = maxWidth
                val h = maxHeight
                // Fraction of each field row's own width taken up by its baked-in icon,
                // so typed text starts clear of the icon instead of on top of it.
                val iconClearance = 0.184f

                Image(
                    painter = painterResource(R.drawable.login_reactor_bg),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                ReactorLoginField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Username",
                    modifier = Modifier
                        .offset(x = w * 0.2038f, y = h * 0.4987f)
                        .size(w * 0.5876f, h * 0.0530f),
                    textPadding = w * 0.5876f * iconClearance
                )

                ReactorLoginField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
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
                        .clickable(onClick = { onLogin(username, password) })
                )

                Box(
                    modifier = Modifier
                        .offset(x = w * 0.2698f, y = h * 0.7401f)
                        .size(w * 0.4556f, h * 0.0451f)
                        .clickable(onClick = onNavigateToSignup)
                )

                Box(
                    modifier = Modifier
                        .offset(x = w * 0.4196f, y = h * 0.7958f)
                        .size(w * 0.1619f, h * 0.0637f)
                        .clickable(onClick = {
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

/**
 * A transparent text field meant to sit directly over a field row baked into the
 * reactor background art (icon + box already drawn). [textPadding] pushes the text
 * start past the baked-in icon; the placeholder shows only while [value] is empty,
 * matching the look of the label already painted into the artwork.
 */
@Composable
private fun ReactorLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    textPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFE7E9F0), fontSize = 16.sp),
            cursorBrush = SolidColor(Color(0xFFFF5A5A)),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = textPadding),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color(0xFF8A8F9E), fontSize = 16.sp)
                    }
                    innerTextField()
                }
            }
        )
    }
}


@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    onSignup: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedAvatarId by remember { mutableStateOf(PRESET_AVATARS[0].id) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Join Uranium TV",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            AvatarCircle(avatar = avatarById(selectedAvatarId), size = 72.dp)

            Spacer(modifier = Modifier.height(20.dp))

            AvatarPickerGrid(
                selectedId = selectedAvatarId,
                onSelect = { selectedAvatarId = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    if (password != confirmPassword) {
                        Toast.makeText(context, "Passwords don't match", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSignup(name, username, password, selectedAvatarId)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Create Account")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNavigateToLogin) {
                Text("Already have an account? Log In")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
