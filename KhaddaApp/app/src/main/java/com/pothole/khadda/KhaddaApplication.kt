package com.pothole.khadda

import android.app.Application
import com.pothole.khadda.data.local.AppDatabase
import com.pothole.khadda.data.repository.PotholeRepository
import org.osmdroid.config.Configuration

class KhaddaApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { PotholeRepository(database.potholeDao(), database.sessionDao()) }

    override fun onCreate() {
        super.onCreate()
        // Initialize osmdroid configuration with application user-agent
        Configuration.getInstance().userAgentValue = packageName
    }
}
