package com.example.puretube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.puretube.api.PipedApiClient
import com.example.puretube.api.TrendingResponse
import com.example.puretube.ui.VideoPlayerScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PureTubeApp()
                }
            }
        }
    }
}

@Composable
fun PureTubeApp() {
    val coroutineScope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<TrendingResponse>>(emptyList()) }
    var selectedVideoUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            videos = PipedApiClient.api.getTrending()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    if (selectedVideoUrl != null) {
        VideoPlayerScreen(videoUrl = selectedVideoUrl!!)
    } else {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn {
                items(videos) { video ->
                    VideoItem(video) { 
                        coroutineScope.launch {
                            val videoId = video.url.substringAfter("?v=")
                            try {
                                val stream = PipedApiClient.api.getVideoStream(videoId)
                                if (stream.hls != null) {
                                    selectedVideoUrl = stream.hls
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoItem(video: TrendingResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Text(text = video.title, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium)
            Text(text = video.uploaderName, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
