package com.zea.scannertools

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.revcore.android.core.comm.LiveSensor
import com.revcore.android.core.protocol.ClearDtcStatus
import com.revcore.android.core.protocol.DtcReadResult
import com.revcore.android.core.protocol.HondaProtocolEngine
import com.revcore.android.core.protocol.LiveTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ZEA Scanner Tools. Diagnostic-first UI built around the RevCore protocol core. */
class MainActivity : ComponentActivity() {
    private lateinit var usbManager: UsbManager
    private lateinit var engine: HondaProtocolEngine
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var connectionValue: TextView
    private lateinit var ecuSummary: TextView
    private lateinit var dtcSummary: TextView
    private lateinit var liveSummary: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var usbScanStatus: TextView
    private var usbScanInProgress = false
    private var usbConnectInProgress = false
    private var usbConnectJob: Job? = null
    private lateinit var dtcList: LinearLayout
    private lateinit var liveList: LinearLayout
    private lateinit var navBar: LinearLayout
    private var selectedPage = PAGE_HOME
    private var liveJob: Job? = null
    private var receiverRegistered = false
    private var activeUsbDeviceId: Int? = null
    private var connectingUsbDeviceId: Int? = null
    private var pendingUsbPermissionDeviceId: Int? = null
    private var activeUsbVendorId: Int? = null
    private var activeUsbProductId: Int? = null
    private var manuallyDisconnectedDeviceId: Int? = null
    private var manuallyDisconnectedKey: String? = null
    private val permissionAction = "com.zea.scannertools.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            when (intent.action) {
                permissionAction -> {
                    pendingUsbPermissionDeviceId = null
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                        connectDevice(device)
                    } else {
                        if (::usbScanStatus.isInitialized) {
                            usbScanStatus.text = "Izin USB ditolak. Izinkan akses lalu tekan SCAN USB kembali."
                            usbScanStatus.setTextColor(AMBER)
                        }
                        Log.w(USB_LOG_TAG, "USB permission denied for ${device?.deviceName ?: "unknown device"}")
                        toast("Izin USB ditolak")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (device != null) onUsbAttached(device)
                    else autoConnectAvailableUsb()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (device != null) onUsbDetached(device)
                    else reconcileUsbState()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        engine = HondaProtocolEngine(usbManager)
        val filter = IntentFilter().apply {
            addAction(permissionAction)
            // Attach is handled through the manifest USB_DEVICE_ATTACHED intent
            // filter so Android can grant access via its default-handler flow.
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION") registerReceiver(usbReceiver, filter)
        }
        receiverRegistered = true
        buildShell()
        showPage(PAGE_HOME)
        // The system may launch this Activity with a USB attach intent and grant
        // access after the user chooses ZEA as the default handler.
        handleUsbAttachIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbAttachIntent(intent)
    }

    private fun handleUsbAttachIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        if (device != null) {
            Log.i(USB_LOG_TAG, "USB attach intent delivered by Android for ${device.deviceName}; hasPermission=${usbManager.hasPermission(device)}")
            onUsbAttached(device)
        } else {
            autoConnectAvailableUsb()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reconcile in case Android delivered attach/detach while Activity was paused.
        reconcileUsbState()
    }

    override fun onDestroy() {
        liveJob?.cancel()
        if (receiverRegistered) unregisterReceiver(usbReceiver)
        engine.disconnect()
        super.onDestroy()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(16), dp(12))
            setBackgroundColor(SURFACE)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("ZEA SCANNER", 18, WHITE, true))
        brand.addView(text("HONDA DIAGNOSTIC", 10, MUTED, true))
        toolbar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = text("⚙", 23, SOFT, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { showPage(PAGE_SETTINGS) }
        }
        toolbar.addView(settings)
        root.addView(toolbar)
        val divider = View(this).apply { setBackgroundColor(STROKE) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setBackgroundColor(SURFACE)
        }
        root.addView(navBar, LinearLayout.LayoutParams(-1, dp(66)))
        setContentView(root)
    }

    private fun showPage(page: Int) {
        selectedPage = page
        liveJob?.cancel()
        content.removeAllViews()
        when (page) {
            PAGE_HOME -> buildHome()
            PAGE_LIVE -> buildLivePage()
            PAGE_DIAG -> buildDiagnosticsPage()
            PAGE_ECU -> buildEcuPage()
            else -> buildSettingsPage()
        }
        buildNavigation()
    }

    private fun buildHome() {
        heading("Dashboard", "Vehicle diagnostic overview")
        val statusCard = card()
        row(statusCard).apply {
            addView(text("CONNECTION", 11, MUTED, true), LinearLayout.LayoutParams(0, -2, 1f))
            statusCard.addView(text("●  ${connectionLabel()}", 11, if (engine.isAdapterOpen.value) GREEN else AMBER, true))
        }
        connectionValue = text(
            if (engine.isAdapterOpen.value) "Port serial USB terbuka · ECU belum diverifikasi" else "Adapter belum terhubung",
            15, WHITE, true
        )
        statusCard.addView(connectionValue, marginTop(8))
        statusCard.addView(text("FTDI USB  ·  HONDA K-LINE", 10, MUTED, false), marginTop(4))
        val connectRow = row(statusCard)
        connectRow.addView(action("SCAN USB", true) { scanUsb() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        connectRow.addView(action("DISCONNECT", false) { disconnect() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        content.addView(statusCard)

        headingSmall("QUICK STATUS")
        val quick = card()
        ecuSummary = text("ECU  ·  Belum diidentifikasi", 13, SOFT, true)
        dtcSummary = text("DTC  ·  Belum diperiksa", 13, SOFT, true)
        liveSummary = text("LIVE DATA  ·  Menunggu koneksi", 13, SOFT, true)
        quick.addView(ecuSummary)
        quick.addView(dtcSummary, marginTop(12))
        quick.addView(liveSummary, marginTop(12))
        content.addView(quick)

        headingSmall("DIAGNOSTIC TOOLS")
        val tools = card()
        toolRow(tools, "Live Data", "Monitor parameter sensor ECU", "›") { showPage(PAGE_LIVE) }
        line(tools)
        toolRow(tools, "DTC Scanner", "Read and clear trouble codes", "›") { showPage(PAGE_DIAG) }
        line(tools)
        toolRow(tools, "ECU Information", "ECU identity and connection", "›") { showPage(PAGE_ECU) }
        content.addView(tools)
        headingSmall("USB DEVICES")
        val usbCard = card()
        usbScanStatus = text(
            if (engine.isAdapterOpen.value) "TERHUBUNG · port serial terbuka. ECU belum diverifikasi."
            else "Belum melakukan pemindaian USB.",
            11, if (engine.isAdapterOpen.value) GREEN else MUTED
        )
        usbCard.addView(usbScanStatus)
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        usbCard.addView(deviceList, marginTop(8))
        content.addView(usbCard)
        renderUsbDevices()
    }

    private fun buildLivePage() {
        heading("Live Data", "Sensor values reported by the ECU")
        val info = card()
        info.addView(text("LIVE MONITOR", 11, MUTED, true))
        info.addView(text("Values are shown only when a valid ECU profile and response are available.", 12, SOFT), marginTop(6))
        val buttons = row(info)
        buttons.addView(action("START MONITOR", true) { startLiveMonitor() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        buttons.addView(action("STOP", false) { liveJob?.cancel(); toast("Live monitor dihentikan") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        content.addView(info)
        headingSmall("SENSOR PARAMETERS")
        val values = card()
        liveList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        values.addView(liveList)
        content.addView(values)
        renderLiveData(engine.liveTelemetry.value)
        if (!engine.liveDataSupported) note("Profil Live Data untuk keluarga ECU yang terdeteksi belum tersedia/terverifikasi. Tidak ada nilai simulasi yang akan ditampilkan.")
    }

    private fun buildDiagnosticsPage() {
        heading("Diagnostics", "Read and clear ECU trouble codes")
        val actions = card()
        actions.addView(text("DIAGNOSTIC TROUBLE CODES", 11, MUTED, true))
        actions.addView(text("Baca DTC untuk melihat kode gangguan yang dilaporkan ECU.", 12, SOFT), marginTop(6))
        val actionRow = row(actions)
        actionRow.addView(action("READ DTC", true) { readDtcs() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        actionRow.addView(action("CLEAR DTC", false) { confirmClearDtc() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        content.addView(actions)
        headingSmall("FAULT CODES")
        val list = card()
        dtcList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(dtcList)
        content.addView(list)
        renderDtcs(engine.activeDtcs.value, "Belum ada hasil pembacaan DTC.")
        headingSmall("ECU RESET")
        val reset = card()
        reset.addView(text("ECU RESET", 11, MUTED, true))
        reset.addView(text("Fungsi reset ECU akan diaktifkan setelah prosedur reset untuk keluarga ECU yang teridentifikasi diverifikasi. Reset tidak akan disamakan dengan Clear DTC.", 12, SOFT), marginTop(6))
        reset.addView(action("ECU RESET  ·  NOT AVAILABLE YET", false, enabled = false) { }, marginTop(12))
        content.addView(reset)
    }

    private fun buildEcuPage() {
        heading("ECU Information", "Identification and session status")
        val c = card()
        c.addView(text("ECU IDENTITY", 11, MUTED, true))
        val model = engine.connectedModel.value
        val identity = engine.ecuIdentity.value
        val info = if (model != null) {
            "${model.bikeName}\nPart number: ${model.partNumber}\nECM ID: ${identity?.ecmId ?: identity?.ecuIdRaw ?: "Not available"}\n${model.statusNotes}"
        } else {
            "ECU belum teridentifikasi.\nRaw ID: ${identity?.ecuIdRaw ?: "—"}\nPart number: ${identity?.partNumber ?: "—"}"
        }
        c.addView(text(info, 13, SOFT), marginTop(10))
        c.addView(action("IDENTIFY ECU", true) { identifyEcu() }, marginTop(14))
        c.addView(action("SCAN USB ADAPTER", false) { scanUsb() }, marginTop(8))
        content.addView(c)
        headingSmall("SESSION")
        val session = card()
        session.addView(text("USB adapter: ${if (engine.isAdapterOpen.value) "OPEN" else "CLOSED"}", 12, SOFT))
        session.addView(text("K-Line initialized: ${engine.isKlineInitialized.value}", 12, SOFT), marginTop(8))
        session.addView(text("ECU response: ${engine.isEcuAlive.value}", 12, SOFT), marginTop(8))
        content.addView(session)
    }

    private fun buildSettingsPage() {
        heading("Settings", "ZEA Scanner Tools")
        val c = card()
        c.addView(text("APPLICATION", 11, MUTED, true))
        c.addView(text("ZEA Scanner Tools\nVersion 0.2.3\nDiagnostic core: RevCore", 13, SOFT), marginTop(8))
        content.addView(c)
        val noteCard = card()
        noteCard.addView(text("DIAGNOSTIC SAFETY", 11, MUTED, true))
        noteCard.addView(text("Clear DTC and ECU Reset are separate operations. A cleared code does not repair the cause of a fault. ECU Reset remains unavailable until the exact procedure is validated for the connected ECU family.", 12, SOFT), marginTop(8))
        content.addView(noteCard)
    }

    private fun buildNavigation() {
        navBar.removeAllViews()
        val items = listOf("⌂\nHome" to PAGE_HOME, "⌁\nLive" to PAGE_LIVE, "⚠\nDTC" to PAGE_DIAG, "▣\nECU" to PAGE_ECU)
        items.forEach { (label, page) ->
            val active = selectedPage == page
            val item = text(label, 11, if (active) ACCENT else MUTED, active).apply {
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(2), dp(5), dp(2))
                setOnClickListener { showPage(page) }
            }
            navBar.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    /** Enumerate Android USB host devices and always provide visible feedback. */
    private fun scanUsb() {
        // Manual scan explicitly overrides a previous manual-disconnect suppression.
        manuallyDisconnectedKey = null
        manuallyDisconnectedDeviceId = null
        if (usbScanInProgress) {
            toast("Pemindaian USB sedang berjalan")
            return
        }
        if (selectedPage != PAGE_HOME) showPage(PAGE_HOME)
        if (!::deviceList.isInitialized || !::usbScanStatus.isInitialized) {
            Log.e(USB_LOG_TAG, "USB scan requested before dashboard views were initialized")
            toast("Halaman scanner belum siap. Coba lagi.")
            return
        }

        usbScanInProgress = true
        usbScanStatus.text = "Memindai perangkat USB…"
        usbScanStatus.setTextColor(ACCENT)
        deviceList.removeAllViews()
        deviceList.addView(text("Mengambil daftar perangkat dari Android USB Host…", 12, MUTED))
        toast("Memindai perangkat USB…")

        lifecycleScope.launch {
            try {
                val devices = withContext(Dispatchers.IO) {
                    usbManager.deviceList.values.toList()
                }
                renderUsbDevices(devices)
                val message = if (devices.isEmpty()) {
                    "Pemindaian selesai · 0 perangkat USB terdeteksi. Periksa OTG dan kabel."
                } else {
                    "Pemindaian selesai · ${devices.size} perangkat USB terdeteksi."
                }
                usbScanStatus.text = message
                usbScanStatus.setTextColor(if (devices.isEmpty()) AMBER else GREEN)
                Log.i(USB_LOG_TAG, "USB scan completed: ${devices.size} device(s)")
                if (devices.isEmpty()) {
                    toast("Tidak ada perangkat USB terdeteksi")
                } else if (devices.size == 1 && isSupportedSerialDevice(devices.first())) {
                    // One supported serial adapter: continue into permission/open flow automatically.
                    toast("Adapter ditemukan. Memulai koneksi…")
                    requestPermission(devices.first())
                } else {
                    toast("${devices.size} perangkat USB ditemukan. Pilih adapter pada daftar.")
                }
            } catch (e: Exception) {
                Log.e(USB_LOG_TAG, "USB enumeration failed", e)
                usbScanStatus.text = "Pemindaian USB gagal: ${e.message ?: e.javaClass.simpleName}"
                usbScanStatus.setTextColor(AMBER)
                deviceList.removeAllViews()
                deviceList.addView(text("Periksa koneksi OTG lalu coba lagi.", 12, MUTED))
                toast("USB scan gagal: ${e.message ?: "kesalahan tidak diketahui"}")
            } finally {
                usbScanInProgress = false
            }
        }
    }

    private fun renderUsbDevices(devices: List<UsbDevice> = usbManager.deviceList.values.toList()) {
        if (!::deviceList.isInitialized) return
        deviceList.removeAllViews()
        if (devices.isEmpty()) {
            deviceList.addView(text("Tidak ada perangkat USB yang terlihat oleh Android.", 12, MUTED))
            return
        }
        devices.forEach { device ->
            val vid = device.vendorId.toString(16).uppercase().padStart(4, '0')
            val pid = device.productId.toString(16).uppercase().padStart(4, '0')
            val permission = if (usbManager.hasPermission(device)) "IZIN DIBERIKAN" else "IZIN DIPERLUKAN"
            val label = "${device.deviceName}  ·  VID $vid / PID $pid  ·  $permission"
            deviceList.addView(action(label, false) { requestPermission(device) }, LinearLayout.LayoutParams(-1, dp(48)))
        }
    }

    private fun isSupportedSerialDevice(device: UsbDevice): Boolean {
        val vid = device.vendorId
        return vid == 0x0403 || // FTDI
            vid == 0x1A86 ||     // WCH CH340/CH341
            vid == 0x10C4 ||     // Silicon Labs CP210x
            vid == 0x067B ||     // Prolific PL2303
            vid == 0x2341 || vid == 0x2A03 // common Arduino CDC devices
    }

    private fun requestPermission(device: UsbDevice) {
        if (usbConnectInProgress) {
            toast("Proses koneksi USB sedang berjalan")
            return
        }
        if (usbManager.hasPermission(device)) {
            connectDevice(device)
        } else {
            usbScanStatus.text = "Perangkat ditemukan · menunggu izin USB dari Android…"
            usbScanStatus.setTextColor(ACCENT)
            val intent = Intent(permissionAction).setPackage(packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(this, device.deviceId, intent, flags)
            pendingUsbPermissionDeviceId = device.deviceId
            Log.i(USB_LOG_TAG, "Requesting USB permission for ${device.deviceName}, VID=0x${device.vendorId.toString(16)}, PID=0x${device.productId.toString(16)}")
            usbManager.requestPermission(device, pending)
            toast("Menunggu izin akses USB…")
        }
    }

    private fun connectDevice(device: UsbDevice) {
        if (usbConnectInProgress) return
        usbConnectInProgress = true
        connectingUsbDeviceId = device.deviceId
        connectionValue.text = "Membuka koneksi USB…"
        if (::usbScanStatus.isInitialized) {
            usbScanStatus.text = "Izin USB tersedia · membuka port serial adapter…"
            usbScanStatus.setTextColor(ACCENT)
        }
        usbConnectJob = lifecycleScope.launch {
            try {
                toast("Membuka port serial USB…")
                val ok = withContext(Dispatchers.IO) { engine.connect(device) }
                val stillAttached = usbManager.deviceList.values.any { it.deviceId == device.deviceId }
                if (!stillAttached) {
                    engine.disconnect()
                    activeUsbDeviceId = null
                    activeUsbVendorId = null
                    activeUsbProductId = null
                    if (::usbScanStatus.isInitialized) {
                        usbScanStatus.text = "DISCONNECTED · adapter dicabut saat proses koneksi."
                        usbScanStatus.setTextColor(AMBER)
                    }
                    return@launch
                }
                if (ok) {
                    activeUsbDeviceId = device.deviceId
                    activeUsbVendorId = device.vendorId
                    activeUsbProductId = device.productId
                    Log.i(USB_LOG_TAG, "USB serial port opened: ${device.deviceName}")
                    toast("Port USB terbuka. ECU belum tentu merespons.")
                    if (::usbScanStatus.isInitialized) {
                        usbScanStatus.text = "TERHUBUNG · port serial terbuka. ECU belum diverifikasi."
                        usbScanStatus.setTextColor(GREEN)
                    }
                } else {
                    activeUsbDeviceId = null
                    activeUsbVendorId = null
                    activeUsbProductId = null
                    Log.e(USB_LOG_TAG, "USB device found but serial port could not be opened: ${device.deviceName}")
                    toast("Adapter ditemukan, tetapi port serial gagal dibuka. Periksa driver/izin.")
                    if (::usbScanStatus.isInitialized) {
                        usbScanStatus.text = "GAGAL MEMBUKA PORT · perangkat USB terdeteksi, tetapi driver serial gagal."
                        usbScanStatus.setTextColor(AMBER)
                    }
                }
            } catch (e: Exception) {
                Log.e(USB_LOG_TAG, "USB connect exception", e)
                toast("Koneksi USB gagal: ${e.message ?: e.javaClass.simpleName}")
                if (::usbScanStatus.isInitialized) {
                    usbScanStatus.text = "KESALAHAN KONEKSI: ${e.message ?: e.javaClass.simpleName}"
                    usbScanStatus.setTextColor(AMBER)
                }
            } finally {
                usbConnectInProgress = false
                connectingUsbDeviceId = null
                if (selectedPage == PAGE_HOME) showPage(PAGE_HOME)
            }
        }
    }

    private fun disconnect() {
        liveJob?.cancel()
        usbConnectJob?.cancel()
        val key = if (activeUsbVendorId != null && activeUsbProductId != null)
            deviceKey(activeUsbVendorId!!, activeUsbProductId!!) else null
        manuallyDisconnectedKey = key
        manuallyDisconnectedDeviceId = activeUsbDeviceId
        engine.disconnect()
        activeUsbDeviceId = null
        activeUsbVendorId = null
        activeUsbProductId = null
        usbConnectInProgress = false
        connectingUsbDeviceId = null
        pendingUsbPermissionDeviceId = null
        connectionValue.text = "Adapter diputus secara manual"
        if (::usbScanStatus.isInitialized) {
            usbScanStatus.text = "DISCONNECTED · koneksi dihentikan manual. Tekan SCAN USB untuk menghubungkan kembali."
            usbScanStatus.setTextColor(MUTED)
        }
        Log.i(USB_LOG_TAG, "USB adapter disconnected manually; auto-reconnect suppressed until manual scan or detach/reattach")
        toast("Adapter diputus")
        if (selectedPage == PAGE_HOME) showPage(PAGE_HOME)
    }

    private fun onUsbAttached(device: UsbDevice) {
        Log.i(USB_LOG_TAG, "USB attached: ${device.deviceName}, VID=0x${device.vendorId.toString(16)}, PID=0x${device.productId.toString(16)}")
        if (!isSupportedSerialDevice(device)) {
            if (::usbScanStatus.isInitialized) {
                usbScanStatus.text = "Perangkat USB terpasang, tetapi bukan adapter serial yang didukung."
                usbScanStatus.setTextColor(AMBER)
            }
            if (selectedPage == PAGE_HOME) showPage(PAGE_HOME)
            return
        }
        val key = deviceKey(device.vendorId, device.productId)
        if (manuallyDisconnectedKey == key) {
            Log.i(USB_LOG_TAG, "Auto-connect skipped: this adapter was manually disconnected")
            return
        }
        if ((engine.isAdapterOpen.value && activeUsbDeviceId == device.deviceId) ||
            connectingUsbDeviceId == device.deviceId || pendingUsbPermissionDeviceId == device.deviceId) return
        if (::usbScanStatus.isInitialized) {
            usbScanStatus.text = "USB ADAPTER TERPASANG · menghubungkan otomatis…"
            usbScanStatus.setTextColor(ACCENT)
        }
        if (selectedPage == PAGE_HOME) showPage(PAGE_HOME)
        requestPermission(device)
    }

    private fun onUsbDetached(device: UsbDevice) {
        Log.i(USB_LOG_TAG, "USB detached: ${device.deviceName}, VID=0x${device.vendorId.toString(16)}, PID=0x${device.productId.toString(16)}")
        val matchesActive = activeUsbDeviceId == device.deviceId ||
            connectingUsbDeviceId == device.deviceId || pendingUsbPermissionDeviceId == device.deviceId ||
            manuallyDisconnectedDeviceId == device.deviceId
        if (matchesActive) {
            liveJob?.cancel()
            usbConnectJob?.cancel()
            engine.disconnect()
            activeUsbDeviceId = null
            activeUsbVendorId = null
            activeUsbProductId = null
            usbConnectInProgress = false
            connectingUsbDeviceId = null
            pendingUsbPermissionDeviceId = null
            manuallyDisconnectedKey = null // a physical detach permits auto-connect on the next attach
            manuallyDisconnectedDeviceId = null
            if (::usbScanStatus.isInitialized) {
                usbScanStatus.text = "DISCONNECTED · adapter USB telah dicabut."
                usbScanStatus.setTextColor(AMBER)
            }
            toast("Adapter USB dicabut · koneksi diputus")
            if (selectedPage == PAGE_HOME) showPage(PAGE_HOME)
        }
    }

    private fun autoConnectAvailableUsb() {
        val device = usbManager.deviceList.values.firstOrNull { isSupportedSerialDevice(it) }
        if (device != null) onUsbAttached(device) else reconcileUsbState()
    }

    private fun reconcileUsbState() {
        if (!::usbManager.isInitialized || !::engine.isInitialized) return
        val devices = usbManager.deviceList.values.toList()
        val currentStillPresent = activeUsbDeviceId?.let { id -> devices.any { it.deviceId == id } } ?: false
        if (engine.isAdapterOpen.value && !currentStillPresent) {
            Log.w(USB_LOG_TAG, "USB adapter no longer present during lifecycle reconciliation; disconnecting")
            engine.disconnect()
            activeUsbDeviceId = null
            activeUsbVendorId = null
            activeUsbProductId = null
            if (::usbScanStatus.isInitialized) {
                usbScanStatus.text = "DISCONNECTED · adapter tidak lagi terdeteksi oleh Android."
                usbScanStatus.setTextColor(AMBER)
            }
            if (selectedPage == PAGE_HOME) showPage(PAGE_HOME)
        }
        if (!engine.isAdapterOpen.value && !usbConnectInProgress && pendingUsbPermissionDeviceId == null) {
            val candidate = devices.firstOrNull { isSupportedSerialDevice(it) }
            if (candidate != null && manuallyDisconnectedKey != deviceKey(candidate.vendorId, candidate.productId)) {
                onUsbAttached(candidate)
            }
        }
    }

    private fun deviceKey(vendorId: Int, productId: Int): String = "$vendorId:$productId"

    private fun identifyEcu() {
        if (!engine.isAdapterOpen.value) { toast("Hubungkan adapter USB terlebih dahulu"); return }
        lifecycleScope.launch {
            val model = withContext(Dispatchers.IO) { engine.identifyEcu() }
            toast(if (model != null) "ECU berhasil diidentifikasi" else "ECU belum dapat diidentifikasi")
            showPage(if (selectedPage == PAGE_ECU) PAGE_ECU else PAGE_HOME)
        }
    }

    private fun readDtcs() {
        if (!engine.isAdapterOpen.value) { toast("Hubungkan adapter USB terlebih dahulu"); return }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { engine.readDtcs() }
            when (result) {
                is DtcReadResult.Success -> {
                    renderDtcs(result.dtcs, if (result.dtcs.isEmpty()) "ECU merespons: tidak ada DTC yang terbaca." else "${result.dtcs.size} kode DTC ditemukan.")
                    toast(if (result.dtcs.isEmpty()) "Tidak ada DTC" else "${result.dtcs.size} DTC ditemukan")
                }
                is DtcReadResult.NoResponse -> renderDtcs(emptyList(), "Tidak ada respons DTC: ${result.reason}")
                is DtcReadResult.Unrecognized -> renderDtcs(emptyList(), "Respons DTC tidak dikenali: ${result.reason}")
            }
        }
    }

    private fun confirmClearDtc() {
        if (!engine.isAdapterOpen.value) { toast("Hubungkan adapter USB terlebih dahulu"); return }
        AlertDialog.Builder(this)
            .setTitle("Clear DTC?")
            .setMessage("Perintah ini akan dikirim ke ECU jika prosedurnya didukung oleh profil ECU. Lanjutkan?")
            .setNegativeButton("BATAL", null)
            .setPositiveButton("CLEAR DTC") { _, _ -> clearDtcs() }
            .show()
    }

    private fun clearDtcs() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { engine.clearDtcs() }
            val message = result.messageId
            renderDtcs(result.remainingDtcs, message)
            toast(when (result.status) {
                ClearDtcStatus.SUCCESS_VERIFIED -> "Clear DTC terverifikasi"
                ClearDtcStatus.CLEARED_UNVERIFIED -> "Clear diterima, hasil belum terverifikasi"
                ClearDtcStatus.RE_DETECTED_ACTIVE_FAULT -> "DTC terdeteksi kembali"
                ClearDtcStatus.UNSUPPORTED_FAMILY -> "Clear DTC belum didukung profil ECU ini"
                ClearDtcStatus.NO_ADAPTER -> "Adapter belum terhubung"
                else -> "ECU tidak mengonfirmasi Clear DTC"
            })
        }
    }

    private fun startLiveMonitor() {
        if (!engine.isAdapterOpen.value) { toast("Hubungkan adapter USB terlebih dahulu"); return }
        liveJob?.cancel()
        liveJob = lifecycleScope.launch {
            while (isActive) {
                val telemetry = withContext(Dispatchers.IO) { engine.queryLiveData() }
                if (selectedPage == PAGE_LIVE) {
                    if (telemetry != null) renderLiveData(telemetry)
                    else renderLiveUnavailable()
                }
                delay(1000)
            }
        }
        toast("Live monitor dimulai")
    }

    private fun renderLiveUnavailable() {
        if (!::liveList.isInitialized) return
        liveList.removeAllViews()
        liveList.addView(text("No validated live-data profile or valid ECU response. Sensor values are intentionally not simulated.", 12, AMBER))
    }

    private fun renderLiveData(t: LiveTelemetry) {
        if (!::liveList.isInitialized) return
        liveList.removeAllViews()
        val rows = listOf(
            Triple("Engine RPM", LiveSensor.RPM, "rpm"),
            Triple("Throttle Position", LiveSensor.TPS_PERCENT, "%"),
            Triple("Engine Coolant Temp", LiveSensor.ECT, "°C"),
            Triple("Intake Air Temp", LiveSensor.IAT, "°C"),
            Triple("Manifold Pressure", LiveSensor.MAP, "kPa"),
            Triple("Battery Voltage", LiveSensor.BATTERY, "V"),
            Triple("Injector Pulse", LiveSensor.INJECTOR, "ms"),
            Triple("Ignition Advance", LiveSensor.IGNITION_ADVANCE, "°"),
            Triple("Vehicle Speed", LiveSensor.SPEED, "km/h"),
            Triple("Oxygen Sensor", LiveSensor.O2, "V")
        )
        rows.forEach { (name, sensor, unit) ->
            val valid = t.isValid(sensor)
            val value = if (!valid) "—" else when (sensor) {
                LiveSensor.RPM -> t.engineRpm.toString()
                LiveSensor.TPS_PERCENT -> "%.1f".format(t.tpsPercent)
                LiveSensor.ECT -> t.engineTempC.toString()
                LiveSensor.IAT -> t.intakeAirTempC.toString()
                LiveSensor.MAP -> t.mapKpa.toString()
                LiveSensor.BATTERY -> "%.2f".format(t.batteryVoltage)
                LiveSensor.INJECTOR -> "%.2f".format(t.injectorPulseMs)
                LiveSensor.IGNITION_ADVANCE -> "%.1f".format(t.ignitionAdvanceDeg)
                LiveSensor.SPEED -> t.vehicleSpeedKmh.toString()
                LiveSensor.O2 -> "%.3f".format(t.o2Voltage)
                else -> "—"
            }
            row(liveList).apply {
                addView(text(name, 12, SOFT), LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(if (valid) "$value $unit" else "${t.stateOf(sensor)}", 12, if (valid) WHITE else MUTED, true))
            }
            if (name != "Oxygen Sensor") line(liveList)
        }
        if (!t.isDataValid) liveList.addView(text("Belum ada parameter valid dari ECU.", 11, AMBER), marginTop(8))
    }

    private fun renderDtcs(codes: List<com.revcore.android.core.protocol.HondaDtc>, message: String) {
        if (!::dtcList.isInitialized) return
        dtcList.removeAllViews()
        dtcList.addView(text(message, 12, SOFT))
        codes.forEach { dtc ->
            line(dtcList)
            dtcList.addView(text("${dtc.standardCode}  ·  BLINK ${dtc.blinkCode}", 14, if (dtc.isKnown) WHITE else AMBER, true), marginTop(8))
            dtcList.addView(text(dtc.titleId, 12, SOFT), marginTop(4))
            dtcList.addView(text(dtc.symptomId, 11, MUTED), marginTop(3))
        }
    }

    private fun heading(title: String, subtitle: String) {
        content.addView(text(title, 23, WHITE, true))
        content.addView(text(subtitle, 12, MUTED), marginTop(4))
        content.addView(View(this), LinearLayout.LayoutParams(1, dp(12)))
    }

    private fun headingSmall(value: String) {
        content.addView(text(value, 10, MUTED, true).apply { letterSpacing = .12f }, marginTop(16))
        content.addView(View(this), LinearLayout.LayoutParams(1, dp(7)))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(SURFACE_CARD, 14, STROKE)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
    }

    private fun toolRow(parent: LinearLayout, title: String, subtitle: String, trailing: String, click: () -> Unit) {
        val r = row(parent).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(12)); setOnClickListener { click() } }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(title, 14, WHITE, true))
        labels.addView(text(subtitle, 11, MUTED), marginTop(3))
        r.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(text(trailing, 22, MUTED, true))
    }

    private fun note(value: String) {
        val n = card()
        n.addView(text(value, 11, AMBER))
        content.addView(n, marginTop(12))
    }

    private fun action(label: String, primary: Boolean, enabled: Boolean = true, click: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 10f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) BG else if (enabled) SOFT else MUTED)
        background = rounded(if (!enabled) 0xFF202630.toInt() else if (primary) ACCENT else 0xFF293442.toInt(), 9)
        isEnabled = enabled
        alpha = if (enabled) 1f else .65f
        setOnClickListener { click() }
        minHeight = dp(42)
        minimumHeight = dp(42)
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun row(parent: LinearLayout): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun line(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(STROKE) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(8) })
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = true
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun marginTop(value: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun connectionLabel(): String = if (engine.isAdapterOpen.value) "ADAPTER OPEN" else "DISCONNECTED"
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()

    companion object {
        private const val USB_LOG_TAG = "ZEA_USB"
        private const val PAGE_HOME = 0
        private const val PAGE_LIVE = 1
        private const val PAGE_DIAG = 2
        private const val PAGE_ECU = 3
        private const val PAGE_SETTINGS = 4
        private const val BG = 0xFF10151C.toInt()
        private const val SURFACE = 0xFF171E27.toInt()
        private const val SURFACE_CARD = 0xFF1D2631.toInt()
        private const val STROKE = 0xFF303B48.toInt()
        private const val WHITE = 0xFFF2F5F8.toInt()
        private const val SOFT = 0xFFD1D8E0.toInt()
        private const val MUTED = 0xFF8793A1.toInt()
        private const val ACCENT = 0xFF63C7B2.toInt()
        private const val GREEN = 0xFF63C7B2.toInt()
        private const val AMBER = 0xFFE7B66A.toInt()
    }
}
