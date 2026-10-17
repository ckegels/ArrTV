package com.aeriotv.android.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import java.net.URI
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * What the app tells a Dispatch More server about itself (a Dispatcharr fork;
 * the contract is its fork/arrTV-integration.md).
 *
 * WHY: the server closes the other channels a device still holds when that
 * device starts one ("Force Close"), which is what makes channel changes work
 * on single-connection IPTV accounts. It used to recognise a device by its
 * address and login, so two devices behind one VPN or NAT address were one
 * device and each channel start closed the other's stream. The app knows
 * which device it is and which channel it is leaving, so it says so.
 *
 * Everything here is extra: a server that did not answer the capabilities
 * call with app_integration >= 1 (stock Dispatcharr answers 404) is never
 * [register]ed, and a request to it carries nothing new. A Dispatch More
 * server whose admin left the features off ignores the headers.
 */
object DispatchMore {

    const val HEADER_DEVICE = "X-Dispatch-Device"
    const val HEADER_DEVICE_NAME = "X-Dispatch-Device-Name"
    const val HEADER_MULTIVIEW = "X-Dispatch-Multiview"
    const val HEADER_PREVIOUS = "X-Dispatch-Previous-Channel"
    const val HEADER_MAX_VIDEO = "X-Dispatch-Max-Video"
    const val DEFAULT_REPORT_PATH = "/api/core/app-reports/"
    const val DEFAULT_STALL_PATH = "/api/core/app-stall/"
    const val DEFAULT_GUIDE_CHOICE_PATH = "/api/core/app-guide/"

    /** What one server said on /api/core/capabilities/. */
    @Serializable
    data class Server(
        val reports: Boolean = false,
        val reportPath: String = DEFAULT_REPORT_PATH,
        val build: String = "",
        /** The admin's switches, for the log only: the headers are sent
         *  whatever they say, and the server decides what to do with them. */
        val devices: Boolean = false,
        val switchHints: Boolean = false,
        /** The server changes a channel's stream when this device says its
         *  picture stalls (v207, "Change stream when arrTV stutters"). */
        val stallSwitch: Boolean = false,
        val stallPath: String = DEFAULT_STALL_PATH,
        /** The server leaves a silent stream of a channel this device starts
         *  after 5 s and one check (v212, "Faster failover when arrTV starts a
         *  channel"): the app's own first moves then wait for it. */
        val fastFailover: Boolean = false,
        /** Anyone watching may put a channel on another guide (v216, "Let
         *  arrTV change a channel's guide"): the player offers "Wrong guide?
         *  Choose another" under the report. */
        val guideChoice: Boolean = false,
        val guideChoicePath: String = DEFAULT_GUIDE_CHOICE_PATH,
    )

    // The formats the server accepts; anything else it ignores.
    private val DEVICE_ID = Regex("^[A-Za-z0-9-]{8,64}$")
    private val CHANNEL_REF = Regex("^[A-Za-z0-9-]{1,80}$")
    private const val NAME_MAX = 80

    private val json = Json { ignoreUnknownKeys = true }

    /** Dispatch More servers by origin (scheme://host:port). A playlist's WAN
     *  and LAN addresses are both registered: streams go to whichever of the
     *  two answers. */
    private val servers = ConcurrentHashMap<String, Server>()

    @Volatile
    var deviceId: String? = null
        private set

    @Volatile
    var deviceName: String = ""
        private set

    /** The tallest picture this device decodes (2160 / 1080 / 720 / 576),
     *  0 until measured. See [setMaxVideo]. */
    @Volatile
    var maxVideo: Int = 0
        private set

    /** Set once at app start from the device's decoders. */
    fun setMaxVideo(height: Int) {
        maxVideo = height
    }

    /** Set while Multiview is on screen; every tile's request carries it. */
    @Volatile
    var multiviewSession: String? = null
        private set

    /** Called once at app start with the stored install id (or null, and the
     *  caller stores what this returns) and the device's name. */
    fun setDevice(storedId: String?, name: String): String {
        val id = storedId?.takeIf { DEVICE_ID.matches(it) } ?: UUID.randomUUID().toString()
        deviceId = id
        deviceName = headerSafe(name).take(NAME_MAX)
        return id
    }

    fun register(baseUrls: Collection<String?>, server: Server) {
        baseUrls.mapNotNull { originOf(it) }.forEach { servers[it] = server }
    }

    fun unregister(baseUrls: Collection<String?>) {
        baseUrls.mapNotNull { originOf(it) }.forEach { servers.remove(it) }
    }

    /** The server answered a stall with 403 (its switch went off): send none to
     *  it until the next capabilities call says otherwise. */
    fun stallRefused(url: String?) {
        val origin = originOf(url) ?: return
        servers.computeIfPresent(origin) { _, s -> s.copy(stallSwitch = false) }
    }

    /** The server behind [url], when it is a Dispatch More server. */
    fun serverFor(url: String?): Server? = originOf(url)?.let { servers[it] }

    /** The registered servers as stored between launches, and back. A launch
     *  may tune before the probe has answered again, and the first stream is
     *  the one that most needs to say which device it is. */
    fun snapshot(): String = json.encodeToString(servers.toMap())

    fun restore(stored: String?) {
        if (stored.isNullOrBlank()) return
        runCatching { json.decodeFromString<Map<String, Server>>(stored) }
            .getOrNull()
            ?.forEach { (origin, server) -> servers.putIfAbsent(origin, server) }
    }

    fun startMultiview() {
        if (multiviewSession == null) multiviewSession = UUID.randomUUID().toString()
    }

    fun endMultiview() {
        multiviewSession = null
    }

    /** The device headers for any request to [url]; empty for any other server. */
    fun deviceHeaders(url: String?): Map<String, String> {
        if (!com.aeriotv.android.core.playback.ArrTvOptimizations.identifyDevice) return emptyMap()
        val id = deviceId ?: return emptyMap()
        if (serverFor(url) == null) return emptyMap()
        return buildMap {
            put(HEADER_DEVICE, id)
            if (deviceName.isNotBlank()) put(HEADER_DEVICE_NAME, deviceName)
            // Contract 8.2: the server starts (and fails over) this device only
            // on streams it can decode -- a 4K stream never reaches a 1080p TV.
            if (com.aeriotv.android.core.playback.ArrTvOptimizations.sendMaxVideo && maxVideo > 0) {
                put(HEADER_MAX_VIDEO, maxVideo.toString())
            }
        }
    }

    /**
     * The headers for one live stream request: the device, the Multiview
     * session when the request is a tile's, and the channel being left when
     * this request is a channel change ([previousUrl] = the stream that was
     * actually playing, see [previousChannel]).
     */
    fun streamHeaders(
        url: String?,
        multiview: String? = null,
        previousUrl: String? = null,
    ): Map<String, String> {
        val device = deviceHeaders(url)
        if (device.isEmpty()) return device
        return buildMap {
            putAll(device)
            multiview?.takeIf { CHANNEL_REF.matches(it) }?.let { put(HEADER_MULTIVIEW, it) }
            previousChannel(url, previousUrl)?.let { put(HEADER_PREVIOUS, it) }
        }
    }

    /**
     * The channel [previousUrl] played, in the form it was opened with, when
     * a request for [url] leaves it: both on the same Dispatch More server and
     * not the same channel (a retry or a failover of one channel leaves
     * nothing). Null otherwise.
     */
    fun previousChannel(url: String?, previousUrl: String?): String? {
        if (previousUrl == null || url == null) return null
        val origin = originOf(url) ?: return null
        if (origin != originOf(previousUrl) || servers[origin] == null) return null
        val left = channelRef(previousUrl) ?: return null
        return left.takeIf { it != channelRef(url) }
    }

    /**
     * The channel a stream URL opens, as the server names it: the UUID of
     * /proxy/ts/stream/<uuid> (Direct Connect), or the number of
     * /live/<user>/<pass>/<id>[.ts|.m3u8] (Xtream). Null for anything else
     * (catch-up, timeshift, VOD), which is never a channel being left.
     */
    fun channelRef(url: String?): String? {
        val path = runCatching { URI(url ?: return null).rawPath }.getOrNull() ?: return null
        val parts = path.split('/').filter { it.isNotEmpty() }
        val ref = when {
            parts.size >= 4 && parts[parts.size - 4] == "proxy" &&
                parts[parts.size - 3] == "ts" && parts[parts.size - 2] == "stream" -> parts.last()
            parts.size >= 4 && parts[parts.size - 4] == "live" ->
                parts.last().substringBefore('.')
            else -> return null
        }
        return ref.takeIf { CHANNEL_REF.matches(it) }
    }

    const val HEADER_ALTERNATIVES = "X-Dispatch-Alternatives"

    /** How many other streams the server said each channel could switch to,
     *  by channel ref (from the last stream response for it). */
    private val alternatives = ConcurrentHashMap<String, Int>()

    /** The server's count for the channel [url] opens, or null when it said
     *  nothing (switched off, an older or stock server). */
    fun alternativesFor(url: String?): Int? = channelRef(url)?.let { alternatives[it] }

    /** On the player's live client: remembers X-Dispatch-Alternatives from
     *  each stream response of a Dispatch More server. */
    val responseInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val url = chain.request().url.toString()
        val ref = channelRef(url)
        if (ref != null && serverFor(url) != null) {
            val said = response.header(HEADER_ALTERNATIVES)?.trim()?.toIntOrNull()
            if (said != null && said >= 0) alternatives[ref] = said else alternatives.remove(ref)
        }
        response
    }

    /** Adds the device headers to every request an OkHttp client sends to a
     *  Dispatch More server (API calls, the live-updates socket). */
    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val headers = deviceHeaders(request.url.toString())
        if (headers.isEmpty()) {
            chain.proceed(request)
        } else {
            chain.proceed(
                request.newBuilder().apply {
                    headers.forEach { (k, v) -> if (request.header(k) == null) header(k, v) }
                }.build(),
            )
        }
    }

    internal fun originOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = when {
            uri.port != -1 -> uri.port
            scheme == "https" -> 443
            scheme == "http" -> 80
            else -> return null
        }
        return "$scheme://$host:$port"
    }

    /** A header value OkHttp accepts (tab or 0x20..0x7e only): accents are
     *  taken off letters, anything else that cannot be sent becomes a space.
     *  "Salón TV" is sent as "Salon TV" rather than refused. */
    internal fun headerSafe(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .map { if (it.code in 0x20..0x7e) it else ' ' }
            .joinToString("")
            .replace(Regex(" {2,}"), " ")
            .trim()
}
