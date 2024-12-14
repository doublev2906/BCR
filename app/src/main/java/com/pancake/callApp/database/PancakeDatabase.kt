package com.pancake.callApp.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.pancake.callApp.PancakePreferences
import com.pancake.callApp.network.APIClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.awaitResponse
import java.io.File
import java.net.URLDecoder


@Database(entities = [CallLog::class, CallEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao
}

object PancakeDatabase {

    private const val TAG = "PancakeDatabase"
    private var appDatabase: AppDatabase? = null

    fun init(context: Context) {
        appDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java, "pancake_database"
        ).build()
    }

    suspend fun pushListCallNonPushed(context: Context) {
        val callLogDao = appDatabase?.callLogDao()
        val callLogs =
            callLogDao?.getUnPushedCallLogs(PancakePreferences(context).lastTimeLogsPushed)
        Log.d(TAG, "pushListCallNonPushed: ${PancakePreferences(context).lastTimeLogsPushed}")
        Log.d(TAG, "pushListCallNonPushed: ${callLogs?.size}")
        if (callLogs.isNullOrEmpty()) return
        for (c in callLogs) {
            val response = pushCallToServer(
                c.callLog.id,
                c.callLog,
                c.calls.map {
                    Call(
                        it.phoneNumber,
                        it.phoneNumberFormatted,
                        it.phoneNumber,
                        it.phoneNumberFormatted
                    )
                })
            if (response) {
                PancakePreferences(context).lastTimeLogsPushed = c.callLog.timestampUnixMs
            } else {
                break
            }
        }
    }

    suspend fun getAllCallLogs(page: Int = 0, limit: Int = 30): List<CallLogWithCalls> {
        val callLogDao = appDatabase?.callLogDao() ?: return emptyList()
        val allCallLogs = callLogDao.getAllCallLogs(page, limit)
        return allCallLogs
    }

    suspend fun insertRecordFromJson(jsonString: String) {
        val callLogDao = appDatabase?.callLogDao() ?: return
        withContext(Dispatchers.IO) {
            val gson = Gson()

            // Parse the main JSON object
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

            // Extract data from JSON and map it to the `CallLog` data class
            val callLog = CallLog(
                timestampUnixMs = jsonObject["timestamp_unix_ms"].asLong,
                timestamp = jsonObject["timestamp"].asString,
                direction = jsonObject["direction"]?.asString,
                simSlot = parseJsonNull(jsonObject, "sim_slot", Int::class.java),
                callLogName = parseJsonNull(jsonObject, "call_log_name", String::class.java),
                isPushToServer = jsonObject["is_push_to_server"].asBoolean,
                output = jsonObject["output"]?.let { outputJson ->
                    Output(
                        format = outputJson.asJsonObject["format"]?.let { formatJson ->
                            Format(
                                type = formatJson.asJsonObject["type"]?.asString,
                                mimeTypeContainer = formatJson.asJsonObject["mime_type_container"]?.asString,
                                mimeTypeAudio = formatJson.asJsonObject["mime_type_audio"]?.asString,
                                parameterType = formatJson.asJsonObject["parameter_type"]?.asString,
                                parameter = formatJson.asJsonObject["parameter"]?.asInt
                            )
                        },
                        recordFile = outputJson.asJsonObject["record_file"]?.asString,
                        recording = outputJson.asJsonObject["recording"]?.let { recordingJson ->
                            Recording(
                                framesTotal = recordingJson.asJsonObject["frames_total"]?.asInt,
                                framesEncoded = recordingJson.asJsonObject["frames_encoded"]?.asInt,
                                sampleRate = recordingJson.asJsonObject["sample_rate"]?.asInt,
                                channelCount = recordingJson.asJsonObject["channel_count"]?.asInt,
                                durationSecsTotal = recordingJson.asJsonObject["duration_secs_total"]?.asDouble,
                                durationSecsEncoded = recordingJson.asJsonObject["duration_secs_encoded"]?.asDouble,
                                bufferFrames = recordingJson.asJsonObject["buffer_frames"]?.asInt,
                                bufferOverruns = recordingJson.asJsonObject["buffer_overruns"]?.asInt,
                                wasEverPaused = recordingJson.asJsonObject["was_ever_paused"]?.asBoolean,
                                wasEverHolding = recordingJson.asJsonObject["was_ever_holding"]?.asBoolean
                            )
                        }
                    )
                }
            )

            // Extract calls array and map it to List of `Call` objects
            val calls = jsonObject["calls"]?.asJsonArray?.map { callJsonElement ->
                val callJson = callJsonElement.asJsonObject
                Call(
                    phoneNumber = parseJsonNull(callJson, "phone_number", String::class.java),
                    phoneNumberFormatted = parseJsonNull(
                        callJson,
                        "phone_number_formatted",
                        String::class.java
                    ),
                    callerName = parseJsonNull(callJson, "caller_name", String::class.java),
                    contactName = parseJsonNull(callJson, "contact_name", String::class.java)
                )
            } ?: emptyList()

            // Insert into the database
            val callId = callLogDao.insertFullCallLog(callLog, calls)
            pushCallToServer(callId, callLog, calls)
        }
    }

    private fun <T> parseJsonNull(jsonElement: JsonObject, key: String, clazz: Class<T>): T? {
        val element = jsonElement.get(key)
        if (element.isJsonNull) return null

        return when (clazz) {
            Int::class.java -> element.asInt as T
            String::class.java -> element.asString as T
            Boolean::class.java -> element.asBoolean as T
            Double::class.java -> element.asDouble as T
            Float::class.java -> element.asFloat as T
            Long::class.java -> element.asLong as T
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }

    private fun createPartFromString(value: String): RequestBody {
        return value.toRequestBody("text/plain".toMediaTypeOrNull())
    }

    private suspend fun pushCallToServer(id: Long, callLog: CallLog, calls: List<Call>): Boolean {
        var path = callLog.output?.recordFile?.replace("file://", "")
        val mimeType = callLog.output?.format?.mimeTypeAudio ?: "audio/wav"
        val filePart = path?.let {
            val formatPath = URLDecoder.decode(path, "UTF-8")
            val file = File(formatPath)
            val requestFile =  file.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("file", file.name, requestFile)
        }
        
        try {
            val response = APIClient.client.uploadCallRecordings(
                createPartFromString(callLog.direction ?: ""),
                createPartFromString(calls.firstOrNull()?.phoneNumber ?: ""),
                createPartFromString(
                    callLog.output?.recording?.durationSecsTotal?.toString() ?: ""
                ),
                createPartFromString(callLog.timestampUnixMs.div(1000).toString()),
                createPartFromString(mimeType),
                filePart
            ).awaitResponse()
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "pushCallToServer: Success")
                appDatabase?.callLogDao().let {
                    it?.markCallLogAsPushed(id);
                }
                return true
            } else {
                Log.e(TAG, "pushCallToServer: ${response.body()}")
            }
            Log.d(TAG, "pushCallToServer: ${response.body()}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "pushCallToServer: $e")
            return false
        }

        return false
    }


}