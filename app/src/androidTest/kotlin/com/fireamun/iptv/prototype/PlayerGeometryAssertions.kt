package com.fireamun.iptv.prototype

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertTrue

/** Compose visibility alone does not establish that a target clears native system bars. */
internal fun assertPlayerTargetClearOfSystemBars(node: SemanticsNodeInteraction) {
    val bounds = node.fetchSemanticsNode().boundsInWindow
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
        val root = activity.window.decorView
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(root)).getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        assertTrue("Player target must clear bottom system controls", bounds.bottom <= root.height - insets.bottom + 1)
        assertTrue("Player target must clear left cutout/bars", bounds.left >= insets.left - 1)
        assertTrue("Player target must clear right cutout/bars", bounds.right <= root.width - insets.right + 1)
    }
}
