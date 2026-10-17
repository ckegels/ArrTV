package com.aeriotv.android.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.aeriotv.android.BuildConfig
import com.aeriotv.android.core.preferences.AppPreferences
import com.aeriotv.android.feature.dvr.LocalRecordingService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Real updater, bound as [UpdateManager] only in the `github` flavor.
 *
 * Flow: check (GitHub releases/latest) -> prompt -> download to
 * filesDir/updates -> verify (size, package, versionCode, OUR signer) ->
 * PackageInstaller session (attended) -> system dialog -> Android replaces
 * the app in place. App data survives automatically (same package + same
 * signing cert). The process is killed by the install; the user relaunches
 * from the home screen and PackageReplacedReceiver finishes bookkeeping.
 *
 * Two steps can kill the process mid-flow (the "Install unknown apps" grant
 * and the session commit), so a PendingUpdate JSON is persisted before each
 * and [resumePending] restores ReadyToInstall on the next launch.
 */
@Singleton
class GithubUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val checker: UpdateChecker,
) : UpdateManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * GitHub-channel eligibility, decided by SIGNING CERT (authoritative):
     * a build signed with the release/upload key is the sideload lineage and
     * can install GitHub APKs. The installer package is only a denylist --
     * Play-installed copies (re-signed by Play App Signing, so the cert check
     * would fail anyway) must never see the updater for policy reasons, and
     * file-manager sideloads report misleading installer names so an
     * allowlist would self-disable.
     */
    override val isEnabled: Boolean by lazy {
        if (!BuildConfig.UPDATER_ENABLED) return@lazy false
        val installer = installingPackageName()
        if (installer == "com.android.vending") return@lazy false
        ownSignerSha256().contains(UPLOAD_KEY_SHA256)
    }

    /** First auto-check of this process bypasses the 12h throttle. */
    private var checkedThisProcess = false

    override suspend fun check(manual: Boolean) {
        if (!isEnabled) return
        // Never clobber an in-flight download/install with a fresh check.
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Verifying,
            is UpdateState.Installing, is UpdateState.AwaitingInstallPermission,
            is UpdateState.ReadyToInstall,
            -> return
            else -> Unit
        }
        // Automatic checks are opt-in (Settings > App Updates, off by default).
        if (!manual && !appPreferences.updateAutoCheckOnce()) return
        val now = System.currentTimeMillis()
        if (!manual) {
            // The 12h throttle applies to foreground RETURNS only. The first
            // auto-check of each process always goes out, so launching the
            // app right after a release publishes surfaces the update prompt
            // immediately instead of waiting out a stamp from the previous
            // session (user request for 0.2.8).
            val last = appPreferences.updateLastCheckAtOnce()
            if (checkedThisProcess && now - last < AUTO_CHECK_INTERVAL_MS) return
        }
        checkedThisProcess = true
        when (val outcome = checker.fetchLatest(BuildConfig.VERSION_NAME)) {
            is UpdateChecker.Outcome.UpdateAvailable -> {
                appPreferences.setUpdateLastCheckAt(now)
                val skipped = appPreferences.updateSkippedVersionOnce()
                if (!manual && outcome.info.versionName == skipped) {
                    _state.value = UpdateState.UpToDate(now)
                } else {
                    _state.value = UpdateState.Available(outcome.info)
                }
            }
            UpdateChecker.Outcome.UpToDate -> {
                appPreferences.setUpdateLastCheckAt(now)
                _state.value = UpdateState.UpToDate(now)
            }
            // Tag published but APK still uploading: don't stamp the check
            // time so the next foreground retries soon.
            UpdateChecker.Outcome.NotReady -> if (manual) {
                _state.value = UpdateState.Error(
                    "A new release is being published. Try again in a minute.", null,
                )
            }
            is UpdateChecker.Outcome.Failed -> if (manual) {
                _state.value = UpdateState.Error("Update check failed: ${outcome.message}", null)
            } else {
                Log.i(TAG, "auto-check failed silently: ${outcome.message}")
            }
        }
    }

    override fun startDownload() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            try {
                val dir = File(context.filesDir, UPDATES_DIR).apply { mkdirs() }
                // Stale staged files are spent the moment a new download starts.
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "AerioTV-${info.versionName}.apk")
                if (dir.usableSpace < info.apkSizeBytes + FREE_SPACE_HEADROOM_BYTES) {
                    _state.value = UpdateState.Error(
                        "Not enough free space to download the update.", info,
                    )
                    return@launch
                }
                _state.value = UpdateState.Downloading(info, 0)
                downloadTo(target, info)
                _state.value = UpdateState.Verifying(info)
                val verified = verifyStagedApk(target, info.apkSizeBytes)
                    ?: return@launch // verifyStagedApk set the Error state
                // Best effort: the compile profile never blocks the update.
                val dmPath = stageDexMetadata(dir, info)
                appPreferences.setUpdatePendingJson(
                    json.encodeToString(PendingUpdate.serializer(), verified.copy(dmPath = dmPath)),
                )
                _state.value = UpdateState.ReadyToInstall(info)
                // Every download is somebody pressing Update (checks only
                // offer), so it goes straight on to the install: no second
                // "Ready to install" stop to press through.
                install()
            } catch (t: Throwable) {
                Log.w(TAG, "download failed", t)
                _state.value = UpdateState.Error(
                    "Download failed: ${t.message ?: t::class.simpleName}", info,
                )
            }
        }
    }

    override fun install() {
        val current = _state.value
        val info = when (current) {
            is UpdateState.ReadyToInstall -> current.info
            is UpdateState.AwaitingInstallPermission -> current.info
            else -> return
        }
        scope.launch {
            // A self-update kills this process; mid-recording that orphans the
            // in-flight DVR file (MediaStore row stays IS_PENDING and is
            // reaped). Hard-refuse rather than risk a silent recording loss.
            if (LocalRecordingService.isActive) {
                _state.value = UpdateState.Error(
                    "A recording is in progress. Finish or stop it, then install the update.",
                    info,
                )
                return@launch
            }
            val pending = readPending()
            val staged = pending?.let { File(it.apkPath) }
            if (pending == null || staged == null || !staged.isFile) {
                _state.value = UpdateState.Error(
                    "The downloaded update is missing. Download it again.", info,
                )
                appPreferences.setUpdatePendingJson("")
                return@launch
            }
            if (!context.packageManager.canRequestPackageInstalls()) {
                // The grant toggle commonly KILLS this process; PendingUpdate
                // is already persisted, so the relaunch resumes ReadyToInstall.
                _state.value = UpdateState.AwaitingInstallPermission(info)
                launchUnknownSourcesSettings()
                return@launch
            }
            try {
                _state.value = UpdateState.Installing(info)
                val dm = pending.dmPath?.let(::File)?.takeIf { it.isFile }
                // Kept for onInstallStatus: if Android rejects the session
                // because of the dex metadata, install again without it.
                retryWithoutDm = if (dm != null) staged else null
                commitSession(staged, dm)
            } catch (t: Throwable) {
                Log.w(TAG, "install commit failed", t)
                _state.value = UpdateState.Error(
                    "Install failed: ${t.message ?: t::class.simpleName}", info,
                )
            }
        }
    }

    override fun skipAvailableVersion() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.ReadyToInstall -> s.info
            is UpdateState.AwaitingInstallPermission -> s.info
            is UpdateState.Error -> s.info
            else -> null
        }
        scope.launch {
            info?.let { appPreferences.setUpdateSkippedVersion(it.versionName) }
            appPreferences.setUpdatePendingJson("")
            File(context.filesDir, UPDATES_DIR).listFiles()?.forEach { it.delete() }
            _state.value = UpdateState.Idle
        }
    }

    override fun dismissError() {
        if (_state.value is UpdateState.Error) _state.value = UpdateState.Idle
    }

    override fun refreshInstallPermission() {
        val awaiting = _state.value as? UpdateState.AwaitingInstallPermission ?: return
        if (context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.ReadyToInstall(awaiting.info)
        }
    }

    override suspend fun resumePending() {
        if (!isEnabled) return
        // Post-update bookkeeping (set by PackageReplacedReceiver).
        val completed = appPreferences.updateCompletedVersionOnce()
        if (completed.isNotBlank()) {
            appPreferences.setUpdateCompletedVersion("")
            // The version-keyed What's New sheet is the visible celebration.
            Log.i(TAG, "self-update to $completed completed")
        }
        val pending = readPending() ?: return
        val staged = File(pending.apkPath)
        val current = BuildConfig.VERSION_CODE.toLong()
        if (pending.versionCode <= current || !staged.isFile) {
            // Stale (update already applied some other way) or file lost.
            appPreferences.setUpdatePendingJson("")
            staged.delete()
            return
        }
        // Survived a process death between download and install (typically the
        // unknown-sources grant): re-verify and re-offer the install.
        val verified = verifyStagedApk(staged, pending.apkSizeBytes.takeIf { it > 0 })
        if (verified != null) {
            _state.value = UpdateState.ReadyToInstall(
                UpdateInfo(
                    versionName = pending.versionName,
                    notes = pending.notes,
                    apkUrl = "",
                    apkSizeBytes = pending.apkSizeBytes,
                ),
            )
        }
    }

    /** APK of an install committed WITH dex metadata, for one retry without
     *  it if the session fails (see [onInstallStatus]). */
    @Volatile private var retryWithoutDm: File? = null

    /** Callback target for InstallStatusReceiver (session status sink). */
    fun onInstallStatus(status: Int, message: String?) {
        val info = when (val s = _state.value) {
            is UpdateState.Installing -> s.info
            else -> null
        }
        val retryApk = retryWithoutDm
        retryWithoutDm = null
        if (retryApk != null && status != PackageInstaller.STATUS_SUCCESS &&
            status != PackageInstaller.STATUS_FAILURE_ABORTED && retryApk.isFile
        ) {
            // A bad or mismatched .dm fails the whole install; the APK alone
            // is always installable, so try once more without the profile.
            Log.w(TAG, "install with dex metadata failed ($status: $message); retrying without it")
            scope.launch {
                readPending()?.let { p ->
                    p.dmPath?.let { File(it).delete() }
                    appPreferences.setUpdatePendingJson(
                        json.encodeToString(PendingUpdate.serializer(), p.copy(dmPath = null)),
                    )
                }
                runCatching { commitSession(retryApk, null) }.onFailure { t ->
                    _state.value = UpdateState.Error(
                        "Install failed: ${t.message ?: t::class.simpleName}", info,
                    )
                }
            }
            return
        }
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit // self-update: process dies
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // User dismissed the system dialog: back to the offer.
                info?.let { _state.value = UpdateState.ReadyToInstall(it) }
            }
            else -> _state.value = UpdateState.Error(
                message ?: "Install failed (status $status)", info,
            )
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private val downloadClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    // HTTPS on every hop: GitHub redirects asset downloads to a
                    // CDN host; allow that, but never an https->http downgrade.
                    followSslRedirects(false)
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
                // No total request timeout: a 22MB APK on a slow link must not
                // be aborted by a wall-clock cap (GOTCHA 21 family).
            }
        }
    }

    /**
     * Download and check the release's compile profile for this device
     * (UpdateInfo.dmUrl) next to the staged APK. Returns its path, or null
     * when there is none or anything about it is off; never fails the update.
     */
    private suspend fun stageDexMetadata(dir: File, info: UpdateInfo): String? {
        val url = info.dmUrl ?: return null
        val target = File(dir, "AerioTV-${info.versionName}.dm")
        return try {
            downloadTo(target, info, url = url, sizeBytes = info.dmSizeBytes, reportProgress = false)
            val ok = target.length() == info.dmSizeBytes &&
                java.util.zip.ZipFile(target).use { zip -> zip.getEntry("primary.prof") != null }
            if (ok) {
                target.absolutePath
            } else {
                Log.w(TAG, "dex metadata rejected (size ${target.length()} vs ${info.dmSizeBytes})")
                target.delete()
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "dex metadata download failed; installing without it", t)
            target.delete()
            null
        }
    }

    private suspend fun downloadTo(
        target: File,
        info: UpdateInfo,
        url: String = info.apkUrl,
        sizeBytes: Long = info.apkSizeBytes,
        reportProgress: Boolean = true,
    ) {
        require(url.startsWith("https://")) { "refusing non-https download" }
        downloadClient.prepareGet(url).execute { response ->
            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("HTTP ${response.status.value}")
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            target.outputStream().use { out ->
                while (true) {
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n == -1) break
                    if (n > 0) {
                        out.write(buffer, 0, n)
                        written += n
                        if (reportProgress) {
                            val pct = if (sizeBytes > 0) {
                                ((written * 100) / sizeBytes).toInt().coerceIn(0, 100)
                            } else 0
                            _state.value = UpdateState.Downloading(info, pct)
                        }
                    }
                }
                out.fd.sync()
            }
        }
    }

    /**
     * All-or-nothing verification of the staged APK. The OS re-checks the
     * signature and rejects downgrades at install time regardless; this
     * exists so we never hand the system installer a doomed file and every
     * failure has a precise message. Returns null (after setting the Error
     * state) on any failure.
     */
    private fun verifyStagedApk(file: File, expectedSize: Long?): PendingUpdate? {
        val info = (_state.value as? UpdateState.Verifying)?.info
            ?: (_state.value as? UpdateState.Downloading)?.info
        fun fail(message: String): PendingUpdate? {
            Log.w(TAG, "verify failed: $message")
            _state.value = UpdateState.Error(message, info)
            file.delete()
            return null
        }
        if (expectedSize != null && file.length() != expectedSize) {
            return fail("The download is incomplete (size mismatch). Try again.")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return fail("The downloaded file is not a readable APK.")
        if (archive.packageName != context.packageName) {
            return fail("The downloaded APK is not AerioTV (${archive.packageName}).")
        }
        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        val currentCode = BuildConfig.VERSION_CODE.toLong()
        if (archiveCode <= currentCode) {
            return fail(
                "The release's version code ($archiveCode) is not newer than the installed " +
                    "one ($currentCode), so Android would refuse it.",
            )
        }
        val archiveSigners = signerSha256(archive)
        if (archiveSigners.isEmpty()) {
            return fail("Could not read the APK's signing certificate.")
        }
        if (archiveSigners != ownSignerSha256()) {
            return fail("The APK's signing certificate does not match this app. Refusing to install.")
        }
        return PendingUpdate(
            versionName = archive.versionName ?: "",
            versionCode = archiveCode,
            apkPath = file.absolutePath,
            notes = info?.notes.orEmpty(),
            apkSizeBytes = file.length(),
        )
    }

    /** [dm]: optional dex metadata (compile profile) written as base.dm, so
     *  Android compiles the update during the install. */
    private fun commitSession(staged: File, dm: File?) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setSize(staged.length() + (dm?.length() ?: 0L))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Somebody pressed Update: Android 12+ skips its own confirm
                // when this app installed the version being replaced, and
                // shows it otherwise (STATUS_PENDING_USER_ACTION). Android 11
                // and older always ask once.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, staged.length()).use { out ->
                staged.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            if (dm != null) {
                session.openWrite("base.dm", 0, dm.length()).use { out ->
                    dm.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
            }
            val callback = Intent(context, InstallStatusReceiver::class.java)
                .setPackage(context.packageName)
            // MUTABLE: the platform fills in the session status extras.
            val pi = PendingIntent.getBroadcast(
                context, sessionId, callback,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(pi.intentSender)
        }
    }

    private fun launchUnknownSourcesSettings() {
        val perApp = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val intent = if (perApp.resolveActivity(context.packageManager) != null) {
            perApp
        } else {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                _state.value = UpdateState.Error(
                    "Open Settings > Apps > Security and allow AerioTV to install apps, " +
                        "then try again.",
                    (_state.value as? UpdateState.AwaitingInstallPermission)?.info,
                )
            }
    }

    private suspend fun readPending(): PendingUpdate? {
        val raw = appPreferences.updatePendingJsonOnce()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString(PendingUpdate.serializer(), raw) }.getOrNull()
    }

    private fun installingPackageName(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    private fun ownSignerSha256(): Set<String> = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        signerSha256(context.packageManager.getPackageInfo(context.packageName, flags))
    }.getOrDefault(emptySet())

    private fun signerSha256(pkg: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkg.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") pkg.signatures
        } ?: return emptySet()
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    companion object {
        private const val TAG = "GithubUpdateManager"
        private const val UPDATES_DIR = "updates"
        private const val AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000
        private const val FREE_SPACE_HEADROOM_BYTES = 50L * 1024 * 1024

        /** SHA-256 of the release/upload signing certificate (the cert on
         *  every GitHub release APK). Builds signed with anything else --
         *  debug, or Play's re-signed deliveries -- are not this lineage.
         *  BuildConfig.UPDATE_KEY_SHA256: the official key unless a fork
         *  overrides it (aerio.updateKeySha256). */
        private val UPLOAD_KEY_SHA256 = BuildConfig.UPDATE_KEY_SHA256
    }
}

/** Hilt access for [InstallStatusReceiver] (manifest-instantiated). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdaterEntryPoint {
    fun githubUpdateManager(): GithubUpdateManager
}
