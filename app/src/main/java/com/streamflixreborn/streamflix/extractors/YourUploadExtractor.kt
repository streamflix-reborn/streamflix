package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

class YourUploadExtractor : Extractor() {

    override val name = "YourUpload"
    override val mainUrl = "https://www.yourupload.com"
    override val aliasUrls = listOf("https://www.yucache.net")

    override suspend fun extract(link: String): Video {
        val service = YourUploadExtractorService.build(mainUrl)
        val doc = service.getSource(link.replace(mainUrl, ""))

        // Extract JWPlayer config script
        val scriptContent = doc.select("script:containsData(jwplayerOptions)").html()

        // Look for .m3u8 first, then fallback to .mp4
        val regex = Regex("""file:\s*'([^']+\.(?:m3u8|mp4))'""")
        val match = regex.find(scriptContent)
        val videoUrl = match?.groupValues?.get(1) ?: ""

        return Video(
            source = videoUrl,
            subtitles = listOf(),
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/116.0.0.0 Safari/537.36"
            )
        )
    }

    private interface YourUploadExtractorService {

        companion object {
            fun build(baseUrl: String): YourUploadExtractorService {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()

                return retrofit.create(YourUploadExtractorService::class.java)
            }
        }

        @GET
        suspend fun getSource(@Url url: String): Document
    }
}