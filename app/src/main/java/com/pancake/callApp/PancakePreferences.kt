package com.pancake.callApp

import android.content.Context

class PancakePreferences(context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences(LOCAL_STORAGE_KEY, Context.MODE_PRIVATE)

    var listCallNonPush: List<String>
        get() = sharedPreferences.getStringSet(LIST_CALL_NON_PUSH, emptySet())?.toList()
            ?: emptyList()
        set(value) = sharedPreferences.edit().putStringSet(LIST_CALL_NON_PUSH, value.toSet())
            .apply()
    
    var listPhoneSuccess: List<String>
        get() = sharedPreferences.getStringSet(LIST_PHONE_SUCCESS, emptySet())?.toList()
            ?: emptyList()
        set(value) = sharedPreferences.edit().putStringSet(LIST_PHONE_SUCCESS, value.toSet())
            .apply()
    
    fun addPhoneSuccess(phone: String) {
        val list = listPhoneSuccess.toMutableList()
        list.add(phone)
        listPhoneSuccess = list
    }

    fun addCallNonPush(call: String) {
        val list = listCallNonPush.toMutableList()
        list.add(call)
        listCallNonPush = list
    }

    companion object {
        private const val LOCAL_STORAGE_KEY = "PANCAKE_LOCAL_STORAGE"
        private const val LIST_CALL_NON_PUSH = "LIST_CALL_NON_PUSH"
        private const val LIST_PHONE_SUCCESS = "LIST_PHONE_SUCCESS"
    }
}