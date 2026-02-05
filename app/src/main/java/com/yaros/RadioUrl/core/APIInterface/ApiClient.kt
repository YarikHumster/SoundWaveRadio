package com.yaros.RadioUrl.core.APIInterface

import com.yaros.RadioUrl.BuildConfig
import com.yaros.RadioUrl.helpers.NetworkHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val okHttpClient = NetworkHelper.createSecureOkHttpClient()
        .newBuilder()
        .addInterceptor(createApiKeyInterceptor())
        .addInterceptor(createLoggingInterceptor())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun createApiKeyInterceptor() = Interceptor { chain ->
        val originalRequest = chain.request()
        val newUrl = originalRequest.url.newBuilder()
            .addQueryParameter("api_key", BuildConfig.API_KEY)
            .build()
        chain.proceed(originalRequest.newBuilder().url(newUrl).build())
    }

    private fun createLoggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}