/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pancake.callApp.HeatBeatWorker
import com.pancake.callApp.PancakeBackgroundService
import com.pancake.callApp.UploadCallWorker
import java.util.concurrent.TimeUnit

class DirectBootMigrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        context.startService(Intent(context, DirectBootMigrationService::class.java))
        val uploadCallWorker = OneTimeWorkRequestBuilder<UploadCallWorker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        val heatBeatWorker = OneTimeWorkRequestBuilder<HeatBeatWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueue(arrayListOf(uploadCallWorker, heatBeatWorker))
    }
}
