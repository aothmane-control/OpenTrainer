# Building and Running the Kickr Trainer App

## Prerequisites

1. **Android Studio** (recommended version: Hedgehog | 2023.1.1 or newer)
   - Download from: https://developer.android.com/studio

2. **Android SDK**
   - Minimum SDK: API 26 (Android 8.0)
   - Target SDK: API 34 (Android 14)
   - Build Tools: 34.0.0

3. **Java Development Kit (JDK)**
   - JDK 8 or higher (JDK 17 recommended)

## Setup Instructions

### Option 1: Using Android Studio (Recommended)

1. **Open the project:**
   ```
   File → Open → Select the kickr_android directory
   ```

2. **Sync Gradle:**
   - Android Studio will automatically prompt to sync Gradle
   - Or click: File → Sync Project with Gradle Files

3. **Connect your Android device:**
   - Enable Developer Options on your device:
     - Go to Settings → About Phone
     - Tap "Build Number" 7 times
   - Enable USB Debugging:
     - Go to Settings → Developer Options
     - Enable "USB Debugging"
   - Connect via USB cable

4. **Run the app:**
   - Click the "Run" button (green triangle) or press Shift+F10
   - Select your device from the list

### Option 2: Command Line Build

1. **Build the APK:**
   ```bash
   cd /home/aothmane/Documents/privat/kickr_android
   ./gradlew assembleDebug
   ```

2. **Install on device:**
   ```bash
   ./gradlew installDebug
   ```

3. **Build and install in one step:**
   ```bash
   ./gradlew installDebug
   ```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## First Time Setup

If you don't have the Gradle wrapper installed:

```bash
cd /home/aothmane/Documents/privat/kickr_android

# Download Gradle wrapper
gradle wrapper --gradle-version 8.2

# Make gradlew executable
chmod +x gradlew
```

## Testing Without a Physical Kickr

For development and testing without a real Kickr Core trainer, you can:

1. Use a **Bluetooth LE simulator** app on another device
2. Modify the device filter to connect to any BLE device with Cycling Power Service
3. Use the Android Emulator with Bluetooth support (requires specific setup)

## Troubleshooting

### Gradle Build Issues

**Problem:** `SDK location not found`
```bash
# Create local.properties file
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

**Problem:** `Gradle sync failed`
```bash
# Clean and rebuild
./gradlew clean
./gradlew build --refresh-dependencies
```

### Runtime Issues

**Problem:** App crashes on startup
- Check that your device has Android 8.0 or higher
- Verify Bluetooth permissions in Settings → Apps → Kickr Trainer → Permissions

**Problem:** Cannot scan for devices
- Ensure Bluetooth is enabled
- Grant location permissions (required for BLE scanning on Android 10-11)
- For Android 12+, grant Bluetooth scan/connect permissions

### Build Configuration

If you need to change the build configuration:

Edit `app/build.gradle.kts`:
```kotlin
android {
    compileSdk = 34  // Change SDK versions here
    
    defaultConfig {
        minSdk = 26
        targetSdk = 34
        // ...
    }
}
```

## Building a Release APK

1. **Generate a keystore** (first time only):
   ```bash
   keytool -genkey -v -keystore kickr-release-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias kickr-key
   ```

2. **Create `keystore.properties`**:
   ```properties
   storePassword=yourStorePassword
   keyPassword=yourKeyPassword
   keyAlias=kickr-key
   storeFile=../kickr-release-key.jks
   ```

3. **Update `app/build.gradle.kts`** to use the keystore (if needed)

4. **Build release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

The signed APK will be at:
```
app/build/outputs/apk/release/app-release.apk
```

## Developer Notes

### Code Style
- Kotlin style guide: https://kotlinlang.org/docs/coding-conventions.html
- Use 4 spaces for indentation
- Maximum line length: 120 characters

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

### Debugging
- Use Android Studio's debugger with breakpoints
- View Bluetooth logs: `adb logcat -s "KickrBluetoothService"`
- Monitor all logs: `adb logcat`

## Useful ADB Commands

```bash
# View connected devices
adb devices

# Install APK manually
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View app logs
adb logcat -s "KickrBluetoothService"

# Clear app data
adb shell pm clear com.kickr.trainer

# Uninstall app
adb uninstall com.kickr.trainer
```

## Project Information

- **Package Name:** com.kickr.trainer
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 34 (Android 14)
- **Language:** Kotlin
- **Build System:** Gradle with Kotlin DSL

## Support

For issues or questions:
1. Check the logs: `adb logcat`
2. Review the README.md for common issues
3. Verify all permissions are granted
4. Ensure your Kickr Core is powered on and not connected to other devices
