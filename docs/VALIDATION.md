# Validation

The latest release source is Android 0.9.5 / watch 0.6.19. The store package uses app ID 1136713, version code 35 and disabled debug mode. The signed Android APK uses the same release certificate as Android 0.9.4.

| Check | Result |
| --- | --- |
| Watch and Side Service automated suites | 83 passed, including phone-mode changes, watch display setup and orderly detach |
| Android 0.9.5 release assembly, unit tests and lint | Signed APK verified, 25 unit tests passed and release lint passed |
| Pixel 6a Android bridge test | Passed: authorized detach clears watch presence, keeps the bridge running and allows reconnect. Unauthorized detach is rejected. |
| Watch pairing and capture features | Development setup tested with Pixel 6a and the Indian Active 2 Square (source 10223875, firmware 4.6.0.3, API 402). Other included regional variants and other phones have limited validation. |
| Watch timeout and watch-close UI | User reported the update working. Automated tests cover the requested timeout and detach signal. Firmware timing and abrupt-close behavior were not independently measured. |

Earlier physical runs covered capture, media review, lock and exit behavior on the same device pair. Those runs were spread across previous versions (including Android 0.9.3 and 0.9.4). See [compatibility](COMPATIBILITY.md) for unsupported devices and hardware limitations.
