package com.yaros.RadioUrl.core.supabase

import android.util.Log
import com.yaros.RadioUrl.core.APIInterface.data.Category
import com.yaros.RadioUrl.core.APIInterface.data.Station
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class SupabaseRepository {

//    /**
//     * Получить все категории из таблицы category с подсчетом радиостанций
//     */
    suspend fun getCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.client
                .from("Category")
                .select()
                .decodeList<CategoryDto>()

            // Получаем все радиостанции для подсчета
            val stationsResult = getAllStations()
            val stations = stationsResult.getOrNull() ?: emptyList()

            val categories = response.map { dto ->
                // Подсчитываем количество станций для каждой категории
                val stationCount = stations.count { it.category == dto.id.toString() }
                Category(
                    id = dto.id,
                    name = dto.category_name,
                    stationsCount = stationCount,
                    image = dto.category_image
                )
            }
            Result.success(categories)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching categories", e)
            Result.failure(e)
        }
    }

    /**
     * Получить все радиостанции из таблицы radio
     */
    suspend fun getAllStations(): Result<List<Station>> = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.client
                .from("Radio")
                .select()
                .decodeList<StationDto>()

            val stations = response.map { dto ->
                Station(
                    id = dto.id.toString(),
                    name = dto.radio_name,
                    streamUrl = dto.radio_url,
                    image = dto.radio_image,
                    type = dto.type,
                    category = dto.category_id?.toString(),
                    views = dto.view_count
                )
            }
            Result.success(stations)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching all stations", e)
            Result.failure(e)
        }
    }

    /**
     * Получить радиостанции по категории
     */
    suspend fun getStationsByCategory(categoryId: Int): Result<List<Station>> = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.client
                .from("Radio")
                .select {
                    filter {
                        eq("category_id", categoryId)
                    }
                }
                .decodeList<StationDto>()

            val stations = response.map { dto ->
                Station(
                    id = dto.id.toString(),
                    name = dto.radio_name,
                    streamUrl = dto.radio_url,
                    image = dto.radio_image,
                    type = dto.type,
                    category = dto.category_id?.toString(),
                    views = dto.view_count
                )
            }
            Result.success(stations)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching stations by category", e)
            Result.failure(e)
        }
    }

    /**
     * Поиск радиостанций по имени
     */
    suspend fun searchStations(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.client
                .from("Radio")
                .select {
                    filter {
                        ilike("radio_name", "%$query%")
                    }
                }
                .decodeList<StationDto>()

            val stations = response.map { dto ->
                Station(
                    id = dto.id.toString(),
                    name = dto.radio_name,
                    streamUrl = dto.radio_url,
                    image = dto.radio_image,
                    type = dto.type,
                    category = dto.category_id?.toString(),
                    views = dto.view_count
                )
            }
            Result.success(stations)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error searching stations", e)
            Result.failure(e)
        }
    }

    /**
     * Получить радиостанцию по ID
     */
    suspend fun getStationById(stationId: String): Result<Station?> = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.client
                .from("Radio")
                .select {
                    filter {
                        eq("id", stationId)
                    }
                }
                .decodeSingleOrNull<StationDto>()

            val station = response?.let { dto ->
                Station(
                    id = dto.id.toString(),
                    name = dto.radio_name,
                    streamUrl = dto.radio_url,
                    image = dto.radio_image,
                    type = dto.type,
                    category = dto.category_id?.toString(),
                    views = dto.view_count
                )
            }
            Result.success(station)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching station by id", e)
            Result.failure(e)
        }
    }

    /**
     * Обновить количество просмотров радиостанции
     */
    suspend fun updateStationViews(stationId: String, newViews: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            SupabaseClient.client
                .from("Radio")
                .update({
                    set("view_count", newViews)
                }) {
                    filter {
                        eq("id", stationId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error updating station views", e)
            Result.failure(e)
        }
    }

    /**
     * Получить категорию по ID с подсчетом радиостанций
     */
    suspend fun getCategoryById(categoryId: Int): Result<Category?> = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.client
                .from("Category")
                .select {
                    filter {
                        eq("id", categoryId)
                    }
                }
                .decodeSingleOrNull<CategoryDto>()

            val category = response?.let { dto ->
                // Подсчитываем количество станций для этой категории
                val stationsResult = getStationsByCategory(categoryId)
                val stationCount = stationsResult.getOrNull()?.size ?: 0

                Category(
                    id = dto.id,
                    name = dto.category_name,
                    stationsCount = stationCount,
                    image = dto.category_image
                )
            }
            Result.success(category)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching category by id", e)
            Result.failure(e)
        }
    }
}

// DTO классы для маппинга данных из Supabase
@Serializable
data class CategoryDto(
    val id: Int,
    val category_name: String,
    val category_image: String? = null,
    val category_status: Int? = null,
    val featured: Int? = null,
    val last_update: String? = null
)

@Serializable
data class StationDto(
    val id: Int,
    val category_id: Int? = null,
    val radio_name: String,
    val radio_image: String? = null,
    val radio_url: String,
    val radio_status: Int? = null,
    val view_count: Int = 0,
    val featured: Int? = null,
    val type: String? = null,
    val last_update: String? = null
)
