package com.pothole.khadda.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.RepairStatus
import com.pothole.khadda.model.SeverityLevel
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for PotholeEvent Entity.
 * Matches UML Class Diagram: LocalDatabase methods.
 */
@Dao
interface PotholeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: PotholeEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<PotholeEvent>)

    @Update
    suspend fun updateEvent(event: PotholeEvent)

    @Query("UPDATE pothole_events SET status = :status WHERE eventId = :eventId")
    suspend fun updateRepairStatus(eventId: String, status: RepairStatus)

    @Delete
    suspend fun deleteEvent(event: PotholeEvent)

    @Query("DELETE FROM pothole_events WHERE eventId = :eventId")
    suspend fun deleteEventById(eventId: String)

    @Query("DELETE FROM pothole_events")
    suspend fun clearAllEvents()

    @Query("SELECT * FROM pothole_events ORDER BY timestamp DESC")
    fun getAllEventsFlow(): Flow<List<PotholeEvent>>

    @Query("SELECT * FROM pothole_events ORDER BY timestamp DESC")
    fun getAllEventsLiveData(): LiveData<List<PotholeEvent>>

    @Query("SELECT * FROM pothole_events ORDER BY timestamp DESC")
    suspend fun getAllEvents(): List<PotholeEvent>

    @Query("SELECT * FROM pothole_events WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getEventsBySessionFlow(sessionId: String): Flow<List<PotholeEvent>>

    @Query("SELECT * FROM pothole_events WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getEventsBySession(sessionId: String): List<PotholeEvent>

    @Query("SELECT * FROM pothole_events WHERE severity = :severity ORDER BY timestamp DESC")
    fun getEventsBySeverityFlow(severity: SeverityLevel): Flow<List<PotholeEvent>>

    @Query("SELECT * FROM pothole_events WHERE status = :status ORDER BY timestamp DESC")
    fun getEventsByStatusFlow(status: RepairStatus): Flow<List<PotholeEvent>>

    @Query("SELECT COUNT(*) FROM pothole_events")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM pothole_events WHERE severity = :severity")
    suspend fun getCountBySeverity(severity: SeverityLevel): Int

    @Query("SELECT * FROM pothole_events WHERE syncStatus = 'QUEUED'")
    suspend fun getUnsyncedEvents(): List<PotholeEvent>

    @Query("UPDATE pothole_events SET syncStatus = 'SYNCED' WHERE eventId = :eventId")
    suspend fun markAsSynced(eventId: String)
}
