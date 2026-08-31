package com.example.puretube

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.puretube.updater.GitHubUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    YouTubeWebScreen()
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                coroutineScope.launch {
                    Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
                    val apkUrl = GitHubUpdater.checkForUpdates("heme9999", "PureTube", "v2.0.1")
                    if (apkUrl != null) {
                        downloadAndInstallApk(context, apkUrl)
                    } else {
                        Toast.makeText(context, "当前已是最新版本 v2.0.1", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "更新")
            }
        }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier.fillMaxSize(), // 全屏，不使用 paddingValues 限制顶部
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    // 开启自适应屏幕
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            // 1. 注入 CSS 永久隐藏广告占位符和促销横幅
                            val css = """
                                var style = document.createElement('style');
                                style.innerHTML = `
                                    ad-slot-renderer, 
                                    ytm-promoted-video-renderer, 
                                    ytm-companion-ad-renderer,
                                    ytm-unlimited-promo-renderer,
                                    ytm-mealbar-promo-renderer,
                                    .ad-showing { display: none !important; }
                                `;
                                document.head.appendChild(style);
                            """.trimIndent()
                            view?.evaluateJavascript(css, null)

                            // 2. 注入 JS 循环点击“跳过”并处理视频广告
                            val js = """
                                setInterval(function() {
                                    // 自动点击跳过按钮
                                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                                    if (skipBtn) { skipBtn.click(); }
                                    
                                    // 关闭叠加广告
                                    var closeBtn = document.querySelector('.ytp-ad-overlay-close-button');
                                    if (closeBtn) { closeBtn.click(); }
                                    
                                    // 如果有不可跳过的视频广告，快进到底
                                    var adVideo = document.querySelector('.ad-showing video, .html5-video-player.ad-showing video');
                                    if (adVideo && !isNaN(adVideo.duration)) {
                                        adVideo.playbackRate = 16.0;
                                        adVideo.currentTime = adVideo.duration - 0.1;
                                    }
                                }, 300);
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url?.toString() ?: ""
                            // 屏蔽常见广告联盟和 YouTube 广告追踪域名
                            val adHosts = listOf(
                                "googleads.g.doubleclick.net", 
                                "pagead2.googlesyndication.com", 
                                "pubads.g.doubleclick.net", 
                                "youtube.com/api/stats/ads",
                                "youtube.com/ptracking",
                                "doubleclick.net"
                            )
                            if (adHosts.any { url.contains(it) }) {
                                return WebResourceResponse("text/plain", "UTF-8", null)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    webChromeClient = WebChromeClient()
                    loadUrl("https://m.youtube.com")
                }
            }
        )
    }
}

fun downloadAndInstallApk(context: Context, url: String) {
    // 保持原样
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("PureTube 更新")
            .setDescription("正在下载新版本...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PureTube_update.apk")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        Toast.makeText(context, "开始后台下载更新，完成后将自动提示安装", Toast.LENGTH_LONG).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    try {
                        ctx.startActivity(installIntent)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "打开安装包失败，请到下载目录手动安装", Toast.LENGTH_LONG).show()
                    }
                    try { ctx.unregisterReceiver(this) } catch (e: Exception) {}
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "启动下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
