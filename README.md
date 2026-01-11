# OpenTrainer Android App

**🔓 Open Source | 🔒 Privacy First | 📶 Works Offline**

An Android application that connects to Wahoo Kickr Core and compatible smart trainers via Bluetooth Low Energy (BLE) to display real-time training data with customizable workout resistance profiles and GPX-based elevation workouts.

![OpenTrainer Demo](figs/example.gif)

## 📥 Installation

Download the latest APK from the [Releases](https://github.com/aothmane-control/OpenTrainer/releases) page.

## ✨ Why OpenTrainer?

- **🌟 Completely Open Source** - All code is available under CC BY-NC 4.0 License
- **🔐 Your Data Stays Private** - No user accounts, no profiles, no cloud services
- **📶 Works Offline** - No internet connection required for training (map tiles only needed when viewing maps)
- **🚫 Zero Tracking** - No analytics, no telemetry, no data collection
- **🆓 Always Free** - No subscriptions, no premium features, no ads

## Features

- 🔍 Scan for nearby Kickr Core trainers
- 🔗 Connect via Bluetooth Low Energy
- 📊 Display real-time data:
  - Power (Watts)
  - Cadence (RPM)
  - Speed (km/h) - calculated from power and wheel speed
  - Distance (km) - tracked throughout workout
- 📈 Real-time charts:
  - Power history (last 20 seconds)
  - Speed history (last 20 seconds)
- 🏋️ **Workout Programming:**
  - **Two workout modes: Resistance-based and Power-based (ERG mode)**
  - Create custom resistance profiles with intervals
  - Create custom power profiles with target wattage
  - Set total workout duration
  - Define multiple intervals with specific duration and resistance percentage or target power
  - Edit individual intervals without deleting others
  - Scrollable interval list for managing many intervals
  - Visual workout profile chart showing the entire workout plan (resistance % or power W)
  - Real-time progress indicator during workout execution
  - Automatic resistance or power control throughout the workout
  - **Pause and resume workouts** at any time
  - **Distance tracking** displayed in real-time during workout
  - **Sound notifications**: Musical beeps on interval changes and workout completion
- 💾 **Workout Management:**
  - Save named workout profiles for reuse
  - Load previously saved workouts
  - Delete unwanted workout profiles
  - Multiple workout profiles support
- 📊 **Workout History & Analytics:**
  - Automatic workout data recording (power, speed, cadence, resistance, distance)
  - Complete workout summary with interactive charts after each session
  - Historical workout viewer with searchable list
  - **Multi-select and batch delete** workouts
  - **View workout history even when disconnected**
  - Share workout results
  - All data stored locally on device
- 🗺️ **GPX Track Viewer & Elevation-Based Workouts:**
  - Load and visualize GPX files on OpenStreetMap
  - **Start GPX-based workouts with automatic resistance control**
  - **Resistance adapts dynamically based on elevation profile**
  - **Real-time position tracking based on distance traveled**
  - View live position on map during workout
  - Display track information (distance, elevation, gradient)
  - Support for power and cadence data in GPX
  - Interactive map with zoom and pan
  - Automatic calculation of gradient and resistance mapping
  - Distance tracking for all workout types
  - **Pause and resume GPX workouts**
- 📱 Modern Material Design UI with app toolbar
- 🔄 Automatic data updates
- ℹ️ About dialog with copyright information

## Requirements

- Android device with Bluetooth LE support
- Android 8.0 (API 26) or higher
- Wahoo Kickr Core trainer or compatible smart trainer
- **Internet connection only needed for map tile downloads** (optional, only when using map view)
- All training data stays on your device - no cloud sync, no external servers

## Bluetooth Services Supported

The app implements standard Bluetooth SIG services:
- **Fitness Machine Service (0x1826)** - Primary service for resistance and power control
  - Indoor Bike Data characteristic for power, speed, cadence
  - Fitness Machine Control Point for resistance and power commands
  - Set Target Resistance Level (opcode 0x04) for resistance-based workouts (0-100%)
  - Set Target Power (opcode 0x05) for power-based workouts (1-1000W)
  - Start/Resume command (opcode 0x07) for activating speed reporting
  - Set Wheel Circumference (opcode 0x13) for accurate speed calculation
- **Cycling Power Service (0x1818)** - For power measurements (optional, not used with FTMS)
- **Cycling Speed and Cadence Service (0x1816)** - For speed and cadence (optional, not used with FTMS)

### Workout Control Modes

The app supports two workout control modes via Fitness Machine Service (FTMS):

#### Resistance Mode
- **Set Target Resistance Level (opcode 0x04)** - Direct resistance percentage control (0-100%)
- Sends resistance commands with 200ms minimum delay between commands to prevent BLE queue overflow
- Automatically requests control before each resistance change
- User controls effort by adjusting cadence; power output varies
- Compatible with Wahoo Kickr and other FTMS-compliant trainers

#### Power Mode (ERG Mode)
- **Set Target Power (opcode 0x05)** - Direct power target in watts (1-1000W)
- Sends power commands with 200ms minimum delay between commands
- Automatically requests control before each power change
- Trainer automatically adjusts resistance to maintain target power regardless of cadence
- Ideal for structured training with specific power zones
- Compatible with trainers supporting FTMS power control

## Installation

1. Clone this repository
2. Open the project in Android Studio
3. Build and run on your Android device

```bash
./gradlew assembleDebug
```

## Permissions

The app requires the following permissions:
- `BLUETOOTH_SCAN` - To scan for BLE devices (Android 12+)
- `BLUETOOTH_CONNECT` - To connect to BLE devices (Android 12+)
- `ACCESS_FINE_LOCATION` - Required for BLE scanning on Android 10-11
- `BLUETOOTH` - Legacy Bluetooth permission (Android 11 and below)
- `BLUETOOTH_ADMIN` - Legacy Bluetooth admin permission (Android 11 and below)
- `INTERNET` - Only used for downloading map tiles when viewing GPX routes on the map (optional feature)
### Workout Programming
1. After connecting to the trainer, tap "Setup Workout"
2. Enter a name for your workout profile
3. **Choose workout mode:**
   - **Resistance Mode**: Control resistance percentage (0-100%)
   - **Power Mode**: Control target power in watts (1-1000W)
4. Set the total workout duration in minutes
5. Add intervals by specifying:
   - Duration (in minutes)
   - Target resistance (percentage) OR target power (watts)
6. The app validates that intervals add up to the total duration
7. Save your workout profile for future use
8. Tap "Start Workout" to begin

### During Workout
- The workout status card shows:
  - Elapsed time and remaining time
  - Current interval number
  - Target resistance (%) or target power (W) for the current interval
  - **Current distance traveled**
- The workout profile chart displays:
  - Blue filled area: Complete workout profile (resistance or power)
  - Red dashed line: Current position in the workout
  - Dynamic Y-axis: Scaled to 110% of maximum value for better visibility
  - Axis labels showing resistance (%) or power (W) based on workout type
- Resistance or power is automatically adjusted at each interval transition
- **Sound notifications:**
  - Two-tone beep (G5→C6) when starting a new interval
  - Four-tone ascending chord (C4-E4-G4-C5) when workout completes
- Distance is tracked automatically for all workouts
- All data (power, speed, cadence, resistance, distance) is recorded every second
- **Pause/Resume**: Tap "Pause Workout" to pause, then "Resume Workout" to continue
  - Timer stops during pause
  - Distance tracking pauses
  - Data recording pauses
- **Stop Early**: Tap "Stop Workout" to end the workout prematurely
  - Workout is saved to history
  - Summary screen is displayedworkout resistance plan
  - Red dashed line: Current position in the workout
  - Dynamic Y-axis: Scaled to 110% of maximum resistance for better visibility
  - Axis labels showing resistance values (0-100%+)
- Resistance is automatically adjusted at each interval transition
- Distance is tracked automatically for all workouts
- All data (power, speed, cadence, resistance, distance) is recorded every second
- **Pause/Resume**: Tap "Pause Workout" to pause, then "Resume Workout" to continue
  - Timer stops during pause
  - Distance tracking pauses
  - Data recording pauses
- **Stop Early**: Tap "Stop Workout" to end the workout prematurely
  - Workout is saved to history
  - Summary screen is displayed

### After Workout
- Workout summary screen displays automatically with:
  - Duration, distance, and average power statistics
### Managing Workout Profiles
- **Save**: After configuring a workout, it's automatically saved with the given name
- **Load**: Tap "Load Workout" to see all saved profiles, then select one to load or delete
  - Saved workouts retain their type (Resistance or Power mode)
  - Loading a workout automatically switches to the correct mode
- **Edit**: Use the edit button on any interval to modify duration and resistance/power
  - Edit dialog adapts to workout type (shows "Resistance (0-100%)" or "Power (watts)")
- **Delete**: When loading, choose a workout and select "Delete" with confirmation

### Managing Workout Profiles
- **Save**: After configuring a workout, it's automatically saved with the given name
- **Load**: Tap "Load Workout" to see all saved profiles, then select one to load or delete
- **Edit**: Use the edit button on any interval to modify duration and resistance
- **Delete**: When loading, choose a workout and select "Delete" with confirmation

### Viewing Workout History
- Tap "View History" to see all completed workouts (available even when disconnected)
- Workouts are sorted by date (newest first)
- Each entry shows: name, type, date, duration, distance, and average power
- Tap any workout to view complete details and charts
- **Delete workouts:**
  - **Single delete**: Tap the trash icon on any workout
  - **Batch delete**: Long-press a workout to enter selection mode
    - Checkboxes appear for multi-select
    - Tap workouts to select/deselect
    - Tap "Delete Selected" in the toolbar
    - Confirm deletion of all selected workouts

## Architecture

- **Kotlin** - Primary programming language
- **Coroutines & Flow** - For asynchronous operations and reactive data streams
- **Material Design 3** - Modern UI components with toolbar
- **MVVM Pattern** - Clean separation of concerns
- **MPAndroidChart** - Real-time data visualization and workout analytics
- **osmdroid** - OpenStreetMap integration for GPX visualization
- **SharedPreferences + JSON** - Workout profile persistence
- **Local JSON Storage** - Workout history persistence

## Project Structure

```
app/src/main/java/com/kickr/trainer/
├── MainActivity.kt                 # Main UI controller with workout execution
├── WorkoutSetupActivity.kt         # Workout configuration and management
├── GpxMapActivity.kt              # GPX file viewer and workout starter
├── WorkoutSummaryActivity.kt      # Post-workout summary and charts
├── WorkoutHistoryActivity.kt      # Workout history viewer
├── GpxWorkoutHolder.kt            # Singleton for GPX track data
├── adapter/
│   ├── DeviceAdapter.kt           # RecyclerView adapter for device list
│   ├── IntervalAdapter.kt         # RecyclerView adapter for workout intervals
│   └── WorkoutHistoryAdapter.kt   # RecyclerView adapter for workout history
├── bluetooth/
│   ├── KickrBluetoothService.kt   # BLE service management & resistance control
│   └── GattAttributes.kt          # Bluetooth GATT UUIDs
├── model/
│   ├── TrainerData.kt             # Training data model
│   ├── KickrDevice.kt             # Device model
│   ├── Workout.kt                 # Workout profile model
│   ├── WorkoutInterval.kt         # Interval model
│   ├── GpxTrack.kt                # GPX track data model
│   ├── GpxTrackPoint.kt           # GPX point with elevation data
│   ├── WorkoutDataPoint.kt        # Single workout data recording
│   └── WorkoutHistory.kt          # Complete workout history record
└── utils/
    ├── GpxParser.kt               # GPX file parsing
    └── WorkoutStorageManager.kt   # Workout history storage
```

## Technical Details

### Bluetooth Implementation

The app uses the standard Android Bluetooth LE APIs:
- `BluetoothLeScanner` for device discovery
- `BluetoothGatt` for GATT connection management
- Standard Bluetooth SIG service UUIDs

### Resistance Control Implementation

The app uses the Fitness Machine Service (FTMS) for reliable workout control with two modes:

#### Resistance Mode (opcode 0x04)
1. **Set Target Resistance Level**: Direct resistance percentage control
   - Sends resistance value in 0.1% resolution (0-1000 units = 0-100%)
   - Example: 50% resistance = 500 units
   
2. **Control Flow**:
   - Request Control (opcode 0x00) before each resistance change
   - Wait for write confirmation
   - Send resistance command
   - 200ms minimum delay between commands to prevent queue overflow

#### Power Mode (opcode 0x05)
1. **Set Target Power**: Direct power target in watts
   - Sends power value in 1W resolution as signed 16-bit integer
   - Range: 1-1000W
   - Example: 200W = 200 (0x00C8)
   
2. **Control Flow**:
   - Request Control (opcode 0x00) before each power change
   - Wait for write confirmation
   - Send power command
### Workout Profile Storage

Workouts are stored in SharedPreferences as JSON with support for both resistance and power modes:
```json
{
  "Workout Name": {
    "totalDurationSeconds": 3600,
    "type": "RESISTANCE",
    "intervals": [
      {"duration": 300, "resistance": 50, "power": 0},
      {"duration": 600, "resistance": 75, "power": 0},
      {"duration": 300, "resistance": 60, "power": 0}
    ]
  },
  "Power Intervals": {
    "totalDurationSeconds": 1800,
    "type": "POWER",
    "intervals": [
      {"duration": 300, "resistance": 0, "power": 150},
      {"duration": 600, "resistance": 0, "power": 200},
      {"duration": 300, "resistance": 0, "power": 180}
    ]
  }
}
``` Durations: 150ms, 150ms, 150ms, 450ms (longer final note)
- Sounds are programmatically generated as WAV files with fade in/out envelopes
- Uses `AudioAttributes.USAGE_MEDIA` stream (always audible, independent of notification volume)
   - Request Control (opcode 0x00) before each resistance change
   - Wait for write confirmation
   - Send resistance command
   - 200ms minimum delay between commands to prevent queue overflow

3. **Speed Activation**:
   - Set Wheel Circumference (opcode 0x13) to 2100mm (700c wheel)
   - Send Start/Resume command (opcode 0x07) to activate proper speed reporting
   - Required for Wahoo Kickr to report accurate speed values

### Workout Profile Storage

Workouts are stored in SharedPreferences as JSON:
```json
{
  "Workout Name": {
    "totalDurationSeconds": 3600,
    "intervals": [
      {"duration": 300, "resistance": 50},
      {"duration": 600, "resistance": 75},
      {"duration": 300, "resistance": 60}
    ]
  }
}
```

### Workout History Storage

Completed workouts are saved as individual JSON files in the app's internal storage:
- File format: `workout_YYYYMMDD_HHMMSS.json`
- Location: App internal files directory
- Each file contains:
  - Workout metadata: name, type, start/end timestamps
  - Statistics: average and maximum values for power, speed, cadence
  - Data points: Complete second-by-second recordings including:
    - timestamp, elapsedSeconds, power, speed, cadence, resistance, distance

Example structure:
```json
{
  "name": "Morning Ride",
  "type": "RESISTANCE",
  "startTime": 1735000000000,
  "endTime": 1735003600000,
  "averagePower": 180.5,
  "maxPower": 320.0,
  "averageSpeed": 25.3,
  "maxSpeed": 38.2,
  "averageCadence": 85,
  "maxCadence": 110,
  "totalDistance": 15.5,
  "dataPoints": [
    {"timestamp": 1735000000000, "elapsedSeconds": 0, "power": 150.0, ...},
    {"timestamp": 1735000001000, "elapsedSeconds": 1, "power": 155.0, ...}
  ]
}
```

## Troubleshooting

**Device not found:**
- Ensure your Kickr is powered on
- Make sure Bluetooth is enabled on your phone
- Try moving closer to the trainer

**Connection failed:**
- Restart the Kickr trainer
- Restart Bluetooth on your phone
- Close other apps that might be using Bluetooth

**No data displayed:**
- Ensure you're pedaling (the trainer needs movement to generate data)
- Check that the trainer is not connected to another device

**Resistance not changing:**
- Verify the trainer supports ERG mode or resistance control
- Check Bluetooth logs for resistance command acknowledgment
- Try disconnecting and reconnecting the trainer

**Workout not starting:**
- Ensure all intervals sum to the total workout duration
- Check that the workout name is not empty when saving
- Verify the device is properly connected before starting

## License

This project is open source and available under the CC BY-NC 4.0 License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
