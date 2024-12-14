package com.pancake.callApp

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.util.Log
import androidx.annotation.RequiresApi
import com.chiller3.bcr.extension.phoneNumber
import com.chiller3.bcr.output.CallMetadataCollector
import com.chiller3.bcr.output.OutputFile
import com.google.gson.Gson
import com.pancake.callApp.database.PancakeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.uuid.ExperimentalUuidApi


object PancakeHandleCall {
    private const val TAG = "PancakeHandleCall"
    private const val DIRECTORY_PATH = "/storage/emulated/0/Android/data/com.chiller3.bcr/files/"
    
    fun handleRecordCallSuccess(
        context: Context,
        file: OutputFile?,
        resultForPancake: Map<String, Any>
    ) {
        val mResult = (resultForPancake["meta_data"] as JSONObject).let {
            Gson().fromJson(it.toString(), HashMap::class.java)
        }.toMutableMap()
        Log.d(TAG, "handleRecordCallSuccess: ${mResult["output"]}")   
        val output = mResult["output"] as MutableMap<String, Any>
        output["record_file"] = file?.uri.toString()
        mResult["is_push_to_server"] = false
        CoroutineScope(Dispatchers.IO).launch {
            PancakeDatabase.insertRecordFromJson(JSONObject(mResult).toString())
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    fun handleMissedCall(context: Context, call: Call) {
        val direction = when (call.details?.callDirection) {
            Call.Details.DIRECTION_INCOMING -> "missed_in"
            Call.Details.DIRECTION_OUTGOING -> "missed_out"
            else -> null
        }

        val callMetadataCollector = CallMetadataCollector(context = context, parentCall = call)
        val json = callMetadataCollector.callMetadata.toJson(context)
        json.put("direction", direction)
        json.put("is_push_to_server", false)
        CoroutineScope(Dispatchers.IO).launch {
            PancakeDatabase.insertRecordFromJson(json.toString())
        }
//        val (callLogNumber, callLogName) = CallMetadataCollector(context = context, parentCall = call).getCallLogDetails(call.details, true)    
        Log.d(TAG, "handleMissedCall: ${callMetadataCollector.callMetadata}")
        val phoneNumber = call.details.phoneNumber
        Log.d(TAG, "phoneNumber: $phoneNumber direction: $direction")
        Log.d(TAG, "handleMissedCall: ${call.details}")
    }
    
    fun pushCallToServer(call: MutableMap<Any, Any>) {
        
        
    }

}