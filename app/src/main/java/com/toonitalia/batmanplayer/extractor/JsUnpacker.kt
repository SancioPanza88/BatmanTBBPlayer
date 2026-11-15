package com.toonitalia.batmanplayer.extractor

/**
 * Unpacker per il pacchetto JavaScript "packed" (Dean Edwards packer), usato da
 * moltissimi player di streaming:
 *
 *   eval(function(p,a,c,k,e,d){...}('payload',radix,count,'simboli'))
 *
 * Il pacchetto viene "compresso" sostituendo le parole con i corrispondenti
 * simboli: questo decodificatore ricostruisce il codice originale.
 */
object JsUnpacker {

    private val EVAL_PATTERN =
        Regex("""eval\(function\(p,a,c,k,e,d\)\{.*?\}\('""", RegexOption.DOT_MATCHES_ALL)

    /** Restituisce il codice spacchettato, oppure null se il contenuto non è packed. */
    fun unpack(content: String): String? {
        val match = EVAL_PATTERN.find(content) ?: return null
        val payloadStart = match.range.last + 1

        val payloadEnd = findClosingQuote(content, payloadStart)
        if (payloadEnd < 0) return null
        val payload = content.substring(payloadStart, payloadEnd)
            .replace("\\'", "'")
            .replace("\\\\", "\\")

        val rest = content.substring(payloadEnd + 1)
        val args = Regex("""^,(\d+),(\d+),'""").find(rest) ?: return null
        val radix = args.groupValues[1].toIntOrNull() ?: return null
        val count = args.groupValues[2].toIntOrNull() ?: return null
        if (count <= 0 || count > 20000) return null

        val symbolsStart = args.range.last + 1
        val symbolsEnd = findClosingQuote(rest, symbolsStart)
        if (symbolsEnd < 0) return null
        val symbols = splitSymbols(rest.substring(symbolsStart, symbolsEnd), count)
        if (symbols.size < count) return null

        val indexByKey = HashMap<String, Int>(count * 2)
        for (i in 0 until count) {
            indexByKey[baseEncode(i, radix)] = i
        }

        val alternation = indexByKey.keys.joinToString("|") { Regex.escape(it) }
        val pattern = Regex("(?<![A-Za-z0-9_])($alternation)(?![A-Za-z0-9_])")
        return pattern.replace(payload) { m ->
            val index = indexByKey[m.groupValues[1]]
            if (index != null && index < symbols.size) symbols[index] else ""
        }
    }

    /** Individua la virgoletta singola di chiusura ignorando quelle escaped. */
    private fun findClosingQuote(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\') {
                i += 2
                continue
            }
            if (ch == '\'') return i
            i++
        }
        return -1
    }

    /** I pacchetti usano '|' oppure ',' come separatore dei simboli. */
    private fun splitSymbols(raw: String, count: Int): List<String> {
        if (raw.contains('|')) {
            val byPipe = raw.split('|')
            if (byPipe.size >= count) return byPipe
        }
        return raw.split(',')
    }

    /** Stessa codifica di Number.toString(radix) usata da JavaScript. */
    private fun baseEncode(value: Int, radix: Int): String {
        val alphabet = if (radix <= 36) {
            "0123456789abcdefghijklmnopqrstuvwxyz"
        } else {
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        }
        if (value == 0) return "0"
        var v = value
        val sb = StringBuilder()
        while (v > 0) {
            sb.append(alphabet[v % radix])
            v /= radix
        }
        return sb.reverse().toString()
    }
}
