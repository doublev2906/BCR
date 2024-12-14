package com.pancake.callApp.database

import androidx.room.*

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalls(calls: List<CallEntity>)

    @Transaction
    suspend fun insertFullCallLog(callLog: CallLog, calls: List<Call>) : Long {
        val callLogId = insertCallLog(callLog)
        val callEntities = calls.map {
            CallEntity(
                callLogId = callLogId,
                phoneNumber = it.phoneNumber,
                phoneNumberFormatted = it.phoneNumberFormatted,
                callerName = it.callerName,
                contactName = it.contactName
            )
        }
        insertCalls(callEntities)
        return callLogId
    }

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getCallLog(id: Long): CallLog?

    @Query("SELECT * FROM calls WHERE call_log_id = :callLogId")
    suspend fun getCallsForCallLog(callLogId: Long): List<CallEntity>

    @Transaction
    @Query("SELECT * FROM call_logs ORDER BY timestamp_unix_ms DESC LIMIT :limit OFFSET :page * :limit")
    suspend fun getAllCallLogs(page: Int = 0, limit: Int = 30): List<CallLogWithCalls>
    
//    @Query("SELECT * FROM call_logs WHERE is_push_to_server = 0 ORDER BY timestamp_unix_ms DESC")
//    suspend fun getUnPushedCallLogs(): List<CallLog>
    
    @Query("SELECT * FROM call_logs WHERE is_push_to_server = 0 AND timestamp_unix_ms > :lastTimeCheck ORDER BY timestamp_unix_ms DESC")
    suspend fun getUnPushedCallLogs(lastTimeCheck: Long = 0): List<CallLogWithCalls>
    
    @Transaction
    @Query("UPDATE call_logs SET is_push_to_server = 1 WHERE id = :callLogId")
    suspend fun markCallLogAsPushed(callLogId: Long)
}
