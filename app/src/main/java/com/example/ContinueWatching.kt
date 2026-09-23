package com.example

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue

/**
 * 2 hours in milliseconds.
 * Video history disappears after 2 hours of video creation, or if the user leaves the room
 * for 2 hours continuously.
 */
const val CONTINUE_WATCHING_EXPIRY_MS = 2L * 60L * 60L * 1000L // 7,200,000 ms

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
    val updatedAt: Long,
    val createdAt: Long = 0L,
    val leftAt: Long = 0L
) {
    /**
     * Checks if this continue watching entry should be removed:
     * 1. 2 hours have passed since the video creation/addition.
     * 2. The user has left that room continuously for 2 hours.
     */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        // 1. Expire after two hours of video creation
        val creation = if (createdAt > 0L) createdAt else updatedAt
        if (creation > 0L && (now - creation >= CONTINUE_WATCHING_EXPIRY_MS)) {
            return true
        }

        // 2. Expire if user left the room for two hours continuously
        if (leftAt > 0L && (now - leftAt >= CONTINUE_WATCHING_EXPIRY_MS)) {
            return true
        }

        // Fallback: If leftAt was not explicitly recorded, but last active time is >= 2 hours ago
        if (leftAt == 0L && updatedAt > 0L && (now - updatedAt >= CONTINUE_WATCHING_EXPIRY_MS)) {
            return true
        }

        return false
    }
}

fun DataSnapshot.toContinueWatchingEntry(roomCode: String): ContinueWatchingEntry? {
    val videoUrl = child("videoUrl").getValue(String::class.java) ?: return null
    if (videoUrl.isEmpty()) return null
    return ContinueWatchingEntry(
        roomCode = roomCode,
        videoUrl = videoUrl,
        position = child("position").getValue(Long::class.java) ?: 0L,
        isYouTube = child("isYouTube").getValue(Boolean::class.java) ?: false,
        updatedAt = child("updatedAt").getValue(Long::class.java) ?: 0L,
        createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
        leftAt = child("leftAt").getValue(Long::class.java) ?: 0L
    )
}

/** Call whenever this user pushes or receives a playback update for a room. */
fun recordContinueWatching(
    usersRef: DatabaseReference,
    uid: String,
    roomCode: String,
    videoUrl: String,
    position: Long,
    isYouTube: Boolean,
    isNewVideo: Boolean = false
) {
    if (uid.isEmpty() || videoUrl.isEmpty()) return
    val cwRef = usersRef.child(uid).child("continueWatching").child(roomCode)
    cwRef.get().addOnSuccessListener { snapshot ->
        val existingCreatedAt = snapshot.child("createdAt").getValue(Long::class.java)
        val existingVideo = snapshot.child("videoUrl").getValue(String::class.java)
        val shouldSetCreatedAt = isNewVideo || existingCreatedAt == null || existingCreatedAt == 0L || existingVideo != videoUrl

        val updates = mutableMapOf<String, Any>(
            "videoUrl" to videoUrl,
            "position" to position,
            "isYouTube" to isYouTube,
            "updatedAt" to ServerValue.TIMESTAMP,
            "leftAt" to 0L // reset leftAt while active in the room
        )
        if (shouldSetCreatedAt) {
            updates["createdAt"] = ServerValue.TIMESTAMP
        }
        cwRef.updateChildren(updates)
    }.addOnFailureListener {
        cwRef.updateChildren(
            mapOf(
                "videoUrl" to videoUrl,
                "position" to position,
                "isYouTube" to isYouTube,
                "updatedAt" to ServerValue.TIMESTAMP,
                "leftAt" to 0L
            )
        )
    }
}

/** Call when user leaves a room (onDispose of RoomScreen / WatchScreen). */
fun recordRoomLeave(
    usersRef: DatabaseReference,
    uid: String,
    roomCode: String
) {
    if (uid.isEmpty() || roomCode.isEmpty()) return
    usersRef.child(uid).child("continueWatching").child(roomCode).updateChildren(
        mapOf("leftAt" to ServerValue.TIMESTAMP)
    )
}

/** Explicitly remove a room from continue watching history. */
fun removeContinueWatching(
    usersRef: DatabaseReference,
    uid: String,
    roomCode: String
) {
    if (uid.isEmpty() || roomCode.isEmpty()) return
    usersRef.child(uid).child("continueWatching").child(roomCode).removeValue()
}

