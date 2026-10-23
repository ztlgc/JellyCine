package com.jellycine.app.ui.screens.detail

import androidx.compose.ui.unit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDetailMenuPositionTest {
    private val positioner = VideoDetailMenuPositionProvider(gap = 6, margin = 8)

    @Test fun menuAlignsWithTheButtonsRightEdgeAndStartsBelowIt() {
        val position = positioner.calculatePosition(IntRect(350, 440, 394, 484), IntSize(412, 892), LayoutDirection.Ltr, IntSize(248, 160))
        assertEquals(IntOffset(146, 490), position)
    }

    @Test fun shortWindowsKeepMenuBelowRatherThanFlippingAboveTheButton() {
        val anchor = IntRect(290, 290, 334, 334)
        // Content height is constrained to the available space and scrolls inside the popup.
        val position = positioner.calculatePosition(anchor, IntSize(360, 412), LayoutDirection.Ltr, IntSize(248, 64))
        assertEquals(340, position.y)
        assertTrue(position.y + 64 <= 412 - 8)
    }

    @Test fun narrowAndRtlWindowsKeepTheMenuInsideHorizontalMargins() {
        val position = positioner.calculatePosition(IntRect(8, 150, 52, 194), IntSize(320, 640), LayoutDirection.Rtl, IntSize(248, 160))
        assertEquals(IntOffset(8, 200), position)
        val ltr = positioner.calculatePosition(IntRect(8, 150, 52, 194), IntSize(320, 640), LayoutDirection.Ltr, IntSize(248, 160))
        assertEquals(8, ltr.x)
    }
}
