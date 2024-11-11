package com.pancake.callApp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Database(entities = [CallLog::class, CallEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao
}

object PancakeDatabase {
    
    private const val TAG = "PancakeDatabase"
    private var appDatabase: AppDatabase? = null
    
    fun init(context: Context){
        appDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java, "pancake_database"
        ).build()
    }
    
    suspend fun getAllCallLogs(page: Int = 0, limit: Int = 30) : List<CallLogWithCalls> {
        val callLogDao = appDatabase?.callLogDao() ?: return emptyList()
        val allCallLogs = callLogDao.getAllCallLogs()
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
                    phoneNumberFormatted = parseJsonNull(callJson, "phone_number_formatted", String::class.java),
                    callerName =  parseJsonNull(callJson, "caller_name", String::class.java),
                    contactName = parseJsonNull(callJson, "contact_name", String::class.java)
                )
            } ?: emptyList()

            // Insert into the database
            callLogDao.insertFullCallLog(callLog, calls)
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
    
    
    
}