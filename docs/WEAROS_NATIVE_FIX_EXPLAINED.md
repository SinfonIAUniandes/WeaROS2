# Solution to Native Library Issues on Wear OS (Galaxy Watch 7)

This document explains step-by-step the series of issues and solutions implemented to get the `jros2` library (which uses Fast-DDS written in C++) working correctly inside a Galaxy Watch 7 smartwatch running Wear OS 5.

## Main Issue: "No implementation found" and "Silent Crashes"
When attempting to start ROS 2 communication from the watch app, the application would crash without leaving a trace in standard logs, or it would show the error:
`ROS2 Error: ExceptionInInitializerError Cause: UnsatisfiedLinkError: No implementation found...`

This indicated that the Android Virtual Machine (Dalvik) was unable to link Java functions with their native C++ implementations.

## Implemented Solutions

### 1. The Secret Architecture of Wear OS (32-bit)
Even though the Galaxy Watch 7 processor (Exynos W1000) is 64-bit (`arm64-v8a`), **Google and Samsung configure Wear OS 5 to operate in a 32-bit environment (`armeabi-v7a`)** to aggressively conserve RAM and battery life.
* **Issue:** The original `jros2` build script (`build-android-arm64.bash`) only generated 64-bit libraries. When installing the APK on the watch, it searched for the `armeabi-v7a` directory and, failing to find the native library, failed during loading.
* **Solution:** A new script `build-android-armeabi-v7a.bash` was created and `cppbuild.bash` in the `jros2` repository was modified to add official cross-compilation support for the `armeabi-v7a` architecture.

### 2. JavaCPP Dropping 32-bit Support
When attempting to compile for 32-bit, we encountered a dependency issue: the `JavaCPP` bridging library (used by `jros2`) **removed support for 32-bit Android (`android-arm`) starting with version 1.5.10.**
* **Issue:** `jros2` was using JavaCPP version `1.5.11`. When trying to build, Gradle failed because it could not download the 32-bit package from Maven Central (as it no longer exists).
* **Solution:** A forced downgrade across the ecosystem was performed. We modified `cppbuild.bash`, `jros2/android/build.gradle.kts`, and `WeaROS2/app/build.gradle.kts` to use **JavaCPP 1.5.9**, the last version in history to support 32-bit Wear OS watches. Then the AAR was recompiled and republished to Maven Local.

### 3. Packaging `useLegacyPackaging = true`
Smartwatches have much stricter virtual memory rules compared to smartphones.
* **Issue:** By default, new versions of the Android Gradle Plugin attempt to read `.so` libraries directly from the compressed APK using memory mapping (mmap). This caused the watch operating system to kill the process due to lack of RAM when mapping the massive Fast-DDS library.
* **Solution:** `useLegacyPackaging = true` was added to the `build.gradle.kts` of WeaROS2. This forces Android to unpack and physically copy `.so` files to the watch's internal storage during installation, avoiding memory mapping issues upon loading.

### 4. ABI Filtering (`abiFilters`)
To prevent Gradle from packaging unnecessary architectures and ensure compatibility with both the watch and emulator, the NDK block in WeaROS2 was configured:
```kotlin
ndk {
    abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
}
```

### 5. Preventing Silent Crashes (`Throwable`)
The original silent crash occurred because the Kotlin code used a `try-catch(e: Exception)` block.
* **Issue:** Low-level errors related to JNI or loading native libraries (`UnsatisfiedLinkError`, `ExceptionInInitializerError`) do not inherit from `Exception`, but from `Error`. Consequently, they bypassed the catch block and killed the app.
* **Solution:** Error handling in `WearSensorBridge.kt` was changed to `catch (t: Throwable)`. Furthermore, on-screen logging was improved to print not only the message but also the exact error class and "cause" (`t.cause`), revealing the true nature of native failures.

---

### How to recompile in the future?
If you make changes to C++ interfaces in the future, you must:
1. Export the correct NDK: `export ANDROID_NDK='.../ndk/30.0.14904198'`
2. Run `bash build-android-arm64.bash`
3. Run `bash build-android-armeabi-v7a.bash`
4. Publish to Maven Local: `cd android && ../gradlew -p . publishReleasePublicationToMavenLocal`
