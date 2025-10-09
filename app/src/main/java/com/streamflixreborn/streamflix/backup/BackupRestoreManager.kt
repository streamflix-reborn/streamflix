package com.streamflixreborn.streamflix.backup

import android.content.Context
import android.util.Log
import com.streamflixreborn.streamflix.database.dao.EpisodeDao
import com.streamflixreborn.streamflix.database.dao.MovieDao
import com.streamflixreborn.streamflix.database.dao.TvShowDao
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.WatchItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ProviderBackupContext(
    val name: String,
    val movieDao: MovieDao,
    val tvShowDao: TvShowDao,
    val episodeDao: EpisodeDao
)

class BackupRestoreManager(
    private val context: Context,
    private val providers: List<ProviderBackupContext>
) {

    fun exportUserData(): String? {
        return try {
            val root = JSONObject()
            root.put("version", 3)
            root.put("exportedAt", System.currentTimeMillis())

            val providersArray = JSONArray()
            for (p in providers) {
                val providerObj = JSONObject()
                providerObj.put("name", p.name)

                val moviesArray = JSONArray()
                p.movieDao.getAll().forEach { movie ->
                    val obj = JSONObject().apply {
                        put("id", movie.id)
                        put("title", movie.title)
                        put("poster", movie.poster)
                        put("banner", movie.banner)
                        put("isFavorite", movie.isFavorite)
                        put("isWatched", movie.isWatched)
                        put("watchedDate", movie.watchedDate?.timeInMillis ?: JSONObject.NULL)
                        put("watchHistory", movie.watchHistory?.toJson() ?: JSONObject.NULL)
                    }
                    moviesArray.put(obj)
                }
                providerObj.put("movies", moviesArray)
                val tvShowsArray = JSONArray()
                p.tvShowDao.getAllForBackup().forEach { show ->
                    val obj = JSONObject().apply {
                        put("id", show.id)
                        put("title", show.title)
                        put("poster", show.poster)
                        put("banner", show.banner)
                        put("isFavorite", show.isFavorite)
                        put("isWatching", show.isWatching)
                    }
                    tvShowsArray.put(obj)
                }
                providerObj.put("tvShows", tvShowsArray)

                val episodesArray = JSONArray()
                p.episodeDao.getAllForBackup().forEach { ep ->
                    val obj = JSONObject().apply {
                        put("id", ep.id)
                        put("number", ep.number)
                        put("title", ep.title)
                        put("poster", ep.poster)
                        put("tvShowId", ep.tvShow?.id)
                        put("seasonId", ep.season?.id)
                        put("isWatched", ep.isWatched)
                        put("watchedDate", ep.watchedDate?.timeInMillis ?: JSONObject.NULL)
                        put("watchHistory", ep.watchHistory?.toJson() ?: JSONObject.NULL)
                    }
                    episodesArray.put(obj)
                }
                providerObj.put("episodes", episodesArray)

                providersArray.put(providerObj)
            }

            root.put("providers", providersArray)
            root.toString()
        } catch (t: Throwable) {
            Log.e("BackupRestore", "Error during exportUserData", t)
            null
        }
    }


    fun importUserData(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val providersArray = obj.optJSONArray("providers") ?: return false

            for (i in 0 until providersArray.length()) {
                val providerObj = providersArray.optJSONObject(i) ?: continue
                val providerName = providerObj.optString("name") ?: continue
                val providerCtx = providers.find { it.name == providerName } ?: continue
                providerObj.optJSONArray("movies")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val m = arr.optJSONObject(j) ?: continue
                        val movie = Movie(
                            id = m.optString("id", ""),
                            title = m.optString("title", "")
                        ).apply {
                            poster = m.optStringOrNull("poster")
                            banner = m.optStringOrNull("banner")
                            isFavorite = m.optBoolean("isFavorite", false)
                            isWatched = m.optBoolean("isWatched", false)
                            watchedDate = m.optLongOrNull("watchedDate")?.toCalendar()
                            watchHistory = m.optJSONObject("watchHistory")?.toWatchHistory()
                        }
                        providerCtx.movieDao.save(movie)
                    }
                }

                providerObj.optJSONArray("tvShows")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val s = arr.optJSONObject(j) ?: continue
                        val tvShow = TvShow(
                            id = s.optString("id", ""),
                            title = s.optString("title", "")
                        ).apply {
                            poster = s.optStringOrNull("poster")
                            banner = s.optStringOrNull("banner")
                            isFavorite = s.optBoolean("isFavorite", false)
                            isWatching = s.optBoolean("isWatching", true)
                        }
                        providerCtx.tvShowDao.save(tvShow)
                    }
                }

                providerObj.optJSONArray("episodes")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val e = arr.optJSONObject(j) ?: continue
                        val ep = Episode(id = e.optString("id", "")).apply {
                            number = e.optInt("number", 0)
                            title = e.optStringOrNull("title")
                            poster = e.optStringOrNull("poster")
                            e.optStringOrNull("tvShowId")?.let { tvId -> tvShow = TvShow(tvId, "") }
                            e.optStringOrNull("seasonId")?.let { sId -> season = Season(sId, 0) }
                            isWatched = e.optBoolean("isWatched", false)
                            watchedDate = e.optLongOrNull("watchedDate")?.toCalendar()
                            watchHistory = e.optJSONObject("watchHistory")?.toWatchHistory()
                        }
                        providerCtx.episodeDao.save(ep)
                    }
                }
            }

            true
        } catch (t: Throwable) {
            Log.e("BackupRestore", "Error during importUserData", t)
            false
        }
    }


}
private fun Long.toCalendar(): Calendar = Calendar.getInstance().apply { timeInMillis = this@toCalendar }

private fun WatchItem.WatchHistory.toJson(): JSONObject =
    JSONObject().apply {
        put("lastEngagementTimeUtcMillis", lastEngagementTimeUtcMillis)
        put("lastPlaybackPositionMillis", lastPlaybackPositionMillis)
        put("durationMillis", durationMillis)
    }

private fun JSONObject.toWatchHistory(): WatchItem.WatchHistory =
    WatchItem.WatchHistory(
        optLong("lastEngagementTimeUtcMillis", 0L),
        optLong("lastPlaybackPositionMillis", 0L),
        optLong("durationMillis", 0L)
    )


private fun JSONObject.optLongOrNull(name: String): Long? {
    return if (has(name) && !isNull(name)) optLong(name) else null
}

private fun JSONObject.optStringOrNull(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}

