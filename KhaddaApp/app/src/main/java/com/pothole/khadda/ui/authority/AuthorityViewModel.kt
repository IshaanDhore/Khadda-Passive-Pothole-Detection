package com.pothole.khadda.ui.authority

import android.app.Application
import androidx.lifecycle.*
import com.pothole.khadda.KhaddaApplication
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.RepairStatus
import kotlinx.coroutines.launch

class AuthorityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as KhaddaApplication).repository

    val allEvents = repository.allEventsFlow.asLiveData()

    private val _filterStatus = MutableLiveData<RepairStatus?>(null)
    val filterStatus: LiveData<RepairStatus?> = _filterStatus

    fun setFilter(status: RepairStatus?) {
        _filterStatus.value = status
    }

    fun updateStatus(eventId: String, newStatus: RepairStatus) {
        viewModelScope.launch {
            repository.updateRepairStatus(eventId, newStatus)
        }
    }
}
