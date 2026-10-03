package com.pothole.khadda.ui.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.pothole.khadda.model.DetectionReport
import com.pothole.khadda.model.PotholeEvent
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * ReportExporter generates CSV and JSON export files for project evaluation,
 * lab assignments, and external sharing.
 */
object ReportExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun exportToCsv(context: Context, report: DetectionReport, events: List<PotholeEvent>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "khadda_report_${System.currentTimeMillis()}.csv")

        FileWriter(file).use { writer ->
            // Header
            writer.append("EventId,SessionId,Timestamp,Date,Latitude,Longitude,Impact_ms2,Severity,Algorithm,Status\n")
            for (e in events) {
                writer.append("\"${e.eventId}\",")
                writer.append("\"${e.sessionId}\",")
                writer.append("${e.timestamp},")
                writer.append("\"${dateFormat.format(Date(e.timestamp))}\",")
                writer.append("${e.latitude},")
                writer.append("${e.longitude},")
                writer.append("${e.zDiffValue},")
                writer.append("\"${e.severity}\",")
                writer.append("\"${e.algorithm}\",")
                writer.append("\"${e.status}\"\n")
            }
        }
        return file
    }

    fun exportToJson(context: Context, report: DetectionReport, events: List<PotholeEvent>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "khadda_report_${System.currentTimeMillis()}.json")

        val exportData = mapOf(
            "report" to report,
            "events" to events,
            "exportedAt" to System.currentTimeMillis()
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        FileWriter(file).use { writer ->
            gson.toJson(exportData, writer)
        }
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Pothole Report"))
    }
}
