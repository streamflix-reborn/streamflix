package com.streamflixreborn.streamflix.providers

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Season
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import retrofit2.http.GET
import retrofit2.http.Url

object Altadefinizione01Provider : Provider {

    override val name: String = "Altadefinizione01"
    override val baseUrl: String = "https://altadefinizione01.wang"
    override val logo: String get() = "$baseUrl/templates/Darktemplate_pagespeed/images/logo.png"
    override val language: String = "it"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    private interface Service {
        @GET
        suspend fun getPage(@Url url: String): Document
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(getOkHttpClient())
        .build()

    private val service = retrofit.create(Service::class.java)

    private fun getOkHttpClient(): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)

        val doh = DnsOverHttps.Builder()
            .client(clientBuilder.build())
            .url("https://1.1.1.1/dns-query".toHttpUrl())
            .build()

        clientBuilder
            .dns(doh)
            .addInterceptor { chain ->
                val original = chain.request()
                val req = original.newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(req)
            }

        return clientBuilder.build()
    }

    override suspend fun getHome(): List<Category> {
        val doc = service.getPage(baseUrl)

        val categories = mutableListOf<Category>()

        doc.select("div.slider").forEach { slider ->
            val title = slider.selectFirst(".slider-strip b")?.text()?.trim()
                ?: return@forEach
            val items = slider.select(".boxgrid.caption").mapNotNull { parseGridItem(it) }
            if (items.isNotEmpty()) {
                categories.add(Category(name = title, list = items))
            }
        }

        doc.select("div.son_eklenen").forEach { section ->
            val strongTitle = section.selectFirst(".son_eklenen_head > strong")?.text()?.trim()
            val title = when {
                !strongTitle.isNullOrBlank() -> strongTitle
                section.selectFirst(".son_eklenen_head_tv") != null -> "Sub ITA"
                else -> return@forEach
            }
            val items = section.select("#son_eklenen_kapsul .boxgrid.caption").mapNotNull { parseGridItem(it) }
            if (items.isNotEmpty()) {
                categories.add(Category(name = title, list = items))
            }
        }

        return categories
    }

    private fun parseGridItem(el: Element): AppAdapter.Item? {
        val titleAnchor = el.selectFirst(".cover.boxcaption h2 a, h3 a, .boxcaption h2 a") ?: return null
        val title = titleAnchor.text().trim()
        val href = titleAnchor.attr("href").trim()
        val img = el.selectFirst("a > img")
        val imgUrlRaw = img?.attr("data-src") ?: ""
        val poster = normalizeUrl(imgUrlRaw)
        val isTv = el.selectFirst(".se_num") != null || el.selectFirst(".ml-cat a[href*='/serie-tv/']") != null

        return if (isTv) {
            TvShow(
                id = href,
                title = title,
                poster = poster,
                banner = null,
                rating = null,
            )
        } else {
            Movie(
                id = href,
                title = title,
                poster = poster,
                banner = null,
                rating = null
            )
        }
    }

    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> baseUrl.trimEnd('/') + url
            url.isBlank() -> ""
            else -> url
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()
            val home = service.getPage(baseUrl)
            val genreLinks = home
                .select(".widget-title:matches(^Categorie in Altadefinizione$)")
                .firstOrNull()?.parent()
                ?.select("#wtab1 .kategori_list li > a[href]") ?: emptyList()
            return genreLinks.mapNotNull { a ->
                val href = a.attr("href").trim()
                val text = a.text().trim()
                if (href.isBlank() || text.isBlank()) return@mapNotNull null
                Genre(
                    id = if (href.startsWith("http")) href else baseUrl + "/" + href.removePrefix("/")
                        .removePrefix(baseUrl.removeSuffix("/")),
                    name = text
                )
            }.sortedBy { (it as Genre).name }
        }

        val encoded = URLEncoder.encode(query, "UTF-8")

        val baseSearchUrl = "$baseUrl/?do=search&subaction=search&titleonly=3&story=$encoded"

        val firstDoc = service.getPage(baseSearchUrl)
        val hasPager = firstDoc.selectFirst("div.page_nav") != null
        if (page > 1 && !hasPager) return emptyList()

        val doc: Document = if (page <= 1) firstDoc else run {
            val searchStart = page
            val resultFrom = (page - 1) * 50 + 1
            val url = "$baseUrl/index.php?do=search&subaction=search&full_search=0&search_start=$searchStart&result_from=$resultFrom&story=$encoded"
            service.getPage(url)
        }

        return doc.select("#dle-content .boxgrid.caption")
            .mapNotNull { el -> parseGridItem(el) }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val base = "$baseUrl/cinema"
        val url = if (page > 1) "$base/page/$page/" else "$base/"

        val doc = service.getPage(url)

        return doc.select("#dle-content .boxgrid.caption")
            .mapNotNull { el -> parseGridItem(el) as? Movie }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val base = "$baseUrl/serie-tv"
        val url = if (page > 1) "$base/page/$page/" else "$base/"

        val doc = service.getPage(url)

        return doc.select("#dle-content .boxgrid.caption")
            .mapNotNull { el -> parseGridItem(el) as? TvShow }
    }

    override suspend fun getMovie(id: String): Movie {
        val doc = service.getPage(id)
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.selectFirst("h1,h2,title")?.text()?.trim()
            ?: ""
        val posterRaw = doc.selectFirst("img.wp-post-image")?.attr("data-src") ?: ""
        val poster = normalizeUrl(posterRaw)
        val rating = doc.selectFirst("div.imdb_r [itemprop=ratingValue]")?.text()?.trim()?.toDoubleOrNull()
        val overview = doc.selectFirst(".sbox .entry-content p")
            ?.ownText()
            ?.trim()
        val genres = doc.select("p.meta_dd b[title=Genere]")
            .firstOrNull()?.parent()?.select("a")?.map { a ->
                Genre(
                    id = a.attr("href"),
                    name = a.text().trim()
                )
            } ?: emptyList()
        val cast = doc.select("p.meta_dd.limpiar:has(b.icon-male) a[href]")
            .mapNotNull { a ->
                val name = a.text().trim()
                val href = a.attr("href").trim()
                if (name.isBlank() || href.isBlank()) return@mapNotNull null
                People(id = href, name = name)
            }
        return Movie(
            id = id,
            title = title,
            poster = poster,
            banner = null,
            rating = rating,
            overview = overview,
            genres = genres,
            cast = cast
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val doc = service.getPage(id)
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.selectFirst("h1,h2,title")?.text()?.trim()
            ?: ""
        val posterRaw = doc.selectFirst("img.wp-post-image")?.attr("data-src") ?: ""
        val poster = normalizeUrl(posterRaw)
        val rating = doc.selectFirst("div.imdb_r [itemprop=ratingValue]")?.text()?.trim()?.toDoubleOrNull()
        val overview = doc.selectFirst(".sbox .entry-content p")
            ?.ownText()
            ?.trim()
        val genres = doc.select("p.meta_dd b[title=Genere]")
            .firstOrNull()?.parent()?.select("a")?.map { a ->
                Genre(
                    id = a.attr("href"),
                    name = a.text().trim()
                )
            } ?: emptyList()
        
        val cast = doc.select("p.meta_dd.limpiar:has(b.icon-male) a[href]")
            .mapNotNull { a ->
                val name = a.text().trim()
                val href = a.attr("href").trim()
                if (name.isBlank() || href.isBlank()) return@mapNotNull null
                People(id = href, name = name)
            }
        val seasons = mutableListOf<Season>()
        doc.select("#tt_holder .tt_season ul li a[data-toggle=tab]").forEach { a ->
            val seasonId = a.attr("href").removePrefix("#")
            val seasonNumber = a.text().trim().toIntOrNull() ?: 0
            val episodes = mutableListOf<Episode>()
            val seasonPane = doc.selectFirst("#${seasonId}")
            seasonPane?.select("ul > li > a[allowfullscreen][data-link]")?.forEach { ep ->
                val epNum = ep.attr("data-num").substringAfter('x').toIntOrNull()
                    ?: ep.text().trim().toIntOrNull() ?: 0
                val epTitle = ep.attr("data-title").ifBlank { null }
                val mirrors = ep.parent()?.select(".mirrors a.mr[data-link]") ?: emptyList()
                val server = mirrors.firstOrNull { m ->
                    val name = m.text().trim()
                    !name.contains("4K", true)
                }
                episodes.add(
                    Episode(
                        id = "$id#s${seasonNumber}e$epNum",
                        number = epNum,
                        title = epTitle,
                        poster = null,
                    )
                )
            }
            seasons.add(
                Season(
                    id = "$id#season-$seasonNumber",
                    number = seasonNumber,
                    episodes = episodes
                )
            )
        }
        return TvShow(
            id = id,
            title = title,
            poster = poster,
            banner = null,
            rating = rating,
            overview = overview,
            genres = genres,
            cast = cast,
            seasons = seasons
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val showUrl = seasonId.substringBefore("#")
        val seasonNumber = seasonId.substringAfter("#season-").toIntOrNull() ?: 0
        val doc = service.getPage(showUrl)

        val paneId = "season-$seasonNumber"
        val seasonPane = doc.selectFirst("#${paneId}")
            ?: return emptyList()

        val episodes = mutableListOf<Episode>()
        seasonPane.select("ul > li > a[allowfullscreen][data-link]").forEach { ep ->
            val epNum = ep.attr("data-num").substringAfter('x').toIntOrNull()
                ?: ep.text().trim().toIntOrNull() ?: 0
            val epTitle = ep.attr("data-title").ifBlank { null }
            val mirrors = ep.parent()?.select(".mirrors a.mr[data-link]") ?: emptyList()
            val server = mirrors.firstOrNull { m ->
                val name = m.text().trim()
                !name.contains("4K", true)
            }
            episodes.add(
                Episode(
                    id = "$showUrl#s${seasonNumber}e$epNum",
                    number = epNum,
                    title = epTitle,
                    poster = null,
                )
            )
        }
        return episodes
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val url = if (page <= 1) id else id.trimEnd('/') + "/page/$page/"
        val doc = service.getPage(url)

        val name = ""

        val shows = doc.select("#dle-content .boxgrid.caption")
            .mapNotNull { el -> parseGridItem(el) as? com.streamflixreborn.streamflix.models.Show }

        return Genre(id = id, name = name, shows = shows)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        val baseDoc = service.getPage(id)
        val hasPager = baseDoc.selectFirst("div.page_nav") != null
        if (page > 1 && !hasPager) {
            return People(id = id, name = "", filmography = emptyList())
        }
        val doc = if (page <= 1) baseDoc else run {
            val base = id.trimEnd('/')
            val pagedBase = if (base.contains("/xfsearch/attori/")) base.replace("/xfsearch/attori/", "/find/") else base
            service.getPage("$pagedBase/page/$page/")
        }

        val name = ""

        val filmography = doc.select("#dle-content .boxgrid.caption")
            .mapNotNull { el -> parseGridItem(el) as? com.streamflixreborn.streamflix.models.Show }

        return People(
            id = id,
            name = name,
            filmography = filmography
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        if (videoType is Video.Type.Episode) {
            return try {
                val showUrl = id.substringBefore('#')
                val sPart = id.substringAfter('#', "") // format s{season}e{ep}
                val seasonNum = sPart.substringAfter('s').substringBefore('e').toIntOrNull() ?: 0
                val epNum = sPart.substringAfter('e').toIntOrNull() ?: 0
                val doc = service.getPage(showUrl)
                val pane = doc.selectFirst("#season-$seasonNum") ?: return emptyList()
                val epAnchor = pane.select("ul > li > a[allowfullscreen][data-link]")
                    .firstOrNull { a ->
                        val numAttr = a.attr("data-num")
                        val n = numAttr.substringAfter('x').toIntOrNull() ?: a.text().trim().toIntOrNull() ?: -1
                        n == epNum
                    } ?: return emptyList()
                val mirrors = epAnchor.parent()?.select(".mirrors a[data-link]") ?: emptyList()
                mirrors
                    .filterNot { m -> m.text().contains("4K", true) }
                    .mapNotNull { m ->
                        val link = m.attr("data-link").trim()
                        if (link.isBlank()) return@mapNotNull null
                        val normalized = when {
                            link.startsWith("//") -> "https:$link"
                            link.startsWith("http") -> link
                            else -> link
                        }
                        val name = m.text().trim().ifBlank { "Server" }
                        Video.Server(id = normalized, name = name, src = normalized)
                    }
            } catch (_: Exception) { emptyList() }
        }

        val doc = service.getPage(id)
        val iframeSrc = doc.selectFirst("iframe[src*='mostraguarda.stream']")?.attr("src")
            ?: throw Exception("Embed iframe not found")
        val embedUrl = normalizeUrl(iframeSrc)
        val embedDoc = service.getPage(embedUrl)
        return embedDoc.select("ul._player-mirrors li[data-link]")
            .filterNot { li -> li.hasClass("fullhd") || li.text().contains("4K", true) }
            .mapNotNull { li ->
                val dataLink = li.attr("data-link").trim()
                if (dataLink.isBlank()) return@mapNotNull null
                val normalized = when {
                    dataLink.startsWith("//") -> "https:$dataLink"
                    dataLink.startsWith("http") -> dataLink
                    else -> "https://$dataLink"
                }
                val nameText = li.ownText().ifBlank { li.text() }.trim()
                val name = nameText.ifBlank { "Server" }
                Video.Server(id = normalized, name = name, src = normalized)
            }
            .filter { it.src.isNotBlank() }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src)
    }
}


