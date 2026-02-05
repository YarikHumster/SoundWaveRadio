package com.yaros.RadioUrl.data

data class Station(
    val id: Int,
    val name: String,
    val url: String,
    val category: String,
    val country: String,
    val language: String,
    val image: String,
    val isFavorite: Boolean,
    val isPlaying: Boolean,
    val listeners: Int
)