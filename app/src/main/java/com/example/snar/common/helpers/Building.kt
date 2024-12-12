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
) //every building will have these properties

fun loadJsonFromAssets(context: Context, fileName: String): String? {
    return try {
        val inputStream = context.assets.open(fileName)
        inputStream.bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
} //opening fileName, then reading the text within. 


fun loadBuildingData(context: Context): List<Building>? {
    return try {
        val json = loadJsonFromAssets(context, "building_info.json")
        val type = object : TypeToken<List<Building>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        null
    }
} //loads the building data using the "building_info.json" file which contains 
// the descriptions for every building


