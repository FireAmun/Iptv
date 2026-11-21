package com.fireamun.iptv.core.designsystem

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Scrolls this requester's content into view after the next frame, once layout reflects the latest focus,
 * keyboard or window change.
 *
 * It stops when the calling effect was cancelled while it waited for that frame. A frame clock can complete
 * the frame for a coroutine that is cancelled in the same frame and still resume it normally (Compose's test
 * clock does). A bring-into-view request made from a cancelled coroutine stays queued in the scroll container
 * after its caller has gone, and Compose foundation (1.11 and 1.12) throws
 * `IllegalStateException: uncancelled requests present` the next time that container's scroll animation ends by
 * cancellation, for example when the container is removed or another scroll takes over.
 */
suspend fun BringIntoViewRequester.bringIntoViewAfterFrame() {
    withFrameNanos { }
    currentCoroutineContext().ensureActive()
    bringIntoView()
}
