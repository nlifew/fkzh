package com.toybox.fkzh.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat


fun Context.hasPermission(perm: String): Boolean {
    return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
}
