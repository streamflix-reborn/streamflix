package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

class LamovieExtractor : Extractor() {

    override val name = "Lamovie"
    override val mainUrl = "https://lamovie.link"
    override val aliasUrls = listOf("https://vimeos.net")

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val document = service.get(link)
        val packedJS = Regex("(eval\\(function\\(p,a,c,k,e,d\\)(.|\\n)*?)</script>")
            .find(document.toString())?.let { it.groupValues[1] }
            ?: throw Exception("Packed JS not found")
        
        val unPacked = JsUnpacker(packedJS).unpack()
            ?: throw Exception("Unpacked is null")
        
        val fileMatch = Regex("""file\s*:\s*["']([^"']+)["']""").find(unPacked)
        val streamUrl = fileMatch?.groupValues?.get(1)
            ?: throw Exception("No file found")

        return Video(
            source = streamUrl ?: throw Exception("Can't retrieve source")
        )
    }


    private interface Service {

        companion object {
            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(@Url url: String): Document
    }
}