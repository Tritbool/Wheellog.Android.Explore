# ADR 002 – Koin as the Sole Dependency Injection Framework

**Date:** 2026-07-17  
**Status:** Accepted  

---

## Context

The project started accumulating two patterns for providing dependencies:
- **Koin** (`org.koin:koin-android`, `koin-androidx-compose`) – used throughout the
  existing codebase for `AppConfig`, `NotificationUtil`, `VolumeKeyController`, and
  `BleSessionViewModel`.
- Ad-hoc singletons accessed via `WheelData.getInstance()` and Kotlin `object`s with
  KoinComponent `inject()`.

The question arose during the dashboard refactoring: should we migrate to **Hilt** (the
Google-recommended alternative) or standardise on Koin?

## Decision

**Retain Koin 4.x as the sole DI framework.**

All new modules (e.g., `dashboardModule`) are registered as Koin modules in `WheelLog.startKoin {}`.

## Rationale

| Factor | Koin | Hilt |
|---|---|---|
| Already adopted | ✅ | ❌ (would require full migration) |
| Annotation processing / KSP | Not required | Required (doubles build time) |
| Compose ViewModel injection | `koinViewModel()` – 1 line | `hiltViewModel()` – needs `@HiltViewModel` annotation on every ViewModel |
| Multiplatform potential | ✅ koin-core is KMP-ready | ❌ Hilt is Android-only |
| Complexity | Low | Higher (generated code, `@EntryPoint`, component graph) |

Migrating to Hilt at this stage would be a large, risky refactor with no clear user-visible benefit.

## Cross-ViewModel injection pattern

When one ViewModel needs a reference to another ViewModel (e.g., `DashboardViewModel` needs the
Activity-scoped `BleSessionViewModel`), pass the already-resolved instance as a Koin parameter:

```kotlin
// Module
viewModel { (bleVm: BleSessionViewModel) -> DashboardViewModel(androidApplication(), bleVm, get()) }

// Composable
val bleVm: BleSessionViewModel = koinViewModel()
val dashVm: DashboardViewModel = koinViewModel { parametersOf(bleVm) }
```

This avoids creating orphaned ViewModel instances while keeping Koin's concise DSL.

## Consequences

- All future ViewModels, repositories, and utilities are provided through Koin modules.
- Hilt is not added as a dependency.
- `WheelLog.startKoin {}` is the single place where all modules are registered.
