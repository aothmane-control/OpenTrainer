# Changelog

All notable changes to OpenTrainer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1] - 2026-01-06

### Added - Workout Analytics System
- **Automatic Workout Data Recording:** Records power, speed, cadence, heart rate, resistance, and distance every second during workouts
- **Post-Workout Summary:** Displays comprehensive workout summary with duration, distance, and average statistics
- **Three Interactive Charts:** Power evolution, speed evolution, and resistance profile charts with zoom/pan
- **Workout History Storage:** JSON-based storage system saves all completed workouts with timestamp filenames
- **Workout History Viewer:** List view of all saved workouts with ability to view complete details
- **Share Functionality:** Share workout results from summary screen

### Added - Distance Tracking
- **Automatic Distance Calculation:** Distance tracked for all workout types (resistance and GPX)
- **Speed from Power Formula:** Calculates speed using `speed (km/h) = (power / 3.6) * 0.3` when no wheel sensor available
- **Cumulative Distance:** Real-time distance accumulation throughout workout

### Added - UI Improvements
- **Scrollable Interval List:** Fixed 200dp height RecyclerView with smooth nested scrolling
- **Edit Interval Feature:** Edit button on each interval allows modifying duration and resistance
- **Edit Interval Dialog:** Input validation ensures edited intervals maintain workout integrity
- **MaterialToolbar:** Added toolbar with app title and menu
- **About Menu:** Three-dot menu with About action showing copyright and app information

### Added - Copyright & Legal
- **Copyright Headers:** Added "Copyright (c) 2026 Amine Othmane" to all 19 Kotlin source files
- **In-App Copyright:** Copyright text displayed at bottom of main screen
- **About Dialog:** Shows complete copyright information and app details

### Added - Data Models
- `WorkoutDataPoint.kt`: Single data point model (timestamp, elapsed, power, speed, cadence, HR, resistance, distance)
- `WorkoutHistory.kt`: Complete workout history with statistics and formatted output methods
- `WorkoutStorageManager.kt`: JSON-based storage manager for workout persistence

### Added - Activities & Layouts
- `WorkoutSummaryActivity.kt`: Post-workout summary screen with three charts
- `WorkoutHistoryActivity.kt`: Workout history list viewer
- `activity_workout_summary.xml`: Summary layout with statistics cards and charts
- `activity_workout_history.xml`: History list layout with toolbar
- `item_workout_history.xml`: Workout history card layout
- `dialog_edit_interval.xml`: Edit interval dialog layout
- `menu_main.xml`: Main toolbar menu

### Changed
- **MainActivity:** Added toolbar setup, data recording logic, workout saving with NaN handling
- **WorkoutSetupActivity:** Added edit interval functionality with validation dialog
- **activity_main.xml:** Replaced title TextView with MaterialToolbar, added copyright TextView
- **item_interval.xml:** Added edit button alongside delete button
- **IntervalAdapter:** Added edit callback support
- **Version:** Updated to 2.1 (versionCode=3, versionName="2.1")
- **APK Naming:** Changed to OpenTrainer-2.1.apk

### Fixed
- **NaN in Workout Statistics:** Fixed crash when saving workouts with no heart rate data by checking if HR values exist before averaging
- **Missing Xml Import:** Added android.util.Xml import to GpxParser.kt after copyright header addition
- **Invisible Menu:** Fixed three-dot menu not showing by adding MaterialToolbar and proper menu handling

## [1.0] - 2026-01-04

### Added - Core Features
- **BLE Device Scanning:** Scan for all Bluetooth Low Energy devices
- **GATT Connection:** Connect to Wahoo Kickr Core and compatible trainers
- **Real-Time Data Display:** Power, cadence, speed, and heart rate
- **Power Chart:** Rolling 20-second window of power data
- **Speed Chart:** Rolling 20-second window of speed data
- **Workout Profile Chart:** Visual resistance profile with progress indicator

### Added - Workout Programming
- **Custom Workouts:** Create multi-interval resistance profiles
- **Interval Configuration:** Set duration (minutes) and resistance (%) for each interval
- **Workout Validation:** Ensures intervals sum to total workout duration
- **Save/Load/Delete:** Workout profile management via SharedPreferences + JSON
- **Workout Execution:** Timer-based workout with automatic resistance control

### Added - Resistance Control
- **Multiple Protocols:** Wahoo Proprietary, FTMS Target Power, FTMS Resistance Level, Cycling Power Control Point
- **Automatic Fallback:** Tries protocols in sequence for maximum compatibility
- **Visual Progress:** Red dashed line on resistance chart shows current position
- **Dynamic Scaling:** Y-axis scales to 110% of maximum resistance

### Added - GPX Support
- **GPX File Loading:** Load GPX tracks from device storage
- **Interactive Map:** osmdroid-based map viewer with route visualization
- **Elevation Profile:** Visual display of route elevation
- **GPX Workouts:** Start workouts from GPX tracks with elevation-based resistance

### Added - Architecture & Dependencies
- Kotlin with Coroutines & Flow for reactive programming
- Material Design 3 components
- MPAndroidChart v3.1.0 for real-time visualization
- osmdroid 6.1.18 for OpenStreetMap integration
- MVVM architecture pattern

### Added - Data Models
- `KickrDevice.kt`: BLE device representation
- `TrainerData.kt`: Training metrics data model
- `Workout.kt`: Workout profile with validation
- `WorkoutInterval.kt`: Single interval model
- `GpxTrack.kt`: GPX track data
- `GpxTrackPoint.kt`: GPX point with elevation

### Added - Bluetooth Implementation
- Cycling Power Service (0x1818)
- Cycling Speed and Cadence Service (0x1816)
- Heart Rate Service (0x180D)
- Fitness Machine Service (0x1826)
- Wahoo Proprietary Trainer Service
- Proper permission handling for Android 8.0 through 14

### Added - UI Components
- Device scanning and selection
- Real-time metric display
- Workout setup screen
- Workout execution screen
- GPX map viewer
- Dynamic charts with smooth updates

### Security & Privacy
- No user accounts or cloud services
- All data stored locally
- No analytics or telemetry
- Open source (MIT License)

---

## Legend
- **Added:** New features
- **Changed:** Changes to existing functionality
- **Deprecated:** Soon-to-be removed features
- **Removed:** Removed features
- **Fixed:** Bug fixes
- **Security:** Security improvements
