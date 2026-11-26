package com.fireamun.iptv.core.designsystem

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * The text box that has focus on the TV, so a paired phone can type into it. Text only goes in: nothing in the box
 * is ever read back to the phone, which matters for password boxes. Used on the main thread only.
 */
object RemoteTyping {
    class Target internal constructor(internal val read: () -> String, internal val write: (String) -> Unit)

    private var focused: Target? = null

    /** Erases [erase] characters at the end of the focused box, then adds [text]; false when no box has focus. */
    fun type(erase: Int, text: String): Boolean {
        val target = focused ?: return false
        var kept = target.read().let { it.dropLast(erase.coerceIn(0, it.length)) }
        // Never leave half of a character made of two code units.
        if (kept.isNotEmpty() && kept.last().isHighSurrogate()) kept = kept.dropLast(1)
        target.write(kept + text)
        return true
    }

    internal fun focus(target: Target, focused: Boolean) {
        if (focused) this.focused = target else if (this.focused === target) this.focused = null
    }
}

/**
 * Lets a paired phone type into this single-line box while it has focus. The box's own change handler still applies,
 * so length limits and validation are unchanged.
 */
fun Modifier.remoteTyping(value: String, onValueChange: (String) -> Unit): Modifier = composed {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val target = remember { RemoteTyping.Target({ current }, { change(it) }) }
    DisposableEffect(target) { onDispose { RemoteTyping.focus(target, false) } }
    onFocusChanged { RemoteTyping.focus(target, it.isFocused) }
}
