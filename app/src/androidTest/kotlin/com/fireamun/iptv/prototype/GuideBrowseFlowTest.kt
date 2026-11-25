package com.fireamun.iptv.prototype

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.GuideKeys
import com.fireamun.iptv.core.data.GuideProgramme
import com.fireamun.iptv.core.data.ParsedGuide
import com.fireamun.iptv.core.data.PlaylistEntry
import com.fireamun.iptv.core.data.SourceSpec
import com.fireamun.iptv.core.model.Channel
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.MediaAddress
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GuideBrowseFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun phoneLiveRowsScheduleAndPlayerShowTheGuide() = exercise(MobileActivity::class.java, false)
    @Test fun tvLiveRowsScheduleAndPlayerShowTheGuideWithTheRemote() = exercise(TvActivity::class.java, true)

    private fun exercise(activity: Class<out Activity>, tv: Boolean) {
        val app = instrumentation.targetContext.applicationContext as IptvApplication
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val saved = runBlocking {
            app.libraryStore.save(SourceSpec.file(), listOf(
                PlaylistEntry(Channel(0, "Guide News", "News", ContentKind.LIVE), MediaAddress.parse("https://example.invalid/news"), guideId = "guide.news"),
                PlaylistEntry(Channel(1, "No Guide Channel", "News", ContentKind.LIVE), MediaAddress.parse("https://example.invalid/other")),
            )).also { library ->
                app.libraryStore.replaceGuide(library.sourceId!!, ParsedGuide(mapOf(GuideKeys.forId("guide.news") to "guide.news"), mapOf("guide.news" to listOf(
                    GuideProgramme(now - hour / 2, now + hour / 2, "Fixture bulletin", "Headlines and weather"),
                    GuideProgramme(now + hour / 2, now + 3 * hour / 2, "Fixture documentary"),
                    GuideProgramme(now + 30 * hour, now + 31 * hour, "Fixture tomorrow"),
                ))), now)
            }
        }
        val news = saved.entries[0].channel.id
        val other = saved.entries[1].channel.id
        ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity)).use {
            await("channel_$news")
            // Row text merges into the row's click target, so guide lines are read from the unmerged tree.
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("guide_lines_$news", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            for (text in listOf("Now: Fixture bulletin", "Fixture documentary"))
                compose.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("guide_lines_$news")), useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("guide_$other").assertDoesNotExist()

            activate("guide_$news", tv)
            await("guide_schedule")
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Fixture tomorrow")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Headlines and weather")).assertExists()
            compose.onAllNodes(hasText("On now", substring = true)).onFirst().assertExists()

            activate("guide_schedule_play", tv)
            await("playback_programme")
            compose.onNodeWithTag("playback_programme").assertTextContains("Fixture bulletin", substring = true)
        }
    }

    private fun await(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun activate(tag: String, tv: Boolean) {
        await(tag)
        val node = compose.onNodeWithTag(tag)
        if (tv) {
            node.performSemanticsAction(SemanticsActions.RequestFocus)
            compose.waitUntil(5000) { node.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
            node.performKeyInput { pressKey(Key.DirectionCenter) }
        } else { runCatching { node.performScrollTo() }; node.performClick() }
    }
}
