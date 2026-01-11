# Changelog

All notable changes to OpenTrainer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.2] - 2026-01-11

### Added - Power-Based Workouts (ERG Mode)
- **Workout Type Selection:** Choose between Resistance mode (0-100%) or Power mode (1-1000W) when creating workouts
- **FTMS Power Control:** Implemented Set Target Power (opcode 0x05) for direct wattage control
- **ERG Mode Support:** Trainer automatically maintains target power by adjusting resistance based on cadence
- **Power Profile Chart:** Workout profile chart adapts to show power (W) for power workouts
- **Dynamic UI:** Input hints, validation, and display automatically switch between resistance and power modes
- **Interval Display:** Interval list shows appropriate units (% or W) based on workout type
- **Save/Load Power Workouts:** Workout storage updated to preserve workout type and both resistance/power values

### Added - Sound Notifications
- **Interval Change Sound:** Musical two-tone beep (G5→C6, 80ms each) when starting new intervals
- **Workout Complete Sound:** Four-tone ascending major chord (C4-E4-G4-C5) with longer final note (450ms)
- **Programmatic Sound Generation:** WAV files generated on-the-fly with proper audio envelopes (10% fade-in, 20% fade-out)
- **Media Stream Audio:** Uses USAGE_MEDIA to ensure sounds are always audible regardless of notification volume
- **SoundPool Implementation:** Efficient sound playback with low latency

### Changed
- **Workout Model:** Added `WorkoutType` enum (RESISTANCE, POWER) and `power` field to `WorkoutInterval`
- **Workout Validation:** Updated to validate either resistance (0-100%) or power (1-1000W) based on type
- **Chart Functions:** `buildWorkoutProfileChart()` and `updateWorkoutProfileProgress()` now respect workout type
- **Interval Editing:** Edit dialog adapts to workout type with appropriate hints and validation
- **IntervalAdapter:** Displays correct value (% or W) based on workout type, updates when type changes
- **Version:** Updated to 2.2 (versionCode=4, versionName="2.2")
- **APK Naming:** Changed to OpenTrainer-2.2.apk

### Fixed
- **Workout Save/Load:** Fixed power workouts not saving/loading correctly - now stores workout type and power values
- **Interval Display:** Fixed power workouts showing "0%" in interval list - now shows power in watts
- **Chart Labels:** Fixed workout profile chart showing "Target Resistance (%)" for power workouts
- **Interval Editing:** Fixed edit dialog only supporting resistance - now adapts to workout type

### Removed
- **Low-Pass Filter:** Removed power smoothing filter (alpha=0.85) for more responsive power display
- **Filtered Power Variables:** Removed `filteredPower`, `filteredSpeed`, and `smoothingAlpha` variables

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
- Open source (CC BY-NC 4.0 License)

---

## Legend
- **Added:** New features
- **Changed:** Changes to existing functionality
- **Deprecated:** Soon-to-be removed features
- **Removed:** Removed features
- **Fixed:** Bug fixes
- **Security:** Security improvements
