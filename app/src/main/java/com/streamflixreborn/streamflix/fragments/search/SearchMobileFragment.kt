package com.streamflixreborn.streamflix.fragments.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.databinding.FragmentSearchMobileBinding
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.ui.SpacingItemDecoration
import com.streamflixreborn.streamflix.utils.CacheUtils
import com.streamflixreborn.streamflix.utils.UserPreferences // <-- IMPORT AÑADIDO
import com.streamflixreborn.streamflix.utils.VoiceRecognitionHelper
import com.streamflixreborn.streamflix.utils.dp
import com.streamflixreborn.streamflix.utils.hideKeyboard
import com.streamflixreborn.streamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch

class SearchMobileFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentSearchMobileBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { SearchViewModel(database) }

    private var appAdapter = AppAdapter()

    private lateinit var voiceHelper: VoiceRecognitionHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeSearch()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                // ========= BLOQUE WHEN MODIFICADO =========
                when (state) {
                    is State.Searching, is State.GlobalSearching -> {
                        binding.isLoading.apply {
                            root.visibility = View.VISIBLE
                            pbIsLoading.visibility = View.VISIBLE
                            gIsLoadingRetry.visibility = View.GONE
                        }
                        binding.rvSearch.adapter = AppAdapter().also {
                            appAdapter = it
                        }
                    }
                    is State.SearchingMore -> appAdapter.isLoading = true
                    is State.SuccessSearching -> {
                        displaySearch(state.results, state.hasMore)
                        appAdapter.isLoading = false
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is State.SuccessGlobalSearching -> {
                        displayGlobalSearch(state.providerResults)
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is State.FailedSearching -> {
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.streamflixreborn.streamflix.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.search(viewModel.query)
                            return@collect
                        }
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (appAdapter.isLoading) {
                            appAdapter.isLoading = false
                        } else {
                            binding.isLoading.apply {
                                pbIsLoading.visibility = View.GONE
                                gIsLoadingRetry.visibility = View.VISIBLE
                                val doRetry = { viewModel.search(viewModel.query) }
                                btnIsLoadingRetry.setOnClickListener { doRetry() }
                                btnIsLoadingClearCache.setOnClickListener {
                                    CacheUtils.clearAppCache(requireContext())
                                    android.widget.Toast.makeText(requireContext(), getString(com.streamflixreborn.streamflix.R.string.clear_cache_done), android.widget.Toast.LENGTH_SHORT).show()
                                    doRetry()
                                }
                            }
                        }
                    }
                }
                // ===========================================
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceHelper.stopRecognition()
        _binding = null
    }

    private fun initializeSearch() {
        binding.etSearch.apply {
            // ========= LÓGICA DE BÚSQUEDA MODIFICADA =========
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = binding.etSearch.text.toString()
                    hideKeyboard()

                    if (binding.swGlobalSearch.isChecked) {
                        val currentLanguage = UserPreferences.currentProvider?.language ?: "es"
                        viewModel.searchGlobal(query, currentLanguage)
                    } else {
                        viewModel.search(query)
                    }
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }
            // =================================================

            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if(s.isNullOrBlank()){
                        binding.etSearch.hint = getString(R.string.search_input_hint)
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        val blink = AlphaAnimation(1f, 0.3f).apply {
            duration = 500
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }

        voiceHelper = VoiceRecognitionHelper(
            fragment = this,
            onResult = { query ->
                binding.btnSearchVoice.clearAnimation()
                binding.etSearch.setText(query)
                viewModel.search(query)
            },
            onError = { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                binding.btnSearchVoice.clearAnimation()
                binding.etSearch.hint = getString(R.string.search_input_hint)
            },
            onListeningStateChanged = { isListening ->
                binding.btnSearchVoice.startAnimation(blink)
                binding.etSearch.hint = getString(R.string.voice_prompt)
            }
        )

        binding.btnSearchVoice.apply {
            requestFocus()
            visibility =
                if (voiceHelper.isAvailable()) View.VISIBLE else View.GONE

            setOnClickListener {
                if (!voiceHelper.isListening) {
                    voiceHelper.startWithPermissionCheck()
                }
            }
        }

        binding.btnSearchClear.setOnClickListener {
            binding.etSearch.setText("")
            binding.etSearch.hint = getString(R.string.search_input_hint)
            viewModel.search("")
        }

        binding.rvSearch.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(10.dp(requireContext()))
            )
        }
    }

    private fun displaySearch(list: List<AppAdapter.Item>, hasMore: Boolean) {
        appAdapter.submitList(list.onEach {
            when (it) {
                is Genre -> it.itemType = AppAdapter.Type.GENRE_GRID_MOBILE_ITEM
                is Movie -> it.itemType = AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM
                is TvShow -> it.itemType = AppAdapter.Type.TV_SHOW_GRID_MOBILE_ITEM
            }
        })

        if (hasMore && viewModel.query != "") {
            appAdapter.setOnLoadMoreListener { viewModel.loadMore() }
        } else {
            appAdapter.setOnLoadMoreListener(null)
        }
    }

    // ========= NUEVA FUNCIÓN PARA MOSTRAR RESULTADOS GLOBALES =========
    private fun displayGlobalSearch(providerResults: List<ProviderResult>) {
        val allItems = mutableListOf<AppAdapter.Item>()

        providerResults.forEach { providerResult ->
            val headerTitle = when (val state = providerResult.state) {
                is ProviderResult.State.Loading -> "${providerResult.provider.name} - Buscando..."
                is ProviderResult.State.Error -> "${providerResult.provider.name} - Error"
                is ProviderResult.State.Success -> {
                    val count = state.results.size
                    "${providerResult.provider.name} - $count ${if (count == 1) "resultado" else "resultados"}"
                }
            }

            val header = Category(
                name = headerTitle,
                list = emptyList()
            ).apply {
                itemType = AppAdapter.Type.CATEGORY_MOBILE_ITEM
            }
            allItems.add(header)

            if (providerResult.state is ProviderResult.State.Success) {
                val results = providerResult.state.results.onEach {
                    when (it) {
                        is Movie -> it.itemType = AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM
                        is TvShow -> it.itemType = AppAdapter.Type.TV_SHOW_GRID_MOBILE_ITEM
                    }
                }
                allItems.addAll(results)
            }
        }

        appAdapter.submitList(allItems)
        appAdapter.setOnLoadMoreListener(null) // Desactivamos la carga infinita en la búsqueda global
    }
    // ================================================================
}