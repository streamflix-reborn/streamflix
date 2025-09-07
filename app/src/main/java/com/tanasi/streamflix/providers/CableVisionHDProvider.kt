package com.tanasi.streamflix.providers

import android.util.Base64
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.tanasi.streamflix.models.Category
import com.tanasi.streamflix.models.Episode
import com.tanasi.streamflix.models.Genre
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.models.People
import com.tanasi.streamflix.models.Season
import com.tanasi.streamflix.models.Show
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.models.Video
import com.tanasi.streamflix.models.cablevisionhd.toTvShows
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll

object CableVisionHDProvider : com.tanasi.streamflix.providers.Provider {

    override val name = "CableVisionHD"
    override val baseUrl = "https://www.cablevisionhd.com"
    override val logo = "https://i.ibb.co/4gMQkN2b/imagen-2025-09-05-212536248.png"
    override val language = "es"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val service = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()
        .create(CableVisionHDService::class.java)


    interface CableVisionHDService {
        @GET
        suspend fun getPage(
            @Url url: String,
            @Header("Referer") referer: String
        ): Document
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        val document = service.getPage(baseUrl, referer = baseUrl)
        val allShows = document.toTvShows()

        val categories = mutableListOf<Deferred<Category>>()
        categories.add(async {
            Category(
                name = "Todos los Canales",
                list = allShows
            )
        })

        categories.add(async {
            Category(
                name = "Deportes",
                list = allShows.filter { it.title.contains("sports", ignoreCase = true) || it.title.contains("espn", ignoreCase = true) }
            )
        })

        categories.add(async {
            Category(
                name = "Noticias",
                list = allShows.filter { it.title.contains("news", ignoreCase = true) || it.title.contains("noticias", ignoreCase = true) }
            )
        })

        categories.add(async {
            Category(
                name = "Cine y Series",
                list = allShows.filter { it.title.contains("hbo", ignoreCase = true) || it.title.contains("max", ignoreCase = true) || it.title.contains("cine", ignoreCase = true) }
            )
        })
        categories.add(async {
            val creador = TvShow(
                id = "creador-info", // ID especial
                title = "Reportar problemas con proveedores en ES.",
                poster = "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg"
            )
            val apoyo = TvShow(
                id = "apoyo-info", // ID especial
                title = "Apoya Si te gusta este Proveedor",
                poster = "https://i.ibb.co/B234HsZg/APOYO-NANDO.png"
            )
            Category(
                name = "Acerca de este Proveedor",
                list = listOf(creador, apoyo)
            )
        })
        categories.awaitAll().filter { it.list.isNotEmpty() }
    }

    override suspend fun search(query: String, page: Int): List<Show> {
        val document = service.getPage(baseUrl, referer = baseUrl)
        val allShows = document.toTvShows()
        return allShows.filter {
            it.title.contains(query, ignoreCase = true)
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> {
        if (page > 1) return emptyList()
        val document = service.getPage(baseUrl, referer = baseUrl)
        return document.toTvShows()
    }

    override suspend fun getMovie(id: String): Movie = throw NotImplementedError()

    override suspend fun getTvShow(id: String): TvShow {
        when (id) {
            "creador-info" -> {
                return TvShow(
                    id = id,
                    title = "Reportar problemas con proveedores en ES.",
                    poster = "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg",
                    banner = "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg",
                    overview = "¡Hola a todos!\n" +
                            "\n" +
                            "Les presento este nuevo proveedor, que ha sido desarrollado gracias a la fantástica colaboración de \uD83D\uDC8E@NandoGT\uD83D\uDC8E y \uD83D\uDC8E@Nandofs\uD83D\uDC8E. ¡Un excelente trabajo en equipo!\n" +
                            "\n" +
                            "Soporte y Reporte de Errores\n" +
                            "Si llegan a encontrar algún problema o error con los proveedores para contenido en español (Latinoamérica) que he creado, por favor no duden en contactarme para reportarlo. De esta manera, podré trabajar en una solución lo antes posible.\n" +
                            "\n" +
                            "¿Interesado en el proveedor?\n" +
                            "Si tienes curiosidad, quieres implementarlo o tienes alguna consulta sobre su funcionamiento, ¡pregunta con toda confianza! Estaré encantado de ayudarte.\n" +
                            "\n" +
                            "Agradecimientos Especiales\n" +
                            "Finalmente, quiero extender un agradecimiento a todas las personas que aportan a este proyecto, y de manera muy especial a su autor principal, Lory-Stan TANASI (stantanasi.github.io), por su increíble trabajo y dedicación.\n" +
                            "\n" +
                            "¡Gracias a todos por su apoyo!",
                    seasons = emptyList()
                )
            }
            "apoyo-info" -> {
                return TvShow(
                    id = id,
                    title = "Apoya Si te gusta este Proveedor",
                    poster = "https://i.ibb.co/B234HsZg/APOYO-NANDO.png",
                    banner = "https://i.ibb.co/B234HsZg/APOYO-NANDO.png",
                    overview = "Si disfrutas usando este proveedor, te invito a considerar apoyar mi trabajo y, a su vez, al proyecto principal de Streamflix. Saber que valoras nuestro esfuerzo nos anima a seguir dedicándole tiempo y pasión a este hermoso proyecto.\n" +
                            "\n" +
                            "Para colaborar, simplemente escanea el siguiente código QR.\n" +
                            "\n" +
                            "¡Cualquier aporte, por pequeño que sea, hace una gran diferencia!",
                    seasons = emptyList()
                )
            }
        }
        val document = service.getPage(id, referer = baseUrl)

        val title = document.selectFirst("div.card-body h2")?.text() ?: ""
        val poster = document.selectFirst("div.card-body img")?.attr("src")
        val overview = document.selectFirst("div.card-body p")?.text()

        val season = Season(id = id, number = 1, title = "Ver en Vivo")

        return TvShow(
            id = id,
            title = title,
            overview = overview,
            poster = poster?.let { if (!it.startsWith("http")) "$baseUrl/$it" else it },
            banner = poster?.let { if (!it.startsWith("http")) "$baseUrl/$it" else it },
            seasons = listOf(season)
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val episode = Episode(id = seasonId, number = 1, title = "Canal en Vivo")
        return listOf(episode)
    }

    override suspend fun getGenre(id: String, page: Int): Genre = throw NotImplementedError()

    override suspend fun getPeople(id: String, page: Int): People = throw NotImplementedError()

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val document = service.getPage(id, referer = baseUrl)
        val serverElements = document.select("a.btn.btn-md[target=iframe]")
        return serverElements.map {
            Video.Server(
                id = it.attr("href"),
                name = it.text()
            )
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val intermediateDocument = service.getPage(server.id, referer = baseUrl)

        val finalIframeUrl = intermediateDocument.selectFirst("iframe")?.attr("src")
            ?: throw Exception("No se encontró el iframe final en la página intermedia.")

        val finalDocument = service.getPage(finalIframeUrl, referer = server.id)

        val script = finalDocument.selectFirst("script:containsData(const decodedURL)")?.data()
            ?: finalDocument.selectFirst("script:containsData(var src)")?.data()
            ?: throw Exception("No se encontró ningún script de video conocido.")

        val videoUrl = when {
            script.contains("const decodedURL") -> {
                val encodedUrl = script.substringAfter("atob(\"").substringBefore("\"))))")
                String(Base64.decode(String(Base64.decode(String(Base64.decode(encodedUrl, Base64.DEFAULT)), Base64.DEFAULT)), Base64.DEFAULT))
            }
            script.contains("var src") -> {
                script.substringAfter("var src = \"").substringBefore("\";").replace("\\/", "/")
            }
            else -> throw Exception("El script encontrado no tiene un formato de video reconocible.")
        }

        return Video(
            source = videoUrl
        )
    }
}