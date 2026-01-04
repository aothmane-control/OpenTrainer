# Project Summary: OpenTrainer Android App

## 🔓 Open Source | 🔒 Privacy First | 📶 Offline-Ready

A complete Android application for connecting to Wahoo Kickr Core and compatible smart trainers via Bluetooth Low Energy, displaying real-time training metrics, executing programmable resistance workouts with GPX-based elevation profiles, interactive map visualization, and comprehensive workout analytics with historical tracking.

## ✨ Core Principles

- **100% Open Source** - CC BY-NC 4.0 License, all code publicly available
- **Privacy Focused** - No user accounts, no cloud services, no data collection
- **Offline Training** - Works completely offline (internet only for map tiles)
- **Your Data Stays Local** - All workout profiles and history stored on device
- **Zero Tracking** - No analytics, no telemetry, no external connections

## Overview
A complete Android application for connecting to Wahoo Kickr Core trainers via Bluetooth Low Energy, displaying real-time training metrics, and executing programmable resistance workouts with interval training.

## Complete File Structure

```
kickr_android/
├── app/
│   ├── build.gradle.kts                          # App-level build configuration with APK naming
│   ├── proguard-rules.pro                        # ProGuard rules for release builds
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml               # App manifest with Bluetooth permissions
│           ├── java/com/kickr/trainer/
│           │   ├── MainActivity.kt               # Main UI controller with workout execution (1198 lines)
│           │   ├── WorkoutSetupActivity.kt       # Workout configuration & management (408 lines)
│           │   ├── GpxMapActivity.kt             # GPX file viewer with interactive map
│           │   ├── WorkoutSummaryActivity.kt     # Post-workout summary with charts (188 lines)
│           │   ├── WorkoutHistoryActivity.kt     # Workout history viewer (82 lines)
│           │   ├── GpxWorkoutHolder.kt           # Singleton for GPX track data
│           │   ├── adapter/
│           │   │   ├── DeviceAdapter.kt          # RecyclerView adapter for BLE devices
│           │   │   ├── IntervalAdapter.kt        # RecyclerView adapter for workout intervals
│           │   │   └── WorkoutHistoryAdapter.kt  # RecyclerView adapter for workout history
│           │   ├── bluetooth/
│           │   │   ├── GattAttributes.kt         # Bluetooth GATT UUIDs (includes FTMS)
│           │   │   └── KickrBluetoothService.kt  # Core BLE service with resistance control (426 lines)
│           │   ├── model/
│           │   │   ├── KickrDevice.kt            # Device data model
│           │   │   ├── TrainerData.kt            # Training metrics data model
│           │   │   ├── Workout.kt                # Workout profile model with validation
│           │   │   ├── WorkoutInterval.kt        # Workout interval model
│           │   │   ├── GpxTrack.kt               # GPX track data model
│           │   │   ├── GpxTrackPoint.kt          # GPX point with elevation data
│           │   │   ├── WorkoutDataPoint.kt       # Single workout data recording (18 lines)
│           │   │   └── WorkoutHistory.kt         # Complete workout history record (47 lines)
│           │   └── utils/
│           │       ├── GpxParser.kt              # GPX file parsing
│           │       └── WorkoutStorageManager.kt  # Workout history storage (132 lines)
│           └── res/
│               ├── layout/
│               │   ├── activity_main.xml         # Main screen with charts & workout status (467 lines)
│               │   ├── activity_workout_setup.xml# Workout configuration UI (260 lines)
│               │   ├── activity_gpx_map.xml      # GPX map viewer layout
│               │   ├── activity_workout_summary.xml # Workout summary with charts (231 lines)
│               │   ├── activity_workout_history.xml # Workout history list (63 lines)
│               │   ├── item_device.xml           # Device list item layout
│               │   ├── item_interval.xml         # Interval list item layout with edit button
│               │   ├── item_workout_history.xml  # Workout history card layout (104 lines)
│               │   └── dialog_edit_interval.xml  # Edit interval dialog (52 lines)
│               ├── menu/
│               │   └── menu_main.xml             # Main toolbar menu (About action)
│               ├── mipmap-*/                     # Launcher icons (all densities)
│               │   ├── ic_launcher.png
│               │   └── ic_launcher_round.png
│               ├── mipmap-anydpi-v26/            # Adaptive icons (Android 8.0+)
│               │   ├── ic_launcher.xml
│               │   ├── ic_launcher_round.xml
│               │   └── ic_launcher_foreground.xml
│               ├── values/
│               │   ├── colors.xml                # Color definitions
│               │   ├── strings.xml               # String resources (app name: OpenTrainer)
│               │   ├── themes.xml                # Material Design theme
│               │   └── ic_launcher_background.xml
│               └── xml/
│                   ├── backup_rules.xml
│                   └── data_extraction_rules.xml
├── build.gradle.kts                              # Project-level build configuration
├── settings.gradle.kts                           # Gradle settings with MPAndroidChart repo
├── gradle.properties                             # Gradle properties
├── .gitignore                                    # Git ignore rules
├── README.md                                     # Comprehensive user documentation
├── PROJECT_SUMMARY.md                            # This file
├── CHANGELOG.md                                  # Version history and changes
└── LICENSE                                       # CC BY-NC 4.0 License
```
│                   └── data_extraction_rules.xml
├── build.gradle.kts                              # Project-level build configuration
├── settings.gradle.kts                           # Gradle settings with MPAndroidChart repo
├── gradle.properties                             # Gradle properties
├── README.md                                     # Comprehensive user documentation
├── PROJECT_SUMMARY.md                            # This file
├── CHANGELOG.md                                  # Version history and changes
└── LICENSE                                       # CC BY-NC 4.0 License
```

## Key Features Implemented

### 1. Bluetooth Low Energy (BLE) Implementation
- **Device Scanning:** Scans for all BLE devices (no filter for maximum compatibility)
- **GATT Connection:** Manages connection lifecycle with detailed logging
- **Service Discovery:** Automatically discovers and subscribes to:
  - Cycling Power Service (0x1818)
  - Cycling Speed and Cadence Service (0x1816)
  - Heart Rate Service (0x180D)
  - Fitness Machine Service (0x1826) - for resistance control

### 2. Data Parsing & Calculation
Implements Bluetooth SIG specifications for:
- **Power Measurement:** Instantaneous power in watts
- **Cadence Calculation:** From crank revolution data (RPM)
- **Speed Calculation:** From power using formula: speed (km/h) = (power / 3.6) * 0.3
- **Distance Tracking:** Cumulative distance from speed integration for all workout types
- **Heart Rate:** BPM from HR sensor (if available)

### 3. Real-Time Data Visualization
- **MPAndroidChart Integration:** Real-time charts with smooth updates
- **Power Chart:** Last 20 seconds of power data with rolling window
- **Speed Chart:** Last 20 seconds of speed data
- **Workout Profile Chart:** Complete resistance profile visualization
  - Blue filled area showing target resistance over time
  - Red dashed line indicating current workout position
  - Dynamic Y-axis scaling (110% of max resistance for better visibility)

### 4. Workout Programming & Execution
- **Custom Workouts:** Create multi-interval resistance profiles
- **Interval Configuration:**
  - Set total workout duration in minutes
  - Add intervals with duration (minutes) and target resistance (%)
  - Edit existing intervals with validation
  - Scrollable interval list (200dp height with nested scrolling)
  - Validation ensures intervals sum to total duration
  - Input fields disabled when workout is complete
- **Workout Execution:**
  - CountDownTimer for precise interval transitions
  - Automatic resistance control at each interval
  - Real-time display of current interval, elapsed time, target resistance
  - Visual progress indicator on resistance profile chart
  - Stop workout functionality
  - **Automatic Data Recording:** Records every second during workout:
    - timestamp, elapsed time, power, speed, cadence, heart rate, resistance, distance
- **Workout Profile Management:**
  - Save workouts with custom names
  - Load from multiple saved profiles (selection dialog)
  - Delete unwanted profiles (with confirmation)
  - Storage via SharedPreferences + JSON

### 5. Workout History & Analytics
- **Automatic Workout Saving:** Every workout is automatically saved when stopped
- **Workout Summary:** Post-workout screen displays:
  - Duration, distance, average power statistics
  - Three interactive charts: Power Evolution, Speed Evolution, Resistance Profile
  - Share functionality to export workout results
- **Workout History Viewer:**
  - List view of all completed workouts (sorted by date)
  - Each entry shows: name, type, date, duration, distance, average power
  - Tap any workout to view complete details and charts
- **Historical Data Storage:**
  - JSON files in app internal storage
  - File format: `workout_YYYYMMDD_HHMMSS.json`
  - Contains complete second-by-second data and statistics

### 6. GPX Integration
- **GPX File Support:** Load GPX tracks from device storage
- **Interactive Map:** osmdroid-based map viewer showing complete route
- **Elevation Profile:** Visual display of route elevation
- **GPX Workouts:** Start workouts from GPX tracks with elevation-based resistance

### 7. Resistance Control
Multiple protocols for maximum compatibility:
1. **Wahoo Proprietary Protocol:** Uses custom service UUID with 0x42 command
2. **FTMS Target Power:** Uses Fitness Machine Control Point (opcode 0x05)
3. **FTMS Resistance Level:** Uses Fitness Machine Control Point (opcode 0x04)
4. **Cycling Power Control Point:** Alternative power control method

### 8. User Interface & Usability
- **Material Design 3:** Modern, clean interface with proper theming
- **Toolbar with Menu:** About dialog showing copyright and app information
- **Copyright Display:** "Copyright (c) 2026 Amine Othmane" in app footer
- **Scrollable Interval Lists:** Fixed 200dp height with smooth scrolling
- **Interactive Charts:** Zoomable, pannable workout data visualization
- **Permission Handling:** Proper runtime permission flows for Android 12+

## Dependencies
- AndroidX Core KTX 1.12.0
- AppCompat 1.6.1
- Material Design 3 1.11.0
- ConstraintLayout 2.1.4
- Lifecycle Runtime/ViewModel 2.7.0
- Activity KTX 1.8.2
- Coroutines 1.7.3
- **MPAndroidChart v3.1.0** - Real-time chart visualization
- **osmdroid 6.1.18** - OpenStreetMap map viewer (offline tiles supported)

**Privacy Note:** All dependencies are standard Android libraries. Map tiles from OpenStreetMap are only downloaded when explicitly viewing maps - no tracking or user identification occurs.

### Bluetooth Services
Standard Bluetooth SIG services:
- **0x1818:** Cycling Power Service
- **0x1816:** Cycling Speed and Cadence Service  
- **0x180D:** Heart Rate Service
- **0x180A:** Device Information Service
- **0x1826:** Fitness Machine Service (FTMS) - for resistance control
- **a026ee0b-0a7d-4ab3-97fa-f1500f9feb8b:** Wahoo Proprietary Trainer Service

### Characteristics
- **0x2A63:** Cycling Power Measurement
- **0x2A5B:** CSC Measurement
- **0x2A37:** Heart Rate Measurement
- **0x2902:** Client Characteristic Configuration (notifications)
- **0x2AD9:** Fitness Machine Control Point (resistance control)
- **a026e005-0a7d-4ab3-97fa-f1500f9feb8b:** Wahoo Trainer Control Point
### Permissions
- **Android 12+ (API 31+):** BLUETOOTH_SCAN, BLUETOOTH_CONNECT
- **Android 10-11 (API 29-30):** ACCESS_FINE_LOCATION
- **Runtime Permissions:** Proper request flow with user prompts

## Code Statistics

| Component | Lines of Code | Description |
|-----------|--------------|-------------|
| MainActivity.kt | ~1198 | Main UI, charts, workout execution & data recording |
| WorkoutSetupActivity.kt | ~408 | Workout configuration & interval management |
| WorkoutSummaryActivity.kt | ~188 | Post-workout summary with three charts |
| WorkoutHistoryActivity.kt | ~82 | Workout history list viewer |
| WorkoutStorageManager.kt | ~132 | JSON-based workout history storage |
| KickrBluetoothService.kt | ~426 | BLE functionality with resistance control |
| GpxMapActivity.kt | ~150 | GPX file viewer with osmdroid map |
| activity_main.xml | ~467 | Main screen layout with toolbar & charts |
| activity_workout_setup.xml | ~260 | Workout setup UI with scrollable intervals |
| activity_workout_summary.xml | ~231 | Summary screen with stats and charts |
| activity_workout_history.xml | ~63 | History list layout |
| item_workout_history.xml | ~104 | Workout history card layout |
| DeviceAdapter.kt | ~50 | Device list adapter |
| IntervalAdapter.kt | ~60 | Interval list adapter with edit support |
| WorkoutHistoryAdapter.kt | ~65 | Workout history list adapter |
| GattAttributes.kt | ~45 | UUID constants (including FTMS) |
| Data Models | ~220 | 10 model classes including WorkoutDataPoint & WorkoutHistory |
| **Total** | **~4400+** | Complete workout tracking & analytics system |
## How It Works

### Basic Training Flow

1. **Startup:**
   - App launches, checks Bluetooth availability
   - Initializes KickrBluetoothService
   - Sets up UI observers and chart configurations

2. **Scanning:**
   - User taps "Scan for Devices"
   - App checks/requests Bluetooth permissions
   - BLE scanner starts (scans all devices for maximum compatibility)
   - Discovered devices populate RecyclerView

3. **Connection:**
   - User taps a device from the list
   - App connects via GATT
   - Discovers available services (with detailed logging)
   - Subscribes to notifications for all relevant characteristics
   - Initializes charts (power, speed)

4. **Real-Time Data Flow:**
   - Kickr sends BLE notifications with measurement data
   - KickrBluetoothService parses binary data per Bluetooth spec
   - Updates StateFlow with new TrainerData
   - MainActivity observes Flow and updates:
     - Numeric displays (power, cadence, speed, HR)
     - Power chart (last 20 seconds, rolling window)
     - Speed chart (last 20 seconds, rolling window)
   - Charts update smoothly with new data points

### Workout Programming Flow

5. **Workout Setup:**
   - User taps "Setup Workout" button
   - WorkoutSetupActivity launches
   - User enters workout name
   - User sets total duration in minutes
   - User adds intervals:
     - Duration in minutes (converted to seconds internally)
     - Target resistance percentage
   - App validates intervals sum to total duration
   - Add button disabled when duration fully allocated
   - User can save workout profile with custom name
   - Saved workouts stored as JSON in SharedPreferences

6. **Workout Execution with Data Recording:**
   - User taps "Start Workout" after configuration
   - Intent passes workout data to MainActivity
   - Workout status card becomes visible (blue background)
   - **Data recording begins:** Every second the app records:
     - timestamp, elapsedSeconds, power, speed, cadence, heartRate, resistance, distance
   - Resistance profile chart displays entire workout:
     - X-axis: Time in seconds
     - Y-axis: Resistance % (scaled to 110% of max)
     - Blue filled area showing step function of resistance
   - CountDownTimer starts:
     - Fires every second
     - Updates elapsed/remaining time display
     - Updates current interval information
     - Sends resistance commands at interval transitions
     - Updates red dashed progress line on chart
     - **Records data point to workout history**
   - Resistance control:
     - Tries Wahoo proprietary protocol first
     - Falls back to FTMS Target Power
     - Falls back to FTMS Resistance Level
     - Falls back to Cycling Power Control Point
     - All attempts logged for debugging

7. **Post-Workout Summary:**
   - When workout stops, data is automatically saved to JSON file
   - WorkoutSummaryActivity launches with:
     - Duration, distance, average power statistics
     - Three interactive charts: Power Evolution, Speed Evolution, Resistance Profile
     - Share functionality to export results
   - Workout saved with timestamp filename: `workout_YYYYMMDD_HHMMSS.json`

8. **Workout Management:**
   - **Load:** Opens dialog showing all saved workout names
     - Select workout → shows Load/Delete options
     - Load: Restores workout configuration to UI
     - Delete: Confirmation dialog → removes from storage
   - **Save:** Stores current workout with entered name
   - **Edit Intervals:** Tap edit button on any interval to modify duration/resistance
   - **View History:** Tap "View History" to see all completed workouts
   - Multiple workouts can be saved and managed

9. **Workout History Review:**
   - WorkoutHistoryActivity shows list of all completed workouts
   - Sorted by date (newest first)
   - Tap any workout to view complete summary and charts
   - Historical data never expires (stored locally)

10. **Disconnect:**
   - User taps "Disconnect" or "Stop Workout"
## Completed Features

- [x] Real-time data display (power, cadence, speed, HR, distance)
- [x] BLE device scanning and connection
- [x] Charts and graphs for power/speed trends (20-second rolling window)
- [x] Interval training with programmable resistance
- [x] Visual workout profile with progress indicator
- [x] Save/load/delete workout profiles
- [x] Edit individual workout intervals
- [x] Scrollable interval list (200dp fixed height)
- [x] Multiple resistance control protocols
- [x] Dynamic chart Y-axis scaling
- [x] Timer-based workout execution
- [x] **Automatic workout data recording** (every second during workouts)
- [x] **Distance tracking** for all workout types
- [x] **Post-workout summary** with three detailed charts
- [x] **Workout history storage** (JSON files with complete data)
- [x] **Workout history viewer** (list and detail views)
- [x] **Speed calculation from power** (no wheel sensor required)
- [x] GPX file support with interactive map
- [x] Elevation-based resistance from GPX tracks
- [x] **Toolbar with menu** and About dialog
- [x] **Copyright display** in app and source files
- [x] Custom APK naming (OpenTrainer-2.1.apk)
- [x] Dynamic chart Y-axis scaling
- [x] Timer-based workout execution
- [x] Custom APK naming (OpenTrainer-1.0.apk)
## Known Limitations

1. **Single Device Connection:** Can only connect to one trainer at a time
2. **No Calibration:** No zero offset or spindown calibration support
3. **Resistance Protocol Uncertainty:** Multiple protocols tried but effectiveness varies by device
4. **Fixed Chart Window:** Power/speed charts limited to 20-second view
5. **Manual Interval Entry:** No workout templates or quick presets (yet)
6. **GPX Offline Maps:** Map tiles require internet connection on first view (cached afterwards)
- [ ] FTP test mode with automated ramp test
- [ ] Connect to multiple sensors simultaneously (power meter + HR monitor)
- [ ] ANT+ support for non-BLE devices
- [ ] Integration with Strava/TrainingPeaks
## Testing Checklist

- [x] App builds successfully
- [x] Manifest permissions correct
- [x] BLE scanner works
- [x] Device list populates
- [x] Workout setup UI functional
- [x] Interval validation works
- [x] Interval edit functionality works
- [x] Scrollable interval list works
- [x] Workout profile save/load/delete
- [x] Charts display correctly
- [x] Timer updates every second
- [x] Resistance profile chart shows complete workout
- [x] Progress indicator updates during workout
- [x] Dynamic Y-axis scaling works
- [x] Workout data recording every second
- [x] Distance calculation and tracking
- [x] Post-workout summary displays
- [x] Workout history saves automatically
- [x] Workout history viewer works
- [x] GPX file loading and map display
- [x] Toolbar and menu display correctly
- [x] Copyright displayed in app

## Deployment Ready

The app is ready to:
- ✅ Build debug APK (OpenTrainer-2.1.apk)
- ✅ Build release APK
- ✅ Install on physical devices
- ✅ Run in Android Studio
- ✅ Scan for BLE devices
- ✅ Display UI with toolbar and charts
- ✅ Create and save workout profiles
- ✅ Edit workout intervals
- ✅ Execute timed workouts with resistance control
- ✅ Record complete workout data
- ✅ Display post-workout analytics
- ✅ View workout history
- ✅ Load and display GPX tracks
- ⚠️  Requires real Kickr Core for resistance control testing

## Next Steps for Future Development

1. **Hardware Testing:**
   - Verify resistance commands work correctly with real Kickr Core
   - Test all four resistance protocols
   - Validate data accuracy against known values
   - Confirm ERG mode engagement
   - Test interval transitions
   - Verify resistance percentage accuracy

2. **Extended Testing:**
   - Test workout history with 50+ saved workouts
   - Test edge cases (0% resistance, 100% resistance)
   - Test long workouts (2+ hours)
   - Test GPX files with complex elevation profiles
   - Test with multiple Android devices and OS versions

3. **Code Quality:**
   - Add unit tests for data parsing logic
   - Add unit tests for workout validation and statistics
   - Add unit tests for WorkoutStorageManager
   - Improve error handling for BLE failures
   - Add integration tests for workout execution

4. **Performance Optimization:**
   - Monitor chart performance with long sessions (data point optimization)
   - Optimize JSON parsing for large workout history files
   - Profile memory usage during extended workouts
   - Test workout history list with 100+ entries

5. **UX Improvements:**
   - Add workout templates (FTP test, endurance, intervals, etc.)
   - Add workout search/filter in history
   - Add workout comparison feature
   - Add progress trends over time
   - Add export to GPX/TCX/FIT formats
   - Improve launcher icons with professional design

6. **Feature Enhancements:**
   - Multi-sensor support (connect HR strap + power meter simultaneously)
   - ANT+ support for non-BLE devices
   - Structured workout library (TrainerRoad-style)
   - Virtual power estimation for non-power trainers
   - Integration with Strava/TrainingPeaks (optional)
   - Workout sharing between devices

---

**Created:** January 4, 2026  
**Last Updated:** January 6, 2026  
**Current Version:** 2.1  
**Status:** Feature-complete workout tracking & analytics system  
**Total Development Time:** Extended development sessions  
**Files Created:** 40+ files  
**APK Output:** OpenTrainer-2.1.apk  
**Copyright:** © 2026 Amine Othmane
   - Add workout templates
   - Add help/tutorial screen
7. **Multi-Device Testing:**
   - Test on Android 8.0 through 14
   - Test on different screen sizes
   - Test with other smart trainers (compatibility)

---

**Created:** January 4, 2026  
**Last Updated:** January 4, 2026  
**Status:** Feature-complete, ready for hardware testing  
**Total Development Time:** Extended development session  
**Files Created:** 30+ files  
**APK Output:** OpenTrainer-1.0.apkoothService
   - Sets up UI observers

2. **Scanning:**
   - User taps "Scan for Devices"
   - App checks/requests Bluetooth permissions
   - BLE scanner starts with Cycling Power Service filter
   - Discovered devices populate RecyclerView

3. **Connection:**
   - User taps a device from the list
   - App connects via GATT
   - Discovers available services
   - Subscribes to notifications for all relevant characteristics

4. **Data Flow:**
   - Kickr sends BLE notifications with measurement data
   - KickrBluetoothService parses binary data per Bluetooth spec
   - Updates StateFlow with new TrainerData
   - MainActivity observes Flow and updates UI
   - Data displayed in real-time (power, cadence, speed, HR)

5. **Disconnect:**
   - User taps "Disconnect" or app closes
   - GATT connection closed gracefully
   - Resources cleaned up

## Future Enhancement Ideas

- [ ] Save workout sessions to database
- [ ] Export data to GPX/TCX files
- [ ] Charts and graphs for power/cadence trends
- [ ] Virtual power calculation for non-power trainers
- [ ] FTP test mode
- [ ] Interval training timer
- [ ] Connect to multiple sensors simultaneously
- [ ] ANT+ support for non-BLE devices
- [ ] Integration with Strava/TrainingPeaks

## Known Limitations

1. **Single Device Connection:** Can only connect to one trainer at a time
2. **No Data Persistence:** Data resets on disconnect
3. **Basic UI:** Minimal visualization (no charts)
4. **No Calibration:** No zero offset or spindown calibration
5. **Limited Error Handling:** Basic error messages only

## Testing Checklist

- [x] App builds successfully
- [x] Manifest permissions correct
- [x] BLE scanner works
- [x] Device list populates
- [ ] Connects to real Kickr Core
- [ ] Receives power data
- [ ] Receives cadence data  
- [ ] Receives speed data
- [ ] Handles disconnection gracefully
- [ ] Permissions flow works on Android 12+
- [ ] Works on Android 8.0 minimum

## Deployment Ready

The app is ready to:
- ✅ Build debug APK
- ✅ Install on physical devices
- ✅ Run in Android Studio
- ✅ Scan for BLE devices
- ✅ Display UI correctly
- ⚠️  Requires real Kickr Core for full testing

## Next Steps

1. **Test with actual Kickr Core trainer**
2. **Verify data accuracy** against known values
3. **Add unit tests** for data parsing logic
4. **Improve error handling** for edge cases
5. **Consider adding data persistence** (Room database)
6. **Create proper launcher icons** (current ones are placeholders)
7. **Test on multiple Android versions** (8.0 through 14)

---

**Created:** January 4, 2026  
**Status:** Complete and ready for testing  
**Total Development Time:** ~30 minutes  
**Files Created:** 24 files
