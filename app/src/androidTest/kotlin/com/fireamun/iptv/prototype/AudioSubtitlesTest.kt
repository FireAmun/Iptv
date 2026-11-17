package com.fireamun.iptv.prototype

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real Room -> catalogue -> detail -> Media3 path and owned loopback media. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioSubtitlesTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val appearance = object : org.junit.rules.ExternalResource() {
        private var original = true
        override fun before() { original = Appearance.isDark(instrumentation.targetContext) }
        override fun after() {
            instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, original) }
        }
    }
    @get:Rule(order = 3) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IptvApplication
    private var openedControl: String? = null

    @Test fun phoneSelectsAudioAndAutoWithoutRestart() = audio(MobileActivity::class.java, false)
    @Test fun tvSelectsAudioAndAutoWithoutRestart() = audio(TvActivity::class.java, true)
    @Test fun phoneRendersSubtitlesThenOffAndAutoPreserveAudio() = subtitles(MobileActivity::class.java, false)
    @Test fun tvRendersSubtitlesThenOffAndAutoPreserveAudio() = subtitles(TvActivity::class.java, true)
    @Test fun phoneArabicEmptySubtitlesAndNativeAudio() = single(MobileActivity::class.java, false)
    @Test fun tvArabicEmptySubtitlesAndNativeAudio() = single(TvActivity::class.java, true)
    @Test fun phoneStopStartPreservesChoicesAndDismissesDialog() = staleDialog(MobileActivity::class.java, false)
    @Test fun tvStopStartPreservesChoicesAndDismissesDialog() = staleDialog(TvActivity::class.java, true)
    @Test fun phoneOldDialogActionsCannotChangeNewGroupsInSamePlayer() = changedGroups(MobileActivity::class.java, false)
    @Test fun tvOldDialogActionsCannotChangeNewGroupsInSamePlayer() = changedGroups(TvActivity::class.java, true)
    @Test fun phoneSelectsSuppliedQualityAndAutoPreservesPlayback() = quality(MobileActivity::class.java, false)
    @Test fun tvSelectsSuppliedQualityAndAutoPreservesPlayback() = quality(TvActivity::class.java, true)
    @Test fun phoneSingleQualityIsHonestInArabicLightMode() = singleQuality(MobileActivity::class.java, false)
    @Test fun tvSingleQualityIsHonestInArabicLightMode() = singleQuality(TvActivity::class.java, true)

    private data class Choice(val group: TrackGroup, val index: Int, val format: Format)

    private fun quality(activity: Class<out Activity>, tv: Boolean) = playback(activity, tv, quality = true) { scenario ->
        val identity = read(scenario) { it }
        val item = read(scenario) { it.currentMediaItem }
        val audio = choose(scenario, "player_audio", C.TRACK_TYPE_AUDIO, 1, tv)
        val text = choose(scenario, "player_subtitles", C.TRACK_TYPE_TEXT, 0, tv)
        awaitPlayer(scenario) { it.currentCues.cues.any { cue -> cue.text.toString() == "Synthetic English subtitle" } }
        val formats = read(scenario) { choices(it, C.TRACK_TYPE_VIDEO) }
        assertEquals(setOf(180, 360), formats.map { it.format.height }.toSet())
        if (tv) {
            compose.onNodeWithTag("player_subtitles").performKeyInput { pressKey(Key.DirectionRight) }
            awaitFocused("player_quality", dialog = false)
        }
        for (index in formats.indices) {
            // Return to Auto so each TV menu opens at the same native focus origin.
            if (index > 0) { open("player_quality", tv); activate("track_auto", tv) }
            val chosen = choose(scenario, "player_quality", C.TRACK_TYPE_VIDEO, index, tv)
            read(scenario) {
                assertSame(identity, it)
                assertSame(item, it.currentMediaItem)
                assertFalse("Quality selection must preserve paused intent", it.playWhenReady)
                assertTrue(it.contentPosition in 5_500..7_000)
                it.play()
            }
            awaitPlayer(scenario) {
                val exo = it as ExoPlayer
                selected(it, chosen) && exo.videoFormat?.height == chosen.format.height &&
                    it.videoSize.height == chosen.format.height &&
                    (exo.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 && it.contentPosition > 6_250 &&
                    selected(it, audio) && selected(it, text)
            }
            read(scenario) { it.pause(); it.seekTo(6_000) }
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 5_500..7_000 }
        }
        open("player_quality", tv)
        compose.onNodeWithTag("track_option_1").assertIsSelected()
        captureSyntheticPreview(instrumentation, "quality-${if (tv) "tv" else "phone"}-owned-menu")
        activate("track_auto", tv)
        awaitPlayer(scenario) { !hasOverride(it, C.TRACK_TYPE_VIDEO) && selected(it, audio) && selected(it, text) }
        open("player_quality", tv)
        compose.onNodeWithTag("track_auto").assertIsSelected()
        cancel("player_quality", tv)
        read(scenario) { assertSame(identity, it); assertSame(item, it.currentMediaItem); assertFalse(it.playWhenReady); assertTrue(it.contentPosition in 5_500..7_000) }
        assertTrue(subtitlePixels(scenario) > 0)
    }

    private fun singleQuality(activity: Class<out Activity>, tv: Boolean) = playback(activity, tv, single = true, arabic = true) { scenario ->
        compose.onNodeWithTag("player_quality").assertTextEquals("الجودة")
        val only = read(scenario) { choices(it, C.TRACK_TYPE_VIDEO).single() }
        open("player_quality", tv)
        compose.onNodeWithTag("track_auto").assertTextContains("تلقائي", substring = true)
        compose.onNodeWithTag("track_single_quality").assertExists()
        compose.onNodeWithTag("track_option_0").assertTextContains("${only.format.width} × ${only.format.height}", substring = true)
        compose.onNodeWithTag("track_option_1").assertDoesNotExist()
        compose.onNodeWithTag("track_off").assertDoesNotExist()
        captureSyntheticPreview(instrumentation, "quality-${if (tv) "tv" else "phone"}-single-ar")
        activate("track_option_0", tv)
        awaitPlayer(scenario) { selected(it, only) }
        open("player_quality", tv)
        compose.onNodeWithTag("track_option_0").assertIsSelected()
        val staleAuto = compose.onNodeWithTag("track_auto").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        read(scenario) {
            val uri = it.currentMediaItem!!.localConfiguration!!.uri.buildUpon().path("/master.m3u8").build()
            it.setMediaItem(MediaItem.fromUri(uri)); it.prepare()
        }
        awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && choices(it, C.TRACK_TYPE_TEXT).isNotEmpty() }
        awaitGone("player_track_dialog")
        read(scenario) {
            val video = choices(it, C.TRACK_TYPE_VIDEO).single()
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(video.group, video.index)).build()
        }
        instrumentation.runOnMainSync { staleAuto() }
        read(scenario) { assertTrue("Obsolete quality Auto must not clear new-group selection", hasOverride(it, C.TRACK_TYPE_VIDEO)) }
    }

    private fun audio(activity: Class<out Activity>, tv: Boolean) = playback(activity, tv) { scenario ->
        val identity = read(scenario) { it }
        val videoOverride = read(scenario) {
            val video = choices(it, C.TRACK_TYPE_VIDEO).single()
            val override = TrackSelectionOverride(video.group, video.index)
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setOverrideForType(override).build()
            override
        }
        val second = choose(scenario, "player_audio", C.TRACK_TYPE_AUDIO, 1, tv)
        assertEquals("ar", second.format.language)
        awaitPlayer(scenario) { selected(it, second) && (it as ExoPlayer).audioFormat?.language == "ar" }
        val beforeOutput = read(scenario) {
            assertFalse("Selection must preserve paused intent", it.playWhenReady)
            val output = (it as ExoPlayer).audioDecoderCounters?.renderedOutputBufferCount ?: 0
            (output to it.contentPosition).also { _ -> it.play() }
        }
        awaitPlayer(scenario) {
            val exo = it as ExoPlayer
            exo.audioFormat?.language == "ar" &&
                (exo.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > beforeOutput.first + 2 &&
                it.contentPosition > beforeOutput.second + 250
        }
        read(scenario) { it.pause(); it.seekTo(6_000) }
        awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 5_500..7_000 }
        open("player_audio", tv)
        compose.onNodeWithTag("track_option_1").assertIsSelected()
        activate("track_auto", tv)
        awaitPlayer(scenario) { !hasOverride(it, C.TRACK_TYPE_AUDIO) && !it.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO) }
        open("player_audio", tv)
        compose.onNodeWithTag("track_auto").assertIsSelected()
        cancel("player_audio", tv)
        read(scenario) {
            assertSame(identity, it)
            assertTrue(it.contentPosition in 5_500..7_000)
            assertFalse(it.playWhenReady)
            assertEquals(videoOverride, it.trackSelectionParameters.overrides[videoOverride.mediaTrackGroup])
        }
    }

    private fun subtitles(activity: Class<out Activity>, tv: Boolean) = playback(activity, tv) { scenario ->
        val audio = choose(scenario, "player_audio", C.TRACK_TYPE_AUDIO, 1, tv)
        if (tv) {
            // Start at the restored audio opener and traverse to subtitles using a real remote key.
            compose.onNodeWithTag("player_audio").performKeyInput { pressKey(Key.DirectionRight) }
            awaitFocused("player_subtitles", dialog = false)
        }
        val text = choose(scenario, "player_subtitles", C.TRACK_TYPE_TEXT, 0, tv)
        awaitPlayer(scenario) { selected(it, text) && it.currentCues.cues.any { cue -> cue.text.toString() == "Synthetic English subtitle" } }
        compose.waitUntil(5_000) { subtitlePixels(scenario) > 0 }
        captureSyntheticPreview(instrumentation, "tracks-${if (tv) "tv" else "phone"}-rendered-owned-caption")
        open("player_subtitles", tv)
        compose.onNodeWithTag("track_option_0").assertIsSelected()
        activate("track_off", tv)
        awaitPlayer(scenario) { it.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) && it.currentCues.cues.isEmpty() && selected(it, audio) }
        compose.waitUntil(5_000) { subtitlePixels(scenario) == 0 }
        open("player_subtitles", tv)
        compose.onNodeWithTag("track_off").assertIsSelected()
        activate("track_auto", tv)
        awaitPlayer(scenario) { !hasOverride(it, C.TRACK_TYPE_TEXT) && !it.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) && selected(it, audio) }
        open("player_subtitles", tv)
        compose.onNodeWithTag("track_auto").assertIsSelected()
        cancel("player_subtitles", tv)
    }

    private fun single(activity: Class<out Activity>, tv: Boolean) = playback(activity, tv, single = true, arabic = true) { scenario ->
        compose.onNodeWithTag("player_audio").assertTextEquals("الصوت")
        compose.onNodeWithTag("player_subtitles").assertTextEquals("الترجمة")
        read(scenario) { assertEquals(1, choices(it, C.TRACK_TYPE_VIDEO).size); assertTrue(choices(it, C.TRACK_TYPE_TEXT).isEmpty()) }
        open("player_subtitles", tv)
        compose.onNodeWithTag("track_option_0").assertDoesNotExist()
        compose.onNodeWithTag("track_auto").assertExists()
        compose.onNodeWithTag("track_off").assertExists()
        compose.onNodeWithTag("track_auto").assertTextContains("تلقائي", substring = true)
        compose.onNodeWithTag("track_off").assertTextContains("إيقاف", substring = true)
        cancel("player_subtitles", tv)
        read(scenario) { assertTrue(choices(it, C.TRACK_TYPE_TEXT).isEmpty()); assertTrue(it.currentCues.cues.isEmpty()) }
    }

    private fun changedGroups(activity: Class<out Activity>, tv: Boolean) = playback(activity, tv) { scenario ->
        val identity = read(scenario) { it }
        open("player_audio", tv)
        val staleAuto = compose.onNodeWithTag("track_auto").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        cancel("player_audio", tv)
        open("player_subtitles", tv)
        val staleOff = compose.onNodeWithTag("track_off").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        read(scenario) { player ->
            val uri = player.currentMediaItem!!.localConfiguration!!.uri.buildUpon().path("/single.mp4").build()
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }
        awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && choices(it, C.TRACK_TYPE_AUDIO).size == 1 && choices(it, C.TRACK_TYPE_TEXT).isEmpty() }
        awaitGone("player_track_dialog")
        read(scenario) { player ->
            val choice = choices(player, C.TRACK_TYPE_AUDIO).single()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
        // Reproduce queued actions from the obsolete UI after a real same-player media change.
        instrumentation.runOnMainSync { staleAuto(); staleOff() }
        read(scenario) {
            assertSame(identity, it)
            assertTrue(hasOverride(it, C.TRACK_TYPE_AUDIO))
            assertFalse(C.TRACK_TYPE_TEXT in it.trackSelectionParameters.disabledTrackTypes)
        }
    }

    private fun staleDialog(activity: Class<out Activity>, tv: Boolean) = playback(activity, tv) { scenario ->
        val audio = choose(scenario, "player_audio", C.TRACK_TYPE_AUDIO, 1, tv)
        open("player_subtitles", tv)
        activate("track_off", tv)
        val old = read(scenario) { it }
        open("player_audio", tv)
        val staleAuto = compose.onNodeWithTag("track_auto").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        awaitPlayer(scenario) { it !== old && it.playbackState == Player.STATE_READY && selected(it, audio) }
        awaitGone("player_track_dialog")
        instrumentation.runOnMainSync { staleAuto() }
        read(scenario) {
            assertTrue(hasOverride(it, C.TRACK_TYPE_AUDIO))
            assertTrue(C.TRACK_TYPE_TEXT in it.trackSelectionParameters.disabledTrackTypes)
        }
        open("player_audio", tv)
        compose.onNodeWithTag("track_option_1").assertIsSelected()
        cancel("player_audio", tv)
    }

    private fun playback(activity: Class<out Activity>, tv: Boolean, single: Boolean = false, arabic: Boolean = false, quality: Boolean = false, block: (ActivityScenario<Activity>) -> Unit) {
        instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, !arabic) }
        if (arabic) instrumentation.runOnMainSync {
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.targetContext.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags("ar")
            else androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags("ar"))
        }
        val assets = instrumentation.context.assets
        val media = assets.list("player-tracks")!!.filter { it.endsWith(".m3u8") || it.endsWith(".ts") || it.endsWith(".vtt") }
            .associate { "/$it" to assets.open("player-tracks/$it").use { stream -> stream.readBytes() } }.toMutableMap()
        media["/single.mp4"] = assets.open("synthetic-test.mp4").use { it.readBytes() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    val bytes = media[path] ?: return MockResponse(code = 404)
                    val range = request.headers["Range"]
                    val match = range?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it) }
                    if (range != null && match == null) return MockResponse(code = 416)
                    val start = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val end = match?.groupValues?.get(2)?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
                    if (start !in bytes.indices || end < start) return MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${bytes.size}").build()
                    val type = when {
                        path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
                        path.endsWith(".vtt") -> "text/vtt"
                        path.endsWith(".ts") -> "video/mp2t"
                        else -> "video/mp4"
                    }
                    return MockResponse.Builder().code(if (range == null) 200 else 206)
                        .addHeader("Content-Type", type).addHeader("Accept-Ranges", "bytes")
                        .apply { if (range != null) addHeader("Content-Range", "bytes $start-$end/${bytes.size}") }
                        .body(Buffer().write(bytes, start, end - start + 1)).build()
                }
            }
            server.start()
            val movieId = runBlocking {
                val saved = app.libraryStore.save(SourceSpec.file(), listOf(PlaylistEntry(
                    Channel(0, "Owned track fixture", "Synthetic", ContentKind.MOVIE),
                    MediaAddress.parse(server.url(if (single) "/single.mp4" else if (quality) "/quality-master.m3u8" else "/master.m3u8").toString()))))
                saved.entries.single().channel.id
            }
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity)).use { scenario ->
                scenario.onActivity {
                    val night = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    assertEquals(if (arabic) android.content.res.Configuration.UI_MODE_NIGHT_NO else android.content.res.Configuration.UI_MODE_NIGHT_YES, night)
                }
                compose.openDestination("nav_movies", tv)
                if (arabic) compose.onNodeWithTag("nav_movies").assertTextContains("الأفلام", substring = true)
                activate("channel_$movieId", tv)
                activate("detail_play", tv)
                awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && choices(it, C.TRACK_TYPE_VIDEO).isNotEmpty() }
                read(scenario) { it.pause(); it.seekTo(6_000) }
                awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 5_500..7_000 && !it.playWhenReady }
                block(scenario)
            }
        }
    }

    private fun choices(player: Player, type: Int): List<Choice> = player.currentTracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { Choice(group.mediaTrackGroup, it, group.getTrackFormat(it)) }
    }

    private fun selected(player: Player, choice: Choice): Boolean = player.currentTracks.groups.any {
        it.mediaTrackGroup == choice.group && it.isTrackSelected(choice.index)
    }
    private fun hasOverride(player: Player, type: Int) = player.trackSelectionParameters.overrides.values.any { it.type == type }

    private fun choose(scenario: ActivityScenario<Activity>, control: String, type: Int, index: Int, tv: Boolean): Choice {
        val choice = read(scenario) { choices(it, type)[index] }
        open(control, tv)
        compose.onNodeWithTag("track_option_$index").assertExists()
        if (tv) {
            // A fresh menu starts on Auto; reach the track through directional focus, not RequestFocus.
            compose.onNodeWithTag("track_auto").assertIsFocused().performKeyInput {
                repeat(index + if (type == C.TRACK_TYPE_TEXT) 2 else 1) { pressKey(Key.DirectionDown) }
            }
            awaitFocused("track_option_$index", dialog = true)
        }
        activate("track_option_$index", tv)
        awaitPlayer(scenario) { selected(it, choice) && it.trackSelectionParameters.overrides[choice.group]?.trackIndices == listOf(choice.index) }
        return choice
    }

    private fun open(tag: String, tv: Boolean) {
        activate(tag, tv)
        awaitTag("player_track_dialog")
        openedControl = tag
        if (tv) compose.waitUntil(5_000) {
            compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("player_track_dialog"))).fetchSemanticsNodes().isNotEmpty() && nativeWindowFocused(dialog = true)
        }
    }

    private fun cancel(control: String, tv: Boolean) {
        awaitNativeWindow(dialog = true, tag = control)
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        awaitDialogClosed(control, tv)
    }

    private fun awaitDialogClosed(control: String, tv: Boolean) {
        awaitGone("player_track_dialog")
        // Dismissal removes semantics before the parent-window focus effect restores the opener.
        if (tv) awaitFocused(control, dialog = false)
        openedControl = null
    }

    private fun awaitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitGone(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun awaitFocused(tag: String, dialog: Boolean) {
        try {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } } && nativeWindowFocused(dialog)
            }
        } catch (failure: ComposeTimeoutException) {
            recordFocusFailure(tag, dialog)
            throw failure
        }
    }
    private fun awaitNativeWindow(dialog: Boolean, tag: String) {
        try {
            compose.waitUntil(5_000) { nativeWindowFocused(dialog) }
        } catch (failure: ComposeTimeoutException) {
            recordFocusFailure(tag, dialog)
            throw failure
        }
    }
    private fun resumedDecor(): View? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).singleOrNull()?.window?.decorView

    private fun nativeWindowFocused(dialog: Boolean): Boolean {
        val activeApp = android.os.Build.VERSION.SDK_INT >= 29 || !dialog ||
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == instrumentation.targetContext.packageName
        var focused = false
        instrumentation.runOnMainSync {
            val parent = resumedDecor()
            focused = when {
                parent == null -> false
                !dialog -> parent.hasWindowFocus()
                android.os.Build.VERSION.SDK_INT >= 29 -> android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                    it !== parent && (it.layoutParams as? android.view.WindowManager.LayoutParams)?.type ==
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION && it.hasWindowFocus()
                }
                // API28 has no WindowInspector; require the app's active dialog and loss of parent focus.
                else -> activeApp && !parent.hasWindowFocus()
            }
        }
        return focused
    }

    /** Only fixture tags and native focus flags; never dump semantics, titles or media URLs. */
    private fun recordFocusFailure(tag: String, dialog: Boolean) {
        val knownTags = listOf("nav_movies", "detail_play", "player_audio", "player_subtitles", "player_quality",
            "track_auto", "track_off", "track_option_0", "track_option_1")
        val safeTag = tag.takeIf { it in knownTags || it.matches(Regex("channel_[0-9]+")) } ?: "unknown"
        runCatching {
            val focused = knownTags.filter { known -> compose.onAllNodesWithTag(known).fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.Focused) { false }
            } }
            android.util.Log.e("IptvTrackFocus", "target=$safeTag dialog=$dialog focusedTags=$focused nativeReady=${nativeWindowFocused(dialog)}")
            instrumentation.runOnMainSync {
                val parent = resumedDecor()
                android.util.Log.e("IptvTrackFocus", "parentPresent=${parent != null} parentFocused=${parent?.hasWindowFocus() == true}")
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.view.inspector.WindowInspector.getGlobalWindowViews().forEach {
                        android.util.Log.e("IptvTrackFocus", "nativeType=${(it.layoutParams as? android.view.WindowManager.LayoutParams)?.type} focused=${it.hasWindowFocus()} parent=${it === parent}")
                    }
                }
            }
            if (android.os.Build.VERSION.SDK_INT < 29) instrumentation.uiAutomation.windows.forEach {
                android.util.Log.e("IptvTrackFocus", "accessibilityType=${it.type} focused=${it.isFocused}")
            }
        }
        runCatching { captureSyntheticPreview(instrumentation, "tracks-focus-timeout-${safeTag.replace('_', '-')}") }
    }

    private fun activate(tag: String, tv: Boolean) {
        awaitTag(tag)
        val node = compose.onNodeWithTag(tag)
        node.assertIsEnabled()
        if (!tv && tag.startsWith("player_")) assertPlayerTargetClearOfSystemBars(node)
        val selectionControl = if (tag == "track_auto" || tag == "track_off" || tag.startsWith("track_option_"))
            requireNotNull(openedControl) { "Track selection must have an originating control" } else null
        if (tv) {
            val dialog = selectionControl != null || tag == "detail_play"
            awaitNativeWindow(dialog, tag)
            node.performSemanticsAction(SemanticsActions.RequestFocus)
            awaitFocused(tag, dialog)
            node.performKeyInput { pressKey(Key.DirectionCenter) }
        } else {
            node.assertIsDisplayed().performClick()
        }
        if (selectionControl != null) awaitDialogClosed(selectionControl, tv)
    }


    private fun playerView(activity: Activity): PlayerView {
        fun find(view: View): PlayerView? = when (view) {
            is PlayerView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
            else -> null
        }
        return find(activity.window.decorView) ?: error("Real PlayerView is not attached")
    }
    private fun <T> read(scenario: ActivityScenario<Activity>, block: (Player) -> T): T {
        var result: Result<T>? = null
        scenario.onActivity { result = runCatching { block(playerView(it).player ?: error("Player is not attached")) } }
        return result!!.getOrThrow()
    }
    private fun awaitPlayer(scenario: ActivityScenario<Activity>, predicate: (Player) -> Boolean) = compose.waitUntil(15_000) {
        runCatching { read(scenario, predicate) }.getOrDefault(false)
    }

    /** Draw only the actual live subtitle child, avoiding video-frame or controller pixels. */
    private fun subtitlePixels(scenario: ActivityScenario<Activity>): Int {
        var count = 0
        scenario.onActivity {
            val view = playerView(it).subtitleView ?: error("Native SubtitleView missing")
            assertTrue("Subtitle view must be visible", view.getGlobalVisibleRect(Rect()))
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val pixels = IntArray(view.width * view.height)
                bitmap.getPixels(pixels, 0, view.width, 0, 0, view.width, view.height)
                count = pixels.count { pixel -> pixel ushr 24 != 0 }
            } finally { bitmap.recycle() }
        }
        return count
    }
}
