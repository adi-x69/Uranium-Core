package com.example

import android.util.Log
import com.google.firebase.database.DataSnapshot

// Safe conversion extensions to prevent any DatabaseException on unexpected field types from admin
fun Any?.toSafeString(default: String = ""): String {
    return when (this) {
        null -> default
        is String -> this
        else -> this.toString()
    }
}

fun Any?.toSafeLong(default: Long = 0L): Long {
    return when (this) {
        null -> default
        is Number -> this.toLong()
        is String -> this.toLongOrNull() ?: default
        else -> default
    }
}

fun Any?.toSafeInt(default: Int = 0): Int {
    return when (this) {
        null -> default
        is Number -> this.toInt()
        is String -> this.toIntOrNull() ?: default
        else -> default
    }
}

fun Any?.toSafeBoolean(default: Boolean = true): Boolean {
    return when (this) {
        null -> default
        is Boolean -> this
        is Number -> this.toInt() != 0
        is String -> this.equals("true", ignoreCase = true) || this == "1"
        else -> default
    }
}

data class Broadcast(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val emoji: String = "",
    val accent: String = "info", // "info", "celebrate", "warning", "urgent"
    val audience: String = "all", // "all", "specific"
    val targetCount: Int = 0,
    val rule: String = "count", // "count", "until"
    val maxShows: Int = 1,
    val expiresAt: Long = 0L,
    val reactionsEnabled: Boolean = true,
    val status: String = "active", // "active", "stopped"
    val createdAt: Long = 0L,
    val stoppedAt: Long = 0L
) {
    companion object {
        fun fromSnapshot(snapshot: DataSnapshot): Broadcast? {
            return try {
                val id = snapshot.key ?: return null
                val title = snapshot.child("title").value.toSafeString().trim()
                val body = snapshot.child("body").value.toSafeString().trim()
                if (title.isEmpty() || body.isEmpty()) return null

                val emoji = snapshot.child("emoji").value.toSafeString().trim()
                val accent = snapshot.child("accent").value.toSafeString("info").trim().lowercase()
                val audience = snapshot.child("audience").value.toSafeString("all").trim().lowercase()
                val targetCount = snapshot.child("targetCount").value.toSafeInt(0)
                val rule = snapshot.child("rule").value.toSafeString("count").trim().lowercase()
                val maxShows = snapshot.child("maxShows").value.toSafeInt(1).coerceAtLeast(1)
                val expiresAt = snapshot.child("expiresAt").value.toSafeLong(0L)
                val reactionsEnabled = snapshot.child("reactionsEnabled").value.toSafeBoolean(true)
                val status = snapshot.child("status").value.toSafeString("active").trim().lowercase()
                val createdAt = snapshot.child("createdAt").value.toSafeLong(0L)
                val stoppedAt = snapshot.child("stoppedAt").value.toSafeLong(0L)

                Broadcast(
                    id = id,
                    title = title,
                    body = body,
                    emoji = emoji,
                    accent = accent,
                    audience = audience,
                    targetCount = targetCount,
                    rule = rule,
                    maxShows = maxShows,
                    expiresAt = expiresAt,
                    reactionsEnabled = reactionsEnabled,
                    status = status,
                    createdAt = createdAt,
                    stoppedAt = stoppedAt
                )
            } catch (e: Exception) {
                Log.w("BroadcastModels", "Failed to safely parse broadcast: ${e.message}")
                null
            }
        }
    }
}

data class BroadcastReceipt(
    val uid: String = "",
    val username: String = "",
    val name: String = "",
    val avatarId: String = "",
    val shownCount: Int = 0,
    val firstShownAt: Long? = null,
    val lastShownAt: Long? = null,
    val seen: Boolean = false,
    val seenAt: Long? = null,
    val reaction: String? = null,
    val reactedAt: Long? = null
) {
    companion object {
        fun fromSnapshot(snapshot: DataSnapshot): BroadcastReceipt {
            return try {
                val uid = snapshot.child("uid").value.toSafeString()
                val username = snapshot.child("username").value.toSafeString()
                val name = snapshot.child("name").value.toSafeString()
                val avatarId = snapshot.child("avatarId").value.toSafeString()
                val shownCount = snapshot.child("shownCount").value.toSafeInt(0)
                val firstShownAt = snapshot.child("firstShownAt").value?.let { it.toSafeLong(0L) }
                val lastShownAt = snapshot.child("lastShownAt").value?.let { it.toSafeLong(0L) }
                val seen = snapshot.child("seen").value.toSafeBoolean(false)
                val seenAt = snapshot.child("seenAt").value?.let { it.toSafeLong(0L) }
                val reaction = snapshot.child("reaction").value?.toSafeString()?.takeIf { it.isNotEmpty() }
                val reactedAt = snapshot.child("reactedAt").value?.let { it.toSafeLong(0L) }

                BroadcastReceipt(
                    uid = uid,
                    username = username,
                    name = name,
                    avatarId = avatarId,
                    shownCount = shownCount,
                    firstShownAt = firstShownAt,
                    lastShownAt = lastShownAt,
                    seen = seen,
                    seenAt = seenAt,
                    reaction = reaction,
                    reactedAt = reactedAt
                )
            } catch (e: Exception) {
                Log.w("BroadcastModels", "Failed to safely parse receipt: ${e.message}")
                BroadcastReceipt()
            }
        }
    }
}

val BROADCAST_REACTIONS: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "🙏")

fun isBroadcastLive(b: Broadcast, serverNow: Long): Boolean {
    return b.status == "active" && (b.rule != "until" || serverNow < b.expiresAt)
}

fun shouldShow(
    b: Broadcast,
    receipt: BroadcastReceipt?,
    serverNow: Long,
    alreadyShownThisSession: Boolean,
    isTargeted: Boolean
): Boolean {
    if (alreadyShownThisSession) return false
    if (!isBroadcastLive(b, serverNow)) return false
    if (b.audience == "specific" && !isTargeted) return false
    val shown = receipt?.shownCount ?: 0
    return when (b.rule) {
        "count" -> shown < b.maxShows.coerceIn(1, 6)
        "until" -> true
        else -> false
    }
}
