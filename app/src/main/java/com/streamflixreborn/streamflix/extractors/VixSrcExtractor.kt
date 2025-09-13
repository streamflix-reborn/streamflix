package com.streamflixreborn.streamflix.extractors

import android.util.Log
import androidx.media3.common.MimeTypes
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

class VixSrcExtractor : Extractor() {

    override val name = "VixSrc"
    override val mainUrl = "https://vixsrc.to"

    fun server(videoType: Video.Type): Video.Server {
        return Video.Server(
            id = name,
            name = name,
            src = when (videoType) {
                is Video.Type.Episode -> "$mainUrl/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
                is Video.Type.Movie -> "$mainUrl/movie/${videoType.id}"
            },
        )
    }


    private fun sanitizeJsonKeysAndQuotes(jsonLikeString: String): String {
        var temp = jsonLikeString
        // Replace Javascript style single quotes with JSON double quotes
        temp = temp.replace("'", "\"")
        // Quote known keys if they appear as key: (e.g. id:, filename:, token:, expires:, asn:)
        // Use a raw string for the regex pattern and a lambda for replacement.
        temp = Regex("""(\b(?:id|filename|token|expires|asn)\b)\s*:""").replace(temp) { matchResult ->
            '"' + matchResult.groupValues[1] + "\":"
        }
        return temp
    }

    private fun removeTrailingCommaFromJsonObjectString(jsonString: String): String {
        val temp = jsonString.trim() // Changed var to val
        val lastBraceIndex = temp.lastIndexOf('}')
        if (lastBraceIndex > 0 && temp.startsWith("{")) {
            var charIndexBeforeBrace = lastBraceIndex - 1
            while (charIndexBeforeBrace >= 0 && temp[charIndexBeforeBrace].isWhitespace()) {
                charIndexBeforeBrace--
            }
            if (charIndexBeforeBrace >= 0 && temp[charIndexBeforeBrace] == ',') {
                // Reconstruct string without the trailing comma
                return temp.substring(0, charIndexBeforeBrace) + temp.substring(charIndexBeforeBrace + 1)
            }
        }
        return jsonString // Return original if no modification was made or not applicable
    }

    override suspend fun extract(link: String): Video {
        val service = VixSrcExtractorService.build(mainUrl)
        val source = service.getSource(link.replace(mainUrl, ""))

        val scriptText = source.body().selectFirst("script")?.data() ?: ""
        Log.d("VixSrcDebug", "scriptText content: <<<" + scriptText + ">>>")

        // Extract video ID directly from script
        val videoId = scriptText
            .substringAfter("window.video = {", "")
            .substringAfter("id: '", "")
            .substringBefore("',", "")
            .trim()

        Log.d("VixSrcDebug", "Extracted videoId: $videoId")

        // Extract token and expires directly
        val token = scriptText
            .substringAfter("window.masterPlaylist", "")
            .substringAfter("'token': '", "")
            .substringBefore("',", "")
            .trim()

        val expires = scriptText
            .substringAfter("window.masterPlaylist", "")
            .substringAfter("'expires': '", "")
            .substringBefore("',", "")
            .trim()

        Log.d("VixSrcDebug", "Extracted token: $token")
        Log.d("VixSrcDebug", "Extracted expires: $expires")

        val hasBParam = scriptText
            .substringAfter("url:", "")
            .substringBefore(",", "")
            .contains("b=1")

        val canPlayFHD = scriptText.contains("window.canPlayFHD = true")

        val masterParams = mutableMapOf<String, String>()
        masterParams["token"] = token
        masterParams["expires"] = expires

        val currentParams = link.split("&")
            .map { param -> param.split("=") }
            .filter { it.size == 2 }
            .associate { it[0] to it[1] }

        if (hasBParam) masterParams["b"] = "1"
        if (canPlayFHD) masterParams["h"] = "1"

        val baseUrl = "https://vixsrc.to/playlist/${videoId}"
        val httpUrlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid base URL")
        masterParams.forEach { (key, value) -> httpUrlBuilder.addQueryParameter(key, value) }
        val finalUrl = httpUrlBuilder.build().toString()

        return Video(
            source = finalUrl,
            subtitles = listOf(),
            type = MimeTypes.APPLICATION_M3U8,
            headers = mapOf("Referer" to mainUrl, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        )
    }


    private interface VixSrcExtractorService {
        companion object {
            fun build(baseUrl: String): VixSrcExtractorService {
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(VixSrcExtractorService::class.java)
            }
        }

        @GET
        @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        suspend fun getSource(@Url url: String): Document

        data class WindowVideo(
            @SerializedName("id") val id: Int,
            @SerializedName("filename") val filename: String
        )

        data class WindowParams(
            @SerializedName("token") val token: String?,
            @SerializedName("expires") val expires: String?
        )
    }
}
