package com.pancake.callApp

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.pancake.callApp.database.PancakeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PancakeBackgroundService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val context = this
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    PancakeDatabase.pushListCallNonPushed(context)
                }
                mainHandler.postDelayed(this, 300000)
            }
        })


        return START_STICKY // If the service is killed, it will be automatically restarted
    }

}