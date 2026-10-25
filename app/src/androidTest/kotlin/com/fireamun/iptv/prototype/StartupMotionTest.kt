package com.fireamun.iptv.prototype

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.designsystem.AmarStartupScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class StartupMotionTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createComposeRule(effectContext = object : MotionDurationScale { override val scaleFactor = 1f })
    @Test fun welcomeLogoMovesAndCompletionIsOnlyReportedOnce() {
        compose.mainClock.autoAdvance = false
        val completions = AtomicInteger()
        compose.setContent { AmarStartupScreen(animationsEnabled = true, onIntroFinished = { completions.incrementAndGet() }) }
        compose.mainClock.advanceTimeByFrame()
        val initial = compose.onNodeWithTag("startup_logo").fetchSemanticsNode().boundsInRoot.center.y
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Welcome to").assertIsDisplayed()
        compose.onNodeWithText("Amar Iptv").assertIsDisplayed()
        val moved = compose.onNodeWithTag("startup_logo").fetchSemanticsNode().boundsInRoot.center.y
        assertTrue("Logo should visibly bounce", kotlin.math.abs(initial - moved) > 1f)
        compose.waitForIdle()
        captureSyntheticPreview(InstrumentationRegistry.getInstrumentation(), "amar-welcome-motion")
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(1, completions.get()) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, completions.get()) }
    }
    @Test fun reducedMotionFinishesWithoutAnArtificialWait() {
        compose.mainClock.autoAdvance = false
        val completions = AtomicInteger()
        compose.setContent { AmarStartupScreen(animationsEnabled = false, onIntroFinished = { completions.incrementAndGet() }) }
        compose.runOnIdle { assertEquals(1, completions.get()) }
        compose.onNodeWithText("Amar Iptv").assertIsDisplayed()
    }
}
