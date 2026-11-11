package com.kg.merapaisa.repository

import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.base64UrlDecode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which photos a backup carries. */
class PhotosOfTest {

    private val files = mapOf(
        "/files/pfp_1.jpg" to byteArrayOf(1, 2, 3),
        "/files/pfp_2.jpg" to byteArrayOf(4, 5, 6),
        "/files/empty.jpg" to byteArrayOf()
    )

    private fun read(path: String) = files[path]

    @Test
    fun `a person with a photo that can be read is carried`() {
        val photos = photosOf(listOf(Person(id = 2, name = "Asha", pfpType = "photo", pfpValue = "/files/pfp_1.jpg")), ::read)
        assertEquals(setOf(2L), photos.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), base64UrlDecode(photos.getValue(2L)))
    }

    @Test
    fun `initials, an emoji or a missing file carry nothing`() {
        val photos = photosOf(
            listOf(
                Person(id = 1, name = "Asha", pfpType = "initials", pfpValue = "AS"),
                Person(id = 2, name = "Bilal", pfpType = "emoji", pfpValue = "x"),
                Person(id = 3, name = "Chai", pfpType = "photo", pfpValue = "/files/gone.jpg"),
                Person(id = 4, name = "Diego", pfpType = "photo", pfpValue = ""),
                Person(id = 5, name = "Emi", pfpType = "photo", pfpValue = "/files/empty.jpg"),
                Person(id = 6, name = "Farid", pfpType = "photo", pfpValue = "/files/pfp_2.jpg")
            ),
            ::read
        )
        assertEquals(setOf(6L), photos.keys)
    }
}
