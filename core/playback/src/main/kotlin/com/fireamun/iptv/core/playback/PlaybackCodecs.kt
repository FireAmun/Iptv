package com.fireamun.iptv.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * The emulator-only Goldfish AVC decoder keeps the previous output size after an adaptive
 * resolution change (observed on API 34 and API 36). Real-device decoders are untouched.
 */
@OptIn(UnstableApi::class)
internal fun playbackCodecSelector(): MediaCodecSelector = MediaCodecSelector { mimeType, secure, tunneling ->
    val codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
    if (mimeType != MimeTypes.VIDEO_H264) codecs
    else codecs.map { codec ->
        if (codec.name == "c2.goldfish.h264.decoder" && codec.adaptive) {
            // Keep native selection/flags; recreate this decoder when the resolution changes.
            MediaCodecInfo.newInstance(codec.name, codec.mimeType, codec.codecMimeType,
                codec.capabilities, codec.hardwareAccelerated, codec.softwareOnly, codec.vendor,
                true, codec.secure)
        } else codec
    }
}
