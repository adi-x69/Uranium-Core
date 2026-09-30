package com.example

import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Video quality control for the YouTube IFrame player.
 *
 * Why the old menu did nothing: it only called player.setPlaybackQuality(), which YouTube
 * has deprecated for embedded players (it is silently ignored on most videos), and it
 * showed a hard-coded list of resolutions whether or not the video actually had them.
 *
 * This version:
 *  1. Asks the player which resolutions THIS video really offers (getAvailableQualityLevels).
 *  2. Requests the resolution with setPlaybackQualityRange + setPlaybackQuality.
 *  3. Verifies via getPlaybackQuality(). If YouTube ignored the request, it reloads the same
 *     video at the same position with suggestedQuality, which the player honours.
 *  4. Reports honestly whether the change took effect.
 */
const val YT_QUALITY_AUTO = "auto"

object YtQuality {
    /** Highest to lowest. These are YouTube's internal quality codes. */
    val order = listOf("highres", "hd2160", "hd1440", "hd1080", "hd720", "large", "medium", "small", "tiny")

    /** Used only when the player cannot report its levels yet. */
    val fallback = listOf("hd1080", "hd720", "large", "medium", "small")

    fun label(code: String): String = when (code) {
        "highres" -> "4K+"
        "hd2160" -> "2160p"
        "hd1440" -> "1440p"
        "hd1080" -> "1080p"
        "hd720" -> "720p"
        "large" -> "480p"
        "medium" -> "360p"
        "small" -> "240p"
        "tiny" -> "144p"
        else -> "Auto"
    }

    fun description(code: String): String = when (code) {
        "highres" -> "Ultra HD"
        "hd2160" -> "4K Ultra HD"
        "hd1440" -> "2K Quad HD"
        "hd1080" -> "Full HD"
        "hd720" -> "HD"
        "large" -> "SD"
        "medium" -> "Data saver"
        "small" -> "Low"
        "tiny" -> "Lowest"
        else -> "Recommended"
    }

    /** Text for the little badge next to the settings icon, e.g. "Auto · 720p" or "1080p". */
    fun badge(selected: String, actualLabel: String?): String {
        return if (selected == YT_QUALITY_AUTO) {
            if (actualLabel.isNullOrEmpty() || actualLabel == "Auto") "Auto" else "Auto \u00B7 $actualLabel"
        } else {
            label(selected)
        }
    }
}

/** Runs JS in the player WebView and suspends until the result arrives (or 2s pass). */
private suspend fun WebView.evalJs(js: String): String? = withTimeoutOrNull(2000L) {
    suspendCancellableCoroutine<String?> { cont ->
        post {
            try {
                evaluateJavascript(js) { result -> if (cont.isActive) cont.resume(result) }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

/** Resolutions the currently loaded video really offers (empty until the player knows). */
suspend fun ytAvailableQualities(webView: WebView?): List<String> {
    val wv = webView ?: return emptyList()
    val raw = wv.evalJs(
        "(function(){try{return (typeof player!=='undefined'&&player&&player.getAvailableQualityLevels)" +
            "?player.getAvailableQualityLevels():[];}catch(e){return [];}})()"
    ) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        val found = (0 until arr.length()).map { arr.getString(it) }.filter { it in YtQuality.order }
        YtQuality.order.filter { it in found }
    } catch (e: Exception) {
        emptyList()
    }
}

/** The quality code the player is streaming right now, or null if unknown. */
private suspend fun ytCurrentQuality(wv: WebView): String? {
    val raw = wv.evalJs(
        "(function(){try{return player.getPlaybackQuality();}catch(e){return '';}})()"
    ) ?: return null
    val cleaned = raw.trim('"', ' ')
    return if (cleaned.isEmpty() || cleaned == "null" || cleaned == "undefined" || cleaned == "unknown") null else cleaned
}

private fun softQualityJs(code: String): String {
    return if (code == YT_QUALITY_AUTO) {
        """
        (function(){try{
            if (typeof player==='undefined'||!player) return 'noplayer';
            if (player.setPlaybackQualityRange) player.setPlaybackQualityRange('tiny','highres');
            if (player.setPlaybackQuality) player.setPlaybackQuality('default');
            return 'ok';
        }catch(e){return 'err';}})()
        """.trimIndent()
    } else {
        """
        (function(){try{
            if (typeof player==='undefined'||!player) return 'noplayer';
            if (player.setPlaybackQualityRange) player.setPlaybackQualityRange('$code','$code');
            if (player.setPlaybackQuality) player.setPlaybackQuality('$code');
            return 'ok';
        }catch(e){return 'err';}})()
        """.trimIndent()
    }
}

/**
 * Reloads the same video at the same position asking for a specific quality. Playing videos
 * keep playing; paused videos stay paused (cued) and pick the quality up when resumed.
 * Returns "playing" or "cued".
 */
private fun reloadWithQualityJs(code: String, videoId: String): String {
    val q = if (code == YT_QUALITY_AUTO) "default" else code
    val safeId = videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    return """
        (function(){try{
            if (typeof player==='undefined'||!player) return 'noplayer';
            var id = '$safeId';
            if (!id) { try { id = player.getVideoData().video_id; } catch(e) {} }
            if (!id) return 'noid';
            var t = 0; try { t = player.getCurrentTime() || 0; } catch(e) {}
            var st = -1; try { st = player.getPlayerState(); } catch(e) {}
            var opts = { videoId: id, startSeconds: t, suggestedQuality: '$q' };
            if (st === 1 || st === 3) { player.loadVideoById(opts); return 'playing'; }
            player.cueVideoById(opts); return 'cued';
        }catch(e){return 'err';}})()
    """.trimIndent()
}

/** Re-sends the quality request without reloading. Cheap; used when playback (re)starts. */
suspend fun ytReapplyQuality(webView: WebView?, code: String) {
    val wv = webView ?: return
    wv.evalJs(softQualityJs(code))
}

/**
 * Applies a quality choice. Returns true when the player confirms it (or the change is queued
 * because the video is paused), false when YouTube refused it.
 */
suspend fun ytApplyQuality(
    webView: WebView?,
    code: String,
    previousCode: String,
    videoId: String
): Boolean {
    val wv = webView ?: return false

    // Attempt 1: ask the running player directly.
    wv.evalJs(softQualityJs(code))

    if (code == YT_QUALITY_AUTO) {
        // Going back to Auto after a forced quality: reload once so YouTube's adaptive logic
        // takes over again even if the range request was ignored.
        if (previousCode != YT_QUALITY_AUTO) {
            wv.evalJs(reloadWithQualityJs(YT_QUALITY_AUTO, videoId))
        }
        return true
    }

    delay(1200L)
    if (ytCurrentQuality(wv) == code) return true

    // Attempt 2: the player ignored it, so reload at the same spot with suggestedQuality.
    val reload = wv.evalJs(reloadWithQualityJs(code, videoId))?.trim('"')
    if (reload == "cued") return true // applies as soon as the video is resumed
    if (reload != "playing") return false

    // Give the new stream time to start, then confirm.
    repeat(6) {
        delay(800L)
        if (ytCurrentQuality(wv) == code) return true
    }
    return false
}

@Composable
fun YtQualityDialog(
    selected: String,
    actualLabel: String?,
    webView: WebView?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // null = still asking the player; empty = player couldn't tell us.
    var levels by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(webView) {
        var result = emptyList<String>()
        for (attempt in 0 until 5) {
            result = ytAvailableQualities(webView)
            if (result.isNotEmpty()) break
            delay(400L)
        }
        levels = result
    }

    val known = levels != null && levels!!.isNotEmpty()
    val codes: List<String> = when {
        levels == null -> emptyList()
        known -> levels!!
        else -> YtQuality.fallback
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = "Video Quality", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val playing = if (actualLabel.isNullOrEmpty() || actualLabel == "Auto") "" else "Now playing $actualLabel"
                val sub = when {
                    levels == null -> "Checking available resolutions\u2026"
                    known -> if (playing.isEmpty()) "Resolutions available for this video" else "$playing \u00B7 resolutions available for this video"
                    else -> "Couldn't read this video's resolutions yet. Start playback for an exact list."
                }
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                val rows = listOf(YT_QUALITY_AUTO) + codes
                rows.forEach { code ->
                    val isSelected = code == selected
                    val title = if (code == YT_QUALITY_AUTO) "Auto" else YtQuality.label(code)
                    val desc = YtQuality.description(code)
                    Surface(
                        onClick = {
                            onSelect(code)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$title  \u00B7  $desc",
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\u26A1 2x speed player disabled for smooth sync playback",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
