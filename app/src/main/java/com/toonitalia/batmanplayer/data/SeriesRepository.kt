package com.toonitalia.batmanplayer.data

import android.content.Context
import android.net.Uri
import com.toonitalia.batmanplayer.model.Series
import java.io.File

/**
 * Carica la serie dal file JSON:
 *  - dagli asset inclusi nell'app (batman-the-brave-and-the-bold.json)
 *  - oppure da un file esterno scelto dall'utente (Storage Access Framework)
 */
class SeriesRepository(private val context: Context) {

    companion object {
        const val BUNDLED_ASSET = "batman-the-brave-and-the-bold.json"
    }

    fun loadBundled(): Series {
        val text = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
        return SeriesParser.parse(text)
    }

    fun loadFromUri(uri: Uri): Series {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalStateException("Impossibile leggere il file selezionato")
        return SeriesParser.parse(text)
    }

    /** Salva il JSON caricato dall'utente nella cartella privata, così resta disponibile offline. */
    fun saveCopy(content: String, fileName: String = "serie_utente.json"): File {
        val dir = File(context.filesDir, "series").apply { mkdirs() }
        val out = File(dir, fileName)
        out.writeText(content)
        return out
    }
}
