package com.example

import com.google.firebase.database.DataSnapshot

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
            val id = snapshot.key ?: return null
            val title = snapshot.child("title").getValue(String::class.java)?.trim() ?: ""
            val body = snapshot.child("body").getValue(String::class.java)?.trim() ?: ""
            if (title.isEmpty() || body.isEmpty()) return null

            val emoji = snapshot.child("emoji").getValue(String::class.java)?.trim() ?: ""
            val accent = snapshot.child("accent").getValue(String::class.java)?.trim()?.lowercase() ?: "info"
            val audience = snapshot.child("audience").getValue(String::class.java)?.trim()?.lowercase() ?: "all"
            val targetCount = (snapshot.child("targetCount").getValue(Number::class.java))?.toInt() ?: 0
            val rule = snapshot.child("rule").getValue(String::class.java)?.trim()?.lowercase() ?: "count"
            val maxShows = (snapshot.child("maxShows").getValue(Number::class.java))?.toInt() ?: 1
            val expiresAt = (snapshot.child("expiresAt").getValue(Number::class.java))?.toLong() ?: 0L
            val reactionsEnabled = snapshot.child("reactionsEnabled").getValue(Boolean::class.java) ?: true
            val status = snapshot.child("status").getValue(String::class.java)?.trim()?.lowercase() ?: "active"
            val createdAt = (snapshot.child("createdAt").getValue(Number::class.java))?.toLong() ?: 0L
            val stoppedAt = (snapshot.child("stoppedAt").getValue(Number::class.java))?.toLong() ?: 0L

            return Broadcast(
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
            val uid = snapshot.child("uid").getValue(String::class.java) ?: ""
            val username = snapshot.child("username").getValue(String::class.java) ?: ""
            val name = snapshot.child("name").getValue(String::class.java) ?: ""
            val avatarId = snapshot.child("avatarId").getValue(String::class.java) ?: ""
            val shownCount = (snapshot.child("shownCount").getValue(Number::class.java))?.toInt() ?: 0
            val firstShownAt = (snapshot.child("firstShownAt").getValue(Number::class.java))?.toLong()
            val lastShownAt = (snapshot.child("lastShownAt").getValue(Number::class.java))?.toLong()
            val seen = snapshot.child("seen").getValue(Boolean::class.java) ?: false
            val seenAt = (snapshot.child("seenAt").getValue(Number::class.java))?.toLong()
            val reaction = snapshot.child("reaction").getValue(String::class.java)
            val reactedAt = (snapshot.child("reactedAt").getValue(Number::class.java))?.toLong()

            return BroadcastReceipt(
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
