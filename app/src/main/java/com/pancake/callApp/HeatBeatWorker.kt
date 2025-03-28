package com.pancake.callApp

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.pancake.callApp.network.APIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.awaitResponse
import java.util.concurrent.TimeUnit

class HeatBeatWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val androidID = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = APIClient.client.checkHeartbeat(id = androidID, name = android.os.Build.MODEL).awaitResponse()
            } catch (e: Exception) {
                Log.e("HeatBeatWorker", "Error checking heartbeat: ${e.message}")
            }
        }

        // Re-enqueue the worker
        val nextWorkRequest = OneTimeWorkRequestBuilder<HeatBeatWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES) // Delay for 1 minute
            .build()

        WorkManager.getInstance(applicationContext).enqueue(nextWorkRequest)

        return Result.success()
    }
}