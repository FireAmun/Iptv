package com.fireamun.iptv.prototype

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.openDestination(tag: String, tv: Boolean) {
    waitUntil(10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    val selected = onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false }
    if (!selected) {
        if (tv) {
            onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
            waitUntil(5000) { onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
            onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
        } else onNodeWithTag(tag).performClick()
        waitUntil(5000) { onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false } }
        if (tv && tag == "nav_settings") waitUntil(5000) { onAllNodesWithTag("language_switch").fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } } }
    }
}
