package com.toonitalia.batmanplayer.extractor

/**
 * Strategia per i player con codice JavaScript "packed":
 * il file sorgente viene spacchettato e da lì si legge l'URL del video
 * (ed eventualmente i sottotitoli .vtt).
 */
class PackedStrategy : ExtractionStrategy {

    override val name: String = "Packed JS"

    private val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
    private val sourceRegex = Regex("""sources?\s*:\s*\[?\s*\{[^}]*?file\s*:\s*["']([^"']+)["']""")
    private val vttRegex = Regex("""https?://[^\s"'\\]+?\.vtt""", RegexOption.IGNORE_CASE)

    override fun extract(content: String, baseUrl: String): ExtractedVideo? {
        val unpacked = JsUnpacker.unpack(content) ?: return null
        val match = fileRegex.find(unpacked) ?: sourceRegex.find(unpacked) ?: return null
        val raw = match.groupValues[1].replace("\\/", "/")
        if (!raw.startsWith("http")) return null

        val subtitles = mutableListOf<ExtractedSubtitle>()
        val seen = HashSet<String>()
        for (vtt in vttRegex.findAll(unpacked)) {
            val url = vtt.value.replace("\\/", "/")
            if (!seen.add(url)) continue
            val label = url.substringAfterLast('/').substringBefore(".vtt").substringAfterLast('_')
            subtitles += ExtractedSubtitle(
                url = url,
                label = label.ifBlank { "Subtitles" },
                default = label.endsWith("eng", ignoreCase = true)
            )
            if (subtitles.size >= 5) break
        }

        return ExtractedVideo(
            source = raw,
            headers = mapOf(
                "Referer" to baseUrl.trimEnd('/') + "/",
                "User-Agent" to Http.USER_AGENT
            ),
            subtitles = subtitles
        )
    }
}
