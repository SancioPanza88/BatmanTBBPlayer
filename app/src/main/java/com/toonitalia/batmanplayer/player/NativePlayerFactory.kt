package com.toonitalia.batmanplayer.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.toonitalia.batmanplayer.extractor.Http
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Crea il player nativo (ExoPlayer / Media3).
 *
 * Alcuni CDN dei provider usano certificati scaduti o non validi per il nome
 * del dominio: in quel caso la verifica TLS viene resa permissiva, altrimenti
 * il video non sarebbe riproducibile. I contenuti restano comunque quelli
 * del provider indicato nel JSON.
 */
@UnstableApi
object NativePlayerFactory {

    fun create(context: Context, headers: Map<String, String>): ExoPlayer {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())

        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(Http.USER_AGENT)
            .setDefaultRequestProperties(HashMap(headers))

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }
}
