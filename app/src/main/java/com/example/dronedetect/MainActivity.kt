package com.example.dronedetect

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.example.dronedetect.hardware.RtlSdrService
import com.example.dronedetect.hardware.UsbDeviceManager
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var detector: DroneSignalDetector
    private lateinit var statusView: TextView
    private lateinit var scanSummaryView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    // RTL-SDR hardware support
    private var usbDeviceManager: UsbDeviceManager? = null
    private var rtlSdrService: RtlSdrService? = null
    private var isServiceBound = false

    companion object {
        private const val TAG = "MainActivity"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val granted = grantResults.values.any { it }
        statusView.text = if (granted) {
            "Permissions granted. Starting scan…"
        } else {
            "Location/Wi‑Fi permissions are required."
        }
        if (granted) {
            startScanning()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RtlSdrService.LocalBinder
            rtlSdrService = binder.getService()
            isServiceBound = true
            Log.d(TAG, "RTL-SDR service connected")

            // Set up callbacks
            rtlSdrService?.onDroneDetected = { detection ->
                runOnUiThread {
                    statusView.text = "⚠️ Drone detected: ${detection.frequency / 1e6} MHz"
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            rtlSdrService = null
            isServiceBound = false
            Log.d(TAG, "RTL-SDR service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.statusView)
        scanSummaryView = findViewById(R.id.scanSummary)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        detector = DroneSignalDetector(this, ::renderScanResults)
        usbDeviceManager = UsbDeviceManager(this)

        startButton.setOnClickListener { ensurePermissionsAndStart() }
        stopButton.setOnClickListener {
            detector.stopScan()
            rtlSdrService?.stopScanning()
            statusView.text = "Scan stopped"
            scanSummaryView.isVisible = false
        }

        // Check for USB devices on startup
        checkForRtlSdrDevices()

        // Handle USB device attached intent
        handleUsbIntent(intent)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensurePermissionsAndStart()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.stopScan()
        rtlSdrService?.stopScanning()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        usbDeviceManager?.cleanup()
    }

    private fun checkForRtlSdrDevices() {
        val devices = usbDeviceManager?.detectRtlSdrDevices() ?: emptyList()
        if (devices.isNotEmpty()) {
            Log.d(TAG, "Found ${devices.size} RTL-SDR device(s)")
            val device = devices.first()

            if (usbDeviceManager?.hasPermission(device) == true) {
                startRtlSdrService()
            } else {
                usbDeviceManager?.requestPermission(device) { granted, _ ->
                    if (granted) {
                        startRtlSdrService()
                    } else {
                        Log.w(TAG, "USB permission denied")
                    }
                }
            }
        } else {
            Log.d(TAG, "No RTL-SDR devices detected (WiFi scanning only)")
        }
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            Log.d(TAG, "USB device attached")
            checkForRtlSdrDevices()
        }
    }

    private fun startRtlSdrService() {
        Log.d(TAG, "Starting RTL-SDR service")
        val intent = Intent(this, RtlSdrService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun ensurePermissionsAndStart() {
        if (hasWifiPermissions()) {
            startScanning()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun startScanning() {
        detector.startScan()
        statusView.text = "Scanning for rotor signatures…"
        scanSummaryView.isVisible = false
    }

    private fun renderScanResults(results: List<ScanResult>, snapshot: FlightSnapshot) {
        if (results.isEmpty()) {
            statusView.text = "No Wi‑Fi beacons detected yet"
            scanSummaryView.isVisible = false
            return
        }

        val strongest = results.sortedBy { it.level }.takeLast(3).reversed()
        val summary = strongest.joinToString(separator = "\n") { result ->
            val channel = result.frequency
            val rssi = result.level
            "${result.SSID.ifEmpty { "<hidden>" }} (${result.BSSID}) - RSSI ${rssi}dBm @${channel}MHz"
        }

        val timestamp = snapshot.timestamp.atZone(java.time.ZoneId.systemDefault())
        val header = "Telemetry ${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(timestamp)} [${snapshot.source}]"

        runOnUiThread {
            statusView.text = "Active scan: ${results.size} APs"
            scanSummaryView.isVisible = true
            scanSummaryView.text = "$header\n$summary"
        }
    }

    private fun hasWifiPermissions(): Boolean = requiredPermissions().all { perm ->
        ActivityCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }
}
