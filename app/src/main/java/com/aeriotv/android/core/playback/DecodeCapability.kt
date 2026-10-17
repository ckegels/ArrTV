package com.aeriotv.android.core.playback

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * The tallest live picture this device decodes, for Dispatch More's
 * X-Dispatch-Max-Video (contract 8.2): 2160, 1080, 720 or 576.
 *
 * Only hardware decoders count. The software ones (c2.android.*, OMX.google.*)
 * accept 4K on paper on a Chromecast with Google TV HD and cannot play it in
 * real time, which is exactly the case this is for. A size counts when an
 * H.264 or HEVC decoder takes it at 25 fps, broadcast's lowest rate.
 */
object DecodeCapability {

    private val LADDER = listOf(3840 to 2160, 1920 to 1080, 1280 to 720, 720 to 576)

    fun maxVideoHeight(): Int = runCatching {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        val video = decoders.flatMap { info ->
            listOf("video/hevc", "video/avc")
                .filter { type -> info.supportedTypes.any { it.equals(type, ignoreCase = true) } }
                .mapNotNull { type ->
                    runCatching { info.getCapabilitiesForType(type).videoCapabilities }.getOrNull()
                        ?.let { info to it }
                }
        }
        val hardware = video.filter { (info, _) -> isHardware(info) }.ifEmpty { video }
        LADDER.firstOrNull { (w, h) ->
            hardware.any { (_, caps) -> runCatching { caps.areSizeAndRateSupported(w, h, 25.0) }.getOrDefault(false) }
        }?.second ?: 576
    }.getOrDefault(0)

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
        }
}
