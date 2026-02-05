package com.yaros.RadioUrl.core.APIInterface.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.yaros.RadioUrl.core.APIInterface.data.Station

@Entity(tableName = "radio")
data class RadioEntity(
    @PrimaryKey
    @Expose
    @ColumnInfo(name = "radio_id")
    var id: String,

    @Expose
    @ColumnInfo(name = "name")
    var name: String,

    @Expose
    @ColumnInfo(name = "image")
    var image: String,

    @Expose
    @ColumnInfo(name = "url")
    var streamUrl: String,

    @Expose
    @ColumnInfo(name = "type")
    var type: String,

    @Expose
    @ColumnInfo(name = "views")
    var views: Int,

    @Expose
    @ColumnInfo(name = "category")
    var category: String,

    @Expose
    @ColumnInfo(name = "saved_date")
    var savedDate: Long = System.currentTimeMillis()
) {
    companion object {
        fun entity(station: Station): RadioEntity = RadioEntity(
            id = station.id,
            name = station.name,
            image = station.image ?: "",
            streamUrl = station.streamUrl,
            type = station.type ?: "",
            views = station.views,
            category = station.category ?: "",
            savedDate = System.currentTimeMillis()
        )
    }

    fun original(): Station = Station(
        id = id,
        name = name,
        streamUrl = streamUrl,
        image = image.ifEmpty { null },
        type = type.ifEmpty { null },
        category = category.ifEmpty { null },
        views = views
    )
}