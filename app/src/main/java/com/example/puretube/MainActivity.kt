package com.example.puretube

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.puretube.api.PipedApiService
import com.example.puretube.api.TrendingResponse
import com.example.puretube.ui.VideoPlayerScreen
import com.example.puretube.updater.GitHubUpdater
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureTubeApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var videos by remember { mutableStateOf<List<TrendingResponse>>(emptyList()) }
    var selectedVideoUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var apiUrl by remember { mutableStateOf("https://pipedapi.tokhmi.xyz/") }
    var showSettings by remember { mutableStateOf(false) }

    fun loadVideos() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val retrofit = Retrofit.Builder()
                    .baseUrl(apiUrl)
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                val api = retrofit.create(PipedApiService::class.java)
                videos = api.getTrending()
            } catch (e: Exception) {
                errorMessage = e.message ?: e.toString()
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadVideos()
    }

    if (selectedVideoUrl != null) {
        // Simple back button over the player
        Box(modifier = Modifier.fillMaxSize()) {
            VideoPlayerScreen(videoUrl = selectedVideoUrl!!)
            Button(
                onClick = { selectedVideoUrl = null },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("返回列表")
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PureTube") },
                    actions = {
                        IconButton(onClick = { loadVideos() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { showSettings = !showSettings }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                        Button(onClick = {
                            coroutineScope.launch {
                                Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
                                val apkUrl = GitHubUpdater.checkForUpdates("YOUR_GITHUB_USERNAME", "PureTube", "v1.0.0")
                                if (apkUrl != null) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "当前已是最新版本，或未发布 Release", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Text("在线更新")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                if (showSettings) {
                    Card(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("API 节点设置 (如果网络受限请更换)", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = apiUrl,
                                onValueChange = { apiUrl = it },
                                label = { Text("Piped API URL") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            Row(modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { apiUrl = "https://pipedapi.tokhmi.xyz/" }) { Text("节点 1") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { apiUrl = "https://pipedapi.smnz.de/" }) { Text("节点 2") }
                            }
                            Button(onClick = { loadVideos(); showSettings = false }, modifier = Modifier.padding(top = 8.dp)) {
                                Text("保存并重试")
                            }
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Column {
                            Text("网络错误或API受限 (可能需将 API 域名加入梯子代理规则):", color = MaterialTheme.colorScheme.error)
                            Text(errorMessage ?: "", modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                } else if (videos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("没有获取到视频数据")
                    }
                } else {
                    LazyColumn {
                        items(videos) { video ->
                            VideoItem(video) {
                                coroutineScope.launch {
                                    val videoId = video.url.substringAfter("?v=")
                                    try {
                                        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                                        val retrofit = Retrofit.Builder()
                                            .baseUrl(apiUrl)
                                            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                                            .build()
                                        val api = retrofit.create(PipedApiService::class.java)
                                        val stream = api.getVideoStream(videoId)
                                        if (stream.hls != null) {
                                            selectedVideoUrl = stream.hls
                                        } else {
                                            Toast.makeText(context, "无法获取视频流", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "播放请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
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
