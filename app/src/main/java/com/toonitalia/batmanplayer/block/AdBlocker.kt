package com.toonitalia.batmanplayer.block

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Filtro delle richieste di rete: blocca reti pubblicitarie, popunder, tracker e pixel.
 * Usato da WebViewClient.shouldInterceptRequest() per impedire che le richieste
 * pubblicitarie raggiungano mai la rete.
 */
object AdBlocker {

    /** Reti pubblicitarie / popunder / tracker note. */
    private val BLOCKED_DOMAINS = setOf(
        // Google / DoubleClick
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "googletagservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "stats.g.doubleclick.net",
        // Exchange e DSP
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "adblade.com",
        "adnologic.com",
        "adiquity.com",
        "adtop.com",
        "acuityads.com",
        "amazon-adsystem.com",
        "amazon-adsystem.com",
        "bidswitch.net",
        "brightcom.com",
        "casalemedia.com",
        "cdnsun.com",
        "contextweb.com",
        "districtm.io",
        "emxdgt.com",
        "engagebdr.com",
        "engagementads.io",
        "gemini.yahoo.com",
        "indexww.com",
        "lmtm.net",
        "mediaplex.com",
        "mediavertikal.com",
        "mgid.com",
        "onetag-sys.com",
        "openx.net",
        "pubmatic.com",
        "revcontent.com",
        "rubiconproject.com",
        "sharethrough.com",
        "smartadserver.com",
        "sonobi.com",
        "spotxchange.com",
        "teads.tv",
        "themoneytizer.com",
        "triplelift.com",
        "yieldmo.com",
        "zedo.com",
        // Popunder e ad network da streaming
        "adsterra.com",
        "adcash.com",
        "adskeeper.com",
        "adskeeper.com",
        "ads.ign.com",
        "adtop.ru",
        "bidr.io",
        "clickadu.com",
        "clickbank.net",
        "clkmr.com",
        "criteo.com",
        "criteo.net",
        "exoclick.com",
        "exosrv.com",
        "hilltopads.net",
        "juicyads.com",
        "monetag.com",
        "ntrsrv.com",
        "onclickads.net",
        "popads.net",
        "popcash.net",
        "popplerads.com",
        "propellerads.com",
        "propeller-click.com",
        "propellerclick.com",
        "rotatorads.com",
        "servenobid.com",
        "streamrail.com",
        "taboola.com",
        "trafficgenerator.net",
        "valunder.click",
        "vividcash.com",
        "zerads.com",
        "popblock.me",
        "statcounter.com",
        "histats.com",
        "hit.gemius.pl",
        // Outbrain / taboola widget
        "outbrain.com",
        "zergnet.com",
        "ownedmedia.com"
    )

    /** Sottostringe percorso tipiche delle richieste pubblicitarie. */
    private val BLOCKED_PATH_TOKENS = listOf(
        "/ads/",
        "/ads2/",
        "/adsbygoogle",
        "/adserver/",
        "/adserver2/",
        "/adhandler/",
        "/adframe/",
        "/adslot/",
        "/adsense",
        "/advert",
        "/banner",
        "/popunder",
        "/pop-up",
        "/popup",
        "/clicktrack",
        "/clicktracker",
        "/impression",
        "/beacon",
        "/pixel",
        "/poptracker",
        "/partner-ads",
        "/sponsor",
        "/trackclick",
        "/clicktracker"
    )

    private val MEDIA_EXTENSIONS = listOf(
        ".m3u8", ".mp4", ".webm", ".mkv", ".mov", ".avi", ".m4v", ".ogv", ".flv"
    )

    /** Estensioni/percorsi che NON vanno mai bloccati perché sono il video stesso. */
    fun isMedia(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        val path = lower.substringBefore('?')
        if (MEDIA_EXTENSIONS.any { path.endsWith(it) }) return true
        if (lower.contains("mime=video") || lower.contains("type=video")) return true
        if (lower.contains("/videoplayback")) return true
        if (lower.contains("/hls/") && lower.contains(".m3u8")) return true
        if (lower.contains("/dash/") && lower.contains(".mpd")) return true
        return false
    }

    /** True se l'host appartiene a una rete pubblicitaria nota. */
    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase(Locale.US).removePrefix("www.")
        if (BLOCKED_DOMAINS.any { h == it || h.endsWith(".$it") }) return true
        return false
    }

    /** True se la richiesta deve essere bloccata. */
    fun isBlocked(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase(Locale.US)
        if (lower.startsWith("blob:") || lower.startsWith("data:") || lower.startsWith("about:")) return false
        if (isMedia(url)) return false

        val host = hostOf(lower)
        if (isAdHost(host)) return true

        val path = lower.substringAfter("://").substringBefore('?').substringBefore('#')
        if (BLOCKED_PATH_TOKENS.any { path.contains(it) }) return true

        // query string tipica dei tracker: ?ad_id=, ?clickid=, ?affiliate=, ?banner=
        val query = lower.substringAfter('?', "")
        if (query.isNotEmpty()) {
            val badQuery = listOf("ad_id=", "adid=", "clickid=", "click_id=", "affiliate=", "bannerid=", "ad_type=")
            if (badQuery.any { query.contains(it) }) return true
        }
        return false
    }

    /** Risposta vuota: annulla la richiesta restituendo 200 senza contenuto. */
    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun hostOf(lowerUrl: String): String {
        val afterScheme = lowerUrl.substringAfter("://", lowerUrl)
        val hostPart = afterScheme.substringBefore('/').substringBefore('?')
        val host = hostPart.substringAfterLast('@')
        return host.split(':').firstOrNull().orEmpty()
    }
}
