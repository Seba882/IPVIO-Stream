package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LangTagLogicTest {
    @Test fun erkenntKuerzelAmAnfang() {
        assertEquals("de", LangTagLogic.detect("DE | Action"))
        assertEquals("de", LangTagLogic.detect("|DE| NETFLIX"))
        assertEquals("tr", LangTagLogic.detect("TR: Yerli Filmler"))
        assertEquals("de", LangTagLogic.detect("VOD - DE - Comedy"))
        assertEquals("en", LangTagLogic.detect("[EN] Movies 2024"))
        assertEquals("ar", LangTagLogic.detect("AR \u258E MBC"))
    }

    @Test fun erkenntKuerzelAmEndeUndWoerter() {
        assertEquals("de", LangTagLogic.detect("Netflix Serien [GER]"))
        assertEquals("tr", LangTagLogic.detect("TÜRK FİLMLERİ"))
        assertEquals("tr", LangTagLogic.detect("Türkiye Ulusal"))
        assertEquals("de", LangTagLogic.detect("Filme Deutsch HD"))
        assertEquals("es", LangTagLogic.detect("Peliculas Latino"))
    }

    @Test fun keineFehlalarme() {
        assertNull(LangTagLogic.detect("beIN Sports 1"))
        assertNull(LangTagLogic.detect("Al Jazeera"))
        assertNull(LangTagLogic.detect("Action 4K"))
        assertNull(LangTagLogic.detect("NO SIGNAL"))
        assertNull(LangTagLogic.detect(""))
    }

    @Test fun kategorieVorSendername() {
        assertEquals("de", LangTagLogic.of("DE | Sport", "TR: beIN", live = true))
        assertEquals("tr", LangTagLogic.of("Sport", "TR: beIN 1", live = true))
        // Movies/series: only explicit tags in the original name count
        assertEquals("de", LangTagLogic.of("Netflix", "DE: Titel", live = false))
        assertEquals("tr", LangTagLogic.of("Netflix", "Film Adı [TR]", live = false))
        assertEquals("en", LangTagLogic.of("Netflix", "EN - The Movie", live = false))
        assertEquals(LangTagLogic.UNKNOWN, LangTagLogic.of("Netflix", "IT - Welcome to Derry", live = false))
        assertEquals(LangTagLogic.UNKNOWN, LangTagLogic.of("Netflix", "Us (2019)", live = false))
        assertEquals(LangTagLogic.UNKNOWN, LangTagLogic.of("Netflix", "Mr. Robot", live = false))
    }

    @Test fun speichernUndLesen() {
        val s = setOf("tr", "ar", "?")
        assertEquals(s, LangTagLogic.decode(LangTagLogic.encode(s)))
        assertEquals(emptySet<String>(), LangTagLogic.decode(""))
    }

    @Test fun kennungMitBindestrichWirdAusDemTitelEntfernt() {
        assertEquals("Glass Onion (2022)", LangTagLogic.stripDashTag("EN - Glass Onion (2022)"))
        assertEquals("Film", LangTagLogic.stripDashTag("GER – Film"))
        assertEquals("IT - Welcome to Derry", LangTagLogic.stripDashTag("IT - Welcome to Derry"))
        assertEquals("Spider-Man", LangTagLogic.stripDashTag("Spider-Man"))
    }

    @Test
    fun stripLeadingTag_onlyKnownCodes() {
        assertEquals("TVN HD", LangTagLogic.stripLeadingTag("PL: TVN HD"))
        assertEquals("TVN", LangTagLogic.stripLeadingTag("|PL| TVN"))
        assertEquals("Polsat Sport", LangTagLogic.stripLeadingTag("[PL] Polsat Sport"))
        assertEquals("CANAL+ Sport", LangTagLogic.stripLeadingTag("PL | CANAL+ Sport"))
        assertEquals("TVP 1", LangTagLogic.stripLeadingTag("PL - TVP 1"))
        assertEquals("TVN: Fakty", LangTagLogic.stripLeadingTag("TVN: Fakty"))
        assertEquals("IT – Welcome to Derry", LangTagLogic.stripLeadingTag("IT – Welcome to Derry"))
        assertEquals("PL:", LangTagLogic.stripLeadingTag("PL:"))
    }

    @Test
    fun stripLeadingTag_withProviderWords() {
        assertEquals("CINEMAX 2 RAW", LangTagLogic.stripLeadingTag("PL VIP: CINEMAX 2 RAW"))
        assertEquals("TVN", LangTagLogic.stripLeadingTag("PL RAW | TVN"))
        assertEquals("TVN 7", LangTagLogic.stripLeadingTag("|PL HD| TVN 7"))
        assertEquals("VIP: TVN", LangTagLogic.stripLeadingTag("VIP: TVN"))
        assertEquals("PL VIP:", LangTagLogic.stripLeadingTag("PL VIP:"))
    }
}
