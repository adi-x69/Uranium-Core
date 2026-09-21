package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.bouncyClick
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Retrofit interface for YouTube Data API v3
interface YouTubeApiService {
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("maxResults") maxResults: Int = 25
    ): YouTubeSearchResponse
}

data class YouTubeSearchResponse(val items: List<YouTubeSearchItem>?)
data class YouTubeSearchItem(val id: YouTubeVideoId, val snippet: YouTubeSnippet)
data class YouTubeVideoId(val videoId: String)
data class YouTubeSnippet(
    val title: String,
    val channelTitle: String,
    val thumbnails: YouTubeThumbnails
)
data class YouTubeThumbnails(val medium: YouTubeThumbnailUrl)
data class YouTubeThumbnailUrl(val url: String)

@Composable
fun YouTubeSearchScreen(
    roomCode: String,
    onNavigateBack: () -> Unit,
    onVideoSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeSearchItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val api = remember {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YouTubeApiService::class.java)
    }

    fun performSearch() {
        if (query.isNotBlank()) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    val response = api.searchVideos(
                        query = query,
                        apiKey = BuildConfig.YOUTUBE_API_KEY
                    )
                    results = response.items ?: emptyList()
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to scan database"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    FuturisticCyberBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                FuturisticTopBar(
                    title = "ARCHIVE SEARCH",
                    subtitle = "BROADCAST TARGET: $roomCode",
                    onNavigateBack = onNavigateBack,
                    statusText = "DATABASE READY",
                    statusColor = NeonCyberCyan
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Search Input & Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FuturisticTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search YouTube archive...",
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = NeonCyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(NeonCrimson, NeonHazardAmber)))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .bouncyClick { performSearch() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SCAN",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = NeonCyberCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "DECRYPTING ARCHIVE INDEX...",
                                color = NeonCyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FuturisticGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColors = listOf(NeonCrimson, NeonDangerRed)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "TRANSMISSION ERROR",
                                    color = NeonCrimson,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMessage ?: "Unknown error",
                                    color = Color(0xFFC0C7D6),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(results) { item ->
                            YouTubeResultItem(
                                item = item,
                                onClick = { onVideoSelected(item.id.videoId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeResultItem(item: YouTubeSearchItem, onClick: () -> Unit) {
    FuturisticGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        borderColors = listOf(NeonCyberCyan.copy(alpha = 0.5f), NeonCrimson.copy(alpha = 0.5f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                AsyncImage(
                    model = item.snippet.thumbnails.medium.url,
                    contentDescription = "Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Holographic play badge overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .border(1.5.dp, NeonToxicGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Transmit",
                        tint = NeonToxicGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Cyber watermark badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "READY TO BEAM",
                        color = NeonCyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = item.snippet.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(NeonHazardAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.snippet.channelTitle,
                        color = Color(0xFF909BB0),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
