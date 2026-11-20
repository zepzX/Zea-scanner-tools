# USB permission and default-handler behavior — v0.2.4

## User goal
Reduce repeated USB permission dialogs while ZEA Scanner Tools is used with an FTDI adapter. Keep manual SCAN USB and DISCONNECT controls.

## Implementation
- Declare an Activity intent filter for `android.hardware.usb.action.USB_DEVICE_ATTACHED`.
- Associate the filter with `@xml/usb_device_filter`, matching FTDI vendor ID `0x0403` (decimal 1027), without restricting product ID.
- Use `singleTop` and handle both the initial Activity intent and `onNewIntent()` so an attach event delivered by Android is processed while the app is already open.
- When Android routes the attached device to ZEA as the selected/default handler, Android grants access for that attachment; the normal `hasPermission()` check then proceeds directly to serial open without calling `requestPermission()` again.
- Keep a runtime receiver for detach and USB permission results. Handle attach through the manifest Activity intent filter (rather than racing a runtime receiver permission request) and keep the in-app manual scan/disconnect controls.

## Android platform limitation
USB Host permission is controlled by Android and is scoped to a connected device/attachment. An app cannot persist or silently re-grant USB permission itself after physical detach. The attach intent filter lets Android offer ZEA as the handler and remember the user's default choice where the Android build supports that behavior. The first attachment may still show a system chooser/permission prompt. Some OEM Android builds may continue to show a prompt on subsequent attachments; the app cannot override that policy.

## Validation required
On the target phone, test first attach and choose the option equivalent to "Always use this app" if offered. Then unplug/replug while ZEA remains open and again after app restart. Confirm whether Android routes the attach intent to ZEA and whether `UsbManager.hasPermission(device)` is true before the app requests permission. Also test manual SCAN USB and DISCONNECT.
