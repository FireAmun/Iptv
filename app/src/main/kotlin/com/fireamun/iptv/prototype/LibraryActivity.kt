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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    private val library by lazy { ViewModelProvider(this)[LibraryViewModel::class.java] }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (uri.scheme != "content") { library.reportError(ImportError.READ_FAILED); return@registerForActivityResult }
            val resolver = applicationContext.contentResolver
            val signal = CancellationSignal()
            library.importFile(cancelOpen = { signal.cancel() }) {
                try {
                    resolver.openAssetFileDescriptor(uri, "r", signal)?.createInputStream() ?: throw IOException("Unable to open source")
                } catch (_: OperationCanceledException) {
                    throw CancellationException("Document opening cancelled")
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            val state by library.state.collectAsStateWithLifecycle()
            val address by library.playback.collectAsStateWithLifecycle()
            val active = address
            if (active != null) {
                key(active) {
                    var status by remember { mutableStateOf(PlaybackStatus.PREPARING) }
                    var retry by remember { mutableIntStateOf(0) }
                    val title = state.channels.firstOrNull { it.id == state.selectedId }?.title ?: getString(R.string.player_title)
                    val surface: @Composable (Modifier) -> Unit = { PlaybackSurface(active, retry, { status = it }, it) }
                    BackHandler { library.closePlayback() }
                    if (television) TvPlayback(title, status, library::closePlayback, { retry++ }, surface)
                    else MobilePlayback(title, status, library::closePlayback, { retry++ }, surface)
                }
            } else {
                BackHandler(enabled = state.canGoBack) { library.backToCatalog() }
                if (television) TvWelcome(state, { toggleAppLanguage() }, ::chooseFile, library::importUrl, library::selectChannel, library::cancelImport, library::approveHttp, library::dismissHttp, { Appearance.toggle(this) }, library::importXtream, library::backToCatalog)
                else MobileWelcome(state, { toggleAppLanguage() }, ::chooseFile, library::importUrl, library::selectChannel, library::cancelImport, library::approveHttp, library::dismissHttp, { Appearance.toggle(this) }, library::importXtream, library::backToCatalog)
            }
        }
    }
    private fun chooseFile() {
        try { picker.launch(arrayOf("*/*")) }
        catch (_: ActivityNotFoundException) { library.reportError(ImportError.NO_FILE_PICKER) }
        catch (_: SecurityException) { library.reportError(ImportError.READ_FAILED) }
    }
}
