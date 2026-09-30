package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.webkit.WebSettingsCompat
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

// Powerful AdBlocker domain list covering streaming ad networks, popunders, analytics and trackers.
private val AD_DOMAINS = setOf(
    "llvpn.com", "popads.net", "popcash.net", "propellerads.com", "propellerclick.com",
    "propu.sh", "adsterra.com", "clickadu.com", "exoclick.com", "juicyads.com",
    "trafficjunky.com", "adspyglass.com", "ad-maven.com", "admaven.com", "hilltopads.com",
    "monetag.com", "infolinks.com", "adcash.com", "yllix.com", "yllixpro.com",
    "bidvertiser.com", "highcpmgate.com", "deloton.com", "alwingulla.com",
    "brightadnetwork.com", "onclickperformance.com", "onclickalgo.com", "go.ad2upapp.com",
    "adstarget.com", "adsupply.com", "realsrv.com", "tsyndicate.com", "trafficfactory.biz",
    "ero-advertising.com", "dtiserv2.com", "adtng.com", "coin-hive.com", "coinhive.com",
    "histats.com", "mgid.com", "revcontent.com", "zergnet.com", "doubleclick.net",
    "googlesyndication.com", "googleadservices.com", "pagead2.googlesyndication",
    "adservice.google", "adnxs.com", "amazon-adsystem.com", "scorecardresearch.com",
    "outbrain.com", "taboola.com", "criteo.com", "pubmatic.com", "openx.net",
    "rubiconproject.com", "moatads.com", "casalemedia.com", "smartadserver.com",
    "yieldmo.com", "exponential.com", "tribalfusion.com", "contextweb.com",
    "adblade.com", "sovrn.com", "conversantmedia.com", "media.net", "sharethrough.com",
    "spotxchange.com", "teads.tv", "zedo.com", "adcolony.com", "unityads.unity3d.com",
    "applovin.com", "ironsrc.com", "vungle.com", "chartboost.com", "tapjoy.com",
    "fyber.com", "smaato.net", "inmobi.com", "mopub.com", "hotjar.com", "clarity.ms"
)

// 68-byte 1x1 transparent PNG for neutralizing image ad trackers
private val TRANSPARENT_1X1_PNG = byteArrayOf(
    0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
    0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1F.toByte(), 0x15.toByte(), 0xC4.toByte(),
    0x89.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0A.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
    0x54.toByte(), 0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
    0x05.toByte(), 0x00.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x2D.toByte(), 0xB4.toByte(), 0x00.toByte(),
    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4E.toByte(), 0x44.toByte(), 0xAE.toByte(),
    0x42.toByte(), 0x60.toByte(), 0x82.toByte()
)

// Hosts that must NEVER be treated as ads (the site itself, player embeds, video CDNs, captchas).
private val TRUSTED_HOST_HINTS = listOf(
    "netmirror", "hakunaymatata.com", "hakunayamata.com", "movieboxonline.net",
    "watch21.shop", "watch22.shop", "watch-download.shop", "proxy22.shop",
    "imdb3.shop", "imdb4.shop", "mzfi.me", "cloudflare", "hcaptcha", "recaptcha"
)

// Ad hosts that are matched by prefix (they have many country endings).
private val AD_HOST_PREFIXES = listOf("adservice.google.", "pagead2.googlesyndication.")

// Stream pieces (segments / subtitles) - never real "direct links" and never ads.
private val SEGMENT_EXT_REGEX =
    Regex("""\.(ts|m4s|m4a|m4v|aac|cmfv|cmfa|vtt|srt|webvtt)$""", RegexOption.IGNORE_CASE)

private fun urlPath(url: String): String = url.substringBefore('#').substringBefore('?')

private fun hostOf(url: String): String =
    (runCatching { Uri.parse(url).host }.getOrNull() ?: "").lowercase()

private fun isSegmentUrl(url: String): Boolean = SEGMENT_EXT_REGEX.containsMatchIn(urlPath(url))

private fun headerValue(headers: Map<String, String>?, name: String): String =
    headers?.entries?.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.lowercase() ?: ""

/**
 * Ad check by HOST (exact match or sub-domain), so a normal URL that only mentions an ad word
 * in its path/query is never blocked, and the real site / player / CDN hosts are always safe.
 */
private fun isAdUrl(url: String): Boolean {
    if (!url.startsWith("http", ignoreCase = true)) return false
    val host = hostOf(url)
    if (host.isEmpty()) return false
    if (TRUSTED_HOST_HINTS.any { host.contains(it) }) return false
    if (AD_DOMAINS.any { host == it || host.endsWith(".$it") }) return true
    if (AD_HOST_PREFIXES.any { host.startsWith(it) }) return true
    if (isVideoUrl(url) || isSegmentUrl(url)) return false
    val path = urlPath(url).lowercase()
    return path.contains("/popunder") || path.contains("/pop-under") || path.contains("popunder.js") ||
        path.contains("/popups.js") || path.contains("/popup.js") ||
        path.contains("/adserver/") || path.contains("/adservice/") ||
        path.contains("/adsystem/") || path.contains("/advertisement/")
}

private fun stubResponse(mime: String, encoding: String?, body: ByteArray): WebResourceResponse =
    WebResourceResponse(
        mime,
        encoding,
        200,
        "OK",
        mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "public, max-age=3600"
        ),
        ByteArrayInputStream(body)
    )

/**
 * Returns a harmless "200 OK" of the RIGHT type for a blocked ad request, so the page's own
 * onload handlers run and its anti-adblock check sees "everything loaded fine".
 */
private fun createAdBlockResponse(url: String, headers: Map<String, String>?): WebResourceResponse {
    val path = urlPath(url).lowercase()
    val accept = headerValue(headers, "Accept")
    val dest = headerValue(headers, "Sec-Fetch-Dest")
    val isCors = headerValue(headers, "Origin").isNotEmpty() // fetch()/XHR, not <script>/<img>

    val isHtml = dest == "iframe" || dest == "document" || dest == "frame" ||
        (accept.contains("text/html") && !path.endsWith(".js"))
    val isCss = dest == "style" || path.endsWith(".css") || accept.startsWith("text/css")
    val isImg = dest == "image" || accept.startsWith("image/") ||
        path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".jpg") ||
        path.endsWith(".jpeg") || path.endsWith(".webp") || path.endsWith(".ico") ||
        path.endsWith(".svg")
    val isJs = dest == "script" || path.endsWith(".js") || path.contains("tag.min")

    return when {
        isHtml -> stubResponse("text/html", "utf-8", "<html><body></body></html>".toByteArray(Charsets.UTF_8))
        isCss -> stubResponse("text/css", "utf-8", ByteArray(0))
        isImg -> stubResponse("image/png", null, TRANSPARENT_1X1_PNG)
        isJs -> stubResponse("application/javascript", "utf-8", AD_STUB_JS.toByteArray(Charsets.UTF_8))
        isCors -> stubResponse("application/json", "utf-8", "{}".toByteArray(Charsets.UTF_8))
        else -> stubResponse("application/javascript", "utf-8", AD_STUB_JS.toByteArray(Charsets.UTF_8))
    }
}

private const val AD_STUB_JS = """
/* UraniumTV AdBlock Stub */
window.llvpnLoaded = true;
window.canRunAds = true;
window.isAdBlockActive = false;
window.adblock = false;
window.adsbygoogle = window.adsbygoogle || [];
"""

// Real video/playlist files only. Transport segments (.ts, .m4s) and generic "video" URLs are NOT matched.
private val VIDEO_URL_REGEX = Regex("""\.(m3u8|mp4|mkv|mpd)(\?|#|&|/|$)""", RegexOption.IGNORE_CASE)

// fMP4 pieces such as init.mp4 / seg-12.mp4 / chunk_3.mp4 are segments, not the movie.
private val SEGMENT_NAME_REGEX =
    Regex("""/(init|seg|segment|chunk|frag|fragment)[-_.]?[\w-]*\.mp4$""", RegexOption.IGNORE_CASE)

private fun isVideoUrl(url: String): Boolean =
    url.startsWith("http", ignoreCase = true) &&
        VIDEO_URL_REGEX.containsMatchIn(url) &&
        !SEGMENT_NAME_REGEX.containsMatchIn(urlPath(url))

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

/** Rank links preferring hakunayamata.com / hakunaymatata.com and higher quality. */
private fun rankCapturedLink(link: CapturedLink): Long {
    var score = 0L
    val lower = link.url.lowercase()
    if (lower.contains("hakunaymatata.com") || lower.contains("hakunayamata.com")) {
        score += 100_000L
    } else if (lower.contains("proxy22.shop") || lower.contains("watch22.shop")) {
        score += 20_000L
    }
    if (lower.contains(".m3u8")) {
        score += 10_000L
    } else if (lower.contains(".mp4")) {
        score += 5_000L
    }
    score += resolutionValue(link.resolution)
    return score
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
    setTimeout(function () { pending = false; fixAll(); }, 400);
  }

  (window.__nmObs || function () {})(schedule, {
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

  (window.__nmObs || function () {})(schedule);
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

/**
 * Tiny helper used by the other scripts: starts a MutationObserver even when the script runs
 * at document start, before <html> exists (observing null would throw and kill the whole script).
 */
private const val OBSERVE_SCRIPT = """
(function () {
  if (window.__nmObs) return;
  try {
    Object.defineProperty(window, '__nmObs', {
      enumerable: false,
      value: function (cb, opts) {
        function go() {
          try {
            new MutationObserver(cb).observe(document.documentElement, opts || { childList: true, subtree: true });
          } catch (e) {}
        }
        if (document.documentElement) { go(); return; }
        var t = setInterval(function () {
          if (document.documentElement) { clearInterval(t); go(); }
        }, 10);
      }
    });
  } catch (e) {}
})();
"""

/**
 * AdBlocker & Anti-Adblock Defeater script.
 * - llvpnLoaded / canRunAds spoofing so the site's "disable your ad blocker" check passes
 * - popup / window.open traps neutralised
 * - full-screen click-trap overlays removed (never the player: anything holding a <video> or a
 *   trusted player iframe is left alone, and iframes are never removed)
 * IMPORTANT: it must NOT hide "bait" elements (.adsbox, .ad-banner ...). Anti-adblock scripts create
 * those and check whether they got hidden - hiding them is exactly what triggers the warning.
 */
private const val ADBLOCK_SCRIPT = """
(function () {
  if (window.__uraniumAdBlockReady) return;
  window.__uraniumAdBlockReady = true;

  var TRUSTED = /netmirror|hakuna|movieboxonline|watch2[12]\.shop|watch-download\.shop|proxy22\.shop|imdb[34]\.shop|mzfi\.me|cloudflare|hcaptcha|recaptcha/i;

  // 1. llvpnLoaded must always read as true
  try {
    Object.defineProperty(window, 'llvpnLoaded', {
      get: function () { return true; },
      set: function () {},
      configurable: true,
      enumerable: true
    });
  } catch (e) {
    try { window.llvpnLoaded = true; } catch (e2) {}
  }

  // 2. Common anti-adblock flags
  try {
    window.canRunAds = true;
    window.isAdBlockActive = false;
    window.adblock = false;
    window.adsbygoogle = window.adsbygoogle || [];
    window.google_ad_client = 'ca-pub-0000000000000000';
    window.google_ad_status = 1;
  } catch (e) {}

  // 3. Pop-up / pop-under traps get a harmless dummy window
  try {
    window.open = function (url) {
      return {
        closed: true,
        focus: function () {},
        blur: function () {},
        close: function () {},
        location: { href: url || '' }
      };
    };
  } catch (e) {}

  // 4. Errors coming from the blocked llvpn scripts are swallowed
  window.addEventListener('error', function (e) {
    if (e && e.filename && e.filename.indexOf('llvpn') !== -1) {
      try { e.stopImmediatePropagation(); } catch (x) {}
    }
  }, true);

  // 5. Hide ONLY the warning cards and the classic 2147483647 click-trap (never anything holding a player)
  function injectAdblockStyles() {
    if (document.getElementById('__uranium_adblock_css__')) return;
    var style = document.createElement('style');
    style.id = '__uranium_adblock_css__';
    style.textContent =
      '.adblock-container, .adblock-card { display: none !important; }' +
      'div[style*="z-index: 2147483647"]:not(:has(video)):not(:has(iframe)) { display: none !important; }';
    var target = document.head || document.documentElement;
    if (target) target.appendChild(style);
  }

  function holdsPlayer(el) {
    try {
      if (el.tagName === 'VIDEO' || el.querySelector('video')) return true;
      var frames = el.querySelectorAll('iframe');
      for (var i = 0; i < frames.length; i++) {
        if (TRUSTED.test(frames[i].src || '')) return true;
      }
    } catch (e) {}
    return false;
  }

  // 6. Remove full-screen click-trap overlays that ad scripts append straight to <body>
  var cleanTimer = null;
  function cleanAdElements() {
    cleanTimer = null;
    var body = document.body;
    if (!body) return;
    document.querySelectorAll('.adblock-container, .adblock-card').forEach(function (el) { el.remove(); });
    var vw = window.innerWidth, vh = window.innerHeight;
    var kids = body.children;
    for (var i = kids.length - 1; i >= 0; i--) {
      var el = kids[i];
      if (el.tagName !== 'DIV' && el.tagName !== 'A' && el.tagName !== 'SPAN') continue;
      if (holdsPlayer(el)) continue;
      var s = window.getComputedStyle(el);
      if (s.position !== 'fixed' && s.position !== 'absolute') continue;
      if (!(parseInt(s.zIndex, 10) > 10000)) continue;
      var r = el.getBoundingClientRect();
      if (r.width < vw * 0.8 || r.height < vh * 0.8) continue;
      if ((el.textContent || '').trim().length > 40) continue; // real UI (dialogs) has text; traps do not
      el.remove();
    }
  }
  function scheduleClean() {
    if (cleanTimer) return;
    cleanTimer = setTimeout(cleanAdElements, 700);
  }

  injectAdblockStyles();
  document.addEventListener('DOMContentLoaded', function () { injectAdblockStyles(); scheduleClean(); });
  window.addEventListener('load', function () { injectAdblockStyles(); scheduleClean(); });
  (window.__nmObs || function () {})(scheduleClean);
})();
"""

/**
 * Page helper (runs in every frame, including the player iframe):
 *  - reports the playing <video> together with its real size
 *  - also catches playlists / big video files by their response type, so links that have no
 *    ".m3u8" / ".mp4" in the address are found too
 *  - removes "extension not enabled" / "ad blocker detected" warnings (only the small warning box,
 *    never a big page container) and unlocks download buttons
 */
private const val PAGE_SCRIPT = """
(function () {
  if (window.__nmPage) return;
  window.__nmPage = true;
  var VIDEO_RE = /\.(m3u8|mp4|mkv|mpd)(\?|#|&|\/|$)/i;
  var SEG_RE = /\/(init|seg|segment|chunk|frag|fragment)[-_.]?[\w-]*\.mp4$/i;
  var WARN_RE = /extension not enable|ad ?block(er)? detected|disable (your )?ad ?block|please disable (your )?ad|extension required|private dns|preventing required resources/i;
  var seenMedia = {};

  function bridge() {
    try { return window.AndroidBridge; } catch (e) { return null; }
  }

  function reportVideo(v) {
    var src = v.currentSrc || v.src;
    if (!src || !VIDEO_RE.test(src) || SEG_RE.test(src.split('?')[0])) return;
    var w = v.videoWidth || 0;
    var h = v.videoHeight || 0;
    if (h === 0) return; // metadata not loaded yet
    var sig = src + '|' + w + 'x' + h;
    if (v.__nmSig === sig) return; // only report when the video or its quality changes
    v.__nmSig = sig;
    try { bridge().onVideoPlaying(src, w, h); } catch (e) {}
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

  // ---- links without a file extension: recognised by the response's content type ----
  function reportMedia(url) {
    if (!url || url.indexOf('http') !== 0 || seenMedia[url]) return;
    seenMedia[url] = 1;
    try { bridge().onMediaUrl(url); } catch (e) {}
  }
  function looksLikeMedia(ct, len) {
    ct = (ct || '').toLowerCase();
    if (ct.indexOf('mpegurl') >= 0 || ct.indexOf('dash+xml') >= 0) return true;
    return /^video\/(mp4|webm|x-matroska)/.test(ct) && len >= 1000000;
  }
  function totalLength(getHeader) {
    var cr = getHeader('content-range');
    if (cr && cr.indexOf('/') > 0) {
      var t = parseInt(cr.split('/')[1], 10);
      if (t > 0) return t;
    }
    return parseInt(getHeader('content-length') || '0', 10) || 0;
  }

  if (window.fetch && !window.__nmFetch) {
    window.__nmFetch = true;
    var origFetch = window.fetch;
    window.fetch = function () {
      var p = origFetch.apply(this, arguments);
      try {
        p.then(function (res) {
          try {
            var get = function (n) { return res.headers.get(n); };
            if (res && looksLikeMedia(get('content-type'), totalLength(get))) reportMedia(res.url);
          } catch (e) {}
        }, function () {});
      } catch (e) {}
      return p;
    };
  }

  if (window.XMLHttpRequest && !window.__nmXhr) {
    window.__nmXhr = true;
    var origOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function () {
      var xhr = this;
      try {
        xhr.addEventListener('readystatechange', function () {
          if (xhr.readyState !== 2) return;
          try {
            var get = function (n) { return xhr.getResponseHeader(n); };
            if (looksLikeMedia(get('content-type'), totalLength(get))) reportMedia(xhr.responseURL);
          } catch (e) {}
        });
      } catch (e) {}
      return origOpen.apply(this, arguments);
    };
  }

  // ---- warnings: hide the small warning box only ----
  function hideWarnings() {
    document.querySelectorAll('.adblock-container, .adblock-card').forEach(function (el) { el.remove(); });
    var root = document.body;
    if (!root) return;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var node;
    var hits = [];
    while ((node = walker.nextNode())) {
      var v = node.nodeValue;
      if (v && v.length < 400 && WARN_RE.test(v)) hits.push(node);
    }
    hits.forEach(function (n) {
      var el = n.parentElement;
      if (!el || /^(SCRIPT|STYLE|TEXTAREA|NOSCRIPT|CODE|PRE|TITLE)$/.test(el.nodeName)) return;
      var box = el;
      var found = false;
      for (var i = 0; i < 6 && box && box !== document.body && box !== document.documentElement; i++) {
        var pos = window.getComputedStyle(box).position;
        var cls = String(box.className && box.className.baseVal !== undefined ? box.className.baseVal : box.className || '') +
          ' ' + (box.id || '') + ' ' + (box.getAttribute('role') || '');
        if (pos === 'fixed' || pos === 'absolute' || pos === 'sticky' ||
            /modal|dialog|popup|overlay|alert|toast|banner|adblock/i.test(cls)) { found = true; break; }
        box = box.parentElement;
      }
      if (!found) box = el; // no floating box around it: hide just the text element itself
      if (!box || box === document.body || box === document.documentElement) return;
      if (box.querySelector('video, iframe') || (box.textContent || '').length > 600) return;
      box.style.setProperty('display', 'none', 'important');
    });
  }

  function unlockDownloadButtons() {
    document.querySelectorAll('button, a, div[role="button"]').forEach(function (btn) {
      var t = (btn.textContent || '').toLowerCase();
      if (t.length < 60 && t.indexOf('download') >= 0) {
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
  (window.__nmObs || function () {})(schedule);
})();
"""

// Everything that must run as early as possible on EVERY frame (page + player iframes).
// Each script is wrapped in its own try/catch so one failing script can never stop the others.
private val START_SCRIPT: String = listOf(
    OBSERVE_SCRIPT, ADBLOCK_SCRIPT, DETECT_SCRIPT, BRAND_SCRIPT, CLEAN_SCRIPT, LIMIT_SCRIPT, PAGE_SCRIPT
).joinToString("\n") { script -> "try {\n" + script + "\n} catch (e) {}" }

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
    // Always posts to the main thread, even if the WebView is detached or being recreated.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // Bumped when the WebView's render process dies, so a fresh WebView is built (no app crash).
    var webViewKey by remember { mutableStateOf(0) }
    var lastPageUrl by remember { mutableStateOf(NETMIRROR_HOME) }

    var capturedLinks by remember { mutableStateOf<List<CapturedLink>>(emptyList()) }
    var selectedKey by remember { mutableStateOf<String?>(null) }

    // Deduplicated list: strictly ONE link per video quality resolution.
    // If the video has multiple qualities (1080p, 720p, 480p, 360p), each quality has exactly 1 link.
    // hakunayamata.com / hakunaymatata.com is strongly preferred as the best link.
    val displayLinks = remember(capturedLinks) {
        val knownResLinks = capturedLinks
            .filter { !it.resolution.isNullOrBlank() }
            .groupBy { it.resolution!! }
            .mapValues { (_, links) -> links.maxByOrNull { rankCapturedLink(it) }!! }

        if (knownResLinks.isNotEmpty()) {
            knownResLinks.values.sortedWith(
                compareByDescending<CapturedLink> { rankCapturedLink(it) }
                    .thenByDescending { resolutionValue(it.resolution) }
            )
        } else {
            val best = capturedLinks.maxByOrNull { rankCapturedLink(it) }
            if (best != null) listOf(best) else emptyList()
        }
    }

    // Auto-select best link when links change
    LaunchedEffect(displayLinks) {
        if (displayLinks.isNotEmpty()) {
            if (selectedKey == null || displayLinks.none { it.key == selectedKey }) {
                selectedKey = displayLinks.first().key
            }
        }
    }

    // Fullscreen video (WebChromeClient.onShowCustomView)
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Pages the WebView is allowed to navigate to (NetMirror + Cloudflare challenges + player hosts + referer rules).
    val allowedHostHints = remember {
        listOf(
            "netmirror", "cloudflare", "challenges", "turnstile", "recaptcha", "hcaptcha",
            "watch21.shop", "watch22.shop", "watch-download.shop", "proxy22.shop",
            "imdb3.shop", "imdb4.shop", "movieboxonline.net", "mzfi.me"
        ) +
            AppConfig.REFERER_RULES.keys +
            AppConfig.REFERER_RULES.values.mapNotNull { runCatching { Uri.parse(it).host }.getOrNull() }
    }

    fun isAllowedNavigation(url: String): Boolean {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false
        if (isAdUrl(url)) return false
        val host = hostOf(url)
        if (host.isNotEmpty() && allowedHostHints.any { host.contains(it, ignoreCase = true) }) return true
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
                compareByDescending<CapturedLink> { rankCapturedLink(it) }
                    .thenByDescending { resolutionValue(it.resolution) }
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
        val res = resolutionLabel(width, height)
        upsertLink(url, res, select = true)
    }

    val selected = displayLinks.firstOrNull { it.key == selectedKey } ?: displayLinks.firstOrNull()

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
            runCatching { exitFullscreen() }
            webView?.let { wv ->
                runCatching { wv.stopLoading() }
                runCatching { (wv.parent as? ViewGroup)?.removeView(wv) }
                runCatching { wv.destroy() }
            }
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

                            // Clean list: strictly ONE link per video quality resolution.
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 135.dp)
                                    .padding(top = 6.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(displayLinks, key = { it.key }) { item ->
                                    val isSelected = item.key == selected?.key
                                    val isHakuna = item.url.contains("hakunaymatata.com", ignoreCase = true) ||
                                        item.url.contains("hakunayamata.com", ignoreCase = true)
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.resolution ?: "Auto Quality",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                if (isHakuna) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        color = Color(0xFF00E676),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "⚡ PREFERRED CDN",
                                                            color = Color.Black,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
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
                                    text = "${displayLinks.size} link${if (displayLinks.size == 1) "" else "s"} found",
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
        key(webViewKey) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this

                    // Bridge so JavaScript can send links to Kotlin
                    // Any frame (even an ad iframe) can call these, so every argument is treated as
                    // untrusted: nullable + validated + wrapped, an exception here would kill the app.
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onVideoPlaying(url: String?, width: Int, height: Int) {
                            if (url.isNullOrBlank()) return
                            mainHandler.post { runCatching { onVideoPlayingFound(url, width, height) } }
                        }

                        @JavascriptInterface
                        fun onMediaUrl(url: String?) {
                            if (url.isNullOrBlank()) return
                            mainHandler.post { runCatching { addCapturedLink(url) } }
                        }

                        @JavascriptInterface
                        fun onWebPlayTick() {
                            mainHandler.post { runCatching { onPlayTick() } }
                        }

                        @JavascriptInterface
                        fun onWebSeek() {
                            mainHandler.post { runCatching { onSeekEvent() } }
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
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                            WebSettingsCompat.setSafeBrowsingEnabled(this, true)
                        }
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
                            if (url != null && url.startsWith("http")) lastPageUrl = url
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
                            if (request?.isForMainFrame == true) return !isAllowedNavigation(url)
                            // Player / captcha frames: only block ads and non-web schemes,
                            // so a player hosted on a new domain is not cut off.
                            return !(url.startsWith("http://") || url.startsWith("https://")) || isAdUrl(url)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return url == null || !isAllowedNavigation(url)
                        }

                        // Without this, Android KILLS THE WHOLE APP whenever the WebView's renderer
                        // runs out of memory or crashes (heavy pages / long video). Returning true
                        // keeps the app alive; we throw the dead WebView away and build a new one.
                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            mainHandler.post {
                                runCatching { exitFullscreen() }
                                view?.let { dead ->
                                    runCatching { (dead.parent as? ViewGroup)?.removeView(dead) }
                                    runCatching { dead.destroy() }
                                }
                                webView = null
                                isLoading = true
                                Toast.makeText(context, "Page crashed - reloading...", Toast.LENGTH_SHORT).show()
                                webViewKey += 1
                            }
                            return true
                        }

                        override fun onSafeBrowsingHit(
                            view: WebView?,
                            request: WebResourceRequest?,
                            threatType: Int,
                            callback: SafeBrowsingResponse?
                        ) {
                            // Main page flagged: go back to safety. Flagged embedded frames/files
                            // (players, CDNs) are let through so the video is not cut off.
                            if (request?.isForMainFrame == true) callback?.backToSafety(true)
                            else callback?.proceed(false)
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            // Do not bypass invalid SSL certificates to prevent MITM attacks
                            handler?.cancel()
                        }

                        // One interceptor, three steps in order:
                        // 1) block ads  2) capture video links  3) add extension headers (video files only)
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            if (request == null) return null
                            return try {
                                val url = request.url.toString()

                                // 1) Block ads & return a clean stub so anti-adblock detection is neutralized
                                if (isAdUrl(url)) return createAdBlockResponse(url, request.requestHeaders)

                                // 2) Capture video links seen on the network
                                val isVideo = isVideoUrl(url)
                                if (isVideo) mainHandler.post { runCatching { addCapturedLink(url) } }

                                // 3) Referer rules + custom headers - ONLY for the video files/segments
                                //    (never re-download the site's pages/scripts, that breaks the site).
                                if (!request.method.equals("GET", ignoreCase = true)) return null
                                if (!url.startsWith("http")) return null
                                val isMedia = isVideo || isSegmentUrl(url) ||
                                    AppConfig.REFERER_RULES.keys.any { url.contains(it) }
                                if (!isMedia) return null
                                val extra = AppConfig.resolveVideoHeaders(url)
                                if (extra.isEmpty()) return null
                                fetchWithHeaders(url, request.requestHeaders, extra)
                            } catch (e: Exception) {
                                null // fall back to a normal WebView load
                            }
                        }
                    }

                    loadUrl(lastPageUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
        }
    }

    limitReason?.let { reason ->
        WatchInAppDialog(
            reason = reason,
            onWatchInApp = {
                val link = displayLinks.firstOrNull { it.key == selectedKey } ?: displayLinks.firstOrNull()
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
    if (code < 200 || code in 300..399) {
        // WebResourceResponse rejects these; let WebView load the file itself instead.
        conn.disconnect()
        throw java.io.IOException("Unsupported status $code")
    }
    val stream = (if (code >= 400) conn.errorStream else conn.inputStream)
        ?: ByteArrayInputStream(ByteArray(0))
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
