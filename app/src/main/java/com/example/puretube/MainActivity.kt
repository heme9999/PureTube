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
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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

    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            (context as? ComponentActivity)?.finish()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                coroutineScope.launch {
                    Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
                    val apkUrl = GitHubUpdater.checkForUpdates("heme9999", "PureTube", "v2.0.4")
                    if (apkUrl != null) {
                        downloadAndInstallApk(context, apkUrl)
                    } else {
                        Toast.makeText(context, "当前已是最新版本 v2.0.4", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "更新")
            }
        }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewRef = this
                    
                    // 开启所有必要的 Web 权限和设置以确保视频播放器正常加载
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadsImagesAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // 使用一个干净的移动端 Chrome UA，彻底避开 YouTube 对 WebView 的歧视
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                    }

                    // 开启 Cookie 支持，YouTube 严重依赖它来加载播放器组件
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("intent://") || url.startsWith("vnd.youtube") || url.startsWith("android-app://")) {
                                try {
                                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) { }
                                return true
                            }
                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            val js = """
                                // 1. 注入 CSS 修复布局并隐藏无关广告
                                var style = document.createElement('style');
                                style.innerHTML = `
                                    /* 强制播放器区域拥有正常高度，防止被压缩 */
                                    #player-control-container, #player-container-id, ytm-custom-control {
                                        min-height: 220px !important;
                                        display: block !important;
                                        visibility: visible !important;
                                    }
                                    
                                    /* 隐藏各类促销横幅 */
                                    ytm-promoted-video-renderer,
                                    ytm-companion-ad-renderer,
                                    ytm-app-promo-renderer,
                                    ytm-mealbar-promo-renderer,
                                    ytm-bottom-sheet-promo-renderer {
                                        display: none !important;
                                    }
                                `;
                                document.head.appendChild(style);

                                // 2. 轮询处理广告和干扰按钮
                                setInterval(function() {
                                    // 点击跳过视频广告
                                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                                    if (skipBtn) skipBtn.click();
                                    
                                    // 关闭叠加广告
                                    var closeBtn = document.querySelector('.ytp-ad-overlay-close-button');
                                    if (closeBtn) closeBtn.click();
                                    
                                    // 快进不可跳过的广告
                                    var adVid = document.querySelector('.ad-showing video, .html5-video-player.ad-showing video');
                                    if (adVid && !isNaN(adVid.duration)) {
                                        adVid.playbackRate = 16.0;
                                        adVid.currentTime = adVid.duration - 0.1;
                                    }
                                    
                                    // 隐藏顶部的 Open App 按钮
                                    var openAppBtns = document.querySelectorAll('a, button');
                                    openAppBtns.forEach(function(btn) {
                                        if (btn.textContent && (btn.textContent.trim().toLowerCase() === 'open app' || btn.textContent.trim() === '打开App')) {
                                            btn.style.display = 'none';
                                        }
                                    });
                                }, 300);
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url?.toString() ?: ""
                            val adHosts = listOf(
                                "googleads.g.doubleclick.net", 
                                "pagead2.googlesyndication.com", 
                                "pubads.g.doubleclick.net", 
                                "youtube.com/api/stats/ads",
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
