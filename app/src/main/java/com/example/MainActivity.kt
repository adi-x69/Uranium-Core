package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo fallback
            Text(
                text = "Uranium TV",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Username Field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                singleLine = true
            )

            // Login Button
            Button(
                onClick = { onLogin(username, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Log In")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Create Account Link
            TextButton(onClick = onNavigateToSignup) {
                Text("Create an account")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Instagram Link fallback
            TextButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/uraniumm_235"))
                    context.startActivity(intent)
                }
            ) {
                Text("Follow on Instagram")
            }
        }
    }
}

@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    onSignup: (String, String, String, String) -> Unit
) {
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
                    if (password == confirmPassword) {
                        onSignup(name, username, password, selectedAvatarId)
                    }
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
