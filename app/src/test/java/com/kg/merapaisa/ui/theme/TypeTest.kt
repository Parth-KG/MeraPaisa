package com.kg.merapaisa.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's type styles and the copy handed to Material, reached in the order the widget reaches
 * them: the app's styles first.
 *
 * Built that way round, Material's copy was made while the app's styles were still being made, so
 * it held nulls for the life of the process, and the first text field drawn afterwards crashed.
 * The device tests found it when a test drew a button label before any themed screen.
 */
class TypeTest {

    @Test
    fun materialsTypeIsWholeWhenTheAppsStylesAreReachedFirst() {
        val first: Any? = MeraPaisaType.action
        assertTrue("the app's styles are there", first != null)

        val slots: List<Any?> = listOf(
            Typography.displaySmall, Typography.headlineSmall, Typography.titleLarge,
            Typography.titleMedium, Typography.titleSmall, Typography.bodyLarge,
            Typography.bodyMedium, Typography.bodySmall, Typography.labelLarge,
            Typography.labelMedium, Typography.labelSmall
        )
        assertTrue("Material's type has empty slots: $slots", slots.all { it != null })
    }
}
