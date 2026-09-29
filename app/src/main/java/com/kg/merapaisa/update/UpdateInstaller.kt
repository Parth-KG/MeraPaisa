package com.kg.merapaisa.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads an update and hands it to the system installer.
 *
 * **The signature check below is the point of this file.** Android will happily install any APK
 * whose package name matches if the user approves it, and an app that downloads code and installs
 * it without checking what signed it is a worse hole than having no updater at all: it turns a
 * hijacked download, a compromised host, or a tampering proxy into arbitrary code running as Mera
 * Paisa, with the ledger.
 *
 * So: fetch over HTTPS, write to private cache storage, and **compare the downloaded APK's signing
 * certificate against the running app's before it is offered to the installer.** A mismatch means
 * the file is deleted and reported, never installed. The app never learns its own fingerprint from
 * the file it just downloaded. It reads it from itself, via PackageManager.
 *
 * The system installer still asks the user to confirm. This check happens first, so that prompt is
 * only ever shown for a file that genuinely came from the same key as the running app.
 */
object UpdateInstaller {

    private const val UPDATE_DIR = "updates"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

    /** What happened, in terms the UI can explain without guessing. */
    sealed interface Result {
        data class Ready(val file: File) : Result
        data class Failed(val reason: String) : Result

        /**
         * The download was signed by a different key. Deliberately its own arm, and deliberately
         * worded as a warning rather than an error: it should never happen, and if it does, the
         * user needs to know something is wrong rather than to tap retry.
         */
        data object SignatureMismatch : Result
    }

    /**
     * Downloads [url] and verifies it. Never installs; that needs a user tap via [install].
     *
     * [onProgress] receives 0..100, or -1 when the server does not report a length.
     */
    suspend fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (Int) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) {
            return@withContext Result.Failed("GitHub gave a download link that isn't HTTPS, so it wasn't used.")
        }

        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        // One file per version, replaced each time, so a failed download cannot be installed later.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "merapaisa-$version.apk")

        val downloaded = runCatching {
            var connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MeraPaisa")
            }
            // GitHub redirects release assets to objects.githubusercontent.com. HttpURLConnection
            // will not follow a redirect that changes protocol, and refuses to downgrade, so the
            // one hop we follow by hand must be checked to still be HTTPS.
            if (connection.responseCode in 300..399) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                if (next == null || !next.startsWith("https://")) {
                    return@runCatching null
                }
                connection = (URL(next).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "MeraPaisa")
                }
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@runCatching null
            }

            val total = connection.contentLengthLong
            var written = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        onProgress(if (total > 0) ((written * 100) / total).toInt() else -1)
                    }
                }
            }
            connection.disconnect()
            target
        }.getOrNull()

        if (downloaded == null || !downloaded.isFile || downloaded.length() == 0L) {
            target.delete()
            return@withContext Result.Failed("The download didn't finish.")
        }

        if (!signedLikeUs(context, downloaded)) {
            // Delete first, report second. A file that failed this check must not survive on disk
            // where a later code path might pick it up.
            downloaded.delete()
            return@withContext Result.SignatureMismatch
        }

        Result.Ready(downloaded)
    }

    /**
     * True when [apk] carries exactly the same signing certificate as the running app.
     *
     * Compares SHA-256 digests of the full certificate sets. Returning **false on any failure** is
     * deliberate: if the check cannot be completed, the safe answer is to refuse, not to shrug and
     * install.
     */
    /**
     * Whether [apkPath] carries the same signing certificate as the running app.
     *
     * Public so it can be tested directly against a deliberately mis-signed APK. This is the one
     * check standing between a hijacked download and arbitrary code running as Mera Paisa, and a
     * check that is only reachable through a network download is a check nobody verifies.
     */
    fun isSignedLikeThisApp(context: Context, apkPath: String): Boolean =
        signedLikeUs(context, File(apkPath))

    private fun signedLikeUs(context: Context, apk: File): Boolean = runCatching {
        val ours = certificateDigests(context, context.packageName, null)
        val theirs = certificateDigests(context, null, apk.absolutePath)
        ours.isNotEmpty() && ours == theirs
    }.getOrDefault(false)

    /** Digests of either an installed package or an APK file (whichever argument is non-null). */
    @Suppress("DEPRECATION")
    private fun certificateDigests(context: Context, packageName: String?, apkPath: String?): Set<String> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val info = if (packageName != null) {
            pm.getPackageInfo(packageName, flags)
        } else {
            pm.getPackageArchiveInfo(apkPath!!, flags)
        } ?: return emptySet()

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()

        val sha = MessageDigest.getInstance("SHA-256")
        return signatures.map { sha.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
            .toSet()
    }

    /**
     * Opens the system installer for an already-verified file.
     *
     * The APK goes out as a `content://` URI through the existing FileProvider, because a
     * `file://` one throws `FileUriExposedException`, the same reason CSV export goes through it.
     */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Whether the user has granted this app permission to install packages. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /**
     * Settings screen where that permission is granted, for when [canInstall] is false.
     *
     * The per-app "install unknown apps" screen only exists from API 26. Below that the permission
     * is granted wholesale by a single system-wide toggle, and the manifest declaration is the
     * whole story, so older devices get pointed at security settings instead of an Intent action
     * that does not resolve there.
     *
     * [canInstall] already returns true below API 26, so in practice this branch is unreachable
     * from the app's own flow. It is handled anyway rather than left to fail silently on a phone
     * nobody tested.
     */
    fun installPermissionIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData("package:${context.packageName}".toUri())
        } else {
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
        }
}
