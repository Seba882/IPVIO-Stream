package com.ipvio.stream

import java.util.Locale

/**
 * Audio and subtitle languages of a title, read from the file itself ([MediaProbe]) and kept for later. Pure logic.
 * Stored form: "a=pl,en;s=pl" (audio / subtitles, ISO 639-1 where known); "-" = checked, nothing found.
 */
object LangScanLogic {
    data class Langs(val audio: Set<String>, val subs: Set<String>) {
        /** The title has [lang] as audio or subtitles. */
        fun has(lang: String): Boolean = norm(lang).let { it in audio || it in subs }
        val isEmpty get() = audio.isEmpty() && subs.isEmpty()
    }

    private val iso3to1: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (two in Locale.getISOLanguages()) runCatching { m[Locale.forLanguageTag(two).isO3Language.lowercase()] = two }
        // Bibliographic codes that differ from the terminology codes Java knows
        m.putAll(mapOf("ger" to "de", "fre" to "fr", "dut" to "nl", "cze" to "cs", "gre" to "el", "chi" to "zh", "per" to "fa", "rum" to "ro", "slo" to "sk", "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "geo" to "ka", "ice" to "is", "mac" to "mk", "mao" to "mi", "may" to "ms", "tib" to "bo", "wel" to "cy", "scc" to "sr", "scr" to "hr"))
        m
    }
    private val names = mapOf("polish" to "pl", "polski" to "pl", "english" to "en", "german" to "de", "deutsch" to "de", "french" to "fr", "spanish" to "es", "italian" to "it", "turkish" to "tr", "dutch" to "nl", "arabic" to "ar")

    /** "pol", "pl-PL", "Polish" → "pl"; unknown forms stay as they are (lower case). */
    fun norm(code: String): String {
        val c = code.trim().lowercase().substringBefore('-').substringBefore('_')
        if (c.length == 2) return c
        if (c.length == 3) return iso3to1[c] ?: c
        return names[c] ?: c
    }

    fun of(audio: Iterable<String?>, subs: Iterable<String?>) = Langs(
        audio.mapNotNull { it?.takeIf { s -> s.isNotBlank() }?.let(::norm) }.toSet(),
        subs.mapNotNull { it?.takeIf { s -> s.isNotBlank() }?.let(::norm) }.toSet()
    )

    fun encode(l: Langs?): String = if (l == null || l.isEmpty) "-" else "a=" + l.audio.sorted().joinToString(",") + ";s=" + l.subs.sorted().joinToString(",")

    fun decode(s: String?): Langs? {
        if (s.isNullOrBlank() || s == "-") return null
        var a = emptySet<String>(); var sub = emptySet<String>()
        for (part in s.split(';')) {
            val v = part.substringAfter('=', "").split(',').filter { it.isNotBlank() }.toSet()
            if (part.startsWith("a=")) a = v else if (part.startsWith("s=")) sub = v
        }
        return Langs(a, sub)
    }

    /** Short, stable key of a stream address (addresses can be long; the hash alone could collide). */
    fun key(url: String): String = url.hashCode().toUInt().toString(36) + "_" + url.length.toString(36)
}
