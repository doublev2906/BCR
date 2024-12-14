package com.pancake.callApp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pancake.callApp.model.User

class PancakePreferences(context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences(LOCAL_STORAGE_KEY, Context.MODE_PRIVATE)

    var accessToken: String? = null
        get() {
            if (field == null) {
                field = sharedPreferences.getString(USER_TOKEN, null)
            }
            return field
        }
        set(value) {
            if (value == null) {
                sharedPreferences.edit().remove(USER_TOKEN).apply()
            } else {
                sharedPreferences.edit().putString(USER_TOKEN, value).apply(); field = value
            }
        }

    var user: User? = null
        get() {
            if (field == null) {
                val json = sharedPreferences.getString("USER", null)
                if (json != null) {
                    try {
                        field = Gson().fromJson(json, User::class.java)
                    } catch (e: JsonSyntaxException) {
                        Log.e(TAG, "Error parsing user json", e)
                    }
                }
            }
            return field
        }
        set(value) {
            if (value == null) {
                sharedPreferences.edit().remove("USER").apply()
            } else {
                sharedPreferences.edit().putString("USER", Gson().toJson(value)).apply(); field = value
            }
        }

    var lastTimeLogsPushed: Long
        get() = sharedPreferences.getLong(LAST_TIME_LOGS_PUSHED, 0)
        set(value) = sharedPreferences.edit().putLong(LAST_TIME_LOGS_PUSHED, value).apply()

    companion object {
        private const val LOCAL_STORAGE_KEY = "PANCAKE_LOCAL_STORAGE"
        private const val TAG = "PANCAKE_PREFS"
        private const val USER_TOKEN = "USER_TOKEN"
        private const val LAST_TIME_LOGS_PUSHED = "LAST_TIME_LOGS_PUSHED"
    }
}