package com.ipvio.stream

/**
 * Names as shown on screen. With "Hide country prefix" (General.hidePrefix) channel and category names lose a leading
 * country / language tag such as "PL:" or "|PL|". Display only: the stored names, ids, favourites, history, search,
 * EPG matching and channel bundling keep the provider's names.
 */
object ShowName {
    // Order: the user's own texts first ("PL VIP:", " RAW"), then the automatic country tag
    @Volatile private var cacheRaw: String? = null
    @Volatile private var cache: List<String> = emptyList()

    /** The user's own texts to remove (settings), parsed once per change. */
    private fun own(): List<String> {
        val raw = General.removeTexts
        if (raw != cacheRaw) { cache = NameCleanLogic.parse(raw); cacheRaw = raw }
        return cache
    }

    fun of(name: String): String {
        val n = NameCleanLogic.apply(name, own())
        return if (General.hidePrefix) LangTagLogic.stripLeadingTag(n) else n
    }
}
