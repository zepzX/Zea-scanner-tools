# ZEA Scanner Tools v0.2.0 — UI and scanner rebuild

## Scope
- Replaced the old ZEA ECU Monitor-style single long screen with a diagnostic-first multi-page shell.
- Navigation: Home, Live Data, DTC, ECU Information; Settings from the top app bar.
- Uses the RevCore `:core` module for USB/K-Line session, ECU identification, live-data query, DTC read, and DTC clear APIs.
- No 162 FlashPro `.map` files are bundled.

## Important capability boundaries
- The current core exposes `readDtcs()`, `clearDtcs()`, and `queryLiveData()`.
- `clearDtcs()` reports whether the ECU family/profile supports a clear command and verifies the result by reading DTCs again.
- The current core does not expose a verified ECU reset command. The UI therefore shows ECU Reset as unavailable rather than pretending that disconnect/reconnect or Clear DTC is an ECU reset.
- Live Data is displayed only for sensor values whose parser state is `VALID`. Unsupported/unverified profiles display a clear unavailable message; no sample values are fabricated.
- USB adapter open is not treated as proof that the ECU has responded.

## Reference fidelity
The supplied RevCore APK is used as the visual and navigation reference. The APK is a compiled artifact, not the editable source for its original UI. This rebuild follows its compact dark diagnostic-app direction and navigation hierarchy while retaining ZEA identity. Pixel-perfect reproduction requires original RevCore UI source or screenshots of the relevant screens.

## Build
From the project root:

```bat
gradle :app:assembleDebug --no-daemon --max-workers=1
```

APK output:
`app\build\outputs\apk\debug\app-debug.apk`
