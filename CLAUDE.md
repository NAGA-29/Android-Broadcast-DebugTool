# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew build                  # Full build (all variants)
./gradlew testDebugUnitTest      # Run all unit tests
./gradlew testDebugUnitTest --tests "com.example.broadcasttest.MainViewModelTest.addAction_updatesState"  # Run single test
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

**MVVM with Foreground Service for background broadcast monitoring.**

```
MainActivity (View)
  └── MainViewModel (state via StateFlow)
        └── BroadcastService (Foreground Service)
              ├── Dynamic BroadcastReceiver (registers per action)
              ├── MutableSharedFlow<BroadcastLog> (emits to MainActivity)
              └── File I/O (logs to /Android/data/.../files/broadcast_logs/)
```

**Data flow:**
1. User adds broadcast action strings via UI → `MainViewModel.addAction()`
2. MainActivity starts `BroadcastService` via Intent, passing registered actions as extras
3. Service registers a `BroadcastReceiver` with a dynamic `IntentFilter` for those actions
4. On receive, service emits `BroadcastLog(timestamp, action, extras)` via `broadcastFlow` SharedFlow and writes to file
5. MainActivity collects from `broadcastFlow` → calls `viewModel.addLog()` → RecyclerView updates

**Key design decisions:**
- `BroadcastService` holds the `broadcastFlow` as a companion object so `MainActivity` can collect from it without binding
- Service uses `CoroutineScope(Dispatchers.IO + SupervisorJob)` for file writes
- Activity observes ViewModel with `repeatOnLifecycle(STARTED)`
- No DI framework — ViewModel created via `viewModels()` delegate only

## Tech Stack

- **Min SDK:** 26 (Android 8.0) — **Target/Compile SDK:** 34
- **Language:** Kotlin 1.9.22
- **Build:** AGP 8.4.0, Gradle Version Catalog (`gradle/libs.versions.toml`)
- **UI:** View Binding, Material 3, ConstraintLayout, RecyclerView, ChipGroup
- **Async:** Kotlin Coroutines + StateFlow/SharedFlow (no LiveData, no RxJava)
- **Tests:** JUnit 4 (ViewModel unit tests only, no instrumentation tests)

## Required Permissions

- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` (Android 12+)
- `POST_NOTIFICATIONS` — requested at runtime on Android 13+

The app must request notification permission before starting the service, which is handled in `MainActivity`.