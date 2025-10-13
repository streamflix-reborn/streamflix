package com.streamflixreborn.streamflix.adapters

import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.streamflixreborn.streamflix.adapters.viewholders.CategoryViewHolder
import com.streamflixreborn.streamflix.adapters.viewholders.EpisodeViewHolder
import com.streamflixreborn.streamflix.adapters.viewholders.GenreViewHolder
import com.streamflixreborn.streamflix.adapters.viewholders.MovieViewHolder
import com.streamflixreborn.streamflix.adapters.viewholders.PeopleViewHolder
import com.streamflixreborn.streamflix.adapters.viewholders.ProviderViewHolder
import com.streamflixreborn.streamflix.adapters.viewholders.SeasonViewHolder
import com.streamflixreborn.streamflix.adapters.viewholders.TvShowViewHolder
import com.streamflixreborn.streamflix.databinding.ContentCategorySwiperMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentCategorySwiperTvBinding
import com.streamflixreborn.streamflix.databinding.ContentMovieCastMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentMovieCastTvBinding
import com.streamflixreborn.streamflix.databinding.ContentMovieMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentMovieRecommendationsMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentMovieRecommendationsTvBinding
import com.streamflixreborn.streamflix.databinding.ContentMovieTvBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowCastMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowCastTvBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowRecommendationsMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowRecommendationsTvBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowSeasonsMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowSeasonsTvBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowTvBinding
import com.streamflixreborn.streamflix.databinding.ItemCategoryMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemCategorySwiperMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemCategoryTvBinding
import com.streamflixreborn.streamflix.databinding.ItemEpisodeContinueWatchingMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemEpisodeContinueWatchingTvBinding
import com.streamflixreborn.streamflix.databinding.ItemEpisodeMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemEpisodeTvBinding
import com.streamflixreborn.streamflix.databinding.ItemGenreGridMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemGenreGridTvBinding
import com.streamflixreborn.streamflix.databinding.ItemLoadingBinding
import com.streamflixreborn.streamflix.databinding.ItemMovieGridMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemMovieGridTvBinding
import com.streamflixreborn.streamflix.databinding.ItemMovieMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemMovieTvBinding
import com.streamflixreborn.streamflix.databinding.ItemPeopleMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemPeopleTvBinding
import com.streamflixreborn.streamflix.databinding.ItemProviderMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemProviderTvBinding
import com.streamflixreborn.streamflix.databinding.ItemSeasonMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemSeasonTvBinding
import com.streamflixreborn.streamflix.databinding.ItemTvShowGridBinding
import com.streamflixreborn.streamflix.databinding.ItemTvShowGridMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemTvShowMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemTvShowTvBinding
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Provider
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow

class AppAdapter(
    val items: MutableList<Item> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // --- LISTENERS AÑADIDOS AQUÍ ---
    var onMovieClickListener: ((Movie) -> Unit)? = null
    var onTvShowClickListener: ((TvShow) -> Unit)? = null
    var onMovieLongClickListener: ((Movie) -> Unit)? = null
    var onTvShowLongClickListener: ((TvShow) -> Unit)? = null
    var onGenreClickListener: ((Genre) -> Unit)? = null
    var onPeopleClickListener: ((People) -> Unit)? = null
    var onEpisodeClickListener: ((Episode) -> Unit)? = null
    var onSeasonClickListener: ((Season) -> Unit)? = null
    var onProviderClickListener: ((Provider) -> Unit)? = null
    // ---------------------------------
    interface Item {
        var itemType: Type
    }

    enum class Type {
        CATEGORY_MOBILE_ITEM,
        CATEGORY_TV_ITEM,

        CATEGORY_MOBILE_SWIPER,
        CATEGORY_TV_SWIPER,

        EPISODE_MOBILE_ITEM,
        EPISODE_TV_ITEM,
        EPISODE_CONTINUE_WATCHING_MOBILE_ITEM,
        EPISODE_CONTINUE_WATCHING_TV_ITEM,

        FOOTER,

        GENRE_GRID_MOBILE_ITEM,
        GENRE_GRID_TV_ITEM,

        HEADER,

        LOADING_ITEM,

        MOVIE_MOBILE_ITEM,
        MOVIE_TV_ITEM,
        MOVIE_CONTINUE_WATCHING_MOBILE_ITEM,
        MOVIE_CONTINUE_WATCHING_TV_ITEM,
        MOVIE_GRID_MOBILE_ITEM,
        MOVIE_GRID_TV_ITEM,
        MOVIE_SWIPER_MOBILE_ITEM,

        MOVIE_MOBILE,
        MOVIE_TV,
        MOVIE_CAST_MOBILE,
        MOVIE_CAST_TV,
        MOVIE_RECOMMENDATIONS_MOBILE,
        MOVIE_RECOMMENDATIONS_TV,

        PEOPLE_MOBILE_ITEM,
        PEOPLE_TV_ITEM,

        PROVIDER_MOBILE_ITEM,
        PROVIDER_TV_ITEM,

        SEASON_MOBILE_ITEM,
        SEASON_TV_ITEM,

        TV_SHOW_MOBILE_ITEM,
        TV_SHOW_TV_ITEM,
        TV_SHOW_GRID_MOBILE_ITEM,
        TV_SHOW_GRID_TV_ITEM,
        TV_SHOW_SWIPER_MOBILE_ITEM,

        TV_SHOW_MOBILE,
        TV_SHOW_TV,
        TV_SHOW_SEASONS_MOBILE,
        TV_SHOW_SEASONS_TV,
        TV_SHOW_CAST_MOBILE,
        TV_SHOW_CAST_TV,
        TV_SHOW_RECOMMENDATIONS_MOBILE,
        TV_SHOW_RECOMMENDATIONS_TV,
    }

    private val states = mutableMapOf<Int, Parcelable?>()

    var isLoading = false
    private var header: Header<ViewBinding>? = null
    private var onLoadMoreListener: (() -> Unit)? = null
    private var footer: Footer<ViewBinding>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (Type.entries[viewType]) {
            Type.CATEGORY_MOBILE_ITEM -> CategoryViewHolder(
                ItemCategoryMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.CATEGORY_TV_ITEM -> CategoryViewHolder(
                ItemCategoryTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.CATEGORY_MOBILE_SWIPER -> CategoryViewHolder(
                ContentCategorySwiperMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.CATEGORY_TV_SWIPER -> CategoryViewHolder(
                ContentCategorySwiperTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.EPISODE_MOBILE_ITEM -> EpisodeViewHolder(
                ItemEpisodeMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.EPISODE_TV_ITEM -> EpisodeViewHolder(
                ItemEpisodeTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.EPISODE_CONTINUE_WATCHING_MOBILE_ITEM -> EpisodeViewHolder(
                ItemEpisodeContinueWatchingMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.EPISODE_CONTINUE_WATCHING_TV_ITEM -> EpisodeViewHolder(
                ItemEpisodeContinueWatchingTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.FOOTER -> FooterViewHolder(
                footer!!.binding(parent)
            )

            Type.GENRE_GRID_MOBILE_ITEM -> GenreViewHolder(
                ItemGenreGridMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.GENRE_GRID_TV_ITEM -> GenreViewHolder(
                ItemGenreGridTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.HEADER -> HeaderViewHolder(
                header!!.binding(parent)
            )

            Type.LOADING_ITEM -> LoadingViewHolder(
                ItemLoadingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.MOVIE_CONTINUE_WATCHING_MOBILE_ITEM,
            Type.MOVIE_MOBILE_ITEM -> MovieViewHolder(
                ItemMovieMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_CONTINUE_WATCHING_TV_ITEM,
            Type.MOVIE_TV_ITEM -> MovieViewHolder(
                ItemMovieTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_GRID_MOBILE_ITEM -> MovieViewHolder(
                ItemMovieGridMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_GRID_TV_ITEM -> MovieViewHolder(
                ItemMovieGridTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_SWIPER_MOBILE_ITEM -> MovieViewHolder(
                ItemCategorySwiperMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.MOVIE_MOBILE -> MovieViewHolder(
                ContentMovieMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_TV -> MovieViewHolder(
                ContentMovieTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_CAST_MOBILE -> MovieViewHolder(
                ContentMovieCastMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_CAST_TV -> MovieViewHolder(
                ContentMovieCastTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_RECOMMENDATIONS_MOBILE -> MovieViewHolder(
                ContentMovieRecommendationsMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.MOVIE_RECOMMENDATIONS_TV -> MovieViewHolder(
                ContentMovieRecommendationsTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.PEOPLE_MOBILE_ITEM -> PeopleViewHolder(
                ItemPeopleMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.PEOPLE_TV_ITEM -> PeopleViewHolder(
                ItemPeopleTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.PROVIDER_MOBILE_ITEM -> ProviderViewHolder(
                ItemProviderMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.PROVIDER_TV_ITEM -> ProviderViewHolder(
                ItemProviderTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.SEASON_MOBILE_ITEM -> SeasonViewHolder(
                ItemSeasonMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.SEASON_TV_ITEM -> SeasonViewHolder(
                ItemSeasonTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.TV_SHOW_MOBILE_ITEM -> TvShowViewHolder(
                ItemTvShowMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            Type.TV_SHOW_TV_ITEM -> TvShowViewHolder(
                ItemTvShowTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            Type.TV_SHOW_GRID_MOBILE_ITEM -> TvShowViewHolder(
                ItemTvShowGridMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            Type.TV_SHOW_GRID_TV_ITEM -> TvShowViewHolder(
                ItemTvShowGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            Type.TV_SHOW_SWIPER_MOBILE_ITEM -> TvShowViewHolder(
                ItemCategorySwiperMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

            Type.TV_SHOW_MOBILE -> TvShowViewHolder(
                ContentTvShowMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.TV_SHOW_TV -> TvShowViewHolder(
                ContentTvShowTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.TV_SHOW_SEASONS_MOBILE -> TvShowViewHolder(
                ContentTvShowSeasonsMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.TV_SHOW_SEASONS_TV -> TvShowViewHolder(
                ContentTvShowSeasonsTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.TV_SHOW_CAST_MOBILE -> TvShowViewHolder(
                ContentTvShowCastMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.TV_SHOW_CAST_TV -> TvShowViewHolder(
                ContentTvShowCastTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.TV_SHOW_RECOMMENDATIONS_MOBILE -> TvShowViewHolder(
                ContentTvShowRecommendationsMobileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            Type.TV_SHOW_RECOMMENDATIONS_TV -> TvShowViewHolder(
                ContentTvShowRecommendationsTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position >= itemCount - 5 && !isLoading) {
            onLoadMoreListener?.invoke()
            isLoading = true
        }

        val adjustedPosition = header?.let { position - 1 } ?: position
        when (holder) {
            is CategoryViewHolder -> holder.bind(
                items[adjustedPosition] as Category,
                onMovieClickListener,
                onTvShowClickListener,
            )
            is EpisodeViewHolder -> holder.bind(
                items[adjustedPosition] as Episode
            ) // Tu original no pasaba listener, lo respeto
            is FooterViewHolder -> footer?.bind?.invoke(holder.binding)
            is GenreViewHolder -> holder.bind(
                items[adjustedPosition] as Genre
            ) // Tu original no pasaba listener, lo respeto
            is HeaderViewHolder -> header?.bind?.invoke(holder.binding)
            is MovieViewHolder -> holder.bind(
                items[adjustedPosition] as Movie
            ) // Los listeners se manejan dentro del ViewHolder
            is PeopleViewHolder -> holder.bind(
                items[adjustedPosition] as People
            ) // Tu original no pasaba listener, lo respeto
            is ProviderViewHolder -> holder.bind(
                items[adjustedPosition] as Provider
            ) // Tu original no pasaba listener, lo respeto
            is SeasonViewHolder -> holder.bind(
                items[adjustedPosition] as Season
            ) // Tu original no pasaba listener, lo respeto
            is TvShowViewHolder -> holder.bind(
                items[adjustedPosition] as TvShow
            ) // Los listeners se manejan dentro del ViewHolder
        }

        val state = states[holder.layoutPosition]
        if (state != null) {
            when (holder) {
                is CategoryViewHolder -> holder.childRecyclerView?.layoutManager?.onRestoreInstanceState(state)
                is MovieViewHolder -> holder.childRecyclerView?.layoutManager?.onRestoreInstanceState(state)
                is TvShowViewHolder -> holder.childRecyclerView?.layoutManager?.onRestoreInstanceState(state)
            }
        }
    }

    override fun getItemCount(): Int = items.size +
            (header?.let { 1 } ?: 0) +
            (onLoadMoreListener?.let { 1 } ?: 0) +
            (footer?.let { 1 } ?: 0)

    override fun getItemViewType(position: Int): Int {
        if (header != null && position == 0) {
            return Type.HEADER.ordinal
        }

        val adjustedPosition = header?.let { position - 1 } ?: position
        if (adjustedPosition in items.indices) {
            return items[adjustedPosition].itemType.ordinal
        }

        val loadMorePosition = itemCount - 1 - (if (footer != null) 1 else 0)
        if (onLoadMoreListener != null && position == loadMorePosition) {
            return Type.LOADING_ITEM.ordinal
        }

        if (footer != null && position == itemCount - 1) {
            return Type.FOOTER.ordinal
        }

        return Type.LOADING_ITEM.ordinal
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        states[holder.layoutPosition] = when (holder) {
            is CategoryViewHolder -> holder.childRecyclerView?.layoutManager?.onSaveInstanceState()
            is MovieViewHolder -> holder.childRecyclerView?.layoutManager?.onSaveInstanceState()
            is TvShowViewHolder -> holder.childRecyclerView?.layoutManager?.onSaveInstanceState()
            else -> null
        }
    }

    fun onSaveInstanceState(recyclerView: RecyclerView) {
        for (position in items.indices) {
            val holder = recyclerView.findViewHolderForAdapterPosition(position) ?: continue

            states[position] = when (holder) {
                is CategoryViewHolder -> holder.childRecyclerView?.layoutManager?.onSaveInstanceState()
                is MovieViewHolder -> holder.childRecyclerView?.layoutManager?.onSaveInstanceState()
                is TvShowViewHolder -> holder.childRecyclerView?.layoutManager?.onSaveInstanceState()
                else -> null
            }
        }
    }


    fun submitList(list: List<Item>) {
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size

            override fun getNewListSize() = list.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = items[oldItemPosition]
                val newItem = list[newItemPosition]
                return when {
                    oldItem is Category && newItem is Category -> oldItem.name == newItem.name
                    oldItem is Episode && newItem is Episode -> oldItem.id == newItem.id
                    oldItem is Genre && newItem is Genre -> oldItem.id == newItem.id
                    oldItem is Movie && newItem is Movie -> oldItem.id == newItem.id
                    oldItem is People && newItem is People -> oldItem.id == newItem.id
                    oldItem is Provider && newItem is Provider -> oldItem.name == newItem.name
                    oldItem is Season && newItem is Season -> oldItem.id == newItem.id
                    oldItem is TvShow && newItem is TvShow -> oldItem.id == newItem.id
                    else -> false
                } && oldItem.itemType.ordinal == newItem.itemType.ordinal
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = items[oldItemPosition]
                val newItem = list[newItemPosition]
                return oldItem == newItem
            }
        })

        if (items.size < list.size) {
            for (newItemPosition in list.indices.reversed()) {
                val oldItemPosition = result.convertNewPositionToOld(newItemPosition)
                    .takeIf { it != -1 } ?: continue

                states[newItemPosition] = states[oldItemPosition]
            }
        } else if (items.size > list.size) {
            for (oldItemPosition in items.indices) {
                val newItemPosition = result.convertOldPositionToNew(oldItemPosition)
                    .takeIf { it != -1 } ?: continue

                states[newItemPosition] = states[oldItemPosition]
            }
        }

        items.clear()
        items.addAll(list)
        result.dispatchUpdatesTo(this)
    }


    fun <T : ViewBinding> setHeader(
        binding: (parent: ViewGroup) -> T,
        bind: ((binding: T) -> Unit)? = null,
    ) {
        @Suppress("UNCHECKED_CAST")
        this.header = Header(
            binding = binding,
            bind = bind as ((ViewBinding) -> Unit)?,
        )
    }

    fun setOnLoadMoreListener(onLoadMoreListener: (() -> Unit)?) {
        if (this.onLoadMoreListener != null && onLoadMoreListener == null) {
            this.onLoadMoreListener = null
            notifyItemRemoved(items.size)
        } else {
            this.onLoadMoreListener = onLoadMoreListener
        }
    }

    fun <T : ViewBinding> setFooter(
        binding: (parent: ViewGroup) -> T,
        bind: ((binding: T) -> Unit)? = null,
    ) {
        @Suppress("UNCHECKED_CAST")
        this.footer = Footer(
            binding = binding,
            bind = bind as ((ViewBinding) -> Unit)?,
        )
    }


    private class HeaderViewHolder(
        val binding: ViewBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    private data class Header<T : ViewBinding>(
        val binding: (parent: ViewGroup) -> T,
        val bind: ((binding: T) -> Unit)? = null,
    )

    private class LoadingViewHolder(
        binding: ViewBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    private class FooterViewHolder(
        val binding: ViewBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    private data class Footer<T : ViewBinding>(
        val binding: (parent: ViewGroup) -> T,
        val bind: ((binding: T) -> Unit)? = null,
    )
}