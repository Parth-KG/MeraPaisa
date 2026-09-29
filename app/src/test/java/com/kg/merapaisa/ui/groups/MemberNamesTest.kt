package com.kg.merapaisa.ui.groups

import com.kg.merapaisa.data.Person
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Group sentences that name you. You are a member of every group, as a row called "You", and a
 * plain name in a sentence read "xyz pays You" and "You is owed". Found walking the app on a phone.
 */
class MemberNamesTest {

    private val names = MemberNames(
        listOf(
            Person(id = 1, name = "You", isSelf = true),
            Person(id = 2, name = "Asha"),
            Person(id = 3, name = "Bilal")
        )
    )

    @Test
    fun `a payment to you ends in you`() = assertEquals("Asha pays you", names.pays(2, 1))

    @Test
    fun `a payment from you starts with You pay`() = assertEquals("You pay Bilal", names.pays(1, 3))

    @Test
    fun `a payment between two others is unchanged`() = assertEquals("Bilal pays Asha", names.pays(3, 2))

    @Test
    fun `after the first word you is lower case`() {
        assertEquals("you", names.objectOf(1))
        assertEquals("You", names.subject(1))
        assertEquals("Asha", names.objectOf(2))
    }
}
