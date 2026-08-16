package com.soniclab.playlist

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks marked as favorites, persisted in SharedPreferences.
 */
class FavoritesRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("soniclab_favorites", Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    init {
        _favorites.value = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggle(id: Long) {
        val current = _favorites.value.toMutableSet()
        if (!current.add(id)) {
            current.remove(id)
        }
        _favorites.value = current
        prefs.edit().putStringSet("ids", current.map { it.toString() }.toSet()).apply()
    }

    fun isFavorite(id: Long): Boolean = id in _favorites.value
}
