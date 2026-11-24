package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbExtraLogicTest {
    @Test fun keywords_andChristmas() {
        val k = TmdbExtraLogic.keywords(listOf("Christmas", "christmas", "santa claus", " ", "elf"))
        assertEquals(listOf("christmas", "santa claus", "elf"), k)
        assertTrue(TmdbExtraLogic.isXmas(k))
        assertFalse(TmdbExtraLogic.isXmas(listOf("heist", "bank robbery")))
    }

    @Test fun logo_prefersAppLanguageThenEnglishThenNone() {
        val l = listOf(TmdbExtraLogic.Logo("/de.png", "de", 9.0), TmdbExtraLogic.Logo("/en1.png", "en", 5.0),
            TmdbExtraLogic.Logo("/en2.png", "en", 7.0), TmdbExtraLogic.Logo("/none.png", null))
        assertEquals("/en2.png", TmdbExtraLogic.pickLogo(l, "pl"))
        assertEquals("/de.png", TmdbExtraLogic.pickLogo(l, "de"))
        assertEquals("/none.png", TmdbExtraLogic.pickLogo(listOf(TmdbExtraLogic.Logo("/none.png", null), TmdbExtraLogic.Logo("/fr.png", "fr")), "pl"))
        assertNull(TmdbExtraLogic.pickLogo(listOf(TmdbExtraLogic.Logo("/fr.png", "fr")), "pl"))
    }

    @Test fun collectionParts_matchedByIdOrTitleAndYear_inSagaOrder() {
        fun m(id: String, t: String, y: Int?) = Item(id, Kind.MOVIE, t, y, "http://x/$id", null, "g")
        val parts = listOf(TmdbExtraLogic.Part(1, "Casino Royale", "Casino Royale", 2006), TmdbExtraLogic.Part(2, "007 Quantum of Solace", "Quantum of Solace", 2008),
            TmdbExtraLogic.Part(3, "Skyfall", "Skyfall", 2012), TmdbExtraLogic.Part(4, "Spectre", "Spectre", 2015))
        val items = listOf(m("a", "PL: Skyfall", 2012), m("b", "Casino Royale", 2006), m("c", "Casino Royale", 1967), m("d", "Quantum of Solace", 2008), m("e", "Spectre", null))
        assertEquals(listOf("b", "d", "a", "e"), TmdbExtraLogic.inPlaylist(parts, items) { if (it.id == "d") 2 else null }.map { it.second.id })
    }
}
