package com.streamflixreborn.streamflix.fragments.season

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.EpisodeManager
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class SeasonViewModel(
    seasonId: String,
    private val tvShowId: String,
    private val database: AppDatabase,
) : ViewModel() {
    var seasonNumber = 0
    var tvShowTitle = ""
    private val _state = MutableStateFlow<State>(State.LoadingEpisodes)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoadingEpisodes -> {
                    database.episodeDao().getByIdsAsFlow(state.episodes.map { it.id })
                        .collect { emit(it) }
                }

                else -> emit(emptyList<Episode>())
            }
        },
        database.tvShowDao().getByIdAsFlow(tvShowId),
        database.seasonDao().getByIdAsFlow(seasonId),
    ) { state, episodesDb, tvShow, season ->
        season?.number?.let { seasonNumber = it }
        tvShow?.title?.let { tvShowTitle = it }
        when (state) {
            is State.SuccessLoadingEpisodes -> {
                State.SuccessLoadingEpisodes(
                    episodes = state.episodes.map { episode ->
                        episodesDb.find { it.id == episode.id }
                            ?.takeIf { !episode.isSame(it) }
                            ?.let { episode.copy().merge(it) }
                            ?: episode
                    }.onEach { episode ->
                        episode.tvShow = tvShow
                        episode.season = season
                    }
                )
            }
            else -> state
        }
    }

    sealed class State {
        data object LoadingEpisodes : State()
        data class SuccessLoadingEpisodes(val episodes: List<Episode>) : State()
        data class FailedLoadingEpisodes(val error: Exception) : State()
    }

    init {
        getSeasonEpisodes(seasonId)
    }


    fun getSeasonEpisodes(seasonId: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.LoadingEpisodes)

        try {
            val episodes = UserPreferences.currentProvider!!.getEpisodesBySeason(seasonId)

            database.episodeDao().getByIdsAsFlow(episodes.map { it.id }).first()
                .forEach { episodeDb ->
                    episodes.find { it.id == episodeDb.id }
                        ?.merge(episodeDb)
                }

            val tvShow = TvShow(tvShowId)
            val season = Season(seasonId)
            episodes.forEach { episode ->
                episode.tvShow = tvShow
                episode.season = season
            }

            database.episodeDao().insertAll(episodes)

            EpisodeManager.addEpisodes(EpisodeManager.convertToVideoTypeEpisodes(episodes, database, seasonNumber))
            _state.emit(State.SuccessLoadingEpisodes(episodes))
        } catch (e: Exception) {
            Log.e("SeasonViewModel", "getSeasonEpisodes: ", e)
            _state.emit(State.FailedLoadingEpisodes(e))
        }
    }

}