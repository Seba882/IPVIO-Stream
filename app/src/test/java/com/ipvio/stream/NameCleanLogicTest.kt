package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class NameCleanLogicTest {
    private val e = NameCleanLogic.parse("PL VIP:, VIP |, RAW, [MULTI]")

    @Test fun removesAtStartAndEnd() {
        assertEquals("CINEMAX 2", NameCleanLogic.apply("PL VIP: CINEMAX 2 RAW", e))
        assertEquals("TVN", NameCleanLogic.apply("VIP | TVN", e))
        assertEquals("TVN", NameCleanLogic.apply("TVN [MULTI]", e))
    }

    @Test fun wholeWordsOnly_neverEmpty() {
        assertEquals("Rawhide", NameCleanLogic.apply("Rawhide", e))
        assertEquals("RAW", NameCleanLogic.apply("RAW", e))
        assertEquals("PL VIP:", NameCleanLogic.apply("PL VIP:", e))
    }
}
