package com.example.potholedetection;

public class PotholeEvent {
    private String eventId;
    private long timestamp;
    private double latitude;
    private double longitude;
    private String algorithmTriggered;
    private float severityValue;

    public PotholeEvent() {
        this.eventId = "";
        this.timestamp = 0;
        this.latitude = 0.0;
        this.longitude = 0.0;
        this.algorithmTriggered = "";
        this.severityValue = 0.0f;
    }

    public PotholeEvent(String eventId, long timestamp, double latitude, double longitude, String algorithmTriggered, float severityValue) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.algorithmTriggered = algorithmTriggered;
        this.severityValue = severityValue;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getAlgorithmTriggered() { return algorithmTriggered; }
    public void setAlgorithmTriggered(String algorithmTriggered) { this.algorithmTriggered = algorithmTriggered; }

    public float getSeverityValue() { return severityValue; }
    public void setSeverityValue(float severityValue) { this.severityValue = severityValue; }
}
