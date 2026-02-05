package com.yaros.RadioUrl.core.APIInterface.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRadio(radio: RadioEntity)

    @Query("DELETE FROM radio WHERE radio_id = :radio_id")
    fun deleteRadio(radio_id: String)

    @Query("DELETE FROM radio")
    fun deleteAllRadio()

    @Query("SELECT * FROM radio ORDER BY saved_date DESC")
    fun getAllRadio(): List<RadioEntity>

    @Query("SELECT COUNT(radio_id) FROM radio")
    fun getRadioCount(): Int?

    @Query("SELECT * FROM radio WHERE radio_id = :radio_id LIMIT 1")
    fun getRadio(radio_id: String): RadioEntity?
}