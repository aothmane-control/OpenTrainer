# Quick Start Guide - Kickr Trainer App

## 🚀 Getting Started in 5 Minutes

### Step 1: Open in Android Studio
```bash
# Open Android Studio
# File → Open → Navigate to: /home/aothmane/Documents/privat/kickr_android
```

### Step 2: Sync Project
- Wait for Android Studio to sync Gradle (bottom right corner)
- This may take 1-2 minutes on first run

### Step 3: Connect Your Android Device
1. Enable Developer Mode on your phone:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → Developer Options → Enable "USB Debugging"
3. Connect phone via USB cable
4. Accept the "Allow USB Debugging" prompt on your phone

### Step 4: Run the App
- Click the green "Run" button (▶️) in Android Studio
- Or press: `Shift + F10`
- Select your device and click OK

### Step 5: Use the App
1. **Turn on your Kickr Core trainer**
2. In the app, tap **"Scan for Devices"**
3. Grant Bluetooth permissions when prompted
4. Your Kickr should appear in the list
5. Tap on your Kickr to connect
6. Start pedaling to see data!

---

## 📱 What You'll See

### When Not Connected:
- "Disconnected" status (red)
- "Scan for Devices" button
- All metrics showing zeros

### When Connected:
- "Connected" status (green)
- Real-time metrics updating:
  - **POWER**: Watts you're producing
  - **CADENCE**: Pedal RPM
  - **SPEED**: Simulated km/h
  - **HEART RATE**: BPM (if HR sensor connected)

---

## 🔧 Command Line Build (Alternative)

If you prefer command line:

```bash
cd /home/aothmane/Documents/privat/kickr_android

# Build the app
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or do both at once
./gradlew installDebug
```

The APK location:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## ❓ Troubleshooting

### "Bluetooth permissions required"
- Go to: Settings → Apps → Kickr Trainer → Permissions
- Enable: Location (Android 10-11) or Bluetooth (Android 12+)

### "No devices found"
- Make sure Kickr is powered on (LED should be blue)
- Kickr is not connected to another device (Zwift, etc.)
- Move phone closer to trainer
- Try turning Kickr off and on

### "App won't install"
- Check that device is Android 8.0 or higher
- Make sure USB Debugging is enabled
- Try: `adb devices` to verify device is connected

### "Gradle sync failed"
- Check internet connection (needs to download dependencies)
- Try: File → Invalidate Caches → Invalidate and Restart

---

## 📊 Expected Behavior

| Action | Result |
|--------|--------|
| Start pedaling | Power increases immediately |
| Pedal faster | Cadence increases |
| Stop pedaling | All values drop to zero |
| Disconnect | Returns to scanning mode |

---

## 💡 Tips

1. **First Connection**: May take 5-10 seconds to establish
2. **Data Updates**: Refresh every ~1 second
3. **Power Range**: Expect 0-500W for typical riders
4. **Cadence Range**: Typically 60-100 RPM
5. **Bluetooth Range**: Keep phone within 5-10 meters

---

## 🎯 Next Steps

Once the basic app works:
- Check BUILD.md for advanced build options
- Read PROJECT_SUMMARY.md for technical details
- See README.md for full documentation

---

## 🆘 Need Help?

1. Check the logs: `adb logcat -s "KickrBluetoothService"`
2. Verify permissions are granted
3. Restart both app and trainer
4. Check that trainer firmware is up to date

---

**App is ready to use! Just open in Android Studio and run! 🎉**
