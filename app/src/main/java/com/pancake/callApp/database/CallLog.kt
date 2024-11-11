package com.pancake.callApp.database

import androidx.room.*

@Entity(tableName = "call_logs")
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp_unix_ms") val timestampUnixMs: Long,    
    @ColumnInfo(name = "is_push_to_server") val isPushToServer: Boolean,    
    @ColumnInfo(name = "timestamp") val timestamp: String,
    @ColumnInfo(name = "direction") val direction: String?,
    @ColumnInfo(name = "sim_slot") val simSlot: Int?,
    @ColumnInfo(name = "call_log_name") val callLogName: String?,
    @Embedded(prefix = "output_") val output: Output?
)

data class Call(
    @ColumnInfo(name = "phone_number") val phoneNumber: String?,
    @ColumnInfo(name = "phone_number_formatted") val phoneNumberFormatted: String?,
    @ColumnInfo(name = "caller_name") val callerName: String?,
    @ColumnInfo(name = "contact_name") val contactName: String?
)

data class Output(
    @Embedded(prefix = "format_") val format: Format?,
    @Embedded(prefix = "recording_") val recording: Recording?,
    @ColumnInfo(name = "record_file") val recordFile: String?,
)

data class Format(
    @ColumnInfo(name = "type") val type: String?,
    @ColumnInfo(name = "mime_type_container") val mimeTypeContainer: String?,
    @ColumnInfo(name = "mime_type_audio") val mimeTypeAudio: String?,
    @ColumnInfo(name = "parameter_type") val parameterType: String?,
    @ColumnInfo(name = "parameter") val parameter: Int?
)

data class Recording(
    @ColumnInfo(name = "frames_total") val framesTotal: Int?,
    @ColumnInfo(name = "frames_encoded") val framesEncoded: Int?,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int?,
    @ColumnInfo(name = "channel_count") val channelCount: Int?,
    @ColumnInfo(name = "duration_secs_total") val durationSecsTotal: Double?,
    @ColumnInfo(name = "duration_secs_encoded") val durationSecsEncoded: Double?,
    @ColumnInfo(name = "buffer_frames") val bufferFrames: Int?,
    @ColumnInfo(name = "buffer_overruns") val bufferOverruns: Int?,
    @ColumnInfo(name = "was_ever_paused") val wasEverPaused: Boolean?,
    @ColumnInfo(name = "was_ever_holding") val wasEverHolding: Boolean?
)

@Entity(
    tableName = "calls",
    foreignKeys = [ForeignKey(
        entity = CallLog::class,
        parentColumns = ["id"],
        childColumns = ["call_log_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "call_log_id") val callLogId: Long,
    @ColumnInfo(name = "phone_number") val phoneNumber: String?,
    @ColumnInfo(name = "phone_number_formatted") val phoneNumberFormatted: String?,
    @ColumnInfo(name = "caller_name") val callerName: String?,
    @ColumnInfo(name = "contact_name") val contactName: String?
)

data class CallLogWithCalls(
    @Embedded val callLog: CallLog,
    @Relation(
        parentColumn = "id",
        entityColumn = "call_log_id"
    )
    val calls: List<CallEntity>
)

