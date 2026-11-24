package com.ipvio.stream

import java.util.Calendar

/**
 * Seasonal collections, pure logic (no Android). Christmas: from 15 November to 6 January the categories of movies and
 * series get an extra entry with all titles whose name or provider category says Christmas, in many languages.
 * Works without TMDB and without automatic genres (it only reads names), so it also works for very big libraries.
 */
object SeasonLogic {
    /** Christmas words; Polish forms with diacritics only, because "swiat" without them also means "world". */
    private val XMAS = Regex(
        """(christmas|x-?mas\b|\bnoel\b|noël|navidad|navideñ|\bnatal[ei]\b|weihnacht|christkind|\bnikolaus\b|kerst|""" +
            """santa\s*claus|grinch|reindeer|nutcracker|home\s*alone|kevin\s*sam\s*w\s*domu|to\s*właśnie\s*miłość|love\s*actually|""" +
            """świąt|święta\b|świątecz|boże\s*narodzeni|bożego\s*narodzeni|wigili|gwiazdk|mikołaj|renifer|dziadek\s*do\s*orzechów|""" +
            """\bbožić|božićn|yılbaşı|noel\s*baba)""",
        // Kotlin's IGNORE_CASE is Unicode aware ("ŚWIĄTECZNE" = "świąteczne"); an inline (?i) would not be
        RegexOption.IGNORE_CASE
    )

    /** True from 15 November to 6 January (both included). */
    fun isXmasSeason(month: Int, day: Int): Boolean = (month == 11 && day >= 15) || month == 12 || (month == 1 && day <= 6)

    fun isXmasSeason(now: Calendar = Calendar.getInstance()): Boolean =
        isXmasSeason(now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))

    /** A Christmas title: by its name or by its provider category. */
    fun isXmas(title: String, group: String = ""): Boolean = XMAS.containsMatchIn(title) || (group.isNotEmpty() && XMAS.containsMatchIn(group))

    /**
     * Christmas titles of [items], each title once, in the given order: by name, or by TMDB keywords where the app already
     * knows them ([byKeywords], e.g. "Klaus" has no Christmas word in its name).
     */
    fun xmas(items: Iterable<Item>, byKeywords: (Item) -> Boolean = { false }): List<Item> {
        val seen = HashSet<String>()
        return items.filter { (isXmas(it.title, it.group) || byKeywords(it)) && seen.add(it.id) }
    }
}
