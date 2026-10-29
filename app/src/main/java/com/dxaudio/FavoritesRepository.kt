package com.dxaudio

import android.content.Context
import android.net.Uri
import org.json.JSONArray

object FavoritesRepository {
    private const val PREFS_NAME = "dx_favorites"
    private const val KEY_FAVORITES = "favorites_list"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY = 50

    fun getFavorites(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FAVORITES, "[]")
        return parseUriList(json)
    }

    fun toggleFavorite(context: Context, uri: Uri): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getFavorites(context).toMutableList()
        val uriStr = uri.toString()
        
        val isNowFavorite: Boolean
        if (current.contains(uri)) {
            current.remove(uri)
            isNowFavorite = false
        } else {
            current.add(uri)
            isNowFavorite = true
        }
        
        val jsonArray = JSONArray()
        current.forEach { jsonArray.put(it.toString()) }
        prefs.edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
        return isNowFavorite
    }

    fun isFavorite(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        return getFavorites(context).contains(uri)
    }

    fun addToHistory(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getFavoritesHistory(context).toMutableList()
        
        // Удаляем дубликат, если трек уже есть в истории
        current.remove(uri)
        // Добавляем в начало
        current.add(0, uri)
        
        // Ограничиваем размер истории
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.size - 1)
        }

        val jsonArray = JSONArray()
        current.forEach { jsonArray.put(it.toString()) }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun getFavoritesHistory(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, "[]")
        return parseUriList(json)
    }

    private fun parseUriList(json: String?): List<Uri> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i -> Uri.parse(array.getString(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
