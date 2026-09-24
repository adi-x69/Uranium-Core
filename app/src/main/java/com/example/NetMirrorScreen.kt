package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NetMirrorScreen(
    onDirectLinkFound: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var foundLink by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }

    // Comprehensive list of ad / tracking / redirect domains
    val blockedDomains = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google", "pagead2.googlesyndication", "ads.", "adnxs.com",
        "facebook.net", "scorecardresearch.com", "outbrain.com", "taboola.com",
        "criteo.com", "pubmatic.com", "openx.net", "rubiconproject.com",
        "moatads.com", "amazon-adsystem.com", "adsafeprotected.com",
        "googletagmanager.com", "googletagservices.com", "google-analytics.com",
        "hotjar.com", "clarity.ms", "mouseflow.com", "quantserve.com",
        "exoclick.com", "popads.net", "propellerads.com", "adsterra.com",
        "clickadu.com", "juicyads.com", "trafficjunky.com", "adcash.com",
        "adcolony.com", "unityads", "ironsource", "applovin", "vungle",
        "startapp", "chartboost", "fyber", "smaato", "inmobi"
    )

    // Handle system back button
    BackHandler {
        if (canGoBack && webView != null) {
            webView?.goBack()
        } else {
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E18))
    ) {

        // ====================== TOP BAR ======================
        Surface(
            color = Color(0xFF12151F),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (canGoBack && webView != null) webView?.goBack()
                        else onNavigateBack()
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "NetMirror",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Refresh button
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }

                    // Close button
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Direct Link Action Bar (appears only when a link is found)
                if (foundLink != null) {
                    Surface(
                        color = Color(0xFF1B5E20),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Direct Link Captured!",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = foundLink!!.take(60) + if (foundLink!!.length > 60) "..." else "",
                                    color = Color(0xFFB9F6CA),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = { onDirectLinkFound(foundLink!!) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676)
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("USE LINK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Progress Indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFF1744)
            )
        }

        // ====================== WEBVIEW ======================
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        allowFileAccess = false
                        allowContentAccess = false
                    }

                    // ========== Chrome Client (Block Popups) ==========
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            // Completely block all popup windows
                            return false
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            isLoading = newProgress < 100
                        }
                    }

                    // ========== WebView Client ==========
                    webViewClient = object : WebViewClient() {

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = view?.canGoBack() == true

                            // Inject JavaScript to hide common ad elements
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    var selectors = [
                                        'iframe[src*="ads"]', 'iframe[src*="doubleclick"]',
                                        'iframe[src*="googlesyndication"]', 'div[id*="ad"]',
                                        'div[class*="ad-"]', 'div[class*="ads"]',
                                        'div[class*="banner"]', 'div[class*="popup"]',
                                        '.adsbygoogle', '[id*="google_ads"]',
                                        '[class*="sponsored"]'
                                    ];
                                    selectors.forEach(function(sel) {
                                        document.querySelectorAll(sel).forEach(function(el) {
                                            el.style.display = 'none';
                                            el.remove();
                                        });
                                    });
                                })();
                                """.trimIndent(),
                                null
                            )
                        }

                        // Block navigation to other domains
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return true

                            val allowed = url.contains("netmirror.studio", ignoreCase = true) ||
                                    url.contains("netmirror", ignoreCase = true)

                            return if (allowed) {
                                false // Allow NetMirror
                            } else {
                                // Block external redirects
                                true
                            }
                        }

                        // Old API support
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val allowed = url?.contains("netmirror.studio", ignoreCase = true) == true ||
                                    url?.contains("netmirror", ignoreCase = true) == true
                            return !allowed
                        }

                        // Intercept requests: block ads + capture video links
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null

                            // 1. Block ad domains
                            if (blockedDomains.any { domain -> url.contains(domain, ignoreCase = true) }) {
                                return WebResourceResponse("text/plain", "utf-8", null)
                            }

                            // 2. Capture direct video links
                            val isVideoLink = url.contains(".m3u8", ignoreCase = true) ||
                                    url.contains(".mp4", ignoreCase = true) ||
                                    (url.contains("video", ignoreCase = true) &&
                                            (url.contains("http://") || url.contains("https://")) &&
                                            !url.contains("netmirror.studio"))

                            if (isVideoLink) {
                                view?.post {
                                    // Prefer m3u8 over mp4
                                    if (foundLink == null ||
                                        (url.contains(".m3u8", ignoreCase = true) &&
                                                foundLink?.contains(".m3u8") != true)
                                    ) {
                                        foundLink = url
                                        Toast.makeText(
                                            context,
                                            "Direct video link captured!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }

                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    loadUrl("https://netmirror.studio/")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}