package com.streamflixreborn.streamflix.ui

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext
import java.security.SecureRandom

@GlideModule
class GlideCustomModule : AppGlideModule() {
    val DNS_QUERY_URL = "https://1.1.1.1/dns-query"

    private fun getOkHttpClient(): OkHttpClient {
        val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)

        val logging = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)

        // Always trust-all for image loading to avoid SSL issues on some devices/hosts
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, SecureRandom()) }
        val trustManager = trustAllCerts[0] as X509TrustManager

        return Builder()
            .cache(appCache)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    override fun registerComponents(
        context: Context, glide: Glide, registry: com.bumptech.glide.Registry
    ) {
        val okHttpClient = getOkHttpClient()
        registry.replace(
            GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient)
        )
    }
}