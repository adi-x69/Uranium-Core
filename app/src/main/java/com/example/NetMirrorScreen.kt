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

    var capturedLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedLink by remember { mutableStateOf<String?>(null) }

    val blockedDomains = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "pagead2.googlesyndication", "adservice.google", "adnxs.com",
        "amazon-adsystem.com", "scorecardresearch.com", "outbrain.com",
        "taboola.com", "criteo.com", "pubmatic.com", "openx.net",
        "rubiconproject.com", "moatads.com", "exoclick.com", "popads.net",
        "propellerads.com", "adsterra.com", "clickadu.com", "juicyads.com",
        "trafficjunky.com", "popcash.net", "adspyglass.com"
    )

    fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
                lower.contains(".mp4") ||
                lower.contains(".mkv") ||
                lower.contains(".ts") ||
                (lower.contains("video") && lower.contains("http") && !lower.contains("netmirror"))
    }

    fun addCapturedLink(url: String) {
        if (capturedLinks.any { it.equals(url, ignoreCase = true) }) return

        val newList = (capturedLinks + url)
            .distinctBy { it.lowercase() }
            .sortedWith(
                compareByDescending<String> { it.contains(".m3u8", ignoreCase = true) }
                    .thenByDescending { it.length }
            )

        capturedLinks = newList
        if (selectedLink == null) {
            selectedLink = newList.firstOrNull()
        }

        Toast.makeText(context, "Direct link captured!", Toast.LENGTH_SHORT).show()
    }

    // ================= HEAVY JAVASCRIPT INJECTION =================
    val heavyInjectionScript = """
        (function() {
            // ========== 1. Kill Extension Detection ==========
            window.chrome = window.chrome || {};
            window.chrome.runtime = window.chrome.runtime || {};
            window.chrome.runtime.id = "fake-extension-id";
            window.chrome.runtime.getManifest = function() { return { name: "NetMirror Extension" }; };
            window.chrome.runtime.sendMessage = function() {};
            window.chrome.runtime.connect = function() { return { onMessage: { addListener: function(){} } }; };

            // Fake extension presence
            Object.defineProperty(window, 'netmirrorExtension', {
                value: true,
                writable: false
            });

            // ========== 2. Remove "Extension Not Enable" messages ==========
            function removeExtensionWarnings() {
                const keywords = [
                    'extension not enable', 'extension not enabled',
                    'adblocker detected', 'ad blocker detected',
                    'disable your adblock', 'install extension',
                    'download with ext', 'extension required'
                ];

                document.querySelectorAll('div, span, p, h1, h2, h3, h4, button, a').forEach(el => {
                    const text = (el.innerText || el.textContent || '').toLowerCase();
                    if (keywords.some(k => text.includes(k))) {
                        el.style.display = 'none';
                        el.remove();
                    }
                });

                // Hide common warning containers
                document.querySelectorAll('[class*="extension"], [id*="extension"], [class*="adblock"], [id*="adblock"]').forEach(el => {
                    el.style.display = 'none';
                    el.remove();
                });
            }

            // ========== 3. Force unlock Download buttons ==========
            function unlockDownloadButtons() {
                document.querySelectorAll('button, a, div[role="button"]').forEach(btn => {
                    const text = (btn.innerText || '').toLowerCase();
                    if (text.includes('download') || text.includes('ext')) {
                        btn.style.pointerEvents = 'auto';
                        btn.style.opacity = '1';
                        btn.disabled = false;
                        btn.removeAttribute('disabled');
                        btn.classList.remove('disabled');
                    }
                });
            }

            // ========== 4. Extract video sources from player & DOM ==========
            function extractVideoSources() {
                const sources = new Set();

                // <video> and <source> tags
                document.querySelectorAll('video, source').forEach(el => {
                    if (el.src) sources.add(el.src);
                    if (el.currentSrc) sources.add(el.currentSrc);
                });

                // Common player variables
                if (window.player && window.player.src) sources.add(window.player.src);
                if (window.jwplayer) {
                    try {
                        const jw = jwplayer();
                        if (jw && jw.getPlaylist) {
                            jw.getPlaylist().forEach(item => {
                                if (item.file) sources.add(item.file);
                                if (item.sources) item.sources.forEach(s => sources.add(s.file));
                            });
                        }
                    } catch(e) {}
                }

                // HLS.js / video.js
                if (window.Hls && window.Hls.instances) {
                    window.Hls.instances.forEach(h => {
                        if (h.url) sources.add(h.url);
                    });
                }

                // Scan all scripts for m3u8/mp4
                document.querySelectorAll('script').forEach(script => {
                    const content = script.textContent || '';
                    const matches = content.match(/(https?:\/\/[^\s"'`]+\.(m3u8|mp4|mkv)[^\s"'`]*)/gi);
                    if (matches) matches.forEach(m => sources.add(m));
                });

                // Send found links to Android
                sources.forEach(url => {
                    if (url && (url.includes('.m3u8') || url.includes('.mp4') || url.includes('.mkv'))) {
                        window.AndroidBridge.onVideoLinkFound(url);
                    }
                });
            }

            // ========== 5. Continuous monitoring ==========
            removeExtensionWarnings();
            unlockDownloadButtons();
            extractVideoSources();

            // Run again after delays (site loads content dynamically)
            setTimeout(() => {
                removeExtensionWarnings();
                unlockDownloadButtons();
                extractVideoSources();
            }, 1000);

            setTimeout(() => {
                removeExtensionWarnings();
                unlockDownloadButtons();
                extractVideoSources();
            }, 3000);

            setTimeout(() => {
                removeExtensionWarnings();
                unlockDownloadButtons();
                extractVideoSources();
            }, 6000);

            // Observe DOM changes
            const observer = new MutationObserver(() => {
                removeExtensionWarnings();
                unlockDownloadButtons();
                extractVideoSources();
            });
            observer.observe(document.body, { childList: true, subtree: true });

            console.log('NetMirror heavy injection active');
        })();
    """.trimIndent()

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
        // ================= TOP BAR =================
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

                // Captured Link Action Bar
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

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onDirectLinkFound(selectedLink!!) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("USE LINK", fontWeight = FontWeight.Bold)
                                }

                                if (capturedLinks.size > 1) {
                                    Text(
                                        text = "${capturedLinks.size} links found",
                                        color = Color(0xFFB9F6CA),
                                        fontSize = 12.sp,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFF1744)
            )
        }

        // ================= WEBVIEW =================
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this

                    // Bridge so JavaScript can send links to Kotlin
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onVideoLinkFound(url: String) {
                            post {
                                addCapturedLink(url)
                            }
                        }
                    }, "AndroidBridge")

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        allowFileAccess = false
                        allowContentAccess = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // Important for some players
                        javaScriptCanOpenWindowsAutomatically = false
                    }

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

                            // Inject the heavy script
                            view?.evaluateJavascript(heavyInjectionScript, null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return true
                            val allowed = url.contains("netmirror", ignoreCase = true) ||
                                    url.contains(".m3u8") || url.contains(".mp4")
                            return !allowed
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val allowed = url?.contains("netmirror", ignoreCase = true) == true ||
                                    url?.contains(".m3u8") == true || url?.contains(".mp4") == true
                            return !allowed
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null

                            // Block ads
                            if (blockedDomains.any { domain ->
                                    url.contains(domain, ignoreCase = true)
                                }) {
                                return WebResourceResponse("text/plain", "utf-8", null)
                            }

                            // Capture video links from network
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