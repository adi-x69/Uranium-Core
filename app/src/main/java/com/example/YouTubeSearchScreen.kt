package com.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search YouTube") },
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search YouTube") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
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
                                        errorMessage = e.message ?: "Failed to load results"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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

@Composable
fun YouTubeResultItem(item: YouTubeSearchItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AsyncImage(
                model = item.snippet.thumbnails.medium.url,
                contentDescription = "Thumbnail",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.snippet.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.snippet.channelTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
