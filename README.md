# OpenTrainer Android App

An Android application that connects to a Wahoo Kickr Core trainer via Bluetooth Low Energy (BLE) and displays real-time training data with customizable workout resistance profiles.

## Features

- 🔍 Scan for nearby Kickr Core trainers
- 🔗 Connect via Bluetooth Low Energy
- 📊 Display real-time data:
  - Power (Watts)
  - Cadence (RPM)
  - Speed (km/h)
  - Heart Rate (BPM)
- 📈 Real-time charts:
  - Power history (last 20 seconds)
  - Speed history (last 20 seconds)
- 🏋️ **Workout Programming:**
  - Create custom resistance profiles with intervals
  - Set total workout duration
  - Define multiple intervals with specific duration and resistance percentage
  - Visual resistance profile chart showing the entire workout plan
  - Real-time progress indicator during workout execution
  - Automatic resistance control throughout the workout
- 💾 **Workout Management:**
  - Save named workout profiles for reuse
  - Load previously saved workouts
  - Delete unwanted workout profiles
  - Multiple workout profiles support
- 📱 Modern Material Design UI
- 🔄 Automatic data updates

## Requirements

- Android device with Bluetooth LE support
- Android 8.0 (API 26) or higher
- Wahoo Kickr Core trainer or compatible device

## Bluetooth Services Supported

The app implements standard Bluetooth SIG services:
- **Cycling Power Service (0x1818)** - For power measurements
- **Cycling Speed and Cadence Service (0x1816)** - For speed and cadence
- **Heart Rate Service (0x180D)** - For heart rate data (optional)
- **Fitness Machine Service (0x1826)** - For resistance control

### Resistance Control

The app uses multiple protocols to control trainer resistance:
1. **Wahoo Proprietary Protocol** - Direct resistance control via Wahoo's custom service
2. **FTMS Target Power** - Standard Fitness Machine Service power control
3. **FTMS Resistance Level** - Standard Fitness Machine Service resistance control
4. **Cycling Power Control Point** - Alternative power control method

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

## Usage

### Basic Training
1. Launch the app
2. Tap "Scan for Devices"
3. Grant Bluetooth permissions if prompted
4. Select your Kickr Core trainer from the list
5. View real-time training data and charts

### Workout Programming
1. After connecting to the trainer, tap "Setup Workout"
2. Enter a name for your workout profile
3. Set the total workout duration in minutes
4. Add intervals by specifying:
   - Duration (in minutes)
   - Target resistance (percentage)
5. The app validates that intervals add up to the total duration
6. Save your workout profile for future use
7. Tap "Start Workout" to begin

### During Workout
- The workout status card shows:
  - Elapsed time and remaining time
  - Current interval number
  - Target resistance for the current interval
- The resistance profile chart displays:
  - Blue filled area: Complete workout resistance plan
  - Red dashed line: Current position in the workout
  - Dynamic Y-axis: Scaled to 110% of maximum resistance for better visibility
- Resistance is automatically adjusted at each interval transition
- Tap "Stop Workout" to end early

### Managing Workout Profiles
- **Save**: After configuring a workout, it's automatically saved with the given name
- **Load**: Tap "Load Workout" to see all saved profiles, then select one to load or delete
- **Delete**: When loading, choose a workout and select "Delete" with confirmation

## Architecture

- **Kotlin** - Primary programming language
- **Coroutines & Flow** - For asynchronous operations and reactive data streams
- **Material Design 3** - Modern UI components
- **MVVM Pattern** - Clean separation of concerns
- **MPAndroidChart** - Real-time data visualization
- **SharedPreferences + JSON** - Workout profile persistence

## Project Structure

```
app/src/main/java/com/kickr/trainer/
├── MainActivity.kt                 # Main UI controller with workout execution
├── WorkoutSetupActivity.kt         # Workout configuration and management
├── adapter/
│   ├── DeviceAdapter.kt           # RecyclerView adapter for device list
│   └── IntervalAdapter.kt         # RecyclerView adapter for workout intervals
├── bluetooth/
│   ├── KickrBluetoothService.kt   # BLE service management & resistance control
│   └── GattAttributes.kt          # Bluetooth GATT UUIDs
└── model/
    ├── TrainerData.kt             # Training data model
    ├── KickrDevice.kt             # Device model
    ├── Workout.kt                 # Workout profile model
    └── WorkoutInterval.kt         # Interval model
```

## Technical Details

### Bluetooth Implementation

The app uses the standard Android Bluetooth LE APIs:
- `BluetoothLeScanner` for device discovery
- `BluetoothGatt` for GATT connection management
- Standard Bluetooth SIG service UUIDs

### Data Parsing

Power, speed, and cadence data are parsed according to the Bluetooth SIG specifications:
- Cycling Power Measurement characteristic (0x2A63)
- CSC Measurement characteristic (0x2A5B)
- Heart Rate Measurement characteristic (0x2A37)

### Resistance Control Implementation

The app attempts multiple protocols in sequence to ensure compatibility:

1. **Wahoo Proprietary**: Uses service UUID `a026ee0b-0a7d-4ab3-97fa-f1500f9feb8b` with command `0x42`
   - Maps resistance percentage to power (100% = 400W)
   
2. **FTMS Target Power**: Uses Fitness Machine Control Point (opcode `0x05`)
   - Sets target power in watts
   
3. **FTMS Resistance Level**: Uses Fitness Machine Control Point (opcode `0x04`)
   - Sets resistance level directly
   
4. **Cycling Power Control Point**: Alternative power control method
   - Used as fallback for maximum compatibility

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

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Acknowledgments

- Built for Wahoo Kickr Core trainers and compatible smart trainers
- Uses Bluetooth SIG standard services and Fitness Machine Service (FTMS)
- Chart visualization powered by MPAndroidChart
- Inspired by the need for a simple, native Android training app with programmable workouts
