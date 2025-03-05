package com.pancake.callApp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PancakeBackgroundService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val workRequest = PeriodicWorkRequestBuilder<AdbWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueue(workRequest)
        val context = this
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    PancakeHandleCall.pushListCallToServer(context)
                }
                mainHandler.postDelayed(this, 300000)
            }
        })


        return START_STICKY // If the service is killed, it will be automatically restarted
    }

}