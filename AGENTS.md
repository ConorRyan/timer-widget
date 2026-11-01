# AGENTS.md

## Working agreements

- **Keep the architecture section current.** The "Architecture" section below is
  the canonical high-level map of this codebase, so agents can load context in
  one place instead of re-reading the source every session. If you make a change
  that alters the architecture (new module or major class, changed data flow,
  moved responsibilities between layers, new process/storage mechanism), update
  that section in the same change. Do not let it drift.
- **Keep product language in line with fastlane.** The README's product-facing
  content (title, tagline, description, feature list, screenshots) is a copy of
  the store listing in `fastlane/metadata/android/en-US/` (`title.txt`,
  `short_description.txt`, `full_description.txt`, `images/`). When you change
  wording or copy on one side, update the other in the same change. Product
  copy comes first in the README; technical content follows it.
- **Aim for high test coverage, broadly.** When adding or changing code, add or
  extend unit tests. Cover new logic across the codebase, not just the file you
  touched - data models, ViewModel state transitions, formatting/util, and
  widget/service behavior with fakes. Prefer fast JVM/Robolectric unit tests
  (`./gradlew testDebugUnitTest`); keep them deterministic (inject or fake
  time rather than sleeping).
- Build with `./gradlew assembleDebug`; release with `./gradlew assembleRelease`.
  Requires JDK 21 and Android SDK platform 37. Run
  `./gradlew testDebugUnitTest` before finishing a change.
  Debug builds install as `<applicationId>.debug` ("… Debug" in the
  launcher, `app/src/debug/res/`), beside the release-signed app; target
  that package in adb. Never sign debug builds with the release key.
- Match the existing style: Kotlin, Compose for UI, coroutines for async work,
  small files grouped by package (`data`, `ui`, `util`, `widget`). 4-space
  indent, trailing commas, explicit types only where the codebase already
  uses them. Prefer pure, testable functions over Android-coupled code.

## Architecture

TimerWidget is a single-module Android app (`app/`, package
`io.github.conorryan.timerwidget`, minSdk 26 / targetSdk 36, Kotlin 2.x, Compose
via BOM, version catalog in `gradle/libs.versions.toml`). One APK, no network
access. No DI framework - classes construct their dependencies directly
(`TimerStore(context)`).

### Data flow

```
TimerStore (DataStore, JSON list of TimerDef)
      ↑ save/load
TimerViewModel (in-memory StateFlow, optimistic UI updates)
      ↑ state                        │ after save → TimerWidgetViews.refreshAll
App.kt / MainActivity (Compose UI: list ↔ full-screen editor)
      │ tile tap → TimerService.toggleTimer   re-renders every installed widget
      ↑ TimerService.version (StateFlow) → runsAt(TimerService.active) → live countdowns

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
| `TimerViewModel` | `ui/TimerViewModel.kt` | Owns UI state. Applies mutations to the in-memory list first (so bound TextFields never lag), then persists in the background and refreshes every installed widget (`TimerWidgetViews.refreshAll`) so edits show on the home screen without a tap. `toggleRun`/`stopRun` forward in-app start/stop to `TimerService` through a `RunControl`. The run control and refresh hook are constructor-injectable for tests. |
| `MainActivity` / `App.kt` | root | Hosts the theme (always-dark system bars), requests `POST_NOTIFICATIONS` on API 33+, and switches between the list and the editor (`rememberSaveable` editor target, back returns to the list). Reads `TimerService.version` and snapshots `TimerService.active` via `runsAt` on each bump. |
| `Brand` / `TimerWidgetTheme` | `ui/Theme.kt` | Fixed dark palette built on the widget green (`#0F3B2E`) with one amber accent; no dynamic color, so the app matches the widget. |
| `TimerListScreen` | `ui/TimerListScreen.kt` | Two-column grid of timer tiles: tap a tile to start it without a widget (tap again stops every run of that timer), pencil button opens the editor. A "Running" section on top shows a card per run (progress ring, remaining time, end time, Stop). Empty state with the wedge illustration. |
| `RunUi` / `runsAt` | `ui/Runs.kt` | Pure snapshot of `TimerService.active` at a given time for the list (ringing first, then soonest to end). |
| `TimerEditor` | `ui/TimerEditor.kt` | Full-screen add/edit form: name, three `TimeWheel`s, minute presets (`PRESET_MINUTES`), emoji grid (two rows, "Show all" expands; last cell is a single-character free-text entry backed by `firstGrapheme`, so pasted flags/ZWJ sequences stay whole), alarm-sound row, Delete (existing timers only). The sound picker is a `ModalBottomSheet` fed by `AlarmSounds`: tap a sound to preview it, "Use sound" applies, Cancel/dismiss leaves the choice unchanged. |
| `TimeWheel` | `ui/TimeWheel.kt` | Snap-scrolling number wheel (`LazyColumn` + center snap) for hours (0-99) / minutes / seconds; tapping the highlighted value swaps in a number field (`parseWheelEntry` clamps). Programmatic moves (presets, typing) animate without echoing intermediate values. |
| `TimerWidget` / `TimerWidgetViews` | `widget/TimerWidget.kt` | `AppWidgetProvider`; `onUpdate` renders each widget via `TimerWidgetViews.update` (async with `goAsync`). Widget tap = toggle `PendingIntent` created with `PendingIntent.getForegroundService` - a plain `getService` dispatch is rejected by the system when the app is backgrounded/force-closed ("Background start not allowed"). Unconfigured widget tap opens the app. |
| `TimerService` | `widget/TimerService.kt` | Special-use foreground service. `onStartCommand` calls `startForeground` on every delivery before handling the action (the widget tap's `getForegroundService` dispatch requires it, even for toggle-off paths). Static `active: ConcurrentHashMap<runKey, Active>` holds running timers, keyed by widget ID for widget taps or by a negative app-run key (`isAppRun`) for timers started in the app (`ACTION_TOGGLE_TIMER` via `toggleTimer`; `stopRun` reuses `ACTION_STOP_ALARM`); app runs never touch `AppWidgetManager`. A static `version` StateFlow bumps on every change and ticker second so the app redraws (end time computed from wall clock, so it survives process churn within a session). One shared ticker job fires each second: updates every widget's `RemoteViews` and the foreground notification, and plays the alarm (via `AlarmPlayer`) when a timer finishes. When no timer is left counting down, the foreground notification itself becomes the alarm (single notification, Stop action, `timer_alarm_quiet` channel); a separate swipeable alarm notification is posted only while other timers still run. Alarm channels: a single silent `timer_alarm_quiet` channel (IMPORTANCE_HIGH); legacy sound-bearing channels are deleted in `ensureChannels`, since channel sound proved unreliable (one-shot, notification-stream routing on some OEMs, unplayable URIs → silence). Stopping an alarm (notification Stop action / delete intent) clears the finished entry, so the next widget tap starts a new run instead of toggling a leftover off. Service stops itself when no timers are active. |
| `AlarmPlayer` | `widget/AlarmPlayer.kt` | Owns alarm playback: a looping `MediaPlayer` per widget on the alarm audio stream (`USAGE_ALARM`), started when a timer finishes and stopped on every dismissal path (notification delete intent, "Stop" action, widget toggle-off, Clear All, service destroy). Falls back from the per-timer tone to the system default alarm tone if the picked tone is unplayable. |
| `TimerWidgetConfig` | `widget/TimerWidgetConfig.kt` | Widget configure/reconfigure activity: pick which timer a widget controls, stores the mapping in `WidgetPrefs`, returns `RESULT_OK`. |
| `WidgetPrefs` | `widget/WidgetPrefs.kt` | SharedPreferences (`widgets` file) mapping `widget_<id>` -> timer ID; cleared in `onDeleted`. |
| `formatHms` | `util/Format.kt` | `m:ss` / `h:mm:ss` formatting used by app, widget, and notifications. |
| `AlarmSound` / `AlarmSounds` | `util/AlarmSounds.kt` | Enumerates the device's preloaded alarm ringtones via `RingtoneManager` for the per-timer sound picker; `defaultUri` resolves the "Default" option's preview and the alarm fallback tone. |
| `SoundPreviewPlayer` | `util/SoundPreview.kt` | One-shot preview of a candidate alarm tone while the user picks sounds (alarm stream, no looping; replaced on the next tap, stopped when the picker closes). |

### Tests

JUnit + Robolectric under `app/src/test/`: `TimerDefTest` (model),
`TimerViewModelTest` (state transitions, post-save widget refresh, run control), `RunsTest` (run snapshot), `TimeWheelTest` (wheel labels/typing, preset matching), `FormatHmsTest` (formatting), `FirstGraphemeTest`
(emoji entry grapheme handling), `FrenchStringsTest` (every UI string keeps a
French translation; UI is localized en + fr via `values-fr` and a
`localeConfig` for per-app language on API 33+),
`AlarmPlayerTest` (playback + fallback), `SoundPreviewPlayerTest`
(preview playback), `TimerServiceStopAlarmTest`,
`TimerServiceStartForegroundTest` (startForeground contract for every
command), `TimerServiceAppRunTest` (in-app toggle, run keys, version), `TimerWidgetViewsTest` (toggle PendingIntent dispatches as a
foreground-service start). Run with
`./gradlew testDebugUnitTest`.

### Notable invariants

- Run keys, not timer IDs, key the runtime: multiple widgets (and the app) can
  run the same timer definition independently. Widget IDs are positive; app
  runs use negative keys counted up from `Int.MIN_VALUE`, so their alarm
  notification IDs never meet a widget's.
- `TimerService.active` is a static map - widget rendering checks it first, so
  running timers show live countdowns even when re-rendered from the provider.
- The service's ticker aligns to the next wall-clock second
  (`delay(1000 - now % 1000)`); `Active.displaySeconds` shows one less than the
  ceiling so a just-started timer visibly drops from its total.
- UI state is never blocked on DataStore writes (see `TimerViewModel.mutate`).
