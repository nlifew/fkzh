package com.toybox.fkzh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import com.toybox.fkzh.R
import com.toybox.fkzh.app.appContext
import com.toybox.fkzh.ui.MainActivity
import com.toybox.main
import com.toybox.shutdown
import java.io.File
import java.nio.file.Files

val alive = MutableLiveData<Boolean>(false)

private val myIntent by lazy { Intent(appContext, ProxyService::class.java) }

fun Context.startProxyService() { startService(myIntent) }
fun Context.stopProxyService() { stopService(myIntent) }


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
            .setContentIntent(PendingIntent.getActivity(
                appContext,
                1,
                Intent(appContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdown()
        alive.value = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}