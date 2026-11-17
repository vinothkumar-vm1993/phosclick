# WristSnap

**Your phone camera. On your wrist.**

WristSnap is a local camera remote for Amazfit Active 2 Square and Android. Capture a photo, start a video, switch lenses, adjust zoom and review recent captures without reaching for your phone. A minimal AMOLED interface keeps the important controls within a tap or swipe.

[Download Android APK](https://github.com/vinothkumar-vm1993/wristsnap/releases/download/v0.9.4/WristSnap-0.9.4.apk) · [Release notes](https://github.com/vinothkumar-vm1993/wristsnap/releases/tag/v0.9.4) · [Setup guide](docs/SETUP.md) · [Privacy](docs/PRIVACY.md) · [Report an issue](https://github.com/vinothkumar-vm1993/wristsnap/issues)

Android **0.9.4** · Watch **0.6.17**. The watch app has been **submitted to Zepp Store and is under review**; availability depends on Zepp approval. Developers can build the watch package from this repository.

## What you can do

| Feature | Android | Watch |
| --- | --- | --- |
| Photo, Video, Slow Motion, Timelapse | Capture and configure | Swipe between modes; remote shutter/record |
| Front/rear camera | Switch lens | Camera-flip button beside shutter |
| Zoom | Pinch and slider | Compact −/+ control |
| Timer | Off, 3s, 10s | Same countdown on both screens |
| Multi-shot | 1, 3 or 5 photos | Select sequence in Photo settings |
| Flash | Off/Auto/On for photos; torch for video | Remote control, distinct Auto icon |
| Quality and frame rate | Only capabilities reported by the camera | Remote settings |
| Focus and exposure | Tap to focus; exposure adjustment | Not offered without a live view |
| Media review | Photos and video playback, native-gallery access | Last eight captures; photos and video still thumbnails |
| Delete | Long-press for single/multiple selection, Android confirmation | Delete the exact reviewed capture with confirmation |

Resolution choices use readable labels. Video resolutions and FPS depend on the selected phone camera; unsupported combinations are not promised. Normal video can include microphone audio when enabled and permitted. Slow Motion and Timelapse are silent. Timelapse captures at 1 fps and produces a 30 fps movie.

Watch photo review fits the image to the screen without stretching. Watch review has no zoom/pan or video playback. There is **no continuous live preview on the watch**. The gallery button shows the latest capture thumbnail.

## Install and connect

1. Install the signed Android APK from this repository's release. Allow installation from your browser/file manager if Android requests it.
2. Install WristSnap on your supported watch through Zepp Store when available, or follow the developer-install instructions in [Setup](docs/SETUP.md).
3. Pair your watch with the official Zepp Android app normally. Open WristSnap on the phone, grant camera permission, then open WristSnap on the watch.
4. Approve the local watch connection on the phone the first time. Once connected, use the watch shutter. No second Bluetooth pairing, typed pairing key, website address or cloud relay is needed.

Both apps include a skippable first-run guide. Keep the official Zepp app available to maintain its watch connection. [Detailed setup, controls and troubleshooting →](docs/SETUP.md)

## Device support

| Component | Requirement | Validation |
| --- | --- | --- |
| Watch | **Amazfit Active 2 Square**, 390 × 450 | Indian variant, source 10223875, firmware 4.6.0.3/API 402 physically tested |
| Other Active 2 Square variants | Package includes sources 10223872 and 10223873 | Included targets; not physically validated |
| Watch runtime | Zepp OS API minimum 3.6; target 4.0 | Package restricted to the models above |
| Android companion | **Android 12 or later** (API 31+) | Pixel 6a on Android 17/API 37 physically tested |
| Zepp phone app | Official Zepp app; keep updated | Tested with 10.10.5-play |

Other Android phones may work when they support the required camera and background-service behavior, but have not been fully validated. Camera resolution/FPS, flash, stabilization and zoom vary by hardware. Other Amazfit models, round screens, Wear OS, Apple Watch and an iOS companion are **not supported by this release**. [Full compatibility details →](docs/COMPATIBILITY.md)

## Connection, privacy and battery

Commands and requested thumbnails use the existing Zepp Bluetooth connection and a fixed phone-local loopback bridge:

`Watch → Zepp Side Service → 127.0.0.1:47835 → Android camera service`

The Android service listens only on loopback. Initial phone approval grants a random internal capability; commands expire, inputs are bounded and uncertain shutter requests are not replayed automatically. This is not an internet camera server. WristSnap has no analytics, ads, cloud photo upload or required account. Installation/distribution still uses GitHub and Zepp services. [Privacy and permissions →](docs/PRIVACY.md)

Start a session while the phone is unlocked and the app is visible. An active session can continue while the phone is locked or Home is pressed. **Android root Back, removal from Recents, Disconnect or service termination releases camera/microphone access.** The ongoing notification provides a Disconnect action. Closing only the watch controls preserves the phone session. Android/OEM restrictions, force-stop or revoked permissions can require reopening the phone app; automatic camera restart after reboot is not supported.

Camera sessions consume power even with the screen locked. Disconnect when finished. WristSnap avoids continuous thumbnail streaming, redundant control redraws and unnecessary navigation haptics. A single short watch haptic marks capture/record actions and first connection. App sounds are removed.

## Developers

The repository contains the Android Kotlin CameraX/Camera2 companion, Zepp Device App and Side Service, local command protocol and automated tests.

- [Build and test](docs/DEVELOPMENT.md)
- [Validation matrix and known limits](docs/VALIDATION.md)
- [Security reporting](SECURITY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.txt)

Source is available under the [MIT License](LICENSE). WristSnap is an independent project and is not affiliated with Amazfit, Zepp, Google, Apple or DJI.
