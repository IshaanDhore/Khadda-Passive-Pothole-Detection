# Khadda - Passive Pothole Detection App
**Comprehensive System & Processing Architecture**

## 1. Core Purpose
Khadda is an Android-based passive sensing application designed to detect, classify, and log road surface anomalies (potholes and speed breakers) using built-in smartphone sensors (Accelerometer, Gyroscope, Gravity, and GPS) while a user is driving or riding a motorcycle. 

The application operates entirely in the background, utilizing advanced signal processing and heuristic filtering to distinguish true road defects from normal engine vibrations, hard braking, or sharp turns.

---

## 2. System Architecture & Components

The application is built using a modern Android MVVM (Model-View-ViewModel) architecture and is split into several core engines:

### A. Data Collection Engine (`DetectionForegroundService` & `SensorCollector`)
*   **Foreground Service:** Ensures the app remains active and is not killed by the Android OS when the phone screen is locked or the app is minimized. It coordinates all sensors.
*   **100Hz Hardware Sampling:** The `SensorCollector` registers listeners for the device's Accelerometer, Gyroscope, Linear Acceleration, and Gravity sensors, requesting data at a high frequency (~100 samples per second).
*   **GPS Tracking (`LocationTracker`):** Requests high-accuracy location updates. It tracks the user's Latitude, Longitude, Bearing, and precise Speed.

### B. Signal Processing Pipeline (`DataProcessor`)
Because phones can be mounted at various angles on a motorcycle handlebar, the raw X, Y, and Z axes are meaningless. The `DataProcessor` solves this dynamically:
1.  **Gravity Projection:** It reads the Gravity sensor to determine exactly which way is "Down" relative to the earth.
2.  **Vertical Acceleration:** It takes the Linear Acceleration (movement without gravity) and mathematically projects it onto the Gravity vector. This isolates the pure **Vertical Shock** (Z-axis relative to the earth, not the phone).
3.  **Smoothing:** Applies a 5-sample moving average to remove high-frequency hardware noise.
4.  **Rolling Windows:** Maintains a rolling buffer of recent readings to calculate statistical variance (Standard Deviation).

### C. Detection Engine (`PotholeDetector`)
The app utilizes four distinct detection algorithms based on the scientific paper by Mednis et al. (2011). Each algorithm looks for a specific physical signature of a pothole:
1.  **Z-DIFF:** Measures the rapid, sudden change between consecutive vertical acceleration readings (e.g., suspension rapidly compressing).
2.  **Z-THRESH:** Measures absolute vertical deviation from the 1g baseline.
3.  **STDEV(Z):** Measures sustained elevated variance (useful for detecting rough, corrugated roads rather than single sharp impacts).
4.  **G-ZERO:** Looks for a momentary state of free-fall (0g) which occurs when a motorcycle wheel drops completely into a deep cavity before hitting the bottom.

### D. False Positive Filter (`FalsePositiveFilter`)
Even if the `PotholeDetector` flags an anomaly (called a "Candidate"), it must pass strict logical gates to prevent garbage data from being logged:
1.  **Speed Gate:** The GPS must report a speed > 10 km/h. This prevents handling noise, stopping at traffic lights, and stop-and-go traffic jitter from triggering potholes.
2.  **Hard Braking Gate:** Analyzes horizontal deceleration. If the bike is braking heavily (pitching forward), it generates vertical force that looks like a pothole. This gate blocks it.
3.  **Cornering Gate:** Analyzes the Gyroscope's Yaw rate. Sharp turns generate lateral g-forces that confuse the sensors; this gate blocks detections during cornering.
4.  **Cooldown & Duplicate Suppression:** Once a pothole is recorded, the system enforces a strict 1.5-second cooldown. Furthermore, any anomalies detected within 15 meters of the last pothole are suppressed to prevent one large crater from generating 10 separate database entries.

---

## 3. Data Logging & Telemetry

To allow for post-ride Machine Learning analysis and threshold tuning, the app logs every microsecond of the ride asynchronously. To prevent dropping frames in the 100Hz callback, logs are pushed to a `ConcurrentLinkedQueue` and written to disk on a background Coroutine.

**Four logs are generated per session:**
1.  **sensor_log.csv:** Raw 100Hz data including projected vertical acceleration, raw X/Y/Z, yaw/pitch/roll, and manual ground-truth labels.
2.  **gps_log.csv:** Location, speed, bearing, and accuracy.
3.  **candidate_log.csv:** The "Trash Can". Records every anomaly that triggered an algorithm but was rejected by the `FalsePositiveFilter`, detailing the exact reason it was blocked (e.g., "Filtered: Speed below 10km/h").
4.  **metadata.txt:** Device model, Android OS version, wall-clock start time, and the specific mathematical thresholds used during that session.

---

## 4. User Interface & Hardware Integration
*   **Tabs:** Features a Monitor (live stats), Map (visualized pothole clusters), History (editable database), and Report (analytics & exporting) tab.
*   **Hardware Buttons:** Because looking at a screen while riding is dangerous, the app overrides the physical Volume buttons. Pressing Volume UP injects a `MANUAL_POTHOLE` label into the telemetry stream, and Volume DOWN injects `MANUAL_SPEED_BREAKER`.
*   **Torch Control:** A UI toggle allows the rider to turn on the phone's flashlight to illuminate the road for external action cameras.
*   **Exporting:** A single tap zips the massive raw CSV logs into a neat `.zip` file and opens the Android Share Sheet for easy upload to Google Drive or Email.
