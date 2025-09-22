package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

class DroploadExtractor : Extractor() {
    override val name = "Dropload"
    override val mainUrl = "https://dropload.tv"
    override val aliasUrls = listOf("https://dropload.io")

    private val client = OkHttpClient.Builder().build()

    private val service = Retrofit.Builder()
        .baseUrl(mainUrl)
        .addConverterFactory(ScalarsConverterFactory.create())
        .client(client)
        .build()
        .create(DroploadService::class.java)

    private interface DroploadService {
        @GET
        suspend fun get(@Url url: String): String
    }

    override suspend fun extract(link: String): Video {
        val html = service.get(link)

        val scriptData = html
            .substringAfter("eval(function(p,a,c,k,e,d)")
            .substringBefore("</script>")
            .let { "eval(function(p,a,c,k,e,d)$it" }

        if (!scriptData.startsWith("eval")) throw Exception("Packed JS not found")

        val unpacked = JsUnpacker(scriptData).unpack() ?: throw Exception("Unpack failed")

        val fileRegex = Regex("""file\s*:\s*[\"']([^\"']+)[\"']""")
        val streamUrl = fileRegex.find(unpacked)?.groupValues?.get(1)
            ?: throw Exception("Stream URL not found in file field")

        // Parse tracks -> captions (exclude thumbnails)
        val tracksBlock = Regex("""tracks\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(unpacked)
            ?.groupValues?.get(1)
            ?: ""
        val captionMatches = Regex("""file\s*:\s*\"(.*?)\"\s*,\s*label\s*:\s*\"(.*?)\"\s*,\s*kind\s*:\s*\"captions\"""")
            .findAll(tracksBlock)
        val subtitles = captionMatches.map {
            Video.Subtitle(
                label = it.groupValues[2],
                file = it.groupValues[1]
            )
        }.toList()

        return Video(
            source = streamUrl,
            subtitles = subtitles
        )
    }
}


