package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

object AppBufferingController {
    val isBuffering = mutableStateOf(false)
    val bufferingMessage = mutableStateOf("")

    fun setBuffering(buffering: Boolean, message: String = "") {
        isBuffering.value = buffering
        bufferingMessage.value = message
    }

    fun startBuffering(message: String = "") {
        setBuffering(true, message)
    }

    fun stopBuffering() {
        setBuffering(false, "")
    }

    /** Shows the buffering overlay with an optional status message. */
    fun show(message: String = "") {
        setBuffering(true, message)
    }

    /** Hides the buffering overlay. */
    fun hide() {
        setBuffering(false, "")
    }

    fun reportAction(vararg args: Any?) {}
    fun trigger(vararg args: Any?) {}
    fun runWithBuffering(vararg args: Any?, block: () -> Unit = {}) { block() }
    operator fun invoke(vararg args: Any?) {}
}

@Composable
fun GlobalNetworkBufferingOverlay(
    isBuffering: Boolean = false,
    isLoading: Boolean = false,
    message: String = "",
    onDismiss: () -> Unit = {},
    vararg args: Any?
) {
    // Harmless no-op backward compatibility overlay.
    // isLoading is accepted as an alias of isBuffering for older call sites.
}
