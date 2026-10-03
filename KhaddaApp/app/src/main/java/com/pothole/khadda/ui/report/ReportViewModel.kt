package com.pothole.khadda.ui.report

import android.app.Application
import androidx.lifecycle.*
import com.pothole.khadda.KhaddaApplication
import com.pothole.khadda.model.DetectionReport
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.SeverityLevel
import kotlinx.coroutines.launch
import java.util.UUID

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as KhaddaApplication).repository

    val allEvents = repository.allEventsFlow.asLiveData()
    val allSessions = repository.allSessionsFlow.asLiveData()

    private val _currentReport = MutableLiveData<DetectionReport?>()
    val currentReport: LiveData<DetectionReport?> = _currentReport

    fun generateOverallReport(events: List<PotholeEvent>) {
        var low = 0
        var med = 0
        var high = 0
        val algoMap = mutableMapOf<String, Int>()

        for (e in events) {
            when (e.severity) {
                SeverityLevel.LOW -> low++
                SeverityLevel.MEDIUM -> med++
                SeverityLevel.HIGH -> high++
            }
            algoMap[e.algorithm] = (algoMap[e.algorithm] ?: 0) + 1
        }

        val report = DetectionReport(
            reportId = UUID.randomUUID().toString(),
            sessionId = "OVERALL",
            totalEvents = events.size,
            lowCount = low,
            mediumCount = med,
            highCount = high,
            generatedOn = System.currentTimeMillis(),
            durationMillis = 0L,
            algorithmBreakdown = algoMap
        )
        _currentReport.value = report
    }
}
