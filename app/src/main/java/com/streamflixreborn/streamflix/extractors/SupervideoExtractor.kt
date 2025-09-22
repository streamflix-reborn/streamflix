package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

class SupervideoExtractor : Extractor() {
    override val name = "Supervideo"
    override val mainUrl = "https://supervideo.cc"

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()
                return chain.proceed(req)
            }
        })
        .build()
    private val service = Retrofit.Builder()
        .baseUrl(mainUrl)
        .addConverterFactory(ScalarsConverterFactory.create())
        .client(client)
        .build()
        .create(SupervideoService::class.java)

    private interface SupervideoService {
        @GET
        suspend fun get(@Url url: String): String
    }

    override suspend fun extract(link: String): Video {
        val pageHtml = try {
            service.get(link)
        } catch (_: Exception) {
            service.get(if (link.startsWith("http")) link else "https:$link")
        }

        val scriptData = pageHtml
            .substringAfter("eval(function(p,a,c,k,e,d)")
            .substringBefore("</script>")
            .let { "eval(function(p,a,c,k,e,d)$it" }

        if (!scriptData.startsWith("eval")) {
            throw Exception("Packed JS not found")
        }

        val unpacked = JsUnpacker(scriptData).unpack() ?: throw Exception("Unpack failed")

        val fileRegex = Regex("""file\s*:\s*[\"']([^\"']+)[\"']""")
        val streamUrl = fileRegex.find(unpacked)?.groupValues?.get(1)
            ?: throw Exception("Stream URL not found in file field")

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


