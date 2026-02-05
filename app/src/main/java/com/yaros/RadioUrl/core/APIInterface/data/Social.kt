package com.yaros.RadioUrl.core.APIInterface.data

import com.google.gson.annotations.SerializedName

data class Social(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("image") val image: String?
)