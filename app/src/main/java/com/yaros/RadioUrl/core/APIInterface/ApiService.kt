package com.yaros.RadioUrl.core.APIInterface

import com.yaros.RadioUrl.data.Station
import com.google.gson.annotations.SerializedName
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.core.APIInterface.data.Category
import com.yaros.RadioUrl.core.APIInterface.data.NewsItem
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("api.php")
    suspend fun getHome(@Query("x") x: String = "get_home", @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<HomeResponse>

    @GET("api.php")
    suspend fun getRecentRadio(@Query("x") x: String = "get_radios", @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<List<Station>>

    @GET("api.php")
    suspend fun getCategoryIndex(@Query("x") x: String = "get_categories", @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<List<Category>>

    @GET("api.php")
    suspend fun getCategoryDetail(@Query("x") x: String = "get_category_detail", @Query("category_id") categoryId: Int, @Query("filter") filter: String? = null, @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<List<Station>>

    @GET("api.php")
    suspend fun getSearchResults(@Query("x") x: String = "search", @Query("q") query: String, @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<List<Station>>

    @GET("api.php")
    suspend fun getSearchResultsRTL(@Query("x") x: String = "search_rtl", @Query("q") query: String, @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<List<Station>>
    
    @GET("api.php")
    suspend fun getPrivacyPolicy(@Query("x") x: String = "privacy_policy", @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<String>
    
    @GET("api.php")
    suspend fun getSettings(@Query("x") x: String = "settings", @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<Map<String, String>>
    
    @GET("api.php")
    suspend fun getNewSettings(@Query("x") x: String = "setting", @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<Map<String, String>>
    
    @GET("api.php")
    suspend fun getSocial(@Query("x") x: String = "social", @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<List<Any>>
    
    @GET("api.php")
    suspend fun updateView(@Query("x") x: String = "update_view", @Query("station_id") stationId: Int, @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<Unit>
    
    @GET("api.php")
    suspend fun checkConnection(@Query("x") x: String = "check_connection", @Query("api_key") apiKey: String = "cda11lHY0ZafN2nrti4U5QAKMDhTw7Czm1xoSsyVLduvRegkqE"): Response<String>

}

data class HomeResponse(
    @SerializedName("news") val news: List<NewsItem>?,
    @SerializedName("favorites") val favorites: List<Station>?
) 

object ApiConstants {
    const val BASE_URL = Keys.SWH_ADDRESS
}