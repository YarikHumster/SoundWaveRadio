package com.yaros.RadioUrl.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yaros.RadioUrl.core.APIInterface.data.Category
import com.yaros.RadioUrl.data.Station
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Менеджер кеширования для радиостанций и категорий
 */
object CacheManager {

    private const val TAG = "CacheManager"

    // Ключи для SharedPreferences
    private const val PREF_CACHE_ENABLED = "cache_enabled"
    private const val PREF_CACHE_MAX_SIZE = "cache_max_size_mb"
    private const val PREF_STATIONS_CACHE = "stations_cache"
    private const val PREF_STATIONS_CACHE_TIME = "stations_cache_time"
    private const val PREF_CATEGORIES_CACHE = "categories_cache"
    private const val PREF_CATEGORIES_CACHE_TIME = "categories_cache_time"
    private const val PREF_CATEGORY_STATIONS_PREFIX = "category_stations_"
    private const val PREF_CATEGORY_STATIONS_TIME_PREFIX = "category_stations_time_"

    // Время жизни кеша (по умолчанию 1 час)
    private const val CACHE_EXPIRATION_TIME = 60 * 60 * 1000L // 1 час в миллисекундах

    // Размеры кеша в МБ
    const val CACHE_SIZE_SMALL = 10
    const val CACHE_SIZE_MEDIUM = 25
    const val CACHE_SIZE_LARGE = 50
    const val CACHE_SIZE_XLARGE = 100

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * Проверка, включено ли кеширование
     */
    fun isCachingEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_CACHE_ENABLED, true)
    }

    /**
     * Включить кеширование
     */
    fun enableCaching() {
        sharedPreferences.edit().putBoolean(PREF_CACHE_ENABLED, true).apply()
    }

    /**
     * Отключить кеширование
     */
    fun disableCaching() {
        sharedPreferences.edit().putBoolean(PREF_CACHE_ENABLED, false).apply()
    }

    /**
     * Получить максимальный размер кеша в МБ
     */
    fun getMaxCacheSize(): Int {
        return sharedPreferences.getInt(PREF_CACHE_MAX_SIZE, CACHE_SIZE_MEDIUM)
    }

    /**
     * Установить максимальный размер кеша в МБ
     */
    fun setMaxCacheSize(sizeMb: Int) {
        sharedPreferences.edit().putInt(PREF_CACHE_MAX_SIZE, sizeMb).apply()
    }

    /**
     * Сохранить список всех радиостанций в кеш
     */
    fun cacheStations(stations: List<Station>) {
        if (!isCachingEnabled()) return

        try {
            val json = gson.toJson(stations)
            val currentTime = System.currentTimeMillis()

            sharedPreferences.edit()
                .putString(PREF_STATIONS_CACHE, json)
                .putLong(PREF_STATIONS_CACHE_TIME, currentTime)
                .apply()

            Timber.tag(TAG).d("Cached ${stations.size} stations")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error caching stations")
        }
    }

    /**
     * Получить список всех радиостанций из кеша
     */
    fun getCachedStations(): List<Station>? {
        if (!isCachingEnabled()) return null

        try {
            val cacheTime = sharedPreferences.getLong(PREF_STATIONS_CACHE_TIME, 0)
            val currentTime = System.currentTimeMillis()

            // Проверяем, не истек ли срок действия кеша
            if (currentTime - cacheTime > CACHE_EXPIRATION_TIME) {
                Timber.tag(TAG).d("Stations cache expired")
                return null
            }

            val json = sharedPreferences.getString(PREF_STATIONS_CACHE, null) ?: return null
            val type = object : TypeToken<List<Station>>() {}.type
            val stations: List<Station> = gson.fromJson(json, type)

            Timber.tag(TAG).d("Retrieved ${stations.size} stations from cache")
            return stations
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error retrieving cached stations")
            return null
        }
    }

    /**
     * Сохранить список категорий в кеш
     */
    fun cacheCategories(categories: List<Category>) {
        if (!isCachingEnabled()) return

        try {
            val json = gson.toJson(categories)
            val currentTime = System.currentTimeMillis()

            sharedPreferences.edit()
                .putString(PREF_CATEGORIES_CACHE, json)
                .putLong(PREF_CATEGORIES_CACHE_TIME, currentTime)
                .apply()

            Timber.tag(TAG).d("Cached ${categories.size} categories")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error caching categories")
        }
    }

    /**
     * Получить список категорий из кеша
     */
    fun getCachedCategories(): List<Category>? {
        if (!isCachingEnabled()) return null

        try {
            val cacheTime = sharedPreferences.getLong(PREF_CATEGORIES_CACHE_TIME, 0)
            val currentTime = System.currentTimeMillis()

            // Проверяем, не истек ли срок действия кеша
            if (currentTime - cacheTime > CACHE_EXPIRATION_TIME) {
                Timber.tag(TAG).d("Categories cache expired")
                return null
            }

            val json = sharedPreferences.getString(PREF_CATEGORIES_CACHE, null) ?: return null
            val type = object : TypeToken<List<Category>>() {}.type
            val categories: List<Category> = gson.fromJson(json, type)

            Timber.tag(TAG).d("Retrieved ${categories.size} categories from cache")
            return categories
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error retrieving cached categories")
            return null
        }
    }

    /**
     * Сохранить список радиостанций категории в кеш
     */
    fun cacheCategoryStations(categoryId: Int, stations: List<Station>) {
        if (!isCachingEnabled()) return

        try {
            val json = gson.toJson(stations)
            val currentTime = System.currentTimeMillis()

            sharedPreferences.edit()
                .putString("$PREF_CATEGORY_STATIONS_PREFIX$categoryId", json)
                .putLong("$PREF_CATEGORY_STATIONS_TIME_PREFIX$categoryId", currentTime)
                .apply()

            Timber.tag(TAG).d("Cached ${stations.size} stations for category $categoryId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error caching category stations")
        }
    }

    /**
     * Получить список радиостанций категории из кеша
     */
    fun getCachedCategoryStations(categoryId: Int): List<Station>? {
        if (!isCachingEnabled()) return null

        try {
            val cacheTime = sharedPreferences.getLong("$PREF_CATEGORY_STATIONS_TIME_PREFIX$categoryId", 0)
            val currentTime = System.currentTimeMillis()

            // Проверяем, не истек ли срок действия кеша
            if (currentTime - cacheTime > CACHE_EXPIRATION_TIME) {
                Timber.tag(TAG).d("Category $categoryId stations cache expired")
                return null
            }

            val json = sharedPreferences.getString("$PREF_CATEGORY_STATIONS_PREFIX$categoryId", null) ?: return null
            val type = object : TypeToken<List<Station>>() {}.type
            val stations: List<Station> = gson.fromJson(json, type)

            Timber.tag(TAG).d("Retrieved ${stations.size} stations for category $categoryId from cache")
            return stations
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error retrieving cached category stations")
            return null
        }
    }

    /**
     * Очистить весь кеш
     */
    fun clearCache() {
        try {
            val editor = sharedPreferences.edit()

            // Удаляем кеш станций
            editor.remove(PREF_STATIONS_CACHE)
            editor.remove(PREF_STATIONS_CACHE_TIME)

            // Удаляем кеш категорий
            editor.remove(PREF_CATEGORIES_CACHE)
            editor.remove(PREF_CATEGORIES_CACHE_TIME)

            // Удаляем кеш станций по категориям
            val allKeys = sharedPreferences.all.keys
            allKeys.forEach { key ->
                if (key.startsWith(PREF_CATEGORY_STATIONS_PREFIX) ||
                    key.startsWith(PREF_CATEGORY_STATIONS_TIME_PREFIX)) {
                    editor.remove(key)
                }
            }

            editor.apply()
            Timber.tag(TAG).d("Cache cleared")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error clearing cache")
        }
    }

    /**
     * Получить размер кеша в байтах (приблизительно)
     */
    fun getCacheSize(): Long {
        try {
            var totalSize = 0L

            // Размер кеша станций
            sharedPreferences.getString(PREF_STATIONS_CACHE, null)?.let {
                totalSize += it.length * 2 // UTF-16, 2 байта на символ
            }

            // Размер кеша категорий
            sharedPreferences.getString(PREF_CATEGORIES_CACHE, null)?.let {
                totalSize += it.length * 2
            }

            // Размер кеша станций по категориям
            val allKeys = sharedPreferences.all.keys
            allKeys.forEach { key ->
                if (key.startsWith(PREF_CATEGORY_STATIONS_PREFIX)) {
                    sharedPreferences.getString(key, null)?.let {
                        totalSize += it.length * 2
                    }
                }
            }

            return totalSize
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error calculating cache size")
            return 0L
        }
    }

    /**
     * Получить размер кеша в МБ
     */
    fun getCacheSizeMB(): Double {
        return getCacheSize() / (1024.0 * 1024.0)
    }
}
