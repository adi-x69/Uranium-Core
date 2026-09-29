package com.example

data class RoomHistoryParticipant(
    val uid: String = "",
    val username: String = "",
    val avatarId: String = ""
)

data class RoomHistoryItem(
    val roomCode: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val participants: List<RoomHistoryParticipant> = emptyList(),
    val videoUrl: String = "",
    val videoTitle: String = ""
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
}
