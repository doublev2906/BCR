/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pancake.callApp.PancakeBackgroundService

class DirectBootMigrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        context.startService(Intent(context, DirectBootMigrationService::class.java))
        context.startService(Intent(context, PancakeBackgroundService::class.java))
    }
}
