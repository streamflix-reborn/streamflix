package com.tanasi.streamflix.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tanasi.streamflix.models.Movie
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {

    @Query("SELECT * FROM movies")
    fun getAll(): List<Movie> // NUOVO: Per l'esportazione

    @Query("SELECT * FROM movies WHERE id = :id")
    fun getById(id: String): Movie?

    @Query("SELECT * FROM movies WHERE id = :id")
    fun getByIdAsFlow(id: String): Flow<Movie?>

    @Query("SELECT * FROM movies WHERE id IN (:ids)")
    fun getByIds(ids: List<String>): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE isFavorite = 1")
    fun getFavorites(): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE lastEngagementTimeUtcMillis IS NOT NULL ORDER BY lastEngagementTimeUtcMillis DESC")
    fun getWatchingMovies(): Flow<List<Movie>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(movie: Movie)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(movies: List<Movie>) // NUOVO: Per l'importazione

    @Update
    fun update(movie: Movie)

    @Query("DELETE FROM movies")
    fun deleteAll() // NUOVO: Per l'importazione

    fun save(movie: Movie) = getById(movie.id)
        ?.let { update(movie.copy(id = it.id)) } // Assicurati che l'update usi l'ID corretto
        ?: insert(movie)
}
