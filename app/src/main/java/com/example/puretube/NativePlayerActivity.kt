package com.example.puretube

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
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

class NativePlayerActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null

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
                                        streamUrl = videoStreams[0].content
                                        // Update PiP Params for Android 12+ (Seamless PiP)
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
                    
                    if (streamUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    playerView = this
                                    
                                    // Disable settings by not using TrackSelector
                                    exoPlayer = ExoPlayer.Builder(ctx).build().also { player ->
                                        this.player = player
                                        val mediaItem = MediaItem.fromUri(streamUrl!!)
                                        player.setMediaItem(mediaItem)
                                        player.prepare()
                                        player.playWhenReady = true
                                    }

                                    // Attempt to hide settings button completely
                                    try {
                                        val settingsBtn = this.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                                        settingsBtn?.visibility = View.GONE
                                        settingsBtn?.isEnabled = false
                                        settingsBtn?.layoutParams = android.widget.FrameLayout.LayoutParams(0, 0)
                                    } catch (e: Exception) {}
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    private fun triggerPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        triggerPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView?.useController = !isInPictureInPictureMode
    }

    override fun onPause() {
        super.onPause()
        // If not finishing, it means we are pushed to background (e.g. recent apps or home gesture)
        // If PiP hasn't been triggered yet (autoEnterEnabled might fail on some skins), force it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode && !isFinishing) {
            triggerPip()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
