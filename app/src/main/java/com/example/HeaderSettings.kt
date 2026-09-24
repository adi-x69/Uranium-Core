package com.example

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CustomHeader(val name: String, val value: String)

/**
 * Stores the extension's "Pause / Resume" switch and the user's custom header list.
 * Values are kept in memory after the first load, so network threads can read them safely.
 */
object HeaderSettings {
    private const val PREFS = "header_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HEADERS = "headers"

    @Volatile var enabled: Boolean = true
        private set
    @Volatile var customHeaders: List<CustomHeader> = emptyList()
        private set
    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            enabled = prefs.getBoolean(KEY_ENABLED, true)
            customHeaders = parse(prefs.getString(KEY_HEADERS, "[]") ?: "[]")
            loaded = true
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        ensureLoaded(context)
        enabled = value
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun saveHeaders(context: Context, headers: List<CustomHeader>) {
        ensureLoaded(context)
        val clean = headers
            .map { CustomHeader(it.name.trim(), it.value.trim()) }
            .filter { it.name.isNotEmpty() && it.value.isNotEmpty() }
        customHeaders = clean
        val arr = JSONArray()
        clean.forEach { arr.put(JSONObject().put("name", it.name).put("value", it.value)) }
        prefs(context).edit().putString(KEY_HEADERS, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parse(json: String): List<CustomHeader> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            CustomHeader(o.optString("name"), o.optString("value"))
        }.filter { it.name.isNotEmpty() && it.value.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}
