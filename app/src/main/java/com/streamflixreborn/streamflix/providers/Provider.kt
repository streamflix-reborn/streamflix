package com.streamflixreborn.streamflix.providers

import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video

interface Provider {

    val baseUrl: String
    val name: String
    val logo: String
    val language: String

    suspend fun getHome(): List<Category>

    suspend fun search(query: String, page: Int = 1): List<AppAdapter.Item>

    suspend fun getMovies(page: Int = 1): List<Movie>

    suspend fun getTvShows(page: Int = 1): List<TvShow>

    suspend fun getMovie(id: String): Movie

    suspend fun getTvShow(id: String): TvShow

    suspend fun getEpisodesBySeason(seasonId: String): List<Episode>

    suspend fun getGenre(id: String, page: Int = 1): Genre

    suspend fun getPeople(id: String, page: Int = 1): People

    suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server>

    suspend fun getVideo(server: Video.Server): Video

    companion object {
        data class ProviderSupport(
            val movies: Boolean,
            val tvShows: Boolean
        )
        
        val providers = mapOf(
            SflixProvider to ProviderSupport(movies = true, tvShows = true),
            AnyMovieProvider to ProviderSupport(movies = true, tvShows = true),
            HiAnimeProvider to ProviderSupport(movies = true, tvShows = true),
            SerienStreamProvider to ProviderSupport(movies = false, tvShows = true),
            TmdbProvider to ProviderSupport(movies = true, tvShows = true),
            SuperStreamProvider to ProviderSupport(movies = true, tvShows = true),
            StreamingCommunityProvider to ProviderSupport(movies = true, tvShows = true),
            AnimeWorldProvider to ProviderSupport(movies = true, tvShows = true),
            AniWorldProvider to ProviderSupport(movies = false, tvShows = true),
            RidomoviesProvider to ProviderSupport(movies = true, tvShows = true),
            OtakufrProvider to ProviderSupport(movies = true, tvShows = true),
            WiflixProvider to ProviderSupport(movies = true, tvShows = true),
            MStreamProvider to ProviderSupport(movies = true, tvShows = true),
            FrenchAnimeProvider to ProviderSupport(movies = true, tvShows = true),
            FilmPalastProvider to ProviderSupport(movies = true, tvShows = true),
            CuevanaDosProvider to ProviderSupport(movies = true, tvShows = true),
            CuevanaEuProvider to ProviderSupport(movies = true, tvShows = true),
            LatanimeProvider to ProviderSupport(movies = true, tvShows = true),
            DoramasflixProvider to ProviderSupport(movies = true, tvShows = true),
            CineCalidadProvider to ProviderSupport(movies = true, tvShows = true),
            FlixLatamProvider to ProviderSupport(movies = true, tvShows = true),
            LaCartoonsProvider to ProviderSupport(movies = false, tvShows = true),
            AnimeBumProvider to ProviderSupport(movies = false, tvShows = true),
            AnimefenixProvider to ProviderSupport(movies = false, tvShows = true),
            AnimeFlvProvider to ProviderSupport(movies = false, tvShows = true),
            SoloLatinoProvider to ProviderSupport(movies = true, tvShows = true),
            Cine24hProvider to ProviderSupport(movies = true, tvShows = true),
            PelisplustoProvider to ProviderSupport(movies = true, tvShows = true),
            CableVisionHDProvider to ProviderSupport(movies = false, tvShows = true),
        )

        // Helper functions to check support
        fun supportsMovies(provider: Provider): Boolean {
            val support = providers[provider] ?: ProviderSupport(movies = true, tvShows = true)
            return support.movies
        }
        
        fun supportsTvShows(provider: Provider): Boolean {
            val support = providers[provider] ?: ProviderSupport(movies = true, tvShows = true)
            return support.tvShows
        }
    }
}