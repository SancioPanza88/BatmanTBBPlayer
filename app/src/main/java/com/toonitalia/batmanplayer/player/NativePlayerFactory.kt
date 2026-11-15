package com.toonitalia.batmanplayer.player

import android.content.Context
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.toonitalia.batmanplayer.extractor.Http
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Crea il player nativo (ExoPlayer / Media3).
 *
 * Alcuni CDN dei provider usano certificati scaduti o non validi per il nome:
 * in quel caso la verifica TLS viene resa permissiva, altrimenti il video
 * non sarebbe riproducibile. I contenuti restano comunquethose del provider.
 */
object NativePlayerFactory {

    fun create(context: Context, headers: Map<String, String>): ExoPlayer {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(HashMap(headers))
            .setSslSocketFactory(relaxedSocketFactory())
            .setHostnameVerifier(HostnameVerifier { _, _ -> true })

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    private fun relaxedSocketFactory(): SSLSocketFactory {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return sslContext.socketFactory
    }
}
