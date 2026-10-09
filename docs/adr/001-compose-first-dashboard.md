# ADR 001 – Compose-First Dashboard

**Date:** 2026-07-17  
**Status:** Accepted  

---

## Context

The legacy dashboard screen is driven by `WheelView`, a 55 KB custom Android `View`
(Canvas-based) that reads app config and calls `WheelData`/`BleSessionViewModel`
singletons directly from its drawing code.  This couples rendering logic to
global state, makes unit testing impossible, and blocks a clean Compose migration.

## Decision

Replace the legacy `WheelView`-based dashboard with a fully Compose-driven screen:

| Layer | Solution |
|---|---|
| State | `DashboardUiState` – single immutable snapshot of all data the screen needs |
| Mapping | `DashboardMapper` – pure function `BleSessionState + AppConfig → DashboardUiState` |
| ViewModel | `DashboardViewModel` – owns `StateFlow<DashboardUiState>`, exposes `toggleDisplayMode()` |
| Gauge | `DashboardGauge` – `@Composable` Canvas implementation, input = `DashboardUiState` only |
| Feature flag | `AppConfig.useComposeUI` – `true` activates the new screen; `false` falls back to legacy `WheelView` |

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
- **Gradual migration**: the `useComposeUI` flag lets us run both implementations
  side-by-side on the same device without touching other screens.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Keep `WheelView`, bind `DashboardUiState` externally | Doesn't address the testability problem; Canvas imperative code stays |
| Full Compose rewrite of ALL screens at once | Too risky; large surface area, difficult to validate incrementally |
| Use `MotionLayout` or XML for the gauge | Not Compose-first; adds another UI toolkit |

## Consequences

- `WheelView` and its XML layout remain as the legacy fallback behind the feature flag.
- `WheelView` will be deleted after `useComposeUI = true` is proven stable.
- Future feature additions (new metrics, dark/light theme variants) should go into
  `DashboardUiState`/`DashboardMapper`, not into `WheelView`.
