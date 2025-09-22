package com.streamflixreborn.streamflix.providers

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.extractors.Extractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import retrofit2.Response
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import retrofit2.http.Header
import retrofit2.http.Body
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object StreamingItaProvider : Provider {

    override val name = "StreamingIta"
    override val baseUrl = "https://streamingita.homes"
    override val language = "it"
    override val logo: String get() = "$baseUrl/wp-content/uploads/2019/204/logos.png"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    private fun getOkHttpClient(): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
        
        val dns = DnsOverHttps.Builder()
            .client(clientBuilder.build())
            .url("https://1.1.1.1/dns-query".toHttpUrl())
            .build()
        
        return clientBuilder.dns(dns).build()
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(getOkHttpClient())
        .build()
    private val service = retrofit.create(Service::class.java)

    private interface Service {
        @GET
        suspend fun getPage(@Url url: String): Document
        
        @POST("wp-admin/admin-ajax.php")
        suspend fun getPlayerAjax(
            @Header("Referer") referer: String,
            @Body body: FormBody
        ): Response<ResponseBody>
    }

    override suspend fun getHome(): List<Category> {
        return try {
            val document = service.getPage(baseUrl)

			// Top slider
			val sliderItems = document.select("#slider-movies-tvshows article.item").mapNotNull { el ->
				val href = el.selectFirst(".image a")?.attr("href").orEmpty()
				val img = el.selectFirst(".image img")?.attr("src")
				val title = el.selectFirst(".data h3.title, .data h3 a, h3.title")?.text()
					?: el.selectFirst("img")?.attr("alt") ?: ""
				when {
					href.contains("/film/") -> Movie(id = href, title = title, poster = img, banner = img)
					href.contains("/tv/") -> TvShow(id = href, title = title, poster = img, banner = img)
					else -> null
				}
			}

			val categories = mutableListOf<Category>()
			if (sliderItems.isNotEmpty()) categories.add(Category(name = Category.FEATURED, list = sliderItems))

			val contentRoot = document.selectFirst("div.content.full_width_layout.normal") ?: document
			contentRoot.select("h2").forEach { headerEl ->
				val categoryName = headerEl.text().trim()
				val headerBlock = headerEl.parent() ?: headerEl
				var container = headerBlock.nextElementSibling()
				while (container != null && !(container.hasClass("items") || container.id() == "featured-titles" || container.id() == "dt-movies" || container.id() == "dt-tvshows")) {
					container = container.nextElementSibling()
				}
				if (container != null) {
					val items = container.select("article.item").mapNotNull { el ->
						val href = el.selectFirst("a")?.attr("href").orEmpty()
						if (href.isBlank()) return@mapNotNull null
						val img = el.selectFirst(".poster img, .image img, img")?.attr("src")
						val title = el.selectFirst(".data h3 a, .data h3, h3 a, h3.title")?.text()
							?: el.selectFirst("img")?.attr("alt") ?: ""
						when {
							href.contains("/tv/") -> TvShow(id = href, title = title, poster = img)
							href.contains("/film/") -> Movie(id = href, title = title, poster = img)
							else -> null
						}
					}
					if (items.isNotEmpty()) {
						categories.add(Category(name = categoryName, list = items))
					}
				}
			}

			categories
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()
            return try {
                val document = service.getPage(baseUrl)
                val filmMenu = document.selectFirst("ul#main_header > li a[href='/film/']")?.closest("li")
                val genreLinks = filmMenu?.select("ul.sub-menu li a[href]") ?: emptyList()
                genreLinks
                    .mapNotNull { a ->
                        val href = a.attr("href").trim()
                        val text = a.text().trim()
                        if (href.isBlank() || text.isBlank()) return@mapNotNull null
                        if (!href.contains("/genere/")) return@mapNotNull null
                        com.streamflixreborn.streamflix.models.Genre(
                            id = if (href.startsWith("http")) href else baseUrl + href.removePrefix("/"),
                            name = text
                        )
                    }
                    .sortedBy { it.name }
            } catch (_: Exception) {
                emptyList()
            }
        }

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = if (page > 1) "$baseUrl/page/$page/?s=$encoded" else "$baseUrl/?s=$encoded"
            val document = service.getPage(url)
            document.select(".search-page .result-item article").mapNotNull { el ->
                val titleAnchor = el.selectFirst(".details .title > a") ?: return@mapNotNull null
                val href = titleAnchor.attr("href").orEmpty()
                if (href.isBlank()) return@mapNotNull null
                val title = titleAnchor.text().trim()
                val img = el.selectFirst(".thumbnail img")?.attr("src")
                when {
                    href.contains("/film/") -> Movie(id = href, title = title, poster = img)
                    href.contains("/tv/") -> TvShow(id = href, title = title, poster = img)
                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val url = if (page > 1) "$baseUrl/film/page/$page/" else "$baseUrl/film/"
            val document = service.getPage(url)
            // Parse all items as in home, but flatten into a single list
            document.select("article.item").mapNotNull { el ->
                val href = el.selectFirst("a")?.attr("href").orEmpty()
                if (href.isBlank() || !href.contains("/film/")) return@mapNotNull null
                val img = el.selectFirst(".poster img, .image img, img")?.attr("src")
                val title = el.selectFirst(".data h3 a, .data h3, h3 a, h3.title")?.text()
                    ?: el.selectFirst("img")?.attr("alt")
                    ?: ""
                Movie(
                    id = href,
                    title = title,
                    poster = img
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val url = if (page > 1) "$baseUrl/tv/page/$page/" else "$baseUrl/tv/"
            val document = service.getPage(url)
            document.select("article.item").mapNotNull { el ->
                val href = el.selectFirst("a")?.attr("href").orEmpty()
                if (href.isBlank() || !href.contains("/tv/")) return@mapNotNull null
                val img = el.selectFirst(".poster img, .image img, img")?.attr("src")
                val title = el.selectFirst(".data h3 a, .data h3, h3 a, h3.title")?.text()
                    ?: el.selectFirst("img")?.attr("alt")
                    ?: ""
                TvShow(
                    id = href,
                    title = title,
                    poster = img
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovie(id: String): Movie {
        val document = service.getPage(id)
        val title = document.selectFirst("div.data > h1")?.text() ?: ""
        val poster = document.selectFirst("div.poster img[itemprop=image]")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = document.selectFirst("#info .wp-content p")?.text()
        val rating = document.selectFirst(".starstruck-rating .dt_rating_vgs")?.text()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        val trailer = document.selectFirst("#trailer iframe, #trailer .embed iframe")?.attr("src")
            ?.let { normalizeUrl(it) }
            ?.let { mapTrailerToWatchUrl(it) }
        val genres = document.select("div.sgeneros a[rel=tag]").map { el ->
            Genre(
                id = el.text(),
                name = el.text()
            )
        }
        val directors = document.select("#cast h2:matches(^Regia$) + .persons .person").map { el ->
            val name = el.selectFirst(".data .name a")?.text() ?: el.selectFirst("[itemprop=name]")?.attr("content") ?: ""
            val img = el.selectFirst(".img img")?.attr("src")
            val href = el.selectFirst(".data .name a")?.attr("href")
            People(
                id = href ?: name,
                name = name,
                image = img
            )
        }
        val cast = document.select("#cast h2:matches(^Cast$) + .persons .person").map { el ->
            val anchor = el.selectFirst(".data .name a")
            val name = anchor?.text() ?: el.selectFirst("[itemprop=name]")?.attr("content") ?: ""
            val img = el.selectFirst(".img img")?.attr("src")
            val href = anchor?.attr("href")
            val personId = href?.let { h ->
                img?.let { i -> "$h?poster=${URLEncoder.encode(i, "UTF-8")}" } ?: h
            }
            People(
                id = personId ?: name,
                name = name,
                image = img
            )
        }

        return Movie(
            id = id,
            title = title,
            poster = poster,
            overview = plot,
            rating = rating,
            trailer = trailer,
            genres = genres,
            directors = directors,
            cast = cast
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getPage(id)
        val title = document.selectFirst("div.data > h1")?.text() ?: ""
        val poster = document.selectFirst("div.poster img[itemprop=image]")?.attr("src")
        val plot = document.selectFirst("#info .wp-content p")?.text()
        val rating = document.selectFirst(".starstruck-rating .dt_rating_vgs")?.text()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        val trailer = document.selectFirst("#trailer iframe, #trailer .embed iframe")?.attr("src")
            ?.let { normalizeUrl(it) }
            ?.let { mapTrailerToWatchUrl(it) }
        val genres = document.select("div.sgeneros a[rel=tag]").map { el ->
            Genre(
                id = el.text(),
                name = el.text()
            )
        }
        val directors = document.select("#cast h2:matches(^Regia$) + .persons .person").map { el ->
            val anchor = el.selectFirst(".data .name a")
            val name = anchor?.text() ?: el.selectFirst("[itemprop=name]")?.attr("content") ?: ""
            val img = el.selectFirst(".img img")?.attr("src")
            val href = anchor?.attr("href")
            People(
                id = href ?: name,
                name = name,
                image = img
            )
        }
        val castPeople = document.select("#cast h2:matches(^Cast$) + .persons .person").map { el ->
            val anchor = el.selectFirst(".data .name a")
            val name = anchor?.text() ?: el.selectFirst("[itemprop=name]")?.attr("content") ?: ""
            val img = el.selectFirst(".img img")?.attr("src")
            val href = anchor?.attr("href")
            val personId = href?.let { h ->
                img?.let { i -> "$h?poster=${URLEncoder.encode(i, "UTF-8")}" } ?: h
            }
            People(
                id = personId ?: name,
                name = name,
                image = img
            )
        }
        val seasons = document.select("#serie_contenido #seasons .se-c").map { seasonEl ->
            val seasonNumber = seasonEl.selectFirst(".se-q .se-t")?.text()?.trim()?.toIntOrNull() ?: 1
            com.streamflixreborn.streamflix.models.Season(
                id = "$id?season=$seasonNumber",
                number = seasonNumber,
                title = "Stagione $seasonNumber",
            )
        }

        return TvShow(
            id = id,
            title = title,
            poster = poster,
            overview = plot,
            rating = rating,
            trailer = trailer,
            seasons = seasons,
            genres = genres,
            directors = directors,
            cast = castPeople
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<com.streamflixreborn.streamflix.models.Episode> {
        val seasonNumber = seasonId.substringAfter("?season=").toIntOrNull()
        val pageUrl = seasonId.substringBefore("?season=")
        val document = service.getPage(pageUrl)
        return document.select("#serie_contenido #seasons .se-c .se-a ul.episodios > li").mapNotNull { epEl ->
            val numText = epEl.selectFirst(".numerando")?.text()?.trim() ?: ""
            val seasonFromNum = numText.substringBefore("-").trim().toIntOrNull()
            if (seasonNumber != null && seasonFromNum != seasonNumber) return@mapNotNull null
            val epNumber = numText.substringAfter("-").trim().toIntOrNull() ?: 0
            val epLink = epEl.selectFirst(".episodiotitle a")?.attr("href").orEmpty()
            val epTitle = epEl.selectFirst(".episodiotitle a")?.text()
            com.streamflixreborn.streamflix.models.Episode(
                id = epLink,
                number = epNumber,
                title = epTitle,
                poster = epEl.selectFirst(".imagen img")?.attr("src"),
            )
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        // id è l'URL della pagina del genere
        return try {
            val base = if (id.startsWith("http")) id.removeSuffix("/") else "$baseUrl/${id.removePrefix("/").removeSuffix("/")}"
            val url = if (page > 1) "$base/page/$page/" else "$base/"
            val document = service.getPage(url)

            val name = document.selectFirst("h1, .archive-title, .data h1")?.text()?.trim()
                ?: id.substringAfterLast('/').replace('-', ' ')

            val shows = document.select("article.item").mapNotNull { el ->
                val href = el.selectFirst("a")?.attr("href").orEmpty()
                if (href.isBlank()) return@mapNotNull null
                val img = el.selectFirst(".poster img, .image img, img")?.attr("src")
                val title = el.selectFirst(".data h3 a, .data h3, h3 a, h3.title")?.text()
                    ?: el.selectFirst("img")?.attr("alt")
                    ?: ""
                // Considera solo i film per i generi di FILM STREAMING
                if (href.contains("/film/")) {
                    Movie(id = href, title = title, poster = img)
                } else null
            }

            com.streamflixreborn.streamflix.models.Genre(
                id = id,
                name = name,
                shows = shows
            )
        } catch (e: Exception) {
            com.streamflixreborn.streamflix.models.Genre(id = id, name = "", shows = emptyList())
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return try {
            if (page > 1) {
                return People(
                    id = id,
                    name = id.substringAfterLast('/').substringBefore('?').replace('-', ' '),
                    image = null,
                    filmography = emptyList()
                )
            }
            val posterFromId = id.substringAfter("poster=", "").substringBefore("&").let { raw ->
                if (raw.isBlank()) null else URLDecoder.decode(raw, "UTF-8")
            }
            val personUrl = id.substringBefore("?")
            val document = service.getPage(personUrl)
            val name = document.selectFirst(".data h1")?.text()
                ?: document.selectFirst("h1")?.text()
                ?: ""
            val poster = posterFromId ?: document.selectFirst(".poster img")?.attr("src")

            val filmography = parseMixedItems(document)

            People(
                id = id,
                name = name,
                image = poster,
                filmography = filmography
            )
        } catch (e: Exception) {
            throw e
        }
    }

    private fun parseMixedItems(document: Document): List<Show> {
        return document.select("article.item").mapNotNull { el ->
            val href = el.selectFirst("a")?.attr("href").orEmpty()
            if (href.isBlank()) return@mapNotNull null
            val img = el.selectFirst(".poster img, .image img, img")?.attr("src")
            val title = el.selectFirst(".data h3 a, .data h3, h3 a, h3.title")?.text()
                ?: el.selectFirst("img")?.attr("alt")
                ?: ""
            when {
                href.contains("/tv/") -> TvShow(id = href, title = title, poster = img)
                href.contains("/film/") -> Movie(id = href, title = title, poster = img)
                else -> null
            }
        }
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val document = service.getPage(id)
            val postId = document.select("[data-post]").firstOrNull()?.attr("data-post")
                ?: document.select("#report-video").attr("data-post").ifBlank { null }
                ?: throw Exception("Post id not found")
            
            val contentType = document.select("[data-type]").firstOrNull()?.attr("data-type")
                ?: document.select("#report-video").attr("data-type").ifBlank { "movie" }
                ?: "movie"

            // First: Server1
            val embedUrl1 = requestEmbedUrl(postId, nume = "1", type = contentType)
            val finalUrl1 = followRedirect(embedUrl1)

            val host1 = runCatching { finalUrl1.toHttpUrlOrNull()?.host }
                .getOrNull()?.replaceFirst("www.", "")?.substringBefore('.')
                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                ?: "Server"

            val resultServers = mutableListOf(
                Video.Server(
                    id = finalUrl1,
                    name = host1,
                    src = finalUrl1
                )
            )

            // Then: Server2 (mirrors), if present
            val availableServers = document.select("[data-nume]").mapNotNull { 
                it.attr("data-nume").toIntOrNull() 
            }.toSet()
            val hasServer2 = availableServers.contains(2)
            if (hasServer2) {
                val serversFromMirrors = runCatching {
                    val embedUrl2 = requestEmbedUrl(postId, nume = "2", type = contentType)
                    val mirrorsPageUrl = followRedirect(embedUrl2)
                    val mirrorsHtml = httpGet(mirrorsPageUrl, referer = id)
                    val mirrorsDoc = Jsoup.parse(mirrorsHtml, mirrorsPageUrl)

                    mirrorsDoc.select("ul._player-mirrors li[data-link]")
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
                            val name = nameText.ifBlank {
                                normalized.toHttpUrlOrNull()?.host?.substringBefore('.')
                                    ?.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                                    ?: "Server"
                            }
                            Video.Server(
                                id = normalized,
                                name = name,
                                src = normalized
                            )
                        }
                        .filter { it.src.isNotBlank() }
                        // deduplicate by src, prefer the entry whose name contains "hd"
                        .groupBy { it.src }
                        .map { (_, list) ->
                            list.maxByOrNull { server ->
                                val n = server.name.lowercase()
                                if ("hd" in n) 2 else 1
                            }!!
                        }
                }.getOrDefault(emptyList())

                if (serversFromMirrors.isNotEmpty()) resultServers.addAll(serversFromMirrors)
            }

            resultServers
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.id, server)
    }

    private suspend fun requestEmbedUrl(postId: String, nume: String, type: String): String {
        val formBody = FormBody.Builder()
            .add("action", "doo_player_ajax")
            .add("post", postId)
            .add("nume", nume)
            .add("type", type)
            .build()
        
        val response = service.getPlayerAjax(baseUrl, formBody)
        val body = response.body()?.string() ?: throw Exception("Empty response")
        val embed = org.json.JSONObject(body).optString("embed_url")
        if (embed.isNullOrBlank()) throw Exception("embed_url not found")
        return embed.replace("\\/", "/")
    }

    private suspend fun httpGet(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
        val reqBuilder = Request.Builder().url(url).header("User-Agent", DEFAULT_USER_AGENT)
        referer?.let { reqBuilder.header("Referer", it) }
        client.newCall(reqBuilder.build()).execute().use { resp ->
            resp.body?.string() ?: ""
        }
    }

    private suspend fun followRedirect(url: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", baseUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }

    private fun normalizeUrl(raw: String): String {
        val src = raw.trim()
        return when {
            src.startsWith("//") -> "https:$src"
            src.startsWith("http") -> src
            else -> "https://$src"
        }
    }

    private fun mapTrailerToWatchUrl(url: String): String {
        return when {
            url.contains("youtube.com/embed/") -> url.replace("/embed/", "/watch?v=").substringBefore("?")
            else -> url
        }
    }
}


