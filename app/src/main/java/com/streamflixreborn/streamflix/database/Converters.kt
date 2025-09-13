package com.streamflixreborn.streamflix.database

import androidx.room.TypeConverter
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.format
import com.streamflixreborn.streamflix.utils.toCalendar
import java.util.Calendar

class Converters {

    @TypeConverter
    fun fromCalendar(value: Calendar?): String? {
        return value?.format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    @TypeConverter
    fun toCalendar(value: String?): Calendar? {
        return value?.toCalendar()
    }


    @TypeConverter
    fun fromTvShow(value: TvShow?): String? {
        return value?.id
    }

    @TypeConverter
    fun toTvShow(value: String?): TvShow? {
        return value?.let { TvShow(it, "") }
    }


    @TypeConverter
    fun fromSeason(value: Season?): String? {
        return value?.id
    }

    @TypeConverter
    fun toSeason(value: String?): Season? {
        return value?.let { Season(it, 0) }
    }
}