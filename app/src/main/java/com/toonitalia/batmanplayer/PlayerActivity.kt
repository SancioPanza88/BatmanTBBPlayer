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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.android.material.snackbar.Snackbar
import com.toonitalia.batmanplayer.block.AdBlockScripts
import com.toonitalia.batmanplayer.block.AdBlocker
import com.toonitalia.batmanplayer.databinding.ActivityPlayerBinding
import com.toonitalia.batmanplayer.util.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Player di un episodio.
 *
 * Strategia:
 *  1. la pagina dell'episodio viene caricata in una WebView con filtro delle richieste
 *     (nessuna richiesta pubblicitaria raggiunge la rete) e pulizia del DOM;
 *  2. non appena viene individuato il flusso video reale (.m3u8 / .mp4) la riproduzione
 *     passa al player nativo (ExoPlayer / Media3): video senza banner e senza pubblicità;
 *  3. se il flusso non è estraibile la WebView resta in uso, sempre filtrata.
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
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }

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
    private var customView: View? = null
    private var episodeTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        episodeTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        url1 = intent.getStringExtra(EXTRA_URL1).orEmpty()
        url2 = intent.getStringExtra(EXTRA_URL2).orEmpty()
        sourceIndex = intent.getIntExtra(EXTRA_SOURCE, prefs.sourceIndex)

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
            if (binding.webView.canGoBack()) {
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

    private fun updateStatus() {
        val blocked = blockedCount.get()
        binding.status.text = if (prefs.adBlockEnabled) {
            when {
                nativeMode -> getString(R.string.blocked_requests, blocked) +
                    "  •  " + getString(R.string.media_found)
                else -> getString(R.string.blocked_requests, blocked)
            }
        } else {
            getString(R.string.adblock_off)
        }
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
            userAgentString = DESKTOP_UA
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
        releaseNativePlayer()
        binding.playerView.isVisible = false
        binding.webView.isVisible = true
        binding.loading.isVisible = true
        binding.playerToolbar.subtitle =
            if (sourceIndex == 1) getString(R.string.source_chuckle) else getString(R.string.source_uqload)
        binding.webView.loadUrl(target)
        updateStatus()
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
            // le navigazioni verso siti pubblicitari sono già state bloccate sopra
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
            customView?.let { binding.contentContainer.removeView(it) }
            customView = null
            binding.playerToolbar.isVisible = true
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
            if (prefs.preferNativePlayer && !nativeMode && url.startsWith("http")) {
                startNativePlayer(url)
            }
        }
    }

    private fun startNativePlayer(url: String) {
        try {
            releaseNativePlayer()

            val headers = HashMap<String, String>()
            headers["Referer"] = currentUrl
            headers["Origin"] = originOf(currentUrl)
            headers["User-Agent"] = DESKTOP_UA

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(DESKTOP_UA)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)

            val player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()

            player.addListener(nativeListener)

            nativePlayer = player
            binding.playerView.player = player
            player.setMediaItem(MediaItem.fromUri(url))
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
            updateStatus()
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

    private fun originOf(url: String): String {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return "https://$scheme"
        return "$scheme://$host"
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
