package com.aeriotv.android.feature.onboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aeriotv.android.feature.onboarding.components.SourceTypeCard
import com.aeriotv.android.ui.scale.subtext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A Dispatcharr server that answered on the local network. */
data class DiscoveredServer(
    val host: String,
    val port: Int,
    val version: String,
    /** Build label, e.g. "Dispatch More v198"; null on a stock build. */
    val build: String?,
) {
    val baseUrl: String get() = "http://$host:$port"
    val displayName: String get() = build?.takeIf { it.isNotBlank() } ?: "Dispatcharr"
}

/**
 * Finds Dispatcharr servers on the device's own subnet. `GET
 * /api/core/version/` answers without a login ({"version": "0.31.0",
 * "build": "Dispatch More v198", ...}), so every address in the subnet is
 * asked on [ports] in parallel with a short timeout; anything that answers
 * with a version is a server. Limits: only the device's own subnet (at most
 * 254 addresses: the /24 around the device when the subnet is larger) and
 * only the ports asked.
 */
object DispatcharrDiscovery {
    const val DEFAULT_PORT = 9191
    private const val TAG = "ServerDiscovery"
    private const val CONNECT_TIMEOUT_MS = 300
    private const val READ_TIMEOUT_MS = 1_000
    private const val PARALLEL = 48

    /** Calls [onFound] (on the caller's dispatcher) for each server as it answers. */
    suspend fun scan(
        context: Context,
        ports: List<Int> = listOf(DEFAULT_PORT),
        onFound: (DiscoveredServer) -> Unit,
    ) {
        val hosts = localSubnetHosts(context)
        if (hosts.isEmpty()) {
            Log.i(TAG, "no IPv4 LAN address; skipping the scan")
            return
        }
        val started = android.os.SystemClock.elapsedRealtime()
        val gate = Semaphore(PARALLEL)
        coroutineScope {
            for (host in hosts) for (port in ports) {
                launch {
                    val server = gate.withPermit { withContext(Dispatchers.IO) { probe(host, port) } }
                    if (server != null) onFound(server)
                }
            }
        }
        Log.i(
            TAG,
            "scanned ${hosts.size} addresses x ${ports.size} port(s) in " +
                "${android.os.SystemClock.elapsedRealtime() - started}ms",
        )
    }

    private fun probe(host: String, port: Int): DiscoveredServer? = runCatching {
        val conn = URL("http://$host:$port/api/core/version/").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) return@runCatching null
            // The answer is a few dozen bytes; never read more than 4 KB.
            val body = conn.inputStream.use { input ->
                val buf = ByteArray(4096)
                var n = 0
                while (n < buf.size) {
                    val r = input.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                String(buf, 0, n, Charsets.UTF_8)
            }
            val json = JSONObject(body)
            val version = json.optString("version").takeIf { it.isNotBlank() } ?: return@runCatching null
            val build = json.optString("build").takeIf { it.isNotBlank() && it != "null" }
            DiscoveredServer(host = host, port = port, version = version, build = build)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Every other host address in the device's Wi-Fi / Ethernet IPv4 subnet, capped to a /24. */
    private fun localSubnetHosts(context: Context): List<String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
        for (network in cm.allNetworks) {
            val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull() ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) continue
            val props = runCatching { cm.getLinkProperties(network) }.getOrNull() ?: continue
            for (la in props.linkAddresses) {
                val addr = la.address as? Inet4Address ?: continue
                if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                val own = addr.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
                val prefix = la.prefixLength.coerceIn(24, 30)
                val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
                val network0 = own and mask
                val broadcast = network0 or (mask.inv() and 0xFFFFFFFFL)
                return ((network0 + 1) until broadcast)
                    .filter { it != own }
                    .map { ip -> "${ip shr 24 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 8 and 0xFF}.${ip and 0xFF}" }
            }
        }
        return emptyList()
    }
}

/** Scans once when first shown; results arrive while the scan runs. */
@Composable
internal fun rememberServerDiscovery(): Pair<List<DiscoveredServer>, Boolean> {
    val context = LocalContext.current
    val found = remember { mutableStateListOf<DiscoveredServer>() }
    var scanning by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        DispatcharrDiscovery.scan(context.applicationContext) { server ->
            if (found.none { it.host == server.host && it.port == server.port }) {
                found.add(server)
                found.sortBy { s -> s.host.split('.').fold(0L) { acc, p -> acc * 256 + (p.toLongOrNull() ?: 0) } }
            }
        }
        scanning = false
    }
    return found to scanning
}

/**
 * Welcome screen block above everything else: a quiet "looking" line while
 * the scan runs, then one card per server found. Picking one opens the
 * Dispatcharr login with the address filled in. Nothing found: nothing shown.
 * The first server takes D-pad focus when it appears.
 */
@Composable
internal fun DiscoveredServersBlock(
    servers: List<DiscoveredServer>,
    scanning: Boolean,
    onPick: (DiscoveredServer) -> Unit,
) {
    if (servers.isEmpty() && !scanning) return
    val firstFocus = remember { FocusRequester() }
    val hasServers = servers.isNotEmpty()
    LaunchedEffect(hasServers) {
        if (hasServers) {
            delay(50)
            runCatching { firstFocus.requestFocus() }
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        servers.forEachIndexed { index, server ->
            SourceTypeCard(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .clickable { onPick(server) },
                icon = Icons.Outlined.Dns,
                title = server.displayName,
                subtitle = "Found on your network · Dispatcharr ${server.version} · ${server.host}:${server.port}",
                trailing = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        if (scanning) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Looking for Dispatcharr on your network…",
                    style = MaterialTheme.typography.bodySmall.subtext(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
