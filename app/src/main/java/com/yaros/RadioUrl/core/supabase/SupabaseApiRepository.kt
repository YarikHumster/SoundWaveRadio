package com.yaros.RadioUrl.core.supabase

import android.content.Context
import android.util.Log
import com.yaros.RadioUrl.core.APIInterface.data.Category
import com.yaros.RadioUrl.core.APIInterface.data.NewsItem
import com.yaros.RadioUrl.data.Station as DataStation
import com.yaros.RadioUrl.helpers.CacheManager

/**
 * Адаптер для совместимости с существующим ApiRepository
 * Использует Supabase вместо Retrofit API с поддержкой кеширования
 */
class SupabaseApiRepository(private val context: Context? = null) {
    private val supabaseRepository = SupabaseRepository()

    init {
        context?.let {
            CacheManager.init(it)
        }
    }

    // Добавляем метод getHomeData для совместимости
    suspend fun getHomeData(): NetworkResult<HomeResponse> {
        return try {
            // Получаем последние радиостанции как "избранные"
            val stationsResult = supabaseRepository.getAllStations()

            if (stationsResult.isSuccess) {
                val stations = stationsResult.getOrNull()?.map { apiStation ->
                    DataStation(
                        id = apiStation.id.toIntOrNull() ?: 0,
                        name = apiStation.name,
                        url = apiStation.streamUrl,
                        image = apiStation.image ?: "",
                        category = apiStation.category ?: "",
                        country = "",
                        language = "",
                        isFavorite = false,
                        isPlaying = false,
                        listeners = apiStation.views
                    )
                } ?: emptyList()

                // Возвращаем HomeResponse с пустыми новостями и станциями
                NetworkResult.Success(HomeResponse(news = emptyList(), favorites = stations))
            } else {
                NetworkResult.Error(stationsResult.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e("SupabaseApiRepository", "Error getting home data", e)
            NetworkResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCategories(): NetworkResult<List<Category>> {
        return try {
            // Проверяем кеш
            val cachedCategories = CacheManager.getCachedCategories()
            if (cachedCategories != null) {
                Log.d("SupabaseApiRepository", "Returning cached categories")
                return NetworkResult.Success(cachedCategories)
            }

            // Загружаем из сети
            val result = supabaseRepository.getCategories()
            if (result.isSuccess) {
                val categories = result.getOrNull() ?: emptyList()
                // Сохраняем в кеш
                CacheManager.cacheCategories(categories)
                NetworkResult.Success(categories)
            } else {
                NetworkResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e("SupabaseApiRepository", "Error getting categories", e)
            NetworkResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getRecentRadio(): NetworkResult<List<DataStation>> {
        return try {
            // Проверяем кеш
            val cachedStations = CacheManager.getCachedStations()
            if (cachedStations != null) {
                Log.d("SupabaseApiRepository", "Returning cached stations")
                return NetworkResult.Success(cachedStations)
            }

            // Загружаем из сети
            val result = supabaseRepository.getAllStations()
            if (result.isSuccess) {
                // Преобразуем Station из APIInterface в Station из data
                val stations = result.getOrNull()?.map { apiStation ->
                    DataStation(
                        id = apiStation.id.toIntOrNull() ?: 0,
                        name = apiStation.name,
                        url = apiStation.streamUrl,
                        image = apiStation.image ?: "",
                        category = apiStation.category ?: "",
                        country = "",
                        language = "",
                        isFavorite = false,
                        isPlaying = false,
                        listeners = apiStation.views
                    )
                } ?: emptyList()
                // Сохраняем в кеш
                CacheManager.cacheStations(stations)
                NetworkResult.Success(stations)
            } else {
                NetworkResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e("SupabaseApiRepository", "Error getting recent radio", e)
            NetworkResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCategoryDetail(categoryId: Int, filter: String? = null): NetworkResult<List<DataStation>> {
        return try {
            // Проверяем кеш для этой категории
            val cachedStations = CacheManager.getCachedCategoryStations(categoryId)
            if (cachedStations != null) {
                Log.d("SupabaseApiRepository", "Returning cached stations for category $categoryId")
                return NetworkResult.Success(cachedStations)
            }

            // Получаем станции по ID категории из сети
            val stationsResult = supabaseRepository.getStationsByCategory(categoryId)
            if (stationsResult.isSuccess) {
                val stations = stationsResult.getOrNull()?.map { apiStation ->
                    DataStation(
                        id = apiStation.id.toIntOrNull() ?: 0,
                        name = apiStation.name,
                        url = apiStation.streamUrl,
                        image = apiStation.image ?: "",
                        category = apiStation.category ?: "",
                        country = "",
                        language = "",
                        isFavorite = false,
                        isPlaying = false,
                        listeners = apiStation.views
                    )
                } ?: emptyList()
                // Сохраняем в кеш
                CacheManager.cacheCategoryStations(categoryId, stations)
                NetworkResult.Success(stations)
            } else {
                NetworkResult.Error(stationsResult.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e("SupabaseApiRepository", "Error getting category detail", e)
            NetworkResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun search(query: String): NetworkResult<List<DataStation>> {
        return try {
            val result = supabaseRepository.searchStations(query)
            if (result.isSuccess) {
                val stations = result.getOrNull()?.map { apiStation ->
                    DataStation(
                        id = apiStation.id.toIntOrNull() ?: 0,
                        name = apiStation.name,
                        url = apiStation.streamUrl,
                        image = apiStation.image ?: "",
                        category = apiStation.category ?: "",
                        country = "",
                        language = "",
                        isFavorite = false,
                        isPlaying = false,
                        listeners = apiStation.views
                    )
                } ?: emptyList()
                NetworkResult.Success(stations)
            } else {
                NetworkResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e("SupabaseApiRepository", "Error searching", e)
            NetworkResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun searchRTL(query: String): NetworkResult<List<DataStation>> {
        // RTL поиск - используем тот же метод поиска
        return search(query)
    }

    suspend fun updateStationViews(stationId: Int): NetworkResult<Unit> {
        return try {
            // Сначала получаем текущую станцию
            val stationResult = supabaseRepository.getStationById(stationId.toString())
            if (stationResult.isFailure) {
                return NetworkResult.Error(stationResult.exceptionOrNull()?.message ?: "Station not found")
            }

            val station = stationResult.getOrNull()
            if (station == null) {
                return NetworkResult.Error("Station not found")
            }

            // Увеличиваем счетчик просмотров
            val newViews = station.views + 1
            val updateResult = supabaseRepository.updateStationViews(stationId.toString(), newViews)

            if (updateResult.isSuccess) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error(updateResult.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e("SupabaseApiRepository", "Error updating station views", e)
            NetworkResult.Error(e.message ?: "Unknown error")
        }
    }

    sealed class NetworkResult<out T> {
        data class Success<out T>(val data: T) : NetworkResult<T>()
        data class Error(val message: String) : NetworkResult<Nothing>()
    }
}

// Data class для совместимости с HomeFragment
data class HomeResponse(
    val news: List<NewsItem>?,
    val favorites: List<DataStation>?
)
