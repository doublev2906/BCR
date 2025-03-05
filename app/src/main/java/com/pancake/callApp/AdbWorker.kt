package com.pancake.callApp

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.chiller3.bcr.Preferences
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class AdbWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val pref = Preferences(applicationContext)
        if (!pref.isRestartRilDaemon) {
            return Result.success()
        }
        return try {
            // Use 'su' to run the command as root
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)

            // Replace with your ADB command
            val command = "stop ril-daemon && sleep 5 && start ril-daemon\n" // Example: List data directory (requires root)
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            outputStream.close()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()

            var line: String? = reader.readLine()
            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }

            process.waitFor()
            Log.d("ADB_WORKER", "Root Command Output:\n$output")

            if (process.exitValue() == 0) {
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("ADB_WORKER", "Error executing root ADB command", e)
            
            Result.failure()
        }
    }
}
