package com.streamflixreborn.streamflix.models

import com.streamflixreborn.streamflix.adapters.AppAdapter

sealed interface Show : AppAdapter.Item {
    var isFavorite: Boolean
}
