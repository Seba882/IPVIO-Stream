package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LangScanLogicTest {
    @Test
    fun normalizesCodes() {
        assertEquals(listOf("pl", "pl", "pl", "de", "de", "en"), listOf("pol", "pl-PL", "Polish", "ger", "deu", "eng").map { LangScanLogic.norm(it) })
    }

    @Test
    fun encodeDecodeAndMatch() {
        val l = LangScanLogic.of(listOf("pol", "eng", null), listOf("pl"))
        val back = LangScanLogic.decode(LangScanLogic.encode(l))!!
        assertTrue(back.has("pl"))
        assertFalse(LangScanLogic.of(listOf("eng"), emptyList()).has("pl"))
        assertTrue(LangScanLogic.of(listOf("eng"), listOf("pol")).has("pl"))   // subtitles count too
        assertEquals("-", LangScanLogic.encode(null))
        assertNull(LangScanLogic.decode("-"))
    }
}
