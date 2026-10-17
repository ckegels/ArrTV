package com.aeriotv.android.core.network

import android.content.Context
import android.util.Log
import com.aeriotv.android.core.data.db.dao.PlaylistDao
import com.aeriotv.android.core.data.sync.DispatcharrLiveUpdates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Wrong guide? Choose another" from the player (Dispatch More v216, contract
 * section 7a). The server lists the guides the channel could be on that have
 * something on right now, best first, and puts the channel on the one picked,
 * for every viewer. Everything is decided there; this finds the server, the
 * login and the channel for the stream being watched.
 *
 * Offered only when the stream's server said on its capabilities call that it
 * allows it, and only for a live channel it streams.
 */
@Singleton
class DispatchMoreGuides @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PlaylistDao,
    private val client: DispatcharrClient,
    private val auth: DispatcharrAuthBroker,
    private val liveUpdates: DispatcharrLiveUpdates,
) {
    /** A choice outlives the list it was made on: the list closes at the press. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun canChoose(streamUrl: String?): Boolean =
        DispatchMore.serverFor(streamUrl)?.guideChoice == true && DispatchMore.channelRef(streamUrl) != null

    suspend fun list(streamUrl: String, shown: List<Int> = emptyList()): DispatcharrClient.GuideAnswer =
        call(streamUrl) { base, key, path, channel -> client.fetchGuideChoices(base, key, path, channel, shown) }

    suspend fun choose(streamUrl: String, epgId: Int): DispatcharrClient.GuideAnswer =
        call(streamUrl) { base, key, path, channel -> client.chooseGuide(base, key, path, channel, epgId) }
            .also { Log.i(TAG, "guide choice $epgId: $it") }

    /**
     * Choose without waiting: the list is already closed, and a short notice
     * says what happened ("Guide changed to ORF 1 HD", or why not). After a
     * change the guide is fetched again once the server has read the new
     * guide's programmes, so the guide and the player's now/next follow.
     */
    fun chooseInBackground(streamUrl: String, option: DispatcharrClient.GuideOption) {
        scope.launch {
            val text = when (val answer = choose(streamUrl, option.epgId)) {
                is DispatcharrClient.GuideAnswer.Chosen -> {
                    liveUpdates.refreshGuideSoon("guide chosen in the player")
                    "Guide changed to ${answer.guide?.name?.ifBlank { null } ?: option.name}"
                }
                is DispatcharrClient.GuideAnswer.Refused -> answer.message
                is DispatcharrClient.GuideAnswer.Listed -> "Guide changed to ${option.name}"
            }
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun call(
        streamUrl: String,
        request: suspend (base: String, key: String, path: String, channel: String) -> DispatcharrClient.GuideAnswer,
    ): DispatcharrClient.GuideAnswer = withContext(Dispatchers.IO) {
        val server = DispatchMore.serverFor(streamUrl)?.takeIf { it.guideChoice }
            ?: return@withContext DispatcharrClient.GuideAnswer.Refused(403, "Changing the guide is switched off on the server")
        val base = DispatchMore.originOf(streamUrl)
            ?: return@withContext DispatcharrClient.GuideAnswer.Refused(404, "Not a channel of this server")
        val channel = DispatchMore.channelRef(streamUrl)
            ?: return@withContext DispatcharrClient.GuideAnswer.Refused(404, "Not a channel of this server")
        val playlist = dao.firstActive()?.takeIf { !it.apiKey.isNullOrBlank() }
            ?: return@withContext DispatcharrClient.GuideAnswer.Refused(0, "No Dispatcharr login to ask with")
        runCatching {
            auth.withApiKeyRetry(playlist.id) { key -> request(base, key, server.guideChoicePath, channel) }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            DispatcharrClient.GuideAnswer.Refused(0, it.message ?: "Could not reach the server")
        }
    }

    private companion object {
        const val TAG = "DispatchMoreGuides"
    }
}
