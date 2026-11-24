package com.ipvio.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonLogicTest {
    @Test
    fun christmasTitles_inSeveralLanguages() {
        for (t in listOf("Gwiazdka w Pałacu", "Christmas at the Palace", "Świąteczny romans", "Grinch: Świąt nie będzie",
            "Wigilijne psy", "Weihnachten mit Papa", "Kerstmis", "Kevin sam w domu")) assertTrue(t, SeasonLogic.isXmas(t))
        assertTrue(SeasonLogic.isXmas("Any film", "PL | FILMY ŚWIĄTECZNE"))
    }

    @Test
    fun noFalseHits_onSimilarWords() {
        // "świat" (world) and "święty" (saint) are not Christmas; "Santa" alone is a name
        for (t in listOf("Świat według Kiepskich", "Święty", "Santa Clarita Diet", "Natalia")) assertFalse(t, SeasonLogic.isXmas(t))
    }

    @Test
    fun season_from15NovemberTo6January() {
        assertFalse(SeasonLogic.isXmasSeason(11, 14))
        assertTrue(SeasonLogic.isXmasSeason(11, 15))
        assertTrue(SeasonLogic.isXmasSeason(12, 31))
        assertTrue(SeasonLogic.isXmasSeason(1, 6))
        assertFalse(SeasonLogic.isXmasSeason(1, 7))
    }
}
