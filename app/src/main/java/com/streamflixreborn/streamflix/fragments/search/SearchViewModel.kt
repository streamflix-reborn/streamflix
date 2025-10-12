package com.streamflixreborn.streamflix.fragments.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

// DEFINICIONES DE ESTADO Y RESULTADOS (Fuera de la clase para mejor acceso)
sealed class State {
    data object Searching : State()
    data object SearchingMore : State()
    data class SuccessSearching(val results: List<AppAdapter.Item>, val hasMore: Boolean) : State()
    data class FailedSearching(val error: Exception) : State()
    data object GlobalSearching : State()
    data class SuccessGlobalSearching(val providerResults: List<ProviderResult>) : State()
}

data class ProviderResult(
    val provider: Provider,
    val state: State,
) {
    sealed class State {
        data object Loading : State()
        data class Success(val results: List<AppAdapter.Item>) : State()
        data class Error(val error: Exception) : State()
    }
}


class SearchViewModel(database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Searching)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessSearching -> {
                    val movies = state.results
                        .filterIsInstance<Movie>()
                    database.movieDao().getByIds(movies.map { it.id })
                        .collect { emit(it) }
                }
                // Añadido para ser exhaustivo
                else -> emit(emptyList<Movie>())
            }
        },
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessSearching -> {
                    val tvShows = state.results
                        .filterIsInstance<TvShow>()
                    database.tvShowDao().getByIds(tvShows.map { it.id })
                        .collect { emit(it) }
                }
                // Añadido para ser exhaustivo
                else -> emit(emptyList<TvShow>())
            }
        },
    ) { state, moviesDb, tvShowsDb ->
        when (state) {
            is State.SuccessSearching -> {
                State.SuccessSearching(
                    results = state.results.map { item ->
                        when (item) {
                            is Movie -> moviesDb.find { it.id == item.id }
                                ?.takeIf { !item.isSame(it) }
                                ?.let { item.copy().merge(it) }
                                ?: item
                            is TvShow -> tvShowsDb.find { it.id == item.id }
                                ?.takeIf { !item.isSame(it) }
                                ?.let { item.copy().merge(it) }
                                ?: item
                            else -> item
                        }
                    },
                    hasMore = state.hasMore
                )
            }
            // Añadido para ser exhaustivo
            else -> state
        }
    }

    var query = ""
    private var page = 1

    init {
        search(query)
    }

    fun search(query: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Searching)

        try {
            val results = UserPreferences.currentProvider!!.search(query)
            this@SearchViewModel.query = query
            page = 1
            _state.emit(State.SuccessSearching(results, true))
        } catch (e: Exception) {
            Log.e("SearchViewModel", "search: ", e)
            _state.emit(State.FailedSearching(e))
        }
    }

    fun loadMore() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.first()
        if (currentState is State.SuccessSearching) {
            _state.emit(State.SearchingMore)
            try {
                val results = UserPreferences.currentProvider!!.search(query, page + 1)
                page += 1
                _state.emit(
                    State.SuccessSearching(
                        results = currentState.results + results,
                        hasMore = results.isNotEmpty(),
                    )
                )
            } catch (e: Exception) {
                Log.e("SearchViewModel", "loadMore: ", e)
                _state.emit(State.FailedSearching(e))
            }
        }
    }

    // FUNCIÓN DE BÚSQUEDA GLOBAL AÑADIDA
    fun searchGlobal(query: String, currentLanguage: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.GlobalSearching)

        val targetProviders = Provider.providers.keys
            .filter { it.language == currentLanguage }
            .toList()

        if (targetProviders.isEmpty()) {
            _state.emit(State.SuccessGlobalSearching(emptyList()))
            return@launch
        }

        val initialResults = targetProviders.map { provider ->
            ProviderResult(provider, ProviderResult.State.Loading)
        }
        _state.emit(State.SuccessGlobalSearching(initialResults))

        val mutableResults = initialResults.toMutableList()

        val stateComparator = compareBy<ProviderResult> { providerResult ->
            when (val state = providerResult.state) {
                is ProviderResult.State.Success -> if (state.results.isNotEmpty()) 1 else 3
                is ProviderResult.State.Loading -> 2
                is ProviderResult.State.Error -> 4
            }
        }

        targetProviders.forEachIndexed { index, provider ->
            launch {
                try {
                    val results = provider.search(query).onEach { item ->
                        // ========= ¡AQUÍ ESTÁ LA MAGIA! =========
                        // Le ponemos el sello a cada resultado
                        when (item) {
                            is Movie -> item.providerName = provider.name
                            is TvShow -> item.providerName = provider.name
                        }
                        // =======================================
                    }
                    mutableResults[index] = ProviderResult(provider, ProviderResult.State.Success(results))
                } catch (e: Exception) {
                    Log.e("SearchViewModel", "searchGlobal for ${provider.name}: ", e)
                    mutableResults[index] = ProviderResult(provider, ProviderResult.State.Error(e))
                }

                _state.emit(State.SuccessGlobalSearching(mutableResults.sortedWith(stateComparator)))
            }
        }
    }
}