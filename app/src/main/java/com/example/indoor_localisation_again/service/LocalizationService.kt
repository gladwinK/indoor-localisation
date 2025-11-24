package com.example.indoor_localisation_again.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.indoor_localisation_again.R
import com.example.indoor_localisation_again.data.FingerprintRepository
import com.example.indoor_localisation_again.engine.LocalizationEngine
import com.example.indoor_localisation_again.ui.MainActivity

class LocalizationService : Service() {
    private val binder = LocalizationBinder()
    private lateinit var engine: LocalizationEngine

    override fun onCreate() {
        super.onCreate()
        engine = LocalizationEngine(FingerprintRepository.create(applicationContext))
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Idle"))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun getEngine(): LocalizationEngine = engine

    fun updateStatus(status: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    inner class LocalizationBinder : Binder() {
        fun service(): LocalizationService = this@LocalizationService
    }

    companion object {
        const val CHANNEL_ID = "localization_channel"
        const val NOTIFICATION_ID = 101
    }
}
