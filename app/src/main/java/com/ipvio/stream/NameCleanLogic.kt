package com.ipvio.stream

/**
 * The user's own list of texts to remove from channel and category names (settings), pure logic. Entries are separated
 * by commas or new lines. An entry is removed at the start of a name ("PL VIP:", "VIP |") or at its end (" RAW",
 * "[MULTI]"); case does not matter; separators left over at that edge go too. A name never becomes empty.
 */
object NameCleanLogic {
    private const val EDGE = " |:-–—_.[]()"

    fun parse(raw: String): List<String> =
        raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }

    fun apply(name: String, entries: List<String>): String {
        if (entries.isEmpty()) return name
        var t = name.trim()
        var changed = true
        var rounds = 0
        while (changed && rounds++ < 4) {
            changed = false
            for (e in entries) {
                // Whole words only: "RAW" removes "… RAW" but leaves "Rawhide"
                if (t.length > e.length && t.startsWith(e, ignoreCase = true) && (!e.last().isLetterOrDigit() || !t[e.length].isLetterOrDigit())) {
                    t = t.substring(e.length).trimStart { it in EDGE }; changed = true
                }
                if (t.length > e.length && t.endsWith(e, ignoreCase = true) && (!e.first().isLetterOrDigit() || !t[t.length - e.length - 1].isLetterOrDigit())) {
                    t = t.substring(0, t.length - e.length).trimEnd { it in EDGE && it != ']' && it != ')' }.trimEnd(); changed = true
                }
            }
        }
        return t.ifBlank { name }
    }
}
