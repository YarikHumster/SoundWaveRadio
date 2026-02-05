package com.yaros.RadioUrl.core.APIInterface

import android.util.Log
import com.yaros.RadioUrl.core.APIInterface.ApiService
import com.yaros.RadioUrl.core.APIInterface.HomeResponse
import com.yaros.RadioUrl.core.APIInterface.data.Category
import com.yaros.RadioUrl.core.APIInterface.data.NewsItem
import com.yaros.RadioUrl.data.Station
import retrofit2.Response
import java.io.IOException

class ApiRepository(private val apiService: ApiService) {

    suspend fun getHomeData(): NetworkResult<HomeResponse> {
        return handleApiResponse { apiService.getHome() }
    }

    suspend fun getRecentRadio(): NetworkResult<List<Station>> {
        return handleApiResponse { apiService.getRecentRadio() }
    }

    suspend fun getCategories(): NetworkResult<List<Category>> {
        return handleApiResponse { apiService.getCategoryIndex() }
    }

    suspend fun getCategoryDetail(categoryId: Int, filter: String? = null): NetworkResult<List<Station>> {
        return handleApiResponse {
            apiService.getCategoryDetail(
                categoryId = categoryId,
                filter = filter
            )
        }
    }

    suspend fun search(query: String): NetworkResult<List<Station>> {
        return handleApiResponse {
            apiService.getSearchResults(query = query)  // Явное указание имени параметра
        }
    }

    suspend fun searchRTL(query: String): NetworkResult<List<Station>> {
        return handleApiResponse {
            apiService.getSearchResultsRTL(query = query)  // Явное указание имени параметра
        }
    }

    suspend fun getPrivacyPolicy(): NetworkResult<String> {
        return handleApiResponse { apiService.getPrivacyPolicy() }
    }

    suspend fun getAppSettings(): NetworkResult<Map<String, String>> {
        return handleApiResponse { apiService.getSettings() }
    }

    suspend fun getNewSettings(): NetworkResult<Map<String, String>> {
        return handleApiResponse { apiService.getNewSettings() }
    }

    suspend fun getSocialLinks(): NetworkResult<List<Any>> {
        return handleApiResponse { apiService.getSocial() }
    }

    suspend fun updateStationViews(stationId: Int): NetworkResult<Unit> {
        return handleApiResponse {
            apiService.updateView(stationId = stationId)  // Явное указание имени параметра
        }
    }

    suspend fun checkServerConnection(): NetworkResult<String> {
        return handleApiResponse { apiService.checkConnection() }
    }

    private suspend inline fun <T> handleApiResponse(crossinline request: suspend () -> Response<T>): NetworkResult<T> {
        return try {
            val response = request.invoke()
            if (response.isSuccessful) {
                response.body()?.let {
                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("Response body is empty")
            } else {
                NetworkResult.Error("API error: ${response.code()} - ${response.message()}")
            }
        } catch (e: IOException) {
            NetworkResult.Error("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            NetworkResult.Error("Unexpected error: ${e.localizedMessage}")
        }
    }

    sealed class NetworkResult<out T> {
        data class Success<out T>(val data: T) : NetworkResult<T>()
        data class Error(val message: String) : NetworkResult<Nothing>()
    }
}