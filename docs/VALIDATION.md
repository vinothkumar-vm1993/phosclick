# Validation and release scope

The published source matches the tested application line: Android 0.9.4 / watch 0.6.17. The store build changes the registered Zepp application ID to 1136713 and disables debug mode. The APK is signed for public distribution; it is not the earlier debug certificate.

| Check | Result / scope |
| --- | --- |
| Watch/Side Service automated suite | 79 passing tests in the development baseline; rerun for publication |
| Android JVM tests | 25 passing tests; release variant rerun for publication |
| Release APK assembly and lint | Run on the clean publication tree; see release notes |
| Pixel camera, media, lock/exit, UI regression | 32 native checks on 0.9.3, seven targeted checks on 0.9.4; 33 unique checks across those runs, not a full latest-version 33-test run |
| Watch 0.6.17 large aspect-fit review without zoom/pan | User physically confirmed working |
| Photo/video, front/rear, single haptic, countdown, history/delete | Physical confirmations on the supported development device pair |
| Earlier locked/idle endurance | 28m44s on Android 0.9.0, start/end captures; not a new 0.9.4 endurance run |
| Real out-of-range walking test | Not physically completed; remains unverified |
| Real low-battery/storage thresholds | Logic tested; physical warning conditions not induced |
| Other Android phones / other regional watch targets | Not physically validated |

Automated checks cannot establish real Bluetooth range, subjective smoothness, battery life on every device, or store approval. No claim of zero bugs, zero battery drain or universal hardware support is made. Release signing/package registration do not substitute for testing the store-installed pair. Report regressions with versions and reproducible steps.
