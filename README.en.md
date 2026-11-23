# ChuanSuo (穿梭)

**Developer · YH**

ChuanSuo is an Android app for simulating a fixed location. Choose a point on the map, search for a place, or enter coordinates, then start or stop simulation from the map. An optional floating joystick moves the simulated position. The app supports ARM64 devices running Android 8.1 or later.

[Project repository](https://github.com/liuqingwlil/chuansuo-flue) · [Releases](https://github.com/liuqingwlil/chuansuo-flue/releases)

Includes saved-place/route groups, pinning and ordering, map/coordinate route editing, clock-based route playback with pause/resume/speed/loop controls, GPX import/export with preview and transactions.

The package ID is `com.chuansuo.app` for both builds.

Build with JDK 17, Android SDK Platform 32 and Build Tools 36.0.0:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug assembleRelease lintDebug lintRelease
```

The debug build includes map Key replacement and diagnostic logging controls. The release build uses a Baidu Android SDK Key supplied at build time through the ignored `local.properties` file or a GitHub Actions secret. GitHub Releases distributes one signed standard APK.

Routes interpolate directly between nodes, not along road networks; GPX segment breaks are preserved and skipped without a connecting route. GPX timestamps are retained for export; playback uses configured speed.

Day-to-day development takes place on `codex/develop`. For a release, merge into `main`, tag the release commit with the app version, and upload the signed APK to GitHub Releases.

ChuanSuo is a modified version of [GoGoGo](https://github.com/ZCShou/GoGoGo) (© ZCShou), maintained by YH with changes to the interface, place search, favorites, and routes. This repository was established on 2026-11-23 and the source is released under [GNU GPL v3](LICENSE). Third-party components such as the Baidu Maps SDK are subject to their own license terms.

See the [Chinese README](README.md), [feature guide](docs/FEATURES.md), and [implementation notes](docs/IMPLEMENTATION_PLAN.md).
