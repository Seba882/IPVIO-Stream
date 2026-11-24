package com.ipvio.stream

/**
 * Names as shown on screen. With "Hide country prefix" (General.hidePrefix) channel and category names lose a leading
 * country / language tag such as "PL:" or "|PL|". Display only: the stored names, ids, favourites, history, search,
 * EPG matching and channel bundling keep the provider's names.
 */
object ShowName {
    fun of(name: String): String = if (General.hidePrefix) LangTagLogic.stripLeadingTag(name) else name
}
