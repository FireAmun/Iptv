package com.fireamun.iptv.core.playback

import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** Android16 Goldfish AVC retains the previous output size after adaptive resolution changes. */
@OptIn(UnstableApi::class)
internal fun playbackCodecSelector(): MediaCodecSelector = MediaCodecSelector { mimeType, secure, tunneling ->
    val codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
    if (Build.VERSION.SDK_INT != 36 || mimeType != MimeTypes.VIDEO_H264) codecs
    else codecs.map { codec ->
        if (codec.name == "c2.goldfish.h264.decoder" && codec.adaptive) {
            // Keep native selection/flags; recreate this decoder when the resolution changes.
            MediaCodecInfo.newInstance(codec.name, codec.mimeType, codec.codecMimeType,
                codec.capabilities, codec.hardwareAccelerated, codec.softwareOnly, codec.vendor,
                true, codec.secure)
        } else codec
    }
}
