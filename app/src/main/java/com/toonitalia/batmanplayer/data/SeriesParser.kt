package com.toonitalia.batmanplayer.data

import com.toonitalia.batmanplayer.model.Episode
import com.toonitalia.batmanplayer.model.Season
import com.toonitalia.batmanplayer.model.Series
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser del file JSON descrittivo della serie.
 * Formato atteso (chiavi italiane):
 * {
 *   "titolo": "...", "stagioni": [ { "stagione": 1, "episodi": [ {...} ] } ]
 * }
 */
object SeriesParser {

    fun parse(raw: String): Series {
        val root = JSONObject(raw)

        val seasons = mutableListOf<Season>()
        val seasonsArray: JSONArray? = root.optJSONArray("stagioni")
        if (seasonsArray != null) {
            for (i in 0 until seasonsArray.length()) {
                val seasonObj = seasonsArray.optJSONObject(i) ?: continue
                val seasonNumber = seasonObj.optInt("stagione", i + 1)
                val episodesArray: JSONArray? = seasonObj.optJSONArray("episodi")
                val episodes = mutableListOf<Episode>()
                if (episodesArray != null) {
                    for (j in 0 until episodesArray.length()) {
                        val ep = episodesArray.optJSONObject(j) ?: continue
                        episodes += Episode(
                            season = ep.optInt("stagione", seasonNumber),
                            number = ep.optInt("episodio", j + 1),
                            title = ep.optString("titolo").trim().ifBlank { "Episodio ${j + 1}" },
                            player1 = ep.optString("player1").trim(),
                            player2 = ep.optString("player2").trim()
                        )
                    }
                }
                seasons += Season(number = seasonNumber, episodes = episodes)
            }
        }

        val sigla = root.optJSONObject("sigla_iniziale")

        return Series(
            title = root.optString("titolo").trim().ifBlank { "Serie" },
            originalTitle = root.optString("titolo_originale").trim(),
            alternativeTitles = root.optString("titoli_alternativi").trim(),
            country = root.optString("paese_di_origine").trim(),
            releaseDate = root.optString("data_pubblicazione").trim(),
            status = root.optString("stato").trim(),
            declaredEpisodes = root.optInt("episodi_totali_dichiarati", 0),
            extractedEpisodes = root.optInt("episodi_estratti", 0),
            streamingUpdate = root.optString("aggiornamento_streaming").trim(),
            sourceUrl = root.optString("fonte").trim(),
            initialPlayer = sigla?.optString("player")?.trim().orEmpty(),
            initialUrl = sigla?.optString("url")?.trim().orEmpty(),
            seasons = seasons
        )
    }
}
