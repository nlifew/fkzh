package com.toybox.fkzh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import com.toybox.fkzh.R
import com.toybox.main
import java.io.File
import java.nio.file.Files

val alive = MutableLiveData<Boolean>(false)

private const val NOTIFICATION_ID = 1
private const val NOTIFICATION_CHANNEL_ID = "channel_1"

class ProxyService: Service() {

    override fun onCreate() {
        super.onCreate()
        alive.value = true
        startNotification()
        startMain()
    }

    private fun startMain() {
        val configFile = File(dataDir, "config.json")
        if (!configFile.exists()) {
            assets.open("config.json").use { Files.copy(it, configFile.toPath()) }
        }
        main(arrayOf("--config=${configFile.path}"))
    }

    private fun startNotification() {
        val nm = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.proxy_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setWhen(System.currentTimeMillis())
            .setContentTitle(getString(R.string.proxy_notification_title))
            .setContentText(getString(R.string.proxy_notification_message))
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(null)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        alive.value = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}