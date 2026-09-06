package com.kg.merapaisa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The downscale factor was originally derived with `generateSequence(1) { it * 2 }.last { ... }`,
 * which never terminates on its own — the factor doubled until Int overflowed to zero and the
 * division threw, so no profile photo was ever written to disk.
 */
class ProfilePhotosTest {

    @Test
    fun imagesAlreadySmallEnoughAreNotDownscaled() {
        assertEquals(1, sampleSizeFor(256))
        assertEquals(1, sampleSizeFor(100))
        assertEquals(1, sampleSizeFor(1))
    }

    @Test
    fun picksTheLargestFactorThatStaysAboveTheTarget() {
        assertEquals(1, sampleSizeFor(511))   // 511/2 = 255, below 256
        assertEquals(2, sampleSizeFor(512))   // 512/2 = 256, exactly the target
        assertEquals(4, sampleSizeFor(1024))
        // 4032/8 = 504 is the last step still at or above 256; /16 would be 252.
        assertEquals(8, sampleSizeFor(4032))  // a typical phone camera long edge
    }

    @Test
    fun terminatesAndStaysSaneForEveryPlausibleSize() {
        // The original implementation hung here rather than returning a factor.
        listOf(1, 2, 255, 256, 257, 1080, 4032, 12000, Int.MAX_VALUE).forEach { edge ->
            val sample = sampleSizeFor(edge)
            assertTrue("factor must be positive for $edge, was $sample", sample >= 1)
            assertTrue("factor must be a power of two for $edge", sample and (sample - 1) == 0)
            assertTrue(
                "decoding $edge at 1/$sample must not fall below the target",
                edge <= 256 || edge / sample >= 256
            )
        }
    }
}
