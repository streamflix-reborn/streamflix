package com.tanasi.streamflix.extractors

import android.util.Log
import androidx.media3.common.MimeTypes
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.tanasi.streamflix.models.Video
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

class VixcloudExtractor : Extractor() {

    override val name = "vixcloud"
    override val mainUrl = "https://vixcloud.co/"

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
        val service = VixcloudExtractorService.build(mainUrl)
        val source = service.getSource(link.replace(mainUrl, ""))

        val scriptText = source.body().selectFirst("script")?.data() ?: ""
        Log.d("VixcloudDebug", "scriptText content: <<<" + scriptText + ">>>")

        var videoJson = scriptText
            .substringAfter("window.video = ", "")
            .substringBefore(";", "")
            .trim()
        Log.d("VixcloudDebug", "Original videoJson: <<<" + videoJson + ">>>")
        if (videoJson.isNotEmpty()) {
            videoJson = sanitizeJsonKeysAndQuotes(videoJson)
            videoJson = removeTrailingCommaFromJsonObjectString(videoJson)
            // Ensure it's actually an object, in case of unexpected extraction
            if (!videoJson.startsWith("{") && videoJson.contains(":")) videoJson = "{$videoJson"
            if (!videoJson.endsWith("}") && videoJson.contains(":")) videoJson = "$videoJson}"
        }
        Log.d("VixcloudDebug", "Processed videoJson: <<<" + videoJson + ">>>")

        val paramsObjectContent = scriptText
            .substringAfter("window.masterPlaylist", "")
            .substringAfter("params: {", "")
            .substringBefore("},", "") // This gets content between 'params: {' and '},'
            .trim()
        Log.d("VixcloudDebug", "Extracted paramsObjectContent: <<<" + paramsObjectContent + ">>>")

        var masterPlaylistJson: String
        if (paramsObjectContent.isNotEmpty()) {
            var processedParams = sanitizeJsonKeysAndQuotes(paramsObjectContent)
            // Remove trailing comma from the *content* string before wrapping with braces
            processedParams = processedParams.trim()
            if (processedParams.endsWith(",")) {
                processedParams = processedParams.substring(0, processedParams.length - 1).trim()
            }
            masterPlaylistJson = "{${processedParams}}"
        } else {
            masterPlaylistJson = "{}"
        }
        Log.d("VixcloudDebug", "Processed masterPlaylistJson: <<<" + masterPlaylistJson + ">>>")

        val hasBParam = scriptText
            .substringAfter("url:", "")
            .substringBefore(",", "")
            .contains("b=1")

        val gson = Gson()
        val windowVideo = gson.fromJson(videoJson, VixcloudExtractorService.WindowVideo::class.java)
        val masterPlaylist = gson.fromJson(masterPlaylistJson, VixcloudExtractorService.WindowParams::class.java)

        val masterParams = mutableMapOf<String, String>()
        if (masterPlaylist?.token != null) {
            masterParams["token"] = masterPlaylist.token
        }
        if (masterPlaylist?.expires != null) {
            masterParams["expires"] = masterPlaylist.expires
        }

        val currentParams = link.split("&")
            .map { param -> param.split("=") }
            .filter { it.size == 2 }
            .associate { it[0] to it[1] }

        if (hasBParam) masterParams["b"] = "1"
        if (currentParams.containsKey("canPlayFHD")) masterParams["h"] = "1"

        val baseUrl = "https://vixcloud.co/playlist/${windowVideo.id}"
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

    private interface VixcloudExtractorService {
        companion object {
            fun build(baseUrl: String): VixcloudExtractorService {
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(VixcloudExtractorService::class.java)
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
