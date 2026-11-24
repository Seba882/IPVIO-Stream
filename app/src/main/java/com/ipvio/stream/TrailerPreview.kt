package com.ipvio.stream

import android.content.Context
import android.content.ContextWrapper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay

/** Seconds a way gets to report "playing" before the next way is tried (same rule as the trailer dialog, T-032). */
private const val PREVIEW_ATTEMPT_MS = 10_000L

private fun activityOf(c: Context): ComponentActivity? =
    generateSequence(c) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<ComponentActivity>().firstOrNull()

/**
 * Trailer behind the header of the detail page, like Prime Video: starts [TrailerLogic.PREVIEW_DELAY_MS] after
 * [active] turns true, stays invisible (the backdrop shows through) until YouTube reports "playing", then fades in.
 * Ends silently: when the trailer is over, when [active] turns false (focus left the header, page closed), when the
 * app goes to the background, or when no way plays within 10 s each. Never opens YouTube outside the app and never
 * takes focus, so the remote keeps working on the buttons.
 */
@Composable
fun TrailerPreview(key: String?, active: Boolean, modifier: Modifier = Modifier) {
    if (!TrailerLogic.validKey(key)) return
    val ctx = LocalContext.current
    var resumed by remember { mutableStateOf(true) }
    val lifecycle = remember(ctx) { activityOf(ctx)?.lifecycle }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_PAUSE -> resumed = false
                Lifecycle.Event.ON_RESUME -> resumed = true
                else -> {}
            }
        }
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    val run = active && resumed
    // A new run (focus back on the header, back from the background) starts the trailer again after the delay
    var started by remember(key, run) { mutableStateOf(false) }
    var attempt by remember(key, run) { mutableStateOf(0) }
    var playing by remember(key, run) { mutableStateOf(false) }
    var done by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, run) {
        if (!run || done) return@LaunchedEffect
        delay(TrailerLogic.PREVIEW_DELAY_MS)
        started = true
    }
    LaunchedEffect(key, run, started, attempt) {
        if (!started) return@LaunchedEffect
        delay(PREVIEW_ATTEMPT_MS)
        if (!playing) {
            Diag.report("Trailer preview: way ${attempt + 1} no playback after 10 s")
            if (attempt + 1 < TrailerLogic.ATTEMPTS) attempt++ else done = true
        }
    }
    val alpha by animateFloatAsState(if (playing) 1f else 0f, tween(700), label = "trailerPreview")
    if (!run || !started || done) return
    // TEST (black picture on TV devices). Mode 1/2: translucent window (+ transparent hole); both ruled out on Fire TV.
    // Mode 3: the WebView as a plain Android view on top of the window, outside Compose.
    val mode = General.trailerMode
    val activity = remember(ctx) { activityOf(ctx) }
    val window = activity?.window
    DisposableEffect(playing, mode) {
        val w = window
        if (!playing || (mode != 1 && mode != 2) || w == null) return@DisposableEffect onDispose { }
        val old = w.attributes.format
        w.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        Diag.report("Trailer preview: picture mode ${mode + 1} (window format $old -> translucent)")
        onDispose { w.setFormat(old) }
    }
    if (playing && mode == 2) Spacer(modifier.drawBehind { drawRect(Color.Black, blendMode = BlendMode.Clear) })
    androidx.compose.runtime.key(attempt) {
        val n = attempt
        val holder = remember { arrayOfNulls<WebView>(1) }
        DisposableEffect(Unit) {
            onDispose {
                holder[0]?.let { w ->
                    (w.parent as? android.view.ViewGroup)?.removeView(w)
                    w.stopLoading(); w.loadUrl("about:blank"); w.destroy()
                }
                holder[0] = null
            }
        }
        val way = TrailerLogic.attempt(n, key, preview = true, clearBg = mode == 1 || mode == 2)
        val make: (Context) -> WebView = { c ->
            WebView(c).apply {
                isFocusable = false
                isFocusableInTouchMode = false
                setBackgroundColor(if (mode == 1 || mode == 2) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                        request?.isForMainFrame != false
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                        val msg = m?.message()
                        TrailerLogic.diagLine(msg)?.let { Diag.report("$it (preview way ${n + 1}, mode ${mode + 1})") }
                        if (TrailerLogic.isPlaying(msg)) playing = true
                        // Over, or YouTube forbids embedding: back to the still backdrop
                        if (TrailerLogic.isEnded(msg) || TrailerLogic.isVideoBlocked(TrailerLogic.playerError(msg))) done = true
                        return true
                    }
                }
                // Codec report in every mode; mode 5 also hides VP9/AV1 so YouTube sends H.264 (needs DOCUMENT_START_SCRIPT)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    runCatching {
                        WebViewCompat.addDocumentStartJavaScript(this, TrailerLogic.codecScript(mode == 4),
                            setOf("https://www.youtube-nocookie.com", "https://www.youtube.com"))
                    }.onFailure { Diag.report("Trailer preview: codec script failed (${it.javaClass.simpleName})") }
                } else Diag.report("Trailer preview: WebView without document start scripts")
                if (way != null) loadDataWithBaseURL(way.baseUrl, way.html, "text/html", "utf-8", null)
                holder[0] = this
            }
        }
        if (way == null) {
            // invalid id: nothing to show
        } else if (mode != 3) {
            AndroidView(modifier = modifier.alpha(alpha), factory = make)
        } else {
            // Placeholder in the layout gives the place; the WebView itself sits in the activity's content frame
            val content = remember(activity) { activity?.findViewById<android.widget.FrameLayout>(android.R.id.content) }
            Spacer(modifier.onGloballyPositioned { lc ->
                val frame = content ?: return@onGloballyPositioned
                val b = lc.boundsInWindow()
                val loc = IntArray(2).also { frame.getLocationInWindow(it) }
                val lp = android.widget.FrameLayout.LayoutParams(b.width.toInt(), b.height.toInt()).apply {
                    leftMargin = b.left.toInt() - loc[0]; topMargin = b.top.toInt() - loc[1]
                }
                val w = holder[0] ?: make(frame.context).also {
                    frame.addView(it, lp)
                    Diag.report("Trailer preview: picture mode 4 (plain view ${lp.width}x${lp.height})")
                }
                if (w.parent != null && (w.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
                        it.width != lp.width || it.height != lp.height || it.leftMargin != lp.leftMargin || it.topMargin != lp.topMargin
                    } == true) w.layoutParams = lp
                // Transparent until YouTube reports playing (not INVISIBLE: a hidden WebView may pause the video)
                w.alpha = if (playing) 1f else 0f
            })
            // Visibility also follows "playing" without a new layout pass
            androidx.compose.runtime.SideEffect { holder[0]?.alpha = if (playing) 1f else 0f }
        }
    }
}
