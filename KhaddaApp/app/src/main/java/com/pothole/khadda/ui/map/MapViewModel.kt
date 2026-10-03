package com.pothole.khadda.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.pothole.khadda.KhaddaApplication

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KhaddaApplication).repository
    val allEvents = repository.allEventsFlow.asLiveData()
}
