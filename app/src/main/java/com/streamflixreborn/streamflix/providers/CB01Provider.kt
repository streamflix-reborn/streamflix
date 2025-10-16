package com.streamflixreborn.streamflix.providers

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.extractors.Extractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Field
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object CB01Provider : Provider {

    override val name = "CB01"
    override val baseUrl = "https://cb01.uno"
    override val logo: String get() = "$baseUrl/apple-icon-180x180px.png"
    override val language = "it"

    private const val USER_AGENT = "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private interface CB01Service {
        @Headers(USER_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(USER_AGENT)
        @GET("page/{page}/")
        suspend fun getMovies(@Path("page") page: Int): Document

        @Headers(USER_AGENT)
        @GET("serietv/")
        suspend fun getTvShows(): Document

        @Headers(USER_AGENT)
        @GET("serietv/page/{page}/")
        suspend fun getTvShows(@Path("page") page: Int): Document

        @Headers(USER_AGENT)
        @GET(".")
        suspend fun searchMovies(@Query("s") query: String): Document

        @Headers(USER_AGENT)
        @GET("page/{page}/")
        suspend fun searchMovies(@Path("page") page: Int, @Query("s") query: String): Document

        @Headers(USER_AGENT)
        @GET("serietv/")
        suspend fun searchTvShows(@Query("s") query: String): Document

        @Headers(USER_AGENT)
        @GET("serietv/page/{page}/")
        suspend fun searchTvShows(@Path("page") page: Int, @Query("s") query: String): Document

        @Headers(USER_AGENT)
        @GET
        suspend fun getPage(@Url url: String): Document

        companion object {
            fun build(baseUrl: String): CB01Service {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)

                val doh = DnsOverHttps.Builder()
                    .client(clientBuilder.build())
                    .url("https://1.1.1.1/dns-query".toHttpUrl())
                    .build()

                val client = clientBuilder.dns(doh).build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(CB01Service::class.java)
            }
        }
    }

    private val service = CB01Service.build(baseUrl)

    private interface StayService {
        @FormUrlEncoded
        @POST
        suspend fun postAjax(
            @Url url: String,
            @Field("id") id: String,
            @Field("ref") ref: String = "",
            @Header("User-Agent") userAgent: String,
            @Header("Referer") referer: String
        ): Response<ResponseBody>

        companion object {
            fun build(): StayService {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)

                val doh = DnsOverHttps.Builder()
                    .client(clientBuilder.build())
                    .url("https://1.1.1.1/dns-query".toHttpUrl())
                    .build()

                val client = clientBuilder.dns(doh).build()

                val retrofit = Retrofit.Builder()
                    .baseUrl("https://stayonline.pro/")
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(StayService::class.java)
            }
        }
    }

    private val stayService = StayService.build()

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    private fun cleanTitle(raw: String): String {
        val withoutYearHd = raw
            .replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "")
            .replace(Regex("\\s*\\[\\s*hd\\s*(?:/3d)?\\s*\\]", RegexOption.IGNORE_CASE), "")

        val episodeLangRegex = Regex(
            pattern = "\\s*[–-]\\s*\\d+[x×]\\d+(?:[./]\\d+)*\\s*[–-]\\s*([A-Za-z][A-Za-z -]{1,})\\s*$",
            option = RegexOption.IGNORE_CASE
        )
        val cleanedEpisode = episodeLangRegex.replace(withoutYearHd) { mr ->
            val lang = mr.groupValues.getOrNull(1)?.trim().orEmpty()
            if (lang.startsWith("sub", ignoreCase = true)) " - $lang" else ""
        }
        val completaRegex = Regex("\\s*[–-]\\s*Stagione\\s+\\d+\\s*[–-]\\s*COMPLETA\\s*$", RegexOption.IGNORE_CASE)
        val cleaned = completaRegex.replace(cleanedEpisode, "")

        return cleaned.trim()
    }

    private fun parseGenresText(raw: String, hasDurationMarker: Boolean): List<Genre> {
        val withoutDuration = if (hasDurationMarker) {
            raw.split(Regex("\\s*[–-]\\s*DURATA", RegexOption.IGNORE_CASE)).firstOrNull()
                ?: raw
        } else raw

        val withoutYears = withoutDuration.replace(Regex("\\s*\\(\\d{4}[^)]*\\)\\s*$"), "").trim()

        return withoutYears.split(Regex("\\s*/\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { name ->
                val normalized = name.lowercase().replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase() else c.toString()
                }
                Genre(id = normalized, name = normalized)
        }
    }

    private fun parseHomeMovie(el: Element): Movie? {
        val titleAnchor = el.selectFirst("h3.card-title a[href]") ?: return null
        val href = titleAnchor.attr("href").trim()
        val rawTitle = titleAnchor.text().trim()
        val title = cleanTitle(rawTitle)
        val img = el.selectFirst(".card-image img[src]")?.attr("src").orEmpty()
        val poster = img
        val quality = if (rawTitle.contains("[HD]", ignoreCase = true) || rawTitle.contains("[HD/3D]", ignoreCase = true)) "HD" else null
        if (href.isBlank() || title.isBlank()) return null
        return Movie(
            id = href,
            title = title,
            poster = poster,
            quality = quality,
        )
    }

    private fun parseHomeTvShow(el: Element): TvShow? {
        val titleAnchor = el.selectFirst("h3.card-title a[href]") ?: return null
        val href = titleAnchor.attr("href").trim()
        val rawTitle = titleAnchor.text().trim()
        val title = cleanTitle(rawTitle)
        val img = el.selectFirst(".card-image img[src]")?.attr("src").orEmpty()
        val poster = img
        val quality = if (rawTitle.contains("[HD]", ignoreCase = true) || rawTitle.contains("[HD/3D]", ignoreCase = true)) "HD" else null
        if (href.isBlank() || title.isBlank()) return null
        return TvShow(
            id = href,
            title = title,
            poster = poster,
            quality = quality,
        )
    }

    override suspend fun getHome(): List<Category> {
        val doc = service.getHome()

        val movies = doc.select("div.card.mp-post.horizontal").mapNotNull { parseHomeMovie(it) }
        return if (movies.isNotEmpty()) listOf(Category(name = "Film", list = movies)) else emptyList()
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()
            val home = service.getHome()
            val hdItems = home.select("#mega-menu-sequex-main-menu li:has(> a:matchesOwn(^Film HD Streaming$)) .mega-sub-menu a.mega-menu-link")
                .mapNotNull { a ->
                    val href = a.attr("href").trim()
                    val nameRaw = a.text().trim()
                    if (href.isBlank() || nameRaw.isBlank()) return@mapNotNull null
                    if (!href.contains("/category/film-hd-streaming/", true)) return@mapNotNull null
                    val normName = nameRaw.lowercase()
                        .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                        .replace(Regex("\\bhd\\b", RegexOption.IGNORE_CASE), "Hd")
                    Genre(id = if (href.startsWith("http")) href else baseUrl + href, name = normName)
                }

            val genreItems = home.select("#mega-menu-sequex-main-menu li:has(> a:matchesOwn(^Film per Genere$)) .mega-sub-menu a.mega-menu-link")
                .mapNotNull { a ->
                    val href = a.attr("href").trim()
                    val nameRaw = a.text().trim()
                    if (href.isBlank() || nameRaw.isBlank()) return@mapNotNull null
                    if (!href.contains("/category/", true)) return@mapNotNull null
                    val normName = nameRaw.lowercase().replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                    Genre(id = if (href.startsWith("http")) href else baseUrl + href, name = normName)
                }

            return (hdItems + genreItems).sortedBy { it.name }
        }

        val movieResults = try {
            val movieDoc = if (page <= 1) service.searchMovies(query) else service.searchMovies(page, query)
            movieDoc.select("div.card.mp-post.horizontal").mapNotNull { parseHomeMovie(it) }
        } catch (_: Exception) {
            emptyList()
        }
        
        val tvResults = try {
            val tvDoc = if (page <= 1) service.searchTvShows(query) else service.searchTvShows(page, query)
            tvDoc.select("div.card.mp-post.horizontal").mapNotNull { parseHomeTvShow(it) }
        } catch (_: Exception) {
            emptyList()
        }
        
        return movieResults + tvResults
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val doc = if (page <= 1) service.getHome() else service.getMovies(page)
            doc.select("div.card.mp-post.horizontal").mapNotNull { parseHomeMovie(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val doc = if (page <= 1) service.getTvShows() else service.getTvShows(page)
            doc.select("div.card.mp-post.horizontal").mapNotNull { parseHomeTvShow(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovie(id: String): Movie {
        val doc = service.getPage(id)

        val rawTitle = doc.selectFirst("span.breadcrumb_last")?.text()?.trim()
            ?: doc.selectFirst("h1, h2")?.text()?.trim()
            ?: ""
        val title = cleanTitle(rawTitle)
        val detailQuality = if (rawTitle.contains("[HD]", ignoreCase = true) || rawTitle.contains("[HD/3D]", ignoreCase = true)) "HD" else null

        val poster = doc.selectFirst("div.sequex-featured-img.s-post img[src]")?.attr("src")

        val categoryText = doc.selectFirst("div.ignore-css > p > strong")?.text()?.trim()
        val genres = categoryText?.let { parseGenresText(it, hasDurationMarker = true) } ?: emptyList()

        val overview = run {
            val paragraphs = doc.select("div.ignore-css > p")
            val chosen = paragraphs.firstOrNull { p ->
                val t = p.text().trim()
                t.isNotBlank() && !t.contains(" – DURATA", ignoreCase = true)
            }
            chosen?.text()
                ?.replace(Regex("\\s*\\+?Info\\s*»\\s*$", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("\\s*\\+?DATE\\s+USCITA\\s+EPISODI\\s*$", RegexOption.IGNORE_CASE), "")
                ?.trim()
        }

        val trailerDataSrc = doc
            .selectFirst("table.cbtable:has(font:matchesOwn(^Guarda il Trailer:$)) + p iframe[data-src*='youtube.com/embed/']")
            ?.attr("data-src")
            ?.takeIf { it.isNotBlank() }
        val trailerWatchUrl = trailerDataSrc
            ?.replace("/embed/", "/watch?v=")


        return Movie(
            id = id,
            title = title,
            poster = poster,
            overview = overview,
            genres = genres,
            trailer = trailerWatchUrl,
            quality = detailQuality,
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val doc = service.getPage(id)

        val rawTitle = doc.selectFirst("span.breadcrumb_last")?.text()?.trim()
            ?: doc.selectFirst("h1, h2")?.text()?.trim()
            ?: ""
        val title = cleanTitle(rawTitle)
        val poster = doc.selectFirst("div.sequex-featured-img.s-post img[src]")?.attr("src")

        val categoryText = doc.selectFirst("div.ignore-css > p > strong")?.text()?.trim()
        val genres = categoryText?.let { parseGenresText(it, hasDurationMarker = false) } ?: emptyList()

        val overview = run {
            val infoP = doc.selectFirst("div.ignore-css > p:has(strong)")
            infoP?.let { p ->
                p.selectFirst("strong")?.remove()
                val text = p.text().trim()
                    .replace(Regex("\\s*\\+?Info\\s*»\\s*$", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s*\\+?DATE\\s+USCITA\\s+EPISODI\\s*$", RegexOption.IGNORE_CASE), "")
                    .trim()
                text.takeIf { it.isNotBlank() }
            }
        }

        val tvTrailerIframe = doc.selectFirst(
            "table.cbtable:has(font:matchesOwn(^Guarda il Trailer:$)) + p iframe"
        )
        val tvTrailerRaw = tvTrailerIframe?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: tvTrailerIframe?.attr("src")?.takeIf { it.contains("youtube.com/embed/") }
        val tvTrailerWatchUrl = tvTrailerRaw
            ?.replace("/embed/", "/watch?v=")

        val seasons = doc.select("div.sp-wrap").mapNotNull { wrap ->
            val head = wrap.selectFirst("div.sp-head")?.text()?.trim() ?: return@mapNotNull null
            if (head.contains(Regex("STAGIONE\\s+\\d+\\s+A\\s+\\d+", RegexOption.IGNORE_CASE)) ||
                head.contains("TUTTE LE STAGIONI", ignoreCase = true)
            ) return@mapNotNull null
            val body = wrap.selectFirst("div.sp-body")
            if (body != null) {
                val hasMixdrop = body.select("a[href]").any { a ->
                    val href = a.attr("href").lowercase()
                    val text = a.text().lowercase()
                    text.contains("mixdrop") || href.contains("stayonline.pro")
                }
                if (!hasMixdrop) return@mapNotNull null
            }
            val seasonNumber = Regex("STAGIONE\\s+(\\d+)", RegexOption.IGNORE_CASE)
                .find(head)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            Season(
                id = "$id#s$seasonNumber",
                number = seasonNumber,
            )
        }

        return TvShow(
            id = id,
            title = title,
            poster = poster,
            trailer = tvTrailerWatchUrl,
            overview = overview,
            genres = genres,
            seasons = seasons,
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val showId = seasonId.substringBefore("#s")
        val seasonNum = seasonId.substringAfter("#s").toIntOrNull() ?: return emptyList()

        val doc = service.getPage(showId)
        val wrap = doc.select("div.sp-wrap").firstOrNull { w ->
            val head = w.selectFirst("div.sp-head")?.text()?.trim().orEmpty()
            head.contains(Regex("STAGIONE\\s+$seasonNum", RegexOption.IGNORE_CASE))
        } ?: return emptyList()

        val body = wrap.selectFirst("div.sp-body") ?: return emptyList()

        return body.select("p").mapNotNull { p ->
            val text = p.text().trim()
            val epNum = Regex("(\\d+)[x×](\\d+)").find(text)?.groupValues?.getOrNull(2)?.toIntOrNull()
                ?: return@mapNotNull null
            Episode(
                id = "$showId#s${seasonNum}-e${epNum}",
                number = epNum,
                title = null,
                poster = null,
            )
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id = id, name = "", filmography = emptyList())
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val url = if (page <= 1) id else id.trimEnd('/') + "/page/$page/"
        return try {
            val doc = service.getPage(url)
            val movies = doc.select("div.card.mp-post.horizontal").mapNotNull { parseHomeMovie(it) }
            Genre(id = id, name = id.substringAfterLast('/').replace('-', ' ').uppercase(), shows = movies)
        } catch (_: Exception) {
            Genre(id = id, name = id.substringAfterLast('/'), shows = emptyList())
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return when (videoType) {
            is Video.Type.Movie -> {
                val doc = service.getPage(id)

                val servers3D = mutableListOf<Video.Server>()
                val hdServers = mutableListOf<Video.Server>()
                val sdServers = mutableListOf<Video.Server>()
                var includeLinks = false
                var inHdSection = false
                var in3dSection = false

                for (tbl in doc.select("table.tableinside, table.cbtable")) {
                    val heading = tbl.selectFirst("u > strong")?.text()?.trim()?.lowercase()
                    if (heading != null) {
                        includeLinks = heading.startsWith("streaming")
                        inHdSection = heading.startsWith("streaming hd")
                        in3dSection = heading.startsWith("streaming 3d")
                        if (heading.startsWith("download")) includeLinks = false
                        continue
                    }

                    if (!includeLinks || !tbl.hasClass("tableinside")) continue

                    for (a in tbl.select("a[href]")) {
                        val href = a.attr("href").trim()
                        if (href.isBlank()) continue
                        val baseName = a.text().trim().ifBlank { "Server" }
                        val normalized = href
                        val lowerName = baseName.lowercase()
                        if (!lowerName.contains("mixdrop")) continue

                        fun addServer(finalUrl: String) {
                            val labeled = when {
                                in3dSection && !baseName.endsWith("3D", true) -> "$baseName 3D"
                                inHdSection && !baseName.endsWith("HD", true) -> "$baseName HD"
                                else -> baseName
                            }
                            val server = Video.Server(id = finalUrl, name = labeled, src = finalUrl)
                            when {
                                in3dSection -> servers3D.add(server)
                                inHdSection -> hdServers.add(server)
                                else -> sdServers.add(server)
                            }
                        }

                        if (normalized.contains("stayonline.pro", ignoreCase = true)) {
                            val stayId = Regex("/l/([A-Za-z0-9]+)/?").find(normalized)?.groupValues?.getOrNull(1)
                            if (!stayId.isNullOrBlank()) {
                                try {
                                    val resp = stayService.postAjax(
                                        url = "https://stayonline.pro/ajax/linkEmbedView.php",
                                        id = stayId,
                                        ref = "",
                                        userAgent = DEFAULT_USER_AGENT,
                                        referer = "https://stayonline.pro/"
                                    )
                                    val body = resp.body()?.string().orEmpty()
                                    val mixdropUrl = try {
                                        val json = JSONObject(body)
                                        if (json.optString("status").equals("success", ignoreCase = true)) {
                                            json.optJSONObject("data")?.optString("value")?.takeIf { it.isNotBlank() }
                                        } else null
                                    } catch (_: Exception) { null }
                                    if (!mixdropUrl.isNullOrBlank()) addServer(mixdropUrl)
                                } catch (_: Exception) { }
                            }
                        } else {
                            addServer(normalized)
                        }
                    }
                }

                hdServers + sdServers + servers3D
            }
            is Video.Type.Episode -> {
                val showId = id.substringBefore("#s")
                val part = id.substringAfter("#s")
                val seasonNum = part.substringBefore("-e").toIntOrNull() ?: return emptyList()
                val episodeNum = part.substringAfter("-e").toIntOrNull() ?: return emptyList()

                val doc = service.getPage(showId)
                val wrap = doc.select("div.sp-wrap").firstOrNull { w ->
                    val head = w.selectFirst("div.sp-head")?.text()?.trim().orEmpty()
                    head.contains(Regex("STAGIONE\\s+$seasonNum", RegexOption.IGNORE_CASE))
                } ?: return emptyList()
                val body = wrap.selectFirst("div.sp-body") ?: return emptyList()

                val line = body.select("p").firstOrNull { para ->
                    val text = para.text().trim()
                    Regex("(\\d+)[x×](\\d+)").find(text)?.groupValues?.getOrNull(2)?.toIntOrNull() == episodeNum
                } ?: return emptyList()

                val servers = mutableListOf<Video.Server>()
                for (a in line.select("a[href]")) {
                    val href = a.attr("href").trim()
                    val name = a.text().trim().lowercase()
                    if (href.isBlank() || !name.contains("mixdrop")) continue

                    if (href.contains("stayonline.pro", ignoreCase = true)) {
                        val stayId = Regex("/l/([A-Za-z0-9]+)/?").find(href)?.groupValues?.getOrNull(1)
                        if (!stayId.isNullOrBlank()) {
                            try {
                                val resp = stayService.postAjax(
                                    url = "https://stayonline.pro/ajax/linkEmbedView.php",
                                    id = stayId,
                                    ref = "",
                                    userAgent = DEFAULT_USER_AGENT,
                                    referer = "https://stayonline.pro/"
                                )
                                val body = resp.body()?.string().orEmpty()
                                val mixdropUrl = try {
                                    val json = JSONObject(body)
                                    if (json.optString("status").equals("success", ignoreCase = true)) {
                                        json.optJSONObject("data")?.optString("value")?.takeIf { it.isNotBlank() }
                                    } else null
                                } catch (_: Exception) { null }
                                if (!mixdropUrl.isNullOrBlank()) {
                                    servers.add(Video.Server(id = mixdropUrl, name = "Mixdrop", src = mixdropUrl))
                                }
                            } catch (_: Exception) { }
                        }
                    } else {
                        servers.add(Video.Server(id = href, name = "Mixdrop", src = href))
                    }
                }

                servers
            }
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src, server)
    }
}