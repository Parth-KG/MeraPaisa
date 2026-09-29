package com.kg.merapaisa.update

import com.kg.merapaisa.data.JsonObject
import com.kg.merapaisa.data.parseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Whether a newer release exists on GitHub.
 *
 * The app is sideloaded, so there is no store to notice a new version and nothing to nag anyone.
 * Until now an update meant visiting the releases page and finding the APK by hand, which in
 * practice means friends stay on whatever they installed months ago.
 *
 * Parsed with `data/Json.kt` rather than `org.json`, for the same reason the share codec is
 * hand-rolled: `org.json` is an Android framework stub that throws under plain JVM unit tests, and
 * a version comparison that decides whether to install an APK is worth testing without a device.
 *
 * This file only *finds* an update. Downloading and installing one is [UpdateInstaller]'s job, and
 * it is kept separate because that half has to verify a signature before it does anything.
 */

/** What the check found. Every arm is a sentence the UI can show without inventing one. */
sealed interface UpdateStatus {
    /** Running the newest release, or a build newer than any published one. */
    data object UpToDate : UpdateStatus

    data class Available(
        val version: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val notes: String
    ) : UpdateStatus

    /** No network, GitHub unreachable, rate-limited, or an answer that made no sense. */
    data class Unreachable(val reason: String) : UpdateStatus
}

class UpdateCheck(private val releasesUrl: String = LATEST_RELEASE_URL) {

    /**
     * Asks GitHub for the latest release.
     *
     * [currentVersion] is `BuildConfig.VERSION_NAME`. Returns [UpdateStatus.Unreachable] rather
     * than throwing: an update check failing is ordinary and must never look like a fault.
     */
    suspend fun check(currentVersion: String): UpdateStatus {
        val body = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { fetch() }
        } ?: return UpdateStatus.Unreachable("GitHub did not answer in time.")

        return interpret(body, currentVersion)
    }

    private fun fetch(): String? {
        val connection = (URL(releasesUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            // Asking for the documented media type keeps the shape stable if GitHub's default moves.
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MeraPaisa")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Parth-KG/MeraPaisa/releases/latest"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val REQUEST_TIMEOUT_MS = 12_000L

        /**
         * Reads a release payload and decides. Separated from the network so it can be tested
         * against real GitHub responses without one.
         */
        fun interpret(body: String?, currentVersion: String): UpdateStatus {
            if (body == null) return UpdateStatus.Unreachable("Couldn't reach GitHub.")
            val root = parseJson(body) as? JsonObject
                ?: return UpdateStatus.Unreachable("GitHub's answer could not be read.")

            if (root.bool("draft") == true || root.bool("prerelease") == true) {
                return UpdateStatus.UpToDate
            }

            val tag = root.string("tag_name")
                ?: return UpdateStatus.Unreachable("That release has no version tag.")
            val latest = tag.removePrefix("v")

            if (compareVersions(latest, currentVersion.removePrefix("v")) <= 0) {
                return UpdateStatus.UpToDate
            }

            // The APK, not the source tarballs GitHub attaches to every release.
            val asset = root.objects("assets").orEmpty().firstOrNull {
                it.string("name")?.endsWith(".apk", ignoreCase = true) == true
            } ?: return UpdateStatus.Unreachable("Release $tag has no APK attached.")

            val url = asset.string("browser_download_url")
                ?: return UpdateStatus.Unreachable("That release's APK has no download link.")

            return UpdateStatus.Available(
                version = latest,
                downloadUrl = url,
                sizeBytes = asset.long("size") ?: 0L,
                notes = plainNotes(root.string("body").orEmpty())
            )
        }

        /**
         * Release notes as readable text.
         *
         * Two problems, and the second is the one that actually looks broken on a phone.
         *
         * GitHub returns markdown, so `**bold**` arrives with its asterisks and headings keep
         * their `#`. That is the easy half.
         *
         * The harder half: release notes are written hard-wrapped at around 95 characters, which
         * is right in a text editor and wrong in a narrow dialog. Rendered as-is, every source
         * line break becomes a real one, so the text breaks at seemingly random points that have
         * nothing to do with where the dialog's edge is.
         *
         * So paragraphs are **reflowed** (lines within a paragraph are joined back into one and
         * left for the layout to wrap), while blank lines, list items and table rows keep their
         * breaks, because there the line break carries meaning.
         *
         * Deliberately not a markdown renderer. It removes emphasis and heading markers, drops
         * callout syntax, reflows prose, and leaves everything else as written.
         */
        fun plainNotes(markdown: String): String {
            val cleaned = markdown
                .lineSequence()
                .filterNot { it.trimStart().startsWith("> [!") }
                .map { line ->
                    line.trim()
                        .replace(Regex("^#{1,6}\\s*"), "")
                        .replace(Regex("^>\\s?"), "")
                        .replace("**", "")
                        .replace(Regex("(?<!\\w)[*_](?=\\S)([^*_]+)(?<=\\S)[*_](?!\\w)"), "$1")
                }
                .toList()

            val out = StringBuilder()
            val paragraph = StringBuilder()
            var inBullet = false

            fun flush() {
                if (paragraph.isNotEmpty()) {
                    if (out.isNotEmpty()) out.append("\n\n")
                    out.append(paragraph.toString().trim())
                    paragraph.setLength(0)
                }
            }

            for (line in cleaned) {
                when {
                    // A blank line ends a paragraph.
                    line.isEmpty() -> { flush(); inBullet = false }
                    // Lines whose break means something: bullets, numbered items, table rows,
                    // and horizontal rules. Each stands alone.
                    line.startsWith("- ") || line.startsWith("* ") || line.startsWith("|") ||
                        line.startsWith("---") || Regex("^\\d+\\.\\s").containsMatchIn(line) -> {
                        flush()
                        if (out.isNotEmpty()) out.append("\n")
                        out.append(line)
                        inBullet = true
                    }
                    // A plain line straight after a bullet is that bullet wrapping, not a new
                    // paragraph. Splitting it leaves half a sentence stranded below a gap.
                    inBullet -> out.append(' ').append(line)
                    // Ordinary prose: join onto the paragraph being built.
                    else -> {
                        if (paragraph.isNotEmpty()) paragraph.append(' ')
                        paragraph.append(line)
                    }
                }
            }
            flush()
            return summarise(out.toString().replace(Regex("\n{3,}"), "\n\n").trim())
        }

        /**
         * Trims notes to something a dialog can hold.
         *
         * Full notes belong on the releases page; this is a summary to decide by. Cut at a
         * paragraph if one falls in range, otherwise at a word. Never mid-word, and never
         * mid-sentence without an ellipsis to show it was cut.
         *
         * The punctuation trimmed off the cut includes an em dash, kept as a unicode escape so
         * that no literal one sits in the source.
         */
        private fun summarise(text: String, limit: Int = 420): String {
            if (text.length <= limit) return text

            val head = text.take(limit)
            val atParagraph = head.lastIndexOf("\n\n")
            if (atParagraph >= limit / 2) return text.take(atParagraph).trimEnd() + "\n\n…"

            val atWord = head.lastIndexOf(' ')
            val cut = if (atWord >= limit / 2) atWord else limit
            return text.take(cut).trimEnd().trimEnd(',', ';', ':', '\u2014', '-') + "…"
        }

        /**
         * Compares dotted numeric versions: negative if [a] is older, zero if equal, positive if
         * newer. `2.10.0` is newer than `2.9.0`, which string comparison gets backwards.
         *
         * Non-numeric parts sort as zero rather than throwing. A malformed tag should make the app
         * decide "no update" quietly, not crash on launch.
         */
        fun compareVersions(a: String, b: String): Int {
            val left = a.trim().split('.')
            val right = b.trim().split('.')
            for (i in 0 until maxOf(left.size, right.size)) {
                val l = left.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
                val r = right.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
                if (l != r) return l - r
            }
            return 0
        }
    }
}
