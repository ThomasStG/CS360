package com.example.snar.common.helpers
import android.content.ContentProvider
import androidx.core.content.ContentProviderCompat.requireContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Context
import java.io.IOException

data class Building(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val description: String
)

fun loadJsonFromAssets(context: Context, fileName: String): String? {
    return try {
        val inputStream = context.assets.open(fileName)
        inputStream.bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}


fun loadBuildingData(context: Context): List<Building>? {
    return try {
        val json = loadJsonFromAssets(context, "building_info.json")
        val type = object : TypeToken<List<Building>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        null
    }
}


