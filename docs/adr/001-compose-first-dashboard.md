# ADR 001 – Compose-First Dashboard

**Date:** 2026-07-17  
**Status:** Accepted; feature-flag rollout superseded on 2026-10-07

---

## Context

The legacy dashboard screen is driven by `WheelView`, a 55 KB custom Android `View`
(Canvas-based) that reads app config and calls `WheelData`/`BleSessionViewModel`
singletons directly from its drawing code.  This couples rendering logic to
global state, makes unit testing impossible, and blocks a clean Compose migration.

## Decision

**Update (2026-10-07):** The production container, dashboard, telemetry, events,
BMS, trips and scan content are now Compose-only. All seven renderer switches
are removed; previously saved `false` preferences are ignored. The original
gradual-rollout decision below is historical where it mentions a fallback.
Native header/dialog and MPAndroidChart interoperability remains, as does view binding.

Replace the legacy `WheelView`-based dashboard with a fully Compose-driven screen:

| Layer | Solution |
|---|---|
| State | `DashboardUiState` – single immutable snapshot of all data the screen needs |
| Mapping | `DashboardMapper` – pure function `BleSessionState + AppConfig → DashboardUiState` |
| ViewModel | `DashboardViewModel` – owns `StateFlow<DashboardUiState>`, exposes `toggleDisplayMode()` |
| Gauge | `DashboardGauge` – `@Composable` Canvas implementation, input = `DashboardUiState` only |
| Renderer selection | Compose-only; the original `AppConfig.useComposeUI` flag and legacy `WheelView` fallback are removed |

## Data flow

```
EUCData (BLE lib)
   └─► BleSessionViewModel (StateFlow<BleSessionState>)
              └─► DashboardViewModel (combine + map via DashboardMapper)
                        └─► StateFlow<DashboardUiState>
                                  └─► DashboardGauge  ──►  pixels on screen
```

## Rationale

- **Testability**: `DashboardMapper` is a pure Kotlin function with no Android framework
  dependencies – trivially unit-testable with MockK.
- **Single source of truth**: `DashboardUiState` encodes every piece of information the
  gauge needs (fractions pre-computed, formatted strings pre-built).  The composable
  is a pure render function.
- **Animation**: Jetpack Compose `animateFloatAsState` provides smooth transitions with
  less boilerplate than the manual `Handler.postDelayed` loop in `WheelView`.
- **Gradual migration (superseded)**: the original `useComposeUI` flag allowed
  side-by-side validation. Production rendering is now unconditionally Compose.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Keep `WheelView`, bind `DashboardUiState` externally | Doesn't address the testability problem; Canvas imperative code stays |
| Full Compose rewrite of ALL screens at once | Too risky; large surface area, difficult to validate incrementally |
| Use `MotionLayout` or XML for the gauge | Not Compose-first; adds another UI toolkit |

## Consequences

- The original retention of `WheelView` behind a feature flag is superseded:
  the class, legacy dashboard widget and flag have been removed.
- `DashboardCanvasRenderer` remains the drawing engine used by the Compose gauge;
  removing the fallback is not evidence of completed device/hardware validation.
- Future feature additions (new metrics, dark/light theme variants) should go into
  `DashboardUiState`/`DashboardMapper`, without restoring the removed fallback.
