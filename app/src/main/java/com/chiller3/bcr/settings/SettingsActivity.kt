/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import androidx.fragment.app.Fragment
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.chiller3.bcr.PreferenceBaseActivity
import com.chiller3.bcr.R
import com.pancake.callApp.AdbWorker
import java.util.concurrent.TimeUnit

class SettingsActivity : PreferenceBaseActivity() {
    override val titleResId: Int = R.string.app_name_full

    override val showUpButton: Boolean = false

    override fun createFragment(): Fragment = SettingsFragment()
}
