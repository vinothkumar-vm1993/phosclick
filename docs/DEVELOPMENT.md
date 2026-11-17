# Build and test

## Android

Use JDK 21, Android SDK platform 36/build tools and Gradle wrapper 8.14.3. Configure `ANDROID_HOME` or a private `android/local.properties` (`sdk.dir=...`). Never commit signing keys, local.properties or authorization data.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Connect a development phone with USB debugging for instrumentation. These tests can access camera/mic and create test media; run only on a device you control:

```sh
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Public release signing uses environment variables, not checked-in credentials:

```sh
export WRISTSNAP_KEYSTORE=/secure/path/wristsnap-release.p12
export WRISTSNAP_KEY_ALIAS=wristsnap
# Set WRISTSNAP_STORE_PASSWORD and WRISTSNAP_KEY_PASSWORD privately.
./gradlew :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

Without a configured keystore, the release build is unsigned. Preserve the signing key securely for future compatible updates. Do not distribute a debug-signed APK as the public release. App identity is `dev.zeppcamera.poc`.

## Zepp watch + Side Service

Use a supported Node.js LTS and the pinned npm dependencies:

```sh
cd watch
npm ci
npm test
npm run build
```

The production manifest uses registered application ID 1136713 and `debug: false`. Developers publishing their own app must register their own ID and update the manifest. Use the official Zepp CLI/Developer Mode workflow for local device preview; a local QR is not a permanent store download.

The Device App and Side Service are packaged together. The watch communicates through Zepp's existing connection; the Side Service sends bounded requests to the companion's fixed loopback endpoint. The Android app requires first-time native approval and an internal random capability. Do not substitute a publicly bound server or remove approval to simplify testing.

## Repository map

- `android/app/src/main`: Kotlin camera/service/UI, resources, manifest.
- `android/app/src/test`: JVM protocol/state tests.
- `android/app/src/androidTest`: real-device UI/camera/media checks.
- `watch/page`: Device App UI and gestures.
- `watch/app-side`: Zepp Side Service/loopback transport.
- `watch/shared`: shared protocol helpers.
- `watch/tests`: VM/transport regression tests.
- `docs`: setup, privacy, compatibility and validation.

The repository excludes personal captures, device identifiers, debug dumps, SDK caches and private signing material. License notices are included in the APK assets and repository. See THIRD_PARTY_NOTICES.txt.
