package com.kg.merapaisa.ui.dialogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** What the UPI ID field says, and what a saved sheet stores. */
class UpiIdFieldTest {

    @Test
    fun `an empty field is fine, it is optional`() {
        assertNull(upiIdProblem(""))
        assertNull(upiIdProblem("   "))
    }

    @Test
    fun `a real ID is fine and a typo is named`() {
        assertNull(upiIdProblem("asha@okaxis"))
        assertNotNull(upiIdProblem("asha okaxis"))
        assertNotNull(upiIdProblem("asha@"))
    }

    @Test
    fun `saving stores the trimmed ID, and blank clears it`() {
        assertEquals("asha@okaxis", savedUpiIdFor("  asha@okaxis ", "INR", previous = null))
        assertNull(savedUpiIdFor("", "INR", previous = "old@ybl"))
    }

    @Test
    fun `someone kept in another currency keeps whatever ID they had`() {
        assertEquals("old@ybl", savedUpiIdFor("", "USD", previous = "old@ybl"))
        assertNull(savedUpiIdFor("new@ybl", "USD", previous = null))
    }
}
