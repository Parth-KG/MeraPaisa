package com.kg.merapaisa.repository

import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.base64UrlEncode

/**
 * The profile photo files a backup reads and a restore writes.
 *
 * An interface so the backup repository can be built without an Android Context, as the device
 * tests that predate photos still build it, and so the decisions around it stay testable. The real
 * one is `AppPhotoStore`, over the app's private files.
 */
interface PhotoStore {
    /** The bytes of a photo this app saved, or null if [path] is not one or cannot be read. */
    fun read(path: String): ByteArray?

    /** Saves [bytes] as a new photo file and returns its path, or null if they are not an image. */
    fun save(bytes: ByteArray): String?

    /** Removes a photo this app saved. Anything else is left alone. */
    fun delete(path: String)

    /** The paths of every photo file this app has saved. */
    fun all(): List<String>

    /** No files at all: backups carry no photos and restores write none. */
    object None : PhotoStore {
        override fun read(path: String): ByteArray? = null
        override fun save(bytes: ByteArray): String? = null
        override fun delete(path: String) = Unit
        override fun all(): List<String> = emptyList()
    }
}

/**
 * The photos a backup carries: one per person whose picture is a photo and whose file can still be
 * read, keyed by person id. A person whose file has gone shows initials on this phone already, and
 * restores with them.
 */
internal fun photosOf(persons: List<Person>, read: (String) -> ByteArray?): Map<Long, String> =
    persons.filter { it.pfpType == "photo" && it.pfpValue.isNotBlank() }
        .mapNotNull { person ->
            val bytes = read(person.pfpValue)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            person.id to base64UrlEncode(bytes)
        }
        .toMap()
