# Khadda Technical Report
**Project:** Smartphone-based Passive Pothole Detection App

## STEP 0 and STEP 1 findings

| Item | What the code does | File and Line | Status |
| :--- | :--- | :--- | :--- |
| `TelemetryLogger` | Logs sensor, GPS, and candidates to CSV files in a background coroutine every 1.5s. | `service/TelemetryLogger.kt:10` | VERIFIED |
| `candidate_log` | CSV file tracking all triggers (even rejected ones) with reasons. | `service/TelemetryLogger.kt:40` | VERIFIED |
| `metadata_` | Text file tracking device info and active thresholds per session. | `service/TelemetryLogger.kt:33` | VERIFIED |
| `gZeroCounter` | Increments if accelMagnitude <= 2.5, resets to 0 otherwise. Triggers at >= 3. | `detector/PotholeDetector.kt:63-65` | VERIFIED |
| `vertWindow` | A 5-sample ArrayDeque for a moving average on vertical acceleration. | `detector/DataProcessor.kt:34` | VERIFIED |
| `MANUAL_POTHOLE` | String label injected into telemetry for ground truth marking. | `ui/monitor/MonitorFragment.kt:120` | VERIFIED |
| `MANUAL_SPEED_BREAKER` | String label injected into telemetry for ground truth marking. | `ui/monitor/MonitorFragment.kt:130` | VERIFIED |
| `RoadSimulationManager`| Generates synthetic data that feeds directly into the live pipeline if toggled. | `service/DetectionForegroundService.kt:142` | VERIFIED |
| `MockSensorSimulator` | Not used in the live app, only in test directories. | N/A | NOT FOUND |
| `elapsedRealtimeNanos` | Used for accurate GPS time logging on API 17+. | `service/DetectionForegroundService.kt:129` | VERIFIED |
| `SENSOR_DELAY_FASTEST` | Not explicitly typed. Hardcoded to `10000` µs (100Hz). | `sensor/SensorCollector.kt:16` | NOT FOUND |
| `TYPE_GRAVITY` | Android Sensor used to isolate the gravity up-vector. | `sensor/SensorCollector.kt:22` | VERIFIED |
| `TYPE_LINEAR_ACCELERATION`| Android Sensor used for pure vehicle acceleration (gravity removed). | `sensor/SensorCollector.kt:23` | VERIFIED |

### Verified Logic Checks
1. **Speed Gate**: The 2.77 m/s check is commented out: `// if (speedMps < 2.77f) {` (`FalsePositiveFilter.kt:35`). (DISABLED).
2. **Vertical Acceleration**: It is the dot product of Linear Acceleration and Gravity up-vector. `val vertAccel = dot(lin, up).toDouble()` (`DataProcessor.kt:76`). It uses the `TYPE_GRAVITY` sensor, not a mathematical low-pass filter.
3. **zDiff**: A 5-sample moving average is applied first. Then, zDiff subtracts the current smoothed value from the smoothed value 10 samples ago: `abs(smoothedVert - smoothedVertHistory.first())` (`DataProcessor.kt:87`).
4. **zThreshDiff and stdevZ**: `zThreshDiff = abs(smoothedVert)` and `stdevZ = sqrt(sumSquaredDiffs / zWindow.size)` where `zWindow` is 20 samples of `vertAccel`. Both use the gravity-projected vertical acceleration, not raw Z!
5. **Sensor Registration**: Registered in `SensorCollector.kt:50-58` with a hardcoded `samplingRate = 10000` (100Hz).
6. **G-ZERO**: It tests the raw accelerometer magnitude `data.getAccelMagnitude()` (`DataProcessor.kt:94`). Counter resets at `gZeroCounter = 0` (`PotholeDetector.kt:65`).
7. **Voting**: Requires 2 votes: `if (triggered.size < 2)` (`DetectionForegroundService.kt:188`).
8. **Severity**: LOW (`< 6.5`), MEDIUM (`>= 6.5`), HIGH (`>= 9.0`) (`PotholeEvent.kt:35`).
9. **Clock**: `System.currentTimeMillis()` is used for GPS timestamps, cooldowns, manual labels, and DB creation. `elapsedRealtimeNanos()` is used for GPS telemetry. Sensor rows use the nanosecond `event.timestamp`.
10. **GPS**: `GPS_PROVIDER` and `NETWORK_PROVIDER` used with `1000L` ms and `1.0f` meters (`LocationTracker.kt:30`). Speed age is tracked (`speedAgeMs`), but `hasSpeed()` is never checked.
11. **Camera**: `Size(320, 320)`, capped at `200ms` (5FPS), buffer holds `25` frames. Lookback: `lookbackS = if (speedMps > 0.1f) 8f / speedMps else 2f` clamped via `coerceIn(0.3f, 2f) + 0.1f`. If no frame is found, the event passes validation by default (`DetectionForegroundService.kt:209`).
12. **Vision Model**: Custom raw `Interpreter` is used (`PotholeVisionAnalyzer.kt:69`). Input is `[1, 320, 320, 3]`. It reads index 4 for confidence: `val confidence = outputBuffer[0][4][i]`. If `isVisuallyConfirmed` is false, it rejects the pothole (`isValid = false`) and the event is NOT saved (`DetectionForegroundService.kt:212`).
13. **Telemetry Logs**: Writes 3 CSVs to `Documents/KhaddaLogs/` (not cache). Flushed every 1.5s via `Dispatchers.IO`. Columns exactly match the constructor.
14. **Manual Labels**: "MANUAL_POTHOLE" and "MANUAL_SPEED_BREAKER" are written into the `label` column of `sensor_log`.
15. **Settings**: Default thresholds are hardcoded in `PotholeDetector`. They are NEVER loaded from SharedPreferences at startup. (NOT FOUND). Active defaults are logged to `metadata_`.
16. **Simulation**: `simulationManager.onSimulatedSensorData` actively feeds the live `DataProcessor` if toggled (`DetectionForegroundService.kt:142`).

---

## 1. System Overview
**Purpose:** To passively detect, record, and map road anomalies using smartphone IMU sensors and YOLOv8 camera vision validation.
**Target Platform:** Android (Min SDK 26, Target SDK 34).
**Modules:** LocationService, SensorCollector, DataProcessor, PotholeDetector, PotholeVisionAnalyzer, TelemetryLogger, Room DB.
**Data Flow:** `Hardware Sensors` -> `DetectionForegroundService` -> `DataProcessor` -> `PotholeDetector` -> `FalsePositiveFilter` -> `PotholeVisionAnalyzer` -> `Room Database`.

## 2. Data Acquisition
- **Sensors**: `TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE`, `TYPE_GRAVITY`, `TYPE_LINEAR_ACCELERATION`. Sampled at 10,000 µs (100Hz).
- **GPS**: Updates every 1000ms or 1.0 meters.
- **CameraX**: 320x320 resolution at ~5 FPS. Ring buffer holds 25 frames.
- **Threading**: Sensors run on the main thread (UI loop), Logging runs on `Dispatchers.IO`. 

## 3. Signal Processing Pipeline
0. The first 1000ms of any session are discarded to allow the Android `TYPE_GRAVITY` sensor fusion to stabilize.
1. Gravity is obtained natively via `TYPE_GRAVITY` and normalized into an up-vector.
2. Vertical acceleration is computed via the dot product of `TYPE_LINEAR_ACCELERATION` and the up-vector.
3. Vertical acceleration is smoothed with a 5-sample moving average (`vertWindow`).
4. Z-Diff subtracts the current smoothed value from the 10th previous smoothed value.

## 4. Detection Algorithms
1. **Z-DIFF**: Threshold = `5.0`.
2. **Z-THRESH**: Threshold = `3.92`.
3. **STDEV(Z)**: Threshold = `5.0`. Window = `20`.
4. **G-ZERO**: Threshold = `< 2.5 m/s^2`. Must trigger for `3` consecutive samples.

**Voting Rule**: At least 2 out of the 4 algorithms must trigger to pass as a candidate event.

## 5. False-Positive Filtering
1. **Braking**: `horizontalAccel > 4.5` AND `zDiff < 2.5`.
2. **Turning**: `abs(yaw) > 0.3 rad/s`.
3. **Legacy Gyro**: `gyroMagnitude > 1.2 rad/s` AND `zDiff < 2.5`.
4. **Cooldown**: `timeDiff < 1500L` ms.
5. **GPS Quality Gate**: `speedAgeMs < 2000L` and `locationTracker.accuracy < 20f`.
6. **Duplicate**: `distance <= 15.0m` within `3000L` ms.
Rejections are logged into `candidate_log.csv` under the `rejectedReason` column.

## 6. Severity Calculation
Based on max(`zDiff`, `zThreshDiff`):
- LOW: `< 6.5`
- MEDIUM: `>= 6.5`
- HIGH: `>= 9.0`

## 7. Data Storage and Export
- **Room DB (`pothole_events`)**: `eventId`, `timestamp`, `latitude`, `longitude`, `zDiffValue`, `severity`, `algorithm`, `status`, `syncStatus`, `address`. Only saves if a recent, accurate GPS fix is available.
- **CSVs**: Written to `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) / "KhaddaLogs"`. Flushed every 1500ms on `Dispatchers.IO`.
  - `sensor_log`: Logs projected `vertAccel`, `gravUnitX` (gravity direction vector), and IMU raw data.
  - `candidate_log`: Logs `speed`, `yaw`, and individual algorithm voting flags (`algZDiff`, `algZThresh`, `algStdev`, `algGZero`). Blank (`NaN`) is written for missing GPS coordinates.

## 8. Mapping and UI
Uses `osmdroid` for mapping. Displays events based on SQLite DB queries.

## 9. Simulation Mode
A fully synthetic sine-wave generator (`RoadSimulationManager`) replaces hardware sensor callbacks when activated via the UI switch.

## 10. Configuration Table
| Parameter | Default | Unit | File | User Editable |
| :--- | :--- | :--- | :--- | :--- |
| `samplingRate` | 10000 | µs | SensorCollector.kt | No |
| `zDiffThreshold` | 5.0 | m/s^2 | PotholeDetector.kt | No (Hardcoded) |
| `zThreshThreshold` | 3.92 | m/s^2 | PotholeDetector.kt | No (Hardcoded) |
| `stdevThreshold` | 5.0 | m/s^2 | PotholeDetector.kt | No (Hardcoded) |
| `gZeroThreshold` | 2.5 | m/s^2 | PotholeDetector.kt | No (Hardcoded) |

## 11. Change History
- Reduced `gZeroThreshold` from `7.84` to `2.5` to eliminate noise.
- Migrated from Google's `task-vision` ObjectDetector to a custom raw `Interpreter` parser to bypass Android 15 16KB alignment crashes.

## 12. Limitations
- User UI settings for thresholds are completely ignored at service startup because the code lacks `SharedPreferences` instantiation.
- The 100Hz UI update for `LiveWaveformView` causes main thread blocking (though recently reverted back to 100Hz for accuracy).
- GPS speed logic never checks `location.hasSpeed()`, trusting zero values by default.

## 13. Reproducibility Checklist
- Android 8.0+ device rigidly mounted to a vehicle.
- YOLOv8 `.tflite` model (320x320) placed in `assets/pothole_model.tflite`.
- `org.tensorflow:tensorflow-lite:2.16.1` installed in Gradle.

---
### A) Safe Facts for the Paper
1. The IMU sampling delay is explicitly set to 10,000 microseconds (100Hz) (`SensorCollector.kt:16`).
2. Vertical acceleration is decoupled from phone mounting orientation by projecting `TYPE_LINEAR_ACCELERATION` onto `TYPE_GRAVITY` (`DataProcessor.kt:76`).
3. The YOLOv8 computer vision model serves as a final veto gate; if it returns false, the event is completely deleted from the database (`DetectionForegroundService.kt:212`).

### B) Items Needing Confirmation
1. Threshold settings changed in the UI are never loaded into the background service. Is this intentional for the current prototype phase?
2. The 10km/h speed gate is commented out, meaning the app will record footstep vibrations if left running while walking.
3. The UI `LiveWaveformView` is currently receiving 100 Runnables per second on the Main Thread. Do you want to keep this, or re-apply the 30FPS throttle?
