package com.yaros.RadioUrl.core.APIInterface.data

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName("cid") val id: Int,
    @SerializedName("category_name") val name: String,
    @SerializedName("radio_count") val stationsCount: Int,
    @SerializedName("category_image") val image: String?
)
