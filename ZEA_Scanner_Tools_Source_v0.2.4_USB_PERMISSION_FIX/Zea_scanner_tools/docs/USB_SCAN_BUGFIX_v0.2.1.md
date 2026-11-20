# USB Scan bug fix — v0.2.1

## Perubahan
- Tombol Scan USB memberi umpan balik langsung saat ditekan.
- Enumerasi `UsbManager.deviceList` dijalankan pada `Dispatchers.IO`.
- Status hasil menampilkan jumlah perangkat yang terlihat Android USB Host.
- Daftar perangkat menampilkan VID/PID empat digit dan status izin USB.
- Error enumerasi ditampilkan di UI dan dicatat dengan tag `ZEA_USB`.
- Pemindaian ganda dicegah selama proses berlangsung.

## Batasan validasi
Perubahan ini memperbaiki alur tombol dan visibilitas hasil enumerasi. Keberhasilan mendeteksi FT232RL tetap harus diuji pada ponsel Android, kabel OTG, dan adapter fisik. Hasil `0 perangkat` berarti Android USB Host belum melihat perangkat; itu belum membuktikan kerusakan FT232RL.

## Build
Dari folder project:

```bat
gradle :app:assembleDebug --no-daemon --max-workers=1
```

APK keluaran:
`app\build\outputs\apk\debug\app-debug.apk`
