package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

class PlusPomlaExtractor : Extractor() {

    override val name = "PlusPomla"
    override val mainUrl = "https://apu.animemovil2.com"

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        
        // Follow redirect
        val document = service.get(link)
        
        // Find script with $.ajax({url:"//
        val scriptMatch = Regex("""\$\.ajax\s*\(\s*\{\s*url\s*:\s*["']([^"']+)["']""").find(document.toString())
        val ajaxUrl = scriptMatch?.groupValues?.get(1)
            ?: throw Exception("Ajax URL not found")
        
        // Add https: because the URL starts with //
        val fullUrl = "https:$ajaxUrl"
        
        // Make request with referer
        val dataResponse = service.getData(fullUrl, link)
        
        // Extract sources from response
        val sources = dataResponse.sources?.map { it.file } ?: emptyList()
        
        if (sources.isEmpty()) {
            throw Exception("No sources found")
        }
        
        return Video(
            source = sources.first()
        )
    }

    private interface Service {

        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(@Url url: String): Document

        @GET
        suspend fun getData(
            @Url url: String,
            @Header("Referer") referer: String
        ): DataResponse
    }

    private data class DataResponse(
        val sources: List<Source>?
    )

    private data class Source(
        val file: String
    )
}
