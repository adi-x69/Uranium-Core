package com.example

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject

data class RoomHistoryParticipant(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val avatarId: String = "iron_man",
    val joinedAt: Long = System.currentTimeMillis()
)

data class CreatedRoomRecord(
    val roomCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val creatorUid: String = "",
    val creatorName: String = "",
    val creatorUsername: String = "",
    val videoUrl: String = "",
    val isPlaying: Boolean = false,
    val vibe: String = "Default",
    val controlsUnlocked: Boolean = false,
    val participants: List<RoomHistoryParticipant> = emptyList()
)

object RoomHistoryManager {
    private const val PREFS_NAME = "uranium_room_history_prefs"
    private const val KEY_PREFIX_ROOMS = "created_rooms_"
    private const val DATABASE_URL = "https://uranium-tv-core-default-rtdb.firebaseio.com"

    private fun getDb() = FirebaseDatabase.getInstance(DATABASE_URL).reference

    /**
     * Record a new room created by the user.
     * Persisted both locally (so it survives any cloud cleanup) and in Firebase RTDB.
     */
    fun recordRoomCreated(
        context: Context,
        roomCode: String,
        creatorUid: String,
        creatorName: String,
        creatorUsername: String,
        createdAt: Long = System.currentTimeMillis()
    ) {
        if (roomCode.isBlank() || creatorUid.isBlank()) return

        val newRecord = CreatedRoomRecord(
            roomCode = roomCode,
            createdAt = createdAt,
            creatorUid = creatorUid,
            creatorName = creatorName,
            creatorUsername = creatorUsername,
            videoUrl = "",
            isPlaying = false,
            vibe = "Default",
            controlsUnlocked = false,
            participants = emptyList()
        )

        // 1. Save Locally
        saveLocally(context, creatorUid, newRecord)

        // 2. Save in Firebase user profile history node
        val userRoomRef = getDb().child("users").child(creatorUid).child("createdRooms").child(roomCode)
        val cloudData = mapOf(
            "roomCode" to roomCode,
            "createdAt" to createdAt,
            "creatorUid" to creatorUid,
            "creatorName" to creatorName,
            "creatorUsername" to creatorUsername,
            "videoUrl" to "",
            "isPlaying" to false,
            "vibe" to "Default"
        )
        userRoomRef.updateChildren(cloudData)
    }

    /**
     * Update participant join log for this created room.
     */
    fun recordParticipantJoined(
        context: Context,
        roomCode: String,
        hostUid: String,
        participant: RoomHistoryParticipant
    ) {
        if (roomCode.isBlank() || hostUid.isBlank() || participant.uid.isBlank()) return

        // 1. Update Firebase history
        val participantRef = getDb()
            .child("users")
            .child(hostUid)
            .child("createdRooms")
            .child(roomCode)
            .child("participants")
            .child(participant.uid)

        val partMap = mapOf(
            "uid" to participant.uid,
            "name" to participant.name,
            "username" to participant.username,
            "avatarId" to participant.avatarId,
            "joinedAt" to participant.joinedAt
        )
        participantRef.updateChildren(partMap)

        // 2. Update Local cache
        val currentRooms = getCreatedRooms(context, hostUid).toMutableList()
        val index = currentRooms.indexOfFirst { it.roomCode == roomCode }
        if (index != -1) {
            val existing = currentRooms[index]
            val updatedParticipants = existing.participants.filter { it.uid != participant.uid }.toMutableList()
            updatedParticipants.add(participant)
            currentRooms[index] = existing.copy(participants = updatedParticipants)
            saveAllLocally(context, hostUid, currentRooms)
        }
    }

    /**
     * Update video link for this room history entry.
     */
    fun recordVideoUpdated(
        context: Context,
        roomCode: String,
        hostUid: String,
        videoUrl: String,
        isPlaying: Boolean = true
    ) {
        if (roomCode.isBlank() || hostUid.isBlank()) return

        getDb().child("users").child(hostUid).child("createdRooms").child(roomCode)
            .updateChildren(mapOf("videoUrl" to videoUrl, "isPlaying" to isPlaying))

        val currentRooms = getCreatedRooms(context, hostUid).toMutableList()
        val index = currentRooms.indexOfFirst { it.roomCode == roomCode }
        if (index != -1) {
            val existing = currentRooms[index]
            currentRooms[index] = existing.copy(videoUrl = videoUrl, isPlaying = isPlaying)
            saveAllLocally(context, hostUid, currentRooms)
        }
    }

    /**
     * Retrieves all rooms created by this user from local storage.
     */
    fun getCreatedRooms(context: Context, uid: String): List<CreatedRoomRecord> {
        if (uid.isBlank()) return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PREFIX_ROOMS + uid, null) ?: return emptyList()

        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CreatedRoomRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val participantsList = mutableListOf<RoomHistoryParticipant>()
                if (obj.has("participants")) {
                    val pArray = obj.getJSONArray("participants")
                    for (p in 0 until pArray.length()) {
                        val pObj = pArray.getJSONObject(p)
                        participantsList.add(
                            RoomHistoryParticipant(
                                uid = pObj.optString("uid", ""),
                                name = pObj.optString("name", ""),
                                username = pObj.optString("username", ""),
                                avatarId = pObj.optString("avatarId", "iron_man"),
                                joinedAt = pObj.optLong("joinedAt", 0L)
                            )
                        )
                    }
                }

                list.add(
                    CreatedRoomRecord(
                        roomCode = obj.optString("roomCode", ""),
                        createdAt = obj.optLong("createdAt", 0L),
                        creatorUid = obj.optString("creatorUid", uid),
                        creatorName = obj.optString("creatorName", ""),
                        creatorUsername = obj.optString("creatorUsername", ""),
                        videoUrl = obj.optString("videoUrl", ""),
                        isPlaying = obj.optBoolean("isPlaying", false),
                        vibe = obj.optString("vibe", "Default"),
                        controlsUnlocked = obj.optBoolean("controlsUnlocked", false),
                        participants = participantsList
                    )
                )
            }
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLocally(context: Context, uid: String, record: CreatedRoomRecord) {
        val current = getCreatedRooms(context, uid).toMutableList()
        val existingIndex = current.indexOfFirst { it.roomCode == record.roomCode }
        if (existingIndex != -1) {
            current[existingIndex] = record
        } else {
            current.add(0, record)
        }
        saveAllLocally(context, uid, current)
    }

    fun saveAllLocally(context: Context, uid: String, records: List<CreatedRoomRecord>) {
        try {
            val array = JSONArray()
            records.forEach { r ->
                val obj = JSONObject().apply {
                    put("roomCode", r.roomCode)
                    put("createdAt", r.createdAt)
                    put("creatorUid", r.creatorUid)
                    put("creatorName", r.creatorName)
                    put("creatorUsername", r.creatorUsername)
                    put("videoUrl", r.videoUrl)
                    put("isPlaying", r.isPlaying)
                    put("vibe", r.vibe)
                    put("controlsUnlocked", r.controlsUnlocked)
                    val pArray = JSONArray()
                    r.participants.forEach { p ->
                        pArray.put(JSONObject().apply {
                            put("uid", p.uid)
                            put("name", p.name)
                            put("username", p.username)
                            put("avatarId", p.avatarId)
                            put("joinedAt", p.joinedAt)
                        })
                    }
                    put("participants", pArray)
                }
                array.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PREFIX_ROOMS + uid, array.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Synchronize room history with Firebase Realtime Database.
     * Merges cloud data into local persistence.
     */
    fun syncWithCloud(context: Context, uid: String, onDone: (List<CreatedRoomRecord>) -> Unit) {
        if (uid.isBlank()) {
            onDone(emptyList())
            return
        }

        val ref = getDb().child("users").child(uid).child("createdRooms")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cloudRooms = mutableListOf<CreatedRoomRecord>()
                for (child in snapshot.children) {
                    val code = child.child("roomCode").getValue(String::class.java) ?: child.key ?: continue
                    val createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L
                    val creatorUid = child.child("creatorUid").getValue(String::class.java) ?: uid
                    val creatorName = child.child("creatorName").getValue(String::class.java) ?: ""
                    val creatorUsername = child.child("creatorUsername").getValue(String::class.java) ?: ""
                    val videoUrl = child.child("videoUrl").getValue(String::class.java) ?: ""
                    val isPlaying = child.child("isPlaying").getValue(Boolean::class.java) ?: false
                    val vibe = child.child("vibe").getValue(String::class.java) ?: "Default"
                    val controlsUnlocked = child.child("controlsUnlocked").getValue(Boolean::class.java) ?: false

                    val participants = mutableListOf<RoomHistoryParticipant>()
                    for (pChild in child.child("participants").children) {
                        val pUid = pChild.child("uid").getValue(String::class.java) ?: pChild.key ?: continue
                        val pName = pChild.child("name").getValue(String::class.java) ?: ""
                        val pUsername = pChild.child("username").getValue(String::class.java) ?: ""
                        val pAvatar = pChild.child("avatarId").getValue(String::class.java) ?: "iron_man"
                        val pJoinedAt = pChild.child("joinedAt").getValue(Long::class.java) ?: 0L
                        participants.add(
                            RoomHistoryParticipant(
                                uid = pUid,
                                name = pName,
                                username = pUsername,
                                avatarId = pAvatar,
                                joinedAt = pJoinedAt
                            )
                        )
                    }

                    cloudRooms.add(
                        CreatedRoomRecord(
                            roomCode = code,
                            createdAt = createdAt,
                            creatorUid = creatorUid,
                            creatorName = creatorName,
                            creatorUsername = creatorUsername,
                            videoUrl = videoUrl,
                            isPlaying = isPlaying,
                            vibe = vibe,
                            controlsUnlocked = controlsUnlocked,
                            participants = participants
                        )
                    )
                }

                // Also check rooms where hostUid == uid from the active rooms node to ensure nothing is missed
                getDb().child("rooms").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(activeRoomsSnapshot: DataSnapshot) {
                        for (roomChild in activeRoomsSnapshot.children) {
                            val host = roomChild.child("hostUid").getValue(String::class.java) ?: ""
                            if (host == uid) {
                                val rCode = roomChild.key ?: continue
                                val vUrl = roomChild.child("videoUrl").getValue(String::class.java) ?: ""
                                val playing = roomChild.child("isPlaying").getValue(Boolean::class.java) ?: false
                                val cAt = roomChild.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                                val vibe = roomChild.child("vibe").getValue(String::class.java) ?: "Default"

                                // Check if already in cloudRooms
                                val existing = cloudRooms.find { it.roomCode == rCode }
                                if (existing != null) {
                                    val updated = existing.copy(videoUrl = vUrl, isPlaying = playing, vibe = vibe)
                                    cloudRooms[cloudRooms.indexOf(existing)] = updated
                                } else {
                                    cloudRooms.add(
                                        CreatedRoomRecord(
                                            roomCode = rCode,
                                            createdAt = cAt,
                                            creatorUid = uid,
                                            videoUrl = vUrl,
                                            isPlaying = playing,
                                            vibe = vibe
                                        )
                                    )
                                }
                            }
                        }

                        // Merge with local records
                        val localRooms = getCreatedRooms(context, uid)
                        val combined = (cloudRooms + localRooms)
                            .distinctBy { it.roomCode }
                            .sortedByDescending { it.createdAt }

                        saveAllLocally(context, uid, combined)
                        onDone(combined)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        val localRooms = getCreatedRooms(context, uid)
                        val combined = (cloudRooms + localRooms)
                            .distinctBy { it.roomCode }
                            .sortedByDescending { it.createdAt }
                        saveAllLocally(context, uid, combined)
                        onDone(combined)
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                onDone(getCreatedRooms(context, uid))
            }
        })
    }
}
