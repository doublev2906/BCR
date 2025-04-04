package com.pancake.callApp

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pancake.callApp.network.APIClient
import retrofit2.awaitResponse

class UploadCallWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            val androidID = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
            APIClient.client.checkHeartbeat(id = androidID, name = android.os.Build.MODEL).awaitResponse()
            PancakeHandleCall.pushListCallToServer(applicationContext)
        } catch (e: Exception) {
            Log.d("UploadCallWorker", "Error checking heartbeat: ${e.message}")
        }
        return Result.success()
    }
}