package com.example.dronedetect

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var detector: DroneSignalDetector
    private lateinit var statusView: TextView
    private lateinit var scanSummaryView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.statusView)
        scanSummaryView = findViewById(R.id.scanSummary)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        detector = DroneSignalDetector(this, ::renderScanResults)

        startButton.setOnClickListener { ensurePermissionsAndStart() }
        stopButton.setOnClickListener {
            detector.stopScan()
            statusView.text = "Scan stopped"
            scanSummaryView.isVisible = false
        }
    }

    override fun onResume() {
        super.onResume()
        ensurePermissionsAndStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.stopScan()
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
