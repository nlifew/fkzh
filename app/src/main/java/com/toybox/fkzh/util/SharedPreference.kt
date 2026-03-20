package com.toybox.fkzh.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.toybox.fkzh.app.appContext


const val KEY_PROXY_SERVICE_ALIVE = "is_proxy_service_alive"



private const val ACTION_NAME = "com.toybox.fkzh.util.ACTION_UPDATE"
private const val EXTRA_NAME = "name"

private val sp = appContext.getSharedPreferences("sp", Context.MODE_PRIVATE)


fun LifecycleOwner.observeStorageKey(key: String, block: () -> Unit) {
    val manager = LocalBroadcastManager.getInstance(appContext)

    val receiver = object : BroadcastReceiver(), DefaultLifecycleObserver {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(EXTRA_NAME) == key) {
                block()
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            manager.unregisterReceiver(this)
        }
    }

    manager.registerReceiver(receiver, IntentFilter(ACTION_NAME))
}


fun getStorageInt(key: String, defValue: Int) = sp.getInt(key, defValue)
fun getStorageBoolean(key: String, defValue: Boolean) = sp.getBoolean(key, defValue)

fun putStorageInt(key: String, newValue: Int) {
    sp.edit { putInt(key, newValue) }
    notifyListeners(key)
}

fun putStorageBoolean(key: String, newValue: Boolean) {
    sp.edit { putBoolean(key, newValue) }
    notifyListeners(key)
}


private fun notifyListeners(key: String) {
    val intent = Intent(ACTION_NAME)
        .putExtra(EXTRA_NAME, key)
    LocalBroadcastManager.getInstance(appContext)
        .sendBroadcastSync(intent)
}