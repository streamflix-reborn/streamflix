package com.streamflixreborn.streamflix.providers

import com.google.gson.annotations.SerializedName
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.VixcloudExtractor
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.logging.HttpLoggingInterceptor // Import aggiunto
import org.json.JSONObject
import org.jsoup.parser.Parser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext
import java.security.SecureRandom

object StreamingCommunityProvider : Provider {
    private const val DEFAULT_DOMAIN: String = "streamingcommunityz.me"
    override val baseUrl = DEFAULT_DOMAIN
    private var _domain: String? = null
    private var domain: String
        get() {
            if (!_domain.isNullOrEmpty())
                return _domain!!

            val storedDomain = UserPreferences.streamingcommunityDomain

            _domain = if (storedDomain.isNullOrEmpty())
                DEFAULT_DOMAIN
            else
                storedDomain

            return _domain!!
        }
        set(value) {
            if (value != domain) {
                _domain = value
                UserPreferences.streamingcommunityDomain = value
                rebuildService(value)
            }
        }
    private const val LANG = "it"

    override val name = "StreamingCommunity"
    override val logo = "https://$domain/apple-touch-icon.png"
    override val language = "it"
    private const val MAX_SEARCH_RESULTS = 60

    private var service = StreamingCommunityService.build("https://$domain/")

    fun rebuildService(newDomain: String = domain) {
        val finalBase = resolveFinalBaseUrl("https://$newDomain/")
        domain = finalBase.substringAfter("https://").substringBefore("/")
        service = StreamingCommunityService.build(finalBase)
    }

    private fun rebuildServiceUnsafe(newDomain: String = domain) {
        val finalBase = resolveFinalBaseUrl("https://$newDomain/")
        domain = finalBase.substringAfter("https://").substringBefore("/")
        service = StreamingCommunityService.buildUnsafe(finalBase)
    }

    private fun resolveFinalBaseUrl(startBaseUrl: String): String {
        return try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            val req = okhttp3.Request.Builder().url(startBaseUrl).get().build()
            client.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url
                finalUrl.scheme + "://" + finalUrl.host + "/"
            }
        } catch (_: Exception) {
            startBaseUrl
        }
    }

    private suspend fun <T> withSslFallback(block: suspend (StreamingCommunityService) -> T): T {
        return try {
            block(service)
        } catch (e: Exception) {
            val isSsl = e is SSLHandshakeException || e is CertPathValidatorException
            if (!isSsl) throw e
            rebuildServiceUnsafe(domain)
            block(service)
        }
    }

    private var version: String = ""
        get() {
            if (field != "") return field

            val document = runBlocking { withSslFallback { it.getHome() } }
            val dataAttr = document.selectFirst("#app")?.attr("data-page") ?: ""
            val decoded = Parser.unescapeEntities(dataAttr, false)
            val dataJson = JSONObject(decoded)
            // Update domain if app_url points to a different host (regardless of TLD)
            try {
                val props = dataJson.optJSONObject("props")
                val appUrl = props?.optString("app_url") ?: ""
                if (appUrl.startsWith("http")) {
                    val newHost = appUrl.substringAfter("://").substringBefore("/")
                    if (newHost.isNotEmpty() && newHost != domain) {
                        domain = newHost
                        rebuildService(newHost)
                    }
                }
            } catch (_: Exception) { }
            field = dataJson.getString("version")
            return field
        }

    private fun getImageLink(filename: String?): String? {
        if (filename.isNullOrEmpty())
            return null
        return "https://cdn.$domain/images/$filename"
    }

    override suspend fun getHome(): List<Category> {
        val res = withSslFallback { it.getHome(version = version) }
        if (version != res.version) version = res.version

        val mainTitles = res.props.sliders[2].titles

        val categories = mutableListOf<Category>()

        categories.add(
            // 2: top10
            Category(
                name = Category.FEATURED,
                list = mainTitles.map {
                    if (it.type == "movie")
                        Movie(
                            id = it.id + "-" + it.slug,
                            title = it.name,
                            banner = getImageLink(it.images.find { image -> image.type == "background" }?.filename),
                            rating = it.score
                        )
                    else
                        TvShow(
                            id = it.id + "-" + it.slug,
                            title = it.name,
                            banner = getImageLink(it.images.find { image -> image.type == "background" }?.filename),
                            rating = it.score
                        )
                },
            )
        )

        categories.addAll(
            // 0: trending, 1:latest
            listOf(0, 1).map { index ->
                val slider = res.props.sliders[index]
                Category(
                    name = slider.label,
                    list = slider.titles.map {
                        if (it.type == "movie")
                            Movie(
                                id = it.id + "-" + it.slug,
                                title = it.name,
                                released = it.lastAirDate,
                                rating = it.score,
                                poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename),
                                banner = getImageLink(it.images.find { image -> image.type == "background" }?.filename)
                            )
                        else
                            TvShow(
                                id = it.id + "-" + it.slug,
                                title = it.name,
                                released = it.lastAirDate,
                                rating = it.score,
                                poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename),
                                banner = getImageLink(it.images.find { image -> image.type == "background" }?.filename)
                            )
                    }
                )
            }
        )

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) {
            val res = service.getHome(version = version)
            if (version != res.version) version = res.version

            return res.props.genres.map {
                Genre(
                    id = it.id,
                    name = it.name
                )
            }.sortedBy { it.name }
        }

        val res = withSslFallback { it.search(query, (page - 1) * MAX_SEARCH_RESULTS) }
        if (res.currentPage == null || res.lastPage == null || res.currentPage > res.lastPage) {
            return listOf()
        }

        return res.data.map {
            val poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename)

            if (it.type == "movie")
                Movie(
                    id = it.id + "-" + it.slug,
                    title = it.name,
                    released = it.lastAirDate,
                    rating = it.score,
                    poster = poster
                )
            else
                TvShow(
                    id = it.id + "-" + it.slug,
                    title = it.name,
                    released = it.lastAirDate,
                    rating = it.score,
                    poster = poster
                )
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        if (page > 1)
            return listOf()

        val res = withSslFallback { it.getArchive(type = "movie", version = version) }

        val movies = mutableListOf<Movie>()

        res.titles.map { title ->
            val poster = getImageLink(title.images.find { image -> image.type == "poster" }?.filename)

            movies.add(
                Movie(
                    id = title.id + "-" + title.slug,
                    title = title.name,
                    released = title.lastAirDate,
                    rating = title.score,
                    poster = poster
                )
            )
        }

        return movies.distinctBy { it.id }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        if (page > 1)
            return listOf()

        val res = withSslFallback { it.getArchive(type = "tv", version = version) }

        val tvShows = mutableListOf<TvShow>()

        res.titles.map { title ->
            val poster = getImageLink(title.images.find { image -> image.type == "poster" }?.filename)

            tvShows.add(
                TvShow(
                    id = title.id + "-" + title.slug,
                    title = title.name,
                    released = title.lastAirDate,
                    rating = title.score,
                    poster = poster
                )
            )
        }

        return tvShows.distinctBy { it.id }
    }


    override suspend fun getMovie(id: String): Movie {
        val res = withSslFallback { it.getDetails(id, version = version) }
        if (version != res.version) version = res.version

        val title = res.props.title

        return Movie(
            id = id,
            title = title.name,
            overview = title.plot,
            released = title.lastAirDate,
            rating = title.score,
            poster = getImageLink(title.images.find { image -> image.type == "poster" }?.filename),
            genres = title.genres?.map {
                Genre(
                    id = it.id,
                    name = it.name
                )
            } ?: listOf(),
            cast = title.actors?.map {
                People (
                    id = it.name,
                    name = it.name
                )
            } ?: listOf(),
            trailer = let {
                val trailerId = title.trailers?.find { trailer -> trailer.youtubeId != "" }?.youtubeId
                if (!trailerId.isNullOrEmpty())
                    "https://youtube.com/watch?v=$trailerId"
                else
                    null
            },
            recommendations = let {
                if (res.props.sliders.isNotEmpty())
                    res.props.sliders[0].titles.map {
                        if (it.type == "movie") {
                            Movie(
                                id = it.id + "-" + it.slug,
                                title = it.name,
                                rating = it.score,
                                poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename)
                            )
                        } else {
                            TvShow(
                                id = it.id + "-" + it.slug,
                                title = it.name,
                                rating = it.score,
                                poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename)
                            )
                        }
                    }
                else
                    listOf()
            }
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val res = withSslFallback { it.getDetails(id, version = version) }
        if (version != res.version) version = res.version

        val title = res.props.title

        return TvShow(
            id = id,
            title = title.name,
            overview = title.plot,
            released = title.lastAirDate,
            rating = title.score,
            poster = getImageLink(title.images.find { image -> image.type == "poster" }?.filename),
            genres = title.genres?.map {
                Genre(
                    id = it.id,
                    name = it.name
                )
            } ?: listOf(),
            cast = title.actors?.map {
                People (
                    id = it.name,
                    name = it.name
                )
            } ?: listOf(),
            trailer = let {
                val trailerId = title.trailers?.find { trailer -> trailer.youtubeId != "" }?.youtubeId
                if (!trailerId.isNullOrEmpty())
                    "https://youtube.com/watch?v=$trailerId"
                else
                    null
            },
            recommendations = let {
                if (res.props.sliders.isNotEmpty())
                    res.props.sliders[0].titles.map {
                        if (it.type == "movie") {
                            Movie(
                                id = it.id + "-" + it.slug,
                                title = it.name,
                                rating = it.score,
                                poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename)
                            )
                        } else {
                            TvShow(
                                id = it.id + "-" + it.slug,
                                title = it.name,
                                rating = it.score,
                                poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename)
                            )
                        }
                    }
                else
                    listOf()
            },
            seasons = title.seasons?.map {
                Season(
                    id = "$id/season-${it.number}",
                    number = it.number.toIntOrNull() ?: (title.seasons.indexOf(it) + 1),
                    title = it.name
                )
            } ?: listOf()
        )
    }


    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val res = withSslFallback { it.getSeasonDetails(seasonId, version = version) }
        if (version != res.version) version = res.version

        return res.props.loadedSeason.episodes.map {
            Episode(
                id = "${seasonId.substringBefore("-")}?episode_id=${it.id}",
                number = it.number.toIntOrNull() ?: (res.props.loadedSeason.episodes.indexOf(it) + 1),
                title = it.name,
                poster = getImageLink(it.images.find { image -> image.type == "cover" }?.filename)
            )
        }
    }


    override suspend fun getGenre(id: String, page: Int): Genre {
        val res = withSslFallback { it.getArchive(genreId = id, offset = (page - 1) * MAX_SEARCH_RESULTS, version = version) }

        val genre = Genre(
            id = id,
            name = "",

            shows = res.titles.map {
                val poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename)

                if (it.type == "movie")
                    Movie(
                        id = it.id + "-" + it.slug,
                        title = it.name,
                        released = it.lastAirDate,
                        rating = it.score,
                        poster = poster
                    )
                else
                    TvShow(
                        id = it.id + "-" + it.slug,
                        title = it.name,
                        released = it.lastAirDate,
                        rating = it.score,
                        poster = poster
                    )
            }
        )

        return genre
    }


    override suspend fun getPeople(id: String, page: Int): People {
        val res = withSslFallback { it.search(id, (page - 1) * MAX_SEARCH_RESULTS) }
        if (res.currentPage == null || res.lastPage == null || res.currentPage > res.lastPage) {
            return People(
                id = id,
                name = id
            )
        }

        return People(
            id = id,
            name = id,
            filmography = res.data.map {
                val poster = getImageLink(it.images.find { image -> image.type == "poster" }?.filename)

                if (it.type == "movie")
                    Movie(
                        id = it.id + "-" + it.slug,
                        title = it.name,
                        released = it.lastAirDate,
                        rating = it.score,
                        poster = poster
                    )
                else
                    TvShow(
                        id = it.id + "-" + it.slug,
                        title = it.name,
                        released = it.lastAirDate,
                        rating = it.score,
                        poster = poster
                    )
            }
        )
    }


    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val base = "https://$domain/"
        val iframeUrl = when (videoType) {
            is Video.Type.Movie -> base + "$LANG/iframe/" + id.substringBefore("-")
            is Video.Type.Episode -> base + "$LANG/iframe/" + id.substringBefore("?") + "?episode_id=" + id.substringAfter("=") + "&next_episode=1"
        }
        val document = StreamingCommunityService.fetchDocumentWithRedirectsAndSslFallback(iframeUrl, base)

        val src = document.selectFirst("iframe")?.attr("src") ?: ""

        return listOf(Video.Server(
            id = id,
            name = "Vixcloud",
            src = src
        ))
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return VixcloudExtractor().extract(server.src)
    }


    private class UserAgentInterceptor(private val userAgent: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", userAgent)
                .build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    private class RefererInterceptor(private val referer: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val request = original.newBuilder()
                .header("Referer", referer)
                .build()
            return chain.proceed(request)
        }
    }

    private class RedirectInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            var response = chain.proceed(request)

            val visited = mutableSetOf<String>()
            while (response.isRedirect) {
                val location = response.header("Location")
                if (location.isNullOrEmpty()) break

                val newUrl = if (location.startsWith("http")) location else {
                    val base = request.url
                    base.resolve(location)?.toString() ?: break
                }

                // detect redirect loops
                if (!visited.add(newUrl)) {
                    break
                }

                // Update provider domain from absolute URL
                val host = newUrl.substringAfter("https://").substringBefore("/")
                if (host.isNotEmpty()) {
                    domain = host
                }

                response.close()
                request = request.newBuilder()
                    .url(newUrl)
                    .build()
                response = chain.proceed(request)
            }

            return response
        }
    }

    private interface StreamingCommunityService {

        companion object {
            private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

            fun build(baseUrl: String): StreamingCommunityService {
                val logging = HttpLoggingInterceptor()
                logging.setLevel(HttpLoggingInterceptor.Level.BODY)

                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor(RefererInterceptor(baseUrl))
                    .addInterceptor(UserAgentInterceptor(USER_AGENT))
                    .addInterceptor(logging) // Added HttpLoggingInterceptor

                val dohProviderUrl = UserPreferences.dohProviderUrl
                if (dohProviderUrl.isNotEmpty() && dohProviderUrl != UserPreferences.DOH_DISABLED_VALUE) {
                    try {
                        val bootstrap = OkHttpClient.Builder()
                            .readTimeout(15, TimeUnit.SECONDS)
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .build()

                        val primaryDoh = DnsOverHttps.Builder().client(bootstrap)
                            .url(dohProviderUrl.toHttpUrl())
                            .build()

                        // Google DoH as secondary fallback
                        val googleDoh = DnsOverHttps.Builder().client(bootstrap)
                            .url("https://dns.google/dns-query".toHttpUrl())
                            .build()

                        // Fallback: primary → Google DoH
                        clientBuilder.dns(object : Dns {
                            override fun lookup(hostname: String): List<InetAddress> {
                                try { return primaryDoh.lookup(hostname) } catch (_: UnknownHostException) {}
                                try { return googleDoh.lookup(hostname) } catch (_: UnknownHostException) {}
                                throw UnknownHostException("DoH resolution failed for $hostname (primary and Google)")
                            }
                        })
                    } catch (_: IllegalArgumentException) {
                        // Invalid DoH URL: fallback to Google DoH only
                        val bootstrap = OkHttpClient.Builder()
                            .readTimeout(15, TimeUnit.SECONDS)
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .build()
                        val googleDoh = DnsOverHttps.Builder().client(bootstrap)
                            .url("https://dns.google/dns-query".toHttpUrl())
                            .build()
                        clientBuilder.dns(googleDoh)
                    }
                }

                val client = clientBuilder.build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(StreamingCommunityService::class.java)
            }

            fun buildUnsafe(baseUrl: String): StreamingCommunityService {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    }
                )
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val trustManager = trustAllCerts[0] as X509TrustManager

                val logging = HttpLoggingInterceptor()
                logging.setLevel(HttpLoggingInterceptor.Level.BODY)

                val client = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor(RefererInterceptor(baseUrl))
                    .addInterceptor(UserAgentInterceptor(USER_AGENT))
                    .addInterceptor(logging) // Added HttpLoggingInterceptor
                    .sslSocketFactory(sslContext.socketFactory, trustManager)
                    .hostnameVerifier { _, _ -> true }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(StreamingCommunityService::class.java)
            }

            private fun buildManualClientUnsafe(): OkHttpClient {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    }
                )
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val trustManager = trustAllCerts[0] as X509TrustManager
                return OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .sslSocketFactory(sslContext.socketFactory, trustManager)
                    .hostnameVerifier { _, _ -> true }
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()
            }

            private fun buildManualClient(): OkHttpClient {
                return OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()
            }

            fun fetchDocumentWithRedirectsAndSslFallback(url: String, referer: String): Document {
                return try {
                    fetchDocumentWithRedirects(url, referer, buildManualClient())
                } catch (e: Exception) {
                    if (e is SSLHandshakeException || e is CertPathValidatorException) {
                        fetchDocumentWithRedirects(url, referer, buildManualClientUnsafe())
                    } else throw e
                }
            }

            private fun fetchDocumentWithRedirects(urlStart: String, referer: String, client: OkHttpClient): Document {
                var currentUrl = urlStart
                val visited = mutableSetOf<String>()
                while (true) {
                    if (!visited.add(currentUrl)) {
                        break
                    }
                    val req = okhttp3.Request.Builder()
                        .url(currentUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", referer)
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isRedirect) {
                            val loc = resp.header("Location")
                            if (loc.isNullOrEmpty()) break
                            val resolved = resp.request.url.resolve(loc)?.toString() ?: break
                            currentUrl = resolved
                            continue
                        }
                        val body = resp.body?.string() ?: ""
                        return Jsoup.parse(body)
                    }
                }
                // fallback empty document
                return Jsoup.parse("")
            }
        }


        @GET("/$LANG")
        suspend fun getHome(): Document

        @GET("/$LANG")
        suspend fun getHome(
            @Header("x-inertia") xInertia: String = "true",
            @Header("x-inertia-version") version: String
        ): HomeRes

        @GET("api/search")
        suspend fun search(
            @Query("q", encoded = true) keyword: String,
            @Query("offset") offset: Int = 0,
            @Query("lang") language: String = LANG
        ): SearchRes

        @GET("api/archive")
        suspend fun getArchive(
            @Query("genre[]") genreId: String? = null,
            @Query("type") type: String? = null,
            @Query("offset") offset: Int = 0,
            @Header("x-inertia") xInertia: String = "true",
            @Header("x-inertia-version") version: String,
            @Query("lang") language: String = LANG
        ): ArchiveRes

        @GET("$LANG/titles/{id}")
        suspend fun getDetails(
            @Path("id") id: String,
            @Header("x-inertia") xInertia: String = "true",
            @Header("x-inertia-version") version: String
        ): HomeRes

        @GET("$LANG/titles/{id}/")
        suspend fun getSeasonDetails(
            @Path("id") id: String,
            @Header("x-inertia") xInertia: String = "true",
            @Header("x-inertia-version") version: String
        ): SeasonRes

        @GET("$LANG/iframe/{id}")
        suspend fun getIframe(@Path("id") id: String): Document

        @GET("$LANG/iframe/{id}")
        suspend fun getIframe(@Path("id") id: String,
                              @Query("episode_id") episodeId: String,
                              @Query("next_episode") nextEpisode: Char = '1'
        ): Document

        data class Image(
            val filename: String,
            val type: String
        )
        data class Genre(
            val id: String,
            val name: String
        )
        data class Actor(
            val id: String,
            val name: String
        )
        data class Trailer(
            @SerializedName("youtube_id") val youtubeId: String?
        )
        data class Season(
            val number: String,
            val name: String?
        )
        data class Show(
            val id: String,
            val name: String,
            val type: String,
            val score: Double,
            val lastAirDate: String,
            val images: List<Image>,
            val slug: String,
            val plot: String?,
            val genres: List<Genre>?,
            @SerializedName("main_actors") val actors: List<Actor>?,
            val trailers: List<Trailer>?,
            val seasons: List<Season>?
        )
        data class Slider(
            val label: String,
            val name: String,
            val titles: List<Show>
        )
        data class Props(
            val genres: List<Genre>,
            val sliders: List<Slider>,
            val title: Show
        )

        data class HomeRes(
            val version: String,
            val props: Props
        )

        data class SearchRes(
            val data: List<Show>,
            @SerializedName("current_page") val currentPage: Int?,
            @SerializedName("last_page") val lastPage: Int?
        )

        data class SeasonPropsEpisodes(
            val id: String,
            val images: List<Image>,
            val name: String,
            val number: String
        )
        data class SeasonPropsDetails(
            val episodes: List<SeasonPropsEpisodes>
        )
        data class SeasonProps(
            val loadedSeason: SeasonPropsDetails
        )
        data class SeasonRes(
            val version: String,
            val props: SeasonProps
        )

        data class ArchiveRes(
            val titles: List<Show>
        )
    }
}
