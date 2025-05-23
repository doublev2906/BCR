package com.pancake.callApp

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
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
import kotlin.math.abs

data class CallRecordingBody(
    val id: String = System.currentTimeMillis().toString(),
    val direction: String,
    val phoneNumber: String,
    val originalPhoneNumber: String,
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
                androidId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ),
                outputFilePath = file?.uri?.toString(),
                originalPhoneNumber = phoneNumber
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
            androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ),
            outputFilePath = null,
            originalPhoneNumber = call.details.phoneNumber.toString()
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

    private suspend fun pushCallToServer(
        context: Context,
        body: CallRecordingBody,
        needSaveToRetry: Boolean = true,
        ignoreSaveCallSuccess: Boolean = false
    ): Boolean {
        try {
            val file = if (body.outputFilePath != null) {
                val path = body.outputFilePath.replace("file://", "")
                val formatPath = URLDecoder.decode(path, "UTF-8")
                File(formatPath)
            } else {
                null
            }

            val filePart: MultipartBody.Part? = if (file != null && file.exists()) {
                val requestFile = file.asRequestBody(body.fileType.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("file", file.name, requestFile)
            } else {
                null
            }

            val response = APIClient.client.uploadCallRecordings(
                data = body.toPartMap(),
                file = filePart
            ).awaitResponse();
            if (response.isSuccessful && response.body()?.success == true) {
                if (file?.exists() == true) {
                    file.delete()
                }
                if (!ignoreSaveCallSuccess) {
                    PancakePreferences(context).addPhoneSuccess("${body.originalPhoneNumber}_${body.timestamp}")
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
            checkDifferentCalls(context)
            var listCall = PancakePreferences(context).listCallNonPush.map {
                CallRecordingBody.fromJson(it)
            }
            if (listCall.isEmpty()) return
            val listCallPushFailed = mutableListOf<CallRecordingBody>()
            for (call in listCall) {
                if (call == null) continue
                val success = pushCallToServer(
                    context, call,
                    needSaveToRetry = false,
                    ignoreSaveCallSuccess = true
                )
                if (!success) {
                    listCallPushFailed.add(call)
                }
            }
            PancakePreferences(context).listCallNonPush = listCallPushFailed.map { it.toJson() }
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            Log.e(TAG, e.stackTraceToString())
        }
    }

    private fun getCallLogs(
        context: Context,
        startTime: Long,
        endTime: Long
    ): List<CallRecordingBody> {
        val callLogs = mutableListOf<CallRecordingBody>()

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        val selection = "${CallLog.Calls.DATE} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(startTime.toString(), endTime.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        val cursor: Cursor? = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)

            while (it.moveToNext()) {
                val number = it.getString(numberIndex)
                val timestamp = it.getLong(dateIndex)
                val duration = it.getInt(durationIndex)
                val direction = when (it.getInt(typeIndex)) {
                    CallLog.Calls.INCOMING_TYPE -> "in"
                    CallLog.Calls.OUTGOING_TYPE -> "out"
                    CallLog.Calls.MISSED_TYPE -> "missed_in"
                    else -> "missed_out"
                }

                callLogs.add(
                    CallRecordingBody(
                        direction = direction,
                        phoneNumber = formatPhoneNumber(number),
                        duration = duration.toString(),
                        timestamp = timestamp.div(1000).toString(),
                        androidId = Settings.Secure.getString(
                            context.contentResolver,
                            Settings.Secure.ANDROID_ID
                        ),
                        outputFilePath = null,
                        originalPhoneNumber = number
                    )
                )
            }
        }

        return callLogs
    }

    private fun isSameTime(
        time1: Long,
        time2: Long,
        threshold: Long = 1
    ): Boolean {
        return abs(time1 - time2) <= threshold
    }

    private fun checkDifferentCalls(context: Context) {
        val preferences = PancakePreferences(context)
        val listPhones = preferences.listPhoneSuccess.sortedBy {
            it.split("_")[1].toLong()
        }
        if (listPhones.isEmpty()) return;
        var startTime = listPhones.firstOrNull()?.split("_")?.get(1)?.toLong()
        startTime = if (startTime == null) {
            System.currentTimeMillis() - 24 * 60 * 60 * 1000
        } else {
            startTime * 1000
        }
        val callLogs = getCallLogs(context, startTime, System.currentTimeMillis())

        val listPhoneSuccessMap: HashMap<String, List<Long>> =
            listPhones.fold(HashMap()) { acc, phone ->
                val phoneNumber = phone.split("_")[0]
                val timestamp = phone.split("_")[1].toLong()
                acc[phoneNumber] = when {
                    acc[phoneNumber] == null -> listOf(timestamp)
                    acc[phoneNumber]?.contains(timestamp) == true -> acc[phoneNumber]!!
                    else -> acc[phoneNumber]!! + timestamp
                }
                acc
            }
        Log.d(TAG, listPhoneSuccessMap.toString())
        val listCallNonPush = preferences.listCallNonPush.map {
            CallRecordingBody.fromJson(it)
        }
        for (call in callLogs) {
            val listTime = listPhoneSuccessMap[call.originalPhoneNumber]

            val sameCallIsWaitPush = listCallNonPush.find {
                if (it == null) return@find false
                it.originalPhoneNumber == call.originalPhoneNumber && isSameTime(
                    call.timestamp.toLong(),
                    it.timestamp.toLong()
                )
            }
            if (sameCallIsWaitPush != null) continue

            if (listTime == null) {
                preferences.addCallNonPush(call.toJson())
                continue
            }
            val isSameCall = listTime.find {
                isSameTime(it, call.timestamp.toLong())
            }
            if (isSameCall != null) preferences.addCallNonPush(call.toJson())
        }

        preferences.listPhoneSuccess = listOf()
    }

}