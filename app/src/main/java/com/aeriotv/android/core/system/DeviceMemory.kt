package com.aeriotv.android.core.system

import android.app.ActivityManager
import android.content.Context

/**
 * Low-memory profile: on constrained devices (Android's low-RAM flag, or
 * under 1.6 GB of RAM as Android reports it: a 1 GB box reports ~0.9 GB, a
 * Chromecast HD ~1.4 GB, while 2 GB boxes report ~1.8-1.9 GB and run fine
 * without it) the app keeps
 * less in memory. The guide holds a narrower time window (widened as the user
 * scrolls) and the image memory cache is smaller. A large guide in memory
 * (100K+ programmes, about 1 KB each) pushed such devices into killing other
 * apps and swapping, which made everything lag.
 *
 * Settings > General can force it on or off; the answer is read
 * synchronously (the image loader is built at startup), so it lives in a
 * plain SharedPreferences file rather than DataStore.
 */
object DeviceMemory {
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"

    private const val FILE = "device_profile"
    private const val KEY = "low_memory_mode"
    private const val CONSTRAINED_BELOW_BYTES = 1_600L * 1024L * 1024L

    @Volatile private var cached: Boolean? = null

    /** What Auto resolves to on this device. */
    fun detected(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.totalMem in 1 until CONSTRAINED_BELOW_BYTES
    }

    fun mode(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, AUTO) ?: AUTO

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply()
        cached = null
    }

    /** True when the low-memory profile applies (forced, or Auto on a constrained device). */
    fun isLow(context: Context): Boolean = cached ?: when (mode(context)) {
        ON -> true
        OFF -> false
        else -> detected(context)
    }.also { cached = it }
}
