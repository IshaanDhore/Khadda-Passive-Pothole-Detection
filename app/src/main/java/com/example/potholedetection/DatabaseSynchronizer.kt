package com.example.potholedetection

import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class DatabaseSynchronizer {
    private val db = FirebaseFirestore.getInstance()

    fun queueEvent(
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        algorithmTriggered: String,
        severityValue: Float
    ) {
        val event = PotholeEvent(
            eventId = UUID.randomUUID().toString(),
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            algorithmTriggered = algorithmTriggered,
            severityValue = severityValue
        )

        db.collection("pothole_events").document(event.eventId)
            .set(event)
            .addOnSuccessListener {
                println("Pothole event synced to Firebase successfully.")
            }
            .addOnFailureListener { e ->
                println("Error writing pothole event to Firebase: $e")
                // Firestore handles local queuing natively if offline persistence is enabled.
            }
    }
}
