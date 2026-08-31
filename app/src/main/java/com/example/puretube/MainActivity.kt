package com.example.puretube

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.puretube.updater.GitHubUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var myWebView: WebView? = null
    private var isMenuVisible by mutableStateOf(true)

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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isMenuVisible = !isInPictureInPictureMode
        val js = if (isInPictureInPictureMode) "document.body.classList.add('pip-mode');" else "document.body.classList.remove('pip-mode');"
        myWebView?.evaluateJavascript(js, null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun YouTubeWebScreen() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        BackHandler {
            if (myWebView?.canGoBack() == true) {
                myWebView?.goBack()
            } else {
                finish()
            }
        }

        Scaffold(
            floatingActionButton = {
                if (isMenuVisible) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        FloatingActionButton(
                            onClick = { expanded = true },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("检查更新 (v2.3.0)") },
                                onClick = {
                                    expanded = false
                                    coroutineScope.launch {
                                        Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
                                        val apkUrl = GitHubUpdater.checkForUpdates("heme9999", "PureTube", "v2.3.0")
                                        if (apkUrl != null) {
                                            downloadAndInstallApk(context, apkUrl)
                                        } else {
                                            Toast.makeText(context, "当前已是最新版本 v2.3.0", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        myWebView = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                
                                // 拦截视频点击，启动原生 ExoPlayer
                                if (url.contains("youtube.com/watch?v=") || url.contains("youtu.be/")) {
                                    val intent = Intent(context, NativePlayerActivity::class.java).apply {
                                        putExtra("video_url", url)
                                    }
                                    context.startActivity(intent)
                                    return true
                                }
                                
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
                                    var style = document.createElement('style');
                                    style.innerHTML = `
                                        #player-control-container, #player-container-id, ytm-custom-control {
                                            min-height: 220px !important;
                                            display: block !important;
                                            visibility: visible !important;
                                        }
                                        ytm-promoted-video-renderer, ytm-companion-ad-renderer,
                                        ytm-app-promo-renderer, ytm-mealbar-promo-renderer,
                                        ytm-bottom-sheet-promo-renderer { display: none !important; }
                                        
                                        body.pip-mode ytm-header-bar, body.pip-mode ytm-item-section-renderer,
                                        body.pip-mode .related-items-container, body.pip-mode ytm-pivot-bar-renderer,
                                        body.pip-mode ytm-single-column-watch-next-results-renderer > *:not(#player-control-container):not(ytm-custom-control) {
                                            display: none !important;
                                        }
                                        body.pip-mode #player-control-container, body.pip-mode ytm-custom-control {
                                            height: 100vh !important; width: 100vw !important;
                                            position: fixed !important; top: 0 !important; left: 0 !important;
                                            z-index: 999999 !important; background: black !important;
                                        }
                                        body.pip-mode .ytp-chrome-bottom, body.pip-mode .ytp-chrome-top,
                                        body.pip-mode .ytp-gradient-bottom, body.pip-mode .ytp-gradient-top {
                                            display: none !important;
                                        }
                                    `;
                                    document.head.appendChild(style);

                                    setInterval(function() {
                                        var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                                        if (skipBtn) skipBtn.click();
                                        var closeBtn = document.querySelector('.ytp-ad-overlay-close-button');
                                        if (closeBtn) closeBtn.click();
                                        var adVid = document.querySelector('.ad-showing video, .html5-video-player.ad-showing video');
                                        if (adVid && !isNaN(adVid.duration)) {
                                            adVid.playbackRate = 16.0;
                                            adVid.currentTime = adVid.duration - 0.1;
                                        }
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
                                    "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", 
                                    "pubads.g.doubleclick.net", "youtube.com/api/stats/ads", "doubleclick.net"
                                )
                                if (adHosts.any { url.contains(it) }) {
                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        
                        webChromeClient = object : WebChromeClient() {
                            private var customView: View? = null
                            private var customViewCallback: CustomViewCallback? = null

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                customView = view
                                customViewCallback = callback
                                
                                val decorView = window.decorView as FrameLayout
                                view?.setBackgroundColor(Color.BLACK)
                                decorView.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

                                isMenuVisible = false
                                WindowCompat.setDecorFitsSystemWindows(window, false)
                                WindowInsetsControllerCompat(window, decorView).let { controller ->
                                    controller.hide(WindowInsetsCompat.Type.systemBars())
                                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                }
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                this@apply.visibility = View.GONE
                            }

                            override fun onHideCustomView() {
                                super.onHideCustomView()
                                val decorView = window.decorView as FrameLayout
                                decorView.removeView(customView)
                                customView = null
                                customViewCallback?.onCustomViewHidden()

                                isMenuVisible = true
                                WindowCompat.setDecorFitsSystemWindows(window, true)
                                WindowInsetsControllerCompat(window, decorView).show(WindowInsetsCompat.Type.systemBars())
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                this@apply.visibility = View.VISIBLE
                            }
                        }
                        loadUrl("https://m.youtube.com")
                    }
                }
            )
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "启动下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
