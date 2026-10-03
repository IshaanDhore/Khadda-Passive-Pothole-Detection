package com.pothole.khadda.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.pothole.khadda.model.DetectionSession
import com.pothole.khadda.model.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for DetectionSession Entity.
 * Matches UML Class Diagram: DetectionSession persistence.
 */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DetectionSession)

    @Update
    suspend fun updateSession(session: DetectionSession)

    @Query("SELECT * FROM detection_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<DetectionSession>>

    @Query("SELECT * FROM detection_sessions ORDER BY startTime DESC")
    fun getAllSessionsLiveData(): LiveData<List<DetectionSession>>

    @Query("SELECT * FROM detection_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): DetectionSession?

    @Query("UPDATE detection_sessions SET status = :status, endTime = :endTime, totalEvents = :totalEvents WHERE sessionId = :sessionId")
    suspend fun closeSession(sessionId: String, status: SessionStatus, endTime: Long, totalEvents: Int)

    @Delete
    suspend fun deleteSession(session: DetectionSession)

    @Query("DELETE FROM detection_sessions")
    suspend fun clearAllSessions()
}
