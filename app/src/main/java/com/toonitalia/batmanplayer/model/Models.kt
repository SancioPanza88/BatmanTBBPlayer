package com.toonitalia.batmanplayer.model

/**
 * Un singolo episodio con le due fonti di streaming disponibili.
 * player1 -> uqload.vc , player2 -> chuckle-tube.com
 */
data class Episode(
    val season: Int,
    val number: Int,
    val title: String,
    val player1: String,
    val player2: String
) {
    /** Restituisce l'URL della fonte selezionata: 0 = player1, 1 = player2. */
    fun urlFor(source: Int): String = if (source == 1) player2 else player1

    fun sourceName(source: Int): String = if (source == 1) "chuckle-tube" else "uqload"
}

data class Season(
    val number: Int,
    val episodes: List<Episode>
)

data class Series(
    val title: String,
    val originalTitle: String,
    val alternativeTitles: String,
    val country: String,
    val releaseDate: String,
    val status: String,
    val declaredEpisodes: Int,
    val extractedEpisodes: Int,
    val streamingUpdate: String,
    val sourceUrl: String,
    val initialPlayer: String,
    val initialUrl: String,
    val seasons: List<Season>
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
}
