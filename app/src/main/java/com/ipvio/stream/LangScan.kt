package com.ipvio.stream

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * "Check languages" for a list of movies: reads the audio and subtitle languages from each file, one title after the
 * other (one connection to the provider at a time), and keeps the result on the device, so the list can then be
 * filtered by the app language. Stops by itself when playback starts ([stop] from the player start).
 */
object LangScan {
    private const val PREFS = "ipvio_langscan"
    /** Pause between two titles: gentle on the provider. */
    private const val GAP_MS = 400L
    /** In memory only: the file could not be read this session (offline, timeout); checked again next time. */
    private const val FAILED = "?"

    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    /** Changes with every new result (the list filter recomputes). */
    var version by mutableIntStateOf(0)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var mem: MutableMap<String, String>? = null

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(c: Context): MutableMap<String, String> = mem ?: synchronized(this) {
        mem ?: java.util.concurrent.ConcurrentHashMap<String, String>().also { m -> prefs(c).all.forEach { (k, v) -> if (v is String) m[k] = v }; mem = m }
    }

    /** Known result of a title: null = not checked yet; [LangScanLogic.Langs] with empty sets = checked, nothing found. */
    fun result(c: Context, i: Item): LangScanLogic.Langs? {
        val v = load(c)[LangScanLogic.key(i.url)] ?: return null
        if (v == FAILED) return null
        return LangScanLogic.decode(v) ?: LangScanLogic.Langs(emptySet(), emptySet())
    }

    fun checked(c: Context, items: List<Item>): Int { val m = load(c); return items.count { LangScanLogic.key(it.url) in m } }

    fun start(c: Context, items: List<Item>) {
        val ctx = c.applicationContext
        stop()
        val todo = items.filter { it.kind == Kind.MOVIE && LangScanLogic.key(it.url) !in load(ctx) }
        total = todo.size; done = 0
        if (todo.isEmpty()) return
        running = true
        job = scope.launch {
            try {
                for (i in todo) {
                    if (!isActive) break
                    val info = MediaProbe.probe(ctx, i.url)
                    val l = info?.let { LangScanLogic.of(it.audio.map { a -> a.language }, it.subtitles) }
                    val k = LangScanLogic.key(i.url)
                    if (info == null) load(ctx)[k] = FAILED
                    else { val v = LangScanLogic.encode(l); load(ctx)[k] = v; prefs(ctx).edit().putString(k, v).apply() }
                    done++; version++
                    delay(GAP_MS)
                }
            } finally {
                running = false
            }
        }
    }

    fun stop() { job?.cancel(); job = null; running = false }
}
