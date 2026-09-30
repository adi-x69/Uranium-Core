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
import com.google.firebase.database.ServerValue
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
                        try {
                            if (event == Lifecycle.Event.ON_STOP) {
                                lastBackgroundAt = System.currentTimeMillis()
                            } else if (event == Lifecycle.Event.ON_START) {
                                val now = System.currentTimeMillis()
                                if (lastBackgroundAt > 0L && (now - lastBackgroundAt) >= SESSION_GAP_MS) {
                                    startNewSession()
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("BroadcastSession", "Lifecycle transition error: ${e.message}")
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

        try {
            // 1. Maintain server time offset
            val offsetRef = FirebaseDatabase
                .getInstance("https://uranium-tv-core-default-rtdb.firebaseio.com")
                .getReference(".info/serverTimeOffset")

            offsetListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        serverTimeOffset = snapshot.value.toSafeLong(0L)
                        evaluateQueue()
                    } catch (e: Exception) {
                        Log.w("BroadcastManager", "Error in offset listener: ${e.message}")
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            offsetRef.addValueEventListener(offsetListener!!)

            // 2. Listen to broadcasts
            val bcRef = db.child("broadcasts").orderByKey()
            broadcastsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
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
                    } catch (e: Exception) {
                        Log.w("BroadcastManager", "Error in broadcasts listener: ${e.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("BroadcastManager", "Broadcasts listener cancelled: ${error.message}")
                }
            }
            bcRef.addValueEventListener(broadcastsListener!!)
        } catch (e: Exception) {
            Log.w("BroadcastManager", "Error starting broadcast listeners: ${e.message}")
        }
    }

    fun stopListening() {
        isListening = false
        try {
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
        } catch (e: Exception) {
            Log.w("BroadcastManager", "Error stopping listeners: ${e.message}")
        }
    }

    private fun loadReceiptAndTarget(b: Broadcast) {
        val bcId = b.id
        try {
            // Load Receipt
            db.child("broadcastReceipts").child(bcId).child(uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
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
                        } catch (e: Exception) {
                            Log.w("BroadcastManager", "Error reading receipt: ${e.message}")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })

            // If specific audience, check target
            if (b.audience == "specific") {
                db.child("broadcastTargets").child(bcId).child(uid)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            try {
                                cachedTargets[bcId] = snapshot.exists()
                                evaluateQueue()
                            } catch (e: Exception) {
                                Log.w("BroadcastManager", "Error reading target: ${e.message}")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
        } catch (e: Exception) {
            Log.w("BroadcastManager", "Error in loadReceiptAndTarget: ${e.message}")
        }
    }

    /**
     * Evaluates cached broadcasts against receipts & rules, sorts by createdAt ascending,
     * and updates queue.
     */
    fun evaluateQueue() {
        if (!isListening || uid.isBlank()) return

        try {
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
        } catch (e: Exception) {
            Log.w("BroadcastManager", "Error in evaluateQueue: ${e.message}")
        }
    }

    private fun presentNextInQueue() {
        try {
            if (queue.isEmpty() || hasBlockingUi || isAdvancing) {
                currentBroadcast = null
                return
            }

            while (queue.isNotEmpty()) {
                val nextId = queue.firstOrNull() ?: break
                val candidate = cachedBroadcasts[nextId]
                if (candidate == null) {
                    queue.remove(nextId)
                    continue
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
                    return
                } else {
                    queue.remove(nextId)
                }
            }
            currentBroadcast = null
        } catch (e: Exception) {
            Log.w("BroadcastManager", "presentNextInQueue error: ${e.message}")
            currentBroadcast = null
        }
    }

    /**
     * Registers that the pop-up has appeared on the screen.
     * Counts display impression race-safely via atomic updateChildren with ServerValue.TIMESTAMP.
     */
    fun registerImpression(broadcast: Broadcast) {
        try {
            val bcId = broadcast.id
            if (BroadcastSession.isShownThisSession(bcId)) return

            // 1. Mark shown in session immediately to block rapid recompositions
            BroadcastSession.markShownThisSession(bcId)

            // 2. Increment shownCount in Firebase via safe updateChildren
            val username = UserProfileStorage.getCachedUsername(context, uid).ifEmpty { "User" }
            val name = UserProfileStorage.getCachedName(context, uid).ifEmpty { username }
            val avatarId = UserProfileStorage.getCachedAvatar(context, uid)

            val currentReceipt = cachedReceipts[bcId]
            val newShownCount = (currentReceipt?.shownCount ?: 0) + 1

            val receiptRef = db.child("broadcastReceipts").child(bcId).child(uid)
            val updates = mutableMapOf<String, Any?>()
            updates["uid"] = uid
            updates["username"] = username
            updates["name"] = name
            updates["avatarId"] = avatarId
            updates["shownCount"] = newShownCount
            if (currentReceipt?.firstShownAt == null) {
                updates["firstShownAt"] = ServerValue.TIMESTAMP
            }
            updates["lastShownAt"] = ServerValue.TIMESTAMP
            updates["seen"] = currentReceipt?.seen ?: false

            receiptRef.updateChildren(updates)
                .addOnSuccessListener {
                    cachedReceipts[bcId] = (currentReceipt ?: BroadcastReceipt(uid = uid)).copy(
                        shownCount = newShownCount,
                        lastShownAt = serverNow()
                    )
                }
                .addOnFailureListener { e ->
                    Log.w("BroadcastManager", "Impression write failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("BroadcastManager", "registerImpression error: ${e.message}")
        }
    }

    /**
     * Marks the broadcast as seen (seen = true, seenAt = TIMESTAMP if absent).
     */
    fun markSeen(broadcast: Broadcast) {
        try {
            val bcId = broadcast.id
            val cached = cachedReceipts[bcId]

            val username = UserProfileStorage.getCachedUsername(context, uid).ifEmpty { "User" }
            val name = UserProfileStorage.getCachedName(context, uid).ifEmpty { username }
            val avatarId = UserProfileStorage.getCachedAvatar(context, uid)

            val updates = mutableMapOf<String, Any?>(
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
        } catch (e: Exception) {
            Log.w("BroadcastManager", "markSeen error: ${e.message}")
        }
    }

    /**
     * Updates or toggles user reaction.
     */
    fun setReaction(broadcast: Broadcast, emoji: String?) {
        try {
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
                    try {
                        Toast.makeText(context, "Couldn't send reaction", Toast.LENGTH_SHORT).show()
                    } catch (t: Throwable) {
                        Log.w("BroadcastManager", "Toast error: ${t.message}")
                    }
                }
        } catch (e: Exception) {
            Log.w("BroadcastManager", "setReaction error: ${e.message}")
        }
    }

    /**
     * Advances to the next broadcast in queue with an intentional 350ms delay.
     */
    fun advance() {
        try {
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
        } catch (e: Exception) {
            Log.w("BroadcastManager", "advance error: ${e.message}")
            isAdvancing = false
            currentBroadcast = null
        }
    }
}
