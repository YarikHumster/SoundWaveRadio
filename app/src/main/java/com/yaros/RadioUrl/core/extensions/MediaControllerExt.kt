package com.yaros.RadioUrl.core.extensions

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.helpers.CollectionHelper
import com.yaros.RadioUrl.helpers.VolumeSettingsHelper
import timber.log.Timber


fun MediaController.startSleepTimer(timerDurationMillis: Long) {
    val bundle = Bundle().apply {
        putLong(Keys.SLEEP_TIMER_DURATION, timerDurationMillis)
    }
    sendCustomCommand(SessionCommand(Keys.CMD_START_SLEEP_TIMER, bundle), bundle)
}


fun MediaController.cancelSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


fun MediaController.requestSleepTimerRemaining(): ListenableFuture<SessionResult> {
    return sendCustomCommand(
        SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY),
        Bundle.EMPTY
    )
}


fun MediaController.requestMetadataHistory(): ListenableFuture<SessionResult> {
    return sendCustomCommand(
        SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY),
        Bundle.EMPTY
    )
}


fun MediaController.play(context: Context, station: Station) {
    Timber.tag("MediaControllerExt").i("=== MediaController.play called ===")
    Timber.tag("MediaControllerExt").i("Station: ${station.name}, UUID: ${station.uuid}")

    // Получаем индивидуальную громкость станции
    val stationVolume = VolumeSettingsHelper.getVolume(context, station.uuid)
    Timber.tag("MediaControllerExt").i("Setting volume to: $stationVolume (${(stationVolume * 100).toInt()}%)")

    stop()
    clearMediaItems()
    setMediaItem(CollectionHelper.buildMediaItem(context, station))

    // Устанавливаем громкость перед воспроизведением
    volume = stationVolume
    Timber.tag("MediaControllerExt").i("Volume applied: $volume")

    prepare()
    play()
}

fun MediaController.playStreamDirectly(streamUri: String) {
    sendCustomCommand(
        SessionCommand(Keys.CMD_PLAY_STREAM, Bundle.EMPTY),
        bundleOf(Pair(Keys.KEY_STREAM_URI, streamUri))
    )
}