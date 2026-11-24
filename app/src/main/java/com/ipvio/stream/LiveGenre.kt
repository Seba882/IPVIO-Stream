package com.ipvio.stream

/**
 * Channels by genre (1.2): "Sports", "Movies", "Documentaries", "Kids", "News", "Music".
 * Detected from the playlist's category and channel name – not perfect, but no effort for you.
 */
object LiveGenre {
    class Genre(val key: String, val label: String, val re: Regex?)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    // Polish (and other) words added for providers that name their groups in the local language ("FILMY I SERIALE",
    // "DOKUMENTALNE", "DLA DZIECI", "INFORMACYJNE", "MUZYCZNE"); two new genres: entertainment and lifestyle.
    val ALL = listOf(
        Genre("GEN:sport", "Sport", null),   // sport is detected by SportLogic
        Genre("GEN:movies", "Filme", rx("""\b(films?|filmy|filmow\w*|movies?|cinema\w*|kino|cine\w*|sinema\w*|filmovi|premiere|cinemax|hbo|serien|series|seriale?|serije|dizi|filmbox|epic\s?drama|axn|amc)\b""")),
        Genre("GEN:doku", "Dokumentationen", rx("""documentar\w*|\bdoku\w*|dokument\w*|discovery|nat\s?geo|national\s?geographic|\bhistory\b|animal\s?planet|\bdmax\b|nautilus|explore|\bscience\b|dokumentar\w*|\bviasat\s?(history|nature|explore)""")),
        Genre("GEN:kids", "Kinder", rx("""\bkids?\b|kinder|children|cartoon\w*|\bnick\w*|disney|junior|\bbaby\w*|toon\w*|boomerang|ragazzi|çocuk|djeca|dječji|\bkika\b|jetix|\bpogo\b|dzieci|dziecię\w*|bajk\w*|mini\s?mini|teletoon|cbeebies|duck\s?tv|\bpolsat\s?jim\s?jam""")),
        Genre("GEN:news", "Nachrichten", rx("""\bnews\b|nachrichten|\bn-?tv\b|\bcnn\b|euronews|al\s?jazeera|tagesschau|\bwelt\b|haber|vijesti|noticias|\bnotizie\b|\binfo(rmation)?\b|\brtl\s?aktuell\b|informac\w*|wiadomo\w*|\btvn\s?24|\btvp\s?info|wpolsce|\btv\s?republika|\bbiznes\b""")),
        Genre("GEN:music", "Musiksender", rx("""\bmusic\w*|musik|\bmtv\b|\bvh1\b|\bviva\b|\bklubbing\b|\bradio\b|\bmuzik\w*|muzy\w*|\bpolo\s?tv|\beska\s?tv|\b4fun|\bvox\s?music|\bstars\s?tv""")),
        Genre("GEN:fun", "Unterhaltung", rx("""entertain\w*|unterhaltung|rozrywk\w*|ogólno\w*|general\b|eğlence|zabav\w*|\bcomedy|komedi\w*|\bshow\b""")),
        Genre("GEN:life", "Lifestyle", rx("""lifestyle|\btlc\b|\bhgtv\b|food\s?network|\btravel|kuchni\w*|cuisine|fashion|\bstyle\b|zdrowi\w*|\bhome\b|dom\s?i\s?ogr\w*|\bwomen\b|\bkobiet\w*|\bid\s?(investigation|xtra)"""))
    )

    /** Channels in none of the genres. Only shown when "Automatic genres" hides the provider's groups. */
    val OTHER = Genre("GEN:other", "Weitere Sender", null)

    fun byKey(k: String): Genre? = if (k == OTHER.key) OTHER else ALL.firstOrNull { it.key == k }

    /**
     * Genres as listed in Live TV and the TV guide. [auto] ("Automatic genres" on): the provider's groups are hidden,
     * so channels in no genre get their own entry [OTHER] (otherwise they would only be under "All channels").
     */
    fun listed(live: List<Item>, auto: Boolean): Map<String, List<Item>> {
        val g = split(live)
        if (!auto) return g
        val inGenre = HashSet<String>()
        for (l in g.values) for (i in l) inGenre.add(i.id)
        val rest = live.filter { it.id !in inGenre }
        if (rest.isEmpty()) return g
        val out = LinkedHashMap(g)
        out[OTHER.key] = rest
        return out
    }

    fun isGenreKey(k: String) = k.startsWith("GEN:")

    fun matches(g: Genre, i: Item): Boolean =
        if (g.re == null) SportLogic.isSportChannel(i) else g.re.containsMatchIn(i.group) || g.re.containsMatchIn(i.title)

    /** Channels per genre; empty genres are missing. */
    fun split(live: List<Item>): LinkedHashMap<String, List<Item>> {
        // The same list is asked again and again (every recomposition, every tab): remember the last result
        synchronized(this) { last.firstOrNull { it.first === live }?.let { return it.second } }
        val r = compute(live)
        // Three results: a filtered source in Live TV no longer evicts the prepared result of all channels
        synchronized(this) { last.add(0, live to r); while (last.size > 3) last.removeAt(last.size - 1) }
        return r
    }

    private val last = ArrayList<Pair<List<Item>, LinkedHashMap<String, List<Item>>>>()

    private fun compute(live: List<Item>): LinkedHashMap<String, List<Item>> {
        val out = LinkedHashMap<String, List<Item>>()
        for (g in ALL) {
            val l = live.filter { matches(g, it) }
            if (l.isNotEmpty()) out[g.key] = l
        }
        return out
    }
}
