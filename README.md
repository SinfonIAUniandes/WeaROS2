# WeaROS2

A Wear OS app that turns a smartwatch into a ROS 2 node, publishing its onboard sensors directly onto a ROS 2 network over Wi-Fi using the fork [SinfonIAUniandes/jros2](https://github.com/SinfonIAUniandes/jros2), forked from [ihmcrobotics/jros2](https://github.com/ihmcrobotics/jros2) (Fast-DDS).

Tested on a Samsung Galaxy Watch 7 running Wear OS 5.

## What it does

The app runs a foreground `ROS2Node` on the watch and publishes each enabled sensor to its own topic:

| Sensor | Default topic | Notes |
|---|---|---|
| IMU | `imu` | Accelerometer/gyro |
| GPS | `gps` | Requires location permission |
| Mic | `audio` | Requires microphone permission |
| Samsung SpO2 & Heart Rate | `samsung_health/heart_rate`, `samsung_health/spo2` | Uses the Samsung Health Sensor API. Heart rate streams continuously (`mobile_sensor_msgs/SamsungHealthHeartRate`). SpO2 (`mobile_sensor_msgs/SamsungHealthSpO2`) is a one-shot on-demand measurement started from the **Measure SpO2** button on the home screen — hold your wrist still until it completes (or times out after 35 s). Needs the `android.permission.health.READ_HEART_RATE` / `READ_OXYGEN_SATURATION` permissions on Android 16, and Health Platform dev mode enabled on the watch |
| Daily Steps | `steps_daily` | Via Health Services passive monitoring |
| Daily Floors | `floors_daily` | Via Health Services passive monitoring |

It also runs receivers in the other direction — subscribing to topics and acting on the watch:

| Receiver | Default topic | Type | Notes |
|---|---|---|---|
| Speaker | `play_audio` | `audio_common_msgs/AudioData` | Subscribes and plays incoming audio; expects the same 16kHz mono PCM16 wire format `Mic` publishes (an optional one-off 44-byte WAV header message is auto-detected and skipped) |
| Notify | `notify` | `std_msgs/String` | Pops up an Android heads-up notification for every message; the published string becomes the notification text. Requires the POST_NOTIFICATIONS permission (requested at runtime on Wear OS 5). Trigger it with `ros2 topic pub --once /watch/notify std_msgs/String "{data: 'Hello watch'}"` |

Interactive **controls** on the home screen let you publish on demand (all use generic `std_msgs` / `sensor_msgs` types):

| Control | Default topic | Type | Notes |
|---|---|---|---|
| Joystick | `joy` | `sensor_msgs/Joy` | Full-screen touch joystick. `axes[0]` = X (right positive), `axes[1]` = Y (up positive), each normalized to [-1, 1]. Publishes on every touch move and once with (0, 0) on release. |
| Publish button | `button` | `std_msgs/Bool` | Publishes `data: true` each time you tap the **Publish button** card. (`std_msgs/Empty` would be the more literal trigger, but jros2 serializes it as zero bytes, which isn't wire-compatible with rosidl's Empty — it breaks `ros2 topic echo` and rqt.) |
| Slider | `slider` | `std_msgs/Float32` | Full-screen round slider for a generic scalar (robot volume, speed limit, brightness…). Turn the rotating bezel/crown or drag around the ring; publishes a normalized 0.0–1.0 value on every change. |

Opening the Joystick or SpO2 auto-starts the bridge; tap the back arrow to return.

Topics are published/subscribed under a configurable namespace (default `watch`, e.g. `/watch/imu`), and each sensor/receiver/control's topic name can be overridden individually from in-app Settings, along with the ROS 2 domain ID.

### The UI

A single, icon-driven home screen keeps the app focused on its one job:

- **Big central button** starts/stops publishing (green play → red stop).
- **Slider ring** hugging the watch edge (drag it or turn the rotating bezel) publishes the `slider` value; while you're using it the center buttons are blocked to avoid mis-taps.
- **Publish button** (the broadcast icon) sends a one-off `button` message.
- Icons open the sub-screens: **Joystick**, **SpO2** (a measurement screen with a progress ring), **Settings**, and **Logs**.

**Settings** edits the domain id, namespace, and per-feature topic names, and has a toggle to enable/disable each feature — all persisted. **Logs** shows the rolling activity log.

## Running in the background

Starting the bridge launches a **foreground service** ([`service/BridgeService.kt`](app/src/main/java/com/jros2/wearos2/service/BridgeService.kt)) that owns the ROS 2 node, holds the Wi-Fi multicast lock and a partial wake lock, and shows an ongoing "bridge running" notification. This means the bridge keeps publishing while the app is **minimized or the screen is off** — you don't need to keep it on screen.

The service is tied to the app's task: **closing the app** (swiping it away from recents) stops the bridge via `onTaskRemoved`. So minimizing keeps it running, closing shuts it down. You can also stop it from the notification's **Stop** action or the in-app Stop button.

The bridge is a process-wide singleton ([`ros/RosBridgeHolder.kt`](app/src/main/java/com/jros2/wearos2/ros/RosBridgeHolder.kt)) shared between the UI and the service, so the on-screen state stays in sync when you reopen the app.

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

Requested at runtime: fine/coarse location, record audio, body sensors, activity recognition, notifications (Android 13+), and — on Android 16 (Baklava) — the granular `health.READ_HEART_RATE` / `health.READ_OXYGEN_SATURATION` permissions that the Samsung Health Sensor API now requires instead of `BODY_SENSORS`. Also declared: network/Wi-Fi state, multicast, wake lock, and background body sensors.

For the Samsung heart-rate/SpO2 sensor to deliver data you must also enable **Health Platform developer mode** on the watch: Settings → Apps → Health Platform, then tap the "Health Platform" title ~10 times until `[Dev mode]` appears.

## Project structure

```
app/src/main/java/com/jros2/wearos2/
├── presentation/             # Compose UI, one file per screen
│   ├── MainActivity.kt       # Activity: lifecycle, permissions, screen navigation
│   ├── UiKit.kt              # Shared palette + Canvas-drawn icon buttons (no icon lib)
│   ├── MainScreen.kt         # Home: hero start/stop, nav icons, publish, integrated slider
│   ├── SettingsScreen.kt     # Domain id, namespace, per-feature enable toggle + topic
│   ├── JoystickScreen.kt     # Full-screen touch joystick
│   ├── Spo2Screen.kt         # On-demand SpO2 measurement with a progress ring
│   ├── LogsScreen.kt         # Scrollable activity log
│   └── theme/
├── ros/
│   ├── WearSensor.kt         # The feature interface (publishers, subscribers, controls)
│   ├── BaseWearSensor.kt     # Boilerplate base (id/name/topic + state flows)
│   ├── Ros2Support.kt        # stampHeader(), reliableQos(), resolveTopic() helpers
│   ├── WearSensorBridge.kt   # Owns the ROS2Node; starts/stops every feature generically
│   └── sensors/              # One file per feature (IMU, GPS, mic, steps, floors,
│                             #   Samsung PPG, speaker playback, notifications, joystick)
└── SettingsManager.kt        # Persists domain id, namespace, per-feature topic names
```

### Adding a new feature

Every publisher/subscriber/control implements the `WearSensor` interface — usually by
extending `BaseWearSensor`, which supplies the id/name/topic identity and the
`enabled` / `messageCount` / `displayValue` state. To add one:

1. Create a class under `ros/sensors/` extending `BaseWearSensor(id, name, defaultTopic)`,
   and implement `start(node, context)` / `stop()`. Read `resolvedTopicName` when creating
   your publisher/subscription (the bridge fills it in with the namespace + any override).
2. Add one instance to `WearSensorBridge.sensors`.

That's it — the bridge resolves the topic and drives its lifecycle generically (no
per-type code), and it shows up automatically in the home list and the Settings topic
editor. Interactive features (like the joystick) additionally expose a typed property on
the bridge and get their own screen under `presentation/`.
