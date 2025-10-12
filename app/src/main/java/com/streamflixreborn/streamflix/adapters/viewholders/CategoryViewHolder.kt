package com.streamflixreborn.streamflix.adapters.viewholders

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.postDelayed
import androidx.core.view.children
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.databinding.ContentCategorySwiperMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentCategorySwiperTvBinding
import com.streamflixreborn.streamflix.databinding.ItemCategoryMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemCategoryTvBinding
import com.streamflixreborn.streamflix.fragments.home.HomeTvFragment
import com.streamflixreborn.streamflix.fragments.home.HomeTvFragmentDirections
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.ui.SpacingItemDecoration
import com.streamflixreborn.streamflix.utils.format
import com.streamflixreborn.streamflix.utils.getCurrentFragment
import com.streamflixreborn.streamflix.utils.toActivity
import java.util.Locale
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.providers.Provider

class CategoryViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private val context = itemView.context
    private lateinit var category: Category

    val childRecyclerView: RecyclerView?
        get() = when (_binding) {
            is ItemCategoryMobileBinding -> _binding.rvCategory
            is ItemCategoryTvBinding -> _binding.hgvCategory
            is ContentCategorySwiperMobileBinding -> _binding.vpCategorySwiper.javaClass
                .getDeclaredField("mRecyclerView").let {
                    it.isAccessible = true
                    it.get(_binding.vpCategorySwiper) as RecyclerView
                }
            else -> null
        }

    fun bind(
        category: Category,
        onMovieClick: ((Movie) -> Unit)? = null,
        onTvShowClick: ((TvShow) -> Unit)? = null
    ) {
        this.category = category

        when (_binding) {
            is ItemCategoryMobileBinding -> displayMobileItem(_binding, onMovieClick, onTvShowClick)
            is ItemCategoryTvBinding -> displayTvItem(_binding, onMovieClick, onTvShowClick)
            is ContentCategorySwiperMobileBinding -> displayMobileSwiper(_binding, onMovieClick, onTvShowClick)
            is ContentCategorySwiperTvBinding -> displayTvSwiper(_binding)
        }
    }

    private fun displayMobileItem(
        binding: ItemCategoryMobileBinding,
        onMovieClick: ((Movie) -> Unit)?,
        onTvShowClick: ((TvShow) -> Unit)?
    ) {
        binding.tvCategoryTitle.text = category.name

        binding.rvCategory.apply {
            adapter = AppAdapter().apply {
                // AQUÍ ESTÁ LA MAGIA: Pasamos los listeners al adaptador interno
                this.onMovieClickListener = onMovieClick
                this.onTvShowClickListener = onTvShowClick
                submitList(category.list)
            }
            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration(category.itemSpacing))
            }
        }
    }

    private fun displayTvItem(
        binding: ItemCategoryTvBinding,
        onMovieClick: ((Movie) -> Unit)?,
        onTvShowClick: ((TvShow) -> Unit)?
    ) {
        binding.tvCategoryTitle.text = category.name

        binding.hgvCategory.apply {
            setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
            adapter = AppAdapter().apply {
                // AQUÍ ESTÁ LA MAGIA: Pasamos los listeners al adaptador interno
                this.onMovieClickListener = onMovieClick
                this.onTvShowClickListener = onTvShowClick
                submitList(category.list)
            }
            setItemSpacing(category.itemSpacing)
        }
    }


    private fun displayMobileSwiper(
        binding: ContentCategorySwiperMobileBinding,
        onMovieClick: ((Movie) -> Unit)?,
        onTvShowClick: ((TvShow) -> Unit)?
    ) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(8_000) {
            binding.vpCategorySwiper.currentItem += 1
        }

        val items = listOf(
            listOfNotNull(category.list.lastOrNull()),
            category.list,
            listOfNotNull(category.list.firstOrNull()),
        ).flatten()
        binding.vpCategorySwiper.apply {
            adapter = AppAdapter().apply {
                // AQUÍ ESTÁ LA MAGIA: Pasamos los listeners al adaptador interno
                this.onMovieClickListener = onMovieClick
                this.onTvShowClickListener = onTvShowClick
                submitList(category.list)
                post { (adapter as AppAdapter).submitList(items) }
            }
        }

        binding.llDotsIndicator.apply {
            removeAllViews()
            repeat(category.list.size) {
                val view = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(15, 15).apply {
                        setMargins(10, 0, 10, 0)
                    }
                    setBackgroundResource(R.drawable.bg_dot_indicator)
                }
                addView(view)
            }
        }

        binding.vpCategorySwiper.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val indicatorPosition = when (position) {
                    0 -> category.list.lastIndex
                    items.lastIndex -> 0
                    else -> position - 1
                }
                binding.llDotsIndicator.children.forEachIndexed { index, view ->
                    view.isSelected = (indicatorPosition == index)
                }
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed(8_000) {
                    binding.vpCategorySwiper.currentItem += 1
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    when (binding.vpCategorySwiper.currentItem) {
                        0 -> binding.vpCategorySwiper.setCurrentItem(
                            items.lastIndex - 1,
                            false
                        )
                        items.lastIndex -> binding.vpCategorySwiper.setCurrentItem(
                            1,
                            false
                        )
                    }
                }
            }
        })
    }

    private fun displayTvSwiper(binding: ContentCategorySwiperTvBinding) {
        val selected = category.list.getOrNull(category.selectedIndex) as? Show ?: return

        fun checkProviderAndRun(show: Show, action: () -> Unit) {
            val providerName = when(show){
                is Movie -> show.providerName
                is TvShow -> show.providerName
                else -> null
            }

            if (!providerName.isNullOrBlank() && providerName != UserPreferences.currentProvider?.name) {
                Provider.providers.keys.find { it.name == providerName }?.let {
                    UserPreferences.currentProvider = it
                }
            }
            action()
        }
        when (val fragment = context.toActivity()?.getCurrentFragment()) {
            is HomeTvFragment -> when (selected) {
                is Movie -> fragment.updateBackground(selected.banner, null)
                is TvShow -> fragment.updateBackground(selected.banner, null)
            }
        }

        binding.tvSwiperTitle.text = when (selected) {
            is Movie -> selected.title
            is TvShow -> selected.title
        }

        binding.tvSwiperTvShowLastEpisode.apply {
            text = when (selected) {
                is TvShow -> selected.seasons.lastOrNull()?.let { season ->
                    season.episodes.lastOrNull()?.let { episode ->
                        if (season.number != 0) {
                            context.getString(
                                R.string.tv_show_item_season_number_episode_number,
                                season.number,
                                episode.number
                            )
                        } else {
                            context.getString(
                                R.string.tv_show_item_episode_number,
                                episode.number
                            )
                        }
                    }
                } ?: context.getString(R.string.tv_show_item_type)
                else -> context.getString(R.string.movie_item_type)
            }
        }

        binding.tvSwiperQuality.apply {
            text = when (selected) {
                is Movie -> selected.quality
                is TvShow -> selected.quality
            }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvSwiperReleased.apply {
            text = when (selected) {
                is Movie -> selected.released?.format("yyyy")
                is TvShow -> selected.released?.format("yyyy")
            }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvSwiperRating.apply {
            text = when (selected) {
                is Movie -> selected.rating?.let { String.format(Locale.ROOT, "%.1f", it) }
                is TvShow -> selected.rating?.let { String.format(Locale.ROOT, "%.1f", it) }
            }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.ivSwiperRatingIcon.visibility = binding.tvSwiperRating.visibility

        binding.tvSwiperOverview.text = when (selected) {
            is Movie -> selected.overview
            is TvShow -> selected.overview
        }


        binding.btnSwiperWatchNow.apply {
            // ... (onFocusChangeListener y onKeyListener sin cambios)
            setOnClickListener {
                checkProviderAndRun(selected) { // <-- LÓGICA APLICADA
                    findNavController().navigate(
                        when (selected) {
                            is Movie -> HomeTvFragmentDirections.actionHomeToMovie(selected.id)
                            is TvShow -> HomeTvFragmentDirections.actionHomeToTvShow(selected.id)
                        }
                    )
                }
            }
            setOnKeyListener { _, _, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            when (val fragment = context.toActivity()?.getCurrentFragment()) {
                                is HomeTvFragment -> fragment.resetSwiperSchedule()
                            }
                            category.selectedIndex = (category.selectedIndex + 1) % category.list.size
                            when (val fragment = context.toActivity()?.getCurrentFragment()) {
                                is HomeTvFragment -> when (val it = category.list[category.selectedIndex]) {
                                    is Movie -> fragment.updateBackground(it.banner, true)
                                    is TvShow -> fragment.updateBackground(it.banner, true)
                                }
                            }
                            bindingAdapter?.notifyItemChanged(bindingAdapterPosition)
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }
            setOnClickListener {
                findNavController().navigate(
                    when (selected) {
                        is Movie -> HomeTvFragmentDirections.actionHomeToMovie(selected.id)
                        is TvShow -> HomeTvFragmentDirections.actionHomeToTvShow(selected.id)
                    }
                )
            }
        }

        binding.pbSwiperProgress.apply {
            val watchHistory = when (selected) {
                is Movie -> selected.watchHistory
                is TvShow -> null
            }

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.llDotsIndicator.apply {
            removeAllViews()
            repeat(category.list.size) { index ->
                val view = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(15, 15).apply {
                        setMargins(10, 0, 10, 0)
                    }
                    setBackgroundResource(R.drawable.bg_dot_indicator)
                    isSelected = (category.selectedIndex == index)
                }
                addView(view)
            }
        }
    }
}