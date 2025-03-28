package com.pancake.callApp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telecom.Call
import android.util.Log
import androidx.annotation.RequiresApi
import com.chiller3.bcr.extension.phoneNumber
import com.chiller3.bcr.output.CallMetadataCollector
import com.chiller3.bcr.output.OutputFile
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pancake.callApp.network.APIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.awaitResponse
import java.io.File
import java.net.URLDecoder

data class CallRecordingBody(
    val id: String = System.currentTimeMillis().toString(),
    val direction: String,
    val phoneNumber: String,
    val duration: String,
    val timestamp: String,
    val androidId: String,
    val fileType: String = "audio/wav",
    val outputFilePath: String? 
) {
    fun toJson(): String {
        return Gson().toJson(this)
    }
    
    companion object {
        fun fromJson(json: String): CallRecordingBody? {
            return try {
                Gson().fromJson(json, CallRecordingBody::class.java)
            } catch (e: JsonSyntaxException) {
                println("Failed to parse JSON: ${e.message}")
                null
            }
        }
    }
}

fun CallRecordingBody.toPartMap(): Map<String, RequestBody> = mapOf(
    "direction" to direction.toRequestBody(),
    "phone_number" to phoneNumber.toRequestBody(),
    "duration" to duration.toRequestBody(),
    "timestamp" to timestamp.toRequestBody(),
    "file_type" to fileType.toRequestBody(),
    "android_id" to androidId.toRequestBody()
)


object PancakeHandleCall {
    private const val TAG = "PancakeHandleCall"
    
    @SuppressLint("HardwareIds")
    fun handleRecordCallSuccess(
        context: Context,
        file: OutputFile?,
        resultForPancake: Map<String, Any>
    ) {
        try {
            val result = JSONObject(resultForPancake)
            val metaData = result.getJSONObject("meta_data")
            val output = metaData.getJSONObject("output")
            Log.d(TAG, "handleRecordCallSuccess: $output")
            
            val phoneNumber = let {
                val calls = metaData.getJSONArray("calls")
                calls.getJSONObject(0).getString("phone_number")
            }
            val duration = let {
                val recording = output.getJSONObject("recording")
                recording.getDouble("duration_secs_total").toString()
            }
            val callRecordingBody = CallRecordingBody(
                direction = metaData.getString("direction"),
                phoneNumber = formatPhoneNumber(phoneNumber),
                duration = duration,
                timestamp = (metaData.getLong("timestamp_unix_ms").div(1000)).toString(),
                androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
                outputFilePath = file?.uri?.toString()
            )
            CoroutineScope(Dispatchers.IO).launch {
                pushCallToServer(context, callRecordingBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            Log.e(TAG, e.stackTraceToString())
        }
        
    }
    
    @SuppressLint("HardwareIds")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun handleMissedCall(context: Context, call: Call) {
        val direction = when (call.details?.callDirection) {
            Call.Details.DIRECTION_INCOMING -> "missed_in"
            Call.Details.DIRECTION_OUTGOING -> "missed_out"
            else -> "missed"
        }

        val callMetadataCollector = CallMetadataCollector(context = context, parentCall = call)
        Log.d(TAG, "handleMissedCall: ${callMetadataCollector.callMetadata}")

        val callRecordingBody = CallRecordingBody(
            direction = direction,
            phoneNumber = formatPhoneNumber(call.details.phoneNumber.toString()),
            duration = "0",
            timestamp = callMetadataCollector.callMetadata.timestamp.toEpochSecond().toString(),
            androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
            outputFilePath = null
        )
        CoroutineScope(Dispatchers.IO).launch {
            pushCallToServer(context, callRecordingBody)
        }
    }

    private fun formatPhoneNumber(phoneNumber: String): String {
        var mPhoneNumber = phoneNumber
        if (phoneNumber.startsWith("1599")) {
            mPhoneNumber = mPhoneNumber.replaceFirst("^1599".toRegex(), "0")
        }
        if (mPhoneNumber.length > 10) {
            mPhoneNumber = mPhoneNumber.substring(mPhoneNumber.length - 10)
        }
        return mPhoneNumber
    }
    
    private suspend fun pushCallToServer(context: Context, body: CallRecordingBody, needSaveToRetry: Boolean = true) : Boolean {
        try {
            val file = if (body.outputFilePath != null) {
                val path = body.outputFilePath.replace("file://", "")
                val formatPath = URLDecoder.decode(path, "UTF-8")
                File(formatPath)
            } else {
                null
            }
            
            if (file == null || !file.exists()) {
                Log.e(TAG, "pushCallToServer: file not found")
                return true
            }
            val requestFile = file.asRequestBody(body.fileType.toMediaTypeOrNull())
            val filePart: MultipartBody.Part?  = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = APIClient.client.uploadCallRecordings(
                data = body.toPartMap(),
                file = filePart
            ).awaitResponse();
            Log.d(TAG, "pushCallToServer: ${response.body()}")
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "pushCallToServer: Success")
                if (file?.exists() == true) {
                    file.delete()
                }
                return true
            } else {
                Log.d(TAG, "pushCallToServer: fail")
                if (needSaveToRetry) {
                    PancakePreferences(context).addCallNonPush(body.toJson())
                }
                return false
            }
        } catch (e: Exception) {
            if (needSaveToRetry) {
                PancakePreferences(context).addCallNonPush(body.toJson())
            }
            return false
        }

    }

    suspend fun pushListCallToServer(context: Context) {
        try {
            var listCall = PancakePreferences(context).listCallNonPush.map {
                CallRecordingBody.fromJson(it)
            }
            if (listCall.isEmpty()) return
            val listCallPushFailed = mutableListOf<CallRecordingBody>()
            for (call in listCall) {
                if (call == null) continue
                val success = pushCallToServer(context, call, false)
                if (!success) {
                    listCallPushFailed.add(call)
                }
            }
            PancakePreferences(context).listCallNonPush = listCallPushFailed.map { it.toJson() }
        }  catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            Log.e(TAG, e.stackTraceToString())
        }
    }

}