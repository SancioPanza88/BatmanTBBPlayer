package com.toonitalia.batmanplayer.extractor

/** Risultato dell'estrazione: URL del media + header necessari per riprodurlo. */
data class ExtractedVideo(
    val source: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<ExtractedSubtitle> = emptyList()
)

data class ExtractedSubtitle(
    val url: String,
    val label: String,
    val default: Boolean = false
)

/**
 * Strategie di estrazione del flusso video da una pagina di streaming.
 * Ogni strategia riceve l'HTML (o il contenuto già elaborato) e prova a ricavare
 * l'URL reale del file (.m3u8 / .mp4).
 */
interface ExtractionStrategy {
    val name: String
    fun extract(content: String, baseUrl: String): ExtractedVideo?
}
