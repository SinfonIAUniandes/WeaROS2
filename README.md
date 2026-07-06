# WeaROS2

A Wear OS app that turns a smartwatch into a ROS 2 node, publishing its onboard sensors directly onto a ROS 2 network over Wi-Fi using [jros2](https://github.com/ihmcrobotics/jros2) (Fast-DDS).

Tested on a Samsung Galaxy Watch 7 running Wear OS 5.

## What it does

The app runs a foreground `ROS2Node` on the watch and publishes each enabled sensor to its own topic:

| Sensor | Default topic | Notes |
|---|---|---|
| IMU | `imu` | Accelerometer/gyro |
| GPS | `gps` | Requires location permission |
| Mic | `audio` | Requires microphone permission |
| Samsung SpO2 & Heart Rate | `samsung_health` | Uses Samsung Health Sensor API |
| Daily Steps | `steps_daily` | Via Health Services passive monitoring |
| Daily Floors | `floors_daily` | Via Health Services passive monitoring |

It also runs one receiver in the other direction — subscribing to an audio topic and playing it through the watch speaker:

| Receiver | Default topic | Notes |
|---|---|---|
| Speaker | `play_audio` | Subscribes and plays incoming audio; expects the same 16kHz mono PCM16 wire format `Mic` publishes (an optional one-off 44-byte WAV header message is auto-detected and skipped) |

Topics are published/subscribed under a configurable namespace (default `watch`, e.g. `/watch/imu`), and each sensor/receiver's topic name can be overridden individually from in-app Settings, along with the ROS 2 domain ID.

From the watch face:
- Toggle individual sensors on/off
- Start/stop the ROS 2 bridge
- View live message counts and a rolling log for debugging
- Jump to OS permission settings if a required permission is missing

## Requirements

- Android Studio with the Wear OS SDK
- A Wear OS 5 device or emulator (minSdk 31, compiled/target SDK 36)
- The watch and the ROS 2 network must be on the same Wi-Fi/multicast-capable network (the app acquires a Wi-Fi multicast lock for DDS discovery)

## Building

```bash
./gradlew assembleDebug
```

Install to a connected watch/emulator:

```bash
./gradlew installDebug
```

### Native library notes

This app links `jros2`'s native Fast-DDS libraries (`armeabi-v7a` and `arm64-v8a`), which requires `org.bytedeco:javacpp:1.5.9` and `useLegacyPackaging = true` in [app/build.gradle.kts](app/build.gradle.kts). Most Wear OS 5 hardware (including the Galaxy Watch 7) runs a 32-bit (`armeabi-v7a`) userspace despite having a 64-bit chip, so the `armeabi-v7a` native build must be present or the app fails silently at ROS 2 startup. See [docs/WEAROS_NATIVE_FIX_EXPLAINED.md](docs/WEAROS_NATIVE_FIX_EXPLAINED.md) for the full background on why these workarounds exist and how to rebuild the native libraries if the C++ side changes.

## Permissions

Requested at runtime: fine/coarse location, record audio, body sensors, activity recognition. Also declared: network/Wi-Fi state, multicast, wake lock, and background body sensors.

## Project structure

```
app/src/main/java/com/jros2/wearos2/
├── presentation/        # Compose UI (MainActivity, home & settings screens, theme)
├── ros/
│   ├── WearSensorBridge.kt   # Owns the ROS2Node, starts/stops sensors
│   └── sensors/              # One class per sensor/receiver (IMU, GPS, mic, steps, floors, Samsung PPG, speaker playback)
└── SettingsManager.kt   # Persists domain ID, namespace, and per-sensor topic names
```
