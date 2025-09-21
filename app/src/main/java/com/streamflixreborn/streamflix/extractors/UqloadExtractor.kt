package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

class UqloadExtractor : Extractor() {
    override val name = "Uqload"
    override val mainUrl = "https://uqload.cx"


    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        val document = service.getSource(url = link)

        val scripts = document.select("script[type=\"text/javascript\"]")
        val scriptContent = scripts.find { it.html().contains("sources:") }?.html()
            ?: throw Exception("Script with sources not found")
        
        val sourcesRegex = Regex("""sources:\s*\["([^"]+)"]""")
        val match = sourcesRegex.find(scriptContent)
            ?: throw Exception("Sources not found in script")

        val sourceUrl = match.groupValues[1]

        return Video(
            source = sourceUrl,
            headers = mapOf(
                "Referer" to mainUrl
            )
        )
    }

    private interface Service {
        @GET
        suspend fun getSource(
            @Url url: String
        ): Document

        companion object {
            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofit.create(Service::class.java)
            }
        }
    }
}
