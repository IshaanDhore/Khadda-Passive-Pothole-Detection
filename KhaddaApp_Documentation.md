# Khadda App - Project Documentation

## 1. Introduction
**Khadda** is a pothole detection application developed as part of a PBL III project. Its primary goal is to automatically detect potholes and road anomalies using smartphone sensors (accelerometer and gyroscope) while a user is driving. By logging these events with GPS coordinates, the app helps map road conditions and facilitates better road maintenance planning.

---

## 2. Product Requirements Document (PRD)

### 2.1 Problem Statement
Poor road conditions and potholes cause vehicle damage, reduce driving comfort, and lead to accidents. There is a lack of real-time, crowdsourced data on road quality to help authorities repair roads efficiently and alert drivers to hazards.

### 2.2 Objectives
- Develop an Android application to detect potholes automatically using built-in smartphone sensors.
- Record the geographical location (GPS) and severity of each detected pothole.
- Store the data locally and display the logged anomalies on a map interface.

### 2.3 Target Audience
- **Commuters/Drivers:** To be aware of bad road patches (future capability).
- **Municipal Authorities:** To prioritize road maintenance based on severity and frequency data.

### 2.4 Key Features
- **Background Tracking:** Uses a Foreground Service to monitor sensors continuously even when the app is minimized.
- **Sensor Data Collection:** Samples triaxial accelerometer and gyroscope data at a high frequency (50Hz).
- **Real-time Detection:** Analyzes sensor streams on-the-fly to detect anomalies.
- **Mapping:** Visualizes logged potholes on an interactive map.
- **Simulation Mode:** Includes a simulation manager to test detection algorithms without driving.

### 2.5 Non-Functional Requirements
- **Performance:** Sensor processing should run efficiently in a background thread to prevent UI freezing.
- **Battery Efficiency:** Sensor sampling and GPS updates should be optimized to reduce battery drain.
- **Offline Capability:** The app must be able to log potholes locally when internet connectivity is unavailable.

---

## 3. Technologies Used
- **Platform:** Android (Minimum SDK supported by modern standards)
- **Language:** Kotlin
- **Architecture:** MVVM (Model-View-ViewModel) for clean separation of concerns.
- **Local Database:** Room Persistence Library (SQLite wrapper) for storing pothole events.
- **Mapping:** osmdroid (OpenStreetMap) for rendering maps and markers without relying on Google Play Services billing.
- **Concurrency:** Kotlin Coroutines and Flows for asynchronous tasks and sensor data streaming.
- **Location Services:** Android Location API / FusedLocationProvider for high-accuracy GPS tracking.

---

## 4. Pothole Detection Algorithms
The detection engine is inspired by established research (such as *Mednis et al.*) and analyzes the Z-axis (vertical) acceleration to identify road anomalies.

1. **Z-THRESH (Z-Threshold):**
   - **Logic:** Checks if the absolute value of the Z-axis acceleration exceeds a predefined threshold.
   - **Use Case:** Identifies sudden, large vertical bumps or drops.

2. **Z-DIFF (Z-Difference):**
   - **Logic:** Calculates the difference between consecutive Z-axis readings. If the change is abrupt and exceeds a threshold, an anomaly is flagged.
   - **Use Case:** Good for detecting sharp, jarring impacts regardless of the baseline gravity.

3. **STDEV(Z) (Standard Deviation of Z):**
   - **Logic:** Computes the standard deviation of the Z-axis over a sliding time window. High variance indicates a rough road patch.
   - **Use Case:** Differentiates between a single bump and a continuously bad road surface.

4. **G-ZERO (Free-fall Detection):**
   - **Logic:** Triggers when the magnitude of all three axes (X, Y, Z) briefly drops near zero.
   - **Use Case:** Often occurs when a vehicle drops into a deep pothole, experiencing momentary free-fall.

**False-Positive Filtering:** 
The application applies filters using speed data and the gyroscope (e.g., ignoring bumps that occur during sharp turns or phone handling) to increase detection accuracy.

---

## 5. Current Limitations
1. **Phone Placement:** The current algorithms assume the phone is relatively stable (e.g., mounted on a dashboard or lying flat). Excessive manual handling of the phone will generate false positives.
2. **Sensor Calibration:** Lack of auto-calibration means if the phone is placed at a tilt, the Z-axis might not perfectly align with true vertical gravity.
3. **Speed Breakers:** Simple thresholding struggles to differentiate between a large pothole and a standard speed bump.
4. **GPS Accuracy:** Location accuracy depends heavily on the device hardware and can degrade in urban canyons or under dense tree cover.

---

## 6. Future Roadmap (What's Next?)
1. **Machine Learning Integration:** Replace or augment threshold-based algorithms with ML models (e.g., Random Forest, Neural Networks) trained on labelled road data for higher accuracy.
2. **Cloud Synchronization (Firebase):** Sync local pothole data to a centralized server to build a crowdsourced roadmap and heatmaps for the city.
3. **Dynamic Auto-Calibration:** Implement algorithms (like PCA - Principal Component Analysis) to dynamically re-orient the sensor axis based on the gravity vector, regardless of phone orientation.
4. **User Verification UI:** Allow users to manually verify or reject automatically detected potholes and upload images.
5. **Driver Alerts:** Provide voice/audio alerts when the driver is approaching a known bad road section or large pothole.

---

## 7. App Screenshots
*(Please insert screenshots of the app below before submitting or finalizing your report)*

**7.1 Home/Dashboard Screen**
*(Insert Screenshot Here - showing the start/stop tracking button and summary stats)*

**7.2 Active Tracking (Foreground Service Notification)**
*(Insert Screenshot Here - showing the persistent notification while tracking is active)*

**7.3 Map View**
*(Insert Screenshot Here - showing the osmdroid map with markers indicating detected potholes)*

**7.4 Settings / Threshold Configuration**
*(Insert Screenshot Here - showing the screen where Z-DIFF, Z-THRESH algorithms can be tuned)*

**7.5 Simulation / Log Screen**
*(Insert Screenshot Here - showing the simulated data or list of logged anomalies)*
