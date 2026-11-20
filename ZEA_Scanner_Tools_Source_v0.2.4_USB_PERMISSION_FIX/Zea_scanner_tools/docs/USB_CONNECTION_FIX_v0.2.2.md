# USB connection flow fix — v0.2.2

## Observed issue
The application could enumerate one USB device and show `1 perangkat USB ditemukan`, while the dashboard remained `DISCONNECTED`. Enumeration alone did not open the serial interface. The dashboard also initialized its connection detail text as disconnected even when the protocol engine had an open adapter.

## Changes
- When exactly one supported USB-serial family is enumerated (FTDI, WCH CH34x, Silicon Labs CP210x, Prolific PL2303, or common Arduino CDC VID), the app now proceeds automatically to USB permission and serial-port opening.
- When multiple devices are present, the app asks the user to select the device row; it does not guess which one is the diagnostic adapter.
- Permission-pending, port-opening, successful-open, failed-open, and exception states are shown explicitly.
- A successful open is labelled as USB serial port open; this is not represented as proof that the ECU has responded or K-Line initialization succeeded.
- Dashboard connection detail now reflects `engine.isAdapterOpen` after page rebuild.
- Disconnect updates the visible status and keeps disconnect separate from scanning.

## Validation limits
This patch has been source-reviewed only in the current environment. No Android SDK/Gradle installation is available here, and no physical FT232RL adapter is attached. Build and hardware validation remain required. ECU/K-Line behavior is intentionally unchanged.
