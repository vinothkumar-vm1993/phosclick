# Setup and everyday use

## Android installation

Download `WristSnap-0.9.5.apk` from [the official repository release](https://github.com/vinothkumar-vm1993/wristsnap/releases/latest). Verify the SHA-256 digest in the release notes when possible. Android may ask you to allow installation from the browser or file manager used for this download; grant that only for the source you chose. Launch WristSnap and grant Camera permission. Microphone is optional, required only for normal-video sound. Allow notifications for the ongoing session control.

The public APK is release-signed. Earlier developer/debug installations use a different signing certificate, so Android may reject an in-place update. Export anything needed and remove the old developer app before installing the public release; app-local settings/access approval reset. Shared Pictures/Movies media are separate from app settings. Do not remove an existing installation without understanding this migration.

## Watch installation

The Zepp Store package targets Amazfit Active 2 Square only. After approval, find WristSnap in the watch's app store in Zepp and install it. Availability is pending store review. The watch source is included in this repository. A built `.zab` is a developer/store package, not an APK and not a file to install directly on Android. For development, build the watch source and use the official Zepp developer preview/QR workflow with your own device and developer account. A preview QR is temporary and is not a permanent public store link.

## First connection

1. Connect the watch to the official Zepp Android app normally.
2. Open WristSnap on the unlocked phone. Keep its camera screen visible for initial authorization.
3. Open WristSnap on the watch. Follow or skip the short guide.
4. Approve the local request in the phone app. A single haptic confirms first connection.
5. Take a test photo and check the phone's media gallery.

There is no second Bluetooth device to pair, no six-digit key and no domain/server to configure. The Side Service runs through Zepp and talks to the companion on the same phone. Keep Zepp permitted to maintain its existing watch connection.

## Watch controls

- App starts in Photo. Swipe left: Photo → Video → Slow Motion → Timelapse. Swipe right moves to the previous mode and stays in Photo at the beginning.
- Large center button: shutter, or start/stop recording. Camera-flip button: right of shutter. Thumbnail: left of shutter.
- Swipe up: mode settings. Swipe down: quick controls. The header back control returns from settings/review. The explicit × closes watch controls.
- Timer Off/3s/10s appears on both screens. Multi-shot 1×/3×/5× applies to Photo; the timer runs once before the sequence.
- Gallery: swipe left to older, right to newer, up to eight recent phone-backed captures. Video is a still thumbnail on the watch. Trash asks before deleting that selected capture.
- Photo review preserves the image aspect ratio. It has no gallery zoom/pan. Camera zoom is a separate −/+ control.

## Android controls

Swipe modes or tap their names. Pinch/slider changes camera zoom; tap preview to focus and adjust exposure. Settings opens a separate AMOLED-black page. Tap the rounded thumbnail for photos/video playback; long-press items to select one or several for deletion. Native-gallery access is also available. Deleted/trashed media are reconciled when returning to WristSnap.

## Lock, exit and reconnect

An already active camera session can continue while the phone is locked or Home is pressed. The ongoing notification indicates the session. Use Disconnect when finished. Root Back, Recents removal or service stop releases camera and microphone; closing only the watch UI does not stop the phone session. Android may stop a service under system/OEM restrictions, so reopen WristSnap visibly if needed. WristSnap requests a 60-second watch page timeout. On watch 0.6.19 with Android 0.9.5, use × then Close to clear the phone's watch connection status. The phone session stays available and any active recording continues. Watch wake/reopen reconnects through Zepp when available. No claim is made that an absent radio signal can be preserved.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Phone app unavailable | Open WristSnap on the phone; approve access and camera permission |
| Watch disconnected | Check Zepp's normal watch connection and Bluetooth range, then retry |
| No microphone sound | Enable normal-video audio and allow Microphone; Slow Motion/Timelapse are silent |
| Unsupported quality/FPS | Choose an available combination for the selected lens |
| Android installation conflict | Check whether an earlier debug APK has a different signing certificate |
| Developer QR download failure | Rebuild/recreate an active official preview session; QR is temporary |

For bugs, open a [GitHub issue](https://github.com/vinothkumar-vm1993/wristsnap/issues) with app versions, phone/watch model and the steps. Do not post access tokens, private photos or identifying logs.
