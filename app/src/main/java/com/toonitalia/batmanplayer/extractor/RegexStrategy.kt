package com.toonitalia.batmanplayer.extractor

/**
 * Strategia di riserva: cerca direttamente nel codice della pagina un URL
 * di media (.m3u8 / .mp4 / .webm).
 */
class RegexStrategy : ExtractionStrategy {

    override val name: String = "Regex"

    private val mediaRegex = Regex(
        """https?://[^\s"'<>\\]+?\.(?:m3u8|mp4|webm|m4v|mov)(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE
    )

    override fun extract(content: String, baseUrl: String): ExtractedVideo? {
        val cleaned = content.replace("\\/", "/")
        val match = mediaRegex.find(cleaned) ?: return null
        val source = match.value
        if (source.length < 12) return null
        return ExtractedVideo(
            source = source,
            headers = mapOf(
                "Referer" to baseUrl.trimEnd('/') + "/",
                "User-Agent" to Http.USER_AGENT
            )
        )
    }
}
