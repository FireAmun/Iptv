package com.fireamun.iptv.prototype

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.fireamun.iptv.core.designsystem.AmarStartupScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.PlaybackStatus
import com.fireamun.iptv.core.playback.PlaybackSurface
import com.fireamun.iptv.core.playback.PlaybackCommands
import com.fireamun.iptv.core.playback.PlaybackPresentation
import com.fireamun.iptv.ui.mobile.MobileLegalGate
import com.fireamun.iptv.ui.mobile.MobilePlayback
import com.fireamun.iptv.ui.mobile.MobileWelcome
import com.fireamun.iptv.ui.tv.TvLegalGate
import com.fireamun.iptv.ui.tv.TvPlayback
import com.fireamun.iptv.ui.tv.TvWelcome
import java.io.IOException
import kotlinx.coroutines.CancellationException
import android.app.Instrumentation
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.designsystem.RemoteTyping
import com.fireamun.iptv.core.remote.RemoteActions
import com.fireamun.iptv.core.remote.RemoteKey
import com.fireamun.iptv.core.remote.RemoteRefusal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class LibraryActivity : AppCompatActivity() {
    protected abstract val television: Boolean
    protected open val pictureInPictureActive: Boolean get() = false
    protected open val pictureInPictureSupported: Boolean get() = false
    protected open fun requestPictureInPicture() {}
    protected open fun updatePlayerPresentation(presentation: PlaybackPresentation) {}
    protected open fun updatePlayerCommands(commands: PlaybackCommands?) {}
    protected open fun updatePlayerBounds(bounds: android.graphics.Rect) {}
    private var playerOwnerSessionId: String? = null
    private var playerOwnerClose: (() -> Unit)? = null
    protected fun closePlaybackFromPictureInPicture() {
        // ON_STOP may release Media3 before Activity.onStop (API29+ pre-stop callback).
        // Keep the concrete owner close until disposal, even after active commands disappear.
        val close = playerOwnerClose
        library.closePlayback()
        close?.invoke()
    }
    private fun acceptPlayerCommands(sessionId: String, commands: PlaybackCommands?) {
        if (commands != null) {
            playerOwnerSessionId = sessionId
            playerOwnerClose = commands.close
        }
        if (playerOwnerSessionId == sessionId) { remoteCommands = commands; updatePlayerCommands(commands) }
    }
    /** Latest player controls and state, for Play/Pause from a paired phone. */
    private var remoteCommands: PlaybackCommands? = null
    private var remotePresentation = PlaybackPresentation()
    /** Ids of the loaded live rows in list order, for channel up/down. */
    private var liveOrder: () -> List<Int> = { emptyList() }
    private val remoteHost by lazy { (application as IptvApplication).remoteHost }
    private val remoteController by lazy { (application as IptvApplication).remoteController }
    private val remoteActions = object : RemoteActions {
        override suspend fun perform(key: RemoteKey): RemoteRefusal? = performKey(key)

        /** Typed text goes to whichever app text box has focus, the same box the TV keyboard would type into. */
        override suspend fun type(erase: Int, text: String): RemoteRefusal? = withContext(Dispatchers.Main) {
            if (RemoteTyping.type(erase, text)) null else RemoteRefusal.NO_FIELD
        }
    }

    private suspend fun performKey(key: RemoteKey): RemoteRefusal? =
        when (key) {
            RemoteKey.PLAY_PAUSE -> withContext(Dispatchers.Main) {
                val commands = remoteCommands ?: return@withContext RemoteRefusal.NO_PLAYBACK
                if (remotePresentation.wantsToPlay) commands.pause() else commands.play()
                null
            }
            RemoteKey.CHANNEL_UP, RemoteKey.CHANNEL_DOWN -> withContext(Dispatchers.Main) {
                if (switchChannel(if (key == RemoteKey.CHANNEL_UP) 1 else -1)) null else RemoteRefusal.NO_PLAYBACK
            }
            RemoteKey.BACK -> goBack()
            RemoteKey.UP -> injectKey(KeyEvent.KEYCODE_DPAD_UP)
            RemoteKey.DOWN -> injectKey(KeyEvent.KEYCODE_DPAD_DOWN)
            RemoteKey.LEFT -> injectKey(KeyEvent.KEYCODE_DPAD_LEFT)
            RemoteKey.RIGHT -> injectKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            RemoteKey.OK -> injectKey(KeyEvent.KEYCODE_DPAD_CENTER)
        }

    /** Back goes to the focused window's back dispatcher, as predictive back no longer turns a Back key into it. */
    private suspend fun goBack(): RemoteRefusal? {
        if (Build.VERSION.SDK_INT < 29) return injectKey(KeyEvent.KEYCODE_BACK)
        return withContext(Dispatchers.Main) {
            val root = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() } ?: return@withContext RemoteRefusal.NOT_FOCUSED
            val dispatcher = root.findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher ?: return@withContext RemoteRefusal.FAILED
            dispatcher.onBackPressed()
            null
        }
    }

    /**
     * Phone keys reach whichever window of this app has focus (the activity or a dialog such as the parental PIN),
     * so every rule that applies to the TV remote applies to them too. Nothing is injected into other apps.
     */
    private suspend fun injectKey(code: Int): RemoteRefusal? {
        if (Build.VERSION.SDK_INT < 29) return withContext(Dispatchers.IO) {
            // Android 9 has no public window list; it lets an app inject only into its own focused window.
            try { Instrumentation().sendKeyDownUpSync(code); null }
            catch (_: SecurityException) { RemoteRefusal.NOT_FOCUSED }
            catch (_: RuntimeException) { RemoteRefusal.FAILED }
        }
        return withContext(Dispatchers.Main) {
            val root = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() } ?: return@withContext RemoteRefusal.NOT_FOCUSED
            // A key press leaves touch mode, as a real remote does, so focus becomes visible and movable.
            if (root.isInTouchMode) (root.findFocus() ?: root).requestFocusFromTouch()
            val time = SystemClock.uptimeMillis()
            val down = KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD)
            root.dispatchKeyEvent(down)
            root.dispatchKeyEvent(KeyEvent.changeTimeRepeat(KeyEvent.changeAction(down, KeyEvent.ACTION_UP), SystemClock.uptimeMillis(), 0))
            null
        }
    }

    /** Moves live playback to the next or previous loaded live row; false when that is not possible. */
    private fun switchChannel(step: Int): Boolean {
        val playing = library.playback.value ?: return false
        if (playing.kind != ContentKind.LIVE && playing.kind != ContentKind.UNKNOWN) return false
        val ids = liveOrder()
        val index = ids.indexOf(library.state.value.selectedId ?: return false)
        if (index < 0) return false
        library.selectChannel(ids.getOrNull(index + step) ?: return false)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP || event.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) && library.playback.value != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) switchChannel(if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP) 1 else -1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    private fun playerOwnerDisposed(sessionId: String) {
        if (playerOwnerSessionId == sessionId) {
            playerOwnerSessionId = null
            playerOwnerClose = null
            remoteCommands = null
            remotePresentation = PlaybackPresentation()
            updatePlayerCommands(null)
        }
        library.playbackDisposed(sessionId)
    }
    private var refreshingFile = false
    private val parental by lazy { (application as IptvApplication).parentalControls }
    private val guide by lazy { (application as IptvApplication).guideRefresher }
    override fun onStart() {
        super.onStart()
        parental.onForeground()
        if (television) remoteHost.attach(remoteActions)
    }
    override fun onStop() {
        if (!isChangingConfigurations) parental.onBackground()
        // The remote server never runs behind another app.
        if (television) remoteHost.detach(remoteActions)
        super.onStop()
    }
    private val library by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(LibraryViewModel(
                persistence = (application as IptvApplication).libraryStore,
                progressRecorder = (application as IptvApplication).progressRecorder,
                parental = parental,
                onImported = guide::imported,
            ))!!
        })[LibraryViewModel::class.java]
    }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val refresh = refreshingFile
        refreshingFile = false
        if (uri != null) {
            if (uri.scheme != "content") { library.reportError(ImportError.READ_FAILED); return@registerForActivityResult }
            val resolver = applicationContext.contentResolver
            val signal = CancellationSignal()
            library.importFile(cancelOpen = { signal.cancel() }, refreshing = refresh) {
                try {
                    resolver.openAssetFileDescriptor(uri, "r", signal)?.createInputStream() ?: throw IOException("Unable to open source")
                } catch (_: OperationCanceledException) {
                    throw CancellationException("Document opening cancelled")
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val systemSplash = installSplashScreen()
        super.onCreate(savedInstanceState)
        systemSplash.setOnExitAnimationListener { it.remove() }
        refreshingFile = savedInstanceState?.getBoolean("refreshingFile") ?: false
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            val state by library.state.collectAsStateWithLifecycle()
            val request by library.playback.collectAsStateWithLifecycle()
            val watch by library.continueWatching.collectAsStateWithLifecycle()
            val catalogue = library.catalogue.collectAsLazyPagingItems()
            val parentalState by parental.state.collectAsStateWithLifecycle()
            val pinPrompt by library.pinPrompt.collectAsStateWithLifecycle()
            val lockedCategories = remember(parentalState, state.activeSourceId, state.tab) {
                val kind = when (state.tab) { com.fireamun.iptv.core.model.LibraryTab.LIVE -> com.fireamun.iptv.core.model.ContentKind.LIVE
                    com.fireamun.iptv.core.model.LibraryTab.MOVIES -> com.fireamun.iptv.core.model.ContentKind.MOVIE
                    com.fireamun.iptv.core.model.LibraryTab.SERIES -> com.fireamun.iptv.core.model.ContentKind.SERIES; else -> null }
                if (kind == null) emptySet() else parental.hidden(state.activeSourceId).labels(kind)
            }
            val parentalSettings: @Composable () -> Unit = {
                ParentalSettings(parental, television, state.activeSourceId.takeIf { state.entryCount > 0 }, library::lockableCategories, library::forgetPin)
            }
            val guideSource = state.activeSourceId.takeIf { state.entryCount > 0 }
            // Opening the app or switching playlists checks whether the guide is due.
            LaunchedEffect(guideSource) { guide.select(guideSource) }
            val guideSettings: @Composable () -> Unit = { GuideSettings(guide, guideSource) }
            val remoteSettings: @Composable () -> Unit = { if (television) TvRemoteSettings(remoteHost) else PhoneRemoteSettings(remoteController) }
            SideEffect { liveOrder = { catalogue.itemSnapshotList.items.filter { it.kind == ContentKind.LIVE || it.kind == ContentKind.UNKNOWN }.map { it.id } } }
            val guideClock = rememberGuideClock()
            LaunchedEffect(Unit) { guide.updates.collect { library.guideUpdated(it) } }
            val browserState = rememberSaveableStateHolder()
            // A recreated activity has already consumed this launch, even if rotated mid-intro.
            var introFinished by remember { mutableStateOf(savedInstanceState != null) }
            val showStartup = !introFinished || state.restoring
            var termsAccepted by remember { mutableStateOf(LegalConsent.termsAccepted(this)) }
            var analyticsEnabled by remember { mutableStateOf(LegalConsent.analyticsEnabled(this)) }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = if (showStartup) true else !dark
                    isAppearanceLightNavigationBars = if (showStartup) false else !dark
                }
            }
            val active = request
            if (showStartup) {
                AmarStartupScreen(onIntroFinished = { introFinished = true })
            } else if (!termsAccepted) {
                // Shown once per Terms version, before any source can be added or played.
                val accept: (Boolean) -> Unit = { analytics -> LegalConsent.accept(this, analytics); analyticsEnabled = analytics; termsAccepted = true }
                if (television) TvLegalGate(accept, ::finish) { toggleAppLanguage() }
                else MobileLegalGate(accept, ::finish) { toggleAppLanguage() }
            } else if (active != null) {
                key(active.sessionId) {
                    var status by remember { mutableStateOf(PlaybackStatus.PREPARING) }
                    var programme by remember { mutableStateOf<com.fireamun.iptv.core.model.GuideSlot?>(null) }
                    val playingId = state.selectedId
                    // Live playback shows the programme on now, re-read as the guide clock ticks.
                    if (active.kind == com.fireamun.iptv.core.model.ContentKind.LIVE || active.kind == com.fireamun.iptv.core.model.ContentKind.UNKNOWN)
                        LaunchedEffect(playingId) {
                            snapshotFlow { guideClock.value }.collect { programme = playingId?.let { library.guideNow(it) }?.at(System.currentTimeMillis())?.first }
                        }
                    val programmeText = playerProgrammeText(programme)
                    var presentation by remember { mutableStateOf(PlaybackPresentation()) }
                    val title = state.selectedTitle ?: getString(R.string.player_title)
                    val surface: @Composable (Modifier) -> Unit = { modifier -> PlaybackSurface(
                        active,
                        { library.startPlaybackInstance(active.sessionId) },
                        { instanceId, checkpoint -> library.playbackCheckpoint(active.sessionId, instanceId, checkpoint) },
                        { status = it },
                        { playerOwnerDisposed(active.sessionId) },
                        modifier,
                        television = television,
                        inPictureInPicture = pictureInPictureActive,
                        onMinimize = if (!television && pictureInPictureSupported && presentation.canEnter) ({ requestPictureInPicture() }) else null,
                        onPresentation = { presentation = it; remotePresentation = it; updatePlayerPresentation(it) },
                        onCommands = { acceptPlayerCommands(active.sessionId, it) },
                        onVideoBounds = ::updatePlayerBounds,
                    ) }
                    BackHandler(enabled = !pictureInPictureActive) { library.closePlayback() }
                    if (television) TvPlayback(title, status, library::closePlayback, { library.retryPlayback(active) }, surface, programme = programmeText)
                    else MobilePlayback(title, status, library::closePlayback, { library.retryPlayback(active) }, surface, hideChrome = pictureInPictureActive, programme = programmeText)
                }
            } else {
                val onAnalytics: (Boolean) -> Unit = { LegalConsent.setAnalytics(this, it); analyticsEnabled = it }
                BackHandler(enabled = state.canGoBack) { library.backToCatalog() }
                BackHandler(enabled = state.tab == com.fireamun.iptv.core.model.LibraryTab.SEARCH && !state.canGoBack) { library.closeSearch() }
                browserState.SaveableStateProvider("browser") { CompositionLocalProvider(LocalLockedCategories provides lockedCategories, LocalGuideClock provides guideClock, LocalGuideSchedule provides library::schedule) {
                    LibraryBrowser(state, catalogue, television, (application as IptvApplication).artworkLoader,
                        library::selectTab, library::search, library::selectCategory, library::selectChannel, library::toggleFavourite, library::backToCatalog, library::closeSearch,
                        watch, library::playRecent) {
                        if (television) TvWelcome(state, { toggleAppLanguage() }, { chooseFile() }, library::importUrl, library::selectChannel, library::cancelImport, library::approveHttp, library::dismissHttp, { Appearance.toggle(this) }, library::importXtream, library::backToCatalog, { library.refreshSource { chooseFile(refreshing = true) } }, library::removeSavedSource, library::selectSource, library::renameSource, library::removeSource,
                            analyticsEnabled, onAnalytics, BuildConfig.VERSION_NAME, parentalSettings, guideSettings, remoteSettings)
                        else MobileWelcome(state, { toggleAppLanguage() }, { chooseFile() }, library::importUrl, library::selectChannel, library::cancelImport, library::approveHttp, library::dismissHttp, { Appearance.toggle(this) }, library::importXtream, library::backToCatalog, { library.refreshSource { chooseFile(refreshing = true) } }, library::removeSavedSource, library::selectSource, library::renameSource, library::removeSource,
                            analyticsEnabled, onAnalytics, BuildConfig.VERSION_NAME, parentalSettings, guideSettings, remoteSettings)
                    }
                } }
                if (pinPrompt) PinDialog(PinMode.ENTER, parental, television, onDone = library::pinAccepted, onDismiss = library::dismissPinPrompt,
                    onForgot = { library.dismissPinPrompt(); library.forgetPin() })
            }
            if (!showStartup && !pictureInPictureActive) ProgressOverlays(watch, television, library::continuePlayback, library::startPlaybackOver, library::cancelResumeChoice, library::dismissProgressError)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("refreshingFile", refreshingFile)
        super.onSaveInstanceState(outState)
    }
    private fun chooseFile(refreshing: Boolean = false) {
        refreshingFile = refreshing
        try { picker.launch(arrayOf("*/*")) }
        catch (_: ActivityNotFoundException) { refreshingFile = false; library.reportError(ImportError.NO_FILE_PICKER) }
        catch (_: SecurityException) { refreshingFile = false; library.reportError(ImportError.READ_FAILED) }
    }
}
