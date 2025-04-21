package com.example.facerecognize.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FaceConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromFloatArray(value: FloatArray): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        val listType = object : TypeToken<FloatArray>() {}.type
        return gson.fromJson(value, listType)
    }
} 