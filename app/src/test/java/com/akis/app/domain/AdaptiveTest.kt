package com.akis.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveTest {
    @Test
    fun columnsGrowWithTheWindow() {
        assertEquals(2, Adaptive.columns(360))
        assertEquals(2, Adaptive.columns(599))
        assertEquals(3, Adaptive.columns(600))
        assertEquals(3, Adaptive.columns(840))
        assertEquals(4, Adaptive.columns(900))
        assertEquals(4, Adaptive.columns(1280))
    }
}
