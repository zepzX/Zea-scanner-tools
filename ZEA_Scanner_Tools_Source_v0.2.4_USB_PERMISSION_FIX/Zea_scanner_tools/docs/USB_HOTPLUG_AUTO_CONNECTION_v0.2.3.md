# USB Hotplug and Automatic Connection — v0.2.3

## Expected behavior
- Register one Activity-scoped receiver for USB permission, `ACTION_USB_DEVICE_ATTACHED`, and `ACTION_USB_DEVICE_DETACHED`.
- On supported serial adapter attach, automatically request USB permission and open the serial port after permission is granted.
- On active/connecting/pending-permission adapter detach, close the protocol engine and reset adapter/ECU UI state immediately.
- Reconcile `UsbManager.deviceList` in `onResume()` to recover attach/detach transitions missed while the Activity was paused.
- Keep manual `SCAN USB`: it explicitly retries/reconnects and clears manual-disconnect suppression.
- Keep manual `DISCONNECT`: closes the serial port and suppresses auto-reconnect for that same VID/PID until physical detach/reattach or a manual scan.
- Ignore detach events for unrelated USB devices.

## State boundaries
`ADAPTER OPEN` means the serial port was opened. It does not mean K-Line initialization or ECU identification succeeded. ECU state is reset by `HondaProtocolEngine.disconnect()` when the adapter is physically detached or manually disconnected.

## Validation still required
Test on Android hardware with FT232RL OTG: attach while app foregrounded, attach while app backgrounded then resume, permission accept/deny, detach while connected, detach while permission dialog is pending, manual disconnect while still plugged, manual scan after manual disconnect, and unrelated USB device detach.
