package com.example

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue

/**
 * One row shown in Home's "Continue Watching" strip: the last thing this user
 * was watching in a given room, scoped per-room (not a global history) so
 * "Resume" always drops you back into that specific room where it left off.
 * Stored at users/{uid}/continueWatching/{roomCode}.
 */
data class ContinueWatchingEntry(
    val roomCode: String,
    val videoUrl: String,
    val position: Long,
    val isYouTube: Boolean,
    val updatedAt: Long
)

fun DataSnapshot.toContinueWatchingEntry(roomCode: String): ContinueWatchingEntry? {
    val videoUrl = child("videoUrl").getValue(String::class.java) ?: return null
    if (videoUrl.isEmpty()) return null
    return ContinueWatchingEntry(
        roomCode = roomCode,
        videoUrl = videoUrl,
        position = child("position").getValue(Long::class.java) ?: 0L,
        isYouTube = child("isYouTube").getValue(Boolean::class.java) ?: false,
        updatedAt = child("updatedAt").getValue(Long::class.java) ?: 0L
    )
}

/** Call whenever this user pushes or receives a playback update for a room. */
fun recordContinueWatching(
    usersRef: DatabaseReference,
    uid: String,
    roomCode: String,
    videoUrl: String,
    position: Long,
    isYouTube: Boolean
) {
    if (uid.isEmpty() || videoUrl.isEmpty()) return
    usersRef.child(uid).child("continueWatching").child(roomCode).updateChildren(
        mapOf(
            "videoUrl" to videoUrl,
            "position" to position,
            "isYouTube" to isYouTube,
            "updatedAt" to ServerValue.TIMESTAMP
        )
    )
}
