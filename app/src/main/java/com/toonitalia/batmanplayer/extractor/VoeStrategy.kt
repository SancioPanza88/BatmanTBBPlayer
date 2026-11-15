package com.toonitalia.batmanplayer.extractor

import android.util.Base64
import org.json.JSONObject

/**
 * Strategia per i player "VOE" (usati ad esempio da chuckle-tube.com).
 *
 * La pagina contiene un payload cifrato in:
 *   <script type="application/json">...</script>
 *
 * Il cifrario (rot13 + sostituzioni + base64 + shift + reverse) e' invertibile
 * lato client: una volta decifrato si ottiene un JSON con il campo "source"
 * (URL diretto del video .m3u8) ed eventualmente i sottotitoli.
 */
class VoeStrategy : ExtractionStrategy {

    override val name: String = "VOE"

    private val encodedPattern =
        Regex("""<script[^>]*type=["']application/json["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    override fun extract(content: String, baseUrl: String): ExtractedVideo? {
        val encoded = encodedPattern.find(content)?.groupValues?.get(1)?.trim().orEmpty()
        if (encoded.isEmpty()) return null

        val json = runCatching { decrypt(encoded) }.getOrNull() ?: return null
        val source = json.optString("source")
        if (source.isBlank()) return null
        if (!source.startsWith("http")) return null

        val subtitles = mutableListOf<ExtractedSubtitle>()
        val captions = json.optJSONArray("captions")
        if (captions != null) {
            for (i in 0 until captions.length()) {
                val caption = captions.optJSONObject(i) ?: continue
                val file = caption.optString("file")
                if (file.isBlank()) continue
                subtitles += ExtractedSubtitle(
                    url = if (file.startsWith("http")) file else baseUrl.trimEnd('/') + file,
                    label = caption.optString("label", "Subtitles"),
                    default = caption.optBoolean("default", false)
                )
            }
        }

        return ExtractedVideo(
            source = source.replace("\\/", "/"),
            headers = mapOf(
                "Referer" to baseUrl.trimEnd('/') + "/",
                "User-Agent" to Http.USER_AGENT
            ),
            subtitles = subtitles
        )
    }

    private fun decrypt(encoded: String): JSONObject {
        var value = rot13(encoded)
        for (pattern in PATTERNS) {
            value = value.replace(pattern, "_")
        }
        value = value.replace("_", "")
        val first = Base64.decode(padBase64(value), Base64.NO_WRAP).toString(Charsets.UTF_8)
        val shifted = StringBuilder(first.length)
        for (ch in first) {
            shifted.append((ch.code - 3).toChar())
        }
        val reversed = shifted.reverse().toString()
        val json = Base64.decode(padBase64(reversed), Base64.NO_WRAP).toString(Charsets.UTF_8)
        return JSONObject(json)
    }

    private fun rot13(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when {
                    ch in 'A'..'Z' -> ((ch - 'A' + 13) % 26 + 'A'.code).toChar()
                    ch in 'a'..'z' -> ((ch - 'a' + 13) % 26 + 'a'.code).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    private fun padBase64(value: String): String {
        val padding = (4 - value.length % 4) % 4
        return value + "=".repeat(padding)
    }

    companion object {
        private val PATTERNS = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
    }
}
