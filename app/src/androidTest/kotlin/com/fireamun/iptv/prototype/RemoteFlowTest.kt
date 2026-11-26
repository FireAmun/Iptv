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
import com.fireamun.iptv.core.remote.RemoteActions
import com.fireamun.iptv.core.remote.RemoteKey
import com.fireamun.iptv.core.remote.RemoteRefusal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RemoteFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IptvApplication

    /** The phone side runs in the same process, so the TV screen is driven only by keys the phone sends. */
    @Test fun tvApprovesAPhoneThatThenNavigatesSwitchesChannelsAndIsRevoked() {
        val host = app.remoteHost
        val controller = app.remoteController
        host.addressOverride = "127.0.0.1"
        runBlocking {
            app.libraryStore.save(SourceSpec.file(), listOf("Alpha", "Beta", "Gamma").mapIndexed { index, title ->
                PlaylistEntry(Channel(index, "$title Channel", "News", ContentKind.LIVE), MediaAddress.parse("https://example.invalid/$index"))
            })
        }
        ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, TvActivity::class.java)).use {
            await("channel_0")
            compose.openDestination("nav_settings", true)
            compose.onNodeWithTag("library_list").performScrollToKey("remote")
            activate("remote_pair", tv = true)
            await("remote_qr")
            val link = host.state.value.server.offer!!.link.encode()
            assertFalse("The code carries no secret beyond its single-use nonce", link.contains("secret"))

            controller.pair(link)
            await("remote_pair_ask")
            compose.onNodeWithTag("remote_pair_ask").assertTextContains(RemoteHost.deviceName(instrumentation.targetContext), substring = true)
            activate("remote_allow", tv = true)
            await("remote_pair_done")
            compose.waitUntil(10_000) { controller.state.value.tvName != null }

            controller.connect()
            compose.waitUntil(10_000) { controller.state.value.connection == RemoteConnection.CONNECTED }
            assertEquals("Nothing plays yet", RemoteRefusal.NO_PLAYBACK, press(RemoteKey.CHANNEL_UP))
            // Back from the phone reaches the pairing dialog's own window.
            assertNull(press(RemoteKey.BACK))
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("remote_pairing").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("library_list").performScrollToKey("remote")
            await("remote_phone_0")

            compose.openDestination("nav_live", true)
            activate("channel_0", tv = true)
            // The list leaves composition during playback, so these titles come from the player.
            await("playback_status")
            awaitText("Alpha Channel")
            assertNull(press(RemoteKey.CHANNEL_UP))
            awaitText("Beta Channel")
            assertNull(press(RemoteKey.CHANNEL_DOWN))
            awaitText("Alpha Channel")
            assertEquals("Play/Pause reaches the player", null, press(RemoteKey.PLAY_PAUSE))
            assertNull(press(RemoteKey.BACK))
            await("channel_0")
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("playback_status").fetchSemanticsNodes().isEmpty() }

            host.revokeAll()
            compose.waitUntil(10_000) { controller.state.value.connection == RemoteConnection.OFFLINE && controller.state.value.problem == RemoteProblem.REVOKED }
            assertNull("A revoked phone forgets the TV", controller.state.value.tvName)
        }
    }

    @Test fun phoneScansPairsAndUsesTheRemoteScreen() {
        val host = app.remoteHost
        val controller = app.remoteController
        val pressed = CopyOnWriteArrayList<RemoteKey>()
        val fakeTv = RemoteActions { key -> pressed += key; if (key == RemoteKey.PLAY_PAUSE) RemoteRefusal.NO_PLAYBACK else null }
        host.addressOverride = "127.0.0.1"
        instrumentation.runOnMainSync { host.attach(fakeTv) }
        try {
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, MobileActivity::class.java)).use {
                await("library_list")
                compose.onNodeWithTag("library_list").performScrollToKey("remote")
                await("remote_scan")
                controller.pair("https://example.com/not-a-tv")
                await("remote_problem")

                instrumentation.runOnMainSync { host.startPairing() }
                compose.waitUntil(10_000) { host.state.value.server.offer != null }
                // What the scanner would return.
                controller.pair(host.state.value.server.offer!!.link.encode())
                compose.waitUntil(10_000) { text("remote_status").contains("Approve", ignoreCase = true) }
                assertEquals(RemoteHost.deviceName(instrumentation.targetContext), host.state.value.server.pending?.name)
                host.approve()
                await("remote_paired")
                compose.onNodeWithTag("remote_problem").assertDoesNotExist()

                compose.onNodeWithTag("remote_open").performScrollTo().performClick()
                await("remote_panel")
                compose.waitUntil(10_000) { text("remote_connection").contains("Connected", ignoreCase = true) }
                for (key in listOf("DOWN", "RIGHT", "OK")) compose.onNodeWithTag("remote_key_$key").performClick()
                compose.waitUntil(10_000) { pressed.size == 3 }
                assertEquals(listOf(RemoteKey.DOWN, RemoteKey.RIGHT, RemoteKey.OK), pressed.toList())
                compose.onNodeWithTag("remote_key_PLAY_PAUSE").performClick()
                compose.waitUntil(10_000) { text("remote_problem").contains("Nothing is playing") }

                compose.onNodeWithTag("remote_close").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("remote_panel").fetchSemanticsNodes().isEmpty() }
                compose.waitUntil(10_000) { host.state.value.server.connected.isEmpty() }
                compose.onNodeWithTag("remote_forget").performScrollTo().performClick()
                await("remote_scan")
                assertEquals("Forgetting on the phone leaves the TV's list to the TV", 1, host.state.value.server.phones.size)
            }
        } finally { instrumentation.runOnMainSync { host.detach(fakeTv) } }
    }

    /** No camera: the phone types the address the TV shows, both screens show one code, and the phone then types. */
    @Test fun tvPairsByComparingCodesAndTakesTypingFromThePhone() {
        val host = app.remoteHost
        val controller = app.remoteController
        host.addressOverride = "127.0.0.1"
        runBlocking {
            app.libraryStore.save(SourceSpec.file(), listOf("Alpha", "Beta").mapIndexed { index, title ->
                PlaylistEntry(Channel(index, "$title Channel", "News", ContentKind.LIVE), MediaAddress.parse("https://example.invalid/$index"))
            })
        }
        ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, TvActivity::class.java)).use {
            await("channel_0")
            compose.openDestination("nav_settings", true)
            compose.onNodeWithTag("library_list").performScrollToKey("remote")
            activate("remote_pair", tv = true)
            await("remote_pair_address")
            val port = host.state.value.server.offer!!.link.port
            assertTrue("The TV shows the address to type", text("remote_pair_address").startsWith("127.0.0.1"))
            captureSyntheticPreview(instrumentation, "tv-remote-pair-offer")

            assertTrue(controller.pairWithAddress("127.0.0.1:$port"))
            await("remote_pair_code")
            compose.waitUntil(10_000) { controller.state.value.code != null }
            assertEquals("Both screens show the same code", controller.state.value.code, text("remote_pair_code").replace(" ", ""))
            captureSyntheticPreview(instrumentation, "tv-remote-pair-compare")
            activate("remote_allow", tv = true)
            await("remote_pair_done")
            compose.waitUntil(10_000) { controller.state.value.tvName != null }
            activate("remote_pair_close", tv = true)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("remote_pairing").fetchSemanticsNodes().isEmpty() }

            controller.connect()
            compose.waitUntil(10_000) { controller.state.value.connection == RemoteConnection.CONNECTED }
            // A button has focus, not a text box: nothing is typed and the phone box empties again.
            instrumentation.runOnMainSync { controller.type("x") }
            compose.waitUntil(10_000) { controller.state.value.problem == RemoteProblem.NO_FIELD && controller.typing.value.isEmpty() }

            compose.openDestination("nav_search", true)
            await("catalogue_search")
            compose.onNodeWithTag("catalogue_search").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.waitUntil(5_000) { compose.onNodeWithTag("catalogue_search").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
            instrumentation.runOnMainSync { controller.type("Bex") }
            compose.waitUntil(10_000) { editable("catalogue_search") == "Bex" }
            instrumentation.runOnMainSync { controller.type("Be") }
            compose.waitUntil(10_000) { editable("catalogue_search") == "Be" }
            instrumentation.runOnMainSync { controller.type("Beta") }
            compose.waitUntil(10_000) { editable("catalogue_search") == "Beta" }
            assertNull(controller.state.value.problem)
            awaitText("Beta Channel")
            // Done starts a new line on the phone; the TV box keeps its text and later typing is added to it.
            instrumentation.runOnMainSync { controller.finishTyping(); controller.type(" قناة") }
            compose.waitUntil(10_000) { editable("catalogue_search") == "Beta قناة" }
        }
    }

    @Test fun phonePairsWithACodeAndTypesFromTheRemoteScreen() {
        val host = app.remoteHost
        val controller = app.remoteController
        val typed = CopyOnWriteArrayList<Pair<Int, String>>()
        val fakeTv = object : RemoteActions {
            override suspend fun perform(key: RemoteKey): RemoteRefusal? = null
            override suspend fun type(erase: Int, text: String): RemoteRefusal? =
                if ("?" in text) RemoteRefusal.NO_FIELD else { typed += erase to text; null }
        }
        host.addressOverride = "127.0.0.1"
        instrumentation.runOnMainSync { host.attach(fakeTv) }
        try {
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, MobileActivity::class.java)).use {
                await("library_list")
                compose.onNodeWithTag("library_list").performScrollToKey("remote")
                activate("remote_pair_with_code", tv = false)
                await("remote_code_pairing")
                compose.onNodeWithTag("remote_address").performTextInput("tv.local")
                // The list of found TVs can grow at any moment (this device announces the TV under test), moving the
                // button mid-tap; press it through its click action so the test does not depend on where it is.
                compose.onNodeWithTag("remote_address_connect").performSemanticsAction(SemanticsActions.OnClick)
                await("remote_problem")

                instrumentation.runOnMainSync { host.startPairing() }
                compose.waitUntil(10_000) { host.state.value.server.offer != null }
                val port = host.state.value.server.offer!!.link.port
                compose.onNodeWithTag("remote_address").performTextClearance()
                compose.onNodeWithTag("remote_address").performTextInput("127.0.0.1:$port")
                compose.onNodeWithTag("remote_address_connect").performSemanticsAction(SemanticsActions.OnClick)
                await("remote_phone_code")
                compose.waitUntil(10_000) { host.state.value.server.pending?.code != null }
                assertEquals("Both screens show the same code", host.state.value.server.pending!!.code, text("remote_phone_code").replace(" ", ""))
                captureSyntheticPreview(instrumentation, "phone-remote-pair-compare")
                host.approve()
                await("remote_paired")
                compose.onNodeWithTag("remote_code_pairing").assertDoesNotExist()

                compose.onNodeWithTag("remote_open").performScrollTo().performClick()
                await("remote_panel")
                compose.waitUntil(10_000) { text("remote_connection").contains("Connected", ignoreCase = true) }
                compose.onNodeWithTag("remote_keyboard").performScrollTo().performClick()
                await("remote_typing")
                compose.onNodeWithTag("remote_typing").performTextInput("Hi")
                compose.waitUntil(10_000) { typed.isNotEmpty() && typed.last() == (0 to "Hi") }
                compose.onNodeWithTag("remote_typing").performTextClearance()
                compose.waitUntil(10_000) { typed.last() == (2 to "") }
                // The TV refused: the phone box shows only what the TV took, and says why.
                compose.onNodeWithTag("remote_typing").performTextInput("?")
                compose.waitUntil(10_000) { text("remote_problem").contains("text box") && editable("remote_typing").isEmpty() }
                captureSyntheticPreview(instrumentation, "phone-remote-typing")
                assertEquals(listOf(0 to "Hi", 2 to ""), typed.toList())

                // With the phone keyboard open, the box and Close are still on screen.
                compose.onNodeWithTag("remote_typing").assertIsDisplayed()
                compose.onNodeWithTag("remote_close").assertIsDisplayed().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("remote_panel").fetchSemanticsNodes().isEmpty() }
            }
        } finally { instrumentation.runOnMainSync { host.detach(fakeTv) } }
    }

    private fun editable(tag: String): String = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
        ?.config?.let { if (SemanticsProperties.EditableText in it) it[SemanticsProperties.EditableText].text else "" }.orEmpty()
    private fun press(key: RemoteKey): RemoteRefusal? = runBlocking { app.remoteController.press(key) }
    private fun text(tag: String): String = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrElse(SemanticsProperties.Text) { emptyList() }?.joinToString { it.text }.orEmpty()
    private fun awaitText(value: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty() }
    private fun await(tag: String) = compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
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
