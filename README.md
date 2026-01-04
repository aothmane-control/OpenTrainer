# Kickr Trainer Android App

An Android application that connects to a Wahoo Kickr Core trainer via Bluetooth Low Energy (BLE) and displays real-time training data.

## Features

- 🔍 Scan for nearby Kickr Core trainers
- 🔗 Connect via Bluetooth Low Energy
- 📊 Display real-time data:
  - Power (Watts)
  - Cadence (RPM)
  - Speed (km/h)
  - Heart Rate (BPM)
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

1. Launch the app
2. Tap "Scan for Devices"
3. Grant Bluetooth permissions if prompted
4. Select your Kickr Core trainer from the list
5. View real-time training data

## Architecture

- **Kotlin** - Primary programming language
- **Coroutines & Flow** - For asynchronous operations and reactive data streams
- **Material Design 3** - Modern UI components
- **MVVM Pattern** - Clean separation of concerns

## Project Structure

```
app/src/main/java/com/kickr/trainer/
├── MainActivity.kt                 # Main UI controller
├── adapter/
│   └── DeviceAdapter.kt           # RecyclerView adapter for device list
├── bluetooth/
│   ├── KickrBluetoothService.kt   # BLE service management
│   └── GattAttributes.kt          # Bluetooth GATT UUIDs
└── model/
    ├── TrainerData.kt             # Training data model
    └── KickrDevice.kt             # Device model
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

## License

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Acknowledgments

- Built for Wahoo Kickr Core trainers
- Uses Bluetooth SIG standard services
- Inspired by the need for a simple, native Android training app
