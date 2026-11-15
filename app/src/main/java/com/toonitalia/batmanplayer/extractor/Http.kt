package com.toonitalia.batmanplayer.extractor

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client HTTP minimale (nessuna dipendenza esterna) per il download delle pagine
 * dei provider e delle pagine "embed".
 */
object Http {

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        request(url, "GET", headers, null)

    fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val body = fields.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val all = HashMap(headers)
        all["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
        return request(url, "POST", all, body)
    }

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val stream = connection.inputStream
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } catch (e: Exception) {
            connection.errorStream?.let { error ->
                BufferedReader(InputStreamReader(error, Charsets.UTF_8)).use { it.readText() }
            } ?: throw e
        } finally {
            connection.disconnect()
        }
    }
}
