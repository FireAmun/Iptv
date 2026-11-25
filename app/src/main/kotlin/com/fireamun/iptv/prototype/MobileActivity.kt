package com.fireamun.iptv.prototype

import android.app.PictureInPictureUiState
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fireamun.iptv.core.playback.PlaybackCommands
import com.fireamun.iptv.core.playback.PlaybackPresentation

class MobileActivity : LibraryActivity() {
    override val television = false
    private var pipVisible by mutableStateOf(false)
    private var pipAvailable by mutableStateOf(false)
    private val pictureInPicture by lazy {
        PhonePictureInPicture(this, { pipVisible = it }, { pipAvailable = it }, ::closePlaybackFromPictureInPicture)
    }

    override val pictureInPictureActive: Boolean get() = pipVisible
    override val pictureInPictureSupported: Boolean get() = pipAvailable
    override fun requestPictureInPicture() = pictureInPicture.enter()
    override fun updatePlayerPresentation(presentation: PlaybackPresentation) = pictureInPicture.update(presentation)
    override fun updatePlayerCommands(commands: PlaybackCommands?) = pictureInPicture.update(commands)
    override fun updatePlayerBounds(bounds: Rect) = pictureInPicture.update(bounds)

    override fun onStart() {
        super.onStart()
        pictureInPicture.onStart()
    }

    override fun onResume() {
        super.onResume()
        pictureInPicture.onResume()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pictureInPicture.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(inPictureInPicture: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPictureInPicture, newConfig)
        pictureInPicture.onModeChanged(inPictureInPicture)
    }

    @RequiresApi(31)
    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT >= 35) pictureInPicture.onTransitionChanged(pipState.isTransitioningToPip)
    }

    override fun onStop() {
        pictureInPicture.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        pictureInPicture.close()
        super.onDestroy()
    }
}
