package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import com.google.gson.JsonParser
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.Locale
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RpmvidExtractor : Extractor() {
    override val name = "Rpmvid"
    override val mainUrl = "https://rpmvid.com"
    override val aliasUrls = listOf("https://cubeembed.rpmvid.com","https://bummi.upns.xyz")

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        private val KEY = "kiemtienmua911ca".toByteArray()
        private val IV = "1234567890oiuytr".toByteArray()
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()
                return chain.proceed(request)
            }
        })
        .build()

    private interface Service {
        @GET
        suspend fun get(
            @Url url: String,
            @Header("Referer") referer: String,
            @Query("id") id: String,
            @Query("w") w: String,
            @Query("h") h: String,
            @Query("r") r: String = "",
        ): String

        companion object {
            fun build(baseUrl: String, client: OkHttpClient): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(Service::class.java)
            }
        }
    }

    override suspend fun extract(link: String): Video {
        val id = extractId(link) ?: throw Exception("Invalid link: missing id after #")
        val mainLink = URL(link).protocol + "://" + URL(link).host
        val service = Service.build(mainLink, client)
        val apiUrl = "$mainLink/api/v1/video"

        val hexResponse = service.get(
            url = apiUrl,
            referer = mainLink,
            id = id,
            w = "1920",
            h = "1080",
        )

        val decryptedJson = decryptHexPayload(hexResponse)
        val json = JsonParser.parseString(decryptedJson).asJsonObject

        val hlsPath = json.get("hls")?.asString?.takeIf { it.isNotEmpty() }
        val cfPath = json.get("cf")?.asString?.takeIf { it.isNotEmpty() }
        
        val (finalUrl, headers) = when {
            !hlsPath.isNullOrEmpty() -> {
                val ttdata = json.get("ttdata")?.asString?.takeIf { it.isNotEmpty() }
                    ?: throw Exception("Missing ttdata in response")
                val ttdataHost = ttdata.substringBefore("::")
                val url = "$mainLink/pproxy/${ttdataHost}${hlsPath}"
                url to mapOf("Referer" to mainLink)
            }
            !cfPath.isNullOrEmpty() -> {
                cfPath to mapOf("Referer" to mainLink)
            }
            else -> throw Exception("Missing both hls and cf in response")
        }

        return Video(
            source = finalUrl,
            headers = headers,
            type = if (!cfPath.isNullOrEmpty()) "application/x-mpegURL" else null
        )
    }

    private fun extractId(link: String): String? {
        val idx = link.indexOf('#')
        if (idx == -1 || idx == link.lastIndex) return null
        return link.substring(idx + 1)
    }

    private fun decryptHexPayload(hex: String): String {
        val bytes = hexToBytes(hex)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"), IvParameterSpec(IV))
        val decrypted = cipher.doFinal(bytes)
        return decrypted.toString(Charsets.UTF_8)
    }

    private fun hexToBytes(input: String): ByteArray {
        val cleaned = input.lowercase(Locale.US).replace(Regex("[^0-9a-f]"), "")
        val even = if (cleaned.length % 2 == 0) cleaned else "0$cleaned"
        val out = ByteArray(even.length / 2)
        var i = 0
        var j = 0
        while (i < even.length) {
            out[j++] = ((even[i].digitToInt(16) shl 4) or even[i + 1].digitToInt(16)).toByte()
            i += 2
        }
        return out
    }
}