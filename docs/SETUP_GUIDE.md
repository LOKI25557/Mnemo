# Mnemo Developer Setup & Environment Guide

This guide provides Windows-friendly instructions for cloning, opening, building, and running the **Mnemo** project using Android Studio and the command line.

---

## 1. Prerequisites

Before setting up the project, ensure your development machine has:

1. **Operating System:** Windows 10/11, macOS, or Linux.
2. **Java Development Kit (JDK):**
   - Recommended: **JDK 17** or **JDK 21** LTS (Temurin / Microsoft OpenJDK / Oracle).
   - Verify in terminal:
     ```powershell
     java -version
     javac -version
     ```
3. **Android Studio:**
   - Android Studio Ladybug / Koala / Meerkat (2024.1+ or newer) with Jetpack Compose support.
4. **Android SDK:**
   - Platform: `Android 35` (API 35, Android 15).
   - Build Tools: `35.0.0`.
   - Command-line Tools: `latest`.

---

## 2. Cloning the Repository

```powershell
git clone https://github.com/LOKI25557/Mnemo.git
cd Mnemo
```

---

## 3. Configuring Local Android SDK Path

The Android Gradle Plugin requires the path to your installed Android SDK.

Create or verify `local.properties` in the project root:

```properties
sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
```

*(On macOS/Linux, use `sdk.dir=/Users/<username>/Library/Android/sdk`)*

> [!NOTE]
> `local.properties` is strictly ignored by `.gitignore` and must never be committed to Git.

---

## 4. Opening in Android Studio

1. Launch **Android Studio**.
2. Select **Open** from the welcome screen (or `File -> Open`).
3. Navigate to and select the `Mnemo` directory.
4. Allow Android Studio to complete the initial **Gradle Sync**.
   - Gradle wrapper will automatically download Gradle 9.1.0 if not already cached.
   - Foojay toolchain resolver will automatically resolve the required Java 17 toolchain.
5. In the toolbar, ensure the active run configuration is set to **`app`**.

---

## 5. Building & Running via Command Line (PowerShell)

### List Available Gradle Tasks
```powershell
.\gradlew.bat tasks
```

### Run All Unit Tests Across All Modules
```powershell
.\gradlew.bat test
```

### Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```

The output APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### Build & Run Database Encryption Tests
```powershell
# Run Key Management Unit Tests
.\gradlew.bat :core:database:testDebugUnitTest

# Compile SQLCipher Instrumentation Test APK (device-independent build verification)
.\gradlew.bat :core:database:assembleDebugAndroidTest

# Run Instrumentation Tests on Connected Physical Device or Emulator
.\gradlew.bat :core:database:connectedDebugAndroidTest
```

### Clean Build
```powershell
.\gradlew.bat clean
```

---

## 6. SQLCipher & Hardware Keystore Configuration

### Native Library ABIs
Mnemo bundles `net.zetetic:sqlcipher-android:4.6.1` which provides compiled native libraries (`libsqlcipher.so`) for all major Android ABIs:
- `arm64-v8a` (Modern physical Android devices)
- `armeabi-v7a` (Legacy 32-bit ARM devices)
- `x86_64` (Standard Android Studio emulators)
- `x86` (Legacy 32-bit emulators)

All four ABIs are automatically packaged into `app-debug.apk`.

### Hardware Keystore Support
- On physical devices running Android 8.0+ (API 26+), the master key is created in hardware StrongBox Keymaster or TEE (Trusted Execution Environment).
- When running tests on an Android Emulator:
  - Use an API 26+ System Image (e.g., Pixel 8 with Google APIs or Google Play, API 35).
  - The Android emulator provides software-backed emulation of the AndroidKeyStore service, which supports the full AES-256-GCM cipher suite and key alias operations.

---

## 7. Windows File System & Gradle Performance

When developing on Windows with active cloud sync (e.g. OneDrive) or Windows Defender:
- Gradle daemons may occasionally encounter `IOException: Unable to delete directory ... cacheable`.
- The following optimizations are configured in `gradle.properties` to ensure reliable builds:
  ```properties
  kotlin.compiler.execution.strategy=in-process
  kotlin.incremental=false
  org.gradle.vfs.watch=false
  ```
- If a build lock occurs, run:
  ```powershell
  .\gradlew.bat --stop
  ```

---

## 8. Verifying Privacy Architecture (No Internet Permission)

You can verify that the Android manifest has zero network permissions by running:

```powershell
Get-Content app/src/main/AndroidManifest.xml | Select-String "INTERNET"
```

Expected output: **(None)**.

If any line references `android.permission.INTERNET`, the build fails CI security validation.

---

## 9. Troubleshooting Common Issues

### Issue: "SDK location not found"
**Fix:** Verify `local.properties` exists in the project root and points to a valid Android SDK directory.

### Issue: "Unsupported class file major version"
**Fix:** Ensure Gradle JVM in Android Studio (`Settings -> Build, Execution, Deployment -> Build Tools -> Gradle`) is configured to use JDK 17 or JDK 21.

### Issue: "Gradle build daemon timeout"
**Fix:** Run `.\gradlew.bat --stop` to kill stale daemons, then retry.
