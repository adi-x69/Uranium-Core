package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Global Buffering Controller for Uranium TV.
 * Displays the rotating Uranium Nuclear Radiation symbol buffering animation
 * and blocks touch interaction across all screens whenever an action takes time
 * or the user's internet is slow/lagging.
 */
object AppBufferingController {
    var isBuffering by mutableStateOf(false)
        private set

    var bufferingMessage by mutableStateOf("REACTOR BUFFERING...")
        private set

    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Show the global rotating uranium radiation buffering overlay.
     * Automatically applies a safety timeout so users are never stuck permanently.
     */
    fun show(message: String = "REACTOR BUFFERING...", maxTimeoutMs: Long = 10000L) {
        timeoutJob?.cancel()
        bufferingMessage = message
        isBuffering = true
        if (maxTimeoutMs > 0) {
            timeoutJob = scope.launch {
                delay(maxTimeoutMs)
                if (isBuffering) {
                    isBuffering = false
                }
            }
        }
    }

    /**
     * Dismiss the global buffering overlay immediately.
     */
    fun hide() {
        timeoutJob?.cancel()
        isBuffering = false
    }

    /**
     * Wraps a suspend block with the rotating nuclear radiation buffering animation.
     */
    suspend fun <T> runWithBuffering(
        message: String = "PROCESSING REACTOR PROTOCOL...",
        timeoutMs: Long = 10000L,
        block: suspend () -> T
    ): T {
        show(message, timeoutMs)
        return try {
            block()
        } finally {
            hide()
        }
    }
}
