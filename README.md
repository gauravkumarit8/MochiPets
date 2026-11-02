# Mochi Pets

Android screen-pet app: an animated character (Mochi the cat) walks, climbs the screen edges,
hangs from the top, naps, and reacts to touch. Drag and throw her.

Status: Step 1 (overlay service, placeholder art) and Step 2 (climb, hang, squash) are included.

## Requirements (Windows)

1. JDK 17 (Temurin recommended): https://adoptium.net  (check with `java -version`)
2. Android SDK. Easiest: install Android Studio once (it installs the SDK to
   `%LOCALAPPDATA%\Android\Sdk`). In Android Studio: Settings > SDK Manager, install
   "Android SDK Platform 34" and "Android SDK Build-Tools 34.0.0".
3. Git.
4. VS Code with the recommended extensions (Kotlin, Gradle for Java).

Gradle itself is not required: the project ships the Gradle wrapper (`gradlew.bat`).

## Build the APK

    powershell -ExecutionPolicy Bypass -File scripts\build_apk.ps1

The APK is written to `dist\MochiPets-debug.apk`. The first build downloads Gradle and
dependencies and can take several minutes.

## Install on your phone

Option A (USB): enable Developer options and USB debugging, connect the phone, then:

    & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r dist\MochiPets-debug.apk

Option B: copy `dist\MochiPets-debug.apk` to the phone and open it (allow "install unknown apps").

## Project layout

    app/src/main/java/com/mochipets/app/
      PetBrain.kt            state machine and physics (no Android UI code)
      PetView.kt             placeholder Canvas art for Mochi
      PetOverlayService.kt   foreground service, overlay window, touch handling
      MainActivity.kt        settings screen
      Prefs.kt               preference keys
    patches/applied/         patches already baked into the sources (reference only)
    scripts/                 build scripts

## Troubleshooting

- `SDK location not found`: install Android Studio, or set ANDROID_HOME, then delete
  `local.properties` and rebuild.
- `Unsupported class file major version` / Java errors: make sure `java -version` is 17.
- Pet does not appear: grant "Display over other apps" in the app, then tap Start Mochi.
- Pet disappears after a while on Samsung/Xiaomi/Oppo/Vivo: disable battery optimization for the app.
