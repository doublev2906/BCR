package com.pancake.callApp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pancake.callApp.network.APIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.awaitResponse
import java.util.concurrent.TimeUnit

class PancakeBackgroundService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PancakeBackgroundService", "Start service")
        val context = this
        val mainHandler = Handler(Looper.getMainLooper())
        val heartbeatHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    PancakeHandleCall.pushListCallToServer(context)
                }
                mainHandler.postDelayed(this, 300000)
            }
        })

        heartbeatHandler.post(object : Runnable {
            override fun run() {
                val androidID = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                CoroutineScope(Dispatchers.IO).launch {
                    try { 
                        val response = APIClient.client.checkHeartbeat(id = androidID, name = android.os.Build.MODEL).awaitResponse()
                        Log.d("PancakeBackgroundService", "Heartbeat response: ${response.body()}")
                    } catch (e: Exception) {
                        Log.e("PancakeBackgroundService", "Error checking heartbeat: ${e.message}")
                    }
                }
                heartbeatHandler.postDelayed(this, 60000)
            }
        })


        return START_NOT_STICKY // If the service is killed, it will be automatically restarted
    }

}