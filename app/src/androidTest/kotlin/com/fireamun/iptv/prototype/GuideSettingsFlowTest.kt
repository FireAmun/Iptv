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
import com.fireamun.iptv.core.data.PlaylistEntry
import com.fireamun.iptv.core.data.SourceSpec
import com.fireamun.iptv.core.model.Channel
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.MediaAddress
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GuideSettingsFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun phoneRefreshGuideDownloadsAndMatchesTheSchedule() = exercise(MobileActivity::class.java, false)
    @Test fun tvRefreshGuideDownloadsAndMatchesTheScheduleWithTheRemote() = exercise(TvActivity::class.java, true)

    private fun exercise(activity: Class<out Activity>, tv: Boolean) {
        val app = instrumentation.targetContext.applicationContext as IptvApplication
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        fun at(offset: Long) = format.format(Date(now + offset))
        val xml = """<?xml version="1.0" encoding="UTF-8"?><tv>
            <channel id="fixture.news"><display-name>Fixture News</display-name></channel>
            <channel id="fixture.other"><display-name>Not in playlist</display-name></channel>
            <programme start="${at(-1_800_000)}" stop="${at(1_800_000)}" channel="fixture.news"><title>Fixture bulletin</title></programme>
            <programme start="${at(1_800_000)}" stop="${at(5_400_000)}" channel="fixture.news"><title>Fixture weather</title></programme>
            <programme start="${at(-1_800_000)}" stop="${at(1_800_000)}" channel="fixture.other"><title>Skipped</title></programme>
            </tv>"""
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().body(xml).build())
            server.start()
            val saved = runBlocking {
                app.libraryStore.save(SourceSpec.file(), listOf(
                    PlaylistEntry(Channel(0, "Fixture News HD", "News", ContentKind.LIVE), MediaAddress.parse("https://example.invalid/0"), guideId = "fixture.news"),
                    PlaylistEntry(Channel(1, "Fixture Film", "Films", ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/1")),
                )).also { app.libraryStore.setPlaylistGuideLink(it.sourceId!!, MediaAddress.parse(server.url("/guide.xml").toString())) }
            }
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity)).use {
                await("channel_0")
                compose.openDestination("nav_settings", tv)
                showGuide()
                compose.waitUntil(10_000) { status().contains("No guide", ignoreCase = true) }
                assertEquals("Automatic downloads stay off in instrumentation", 0, server.requestCount)
                activate("guide_refresh", tv)
                compose.waitUntil(20_000) { status().contains("2 programmes") }
                assertEquals("/guide.xml", server.takeRequest(5, TimeUnit.SECONDS)!!.target)
                val schedule = runBlocking { app.libraryStore.schedule(saved.sourceId!!, saved.entries[0].channel.id, now, now + 6 * 3_600_000L) }
                assertEquals(listOf("Fixture bulletin", "Fixture weather"), schedule.map { it.title })
                compose.onNodeWithTag("guide_link_mode").assertTextContains("automatic", substring = true, ignoreCase = true)
            }
        }
    }

    private fun status(): String = compose.onAllNodesWithTag("guide_status").fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrElse(SemanticsProperties.Text) { emptyList() }?.joinToString { it.text }.orEmpty()
    private fun showGuide() {
        compose.onNodeWithTag("library_list").performScrollToKey("guide")
        await("guide_settings")
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
