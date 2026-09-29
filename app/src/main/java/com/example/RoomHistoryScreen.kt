package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.MistText
import com.example.ui.theme.MistTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomHistoryScreen(
    onBack: () -> Unit = {},
    onNavigateToRoom: (String) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room History", color = MistText, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MistText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidBlack)
            )
        },
        containerColor = VoidBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No recent rooms in history",
                color = MistTextMuted,
                fontSize = 15.sp
            )
        }
    }
}
