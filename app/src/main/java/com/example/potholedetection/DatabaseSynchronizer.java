package com.example.potholedetection;

import com.google.firebase.firestore.FirebaseFirestore;
import java.util.UUID;

public class DatabaseSynchronizer {
    private FirebaseFirestore db;

    public DatabaseSynchronizer() {
        db = FirebaseFirestore.getInstance();
    }

    public void queueEvent(long timestamp, double latitude, double longitude, String algorithmTriggered, float severityValue) {
        String eventId = UUID.randomUUID().toString();
        PotholeEvent event = new PotholeEvent(eventId, timestamp, latitude, longitude, algorithmTriggered, severityValue);

        db.collection("pothole_events").document(event.getEventId())
            .set(event)
            .addOnSuccessListener(aVoid -> {
                System.out.println("Pothole event synced to Firebase successfully.");
            })
            .addOnFailureListener(e -> {
                System.out.println("Error writing pothole event to Firebase: " + e.getMessage());
            });
    }
}
