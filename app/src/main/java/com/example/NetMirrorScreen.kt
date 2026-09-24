package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

private const val NETMIRROR_HOME = "https://netmirror.studio/"

private val BLOCKED_DOMAINS = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "pagead2.googlesyndication", "adservice.google", "adnxs.com",
    "amazon-adsystem.com", "scorecardresearch.com", "outbrain.com",
    "taboola.com", "criteo.com", "pubmatic.com", "openx.net",
    "rubiconproject.com", "moatads.com", "exoclick.com", "popads.net",
    "propellerads.com", "adsterra.com", "clickadu.com", "juicyads.com",
    "trafficjunky.com", "popcash.net", "adspyglass.com"
)

// Real video/playlist files only. ".ts" segments and generic "video" URLs are NOT matched.
private val VIDEO_URL_REGEX = Regex("""\.(m3u8|mp4|mkv|mpd)(\?|#|$)""", RegexOption.IGNORE_CASE)

private fun isVideoUrl(url: String): Boolean =
    url.startsWith("http", ignoreCase = true) && VIDEO_URL_REGEX.containsMatchIn(url)

/**
 * Port of the extension's content.js: when the page sends NETMIRROR_CHECK,
 * answer that the extension is installed. Injected at document start.
 */
private const val DETECT_SCRIPT = """
(function () {
  if (window.__nmDetect) return;
  window.__nmDetect = true;
  window.addEventListener('message', function (e) {
    if (e.source !== window) return;
    if (e.data && e.data.type === 'NETMIRROR_CHECK') {
      window.postMessage({ type: 'NETMIRROR_EXTENSION_DETECTED', installed: true }, '*');
    }
  });
})();
"""

// Page helper: hides "extension not enabled" warnings, unlocks download buttons,
// and reports video links found in the page. Throttled so it does not slow the site.
private const val PAGE_SCRIPT = """
(function () {
  if (window.__nmPage) return;
  window.__nmPage = true;
  var sent = {};
  var VIDEO_RE = /\.(m3u8|mp4|mkv|mpd)(\?|#|$)/i;

  function send(url) {
    if (!url || sent[url] || !VIDEO_RE.test(url)) return;
    sent[url] = 1;
    try { window.AndroidBridge.onVideoLinkFound(url); } catch (e) {}
  }

  function hideWarnings() {
    var keywords = ['extension not enable', 'extension not enabled', 'adblocker detected',
      'ad blocker detected', 'disable your adblock', 'extension required'];
    document.querySelectorAll('div, span, p, h1, h2, h3, h4, button, a').forEach(function (el) {
      var t = (el.innerText || '').toLowerCase();
      if (t.length > 0 && t.length < 80 && keywords.some(function (k) { return t.indexOf(k) >= 0; })) {
        el.style.display = 'none';
      }
    });
  }

  function unlockDownloadButtons() {
    document.querySelectorAll('button, a, div[role="button"]').forEach(function (btn) {
      var t = (btn.innerText || '').toLowerCase();
      if (t.indexOf('download') >= 0) {
        btn.style.pointerEvents = 'auto';
        btn.style.opacity = '1';
        btn.disabled = false;
        btn.removeAttribute('disabled');
        btn.classList.remove('disabled');
      }
    });
  }

  function extractVideoSources() {
    document.querySelectorAll('video, source').forEach(function (el) {
      send(el.src);
      send(el.currentSrc);
    });
    try {
      if (window.jwplayer) {
        var jw = window.jwplayer();
        if (jw && jw.getPlaylist) {
          jw.getPlaylist().forEach(function (item) {
            send(item.file);
            if (item.sources) item.sources.forEach(function (s) { send(s.file); });
          });
        }
      }
    } catch (e) {}
    document.querySelectorAll('script').forEach(function (script) {
      var content = script.textContent || '';
      var matches = content.match(/(https?:\/\/[^\s"'`]+\.(m3u8|mp4|mkv|mpd)[^\s"'`]*)/gi);
      if (matches) matches.forEach(send);
    });
  }

  function run() {
    hideWarnings();
    unlockDownloadButtons();
    extractVideoSources();
  }

  var timer = null;
  function schedule() {
    if (timer) return;
    timer = setTimeout(function () { timer = null; run(); }, 1500);
  }

  run();
  setTimeout(run, 1000);
  setTimeout(run, 3000);
  setTimeout(run, 6000);
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NetMirrorScreen(
    onDirectLinkFound: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    remember { HeaderSettings.ensureLoaded(context) }

    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }

    var capturedLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedLink by remember { mutableStateOf<String?>(null) }

    // Fullscreen video (WebChromeClient.onShowCustomView)
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Pages the WebView is allowed to navigate to (NetMirror + the sites in the referer rules).
    val allowedHostHints = remember {
        listOf("netmirror") +
            AppConfig.REFERER_RULES.keys +
            AppConfig.REFERER_RULES.values.mapNotNull { runCatching { Uri.parse(it).host }.getOrNull() }
    }

    fun isAllowedNavigation(url: String): Boolean {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false
        if (BLOCKED_DOMAINS.any { url.contains(it, ignoreCase = true) }) return false
        if (allowedHostHints.any { url.contains(it, ignoreCase = true) }) return true
        return isVideoUrl(url)
    }

    fun addCapturedLink(url: String) {
        if (!isVideoUrl(url)) return // the JS bridge is reachable by any frame - validate here
        if (capturedLinks.any { it.equals(url, ignoreCase = true) }) return

        val wasEmpty = capturedLinks.isEmpty()
        val newList = (capturedLinks + url)
            .distinctBy { it.lowercase() }
            .sortedWith(
                compareByDescending<String> { it.contains(".m3u8", ignoreCase = true) }
                    .thenByDescending { it.length }
            )

        capturedLinks = newList
        if (selectedLink == null) selectedLink = newList.firstOrNull()
        if (wasEmpty) Toast.makeText(context, "Direct link captured!", Toast.LENGTH_SHORT).show()
    }

    fun exitFullscreen() {
        val act = activity ?: return
        val decor = act.window.decorView as ViewGroup
        customView?.let { decor.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowCompat.getInsetsController(act.window, decor).show(WindowInsetsCompat.Type.systemBars())
    }

    BackHandler {
        when {
            customView != null -> exitFullscreen()
            canGoBack && webView != null -> webView?.goBack()
            else -> onNavigateBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exitFullscreen()
            webView?.destroy()
            webView = null
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
                selectedLink?.let { link ->
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
                                text = link,
                                color = Color(0xFFB9F6CA),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onDirectLinkFound(link) },
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
                            post { addCapturedLink(url) }
                        }
                    }, "AndroidBridge")

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

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
                        javaScriptCanOpenWindowsAutomatically = false
                    }

                    // Extension's content.js reply - runs before the page's own scripts.
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(this, DETECT_SCRIPT, setOf("*"))
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean = false // block pop-up windows

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            isLoading = newProgress < 100
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            val act = activity
                            if (view == null || act == null || customView != null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            val decor = act.window.decorView as ViewGroup
                            view.setBackgroundColor(android.graphics.Color.BLACK)
                            decor.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            customView = view
                            customViewCallback = callback
                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            WindowCompat.getInsetsController(act.window, decor)
                                .hide(WindowInsetsCompat.Type.systemBars())
                        }

                        override fun onHideCustomView() {
                            exitFullscreen()
                        }
                    }

                    webViewClient = object : WebViewClient() {

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            canGoBack = view?.canGoBack() == true
                            // Fallback for WebViews without document-start scripts.
                            view?.evaluateJavascript(DETECT_SCRIPT, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = view?.canGoBack() == true
                            view?.evaluateJavascript(PAGE_SCRIPT, null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return true
                            return !isAllowedNavigation(url)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return url == null || !isAllowedNavigation(url)
                        }

                        // One interceptor, three steps in order:
                        // 1) block ads  2) capture video links  3) add extension headers
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            if (request == null) return null
                            val url = request.url.toString()

                            // 1) Block ads
                            if (BLOCKED_DOMAINS.any { url.contains(it, ignoreCase = true) }) {
                                return WebResourceResponse(
                                    "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                                )
                            }

                            // 2) Capture video links seen on the network
                            if (isVideoUrl(url)) {
                                view?.post { addCapturedLink(url) }
                            }

                            // 3) Referer rules + custom headers (GET only)
                            if (!request.method.equals("GET", ignoreCase = true)) return null
                            if (!url.startsWith("http")) return null
                            val extra = AppConfig.resolveVideoHeaders(url)
                            if (extra.isEmpty()) return null
                            return try {
                                fetchWithHeaders(url, request.requestHeaders, extra)
                            } catch (e: Exception) {
                                null // fall back to a normal WebView load
                            }
                        }
                    }

                    loadUrl(NETMIRROR_HOME)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}

/** Re-does the request ourselves so we can set headers WebView does not let us change. */
private fun fetchWithHeaders(
    url: String,
    pageHeaders: Map<String, String>,
    extra: Map<String, String>
): WebResourceResponse {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.instanceFollowRedirects = true
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000

    pageHeaders.forEach { (k, v) ->
        // Let HttpURLConnection handle compression itself.
        if (!k.equals("Accept-Encoding", ignoreCase = true)) conn.setRequestProperty(k, v)
    }
    extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
    if (extra.keys.none { it.equals("Cookie", ignoreCase = true) }) {
        CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
    }

    val code = conn.responseCode
    val stream = if (code >= 400) conn.errorStream else conn.inputStream
    val contentType = conn.contentType ?: "application/octet-stream"
    val mime = contentType.substringBefore(';').trim()
    val encoding = if (contentType.contains("charset=", ignoreCase = true))
        contentType.substringAfter("charset=").trim() else null

    val skip = setOf("content-encoding", "content-length", "transfer-encoding")
    val headers = HashMap<String, String>()
    conn.headerFields.forEach { (k, v) ->
        if (k != null && v != null && k.lowercase() !in skip) headers[k] = v.joinToString(", ")
    }
    headers["Access-Control-Allow-Origin"] = "*"

    return WebResourceResponse(
        mime, encoding, code, conn.responseMessage?.ifBlank { null } ?: "OK", headers, stream
    )
}
