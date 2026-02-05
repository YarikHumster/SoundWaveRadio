package com.yaros.RadioUrl.core.APIInterface.data

import com.google.gson.annotations.SerializedName

data class Station(
    @SerializedName("id") var id: String,
    @SerializedName("name") var name: String,
    @SerializedName("url") var streamUrl: String,
    @SerializedName("image") var image: String?,
    @SerializedName("type") var type: String?,
    @SerializedName("category") var category: String?,
    @SerializedName("views") var views: Int
)
