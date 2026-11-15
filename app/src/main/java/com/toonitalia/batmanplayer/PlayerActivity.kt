package com.toonitalia.batmanplayer

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.snackbar.Snackbar
import com.toonitalia.batmanplayer.block.AdBlockScripts
import com.toonitalia.batmanplayer.block.AdBlocker
import com.toonitalia.batmanplayer.databinding.ActivityPlayerBinding
import com.toonitalia.batmanplayer.extractor.ExtractedVideo
import com.toonitalia.batmanplayer.extractor.VideoExtractor
import com.toonitalia.batmanplayer.player.NativePlayerFactory
import com.toonitalia.batmanplayer.util.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Player di un episodio.
 *
 * Due modalita' di riproduzione, come nelle app "ad-free":
 *  1. **Estrazione server** (predefinita): la pagina del provider viene analizzata
 *     lato app, si ricava l'URL reale del video (.m3u8/.mp4) e la riproduzione
 *     avviene con il player nativo ExoPlayer -> nessun banner, nessuna pubblicita',
 *     controlli nativi e supporto HLS;
 *  2. **WebView filtrata**: fallback (o modalita' manuale) in cui la pagina viene
 *     caricata in una WebView con filtro delle richieste pubblicitarie e pulizia
 *     del DOM: anche qui la pubblicita' non viene mostrata.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_URL1 = "extra_url1"
        const val EXTRA_URL2 = "extra_url2"
        const val EXTRA_SOURCE = "extra_source"

        private const val TAG = "BatmanPlayer"
    }

    private enum class PlayMode { EXTRACT, WEB }

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var prefs: Prefs

    private var url1: String = ""
    private var url2: String = ""
    private var sourceIndex: Int = 0
    private var currentUrl: String = ""
    private val blockedCount = AtomicInteger(0)
    private var mediaCandidate: String? = null
    private var nativePlayer: ExoPlayer? = null
    private var nativeMode: Boolean = false
    private var cleanupJob: Job? = null
    private var extractionJob: Job? = null
    private var customView: View? = null
    private var episodeTitle: String = ""
    private var playMode: PlayMode = PlayMode.EXTRACT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        episodeTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        url1 = intent.getStringExtra(EXTRA_URL1).orEmpty()
        url2 = intent.getStringExtra(EXTRA_URL2).orEmpty()
        sourceIndex = intent.getIntExtra(EXTRA_SOURCE, prefs.sourceIndex)
        playMode = if (prefs.useWebMode) PlayMode.WEB else PlayMode.EXTRACT

        setupToolbar()
        setupWebView()
        setupBackHandling()
        loadCurrentSource()
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this) {
            if (customView != null) {
                hideCustomView()
                return@addCallback
            }
            if (binding.webView.isVisible && binding.webView.canGoBack()) {
                binding.webView.goBack()
                return@addCallback
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    // ------------------------------------------------------------------ UI

    private fun setupToolbar() {
        binding.playerToolbar.title = episodeTitle.ifBlank { getString(R.string.app_name) }
        binding.playerToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.playerToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_switch_source -> {
                    switchSource()
                    true
                }

                R.id.action_reload -> {
                    loadCurrentSource()
                    true
                }

                R.id.action_fullscreen -> {
                    toggleFullscreen()
                    true
                }

                R.id.action_open_browser -> {
                    openInBrowser()
                    true
                }

                R.id.action_copy_link -> {
                    copyLink()
                    true
                }

                R.id.action_toggle_mode -> {
                    togglePlayMode()
                    true
                }

                R.id.action_toggle_adblock -> {
                    prefs.adBlockEnabled = !prefs.adBlockEnabled
                    updateStatus()
                    true
                }

                else -> false
            }
        }
    }

    private fun switchSource() {
        sourceIndex = if (sourceIndex == 1) 0 else 1
        prefs.sourceIndex = sourceIndex
        loadCurrentSource()
    }

    private fun togglePlayMode() {
        playMode = if (playMode == PlayMode.EXTRACT) PlayMode.WEB else PlayMode.EXTRACT
        prefs.useWebMode = playMode == PlayMode.WEB
        Snackbar.make(
            binding.root,
            if (playMode == PlayMode.EXTRACT) R.string.native_player else R.string.web_player,
            Snackbar.LENGTH_SHORT
        ).show()
        loadCurrentSource()
    }

    private fun toggleFullscreen() {
        if (binding.playerToolbar.isVisible) {
            binding.playerToolbar.isVisible = false
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            binding.webView.evaluateJavascript(AdBlockScripts.FULLSCREEN_JS, null)
        } else {
            binding.playerToolbar.isVisible = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun openInBrowser() {
        val target = currentUrl.ifBlank { url1 }.ifBlank { url2 }
        if (target.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        } catch (e: Exception) {
            Snackbar.make(binding.root, R.string.no_source, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun copyLink() {
        val target = currentUrl.ifBlank { url1 }.ifBlank { url2 }
        if (target.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("link", target))
        Snackbar.make(binding.root, R.string.copy_link, Snackbar.LENGTH_SHORT).show()
    }

    private fun updateStatus(extra: String? = null) {
        val blocked = blockedCount.get()
        val base = if (prefs.adBlockEnabled) {
            getString(R.string.blocked_requests, blocked)
        } else {
            getString(R.string.adblock_off)
        }
        binding.status.text = if (extra.isNullOrBlank()) base else "$base  •  $extra"
        binding.status.isVisible = true
    }

    // ------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = binding.webView
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = com.toonitalia.batmanplayer.extractor.Http.USER_AGENT
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
        }
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        } catch (e: Exception) {
            Log.w(TAG, "Cookie manager non disponibile: ${e.message}")
        }
        wv.setDownloadListener { _, _, _, _, _ ->
            Snackbar.make(binding.root, R.string.downloaded_blocked, Snackbar.LENGTH_SHORT).show()
        }
        wv.addJavascriptInterface(MediaSniffer(), "bbReport")
        wv.webViewClient = BlockingWebViewClient()
        wv.webChromeClient = PlayerChromeClient()
    }

    private fun loadCurrentSource() {
        val target = if (sourceIndex == 1) url2 else url1
        if (target.isBlank()) {
            Snackbar.make(binding.root, R.string.no_source, Snackbar.LENGTH_LONG).show()
            return
        }
        currentUrl = target
        mediaCandidate = null
        nativeMode = false
        cleanupJob?.cancel()
        extractionJob?.cancel()
        releaseNativePlayer()
        binding.playerView.isVisible = false
        binding.webView.isVisible = false
        binding.loading.isVisible = true
        binding.playerToolbar.subtitle =
            if (sourceIndex == 1) getString(R.string.source_chuckle) else getString(R.string.source_uqload)
        updateStatus()

        if (playMode == PlayMode.EXTRACT) {
            runExtraction(target)
        } else {
            startWebView(target)
        }
    }

    private fun runExtraction(target: String) {
        extractionJob = lifecycleScope.launch {
            updateStatus(getString(R.string.extracting))
            try {
                val video = VideoExtractor.extract(target)
                if (!isActive) return@launch
                binding.loading.isVisible = false
                Log.i(TAG, "Flusso estratto: ${video.source}")
                startNativePlayer(video)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isActive) return@launch
                binding.loading.isVisible = false
                Log.w(TAG, "Estrazione fallita: ${e.message}")
                Snackbar.make(binding.root, R.string.extract_failed, Snackbar.LENGTH_LONG).show()
                startWebView(target)
            }
        }
    }

    private fun startWebView(url: String) {
        binding.webView.isVisible = true
        binding.loading.isVisible = true
        binding.webView.loadUrl(url)
    }

    private inner class BlockingWebViewClient : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            if (prefs.adBlockEnabled && AdBlocker.isBlocked(url)) {
                blockedCount.incrementAndGet()
                runOnUiThread { updateStatus() }
                return AdBlocker.emptyResponse()
            }
            if (AdBlocker.isMedia(url)) {
                onMediaCandidate(url)
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()

            if (prefs.adBlockEnabled && AdBlocker.isBlocked(url)) {
                blockedCount.incrementAndGet()
                updateStatus()
                return true
            }
            if (scheme != null && scheme != "http" && scheme != "https") {
                // blocca javascript:, intent:, market: ecc.
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            currentUrl = url
            view.evaluateJavascript(AdBlockScripts.SNIFFER_JS, null)
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            binding.loading.isVisible = false
            view.evaluateJavascript(AdBlockScripts.SNIFFER_JS, null)
            view.evaluateJavascript(AdBlockScripts.CLEANUP_JS, null)
            startCleanupLoop()
        }
    }

    private inner class PlayerChromeClient : WebChromeClient() {

        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            (view.parent as? ViewGroup)?.removeView(view)
            binding.contentContainer.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            binding.playerToolbar.isVisible = false
        }

        override fun onHideCustomView() {
            hideCustomView()
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(TAG, consoleMessage.message())
            return true
        }
    }

    private inner class MediaSniffer {
        @JavascriptInterface
        fun report(url: String) {
            if (url.isBlank()) return
            if (AdBlocker.isMedia(url)) onMediaCandidate(url)
        }
    }

    // -------------------------------------------------- pulizia / blocco ads

    private fun startCleanupLoop() {
        cleanupJob?.cancel()
        cleanupJob = lifecycleScope.launch {
            repeat(30) {
                delay(1000)
                if (nativeMode) return@launch
                binding.webView.evaluateJavascript(AdBlockScripts.CLEANUP_JS, null)
            }
        }
    }

    // -------------------------------------------------------- player nativo

    private fun onMediaCandidate(url: String) {
        if (mediaCandidate == url) return
        mediaCandidate = url
        runOnUiThread {
            if (!nativeMode && binding.webView.isVisible && url.startsWith("http")) {
                // il flusso intercettato nella WebView passa comunque al player nativo
                startNativePlayer(ExtractedVideo(url, mapOf("Referer" to currentUrl)))
            }
        }
    }

    private fun startNativePlayer(video: ExtractedVideo) {
        try {
            releaseNativePlayer()

            val headers = HashMap(video.headers)
            if (!headers.containsKey("Referer")) headers["Referer"] = currentUrl
            headers["User-Agent"] = com.toonitalia.batmanplayer.extractor.Http.USER_AGENT

            val player = NativePlayerFactory.create(this, headers)
            player.addListener(nativeListener)

            nativePlayer = player
            binding.playerView.player = player

            val mediaItem = if (video.subtitles.isEmpty()) {
                MediaItem.fromUri(video.source)
            } else {
                MediaItem.Builder()
                    .setUri(video.source)
                    .setSubtitleConfigurations(
                        video.subtitles.map { subtitle ->
                            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage(subtitle.label)
                                .setSelectionFlags(
                                    if (subtitle.default) C.SELECTION_FLAG_DEFAULT else 0
                                )
                                .build()
                        }
                    )
                    .build()
            }
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true

            nativeMode = true
            binding.playerView.isVisible = true
            binding.webView.isVisible = false
            binding.loading.isVisible = false

            // ferma l'eventuale riproduzione dentro la WebView
            binding.webView.evaluateJavascript(
                "try{var v=document.querySelector('video');if(v){v.pause();v.removeAttribute('autoplay');}}catch(e){}",
                null
            )
            updateStatus(getString(R.string.media_found))
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile avviare il player nativo: ${e.message}")
            fallbackToWeb()
        }
    }

    private val nativeListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Errore player nativo: ${error.errorCodeName}")
            runOnUiThread { fallbackToWeb() }
        }
    }

    private fun releaseNativePlayer() {
        nativePlayer?.let {
            it.removeListener(nativeListener)
            it.release()
        }
        nativePlayer = null
        binding.playerView.player = null
    }

    private fun fallbackToWeb() {
        if (!nativeMode && nativePlayer == null) return
        nativeMode = false
        releaseNativePlayer()
        binding.playerView.isVisible = false
        binding.webView.isVisible = true
        Snackbar.make(binding.root, R.string.native_error, Snackbar.LENGTH_LONG).show()
        updateStatus()
    }

    // ------------------------------------------------------------ lifecycle

    override fun onPause() {
        super.onPause()
        nativePlayer?.pause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        cleanupJob?.cancel()
        extractionJob?.cancel()
        releaseNativePlayer()
        binding.webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Gestito tramite OnBackPressedDispatcher")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun hideCustomView() {
        customView?.let { binding.contentContainer.removeView(it) }
        customView = null
        binding.playerToolbar.isVisible = true
    }
}
