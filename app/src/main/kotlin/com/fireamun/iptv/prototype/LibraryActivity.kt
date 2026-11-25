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
        if (playerOwnerSessionId == sessionId) updatePlayerCommands(commands)
    }
    private fun playerOwnerDisposed(sessionId: String) {
        if (playerOwnerSessionId == sessionId) {
            playerOwnerSessionId = null
            playerOwnerClose = null
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
    }
    override fun onStop() {
        if (!isChangingConfigurations) parental.onBackground()
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
                        onPresentation = { presentation = it; updatePlayerPresentation(it) },
                        onCommands = { acceptPlayerCommands(active.sessionId, it) },
                        onVideoBounds = ::updatePlayerBounds,
                    ) }
                    BackHandler(enabled = !pictureInPictureActive) { library.closePlayback() }
                    if (television) TvPlayback(title, status, library::closePlayback, { library.retryPlayback(active) }, surface)
                    else MobilePlayback(title, status, library::closePlayback, { library.retryPlayback(active) }, surface, hideChrome = pictureInPictureActive)
                }
            } else {
                val onAnalytics: (Boolean) -> Unit = { LegalConsent.setAnalytics(this, it); analyticsEnabled = it }
                BackHandler(enabled = state.canGoBack) { library.backToCatalog() }
                BackHandler(enabled = state.tab == com.fireamun.iptv.core.model.LibraryTab.SEARCH && !state.canGoBack) { library.closeSearch() }
                browserState.SaveableStateProvider("browser") { CompositionLocalProvider(LocalLockedCategories provides lockedCategories) {
                    LibraryBrowser(state, catalogue, television, (application as IptvApplication).artworkLoader,
                        library::selectTab, library::search, library::selectCategory, library::selectChannel, library::toggleFavourite, library::backToCatalog, library::closeSearch,
                        watch, library::playRecent) {
                        if (television) TvWelcome(state, { toggleAppLanguage() }, { chooseFile() }, library::importUrl, library::selectChannel, library::cancelImport, library::approveHttp, library::dismissHttp, { Appearance.toggle(this) }, library::importXtream, library::backToCatalog, { library.refreshSource { chooseFile(refreshing = true) } }, library::removeSavedSource, library::selectSource, library::renameSource, library::removeSource,
                            analyticsEnabled, onAnalytics, BuildConfig.VERSION_NAME, parentalSettings, guideSettings)
                        else MobileWelcome(state, { toggleAppLanguage() }, { chooseFile() }, library::importUrl, library::selectChannel, library::cancelImport, library::approveHttp, library::dismissHttp, { Appearance.toggle(this) }, library::importXtream, library::backToCatalog, { library.refreshSource { chooseFile(refreshing = true) } }, library::removeSavedSource, library::selectSource, library::renameSource, library::removeSource,
                            analyticsEnabled, onAnalytics, BuildConfig.VERSION_NAME, parentalSettings, guideSettings)
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
