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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class BackupRestoreManager(
    private val context: Context,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val episodeDao: EpisodeDao,
) {

    fun exportUserData(): String? {
        return try {
            val root = JSONObject()
            root.put("version", 2) // bump versione schema
            root.put("exportedAt", System.currentTimeMillis())

            // Movies: includo metadati minimi per UI post-ripristino
            val moviesArray = JSONArray()
            movieDao.getAll().forEach { movie ->
                val obj = JSONObject()
                obj.put("id", movie.id)
                obj.put("title", movie.title)
                obj.put("poster", movie.poster)
                obj.put("banner", movie.banner)
                obj.put("isFavorite", movie.isFavorite)
                obj.put("isWatched", movie.isWatched)
                obj.put("watchedDate", movie.watchedDate?.timeInMillis ?: JSONObject.NULL)
                movie.watchHistory?.let { wh ->
                    val whObj = JSONObject()
                    whObj.put("lastEngagementTimeUtcMillis", wh.lastEngagementTimeUtcMillis)
                    whObj.put("lastPlaybackPositionMillis", wh.lastPlaybackPositionMillis)
                    whObj.put("durationMillis", wh.durationMillis)
                    obj.put("watchHistory", whObj)
                } ?: obj.put("watchHistory", JSONObject.NULL)
                moviesArray.put(obj)
            }
            root.put("movies", moviesArray)

            // TvShows: includo titoli/poster/banner per UI
            val tvShowsArray = JSONArray()
            tvShowDao.getAllForBackup().forEach { show ->
                val obj = JSONObject()
                obj.put("id", show.id)
                obj.put("title", show.title)
                obj.put("poster", show.poster)
                obj.put("banner", show.banner)
                obj.put("isFavorite", show.isFavorite)
                obj.put("isWatching", show.isWatching)
                tvShowsArray.put(obj)
            }
            root.put("tvShows", tvShowsArray)

            // Episodes: includo id relazionali e titolo per evitare e0/immagini nulle
            val episodesArray = JSONArray()
            episodeDao.getAllForBackup().forEach { ep ->
                val obj = JSONObject()
                obj.put("id", ep.id)
                obj.put("number", ep.number)
                obj.put("title", ep.title)
                obj.put("poster", ep.poster)
                obj.put("tvShowId", ep.tvShow?.id)
                obj.put("seasonId", ep.season?.id)
                obj.put("isWatched", ep.isWatched)
                obj.put("watchedDate", ep.watchedDate?.timeInMillis ?: JSONObject.NULL)
                ep.watchHistory?.let { wh ->
                    val whObj = JSONObject()
                    whObj.put("lastEngagementTimeUtcMillis", wh.lastEngagementTimeUtcMillis)
                    whObj.put("lastPlaybackPositionMillis", wh.lastPlaybackPositionMillis)
                    whObj.put("durationMillis", wh.durationMillis)
                    obj.put("watchHistory", whObj)
                } ?: obj.put("watchHistory", JSONObject.NULL)
                episodesArray.put(obj)
            }
            root.put("episodes", episodesArray)

            root.toString()
        } catch (t: Throwable) {
            Log.e("BackupRestore", "Errore durante exportUserData", t)
            null
        }
    }

    fun importUserData(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val version = obj.optInt("version", 1)
            obj.optLong("exportedAt", 0L)

            // Movies
            obj.optJSONArray("movies")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("id", null) ?: continue
                    val movie = Movie(id = id, title = m.optString("title", ""))
                    movie.poster = m.optStringOrNull("poster")
                    movie.banner = m.optStringOrNull("banner")
                    movie.isFavorite = m.optBoolean("isFavorite", false)
                    movie.isWatched = m.optBoolean("isWatched", false)
                    m.optLongOrNull("watchedDate")?.let { ts ->
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = ts
                        movie.watchedDate = cal
                    }
                    m.optJSONObject("watchHistory")?.let { wh ->
                        val lastE = wh.optLong("lastEngagementTimeUtcMillis", 0L)
                        val lastP = wh.optLong("lastPlaybackPositionMillis", 0L)
                        val dur = wh.optLong("durationMillis", 0L)
                        movie.watchHistory = com.streamflixreborn.streamflix.models.WatchItem.WatchHistory(lastE, lastP, dur)
                    }
                    movieDao.save(movie)
                }
            }

            // TvShows
            obj.optJSONArray("tvShows")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val id = s.optString("id", null) ?: continue
                    val tvShow = TvShow(id = id, title = s.optString("title", ""))
                    tvShow.poster = s.optStringOrNull("poster")
                    tvShow.banner = s.optStringOrNull("banner")
                    tvShow.isFavorite = s.optBoolean("isFavorite", false)
                    tvShow.isWatching = s.optBoolean("isWatching", true)
                    tvShowDao.save(tvShow)
                }
            }

            // Episodes
            obj.optJSONArray("episodes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val id = e.optString("id", null) ?: continue
                    val ep = Episode(id = id)
                    ep.number = e.optInt("number", 0)
                    ep.title = e.optStringOrNull("title")
                    ep.poster = e.optStringOrNull("poster")
                    e.optStringOrNull("tvShowId")?.let { tvId -> ep.tvShow = TvShow(tvId, "") }
                    e.optStringOrNull("seasonId")?.let { sId -> ep.season = Season(sId, 0) }
                    ep.isWatched = e.optBoolean("isWatched", false)
                    e.optLongOrNull("watchedDate")?.let { ts ->
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = ts
                        ep.watchedDate = cal
                    }
                    e.optJSONObject("watchHistory")?.let { wh ->
                        val lastE = wh.optLong("lastEngagementTimeUtcMillis", 0L)
                        val lastP = wh.optLong("lastPlaybackPositionMillis", 0L)
                        val dur = wh.optLong("durationMillis", 0L)
                        ep.watchHistory = com.streamflixreborn.streamflix.models.WatchItem.WatchHistory(lastE, lastP, dur)
                    }
                    episodeDao.save(ep)
                }
            }

            true
        } catch (t: Throwable) {
            Log.e("BackupRestore", "Errore durante importUserData", t)
            false
        }
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    return if (has(name) && !isNull(name)) optLong(name) else null
}

private fun JSONObject.optStringOrNull(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}
