package com.example

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.util.Collections

/**
 * Process-level session tracker.
 * A session starts when the process starts, or when returning to foreground
 * after being in background for >= 10 minutes.
 * Within a single session, a broadcast is shown at most once.
 */
object BroadcastSession {
    private const val SESSION_GAP_MS = 10 * 60 * 1000L // 10 minutes

    var sessionId: Int = 1
        private set

    var lastBackgroundAt: Long = 0L
        private set

    val shownThisSession: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())

    private var observerAttached = false

    fun init() {
        if (observerAttached) return
        observerAttached = true

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        if (event == Lifecycle.Event.ON_STOP) {
                            lastBackgroundAt = System.currentTimeMillis()
                        } else if (event == Lifecycle.Event.ON_START) {
                            val now = System.currentTimeMillis()
                            if (lastBackgroundAt > 0L && (now - lastBackgroundAt) >= SESSION_GAP_MS) {
                                startNewSession()
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w("BroadcastSession", "Could not attach ProcessLifecycleObserver: ${e.message}")
            }
        }
    }

    fun startNewSession() {
        sessionId++
        shownThisSession.clear()
    }

    fun isShownThisSession(broadcastId: String): Boolean {
        return shownThisSession.contains(broadcastId)
    }

    fun markShownThisSession(broadcastId: String) {
        shownThisSession.add(broadcastId)
    }
}

/**
 * Manages fetching, caching, queueing, and reporting of Admin Announcements / Popups.
 */
class BroadcastManager(
    private val context: Context,
    private val uid: String
) {
    private val db = FirebaseDatabase
        .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
        .reference

    private var serverTimeOffset: Long = 0L
    private var offsetListener: ValueEventListener? = null
    private var broadcastsListener: ValueEventListener? = null

    // In-memory caches to avoid redundant queries
    private val cachedBroadcasts = mutableMapOf<String, Broadcast>()
    private val cachedReceipts = mutableMapOf<String, BroadcastReceipt>()
    private val cachedTargets = mutableMapOf<String, Boolean>()

    // Current candidate queue of broadcast IDs (sorted oldest first)
    private val queue = mutableListOf<String>()

    // Compose state for the active popup
    var currentBroadcast by mutableStateOf<Broadcast?>(null)
        private set

    // Active reactions state map for instantaneous optimistic UI
    private val myReactions = mutableStateMapOf<String, String?>()

    // Gate when other UI (like watch invite cards) is active on Home
    var hasBlockingUi by mutableStateOf(false)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isAdvancing = false

    init {
        BroadcastSession.init()
    }

    fun serverNow(): Long = System.currentTimeMillis() + serverTimeOffset

    fun myReactionFor(broadcastId: String): String? = myReactions[broadcastId]

    fun startListening() {
        if (isListening || uid.isBlank()) return
        isListening = true

        // 1. Maintain server time offset
        val offsetRef = FirebaseDatabase
            .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
            .getReference(".info/serverTimeOffset")

        offsetListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0L
                evaluateQueue()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        offsetRef.addValueEventListener(offsetListener!!)

        // 2. Listen to broadcasts
        val bcRef = db.child("broadcasts").orderByKey()
        broadcastsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val liveList = mutableListOf<Broadcast>()
                val existingIds = mutableSetOf<String>()

                for (child in snapshot.children) {
                    val b = Broadcast.fromSnapshot(child) ?: continue
                    existingIds.add(b.id)
                    cachedBroadcasts[b.id] = b

                    if (isBroadcastLive(b, serverNow())) {
                        liveList.add(b)
                    }
                }

                // Remove deleted broadcasts from memory & queue
                val toRemove = cachedBroadcasts.keys.filter { it !in existingIds }
                for (id in toRemove) {
                    cachedBroadcasts.remove(id)
                    cachedReceipts.remove(id)
                    cachedTargets.remove(id)
                    queue.remove(id)
                    if (currentBroadcast?.id == id) {
                        currentBroadcast = null
                    }
                }

                // Load receipts and targets for live items
                for (b in liveList) {
                    loadReceiptAndTarget(b)
                }

                evaluateQueue()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("BroadcastManager", "Broadcasts listener cancelled: ${error.message}")
            }
        }
        bcRef.addValueEventListener(broadcastsListener!!)
    }

    fun stopListening() {
        isListening = false
        offsetListener?.let {
            FirebaseDatabase.getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
                .getReference(".info/serverTimeOffset")
                .removeEventListener(it)
        }
        offsetListener = null

        broadcastsListener?.let {
            db.child("broadcasts").removeEventListener(it)
        }
        broadcastsListener = null
    }

    private fun loadReceiptAndTarget(b: Broadcast) {
        val bcId = b.id
        // Load Receipt
        db.child("broadcastReceipts").child(bcId).child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val receipt = if (snapshot.exists()) {
                        BroadcastReceipt.fromSnapshot(snapshot)
                    } else {
                        BroadcastReceipt(uid = uid)
                    }
                    cachedReceipts[bcId] = receipt
                    receipt.reaction?.let { r ->
                        if (!myReactions.containsKey(bcId)) {
                            myReactions[bcId] = r
                        }
                    }
                    evaluateQueue()
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        // If specific audience, check target
        if (b.audience == "specific") {
            db.child("broadcastTargets").child(bcId).child(uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cachedTargets[bcId] = snapshot.exists()
                        evaluateQueue()
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    /**
     * Evaluates cached broadcasts against receipts & rules, sorts by createdAt ascending,
     * and updates queue.
     */
    fun evaluateQueue() {
        if (!isListening || uid.isBlank()) return

        val now = serverNow()
        val candidates = mutableListOf<Broadcast>()

        for ((id, b) in cachedBroadcasts) {
            val receipt = cachedReceipts[id] // null if still loading
            val isTargeted = if (b.audience == "specific") {
                cachedTargets[id] ?: false
            } else {
                true
            }

            val alreadyShown = BroadcastSession.isShownThisSession(id)
            if (shouldShow(b, receipt, now, alreadyShown, isTargeted)) {
                candidates.add(b)
            }
        }

        candidates.sortBy { it.createdAt }

        queue.clear()
        queue.addAll(candidates.map { it.id })

        if (currentBroadcast == null && !isAdvancing && !hasBlockingUi) {
            presentNextInQueue()
        }
    }

    private fun presentNextInQueue() {
        if (queue.isEmpty() || hasBlockingUi) {
            currentBroadcast = null
            return
        }

        val nextId = queue.firstOrNull() ?: return
        val candidate = cachedBroadcasts[nextId]
        if (candidate == null) {
            queue.remove(nextId)
            presentNextInQueue()
            return
        }

        // Re-validate at display time
        val receipt = cachedReceipts[nextId]
        val isTargeted = if (candidate.audience == "specific") {
            cachedTargets[nextId] ?: false
        } else {
            true
        }
        val alreadyShown = BroadcastSession.isShownThisSession(nextId)

        if (shouldShow(candidate, receipt, serverNow(), alreadyShown, isTargeted)) {
            currentBroadcast = candidate
        } else {
            queue.remove(nextId)
            presentNextInQueue()
        }
    }

    /**
     * Registers that the pop-up has appeared on the screen.
     * Counts display impression race-safely and adds to session tracker.
     */
    fun registerImpression(broadcast: Broadcast) {
        val bcId = broadcast.id
        if (BroadcastSession.isShownThisSession(bcId)) return

        // 1. Mark shown in session immediately to block rapid recompositions
        BroadcastSession.markShownThisSession(bcId)

        // 2. Increment shownCount in Firebase via transaction
        val username = UserProfileStorage.getCachedUsername(context, uid).ifEmpty { "User" }
        val name = UserProfileStorage.getCachedName(context, uid).ifEmpty { username }
        val avatarId = UserProfileStorage.getCachedAvatar(context, uid)

        val receiptRef = db.child("broadcastReceipts").child(bcId).child(uid)
        receiptRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val value = currentData.value
                val map: MutableMap<String, Any?> = if (value is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    (value as Map<String, Any?>).toMutableMap()
                } else {
                    mutableMapOf(
                        "uid" to uid,
                        "username" to username,
                        "name" to name,
                        "avatarId" to avatarId,
                        "shownCount" to 0L,
                        "seen" to false
                    )
                }

                val currentCount = (map["shownCount"] as? Number)?.toLong() ?: 0L
                map["shownCount"] = currentCount + 1L

                if (map["firstShownAt"] == null) {
                    map["firstShownAt"] = ServerValue.TIMESTAMP
                }
                map["lastShownAt"] = ServerValue.TIMESTAMP
                map["uid"] = uid
                map["username"] = username
                map["name"] = name
                map["avatarId"] = avatarId

                currentData.value = map
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    Log.w("BroadcastManager", "Impression transaction failed: ${error.message}")
                } else if (committed && currentData != null) {
                    cachedReceipts[bcId] = BroadcastReceipt.fromSnapshot(currentData)
                }
            }
        })
    }

    /**
     * Marks the broadcast as seen (seen = true, seenAt = TIMESTAMP if absent).
     */
    fun markSeen(broadcast: Broadcast) {
        val bcId = broadcast.id
        val cached = cachedReceipts[bcId]

        val username = UserProfileStorage.getCachedUsername(context, uid).ifEmpty { "User" }
        val name = UserProfileStorage.getCachedName(context, uid).ifEmpty { username }
        val avatarId = UserProfileStorage.getCachedAvatar(context, uid)

        val updates = mutableMapOf<String, Any>(
            "seen" to true,
            "uid" to uid,
            "username" to username,
            "name" to name,
            "avatarId" to avatarId
        )

        if (cached?.seenAt == null) {
            updates["seenAt"] = ServerValue.TIMESTAMP
        }

        db.child("broadcastReceipts").child(bcId).child(uid).updateChildren(updates)
            .addOnSuccessListener {
                cachedReceipts[bcId] = cached?.copy(seen = true, seenAt = cached.seenAt ?: serverNow())
                    ?: BroadcastReceipt(uid = uid, seen = true, seenAt = serverNow())
            }
            .addOnFailureListener { e ->
                Log.w("BroadcastManager", "Failed to mark seen: ${e.message}")
            }
    }

    /**
     * Updates or toggles user reaction.
     */
    fun setReaction(broadcast: Broadcast, emoji: String?) {
        val bcId = broadcast.id
        val prevReaction = myReactions[bcId]

        // Optimistic UI update
        myReactions[bcId] = emoji
        markSeen(broadcast)

        val username = UserProfileStorage.getCachedUsername(context, uid).ifEmpty { "User" }
        val name = UserProfileStorage.getCachedName(context, uid).ifEmpty { username }
        val avatarId = UserProfileStorage.getCachedAvatar(context, uid)

        val updates = mutableMapOf<String, Any?>()
        if (emoji == null) {
            updates["reaction"] = null
            updates["reactedAt"] = null
        } else {
            updates["reaction"] = emoji
            updates["reactedAt"] = ServerValue.TIMESTAMP
            updates["seen"] = true
            updates["uid"] = uid
            updates["username"] = username
            updates["name"] = name
            updates["avatarId"] = avatarId
        }

        db.child("broadcastReceipts").child(bcId).child(uid).updateChildren(updates)
            .addOnFailureListener {
                // Revert
                myReactions[bcId] = prevReaction
                Toast.makeText(context, "Couldn't send reaction", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Advances to the next broadcast in queue with an intentional 350ms delay.
     */
    fun advance() {
        val closedId = currentBroadcast?.id
        currentBroadcast = null
        if (closedId != null) {
            queue.remove(closedId)
        }

        isAdvancing = true
        mainHandler.postDelayed({
            isAdvancing = false
            presentNextInQueue()
        }, 350L)
    }
}
