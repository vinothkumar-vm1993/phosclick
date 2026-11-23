# PhosClick

**Android camera remote for Amazfit Active 2 Square.**

PhosClick lets you control your Android phone camera from an Amazfit Active 2 Square through its existing connection to the Zepp Android app.

[Download the signed Android APK](https://github.com/vinothkumar-vm1993/phosclick/releases/latest) · [Setup and troubleshooting](docs/SETUP.md) · [Compatibility](docs/COMPATIBILITY.md) · [Privacy](docs/PRIVACY.md)

Existing installs signed with the release certificate can update in place. Earlier captures remain available in PhosClick’s gallery.

The source versions are **Android 0.9.6** and **watch 0.6.20**. Check the version shown on the GitHub Releases page before downloading. Watch installation through Zepp Store depends on review and availability. Developers can build the watch package from source for Zepp Developer Mode preview.

## Supported devices

| Component | Requirement and test coverage |
| --- | --- |
| Android phone | **Android 12 or later (API 31+)**. Physically tested on **Google Pixel 6a, Android 17 (API 37)**. Other Android models have limited validation. |
| Watch | **Amazfit Active 2 (Square)** and **Amazfit Active 2 NFC (Square)**, 390 × 450. Package targets: 10223875, 10223872 and 10223873. |
| Tested watch | Indian Active 2 (Square), source **10223875**, firmware **4.6.0.3**, API level **402**. The two NFC targets are included in the package and have yet to be physically tested. |
| Zepp OS runtime | Manifest minimum **API 3.6**, target and compatibility **API 4.0**. These are application API versions. |
| Zepp phone app | Official **Zepp for Android** on the same phone as PhosClick. Tested with Zepp **10.10.5-play**. |

The supported watch list is limited to these square targets. Active 2 round models, other Amazfit watches, Wear OS and Apple Watch are outside this release's scope. An Android phone is required.

## Features

| Feature | Android phone | Watch |
| --- | --- | --- |
| Capture | Photo, Video, Slow Motion and Timelapse | Remote shutter and record start/stop. Swipe to change modes. |
| Mode sync | Changes from the watch update the phone | While the watch camera screen is open, it checks for phone mode changes every two seconds. |
| Camera controls | Front/rear switch, pinch/slider zoom, tap to focus and exposure adjustment | Front/rear switch beside the shutter and camera zoom controls |
| Capture settings | Photo quality and aspect ratio, video quality and frame rate, photo flash and video torch where supported | Adjust settings available for the selected mode and lens |
| Timer and multi-shot | Off, 3s or 10s timer and 1, 3 or 5 photos per sequence | Countdown on both screens for watch-triggered timed capture |
| Gallery | Photo review, video playback, native gallery access, and single or multiple selection for deletion | Swipe through the last eight phone-backed captures, see photos fitted to the display and video still thumbnails, or delete the selected capture after confirmation |
| Watch display | Connection status and an ongoing session notification | AMOLED-black interface, 60-second app page timeout, and single haptic feedback for initial connection and capture/record actions |

Camera capabilities vary by phone and lens. Normal video supports optional microphone audio. Slow Motion and Timelapse are silent. Video playback is on the phone. Watch review shows stills and uses aspect-fit photo display. The watch camera dashboard provides controls without a continuous live feed.

The app displays low phone battery and storage warnings. Camera zoom is available during capture setup. Watch gallery review uses a fixed fit-to-screen view.

## Set up and connect

1. Install the signed APK from GitHub Releases and grant Camera permission. Enable Microphone only if you want sound in normal video.
2. Connect the watch to the official Zepp Android app. Install PhosClick from the watch's app store once it is available for your device.
3. Open PhosClick on the unlocked phone, then open it on the watch. Approve the local connection on the phone when prompted.
4. Tap the watch shutter. Photos and videos are saved to the phone. See the setup guide for controls, older debug APK migration, developer preview and troubleshooting.

## Sessions and privacy

PhosClick uses the watch's existing Zepp connection and an authenticated loopback service on the same phone. Setup requires one phone-side approval. The app has no extra Bluetooth pairing, account, typed pairing key or cloud relay. Commands and requested thumbnails are exchanged locally. PhosClick does not send analytics or upload captures.

Start the phone session with PhosClick visible. An active session can continue with the phone locked or after pressing Home, subject to Android and device restrictions. With Android **0.9.6** and watch **0.6.20**, closing the watch controls updates the phone connection status while keeping the phone session and any recording active. If the close signal cannot reach the phone, the connection indicator clears after its timeout. Use **Disconnect** on the phone, root Back or remove the app from Recents to stop the Android camera session.

## Support and contact

For help with PhosClick, email [vinothkumar.vm1993@gmail.com](mailto:vinothkumar.vm1993@gmail.com). For bugs or feature requests, [open a GitHub issue](https://github.com/vinothkumar-vm1993/phosclick/issues). Include your phone model, Android version, watch firmware and both PhosClick versions so the issue can be reproduced.

## Development

This repository includes the Android Kotlin companion, Zepp Device App, Side Service and automated tests. See [build instructions](docs/DEVELOPMENT.md) and the [validation matrix](docs/VALIDATION.md).

[MIT License](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.txt) · [Report an issue](https://github.com/vinothkumar-vm1993/phosclick/issues)
