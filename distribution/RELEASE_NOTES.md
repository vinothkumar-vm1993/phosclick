## PhosClick 0.9.8

The Android camera UI uses blue and violet accents drawn from the Cobalt Violet icon. Camera and Settings surfaces remain AMOLED black. Active flash, focus and exposure, zoom controls, selected modes and settings use the updated colors.

Install **PhosClick-0.9.8.apk** on Android 12 or later. The matching watch build is 0.6.22.

## PhosClick 0.9.7

Install the signed **PhosClick-0.9.7.apk** on Android 12 or later. This release updates the Android launcher to the Cobalt Violet camera icon. The watch app 0.6.22 and its Zepp Store listing use the same artwork. Zepp Store availability depends on review.

Android application ID and signing certificate are retained for in-place upgrades. The watch Store App ID remains 1136713.

## PhosClick 0.9.6

WristSnap is now PhosClick. Android’s application ID and signing certificate are retained for in-place updates. The existing watch store App ID is retained. Earlier captures remain visible in the gallery.

Install the signed **PhosClick-0.9.6.apk** under Assets. The Android update pairs with watch **0.6.20** for the full watch-close and display fixes. Zepp Store availability is subject to review.

- **Two-way mode sync.** The open watch camera screen checks for phone mode changes every two seconds, while watch swipes update the phone.
- **Longer watch display timeout.** PhosClick requests a 60-second page timeout and restores the normal display behavior on exit.
- **Accurate watch connection status.** Closing watch controls clears the phone's connected indicator while an active Android camera session or recording continues. If the watch disappears, the status expires after the connection timeout.
- **Compatibility.** Android 12+ and Amazfit Active 2 Square or Active 2 NFC Square (390 × 450). The physically tested setup uses Pixel 6a and the Indian Active 2 Square. Camera options depend on the phone and lens.

The APK uses the same public signing certificate as 0.9.4. People with the earlier developer/debug build may need to migrate first.

[Setup and features](https://github.com/vinothkumar-vm1993/phosclick#readme) · [Device support](https://github.com/vinothkumar-vm1993/phosclick/blob/main/docs/COMPATIBILITY.md) · [Validation](https://github.com/vinothkumar-vm1993/phosclick/blob/main/docs/VALIDATION.md)
