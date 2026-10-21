# AGENTS.md

## Working agreements

- **Keep the architecture section current.** The "Architecture" section below is
  the canonical high-level map of this codebase, so agents can load context in
  one place instead of re-reading the source every session. If you make a change
  that alters the architecture (new module or major class, changed data flow,
  moved responsibilities between layers, new process/storage mechanism), update
  that section in the same change. Do not let it drift.
- **Aim for high test coverage, broadly.** When adding or changing code, add or
  extend unit tests. Cover new logic across the codebase, not just the file you
  touched — data models, ViewModel state transitions, formatting/util, and
  widget/service behavior with fakes. Prefer fast JVM/Robolectric unit tests
  (`./gradlew testDebugUnitTest`); keep them deterministic (inject or fake
  time rather than sleeping).
- Build with `./gradlew assembleDebug`; release with `./gradlew assembleRelease`.
  Requires JDK 21 and Android SDK platform 37. Run
  `./gradlew testDebugUnitTest` before finishing a change.
- Match the existing style: Kotlin, Compose for UI, coroutines for async work,
  small files grouped by package (`data`, `ui`, `util`, `widget`). 4-space
  indent, trailing commas, explicit types only where the codebase already
  uses them. Prefer pure, testable functions over Android-coupled code.

## Architecture

TimerWidget is a single-module Android app (`app/`, package
`io.github.conorryan.timerwidget`, minSdk 26 / targetSdk 36, Kotlin 2.x, Compose
via BOM, version catalog in `gradle/libs.versions.toml`). One APK, no network
access. No DI framework — classes construct their dependencies directly
(`TimerStore(context)`).

### Data flow

```
TimerStore (DataStore, JSON list of TimerDef)
      ↑ save/load
TimerViewModel (in-memory StateFlow, optimistic UI updates)
      ↑ state                        │ after save → TimerWidgetViews.refreshAll
App.kt / MainActivity (Compose UI: list, add/edit dialog)
      re-renders every installed widget so edits show without a tap

WidgetPrefs (SharedPreferences: widgetId -> timerId)
      ↑ read by widget + service

TimerWidget (AppWidgetProvider)
      ↓ tap -> PendingIntent
TimerService (foreground service, 1s ticker)
      ↓ RemoteViews
Home-screen widgets (per-widget live countdown)
```

### Components

| Component | Path | Role |
|---|---|---|
| `TimerDef` / `TimerStore` | `data/Timers.kt` | Serializable timer model (incl. picked alarm ringtone URI + title); persists the timer list as one JSON string in DataStore Preferences (`timers` store, key `timers_json`). Corrupt data decodes to an empty list. |
| `TimerViewModel` | `ui/TimerViewModel.kt` | Owns UI state. Applies mutations to the in-memory list first (so bound TextFields never lag), then persists in the background and refreshes every installed widget (`TimerWidgetViews.refreshAll`) so edits show on the home screen without a tap. The refresh hook is constructor-injectable for tests. |
| `MainActivity` / `App.kt` | root | Compose list of timers with add/edit `AlertDialog` (name, emoji — curated quick-pick grid whose last cell is a single-character free-text entry backed by `firstGrapheme` (first grapheme cluster, so pasted flags/ZWJ sequences stay whole), h/m/s fields, alarm-sound picker fed by `AlarmSounds`: tap a sound to preview it, OK applies, Cancel leaves the choice unchanged); requests `POST_NOTIFICATIONS` on API 33+. |
| `TimerWidget` / `TimerWidgetViews` | `widget/TimerWidget.kt` | `AppWidgetProvider`; `onUpdate` renders each widget via `TimerWidgetViews.update` (async with `goAsync`). Widget tap = toggle `PendingIntent` created with `PendingIntent.getForegroundService` — a plain `getService` dispatch is rejected by the system when the app is backgrounded/force-closed ("Background start not allowed"). Unconfigured widget tap opens the app. |
| `TimerService` | `widget/TimerService.kt` | Special-use foreground service. `onStartCommand` calls `startForeground` on every delivery before handling the action (the widget tap's `getForegroundService` dispatch requires it, even for toggle-off paths). Static `active: ConcurrentHashMap<widgetId, Active>` holds running timers (end time computed from wall clock, so it survives process churn within a session). One shared ticker job fires each second: updates every widget's `RemoteViews` and the foreground notification, and plays the alarm (via `AlarmPlayer`) when a timer finishes. When no timer is left counting down, the foreground notification itself becomes the alarm (single notification, Stop action, `timer_alarm_quiet` channel); a separate swipeable alarm notification is posted only while other timers still run. Alarm channels: a single silent `timer_alarm_quiet` channel (IMPORTANCE_HIGH); legacy sound-bearing channels are deleted in `ensureChannels`, since channel sound proved unreliable (one-shot, notification-stream routing on some OEMs, unplayable URIs → silence). Stopping an alarm (notification Stop action / delete intent) clears the finished entry, so the next widget tap starts a new run instead of toggling a leftover off. Service stops itself when no timers are active. |
| `AlarmPlayer` | `widget/AlarmPlayer.kt` | Owns alarm playback: a looping `MediaPlayer` per widget on the alarm audio stream (`USAGE_ALARM`), started when a timer finishes and stopped on every dismissal path (notification delete intent, "Stop" action, widget toggle-off, Clear All, service destroy). Falls back from the per-timer tone to the system default alarm tone if the picked tone is unplayable. |
| `TimerWidgetConfig` | `widget/TimerWidgetConfig.kt` | Widget configure/reconfigure activity: pick which timer a widget controls, stores the mapping in `WidgetPrefs`, returns `RESULT_OK`. |
| `WidgetPrefs` | `widget/WidgetPrefs.kt` | SharedPreferences (`widgets` file) mapping `widget_<id>` -> timer ID; cleared in `onDeleted`. |
| `formatHms` | `util/Format.kt` | `m:ss` / `h:mm:ss` formatting used by app, widget, and notifications. |
| `AlarmSound` / `AlarmSounds` | `util/AlarmSounds.kt` | Enumerates the device's preloaded alarm ringtones via `RingtoneManager` for the per-timer sound picker; `defaultUri` resolves the "Default" option's preview and the alarm fallback tone. |
| `SoundPreviewPlayer` | `util/SoundPreview.kt` | One-shot preview of a candidate alarm tone while the user picks sounds (alarm stream, no looping; replaced on the next tap, stopped when the picker closes). |

### Tests

JUnit + Robolectric under `app/src/test/`: `TimerDefTest` (model),
`TimerViewModelTest` (state transitions, post-save widget refresh), `FormatHmsTest` (formatting), `FirstGraphemeTest`
(emoji entry grapheme handling),
`AlarmPlayerTest` (playback + fallback), `SoundPreviewPlayerTest`
(preview playback), `TimerServiceStopAlarmTest`,
`TimerServiceStartForegroundTest` (startForeground contract for every
command), `TimerWidgetViewsTest` (toggle PendingIntent dispatches as a
foreground-service start). Run with
`./gradlew testDebugUnitTest`.

### Notable invariants

- Widget IDs, not timer IDs, key the runtime: multiple widgets can run the same
  timer definition independently.
- `TimerService.active` is a static map — widget rendering checks it first, so
  running timers show live countdowns even when re-rendered from the provider.
- The service's ticker aligns to the next wall-clock second
  (`delay(1000 - now % 1000)`); `Active.displaySeconds` shows one less than the
  ceiling so a just-started timer visibly drops from its total.
- UI state is never blocked on DataStore writes (see `TimerViewModel.mutate`).
