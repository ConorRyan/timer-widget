# Timer Widget

A minimal Android home-screen widget for launching countdown timers. Define named
timers (with an optional emoji) in the app, place one or more 1x1 widgets on your
home screen, and tap a widget to start or cancel its countdown. Each widget
counts down live, and a high-priority alarm notification fires when the timer
finishes.

## Features

- Multiple timers, each with a name, emoji, and hours/minutes/seconds duration
- One 1x1 home-screen widget per timer; tap to start, tap again to cancel
- Live per-second countdown rendered directly on the widget
- Foreground service keeps all running timers ticking, with a persistent
  "next to finish" notification
- Alarm-sound notification (with vibration) when a timer completes
- Timer list persisted locally via DataStore; no network access, no analytics
- Light/dark Material 3 theme

## Screens

| Screen | Purpose |
|---|---|
| Main app | Create, edit, and delete timers (name, emoji, duration) |
| Widget config | Shown when placing a widget: pick which timer it controls; reconfigurable later |
| Widget | Shows the timer's emoji/name and remaining time; tapping toggles start/cancel |

## Requirements

- Android 8.0 (API 26) or newer
- JDK 21
- Android SDK (platform 37, build-tools, platform-tools)

## Building

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # JVM + Robolectric tests
./gradlew assembleRelease      # release APK (minified, resource-shrunk)
```

Install the debug APK on a connected device/emulator with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Testing

Unit tests (JUnit + Robolectric) cover the data model, ViewModel logic, and
time formatting: `./gradlew testDebugUnitTest` (same command as above).

## How it works

- **App UI** (`MainActivity` + `App.kt`): Jetpack Compose list of timers backed
  by `TimerViewModel`, which keeps UI state in memory and persists to DataStore
  in the background so text input never lags.
- **Storage**: `TimerStore` serializes the timer list as JSON into a DataStore
  Preferences file; `WidgetPrefs` maps each widget instance to a timer ID in
  plain SharedPreferences.
- **Widget** (`TimerWidget`): the `AppWidgetProvider` renders the initial view
  from `TimerWidgetViews`; each tap sends a toggle `PendingIntent` to
  `TimerService`.
- **Service** (`TimerService`): a special-use foreground service holds all
  running timers in memory, ticks once per second, pushes updated `RemoteViews`
  to each widget, and posts an alarm notification on completion.

## Project layout

```
app/src/main/java/io/github/conorryan/timerwidget/
├── App.kt                  # Compose timer list + add/edit dialog
├── MainActivity.kt         # Launcher activity, notification permission
├── data/Timers.kt          # TimerDef model + DataStore-backed TimerStore
├── ui/                     # Theme + TimerViewModel
├── util/Format.kt          # formatHms helper
└── widget/                 # AppWidgetProvider, config activity, service, prefs
```

## License

MIT — see [LICENSE](LICENSE). Third-party notices are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
