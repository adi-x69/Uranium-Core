package com.example

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var canGoBack by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }

    // Store captured links (prefer m3u8)
    var capturedLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedLink by remember { mutableStateOf<String?>(null) }

    // Comprehensive ad / tracking / redirect domains
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
        "startapp", "chartboost", "fyber", "smaato", "inmobi",
        "popcash.net", "adspyglass", "hilltopads", "clickaine", "adright"
    )

    fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
                lower.contains(".mp4") ||
                lower.contains(".mkv") ||
                (lower.contains("video") && (lower.startsWith("http://") || lower.startsWith("https://")) &&
                        !lower.contains("netmirror"))
    }

    fun addCapturedLink(url: String) {
        if (capturedLinks.contains(url)) return

        val newList = (capturedLinks + url).distinct()
            .sortedWith(compareByDescending<String> { it.contains(".m3u8", ignoreCase = true) }
                .thenByDescending { it.length }) // longer usually = higher quality

        capturedLinks = newList
        if (selectedLink == null) {
            selectedLink = newList.firstOrNull()
        }

        Toast.makeText(context, "Direct link captured!", Toast.LENGTH_SHORT).show()
    }

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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Text(
                        text = "NetMirror",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }

                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Direct Link Action Bar
                if (selectedLink != null) {
                    Surface(
                        color = Color(0xFF1B5E20),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Direct Link Captured!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = selectedLink!!,
                                color = Color(0xFFB9F6CA),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onDirectLinkFound(selectedLink!!) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("USE LINK", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("video_link", selectedLink))
                                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(Color.White)
                                    )
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy")
                                }

                                if (capturedLinks.size > 1) {
                                    Text(
                                        text = "${capturedLinks.size} links found",
                                        color = Color(0xFFB9F6CA),
                                        fontSize = 12.sp
                                    )
                                }
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
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    // Block all popups
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean = false

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            isLoading = newProgress < 100
                        }
                    }

                    webViewClient = object : WebViewClient() {

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = view?.canGoBack() == true

                            // Aggressive ad hiding + try to surface player sources
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    // Hide common ad containers
                                    var selectors = [
                                        'iframe[src*="ads"]', 'iframe[src*="doubleclick"]',
                                        'iframe[src*="googlesyndication"]', 'div[id*="ad"]',
                                        'div[class*="ad-"]', 'div[class*="ads"]',
                                        'div[class*="banner"]', 'div[class*="popup"]',
                                        '.adsbygoogle', '[id*="google_ads"]',
                                        '[class*="sponsored"]', '[class*="advert"]',
                                        'div[id*="popup"]', 'div[class*="overlay"]'
                                    ];
                                    selectors.forEach(function(sel) {
                                        document.querySelectorAll(sel).forEach(function(el) {
                                            el.style.display = 'none';
                                            el.remove();
                                        });
                                    });

                                    // Try to expose video sources if the player has them
                                    try {
                                        var videos = document.querySelectorAll('video');
                                        videos.forEach(function(v) {
                                            if (v.src) console.log('VIDEO_SRC:' + v.src);
                                            if (v.currentSrc) console.log('VIDEO_SRC:' + v.currentSrc);
                                        });
                                    } catch(e) {}
                                })();
                                """.trimIndent(),
                                null
                            )
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return true
                            val allowed = url.contains("netmirror.studio", ignoreCase = true) ||
                                    url.contains("netmirror", ignoreCase = true)
                            return !allowed
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val allowed = url?.contains("netmirror.studio", ignoreCase = true) == true ||
                                    url?.contains("netmirror", ignoreCase = true) == true
                            return !allowed
                        }

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
                            if (isVideoUrl(url)) {
                                view?.post {
                                    addCapturedLink(url)
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