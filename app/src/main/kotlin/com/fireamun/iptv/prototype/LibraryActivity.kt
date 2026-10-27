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
import com.fireamun.iptv.ui.mobile.MobilePlayback
import com.fireamun.iptv.ui.mobile.MobileWelcome
import com.fireamun.iptv.ui.tv.TvPlayback
import com.fireamun.iptv.ui.tv.TvWelcome
import java.io.IOException
import kotlinx.coroutines.CancellationException

abstract class LibraryActivity : AppCompatActivity() {
    protected abstract val television: Boolean
    private var refreshingFile = false
    private val library by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(LibraryViewModel(persistence = (application as IptvApplication).libraryStore))!!
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
            val address by library.playback.collectAsStateWithLifecycle()
            val catalogue = library.catalogue.collectAsLazyPagingItems()
            val browserState = rememberSaveableStateHolder()
            // A recreated activity has already consumed this launch, even if rotated mid-intro.
            var introFinished by remember { mutableStateOf(savedInstanceState != null) }
            val showStartup = !introFinished || state.restoring
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = if (showStartup) true else !dark
                    isAppearanceLightNavigationBars = if (showStartup) false else !dark
                }
            }
            val active = address
            if (showStartup) {
                AmarStartupScreen(onIntroFinished = { introFinished = true })
            } else if (active != null) {
                key(active) {
                    var status by remember { mutableStateOf(PlaybackStatus.PREPARING) }
                    var retry by remember { mutableIntStateOf(0) }
                    val title = state.selectedTitle ?: getString(R.string.player_title)
                    val surface: @Composable (Modifier) -> Unit = { PlaybackSurface(active, retry, { status = it }, it) }
                    BackHandler { library.closePlayback() }
                    if (television) TvPlayback(title, status, library::closePlayback, { retry++ }, surface)
                    else MobilePlayback(title, status, library::closePlayback, { retry++ }, surface)
                }
            } else {
                BackHandler(enabled = state.canGoBack) { library.backToCatalog() }
                BackHandler(enabled = state.tab == com.fireamun.iptv.core.model.LibraryTab.SEARCH && !state.canGoBack) { library.closeSearch() }
                browserState.SaveableStateProvider("browser") {
                    LibraryBrowser(state, catalogue, television, (application as IptvApplication).artworkLoader,
                        library::selectTab, library::search, library::selectCategory, library::selectChannel, library::toggleFavourite, library::backToCatalog, library::closeSearch) {
                        if (television) TvWelcome(state, { toggleAppLanguage() }, { chooseFile() }, library::importUrl, library::selectChannel, library::cancelImport, library::approveHttp, library::dismissHttp, { Appearance.toggle(this) }, library::importXtream, library::backToCatalog, { library.refreshSource { chooseFile(refreshing = true) } }, library::removeSavedSource, library::selectSource, library::renameSource, library::removeSource)
                        else MobileWelcome(state, { toggleAppLanguage() }, { chooseFile() }, library::importUrl, library::selectChannel, library::cancelImport, library::approveHttp, library::dismissHttp, { Appearance.toggle(this) }, library::importXtream, library::backToCatalog, { library.refreshSource { chooseFile(refreshing = true) } }, library::removeSavedSource, library::selectSource, library::renameSource, library::removeSource)
                    }
                }
            }
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
