package com.rentnest.app.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate): Long = d.toEpochDay()
    @TypeConverter fun longToDate(v: Long): LocalDate = LocalDate.ofEpochDay(v)
    @TypeConverter fun listToJson(l: List<String>): String = Json.encodeToString(l)
    @TypeConverter fun jsonToList(s: String): List<String> = Json.decodeFromString(s)
    @TypeConverter fun mapToJson(m: Map<String, String>): String = Json.encodeToString(m)
    @TypeConverter fun jsonToMap(s: String): Map<String, String> = Json.decodeFromString(s)
}
