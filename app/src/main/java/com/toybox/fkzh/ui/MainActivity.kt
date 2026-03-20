package com.toybox.fkzh.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.toybox.config
import com.toybox.fkzh.R
import com.toybox.fkzh.databinding.ActivityMainBinding
import com.toybox.fkzh.service.ProxyService
import com.toybox.fkzh.service.alive
import com.toybox.fkzh.util.hasPermission
import com.toybox.fkzh.util.toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

private const val TAG = "MainActivity"

class MainActivity: AppCompatActivity() {

    private val viewBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val requestNotificationPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            switchServiceStatus()
        } else {
            toast { getString(R.string.proxy_notification_no_perm) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(viewBinding.root)

        viewBinding.btnService.setOnClickListener {
            checkNotificationAndSwitchStatus()
        }
        alive.observe(this, this::onServiceStateChanged)

        viewBinding.btnCheck.setOnClickListener { check() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private val proxyServiceIntent by lazy {
        Intent(this, ProxyService::class.java)
    }

    private fun checkNotificationAndSwitchStatus() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                switchServiceStatus()
            }
            hasPermission(Manifest.permission.POST_NOTIFICATIONS) -> {
                switchServiceStatus()
            }
            else -> {
                requestNotificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun switchServiceStatus() {
        viewBinding.btnService.isEnabled = false
        if (alive.value == true) {
            stopService(proxyServiceIntent)
        } else {
            startService(proxyServiceIntent)
        }
    }

    private fun onServiceStateChanged(alive: Boolean) {
        viewBinding.btnService.isEnabled = true
        viewBinding.btnService.text = if (alive)
            "stop service"
        else
            "start service"
    }

    private val okhttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private fun check() {
        if (alive.value != true) {
            toast { "ProxyService is NOT alive" }
            return
        }
        val request = Request.Builder()
            .url("http://127.0.0.1:${config.http.localPort}/")
            .header("Host", "www.zhihu.com")
            .build()

        okhttpClient.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "onFailure: ", e)
                toast { "failed: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                Log.i(TAG, "onResponse: $response")
                toast { "success: $response" }
                response.close()
            }
        })
    }
}