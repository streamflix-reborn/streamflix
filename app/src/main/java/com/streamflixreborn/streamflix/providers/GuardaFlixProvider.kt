package com.streamflixreborn.streamflix.providers

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.extractors.Extractor
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import java.util.Base64

object GuardaFlixProvider : Provider {

    override val name: String = "GuardaFlix"
    override val baseUrl: String = "https://guardaflix.monster"
    override val logo: String = "$baseUrl/wp-content/uploads/2021/05/cropped-Guarda-Flix-2.png"
    override val language: String = "it"

    private const val USER_AGENT = "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private interface GuardaFlixService {
        companion object {
            fun build(baseUrl: String): GuardaFlixService {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)

                val dns = DnsOverHttps.Builder()
                    .client(clientBuilder.build())
                    .url("https://1.1.1.1/dns-query".toHttpUrl())
                    .build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(clientBuilder.dns(dns).build())
                    .build()
                    .create(GuardaFlixService::class.java)
            }
        }

        @Headers(USER_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(USER_AGENT)
        @GET
        suspend fun getPage(@Url url: String): Document

        @Headers(USER_AGENT)
        @GET(".")
        suspend fun search(@Query(value = "s", encoded = true) query: String): Document

        @Headers(USER_AGENT)
        @GET("page/{page}/")
        suspend fun search(@Path("page") page: Int, @Query(value = "s", encoded = true) query: String): Document

        @Headers(USER_AGENT)
        @GET("page/{page}/")
        suspend fun movies(@Path("page") page: Int): Document
    }

    private val service = GuardaFlixService.build(baseUrl)

    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> baseUrl.trimEnd('/') + url
            url.isBlank() -> ""
            else -> baseUrl.trimEnd('/') + "/" + url.trimStart('/')
        }
    }

    override suspend fun getHome(): List<Category> {
        val doc = service.getHome()
        val categories = mutableListOf<Category>()

        doc.select("section.section.movies").forEach { section: Element ->
            val title = section.selectFirst("header .section-title")?.text()?.trim() ?: return@forEach
            val items = section.select(".post-lst li").mapNotNull { el: Element -> parseGridItem(el) }
            if (items.isNotEmpty()) {
                categories.add(Category(name = title, list = items))
            }
        }

        return categories
    }

    private fun parseGridItem(el: Element): AppAdapter.Item? {
        val title = el.selectFirst(".entry-title")?.text()?.trim() ?: return null
        val href = el.selectFirst("a.lnk-blk")?.attr("href") ?: return null
        val poster = el.selectFirst("img")?.attr("src")?.let { normalizeUrl(it) } ?: ""
        val rating = el.selectFirst(".vote")?.text()?.trim()?.toDoubleOrNull()

        return Movie(
            id = href,
            title = title,
            poster = poster,
            rating = rating
        )
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()
            val doc = service.getHome()
            val links = doc.select("li.menu-item:has(> a[href*='/movies']) ul.sub-menu li a[href]")
            return links.mapNotNull { a: Element ->
                val href = a.attr("href").trim()
                val text = a.text().trim()
                if (href.isBlank() || text.isBlank()) return@mapNotNull null
                Genre(id = href, name = text)
            }
        }

        val encoded = URLEncoder.encode(query, "UTF-8")
        if (page > 1) {
            val firstDoc = service.search(encoded)
            val hasPager = firstDoc.selectFirst(".navigation.pagination .nav-links a.page-link") != null
            if (!hasPager) return emptyList()
        }

        val doc = if (page > 1) service.search(page, encoded) else service.search(encoded)

        return doc.select(".post-lst li").mapNotNull { el: Element -> parseGridItem(el) }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val doc = if (page > 1) service.movies(page) else service.getHome()
        
        return doc.select("section.section.movies .post-lst li").mapNotNull { el: Element ->
            parseGridItem(el) as? Movie
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return emptyList() // GuardaFlix is movies only
    }

    override suspend fun getMovie(id: String): Movie {
        val doc = service.getPage(id)
        
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = doc.selectFirst(".post-thumbnail img")?.attr("src")?.let { normalizeUrl(it) } ?: ""
        val description = doc.selectFirst(".description p")?.text()?.trim() ?: ""
        val rating = doc.selectFirst("span.vote.fa-star .num")?.text()?.trim()
            ?.replace(',', '.')
            ?.toDoubleOrNull()

        val genres = doc.select("span.genres a[href]").map { a: Element ->
            Genre(
                id = a.attr("href"),
                name = a.text().trim()
            )
        }

        val cast = doc.select("ul.cast-lst p a[href]").map { a: Element ->
            People(
                id = a.attr("href").trim(),
                name = a.text().trim()
            )
        }

        // Trailer: extract from inlined base64 script (funciones_public_js-js-extra)
        val trailer: String? = runCatching {
            val b64Src = doc.selectFirst("script#funciones_public_js-js-extra[src^=data:text/javascript;base64,]")
                ?.attr("src")
                ?.substringAfter("base64,")
                ?: ""
            if (b64Src.isBlank()) null else {
                val decoded = String(Base64.getDecoder().decode(b64Src))
                val fromTrailer = Regex("""\"trailer\"\s*:\s*\".*?src=\\\"(https?:\\/\\/www\.youtube\.com\\/embed\\/[^\\\"]+)\\\"""")
                    .find(decoded)
                    ?.groupValues?.getOrNull(1)
                fromTrailer
                    ?.replace("\\/", "/")
                    ?.let { mapTrailerToWatchUrl(it) }
            }
        }.getOrNull()

        return Movie(
            id = id,
            title = title,
            poster = poster,
            overview = description,
            rating = rating,
            genres = genres,
            cast = cast,
            trailer = trailer
        )
    }

    private fun mapTrailerToWatchUrl(url: String): String {
        return when {
            url.contains("youtube.com/embed/") -> url
                .replace("/embed/", "/watch?v=")
                .substringBefore("?")
            else -> url
        }
    }

    override suspend fun getTvShow(id: String): TvShow {
        throw Exception("TV shows not supported")
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        throw Exception("TV shows not supported")
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val base = if (id.startsWith("http")) id.removeSuffix("/") else "$baseUrl/${id.removePrefix("/").removeSuffix("/")}"
        if (page > 1) {
            // Check if the category has pagination on the first page
            val firstDoc = service.getPage("$base/")
            val name = firstDoc.selectFirst(".section-header .section-title, h1.section-title, h1")?.text()?.trim() ?: ""
            val hasPager = firstDoc.selectFirst(".navigation.pagination .nav-links a.page-link") != null
            if (!hasPager) {
                return Genre(id = id, name = name, shows = emptyList())
            }
            val doc = service.getPage("$base/page/$page/")
        val shows: List<Show> = doc.select("ul.post-lst li").mapNotNull { li: Element -> parseGridItem(li) as? Show }
            return Genre(id = id, name = name, shows = shows)
        } else {
            val doc = service.getPage("$base/")
            val name = doc.selectFirst(".section-header .section-title, h1.section-title, h1")?.text()?.trim() ?: ""
            val shows: List<Show> = doc.select("ul.post-lst li").mapNotNull { li: Element -> parseGridItem(li) as? Show }
            return Genre(id = id, name = name, shows = shows)
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        val doc = service.getPage(id)

        val name = doc.selectFirst(".section-header .section-title, h1.section-title, h1")?.text()?.trim()
            ?: ""

        if (page > 1) {
            return People(
                id = id,
                name = name,
                filmography = emptyList()
            )
        }

        val filmography = doc.select("ul.post-lst li").mapNotNull { li: Element ->
            parseGridItem(li) as? Show
        }

        return People(
            id = id,
            name = name,
            filmography = filmography
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val doc = service.getPage(id)

        val rawIframe = doc.selectFirst("iframe[data-src]")?.attr("data-src")
            ?: doc.selectFirst("iframe")?.attr("src")
            ?: return emptyList()

        val firstUrl = rawIframe.trim()
        val embedDoc = service.getPage(firstUrl)
        val finalIframe = embedDoc.selectFirst(".Video iframe[src]")?.attr("src")?.trim()
        val finalUrl = finalIframe?.takeIf { it.isNotBlank() } ?: return emptyList()
        return listOfNotNull(
            try {
                val serverName = finalUrl.toHttpUrl().host
                    .replaceFirst("www.", "")
                    .substringBefore(".")
                    .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
                Video.Server(
                    id = finalUrl,
                    name = serverName,
                    src = finalUrl
                )
            } catch (_: Exception) {
                null
            }
        )
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src, server)
    }
}
