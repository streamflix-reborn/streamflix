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
        val providers = listOf(
            SflixProvider,
            AnyMovieProvider,
            HiAnimeProvider,
            SerienStreamProvider,
            TmdbProvider,
            SuperStreamProvider,
            StreamingCommunityProvider,
            AnimeWorldProvider,
            AniWorldProvider,
            RidomoviesProvider,
            OtakufrProvider,
            WiflixProvider,
            MStreamProvider,
            FrenchAnimeProvider,
            FilmPalastProvider,
            CuevanaDosProvider,
            CuevanaEuProvider,
            LatanimeProvider,
            DoramasflixProvider,
            CineCalidadProvider,
            FlixLatamProvider,
            AnimeBumProvider,
            AnimefenixProvider,
            AnimeFlvProvider,
            SoloLatinoProvider,
            Cine24hProvider,
            PelisplustoProvider,
            CableVisionHDProvider,
        )
    }
}