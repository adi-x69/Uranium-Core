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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                onSignup = { username, password, avatarId ->
                    if (username.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please enter username and password", Toast.LENGTH_SHORT).show()
                        return@SignupScreen
                    }
                    if (password.length < 6) {
                        Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        return@SignupScreen
                    }
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
                onLogout = {
                    auth.signOut()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("friends") {
            FriendsScreen(
                onNavigateBack = { navController.popBackStack() },
                onInviteToWatch = { friend ->
                    // Phase 4 will wire this up to actually create a watch invite.
                    Toast.makeText(context, "Invite feature coming in the next phase", Toast.LENGTH_SHORT).show()
                }
            )
        }
        composable("room/{roomCode}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            val selectedVideoId = backStackEntry.savedStateHandle.get<String>("selectedVideoId")
            
            RoomScreen(
                roomCode = roomCode,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSearch = { navController.navigate("search/$roomCode") },
                selectedVideoIdFromSearch = selectedVideoId,
                onVideoIdConsumed = { backStackEntry.savedStateHandle.remove<String>("selectedVideoId") }
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
    onSignup: (String, String, String) -> Unit
) {
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
                        onSignup(username, password, selectedAvatarId)
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
