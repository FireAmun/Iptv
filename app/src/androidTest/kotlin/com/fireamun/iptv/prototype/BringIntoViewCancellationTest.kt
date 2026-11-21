package com.fireamun.iptv.prototype

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fireamun.iptv.core.designsystem.bringIntoViewAfterFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The login and URL fields scroll themselves into view from a `LaunchedEffect` that waits a frame first. When
 * focus moves, recomposition cancels that effect in the same frame that ended its wait, and Compose's test clock
 * still resumes it normally. Before the fix the cancelled effect then issued its bring-into-view request; the
 * scroll container kept that request queued after its caller was gone and, the next time its scroll animation
 * ended by cancellation (CI hit this when the login dialog left), threw
 * `IllegalStateException: uncancelled requests present`.
 */
@RunWith(AndroidJUnit4::class)
class BringIntoViewCancellationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun effectCancelledInItsFrameLeavesNoRequestBehind() {
        val requester = BringIntoViewRequester()
        val scroll = ScrollState(0)
        var focused by mutableStateOf(true)
        var resumedAfterCancel = false
        lateinit var scope: CoroutineScope
        compose.mainClock.autoAdvance = false
        compose.setContent {
            scope = rememberCoroutineScope()
            // The same shape as the form fields: the effect restarts when focus changes and waits a frame first.
            LaunchedEffect(focused) { if (focused) requester.bringIntoViewAfterFrame() }
            // Proves the trigger still happens: an identical frame wait, cancelled in the same frame, resumes anyway.
            LaunchedEffect(focused) { if (focused) { withFrameNanos { }; resumedAfterCancel = !isActive } }
            Column(Modifier.size(100.dp).verticalScroll(scroll)) {
                Spacer(Modifier.height(400.dp))
                Box(Modifier.size(50.dp).bringIntoViewRequester(requester))
            }
        }
        val holder = compose.runOnIdle {
            // A higher-priority scroll makes the container's bring-into-view animation end by cancellation as soon
            // as it starts, which is when it cancels and checks every request still queued.
            scope.launch { scroll.scroll(MutatePriority.PreventUserInput) { awaitCancellation() } }
        }
        // The effect is waiting for its frame; losing focus recomposes in that same frame and cancels it.
        compose.runOnIdle { focused = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("the cancelled effect was not resumed after its frame; this test no longer reaches the fix", resumedAfterCancel)
        holder.cancel()
        compose.waitForIdle()
    }
}
