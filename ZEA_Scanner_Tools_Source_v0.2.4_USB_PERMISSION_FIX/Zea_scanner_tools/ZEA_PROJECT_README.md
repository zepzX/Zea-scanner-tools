# ZEA Scanner Tools — Developer Project

Project awal aplikasi diagnostik Android berbasis library `:core` RevCore.

## Struktur
- `:app` — aplikasi Android ZEA Scanner Tools (package `com.zea.scannertools`).
- `:core` — communication/protocol/diagnostic library dari repository RevCore yang disertakan sebagai dependency lokal.

## Build debug
Gunakan JDK 21 dan Android SDK 36. Dari folder root proyek:

```bat
gradle :app:assembleDebug --no-daemon --max-workers=1
```

Jika Gradle Wrapper sudah ditambahkan di lingkungan pengembangan, perintah setaranya:

```bat
gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
```

APK akan berada di:

`app\build\outputs\apk\debug\app-debug.apk`

## Status awal
- Proyek Gradle sudah memiliki modul `:app` dan `:core`.
- UI awal menggunakan patch ZEA ECU Monitor 0.4.0 sebagai titik awal, dengan package dan branding ZEA Scanner Tools.
- Tombol scan USB dan alur permission USB tersedia pada layar awal.
- Status koneksi sengaja menyebut **USB adapter open**, bukan ECU/K-Line connected. `HondaProtocolEngine.connect()` membuka interface USB dan menginisiasi proses wakeup; keberhasilan membuka USB bukan bukti ECU menjawab.
- Identifikasi ECU dan Live Data memanggil API dari `:core`.
- Katalog Live Data bawaan `:core` kosong / belum menyediakan profil terverifikasi. Karena itu jangan menganggap Live Data sudah siap untuk model motor tertentu.
- Belum dilakukan build Android pada lingkungan ini dan belum ada pengujian dengan adapter FT232RL/ECU fisik.

## Catatan hak penggunaan
Ikuti `NOTICE.md` dari repository RevCore. Repository tersebut menyatakan penggunaan dan distribusi modifikasi tunduk pada persetujuan pemilik. Pastikan persetujuan yang dimiliki mencakup integrasi dan distribusi APK ZEA Scanner Tools.
