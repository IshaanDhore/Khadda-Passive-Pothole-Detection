# Khadda (खड्डा) — Real-Time Passive Pothole Detection System

An Android smartphone application for automated road-surface anomaly detection using embedded accelerometer and gyroscope sensors, GPS geolocation, and participatory sensing algorithms.

Developed for **PBL III: Software Engineering and Modelling (CSE20140)** — Semester V, Department of Computer Science & Engineering.

---

## 👥 Authors & Academic Context
| Sr. No. | Student Name | PRN / Student ID |
| :--- | :--- | :--- |
| 1 | **Neeraj Padghan** | 1262242219 |
| 2 | **Ishaan Dhore** | 1262242310 |
| 3 | **Akhilesh Munishwar** | 1262242269 |
| 4 | **Samruddhi Patil** | 1262242202 |

- **Specification Reference**: Software Requirements Specification (SRS) Version 1.0 approved
- **Process Model**: Agile Scrum Methodology (Assignment 1)
- **Design Specification**: Structured Systems Analysis and Design (SSAD) with Level-0 & Level-1 DFDs (Assignment 3) and UML Class/Activity/Sequence Diagrams.

---

## 🔬 Theoretical Foundation & Published Research
This application implements and extends three peer-reviewed research papers:
1. **Mednis et al. (2011)** — *Real Time Pothole Detection Using Android Smartphones with Accelerometers* (IEEE DCOSS). Primary source for the four core detection algorithms (`Z-THRESH`, `Z-DIFF`, `STDEV(Z)`, `G-ZERO`).
2. **Allouch et al. (2017)** — *RoadSense: Smartphone Application to Estimate Road Conditions Using Accelerometer and Gyroscope* (IEEE Sensors Journal). Primary source for multi-sensor fusion and gyroscope turning noise cancellation.
3. **Eriksson et al. (2008)** — *The Pothole Patrol: Using a Mobile Sensor Network for Road Surface Monitoring* (ACM MobiSys). Primary source for spatial/temporal clustering, hard braking rejection, and speed breaker discrimination.

---

## 📐 Detection Algorithms Reference
| Algorithm | Condition / Formula | Reference Threshold | Description |
| :--- | :--- | :--- | :--- |
| **Z-DIFF** | $|a_z[i] - a_z[i-1]| \ge T_{\text{diff}}$ | $0.2g \approx 1.96\text{ m/s}^2$ | Identifies sudden vertical acceleration shocks between successive samples. |
| **Z-THRESH** | $|a_z[i] - 1.0g| \ge T_{\text{thresh}}$ | $0.4g \approx 3.92\text{ m/s}^2$ | Detects vertical acceleration exceeding normal gravity baseline. |
| **STDEV(Z)** | $\sigma(z_{i-19 \dots i}) \ge T_{\text{stdev}}$ | $0.2g \approx 1.96\text{ m/s}^2$ | Rolling window of $N=20$ samples detecting sudden elevated variance. |
| **G-ZERO** | $\sqrt{a_x^2 + a_y^2 + a_z^2} \le T_{\text{gzero}}$ | $0.8g \approx 7.84\text{ m/s}^2$ | Temporary free-fall condition when the vehicle wheel drops into a cavity. |

---

## 📱 Application Modules & Features

### 1. Live Monitor Screen (`MonitorFragment`)
- **Driver Ergonomics**: Single prominent Start/Stop control designed for zero distraction while driving.
- **Real-Time Oscilloscope Waveform**: Custom Canvas-based `LiveWaveformView` plotting triaxial accelerometer ($X$: Red, $Y$: Green, $Z$: Cyan) and gyroscope activity.
- **Telemetry Display**: Live vehicle speed (km/h) and GPS coordinates.
- **Instant Alerts**: Audible tone and haptic vibration feedback on pothole detection.
- **Simulation / Demo Mode Toggle**: Allows instant classroom/lab demonstration of pothole detection without needing a moving car.

### 2. Road-Anomaly Map (`MapFragment`)
- **Interactive OpenStreetMap Integration**: Powered by `osmdroid` — **works completely out of the box with zero Google Maps API keys required!**
- **Severity-Coded Markers**:
  - 🔴 **High Severity**: Crimson marker ($> 6.0\text{ m/s}^2$ impact).
  - 🟡 **Medium Severity**: Amber marker ($3.0 - 6.0\text{ m/s}^2$ impact).
  - 🟢 **Low Severity**: Emerald marker ($< 3.0\text{ m/s}^2$ impact).
- **Marker Inspection**: Tap any marker to view time, algorithm, GPS coordinates, and current repair status.
- **Filter Chips**: Filter map view by severity.

### 3. Detection Log & History (`HistoryFragment`)
- RecyclerView list displaying all recorded events with timestamps, impact g-force, and repair badges.
- One-tap status update dialog.
- Clear all records action.

### 4. Road Maintenance Authority Dashboard (`AuthorityFragment`)
- Designed according to Assignment 3 DFD & Use-Case diagrams.
- Allows municipal officers or evaluators to track potholes through repair states:
  $$\text{REPORTED} \longrightarrow \text{UNDER\_REVIEW} \longrightarrow \text{REPAIRED}$$
- Filter by repair workflow status.

### 5. Analytics & Data Export (`ReportFragment`)
- Statistical distribution of potholes by severity.
- Algorithm breakdown statistics.
- **CSV Export**: Exports full session data to comma-separated file for Excel/data science evaluation.
- **JSON Export**: Exports structured JSON dataset for academic submissions and external synchronization.

### 6. Calibration & Settings (`SettingsFragment`)
- Sliders for all four algorithm thresholds (`Z-DIFF`, `Z-THRESH`, `STDEV(Z)`, `G-ZERO`).
- Audio and haptic alert toggles.
- Reset to reference defaults button.

### 7. Background Continuous Sensing (`DetectionForegroundService`)
- Android Foreground Service with sticky notification.
- Ensures continuous sensor acquisition and GPS tagging even when the screen is turned off or locked.

---

## 🛠️ How to Open and Run in Android Studio

1. Launch **Android Studio**.
2. Click **File $\to$ Open...**
3. Navigate to and select the `KhaddaApp` folder:
   ```
   c:\Users\Neeraj Padghan\OneDrive\Desktop\Khadda\KhaddaApp
   ```
4. Click **Trust Project** if prompted.
5. Android Studio will automatically sync the Gradle configuration using the included Gradle 8.7 wrapper.
6. Connect an Android phone via USB (with Developer Options & USB Debugging enabled) OR start an Android Virtual Device (AVD).
7. Click the green **Run ▶** button (or press `Shift + F10`).
8. To demonstrate in a lab: Toggle **"Demo"** mode at the top right of the Monitor screen and tap **START MONITORING**!

---

## 🧪 Running Unit Tests
Unit tests for the four detection algorithms and false-positive filter can be run directly from Android Studio or via Gradle:
```bash
./gradlew test
```
- `PotholeDetectorTest`: Tests Z-DIFF, Z-THRESH, STDEV(Z), G-ZERO, and severity levels.
- `FalsePositiveFilterTest`: Tests braking rejection, cornering rejection, and duplicate suppression.
