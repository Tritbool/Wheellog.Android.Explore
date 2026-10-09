# TuxWheel

<img src="app/src/main/res/drawable-nodpi/tuxwheel.png" alt="TuxWheel icon" width="80" />

[![Build](https://github.com/Tritbool/TuxWheel/actions/workflows/gradle.yml/badge.svg)](https://github.com/Tritbool/TuxWheel/actions)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

TuxWheel is an unofficial Android companion for electric unicycles (EUCs),
based on WheelLog. It connects over Bluetooth Low Energy (BLE) to display
telemetry, configure supported wheel settings, and record rides.

[Releases](https://github.com/Tritbool/TuxWheel/releases) ·
[Builds](https://github.com/Tritbool/TuxWheel/actions) ·
[Issues](https://github.com/Tritbool/TuxWheel/issues)

## Requirements and wheel support

- Android 6.0 or newer (API 23), with BLE hardware and Bluetooth enabled.
- Wheel-family presentations: KingSong, Gotway/Begode, Veteran, InMotion
  (including V2), and Ninebot/Ninebot Z.
- Actual connectivity, telemetry, settings, and commands depend on
  EUC_BLE_LIBRARY support, the wheel model, and its firmware.
- Smart BMS details are available only for supported wheels/packs.
  A family-specific screen is not a guarantee that every model supports every feature.

## Features

- Customizable dashboard blocks and gauges, with Original and AJDM themes.
- Speed, battery, temperature, current, PWM, and other available telemetry.
- Configurable alarms and charts; optional trips and events pages.
- Smart BMS presentation with per-pack measurements and cell details when available.
- CSV ride logging through a foreground service, plus optional raw BLE diagnostics.
- Picture-in-picture (PiP) on supported Android versions.
- Configurable speed/distance and temperature units.

## Connect to a wheel

1. Enable Bluetooth and open the scan dialog.
2. Grant **Nearby devices** permissions on Android 12+.
   Older Android versions require location permission for BLE scanning;
   enable system location when required.
3. Wait for nearby devices. Scanning stops after **10 seconds**;
   discovered results remain selectable. **Back** cancels the dialog and scan.
4. Tap a device to connect, or enter its Bluetooth MAC address manually.
5. Long-press a discovered device to open the protocol picker.
   Select a specific protocol to force it only when the wheel requires it;
   **Automatic** leaves detection to the library and is the fallback when unsure.

If connection fails, check permissions, Bluetooth, and whether another app
already holds the wheel connection. Report the model, firmware, and selected
protocol with any issue; do not assume all devices in a scan are EUCs.

## Dashboard and pages

Swipe between the dashboard, wheel settings, and enabled optional pages.
Enable charts, trips, and events in the page settings as needed.
Smart BMS visibility and detail depend on the connected wheel.

- **Long-press a dashboard block** to choose its replacement.
- **Single tap** triggers the horn only when that gesture is enabled.
- **Double tap** toggles lights only when the wheel supports the command.
- Choose Original/AJDM styling and units in settings.

Wheel settings and actions are capability-dependent. Verify changes while
stationary; app alarms do not replace the wheel's own safety protections.

## Logging, trips, and diagnostics

Start CSV logging from the app. The foreground service keeps recording while
the interface is in the background and exposes logging controls in its notification.
Grant notification permission on Android versions that request it.

Files use the existing **Downloads/WheelLog** location intentionally, retaining
compatibility with earlier logs and tooling; it is not a stale branding reference.
Storage access follows the Android version's permission and storage rules.

Enable the trips page to inspect recorded ride statistics, share a CSV through
Android's share chooser, or delete a trip after confirmation.
Raw BLE logging is a separate diagnostic option, not a substitute for ride CSVs.

Before sharing CSVs or diagnostic logs, inspect and sanitize them:
remove MAC addresses, device names, location/route data, and other identifiers.
Attach only the relevant, sanitized excerpt to a bug report.

## EUC_BLE_LIBRARY integration

BLE is provided by [EUC_BLE_LIBRARY](https://github.com/Tritbool/euc_ble_library),
not by wheel protocol decoders embedded in this app.
The dependency is `io.github.tritbool:euc-ble-library:0.0.8`;
its version is pinned in `gradle/libs.versions.toml`.

The library owns discovery/scanning, connections, protocol detection/decoding,
and BLE transport. The app's `BleSessionViewModel` wraps library callbacks
into `StateFlow<BleSessionState>` for the UI and other consumers.
It is an **app-wide Koin `single`**, shared by activities, Compose, and services;
resolve it with `inject()`/`koinInject()`, not an Activity-scoped `koinViewModel()`.

Telemetry comes from `EUCData`; BMS details use `getBMSData()` per pack.
Commands must respect the active protocol's capabilities.
The app owns permission prompts, presentation, alarms, storage, and logging.

## UI architecture

Production pages, the pager, and scan content are Compose-only.
Native headers, dialogs, PiP, and the MPAndroidChart chart still use View
interoperability; view binding remains necessary for those integrations.
Compose rendering does not own BLE connections or foreground logging.

Architecture decisions:
[Compose-first dashboard](docs/adr/001-compose-first-dashboard.md) ·
[Koin DI](docs/adr/002-koin-di.md)

## Development

Use **JDK 17**, Android Studio/SDK tooling, and the checked-in Gradle wrapper.
The app currently uses **compileSdk 37**.

```sh
git clone https://github.com/Tritbool/TuxWheel.git
cd TuxWheel
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

These tasks require working resolution of the configured Gradle plugins and
dependencies. The current local environment could not resolve **AGP 9.2.1**
before compilation; this is not a successful build or test result.

`mavenLocal()` is configured. If the pinned BLE artifact is unavailable remotely,
publish the matching library revision/version locally following the
[upstream instructions](https://github.com/Tritbool/euc_ble_library).
Do not downgrade the pinned library or plugins to bypass resolution failures.

The application ID remains `com.cooper.wheellog` (`com.cooper.wheellog.debug`
for debug builds) intentionally for upgrade/data compatibility.

## Screenshots to provide

Replace these tokens with fresh TuxWheel captures, using sanitized/demo data:

- `DASHBOARD`: customized blocks and gauge; capture Original and AJDM themes.
- `SCAN`: discovery dialog showing devices and manual MAC entry.
- `PROTOCOL_PICKER`: long-press device menu with Automatic and protocol choices.
- `SMART_BMS`: supported wheel with per-pack measurements and cell details.
- `TRIPS`: recorded ride statistics and share/delete actions.
- `SETTINGS`: wheel-specific controls, page options, themes, and units.

## Contributing and credits

Report reproducible problems in [Issues](https://github.com/Tritbool/TuxWheel/issues).
For changes, describe the affected wheel/features and validation performed.
UI checks and real-wheel testing remain necessary alongside automated tests.

Thanks to [WheelLog](https://github.com/Wheellog/Wheellog.Android), the
[palachzzz fork](https://github.com/palachzzz/WheelLogAndroid), and their contributors.
TuxWheel is licensed under [GNU GPL v3](LICENSE).
