package com.yaros.RadioUrl.helpers

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

/**
 * Helper для управления индивидуальными настройками громкости радиостанций
 * Громкость хранится отдельно от модели Station в SharedPreferences
 */
object VolumeSettingsHelper {

    private const val PREFS_NAME = "station_volume_settings"
    private const val TAG = "VolumeSettingsHelper"
    private const val DEFAULT_VOLUME = 1.0f // Максимальная громкость по умолчанию

    /**
     * Получить настройку громкости для станции
     * @param context Context приложения
     * @param stationUuid UUID станции
     * @return Значение громкости от 0.0 до 1.0
     */
    fun getVolume(context: Context, stationUuid: String): Float {
        val prefs = getPreferences(context)
        val volume = prefs.getFloat(stationUuid, DEFAULT_VOLUME)
        val hasCustom = prefs.contains(stationUuid)
        Timber.tag(TAG).i("Getting volume for station $stationUuid: $volume (hasCustom: $hasCustom, default: $DEFAULT_VOLUME)")
        return volume
    }

    /**
     * Сохранить настройку громкости для станции
     * @param context Context приложения
     * @param stationUuid UUID станции
     * @param volume Значение громкости от 0.0 до 1.0
     */
    fun setVolume(context: Context, stationUuid: String, volume: Float) {
        val normalizedVolume = volume.coerceIn(0.0f, 1.0f)
        val prefs = getPreferences(context)
        prefs.edit().putFloat(stationUuid, normalizedVolume).apply()
        Timber.tag(TAG).i("Setting volume for station $stationUuid: $normalizedVolume (${(normalizedVolume * 100).toInt()}%)")
    }

    /**
     * Удалить настройку громкости для станции (вернуть к значению по умолчанию)
     * @param context Context приложения
     * @param stationUuid UUID станции
     */
    fun removeVolume(context: Context, stationUuid: String) {
        val prefs = getPreferences(context)
        prefs.edit().remove(stationUuid).apply()
        Timber.tag(TAG).d("Removing volume setting for station $stationUuid")
    }

    /**
     * Проверить, есть ли индивидуальная настройка громкости для станции
     * @param context Context приложения
     * @param stationUuid UUID станции
     * @return true если есть индивидуальная настройка
     */
    fun hasCustomVolume(context: Context, stationUuid: String): Boolean {
        val prefs = getPreferences(context)
        return prefs.contains(stationUuid)
    }

    /**
     * Получить все настройки громкости
     * @param context Context приложения
     * @return Map с UUID станций и их громкостью
     */
    fun getAllVolumeSettings(context: Context): Map<String, Float> {
        val prefs = getPreferences(context)
        val allSettings = mutableMapOf<String, Float>()
        prefs.all.forEach { (key, value) ->
            if (value is Float) {
                allSettings[key] = value
            }
        }
        return allSettings
    }

    /**
     * Очистить все настройки громкости
     * @param context Context приложения
     */
    fun clearAllVolumeSettings(context: Context) {
        val prefs = getPreferences(context)
        prefs.edit().clear().apply()
        Timber.tag(TAG).d("Cleared all volume settings")
    }

    /**
     * Получить процентное значение громкости (0-100)
     * @param context Context приложения
     * @param stationUuid UUID станции
     * @return Значение от 0 до 100
     */
    fun getVolumePercent(context: Context, stationUuid: String): Int {
        return (getVolume(context, stationUuid) * 100).toInt()
    }

    /**
     * Установить громкость в процентах (0-100)
     * @param context Context приложения
     * @param stationUuid UUID станции
     * @param percent Значение от 0 до 100
     */
    fun setVolumePercent(context: Context, stationUuid: String, percent: Int) {
        val normalizedPercent = percent.coerceIn(0, 100)
        val volume = normalizedPercent / 100.0f
        Timber.tag(TAG).i("setVolumePercent called for station $stationUuid: $percent% -> $volume")
        setVolume(context, stationUuid, volume)
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
