package com.ipvio.stream

/** Extra TMDB data of a title (keywords, title logo), pure logic. */
object TmdbExtraLogic {
    /** Keyword names as stored: lower case, without duplicates, at most 25. */
    fun keywords(names: List<String>): List<String> =
        names.map { it.trim().lowercase() }.filter { it.isNotEmpty() && '|' !in it }.distinct().take(25)

    /** Christmas title by its keywords ("christmas", "santa claus", "christmas eve" …). */
    fun isXmas(keywords: List<String>): Boolean = keywords.any { SeasonLogic.isXmas(it) }

    /** A logo of the title: file path and text language (null = no text language). */
    class Logo(val path: String, val lang: String?, val votes: Double = 0.0)

    /** Title logo: app language, then English, then without language; within that the best rated. */
    fun pickLogo(logos: List<Logo>, ui: String): String? {
        fun rank(l: Logo) = when (l.lang?.lowercase()) { ui.lowercase() -> 0; "en" -> 1; null, "", "xx" -> 2; else -> 3 }
        return logos.filter { it.path.startsWith("/") && rank(it) < 3 }
            .minWithOrNull(compareBy<Logo>({ rank(it) }, { -it.votes }))?.path
    }

    /** A movie of a TMDB collection (saga). */
    class Part(val tmdbId: Int, val title: String?, val original: String?, val year: Int?)

    /**
     * The parts of a collection that are in the playlist, in the order of the saga (year): by the TMDB id where the app
     * knows it ([tmdbIdOf]), otherwise by title (localized or original) and year (±1, release dates differ by country).
     */
    fun inPlaylist(parts: List<Part>, items: List<Item>, tmdbIdOf: (Item) -> Int?): List<Pair<Part, Item>> {
        val byTitle = HashMap<String, ArrayList<Item>>()
        for (i in items) if (i.kind == Kind.MOVIE) byTitle.getOrPut(MatchLogic.norm(MatchLogic.searchTitle(i.title))) { ArrayList() }.add(i)
        val out = ArrayList<Pair<Part, Item>>()
        val used = HashSet<String>()
        for (p in parts.sortedWith(compareBy({ it.year ?: Int.MAX_VALUE }, { it.title }))) {
            val hit = items.firstOrNull { it.kind == Kind.MOVIE && it.id !in used && tmdbIdOf(it) == p.tmdbId }
                ?: listOfNotNull(MatchLogic.norm(p.title), MatchLogic.norm(p.original)).filter { it.isNotEmpty() }.distinct()
                    .flatMap { byTitle[it].orEmpty() }
                    .firstOrNull { it.id !in used && (p.year == null || it.year == null || kotlin.math.abs(it.year - p.year) <= 1) }
            if (hit != null) { used.add(hit.id); out.add(p to hit) }
        }
        return out
    }
}
