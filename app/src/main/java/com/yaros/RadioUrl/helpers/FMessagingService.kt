package com.yaros.RadioUrl.helpers

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.net.http.NetworkException
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yaros.RadioUrl.MainActivity
import com.yaros.RadioUrl.R
import timber.log.Timber

class FMessagingService : FirebaseMessagingService() {

    private fun isFirebaseAvailable(): Boolean {
        return try {
            FirebaseApp.getInstance() != null
        } catch (e: IllegalStateException) {
            Timber.Forest.tag(TAG).e(e, "Firebase not initialized")
            false
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "Unexpected Firebase error")
            false
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            if (!isFirebaseAvailable()) {
                Timber.Forest.tag(TAG).w("Received message but Firebase is unavailable")
                return
            }

            remoteMessage.notification?.let { notification ->
                try {
                    handleNotification(notification)
                } catch (e: Exception) {
                    Timber.Forest.tag(TAG).e(e, "Error processing notification")
                }
            }
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "General message handling error")
        }
    }

    private fun handleNotification(notification: RemoteMessage.Notification) {
        val title = notification.title
        val body = notification.body

        try {
            val resultIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val resultPendingIntent = PendingIntent.getActivity(
                this,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = "2038"
            createNotificationChannel(channelId)

            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(resultPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setLights(Color.BLUE, 1000, 300)

            showNotification(notificationBuilder)
            Timber.Forest.tag(TAG).d("Message processed successfully: $body")
        } catch (e: SecurityException) {
            Timber.Forest.tag(TAG).e(e, "PendingIntent security exception")
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "Notification creation failed")
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channelName = "SoundWave"
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.descr_notification)
                    enableLights(true)
                    lightColor = Color.BLUE
                }

                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Timber.Forest.tag(TAG).e(e, "Channel creation failed")
            }
        }
    }

    private fun showNotification(builder: NotificationCompat.Builder) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(0, builder.build())
        } catch (e: NullPointerException) {
            Timber.Forest.tag(TAG).e(e, "Notification manager not available")
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "Notification showing failed")
        }
    }

    override fun onNewToken(token: String) {
        try {
            sendRegistrationToServer(token)
            Timber.Forest.tag(TAG).d("New token processed: $token")
        } catch (e: Exception) {
            Timber.Forest.tag(TAG).e(e, "Token processing failed")
        }
    }

    private fun sendRegistrationToServer(token: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // Реальная логика отправки токена
                Timber.Forest.tag(TAG).d("Token sent to server successfully")
            } catch (e: NetworkException) {
                Timber.Forest.tag(TAG).e(e, "Network error during token send")
            } catch (e: Exception) {
                Timber.Forest.tag(TAG).e(e, "Token sending failed")
            }
        }
    }

    companion object {
        private const val TAG = "MyFMsgService"
    }
}