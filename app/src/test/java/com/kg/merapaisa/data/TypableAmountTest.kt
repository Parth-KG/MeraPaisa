package com.kg.merapaisa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypableAmountTest {

    @Test
    fun rupeesTakeTwoDecimalsAndNoMore() {
        assertTrue(isTypableAmount("", "INR"))
        assertTrue(isTypableAmount("12.", "INR"))
        assertTrue(isTypableAmount("12.50", "INR"))
        assertFalse(isTypableAmount("12.505", "INR"))
        assertFalse(isTypableAmount("1.2.3", "INR"))
        assertFalse(isTypableAmount("12a", "INR"))
    }

    @Test
    fun yenTakesNoPointAtAll() {
        assertTrue(isTypableAmount("1250", "JPY"))
        assertFalse(isTypableAmount("12.5", "JPY"))
        assertFalse(isTypableAmount("12.", "JPY"))
    }

    @Test
    fun theWholePartIsCappedAsOnTheKeypad() {
        assertTrue(isTypableAmount("999999999", "INR"))
        assertFalse(isTypableAmount("1000000000", "INR"))
    }

    @Test
    fun aSignIsOnlyAllowedWhereAnEntryCanBeNegative() {
        assertFalse(isTypableAmount("-40", "INR"))
        assertTrue(isTypableAmount("-40", "INR", allowNegative = true))
        assertTrue(isTypableAmount("−40", "INR", allowNegative = true))
    }
}
