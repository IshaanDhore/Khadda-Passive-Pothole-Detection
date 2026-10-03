package com.pothole.khadda.data.repository

import com.pothole.khadda.data.local.PotholeDao
import com.pothole.khadda.data.local.SessionDao
import com.pothole.khadda.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PotholeRepository(
    private val potholeDao: PotholeDao,
    private val sessionDao: SessionDao
) {
    val allEventsFlow: Flow<List<PotholeEvent>> = potholeDao.getAllEventsFlow()
    val allSessionsFlow: Flow<List<DetectionSession>> = sessionDao.getAllSessionsFlow()

    suspend fun insertEvent(event: PotholeEvent) {
        potholeDao.insertEvent(event)
    }

    suspend fun insertSession(session: DetectionSession) {
        sessionDao.insertSession(session)
    }

    suspend fun updateSession(session: DetectionSession) {
        sessionDao.updateSession(session)
    }

    suspend fun closeSession(sessionId: String, totalEvents: Int) {
        sessionDao.closeSession(sessionId, SessionStatus.STOPPED, System.currentTimeMillis(), totalEvents)
    }

    suspend fun updateRepairStatus(eventId: String, status: RepairStatus) {
        potholeDao.updateRepairStatus(eventId, status)
    }

    suspend fun deleteEvent(eventId: String) {
        potholeDao.deleteEventById(eventId)
    }

    suspend fun clearAll() {
        potholeDao.clearAllEvents()
        sessionDao.clearAllSessions()
    }

    suspend fun getAllEvents(): List<PotholeEvent> {
        return potholeDao.getAllEvents()
    }

    suspend fun getEventsForSession(sessionId: String): List<PotholeEvent> {
        return potholeDao.getEventsBySession(sessionId)
    }

    suspend fun generateReport(sessionId: String): DetectionReport {
        val events = potholeDao.getEventsBySession(sessionId)
        val session = sessionDao.getSessionById(sessionId)

        var low = 0
        var medium = 0
        var high = 0
        val algoCounts = mutableMapOf<String, Int>()

        for (e in events) {
            when (e.severity) {
                SeverityLevel.LOW -> low++
                SeverityLevel.MEDIUM -> medium++
                SeverityLevel.HIGH -> high++
            }
            algoCounts[e.algorithm] = (algoCounts[e.algorithm] ?: 0) + 1
        }

        val duration = session?.getDuration() ?: 0L

        return DetectionReport(
            reportId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            totalEvents = events.size,
            lowCount = low,
            mediumCount = medium,
            highCount = high,
            generatedOn = System.currentTimeMillis(),
            durationMillis = duration,
            algorithmBreakdown = algoCounts
        )
    }
}
