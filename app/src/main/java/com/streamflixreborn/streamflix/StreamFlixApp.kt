package com.streamflixreborn.streamflix

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.providers.AniWorldProvider
import com.streamflixreborn.streamflix.providers.SerienStreamProvider
import com.streamflixreborn.streamflix.utils.EpisodeManager
import com.streamflixreborn.streamflix.utils.UserPreferences // <-- IMPORT AGGIUNTO

class StreamFlixApp : Application() {
    companion object {
        lateinit var instance: StreamFlixApp
            private set
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        UserPreferences.setup(this)

        SerienStreamProvider.initialize(this)
        AniWorldProvider.initialize(this)
    }
}
