package com.ipvio.stream

/**
 * Trailer played inside the app (#15): a YouTube video id from TMDB is embedded in a WebView page. The id is checked
 * before it goes into the HTML, so no other text can get in. Pure logic, no Android, testable.
 */
object TrailerLogic {
    private val ID = Regex("^[A-Za-z0-9_-]{6,20}$")

    /** A YouTube video id as TMDB gives it (letters, digits, - and _). */
    fun validKey(key: String?): Boolean = key != null && ID.matches(key)

    private const val NOCOOKIE = "https://www.youtube-nocookie.com"
    private const val YOUTUBE = "https://www.youtube.com"
    private const val APP_ORIGIN = "https://com.ipvio.stream"

    /** One way to load the trailer page: the base address the WebView reports as the page origin, and the page. */
    class Attempt(val baseUrl: String, val html: String)

    /** Address the Trailer button opens: the video for a valid TMDB id, otherwise a YouTube search for the title. */
    fun externalUrl(key: String?, title: String): String =
        if (validKey(key)) "https://www.youtube.com/watch?v=$key"
        else "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode("$title Trailer", "UTF-8")

    /** Number of ways [attempt] knows. */
    const val ATTEMPTS = 3

    /**
     * Way [n] (0..2) to show the trailer, or null for an invalid id or number (T-032, 20 Nov: the cause of the black
     * picture is unknown, so the dialog tries these in turn and writes which one played to Diagnose).
     * 0: as before (origin and embed host youtube-nocookie.com); 1: origin of the app, embed host nocookie;
     * 2: origin of the app, embed host youtube.com.
     */
    fun attempt(n: Int, key: String?, preview: Boolean = false, clearBg: Boolean = false, lang: String = ""): Attempt? = when (n) {
        0 -> embedHtml(key, NOCOOKIE, NOCOOKIE, preview, clearBg, lang)?.let { Attempt(NOCOOKIE, it) }
        1 -> embedHtml(key, NOCOOKIE, APP_ORIGIN, preview, clearBg, lang)?.let { Attempt(APP_ORIGIN, it) }
        2 -> embedHtml(key, YOUTUBE, APP_ORIGIN, preview, clearBg, lang)?.let { Attempt(APP_ORIGIN, it) }
        else -> null
    }

    /** Delay before the trailer starts behind the detail page header, like Prime Video. */
    const val PREVIEW_DELAY_MS = 2_000L

    /**
     * Page for the WebView, or null if the id is not valid. Default: the privacy-enhanced embed host (way 0).
     * [preview]: background trailer of the detail page: no player controls, no keyboard, no fullscreen, no annotations.
     */
    /** A video of a title from TMDB (only the fields the choice needs). */
    class Video(val key: String, val site: String, val type: String, val lang: String?, val official: Boolean)

    /**
     * The trailer to play: YouTube only, a trailer before a teaser, then the language (app language, English, any other),
     * then official before fan uploads, then TMDB's order. Returns the video or null.
     */
    fun pick(videos: List<Video>, ui: String): Video? {
        val yt = videos.filter { it.site.equals("YouTube", true) && validKey(it.key) && (it.type == "Trailer" || it.type == "Teaser") }
        fun langRank(v: Video) = when (v.lang?.lowercase()) { ui.lowercase() -> 0; "en" -> 1; else -> 2 }
        return yt.withIndex().minWithOrNull(compareBy<IndexedValue<Video>>(
            { if (it.value.type == "Trailer") 0 else 1 }, { langRank(it.value) }, { if (it.value.official) 0 else 1 }, { it.index }
        ))?.value
    }

    /**
     * Player language and subtitles: the YouTube interface in the app language ([ui]); with [captions] subtitles are switched
     * on with the app language preferred (shown only if the video has such a track, possibly an automatic one).
     */
    fun langParams(ui: String?, captions: Boolean): String {
        val l = ui?.lowercase()?.takeIf { Regex("^[a-z]{2}$").matches(it) } ?: return ""
        return "&hl=$l" + if (captions) "&cc_load_policy=1&cc_lang_pref=$l" else ""
    }

    /** Subtitles are wanted when the trailer is not in the app language (unknown language counts as foreign). */
    fun wantCaptions(trailerLang: String?, ui: String): Boolean = !trailerLang.equals(ui, ignoreCase = true)

    fun embedHtml(key: String?, host: String = NOCOOKIE, origin: String = host, preview: Boolean = false, clearBg: Boolean = false, lang: String = ""): String? {
        if (!validKey(key)) return null
        val extra = (if (preview) "&controls=0&disablekb=1&fs=0&iv_load_policy=3&modestbranding=1" else "") + lang
        val bg = if (clearBg) "transparent" else "#000"
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:$bg}iframe{border:0;width:100%;height:100%}</style></head>" +
            "<body><iframe id=\"yt\" src=\"$host/embed/$key?autoplay=1&playsinline=1&rel=0$extra" +
            "&enablejsapi=1&origin=$origin\" " +
            "allow=\"autoplay; encrypted-media; picture-in-picture\" allowfullscreen></iframe>" +
            "<script>$DIAG_SCRIPT</script></body></html>"
    }

    /** The player reports "playing" (state 1). */
    fun isPlaying(console: String?): Boolean = console == "IPVIO_TRAILER_STATE 1"

    /** The player reports "ended" (state 0). */
    fun isEnded(console: String?): Boolean = console == "IPVIO_TRAILER_STATE 0"

    /** YouTube player error code of a console line, or null. */
    fun playerError(console: String?): Int? =
        console?.let { DIAG_LINE.matchEntire(it) }?.takeIf { it.groupValues[1] == "ERR" }?.groupValues?.get(2)?.toIntOrNull()

    /** Codes that mean this video cannot be embedded at all (101, 150: owner forbids; 100: removed): other ways will not help. */
    fun isVideoBlocked(code: Int?): Boolean = code == 100 || code == 101 || code == 150

    /**
     * Diagnostics only (T-032): asks the YouTube player to report its state and errors and writes them to the WebView
     * console as `IPVIO_TRAILER_ERR <code>` / `IPVIO_TRAILER_STATE <code>`; when the iframe is loaded it writes `IPVIO_TRAILER_LOAD`. The app reads these lines in `diagLine`. No bridge object is exposed to the page.
     */
    private const val DIAG_SCRIPT =
        "var f=document.getElementById('yt'),last={},started=false;" +
            "function s(o){try{f.contentWindow.postMessage(JSON.stringify(o),'*')}catch(e){}}" +
            "function rep(){var t=Math.round((Number(last.currentTime)||0)*10)/10,v=Math.round(Number(last.volume)||0)," +
            "q=String(last.playbackQuality||'na').replace(/[^a-z0-9]/g,'').slice(0,10)||'na';" +
            "console.log('IPVIO_TRAILER_INFO t='+t+' v='+v+' m='+(last.muted?1:0)+' q='+q)}" +
            "f.addEventListener('load',function(){console.log('IPVIO_TRAILER_LOAD');s({event:'listening',id:1,channel:'widget'});" +
            "s({event:'command',func:'addEventListener',args:['onError'],id:1,channel:'widget'});" +
            "s({event:'command',func:'addEventListener',args:['onStateChange'],id:1,channel:'widget'})});" +
            "window.addEventListener('message',function(e){if(String(e.origin).indexOf('youtube')<0)return;var d;" +
            "try{d=JSON.parse(e.data)}catch(x){return}" +
            "if(d.event==='infoDelivery'&&d.info){var k;for(k in d.info)last[k]=d.info[k]}" +
            "else if(d.event==='onError')console.log('IPVIO_TRAILER_ERR '+parseInt(d.info,10));" +
            "else if(d.event==='onStateChange'){console.log('IPVIO_TRAILER_STATE '+parseInt(d.info,10));" +
            "if(d.info===1&&!started){started=true;rep();" +
            "s({event:'command',func:'unMute',args:'',id:1,channel:'widget'});" +
            "s({event:'command',func:'setVolume',args:[100],id:1,channel:'widget'});" +
            "setTimeout(rep,4000);setTimeout(rep,9000)}}});"

    private val DIAG_LINE = Regex("^IPVIO_TRAILER_(ERR|STATE) (-?\\d{1,4})$")
    private val INFO_LINE = Regex("^IPVIO_TRAILER_INFO t=(\\d{1,5}(?:\\.\\d)?) v=(\\d{1,3}) m=([01]) q=([a-z0-9]{1,10})$")

    /** Diagnose line for a WebView console message of the embed page, or null for any other message. */
    /** Console prefix of [codecScript]: the media type the YouTube player asked for (shows VP9 / AV1 / H.264). */
    const val CODEC_PREFIX = "IPVIO_TRAILER_CODEC "

    /**
     * Script run at document start in the YouTube frame (androidx.webkit DOCUMENT_START_SCRIPT). It reports each media type
     * the player opens. [forceH264]: answers "not supported" for VP9, AV1 and WebM, so YouTube falls back to H.264
     * (TEST for the black picture on TV devices: suspected hardware VP9/AV1 path of the WebView).
     */
    fun codecScript(forceH264: Boolean): String =
        "(function(){try{var re=/vp0?9|av01|webm/i;var force=" + forceH264 + ";" +
            "if(window.MediaSource){var add=MediaSource.prototype.addSourceBuffer;" +
            "MediaSource.prototype.addSourceBuffer=function(t){try{console.log('" + CODEC_PREFIX + "'+t)}catch(e){};return add.call(this,t)};" +
            "if(force&&MediaSource.isTypeSupported){var o=MediaSource.isTypeSupported.bind(MediaSource);" +
            "MediaSource.isTypeSupported=function(t){return re.test(t)?false:o(t)}}}" +
            "if(force){var cp=HTMLMediaElement.prototype.canPlayType;HTMLMediaElement.prototype.canPlayType=function(t){return re.test(t)?'':cp.call(this,t)};" +
            "if(navigator.mediaCapabilities&&navigator.mediaCapabilities.decodingInfo){var d=navigator.mediaCapabilities.decodingInfo.bind(navigator.mediaCapabilities);" +
            "navigator.mediaCapabilities.decodingInfo=function(c){var ct=(c&&c.video&&c.video.contentType)||'';" +
            "return re.test(ct)?Promise.resolve({supported:false,smooth:false,powerEfficient:false}):d(c)}}}" +
            "}catch(e){}})();"

    fun diagLine(console: String?): String? {
        if (console == null) return null
        if (console == "IPVIO_TRAILER_LOAD") return "Trailer: iframe loaded"
        if (console.startsWith(CODEC_PREFIX)) return "Trailer: codec " + console.removePrefix(CODEC_PREFIX).take(80)
        INFO_LINE.matchEntire(console)?.let {
            return "Trailer: info t=${it.groupValues[1]}s vol=${it.groupValues[2]} muted=${it.groupValues[3]} quality=${it.groupValues[4]}"
        }
        val m = DIAG_LINE.matchEntire(console) ?: return null
        val code = m.groupValues[2]
        return if (m.groupValues[1] == "ERR") "Trailer: player error $code" else "Trailer: player state $code"
    }

    /** A video decoder of the device: its name and the media types it decodes. */
    class Decoder(val name: String, val types: List<String>)

    private val CODECS = listOf("H.264" to "video/avc", "VP9" to "video/x-vnd.on2.vp9", "AV1" to "video/av01", "HEVC" to "video/hevc")

    /** Software decoders shipped with Android: they exist on every device but are slow on weak TV sticks. */
    private fun isSoftware(name: String): Boolean =
        name.startsWith("OMX.google.") || name.startsWith("c2.android.") || name.startsWith("c2.google.")

    /**
     * One Diagnose line about the video decoders (T-032: audio plays but the picture stays black, so the device may lack
     * a decoder for the codec YouTube sends). Per codec: `hw` (a non-Android decoder exists), `sw` (only the Android
     * software decoder) or `no`.
     */
    fun decoderLine(decoders: List<Decoder>): String =
        "Trailer: decoders " + CODECS.joinToString(" ") { (label, mime) ->
            val own = decoders.filter { d -> d.types.any { it.equals(mime, ignoreCase = true) } }
            label + "=" + when {
                own.any { !isSoftware(it.name) } -> "hw"
                own.isNotEmpty() -> "sw"
                else -> "no"
            }
        }
}
