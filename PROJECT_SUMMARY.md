# Project Summary: OpenTrainer Android App

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
│           │   ├── MainActivity.kt               # Main UI controller with workout execution (642 lines)
│           │   ├── WorkoutSetupActivity.kt       # Workout configuration & management (340+ lines)
│           │   ├── adapter/
│           │   │   ├── DeviceAdapter.kt          # RecyclerView adapter for BLE devices
│           │   │   └── IntervalAdapter.kt        # RecyclerView adapter for workout intervals
│           │   ├── bluetooth/
│           │   │   ├── GattAttributes.kt         # Bluetooth GATT UUIDs (includes FTMS)
│           │   │   └── KickrBluetoothService.kt  # Core BLE service with resistance control (426 lines)
│           │   └── model/
│           │       ├── KickrDevice.kt            # Device data model
│           │       ├── TrainerData.kt            # Training metrics data model
│           │       ├── Workout.kt                # Workout profile model with validation
│           │       └── WorkoutInterval.kt        # Workout interval model
│           └── res/
│               ├── layout/
│               │   ├── activity_main.xml         # Main screen with charts & workout status (386 lines)
│               │   ├── activity_workout_setup.xml# Workout configuration UI (234 lines)
│               │   ├── item_device.xml           # Device list item layout
│               │   └── item_interval.xml         # Interval list item layout
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
## Key Features Implemented

### 1. Bluetooth Low Energy (BLE) Implementation
- **Device Scanning:** Scans for all BLE devices (no filter for maximum compatibility)
- **GATT Connection:** Manages connection lifecycle with detailed logging
- **Service Discovery:** Automatically discovers and subscribes to:
  - Cycling Power Service (0x1818)
  - Cycling Speed and Cadence Service (0x1816)
  - Heart Rate Service (0x180D)
  - Fitness Machine Service (0x1826) - for resistance control

### 2. Data Parsing
Implements Bluetooth SIG specifications for:
- **Power Measurement:** Instantaneous power in watts
- **Cadence Calculation:** From crank revolution data (RPM)
- **Speed Calculation:** From wheel revolution data (km/h)
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
  - Validation ensures intervals sum to total duration
  - Input fields disabled when workout is complete
- **Workout Execution:**
  - CountDownTimer for precise interval transitions
  - Automatic resistance control at each interval
  - Real-time display of current interval, elapsed time, target resistance
  - Visual progress indicator on resistance profile chart
  - Stop workout functionality
- **Workout Profile Management:**
  - Save workouts with custom names
  - Load from multiple saved profiles (selection dialog)
  - Delete unwanted profiles (with confirmation)
  - Storage via SharedPreferences + JSON

### 5. Resistance Control
Multiple protocols for maximum compatibility:
1. **Wahoo Proprietary Protocol:** Uses custom service UUID with 0x42 command
### Dependencies
- AndroidX Core KTX 1.12.0
- AppCompat 1.6.1
- Material Design 3 1.11.0
- ConstraintLayout 2.1.4
- Lifecycle Runtime/ViewModel 2.7.0
- Activity KTX 1.8.2
- Coroutines 1.7.3
- **MPAndroidChart v3.1.0** - Real-time chart visualization

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
- **Android 12+ (API 31+):** BLUETOOTH_SCAN, BLUETOOTH_CONNECT
- **Android 10-11 (API 29-30):** ACCESS_FINE_LOCATION
- **Runtime Permissions:** Proper request flow with user prompts
## Code Statistics

| Component | Lines of Code | Description |
|-----------|--------------|-------------|
| MainActivity.kt | ~642 | Main UI, charts, workout execution |
| WorkoutSetupActivity.kt | ~340 | Workout configuration & management |
| KickrBluetoothService.kt | ~426 | BLE functionality with resistance control |
| activity_main.xml | ~386 | Main screen layout with charts |
| activity_workout_setup.xml | ~234 | Workout setup UI |
| DeviceAdapter.kt | ~50 | Device list adapter |
| IntervalAdapter.kt | ~45 | Interval list adapter |
| GattAttributes.kt | ~45 | UUID constants (including FTMS) |
| Data Models | ~120 | Workout, WorkoutInterval, TrainerData, KickrDevice |
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

6. **Workout Execution:**
   - User taps "Start Workout" after configuration
   - Intent passes workout data to MainActivity
   - Workout status card becomes visible (blue background)
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
   - Resistance control:
     - Tries Wahoo proprietary protocol first
     - Falls back to FTMS Target Power
     - Falls back to FTMS Resistance Level
     - Falls back to Cycling Power Control Point
     - All attempts logged for debugging

7. **Workout Management:**
   - **Load:** Opens dialog showing all saved workout names
     - Select workout → shows Load/Delete options
     - Load: Restores workout configuration to UI
     - Delete: Confirmation dialog → removes from storage
   - **Save:** Stores current workout with entered name
   - Multiple workouts can be saved and managed

8. **Disconnect:**
   - User taps "Disconnect" or "Stop Workout"
## Completed Features

- [x] Real-time data display (power, cadence, speed, HR)
- [x] BLE device scanning and connection
- [x] Charts and graphs for power/speed trends (20-second rolling window)
- [x] Interval training with programmable resistance
- [x] Visual workout profile with progress indicator
- [x] Save/load/delete workout profiles
- [x] Multiple resistance control protocols
- [x] Dynamic chart Y-axis scaling
- [x] Timer-based workout execution
- [x] Custom APK naming (OpenTrainer-1.0.apk)
## Known Limitations

1. **Single Device Connection:** Can only connect to one trainer at a time
2. **No Session Persistence:** Workout session data not saved to database
3. **No Calibration:** No zero offset or spindown calibration support
4. **Resistance Protocol Uncertainty:** Multiple protocols tried but effectiveness varies by device
5. **No Historical Data:** Cannot review past workout sessions
6. **Fixed Chart Window:** Power/speed charts limited to 20-second view
7. **Manual Interval Entry:** No workout templates or quick presets
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
- [x] Workout profile save/load/delete
- [x] Charts display correctly
- [x] Timer updates every second
- [x] Resistance profile chart shows complete workout
- [x] Progress indicator updates during workout
- [x] Dynamic Y-axis scaling works
## Deployment Ready

The app is ready to:
- ✅ Build debug APK (OpenTrainer-1.0.apk)
- ✅ Build release APK
- ✅ Install on physical devices
- ✅ Run in Android Studio
- ✅ Scan for BLE devices
- ✅ Display UI with charts
- ✅ Create and save workout profiles
- ✅ Execute timed workouts with resistance control
- ⚠️  Requires real Kickr Core for resistance control testing

## Next Steps

1. **Test with actual Kickr Core trainer**
   - Verify resistance commands work correctly
   - Test all four resistance protocols
   - Validate data accuracy against known values
2. **Hardware Validation:**
   - Confirm ERG mode engagement
   - Test interval transitions
   - Verify resistance percentage accuracy
3. **Extended Testing:**
   - Test workout profile persistence across app restarts
   - Test with multiple saved workouts (10+ profiles)
   - Test edge cases (0% resistance, 100% resistance)
   - Test long workouts (60+ minutes)
4. **Code Quality:**
   - Add unit tests for data parsing logic
   - Add unit tests for workout validation
   - Improve error handling for BLE failures
5. **Performance:**
   - Monitor chart performance with long sessions
   - Optimize SharedPreferences JSON parsing
6. **UX Improvements:**
   - Create proper launcher icons
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
