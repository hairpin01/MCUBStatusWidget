# MCUB Status Widget

Android home-screen widget showing the status of a local [MCUB](https://github.com/hairpin01/MCUB-fork)
instance via its REST API (aiohttp web panel).

- Polls `<host>/status` and `/api/bot/status`
- Material You dynamic colors (Android 12+ system palette)
- Tap-to-refresh + periodic updates via AlarmManager
- Optional Bearer token for the protected API
- 2x2 resizable widget

## Build

```
./gradlew :app:assembleRelease
```
