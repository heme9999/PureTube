package com.example.puretube

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class NativePlayerActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUrl = intent.getStringExtra("video_url") ?: ""
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val coroutineScope = rememberCoroutineScope()
                    var streamUrl by remember { mutableStateOf<String?>(null) }
                    
                    LaunchedEffect(videoUrl) {
                        if (videoUrl.isNotEmpty()) {
                            coroutineScope.launch {
                                try {
                                    val extractor = withContext(Dispatchers.IO) {
                                        ServiceList.YouTube.getStreamExtractor(videoUrl).apply {
                                            fetchPage()
                                        }
                                    }
                                    val videoStreams = extractor.videoStreams
                                    if (videoStreams.isNotEmpty()) {
                                        // 简单取第一个视频流
                                        streamUrl = videoStreams[0].content
                                    } else {
                                        Toast.makeText(this@NativePlayerActivity, "无法获取视频流", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this@NativePlayerActivity, "解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    
                    if (streamUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    exoPlayer = ExoPlayer.Builder(ctx).build().also { player ->
                                        this.player = player
                                        val mediaItem = MediaItem.fromUri(streamUrl!!)
                                        player.setMediaItem(mediaItem)
                                        player.prepare()
                                        player.playWhenReady = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
