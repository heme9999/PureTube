package com.example.puretube

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

class NativePlayerActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var isInPipModeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUrl = intent.getStringExtra("video_url") ?: ""
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val coroutineScope = rememberCoroutineScope()
                    var streamUrl by remember { mutableStateOf<String?>(null) }
                    val isPip = isInPipModeState.value
                    
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
                                        streamUrl = videoStreams[0].content
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            try {
                                                val params = PictureInPictureParams.Builder()
                                                    .setAspectRatio(Rational(16, 9))
                                                    .setAutoEnterEnabled(true)
                                                    .build()
                                                setPictureInPictureParams(params)
                                            } catch (e: Exception) {}
                                        }
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
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (streamUrl != null) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        playerView = this
                                        
                                        exoPlayer = ExoPlayer.Builder(ctx).build().also { player ->
                                            this.player = player
                                            val mediaItem = MediaItem.fromUri(streamUrl!!)
                                            player.setMediaItem(mediaItem)
                                            player.prepare()
                                            player.playWhenReady = true
                                        }

                                        // Completely destroy the settings button from the view hierarchy
                                        try {
                                            val settingsBtn = this.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                                            if (settingsBtn != null && settingsBtn.parent != null) {
                                                (settingsBtn.parent as ViewGroup).removeView(settingsBtn)
                                            }
                                        } catch (e: Exception) {}
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                        if (!isPip) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { finish() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Text("返回", color = Color.White)
                                }
                                Button(
                                    onClick = { enterPipManually() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Text("开启小窗", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enterPipManually() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                val success = enterPictureInPictureMode(params)
                if (!success) {
                    Toast.makeText(this, "小窗失败，请检查手机【设置-应用-画中画】权限是否开启！", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "小窗失败: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "系统版本过低，不支持画中画", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
        playerView?.useController = !isInPictureInPictureMode
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
