# PhosClick privacy statement

Updated: 23 November 2026. Applies to Android 0.9.6 and watch 0.6.20.

PhosClick controls the camera on your Android phone from a supported Zepp OS watch. The app does not require a PhosClick account and contains no advertising or analytics service.

## Information used

- Camera images and videos are captured only as part of an active user-started session and saved through Android media storage under Pictures/PhosClick or Movies/PhosClick. The gallery also reads earlier capture folders.
- Optional microphone audio is recorded only when enabled for normal Video and permission is granted. Slow Motion and Timelapse are silent.
- Camera settings, local approval state and recent-media references are stored on the phone/watch as needed to provide the controls.
- Commands, capability/status information and requested small preview images pass locally through the existing Zepp watch connection and a loopback-only Android service. There is no PhosClick cloud relay. The watch can retain requested preview files locally.
- Phone battery/storage information is used for low-resource warnings. No health, heart-rate or location data is required.

PhosClick does not upload captures to a developer server, sell information or track usage. GitHub distributes the Android/source downloads, and Zepp distributes/runs the watch application and Side Service; their services and privacy policies apply independently. Opening a native gallery transfers control to the gallery app selected by Android.

## Permissions and active sessions

Camera permission enables capture. Microphone is optional for normal-video sound. Notifications expose ongoing-session controls. Foreground-service permissions allow a user-started camera session to continue while locked or away from the camera screen. Media operations use Android MediaStore and OS confirmation where required. The watch uses Zepp Bluetooth, device information and local storage APIs; its Side Service accesses the companion at fixed 127.0.0.1:47835.

Start the session visibly on the phone. Disconnect, Android root Back, removal from Recents or service termination releases camera/microphone resources. Android privacy indicators show camera/mic access while active. The app does not start the camera automatically after reboot.

## Your controls and retention

Delete captures from PhosClick or a native gallery; Android may first move media to Trash. Recent lists reconcile removed/trashed items. Forget access revokes the local approval. Disconnect stops the session. App uninstall removes app-private settings/cache, while media in shared storage may remain until you delete it. App data and private thumbnail caches remain local unless your device/system backup or another app independently copies them.

Report privacy/security concerns at https://github.com/vinothkumar-vm1993/phosclick/security or open an issue without private data. Do not attach secret tokens, personal captures or sensitive logs to a public issue.
