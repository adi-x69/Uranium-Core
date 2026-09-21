package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

/**
 * Reliable multi-tier local and cloud persistence for user profile details (Name, Username, Avatar).
 * Ensures instant display without loading flickers and resilience against network latency.
 */
object UserProfileStorage {
    private const val TAG = "UserProfileStorage"
    private const val PREFS_NAME = "uranium_user_preferences"
    private const val KEY_PREFIX_NAME = "profile_name_"
    private const val KEY_PREFIX_USERNAME = "profile_username_"
    private const val KEY_PREFIX_AVATAR = "profile_avatar_"
    private const val KEY_LAST_KNOWN_NAME = "last_known_name"
    private const val KEY_LAST_KNOWN_USERNAME = "last_known_username"

    private const val DATABASE_URL = "https://uranium-tv-core-default-rtdb.firebaseio.com"

    fun getCachedName(context: Context, uid: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedForUid = if (uid.isNotBlank()) prefs.getString(KEY_PREFIX_NAME + uid, null) else null
        if (!savedForUid.isNullOrBlank()) return savedForUid.trim()

        val lastKnown = prefs.getString(KEY_LAST_KNOWN_NAME, null)
        if (!lastKnown.isNullOrBlank()) return lastKnown.trim()

        val auth = FirebaseAuth.getInstance()
        val authName = auth.currentUser?.displayName
        if (!authName.isNullOrBlank()) return authName.trim()

        return ""
    }

    fun getCachedUsername(context: Context, uid: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedForUid = if (uid.isNotBlank()) prefs.getString(KEY_PREFIX_USERNAME + uid, null) else null
        if (!savedForUid.isNullOrBlank()) return savedForUid.trim()

        val lastKnown = prefs.getString(KEY_LAST_KNOWN_USERNAME, null)
        if (!lastKnown.isNullOrBlank()) return lastKnown.trim()

        val auth = FirebaseAuth.getInstance()
        val email = auth.currentUser?.email
        if (!email.isNullOrBlank() && email.endsWith("@uraniumtv.local")) {
            return email.removeSuffix("@uraniumtv.local").trim()
        }

        return ""
    }

    fun saveNameLocally(context: Context, uid: String, name: String) {
        val clean = name.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (uid.isNotBlank()) {
                putString(KEY_PREFIX_NAME + uid, clean)
            }
            putString(KEY_LAST_KNOWN_NAME, clean)
            apply()
        }
    }

    fun saveUsernameLocally(context: Context, uid: String, username: String) {
        val clean = username.trim().lowercase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (uid.isNotBlank()) {
                putString(KEY_PREFIX_USERNAME + uid, clean)
            }
            putString(KEY_LAST_KNOWN_USERNAME, clean)
            apply()
        }
    }

    fun saveAvatarLocally(context: Context, uid: String, avatarId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (uid.isNotBlank()) {
                putString(KEY_PREFIX_AVATAR + uid, avatarId)
            }
            apply()
        }
    }

    fun getCachedAvatar(context: Context, uid: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = if (uid.isNotBlank()) prefs.getString(KEY_PREFIX_AVATAR + uid, null) else null
        return if (!saved.isNullOrBlank() && !saved.startsWith("avatar_")) saved.trim() else "iron_man"
    }

    /**
     * Persists name across:
     * 1. Local SharedPreferences (Immediate, 0ms latency)
     * 2. FirebaseAuth profile (displayName)
     * 3. Firebase Realtime Database (users/$uid/name and users/$uid/displayName)
     */
    fun saveNameEverywhere(
        context: Context,
        name: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onFailure?.invoke("Name cannot be empty")
            return
        }

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val uid = user?.uid ?: ""

        // 1. Save locally immediately
        saveNameLocally(context, uid, cleanName)

        // 2. Update Firebase Auth displayName
        try {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(cleanName)
                .build()
            user?.updateProfile(profileUpdates)
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating auth displayName", e)
        }

        // 3. Update Firebase RTDB if uid is valid
        if (uid.isNotBlank()) {
            val db = FirebaseDatabase.getInstance(DATABASE_URL).reference
            val updates = mapOf<String, Any>(
                "users/$uid/name" to cleanName,
                "users/$uid/displayName" to cleanName
            )
            db.updateChildren(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Name successfully updated in RTDB: $cleanName")
                    onSuccess?.invoke()
                }
                .addOnFailureListener { err ->
                    Log.e(TAG, "Failed to update name in RTDB", err)
                    // Still successful locally!
                    onSuccess?.invoke()
                }
        } else {
            onSuccess?.invoke()
        }
    }
}
