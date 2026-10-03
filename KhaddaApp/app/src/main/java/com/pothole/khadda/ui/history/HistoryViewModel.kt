package com.pothole.khadda.ui.history

import android.app.Application
import androidx.lifecycle.*
import com.pothole.khadda.KhaddaApplication
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.RepairStatus
import com.pothole.khadda.model.SeverityLevel
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as KhaddaApplication).repository

    val allEvents = repository.allEventsFlow.asLiveData()

    fun updateRepairStatus(eventId: String, status: RepairStatus) {
        viewModelScope.launch {
            repository.updateRepairStatus(eventId, status)
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
