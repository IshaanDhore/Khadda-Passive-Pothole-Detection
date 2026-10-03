package com.pothole.khadda.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.pothole.khadda.R
import com.pothole.khadda.databinding.ActivityMainBinding
import com.pothole.khadda.ui.authority.AuthorityFragment
import com.pothole.khadda.ui.history.HistoryFragment
import com.pothole.khadda.ui.map.MapFragment
import com.pothole.khadda.ui.monitor.MonitorFragment
import com.pothole.khadda.ui.report.ReportFragment
import com.pothole.khadda.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val monitorFragment = MonitorFragment()
    private val mapFragment = MapFragment()
    private val historyFragment = HistoryFragment()
    private val authorityFragment = AuthorityFragment()
    private val reportFragment = ReportFragment()
    private val settingsFragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set default fragment
        if (savedInstanceState == null) {
            loadFragment(monitorFragment, "Khadda - Live Monitor")
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitor -> {
                    loadFragment(monitorFragment, "Khadda - Live Monitor")
                    true
                }
                R.id.nav_map -> {
                    loadFragment(mapFragment, "Road-Anomaly Map")
                    true
                }
                R.id.nav_history -> {
                    loadFragment(historyFragment, "Detection Log")
                    true
                }
                R.id.nav_authority -> {
                    loadFragment(authorityFragment, "Authority Dashboard")
                    true
                }
                R.id.nav_report -> {
                    loadFragment(reportFragment, "Analytics & Export")
                    true
                }
                else -> false
            }
        }

        binding.btnSettings.setOnClickListener {
            loadFragment(settingsFragment, "Algorithm Settings")
        }
    }

    private fun loadFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        binding.toolbar.title = title
    }
}
