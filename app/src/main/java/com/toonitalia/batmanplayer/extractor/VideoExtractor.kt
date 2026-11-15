package com.toonitalia.batmanplayer.extractor

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Estrae l'URL reale del video da una pagina di streaming, esattamente come
 * fanno le app "ad-free": il flusso viene risolto lato server e poi riprodotto
 * dal player nativo, senza banner ne' pubblicita'.
 *
 * Strategie applicate in ordine:
 *  1. provider dedicati (uqload)
 *  2. VOE / payload cifrato
 *  3. codice JavaScript packed
 *  4. ricerca diretta di un URL .m3u8 / .mp4 nella pagina
 */
object VideoExtractor {

    private val strategies = listOf(VoeStrategy(), PackedStrategy(), RegexStrategy())

    suspend fun extract(url: String): ExtractedVideo = withContext(Dispatchers.IO) {
        val host = Uri.parse(url).host.orEmpty().lowercase()
        if (host.isBlank()) throw IllegalArgumentException("URL non valido")
        val base = "https://$host"

        if (host.startsWith("uqload.")) {
            extractUqload(url, base)
        } else {
            val html = Http.get(url, mapOf("Referer" to "$base/", "User-Agent" to Http.USER_AGENT))
            extractFromContent(html, base, url)
        }
    }

    private fun extractUqload(url: String, base: String): ExtractedVideo {
        val code = uqloadCode(url)
        val html = Http.postForm(
            url = "$base/dl",
            fields = mapOf(
                "op" to "embed",
                "file_code" to code,
                "auto" to "1",
                "referer" to url
            ),
            headers = mapOf("Referer" to url, "User-Agent" to Http.USER_AGENT)
        )
        return extractFromContent(html, base, url)
    }

    private fun extractFromContent(content: String, base: String, pageUrl: String): ExtractedVideo {
        for (strategy in strategies) {
            val result = runCatching { strategy.extract(content, base) }.getOrNull()
            if (result != null && result.source.isNotBlank()) {
                val headers = HashMap(result.headers)
                if (!headers.containsKey("Referer")) headers["Referer"] = pageUrl
                return result.copy(headers = headers)
            }
        }
        throw IllegalStateException("Impossibile estrarre il flusso video da $pageUrl")
    }

    /** "https://uqload.vc/e/abc123.html" -> "abc123" */
    private fun uqloadCode(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').removeSuffix(".html").trimEnd('/')
        return path.substringAfterLast('/').substringAfterLast('-')
    }
}
