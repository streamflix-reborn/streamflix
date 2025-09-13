package com.streamflixreborn.streamflix.models

import com.streamflixreborn.streamflix.adapters.AppAdapter

open class Provider(
    val name: String,
    val logo: String,
    val language: String,

    val provider: com.streamflixreborn.streamflix.providers.Provider,
) : AppAdapter.Item {


    override lateinit var itemType: AppAdapter.Type
}