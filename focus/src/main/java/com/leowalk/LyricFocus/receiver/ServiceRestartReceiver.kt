package com.leowalk.LyricFocus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.leowalk.LyricFocus.service.LyricService

class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
        const val ACTION_RESTART_SERVICE = "com.leowalk.LyricFocus.action.RESTART_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESTART_SERVICE -> {
                Log.d(TAG, "Received restart service intent")
                restartServices(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed, restarting services")
                restartServices(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present, checking services")
                restartServices(context)
            }
        }
    }

    private fun restartServices(context: Context) {
        try {
            val lyricIntent = Intent(context, LyricService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(lyricIntent)
            } else {
                context.startService(lyricIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart services", e)
        }
    }
}