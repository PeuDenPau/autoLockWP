# AutoLockWp

An Android app that composes custom lock-screen wallpapers (KLWP-style) and refreshes
them on a user-selected interval (every 1, 5, 10, 15, 30, 60 minutes...).

**Download**: prebuilt APK at [`build/AutoLockWp-debug.apk`](build/AutoLockWp-debug.apk)

## How it works

1. **Editor**: build a scene out of layers — solid color, image (PNG with transparency
   supported), text layers and groups. Layers have X/Y position, anchor (pivot),
   rotation and opacity; the selected layer can be dragged directly on the preview.
2. **Text layers** render a template with live variables, e.g.
   `$TIME_HH$:$TIME_MM$` or `Battery $BATT$%`. Several **bundled FOSS fonts**
   (SIL Open Font License) are preinstalled — Share Tech Mono, Bebas Neue,
   Lobster, Pacifico, Abril Fatface, Space Mono — and you can import your own
   TTF/OTF fonts (Settings → Import font).
3. **Apply**: the preset is rendered at the wallpaper's desired resolution and set as
   the **lock screen wallpaper** (`WallpaperManager.setBitmap(..., FLAG_LOCK)`).
4. **Scheduler**: an exact repeating alarm (`AlarmManager.setExactAndAllowWhileIdle`)
   re-renders and re-applies the wallpaper on your interval. WorkManager is not used
  below 15 minutes because of its minimum period floor.

## Building

No Gradle wrapper is committed (it cannot be generated without a JDK in the authoring
environment). Open the project in Android Studio (it will configure the wrapper), or
run `gradle wrapper --gradle-version 8.7` locally, then:

```
./gradlew :app:assembleDebug
```

Requirements: JDK 17, Android SDK 34. Gradle wrapper 8.7 is included.
A prebuilt debug APK is committed at `build/AutoLockWp-debug.apk`.

## First run / permissions

- On Android 12+ the app needs **exact alarm** permission for sub-15-minute intervals.
  Grant it via Settings → "Request exact alarm permission" (the app opens the system
  dialog) or fall back to inexact updates.
- Images are imported with the system Photo Picker; fonts with the document picker.
  No storage permissions are requested.

## Available variables

| Variable | Meaning |
|---|---|
| `$TIME_HH$` / `$TIME_H$` | hour 00–23 / hour without leading zero |
| `$TIME_H12$` / `$TIME_AMPM$` | 12-hour hour / AM-PM marker |
| `$TIME_MM$` / `$TIME_SS$` | minutes / seconds |
| `$DATE_DD$` / `$DATE_MM$` / `$DATE_YYYY$` | day / month / year |
| `$DATE_WEEKDAY$` / `$DATE_MONTH$` | weekday name / month name |
| `$BATT$` / `$BATT_STATE$` | battery percent / charging state |

## Architecture

```
data/        Layer.kt (polymorphic preset model, kotlinx.serialization JSON)
             PresetStore.kt (persistence + settings)
render/       AssetStore.kt (image/font asset cache + import)
             Renderer.kt (pure canvas renderer, editor + background share it)
             Variables.kt (live variable provider)
             LockWallpaperApplier.kt (render + set lock wallpaper)
schedule/    RenderScheduler.kt (exact alarm scheduler, alarm + boot receivers)
ui/          MainActivity + Compose editor (preview, layer tree, properties)
```

## Roadmap (planned iterations)

- Pedometer layer (`TYPE_STEP_COUNTER` sensor), screen-on-time
  (`UsageStatsManager` with `PACKAGE_USAGE_STATS` special access), currently-playing
  media (`MediaSessionManager`/`NotificationListener`).
- Shape layers (rect, circle, gradient, progress bars/arc meters).
- Move-to-live-wallpaper engine (real-time lock screen clock, like KLCK) as an
  alternative to periodic bitmap updates.
- Multiple presets, per-time-of-day preset switching, export/import of presets.
