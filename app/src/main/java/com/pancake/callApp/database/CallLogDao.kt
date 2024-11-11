package com.pancake.callApp.database

import androidx.room.*

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalls(calls: List<CallEntity>)

    @Transaction
    suspend fun insertFullCallLog(callLog: CallLog, calls: List<Call>) {
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
    }

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getCallLog(id: Long): CallLog?

    @Query("SELECT * FROM calls WHERE call_log_id = :callLogId")
    suspend fun getCallsForCallLog(callLogId: Long): List<CallEntity>

    @Transaction
    @Query("SELECT * FROM call_logs ORDER BY timestamp_unix_ms DESC LIMIT :limit OFFSET :page * :limit")
    suspend fun getAllCallLogs(page: Int = 0, limit: Int = 30): List<CallLogWithCalls>
}
