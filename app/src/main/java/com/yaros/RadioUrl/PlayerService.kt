package com.yaros.RadioUrl

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.ui.PlayerNotificationManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yaros.RadioUrl.helpers.AudioHelper
import com.yaros.RadioUrl.helpers.NetworkHelper
import com.yaros.RadioUrl.helpers.NetworkMonitor
import com.yaros.RadioUrl.helpers.PreferencesHelper
import com.yaros.RadioUrl.helpers.VolumeSettingsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@UnstableApi
class PlayerService : MediaSessionService(), Player.Listener {
    private var exoPlayer: ExoPlayer? = null
    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var networkMonitor: NetworkMonitor
    private var isForegroundService = false
    private lateinit var playerNotificationManager: PlayerNotificationManager

    private var hasAudioFocus = false
    private var playbackDelayed = false
    private var resumeOnFocusGain = false

    private val audioFocusRequest = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Timber.tag("PlayerService").i("Audio focus changed: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.tag("PlayerService").i("Audio focus gained")
                hasAudioFocus = true

                exoPlayer?.let { player ->
                    // Восстанавливаем громкость если была уменьшена (ducking)
                    if (player.volume < 1.0f) {
                        player.volume = 1.0f
                        Timber.tag("PlayerService").i("Volume restored to 100%")
                    }

                    // Если воспроизведение было отложено - запускаем
                    if (playbackDelayed) {
                        Timber.tag("PlayerService").i("Starting delayed playback")
                        player.play()
                        playbackDelayed = false
                    }
                    // Если нужно возобновить после временной паузы
                    else if (resumeOnFocusGain) {
                        Timber.tag("PlayerService").i("Resuming playback after transient loss")
                        player.play()
                        resumeOnFocusGain = false
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.tag("PlayerService").i("Audio focus lost permanently")
                hasAudioFocus = false
                resumeOnFocusGain = false
                playbackDelayed = false
                exoPlayer?.pause()
                // Останавливаем сервис при постоянной потере фокуса
                stopServiceGracefully()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.tag("PlayerService").i("Audio focus lost temporarily")
                hasAudioFocus = false
                resumeOnFocusGain = exoPlayer?.isPlaying == true
                exoPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.tag("PlayerService").i("Audio focus lost, can duck")
                // Уменьшаем громкость вместо паузы
                exoPlayer?.volume = 0.2f
            }
        }
    }

    inner class LocalBinder : Binder() {
        val service: PlayerService
            get() = this@PlayerService
    }

    override fun onCreate() {
        super.onCreate()
        initComponents()

        // Немедленно запускаем foreground service с временным уведомлением
        // чтобы избежать RemoteServiceException
        startForeground(NOTIFICATION_ID, createInitialNotification())
        isForegroundService = true

        setupPlayer()       // Синхронная инициализация
        setupMediaSession() // Должна быть после setupPlayer()
        setupNotificationManager() // Должна быть после setupMediaSession()
        setupNetworkMonitor()
    }

    private fun initComponents() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        networkMonitor = NetworkMonitor(this)
        createNotificationChannel()
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return try {
            OkHttpDataSource.Factory(NetworkHelper.client)
                .setUserAgent("SoundWaveRadio/1.0")
        } catch (e: Exception) {
            Timber.tag("PlayerService").e("Error creating data source factory: ${e.message}")
            // Fallback to default
            OkHttpDataSource.Factory(NetworkHelper.client)
                .setUserAgent("SoundWaveRadio/1.0")
        }
    }

    private fun setupPlayer() {
        try {
            val dataSourceFactory = DefaultDataSource.Factory(
                this,
                createDataSourceFactory()
            )
            exoPlayer = ExoPlayer.Builder(this)
                .setHandleAudioBecomingNoisy(true) // Автоматически останавливает при отключении наушников
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            MIN_BUFFER_MS,
                            MAX_BUFFER_MS,
                            PLAYBACK_BUFFER_MS,
                            REBUFFER_MS
                        )
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build()
                )
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(dataSourceFactory)
                )
                .build()
                .apply {
                    addListener(this@PlayerService)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .setUsage(C.USAGE_MEDIA)
                            .build(),
                        false // НЕ обрабатываем Audio Focus автоматически - делаем это вручную
                    )
                    playWhenReady = true
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setForceHighestSupportedBitrate(true)
                        .build()
                }
            Timber.tag("PlayerService").i("ExoPlayer initialized successfully")
        } catch (e: Exception) {
            Timber.tag("PlayerService").e("Error setting up player: ${e.message}")
            throw e
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(getSessionActivity())
            .setCallback(MediaSessionCallback())
            .build()
    }

    private fun setupNotificationManager() {
        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            NOTIFICATION_CHANNEL_ID
        )
            .setMediaDescriptionAdapter(NotificationMediaAdapter())
            .setChannelNameResourceId(R.string.notification_info)
            .setChannelDescriptionResourceId(R.string.default_notification_channel_name)
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    // Обновляем уведомление, если сервис уже в foreground режиме
                    if (ongoing && isForegroundService) {
                        startForeground(notificationId, notification)
                    } else if (!ongoing) {
                        stopForeground(false)
                        isForegroundService = false
                    }
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser:  Boolean) {
                    stopSelf()
                }
            })
            .build()
            .apply {
                setPlayer(exoPlayer)
                setColorized(true)
                setPriority(NotificationCompat.PRIORITY_LOW)
            }
    }


    private val notificationListener = object : PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            if (ongoing && !isForegroundService) {
                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            stopSelf()
        }
    }

    private fun setupNetworkMonitor() {
        networkMonitor.addListener(object : NetworkMonitor.Listener {
            override fun onNetworkAvailable() {
                // Post to the main thread
                Handler(Looper.getMainLooper()).post {
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                }
            }

            override fun onNetworkLost() {
                // Post to the main thread
                Handler(Looper.getMainLooper()).post {
                    exoPlayer?.pause()
                }
            }
        })
        networkMonitor.start()
    }


    private fun getSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntentFlags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
            else -> {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        }

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return if (::mediaSession.isInitialized) mediaSession else null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Убеждаемся, что сервис в foreground режиме при каждом вызове startForegroundService()
        if (!isForegroundService) {
            startForeground(NOTIFICATION_ID, createInitialNotification())
            isForegroundService = true
        }

        intent?.let {
            when (it.action) {
                Keys.ACTION_PLAY_STREAM -> {
                    val streamUri = it.getStringExtra(Keys.EXTRA_STREAM_URI)
                    val stationName = it.getStringExtra(Keys.EXTRA_STATION_NAME)
                    val stationImage = it.getStringExtra(Keys.EXTRA_STATION_IMAGE)
                    val stationUuid = it.getStringExtra(Keys.EXTRA_STATION_UUID)

                    Timber.tag("PlayerService").i("Received ACTION_PLAY_STREAM: name=$stationName, uri=$streamUri, uuid=$stationUuid")

                    if (!streamUri.isNullOrBlank()) {
                        val artUri = if (!stationImage.isNullOrBlank()) {
                            android.net.Uri.parse(stationImage)
                        } else {
                            null
                        }

                        playRadio(
                            url = streamUri,
                            title = stationName ?: "Radio Station",
                            artist = getString(R.string.app_name),
                            artUri = artUri,
                            stationUuid = stationUuid
                        )
                    } else {
                        Timber.tag("PlayerService").e("Stream URI is null or blank")
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.tag("PlayerService").i("Task removed, isPlaying: ${exoPlayer?.isPlaying}")

        // Останавливаем сервис только если не воспроизводится
        if (exoPlayer?.isPlaying != true) {
            Timber.tag("PlayerService").i("Stopping service - not playing")
            stopServiceGracefully()
        } else {
            Timber.tag("PlayerService").i("Keeping service alive - still playing")
        }
    }

    override fun onDestroy() {
            releaseResources()
        super.onDestroy()
    }

    private fun releaseResources() {
        try {
            retryHandler.removeCallbacksAndMessages(null)
            retryAttempts = 0
            networkMonitor.stop()

            // Освобождаем Audio Focus
            if (hasAudioFocus) {
                audioManager.abandonAudioFocus(audioFocusRequest)
                hasAudioFocus = false
                Timber.tag("PlayerService").i("Audio focus abandoned")
            }

            if (::mediaSession.isInitialized) {
                mediaSession.release()
            }
            exoPlayer?.release()
            exoPlayer = null

            if (::playerNotificationManager.isInitialized) {
                playerNotificationManager.setPlayer(null)
            }
        } catch (e: Exception) {
            Timber.tag("PlayerService").e("Error releasing resources: ${e.message}")
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val baseResult = super.onConnect(session, controller)
            val customCommands = listOf(
                SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY),
                SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY),
                SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY),
                SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY)
            )
            val newSessionCommands = baseResult.availableSessionCommands
                .buildUpon()
                .apply { customCommands.forEach { add(it) } }
                .build()
            return MediaSession.ConnectionResult.accept(
                newSessionCommands,
                baseResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "play" ->  {
                    if (isAppInForeground()) {
                        // Запрашиваем Audio Focus перед воспроизведением
                        val result = audioManager.requestAudioFocus(
                            audioFocusRequest,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN
                        )
                        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            hasAudioFocus = true
                            exoPlayer?.play()
                            Timber.tag("PlayerService").i("Play command: Audio focus granted, playing")
                        } else {
                            Timber.tag("PlayerService").w("Play command: Audio focus denied")
                        }
                    } else {
                        Timber.tag("PlayerService").w("Невозможно воспроизвести из фонового режима")
                    }
                }
                "pause" ->   handlePauseCommand()
                "stop" ->  stopServiceGracefully()
                Keys.CMD_REQUEST_METADATA_HISTORY -> {
                    val metadataHistory = PreferencesHelper.loadMetadataHistory()
                    val resultBundle = Bundle().apply {
                        putStringArrayList(Keys.EXTRA_METADATA_HISTORY, ArrayList(metadataHistory))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        private fun handlePauseCommand() {
            exoPlayer?.pause()

            // Освобождаем Audio Focus при паузе
            if (hasAudioFocus) {
                audioManager.abandonAudioFocus(audioFocusRequest)
                hasAudioFocus = false
                Timber.tag("PlayerService").i("Audio focus abandoned on pause")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            isForegroundService = false
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        Timber.tag("PlayerService").i("onIsPlayingChanged: $isPlaying")

        if (!isPlaying) {
            // Когда воспроизведение останавливается, освобождаем Audio Focus
            if (hasAudioFocus && exoPlayer?.playWhenReady == false) {
                audioManager.abandonAudioFocus(audioFocusRequest)
                hasAudioFocus = false
                Timber.tag("PlayerService").i("Audio focus abandoned - playback stopped")
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        try {
            when (playbackState) {
                Player.STATE_READY -> {
                    Timber.tag("PlayerService").i("Playback state: READY")
                    retryAttempts = 0 // Reset retry counter on successful playback
                    handlePlaybackReady()
                }
                Player.STATE_ENDED -> {
                    Timber.tag("PlayerService").i("Playback state: ENDED")
                    handlePlaybackEnd()
                }
                Player.STATE_IDLE -> {
                    Timber.tag("PlayerService").i("Playback state: IDLE")
                    // Останавливаем сервис если плеер не должен воспроизводить
                    if (exoPlayer?.playWhenReady == false) {
                        // Освобождаем Audio Focus
                        if (hasAudioFocus) {
                            audioManager.abandonAudioFocus(audioFocusRequest)
                            hasAudioFocus = false
                            Timber.tag("PlayerService").i("Audio focus abandoned on idle")
                        }
                        stopServiceGracefully()
                    }
                }
                Player.STATE_BUFFERING -> {
                    Timber.tag("PlayerService").i("Playback state: BUFFERING")
                    handleBufferingState()
                }
            }
        } catch (e: Exception) {
            Timber.tag("PlayerService").e("Error in onPlaybackStateChanged: ${e.message}")
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.tag("PlayerService").e("Playback error: ${error.errorCodeName} - ${error.message}")
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                Timber.tag("PlayerService").e("Network connection failed")
                handlePlaybackError(error)
            }
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                Timber.tag("PlayerService").e("Bad HTTP status: ${error.message}")
                handlePlaybackError(error)
            }
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> {
                Timber.tag("PlayerService").e("Invalid HTTP content type")
                handlePlaybackError(error)
            }
            PlaybackException.ERROR_CODE_TIMEOUT -> {
                Timber.tag("PlayerService").e("Playback timeout")
                handlePlaybackError(error)
            }
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                Timber.tag("PlayerService").e("Unspecified IO error")
                handlePlaybackError(error)
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                Timber.tag("PlayerService").e("Malformed stream format")
                stopServiceGracefully()
            }
            else -> {
                Timber.tag("PlayerService").e("Unknown playback error: ${error.errorCode}")
                handlePlaybackError(error)
            }
        }
    }

    override fun onMetadata(metadata: Metadata) {
        super.onMetadata(metadata)
        try {
            if (metadata.length() > 0) {
                val metadataString = AudioHelper.getMetadataString(metadata)
                if (metadataString.isNotEmpty()) {
                    Timber.tag("PlayerService").i("Metadata received: $metadataString")

                    // Сохраняем метаданные в историю
                    val metadataHistory = PreferencesHelper.loadMetadataHistory()

                    // Добавляем только если это новые метаданные
                    if (metadataHistory.isEmpty() || metadataHistory.last() != metadataString) {
                        metadataHistory.add(metadataString)

                        // Ограничиваем размер истории
                        while (metadataHistory.size > Keys.DEFAULT_SIZE_OF_METADATA_HISTORY) {
                            metadataHistory.removeAt(0)
                        }

                        PreferencesHelper.saveMetadataHistory(metadataHistory)
                        Timber.tag("PlayerService").i("Metadata saved to history")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("PlayerService").e("Error processing metadata: ${e.message}")
        }
    }

    private fun handlePlaybackReady() {
        optimizePowerUsage()
    }

    private fun handleBufferingState() {
        exoPlayer?.playWhenReady = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_info),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.default_notification_channel_name)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_info))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(getSessionActivity())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun playRadio(url: String, title: String, artist: String, artUri: Uri?, stationUuid: String? = null) {
        try {
            Timber.tag("PlayerService").i("=== playRadio called ===")
            Timber.tag("PlayerService").i("Title: $title, URL: $url, UUID: $stationUuid")

            if (url.isBlank()) {
                Timber.tag("PlayerService").e("Invalid URL: URL is blank")
                return
            }

            exoPlayer?.apply {
                // Применяем индивидуальную громкость ДО начала воспроизведения
                val targetVolume = if (!stationUuid.isNullOrBlank()) {
                    val stationVolume = VolumeSettingsHelper.getVolume(this@PlayerService, stationUuid)
                    Timber.tag("PlayerService").i("Station has custom volume: $stationVolume (${(stationVolume * 100).toInt()}%)")
                    stationVolume
                } else {
                    Timber.tag("PlayerService").i("No UUID provided, using default volume: 1.0")
                    1.0f
                }

                // Устанавливаем громкость перед подготовкой
                volume = targetVolume
                Timber.tag("PlayerService").i("Volume set to: $targetVolume before playback")

                // Сбрасываем флаги Audio Focus для нового воспроизведения
                resumeOnFocusGain = false
                playbackDelayed = false

                // Останавливаем текущее воспроизведение
                stop()
                clearMediaItems()

                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .setArtworkUri(artUri)
                            .build()
                    )
                    .build()

                setMediaItem(mediaItem)

                // Подтверждаем, что громкость установлена
                Timber.tag("PlayerService").i("Current player volume before prepare: $volume")

                // Запрашиваем Audio Focus ПЕРЕД prepare и play
                requestAudioFocusAndPlay()
            } ?: run {
                Timber.tag("PlayerService").e("ExoPlayer is null, cannot play radio")
            }
        } catch (e: IllegalArgumentException) {
            Timber.tag("PlayerService").e("Invalid URL format: ${e.message}")
        } catch (e: IllegalStateException) {
            Timber.tag("PlayerService").e("Player in invalid state: ${e.message}")
        } catch (e: Exception) {
            Timber.tag("PlayerService").e("Error playing radio: ${e.message}")
            handlePlaybackError(PlaybackException("Error playing radio", e, PlaybackException.ERROR_CODE_UNSPECIFIED))
        }
    }

    private fun requestAudioFocusAndPlay() {
        Timber.tag("PlayerService").i("Requesting audio focus...")

        val result = audioManager.requestAudioFocus(
            audioFocusRequest,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )

        Timber.tag("PlayerService").i("Audio focus request result: $result")

        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                Timber.tag("PlayerService").i("Audio focus GRANTED - starting playback")
                hasAudioFocus = true
                playbackDelayed = false

                // Теперь можем безопасно начать воспроизведение
                exoPlayer?.let { player ->
                    player.prepare()
                    player.play()
                    Timber.tag("PlayerService").i("Playback started with audio focus")
                }
            }
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                Timber.tag("PlayerService").w("Audio focus FAILED - cannot play")
                hasAudioFocus = false
                playbackDelayed = false
                Toast.makeText(this, getString(R.string.notification_play), Toast.LENGTH_SHORT).show()
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                Timber.tag("PlayerService").i("Audio focus DELAYED - will play when granted")
                hasAudioFocus = false
                playbackDelayed = true

                // prepare() вызовем сейчас, play() будет вызван в onAudioFocusChange
                exoPlayer?.prepare()
            }
        }
    }

    private fun stopServiceGracefully() {
        Timber.tag("PlayerService").i("Stopping service gracefully")

        // Освобождаем Audio Focus перед остановкой
        if (hasAudioFocus) {
            audioManager.abandonAudioFocus(audioFocusRequest)
            hasAudioFocus = false
            Timber.tag("PlayerService").i("Audio focus abandoned on stop")
        }

        exoPlayer?.stop()
        stopForeground(true)
        stopSelf()
    }

    private fun optimizePowerUsage() {
        exoPlayer?.setWakeMode(
            if (isCharging(this)) C.WAKE_MODE_NETWORK
            else C.WAKE_MODE_LOCAL
        )
    }

    private fun handlePlaybackEnd() {
        stopServiceGracefully()
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = packageName
        return appProcesses.any { process ->
            process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
            process.processName == packageName
        }
    }

    fun isCharging(context: Context): Boolean {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private var retryAttempts = 0
    private val maxRetryAttempts = 3
    private val retryHandler = Handler(Looper.getMainLooper())

    private fun handlePlaybackError(error: PlaybackException) {
        Timber.tag("PlayerService").e("Playback error: ${error.errorCodeName} - ${error.message}")
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                if (retryAttempts < maxRetryAttempts) {
                    retryPlayback()
                } else {
                    Timber.tag("PlayerService").e("Max retry attempts reached, stopping playback")
                    retryAttempts = 0
                    stopServiceGracefully()
                }
            }
            else -> {
                retryAttempts = 0
                stopServiceGracefully()
            }
        }
    }

    private fun retryPlayback() {
        retryAttempts++
        val delayMillis = (retryAttempts * 2000L) // Exponential backoff: 2s, 4s, 6s
        Timber.tag("PlayerService").i("Retrying playback (attempt $retryAttempts/$maxRetryAttempts) in ${delayMillis}ms")

        retryHandler.postDelayed({
            try {
                exoPlayer?.let { player ->
                    if (player.playbackState == Player.STATE_IDLE ||
                        player.playbackState == Player.STATE_ENDED) {
                        player.prepare()
                        player.play()
                    }
                }
            } catch (e: Exception) {
                Timber.tag("PlayerService").e("Error during retry: ${e.message}")
                retryAttempts = 0
                stopServiceGracefully()
            }
        }, delayMillis)
    }

    private inner class NotificationMediaAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            return player.mediaMetadata.title ?: getString(R.string.app_name)
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            return getSessionActivity()
        }

        override fun getCurrentContentText(player: Player): CharSequence? {
            return player.mediaMetadata.artist ?: getString(R.string.sample_text_station_metadata)
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            val imageUri = player.mediaMetadata.artworkUri
            return if (imageUri != null) {
                loadBitmapAsync(imageUri.toString(), callback)
                null
            } else {
                null
            }
        }

        private fun loadBitmapAsync(url: String, callback: PlayerNotificationManager.BitmapCallback) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bitmap = Glide.with(this@PlayerService)
                        .asBitmap()
                        .load(url)
                        .apply(RequestOptions().timeout(10000))
                        .submit()
                        .get()
                     callback.onBitmap(bitmap)
                } catch (e: Exception) {
                    Timber.tag("Player").v("Playback error: notification")
                }
            }
        }
    }

    companion object {
        const val NOTIFICATION_ID = 2031
        const val NOTIFICATION_CHANNEL_ID = "Проигрование"
        private const val MIN_BUFFER_MS = 5000
        private const val MAX_BUFFER_MS = 10000
        private const val PLAYBACK_BUFFER_MS = 2000
        private const val REBUFFER_MS = 5000
    }
}
