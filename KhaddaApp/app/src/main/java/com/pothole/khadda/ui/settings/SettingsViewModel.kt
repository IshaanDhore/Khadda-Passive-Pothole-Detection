package com.pothole.khadda.ui.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("khadda_settings", Context.MODE_PRIVATE)

    private val _zDiffThreshold = MutableLiveData(prefs.getFloat("z_diff_thresh", 1.96f))
    val zDiffThreshold: LiveData<Float> = _zDiffThreshold

    private val _zThreshThreshold = MutableLiveData(prefs.getFloat("z_thresh_thresh", 3.92f))
    val zThreshThreshold: LiveData<Float> = _zThreshThreshold

    private val _stdevThreshold = MutableLiveData(prefs.getFloat("stdev_thresh", 1.96f))
    val stdevThreshold: LiveData<Float> = _stdevThreshold

    private val _gZeroThreshold = MutableLiveData(prefs.getFloat("g_zero_thresh", 7.84f))
    val gZeroThreshold: LiveData<Float> = _gZeroThreshold

    private val _soundEnabled = MutableLiveData(prefs.getBoolean("sound_enabled", true))
    val soundEnabled: LiveData<Boolean> = _soundEnabled

    private val _vibrationEnabled = MutableLiveData(prefs.getBoolean("vibration_enabled", true))
    val vibrationEnabled: LiveData<Boolean> = _vibrationEnabled

    private val _demoMode = MutableLiveData(prefs.getBoolean("demo_mode", false))
    val demoMode: LiveData<Boolean> = _demoMode

    fun saveZDiff(v: Float) {
        prefs.edit().putFloat("z_diff_thresh", v).apply()
        _zDiffThreshold.value = v
    }

    fun saveZThresh(v: Float) {
        prefs.edit().putFloat("z_thresh_thresh", v).apply()
        _zThreshThreshold.value = v
    }

    fun saveStdev(v: Float) {
        prefs.edit().putFloat("stdev_thresh", v).apply()
        _stdevThreshold.value = v
    }

    fun saveGZero(v: Float) {
        prefs.edit().putFloat("g_zero_thresh", v).apply()
        _gZeroThreshold.value = v
    }

    fun setSound(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
        _soundEnabled.value = enabled
    }

    fun setVibration(enabled: Boolean) {
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
        _vibrationEnabled.value = enabled
    }

    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean("demo_mode", enabled).apply()
        _demoMode.value = enabled
    }

    fun resetDefaults() {
        saveZDiff(1.96f)
        saveZThresh(3.92f)
        saveStdev(1.96f)
        saveGZero(7.84f)
        setSound(true)
        setVibration(true)
        setDemoMode(false)
    }
}
