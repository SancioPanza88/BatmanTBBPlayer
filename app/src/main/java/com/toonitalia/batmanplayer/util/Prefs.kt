package com.toonitalia.batmanplayer.util

import android.content.Context

/** Preferenze dell'utente: blocco pubblicità, fonte preferita, uso del player nativo. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("batman_prefs", Context.MODE_PRIVATE)

    var adBlockEnabled: Boolean
        get() = sp.getBoolean(KEY_ADBLOCK, true)
        set(value) = sp.edit().putBoolean(KEY_ADBLOCK, value).apply()

    /** 0 = uqload (player1), 1 = chuckle-tube (player2) */
    var sourceIndex: Int
        get() = sp.getInt(KEY_SOURCE, 0)
        set(value) = sp.edit().putInt(KEY_SOURCE, value).apply()

    /** Tenta di riprodurre il video con ExoPlayer invece della WebView. */
    var preferNativePlayer: Boolean
        get() = sp.getBoolean(KEY_NATIVE, true)
        set(value) = sp.edit().putBoolean(KEY_NATIVE, value).apply()

    var lastOpenedEpisode: String
        get() = sp.getString(KEY_LAST_EPISODE, "").orEmpty()
        set(value) = sp.edit().putString(KEY_LAST_EPISODE, value).apply()

    companion object {
        private const val KEY_ADBLOCK = "adblock_enabled"
        private const val KEY_SOURCE = "source_index"
        private const val KEY_NATIVE = "prefer_native"
        private const val KEY_LAST_EPISODE = "last_episode"
    }
}
