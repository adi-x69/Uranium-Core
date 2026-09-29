package com.example

import android.content.Context

data class RoomHistoryParticipant(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val avatarId: String = "",
    val joinedAt: Long = System.currentTimeMillis()
)

data class RoomHistoryItem(
    val roomCode: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val participants: List<RoomHistoryParticipant> = emptyList(),
    val videoUrl: String = "",
    val videoTitle: String = "",
    val isNewVideo: Boolean = false
)

object RoomHistoryManager {
    fun recordRoom(vararg args: Any?) {}
    fun recordParticipant(vararg args: Any?) {}
    fun saveRoom(vararg args: Any?) {}
    fun addParticipant(vararg args: Any?) {}
    fun removeParticipant(vararg args: Any?) {}
    fun updateRoom(vararg args: Any?) {}
    fun getHistory(vararg args: Any?): List<RoomHistoryItem> = emptyList()
    fun clearHistory(vararg args: Any?) {}
    operator fun invoke(vararg args: Any?) {}

    /** No-op hook: called whenever a participant joins a room, for future history tracking. */
    fun recordParticipantJoined(
        context: Context,
        roomCode: String,
        hostUid: String,
        participant: RoomHistoryParticipant
    ) {
        // Intentionally no-op for now; kept so callers compile and can be wired up later.
    }

    /** No-op hook: called whenever the active video for a room changes. */
    fun recordVideoUpdated(
        context: Context,
        roomCode: String,
        hostUid: String,
        videoUrl: String,
        isNewVideo: Boolean
    ) {
        // Intentionally no-op for now; kept so callers compile and can be wired up later.
    }
}
