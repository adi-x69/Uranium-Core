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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.ui.theme.AbyssOutline
import com.example.ui.theme.AbyssSurfaceElevated
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.CyanCore
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.MistText
import com.example.ui.theme.MistTextMuted
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VoidBlack
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

private const val NETMIRROR_HOME = "https://netmirror.studio/"

// Web watch limit: after this many seconds of continuous playback the "watch in the app" popup shows.
private const val WEB_WATCH_LIMIT_SECONDS = 120
// A seek resets the timer, but more than this many seeks also shows the popup.
private const val WEB_MAX_SEEKS = 5

private enum class WebLimitReason { TIME, SEEKS }

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

/** One captured video link. [resolution] is e.g. "480p", or null while unknown. */
private data class CapturedLink(val key: String, val url: String, val resolution: String?)

/** Same file = same URL without the query (the "sign" token changes between requests). */
private fun linkKey(url: String): String =
    url.substringBefore('#').substringBefore('?').lowercase()

private fun resolutionValue(resolution: String?): Int =
    resolution?.removeSuffix("p")?.toIntOrNull() ?: 0

/** Turns the real video size (from the <video> element) into a label like "480p". */
private fun resolutionLabel(width: Int, height: Int): String? {
    if (height <= 0) return null
    // Portrait video -> use the width. Wide/cinema video (e.g. 1920x800) -> use 16:9 height.
    val base = if (width in 1 until height) width else maxOf(height, width * 9 / 16)
    val standards = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160)
    val closest = standards.minByOrNull { kotlin.math.abs(it - base) } ?: return null
    return "${closest}p"
}

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

/**
 * Renames "NetMirror" to "UraniumTV" in everything the user can see: page text, tab title,
 * and alt/title/placeholder texts. It keeps watching, so content the site loads later is
 * renamed too. Web addresses and handles (like netmirror.studio or t.me/netmirror_web) are
 * skipped on purpose - changing their text would not change where they point.
 */
private const val BRAND_SCRIPT = """
(function () {
  if (window.__nmBrand) return;
  window.__nmBrand = true;
  var NAME = 'UraniumTV';
  var RE = /(?<![\w@\/.-])net ?mirror(?![\w@\/-]|\.[a-z])/gi;
  var HAS = /net ?mirror/i;
  var SKIP = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, NOSCRIPT: 1, CODE: 1 };
  var ATTRS = ['alt', 'title', 'placeholder', 'aria-label'];

  function fixText(root) {
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var node;
    while ((node = walker.nextNode())) {
      var parent = node.parentNode;
      if (!parent || SKIP[parent.nodeName]) continue;
      var v = node.nodeValue;
      if (v && HAS.test(v)) {
        var nv = v.replace(RE, NAME);
        if (nv !== v) node.nodeValue = nv;
      }
    }
  }

  function fixAttrs(root) {
    root.querySelectorAll('[alt],[title],[placeholder],[aria-label]').forEach(function (el) {
      ATTRS.forEach(function (a) {
        var v = el.getAttribute(a);
        if (v && HAS.test(v)) {
          var nv = v.replace(RE, NAME);
          if (nv !== v) el.setAttribute(a, nv);
        }
      });
    });
  }

  function fixAll() {
    var root = document.documentElement;
    if (!root) return;
    fixText(root);
    fixAttrs(root);
    if (document.title && HAS.test(document.title)) {
      document.title = document.title.replace(RE, NAME);
    }
  }

  var pending = false;
  function schedule() {
    if (pending) return;
    pending = true;
    requestAnimationFrame(function () { pending = false; fixAll(); });
  }

  new MutationObserver(schedule).observe(document.documentElement, {
    childList: true, subtree: true, characterData: true
  });
  document.addEventListener('DOMContentLoaded', fixAll);
  window.addEventListener('load', fixAll);
  fixAll();
})();
"""

/**
 * Removes the site's "Join our Telegram" promo popup. It finds the promo by its text, climbs to the
 * floating box around it and hides that box. Normal page content is never hidden: only floating
 * (fixed / absolute) boxes with very little text are removed.
 */
private const val CLEAN_SCRIPT = """
(function () {
  if (window.__nmClean) return;
  window.__nmClean = true;
  var TG = /join our telegram|t\.me\/|telegram\.me\//i;
  var SKIP = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, NOSCRIPT: 1 };

  function hideTelegramPromo() {
    var root = document.documentElement;
    if (!root) return;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var node;
    while ((node = walker.nextNode())) {
      var el = node.parentElement;
      if (!el || SKIP[el.nodeName] || !TG.test(node.nodeValue || '')) continue;
      var box = el;
      for (var i = 0; i < 10 && box && box !== document.body && box !== document.documentElement; i++) {
        var pos = window.getComputedStyle(box).position;
        if (pos === 'fixed' || pos === 'absolute' || pos === 'sticky') {
          if (((box.innerText || '').length) < 400) box.style.setProperty('display', 'none', 'important');
          break;
        }
        box = box.parentElement;
      }
    }
  }

  var pending = false;
  function schedule() {
    if (pending) return;
    pending = true;
    setTimeout(function () { pending = false; hideTelegramPromo(); }, 400);
  }

  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  document.addEventListener('DOMContentLoaded', hideTelegramPromo);
  window.addEventListener('load', hideTelegramPromo);
  hideTelegramPromo();
})();
"""

/**
 * Watch-limit sensor. It only REPORTS to the app (the app keeps the counters, so reloading the page
 * does not reset them):
 *  - onWebPlayTick(): once per second while a video is really playing
 *  - onWebSeek():     when the viewer skips/scrubs (one scrub = one seek; small jumps and jumps
 *                     the site makes itself while loading or switching quality are ignored)
 * While the popup is showing, the app answers isBlocked() = true and the video is kept paused.
 */
private const val LIMIT_SCRIPT = """
(function () {
  if (window.__nmLimit) return;
  window.__nmLimit = true;
  var lastTime = new WeakMap();
  var lastSeekAt = 0;

  function attach(v) {
    if (v.__nmLim) return;
    v.__nmLim = true;
    lastTime.set(v, v.currentTime || 0);
    v.__nmIgnore = Date.now() + 4000;
    v.addEventListener('loadstart', function () { v.__nmIgnore = Date.now() + 4000; });
    v.addEventListener('emptied', function () { v.__nmIgnore = Date.now() + 4000; });
    v.addEventListener('timeupdate', function () { if (!v.seeking) lastTime.set(v, v.currentTime); });
    v.addEventListener('seeking', function () {
      var prev = lastTime.get(v) || 0;
      var cur = v.currentTime || 0;
      lastTime.set(v, cur);
      if (Date.now() < (v.__nmIgnore || 0)) return;   // jump made by the site itself
      if (Math.abs(cur - prev) < 2) return;            // tiny correction, not a real skip
      var now = Date.now();
      var newScrub = now - lastSeekAt > 1200;          // dragging the bar = one seek
      lastSeekAt = now;
      if (newScrub) { try { window.AndroidBridge.onWebSeek(); } catch (e) {} }
    });
  }

  setInterval(function () {
    var bridge = window.AndroidBridge;
    if (!bridge) return;
    var blocked = false;
    try { blocked = bridge.isBlocked(); } catch (e) {}
    var playing = false;
    document.querySelectorAll('video').forEach(function (v) {
      attach(v);
      if (blocked) { if (!v.paused) v.pause(); return; }
      if (v.duration > 60 && !v.paused && !v.ended && !v.seeking && v.readyState > 2) playing = true;
    });
    if (playing) { try { bridge.onWebPlayTick(); } catch (e) {} }
  }, 1000);
})();
"""

// Everything that must run as early as possible on every page.
private const val START_SCRIPT = DETECT_SCRIPT + BRAND_SCRIPT + CLEAN_SCRIPT + LIMIT_SCRIPT

// Page helper: hides "extension not enabled" warnings, unlocks download buttons, and reports
// the video that is loaded in the player together with its real size (width x height).
// Throttled so it does not slow the site down.
private const val PAGE_SCRIPT = """
(function () {
  if (window.__nmPage) return;
  window.__nmPage = true;
  var VIDEO_RE = /\.(m3u8|mp4|mkv|mpd)(\?|#|$)/i;
  var last = '';

  function reportVideo(v) {
    var src = v.currentSrc || v.src;
    if (!src || !VIDEO_RE.test(src)) return;
    var w = v.videoWidth || 0;
    var h = v.videoHeight || 0;
    if (h === 0) return; // metadata not loaded yet
    var sig = src + '|' + w + 'x' + h;
    if (sig === last) return; // only report when the video or its quality changes
    last = sig;
    try { window.AndroidBridge.onVideoPlaying(src, w, h); } catch (e) {}
  }

  function scanVideos() {
    document.querySelectorAll('video').forEach(function (v) {
      reportVideo(v);
      if (!v.__nm) {
        v.__nm = true;
        ['loadedmetadata', 'resize', 'playing'].forEach(function (ev) {
          v.addEventListener(ev, function () { reportVideo(v); });
        });
      }
    });
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

  function cleanup() {
    hideWarnings();
    unlockDownloadButtons();
    scanVideos();
  }

  var timer = null;
  function schedule() {
    if (timer) return;
    timer = setTimeout(function () { timer = null; cleanup(); }, 1500);
  }

  cleanup();
  setTimeout(cleanup, 1000);
  setTimeout(cleanup, 3000);
  setTimeout(cleanup, 6000);
  setInterval(scanVideos, 1000); // cheap: only looks at <video> elements
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

    var capturedLinks by remember { mutableStateOf<List<CapturedLink>>(emptyList()) }
    var selectedKey by remember { mutableStateOf<String?>(null) }

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

    // Adds a link, or updates it if the same file is already in the list.
    fun upsertLink(url: String, resolution: String?, select: Boolean) {
        val key = linkKey(url)
        val existing = capturedLinks.firstOrNull { it.key == key }
        val newResolution = resolution ?: existing?.resolution
        // Skip no-op updates (the network sends many range requests for the same file).
        if (existing != null && existing.url == url && existing.resolution == newResolution &&
            !(select && selectedKey != key)
        ) return

        val wasEmpty = capturedLinks.isEmpty()
        capturedLinks = (capturedLinks.filter { it.key != key } + CapturedLink(key, url, newResolution))
            .sortedWith(
                compareByDescending<CapturedLink> { resolutionValue(it.resolution) }
                    .thenByDescending { it.url.contains(".m3u8", ignoreCase = true) }
            )
        if (select || selectedKey == null) selectedKey = key
        if (wasEmpty) Toast.makeText(context, "Direct link captured!", Toast.LENGTH_SHORT).show()
    }

    // From the network: link found, resolution not known yet.
    fun addCapturedLink(url: String) {
        if (!isVideoUrl(url)) return
        upsertLink(url, null, select = false)
    }

    // From the page: this is the video the player is playing right now, with its real size.
    fun onVideoPlayingFound(url: String, width: Int, height: Int) {
        if (!isVideoUrl(url)) return // the JS bridge is reachable by any frame - validate here
        upsertLink(url, resolutionLabel(width, height), select = true)
    }

    val selected = capturedLinks.firstOrNull { it.key == selectedKey }

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

    // ---- Web watch limit (counters live here, so reloading the page cannot reset them) ----
    var watchedSeconds by remember { mutableStateOf(0) }
    var seekCount by remember { mutableStateOf(0) }
    var limitReason by remember { mutableStateOf<WebLimitReason?>(null) }
    val limitBlocked = remember { AtomicBoolean(false) } // read by the JS bridge from another thread

    fun triggerLimit(reason: WebLimitReason) {
        if (limitReason != null) return
        watchedSeconds = 0
        seekCount = 0
        limitBlocked.set(true)
        webView?.evaluateJavascript(
            "document.querySelectorAll('video').forEach(function(v){v.pause();});", null
        )
        if (customView != null) exitFullscreen() // so the popup sits on the normal screen
        limitReason = reason
    }

    fun onPlayTick() {
        if (limitReason != null) return
        watchedSeconds += 1
        if (watchedSeconds >= WEB_WATCH_LIMIT_SECONDS) triggerLimit(WebLimitReason.TIME)
    }

    fun onSeekEvent() {
        if (limitReason != null) return
        watchedSeconds = 0 // a seek resets the 2-minute timer...
        seekCount += 1
        if (seekCount > WEB_MAX_SEEKS) triggerLimit(WebLimitReason.SEEKS) // ...but too many seeks show the popup
    }

    fun dismissLimit() {
        limitReason = null
        limitBlocked.set(false)
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
                        text = "UraniumTV",
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
                selected?.let { current ->
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
                                text = buildString {
                                    append("Direct Link Captured!")
                                    current.resolution?.let { append(" ($it)") }
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            // Scrollable list: pick which quality to use.
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .padding(top = 6.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(capturedLinks, key = { it.key }) { item ->
                                    val isSelected = item.key == selectedKey
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Color(0x4400E676) else Color(0x1AFFFFFF)
                                            )
                                            .clickable { selectedKey = item.key }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedKey = item.key },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF00E676),
                                                unselectedColor = Color.White
                                            )
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.resolution ?: "Unknown quality",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = item.url,
                                                color = Color(0xFFB9F6CA),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onDirectLinkFound(current.url) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("USE LINK", fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = "${capturedLinks.size} link${if (capturedLinks.size == 1) "" else "s"} found",
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
                        fun onVideoPlaying(url: String, width: Int, height: Int) {
                            post { onVideoPlayingFound(url, width, height) }
                        }

                        @JavascriptInterface
                        fun onWebPlayTick() {
                            post { onPlayTick() }
                        }

                        @JavascriptInterface
                        fun onWebSeek() {
                            post { onSeekEvent() }
                        }

                        @JavascriptInterface
                        fun isBlocked(): Boolean = limitBlocked.get()
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
                        WebViewCompat.addDocumentStartJavaScript(this, START_SCRIPT, setOf("*"))
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
                            view?.evaluateJavascript(START_SCRIPT, null)
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

    limitReason?.let { reason ->
        WatchInAppDialog(
            reason = reason,
            onWatchInApp = {
                val link = capturedLinks.firstOrNull { it.key == selectedKey } ?: capturedLinks.firstOrNull()
                dismissLimit()
                if (link != null) {
                    onDirectLinkFound(link.url)
                } else {
                    Toast.makeText(
                        context,
                        "Please play the video for a moment so we can prepare it for the app.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onLater = { dismissLimit() }
        )
    }
}

/** The "continue in the app" popup, styled with the UraniumTV theme. */
@Composable
private fun WatchInAppDialog(
    reason: WebLimitReason,
    onWatchInApp: () -> Unit,
    onLater: () -> Unit
) {
    val message = when (reason) {
        WebLimitReason.TIME ->
            "You've been watching for 2 minutes on the web. To enjoy the full video with smooth " +
                "playback and the best quality, please continue in the UraniumTV app."
        WebLimitReason.SEEKS ->
            "It looks like you're skipping around quite a bit. For the most comfortable way to " +
                "watch the full video, please continue in the UraniumTV app."
    }
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(AbyssSurfaceElevated)
                    .border(1.dp, AbyssOutline, shape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glowing play badge
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            Brush.radialGradient(listOf(CyanCore.copy(alpha = 0.30f), Color.Transparent)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(Brush.linearGradient(listOf(CyanCore, VioletGlow)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = VoidBlack,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Keep watching in the app",
                    color = MistText,
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = message,
                    color = MistTextMuted,
                    fontFamily = BodyFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                // Primary button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(CyanCore, VioletGlow)))
                        .clickable(onClick = onWatchInApp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Watch in App",
                        color = VoidBlack,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(Modifier.height(6.dp))

                TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Maybe later",
                        color = MistTextMuted,
                        fontFamily = BodyFontFamily,
                        fontSize = 14.sp
                    )
                }
            }
        }
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
