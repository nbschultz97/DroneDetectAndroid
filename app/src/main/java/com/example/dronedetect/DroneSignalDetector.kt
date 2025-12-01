package com.example.dronedetect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class DroneSignalDetector(
    private val context: Context,
    private val onResults: (List<ScanResult>, FlightSnapshot) -> Unit = { _, _ -> }
) {
    private val handler = Handler(Looper.getMainLooper())
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val receiver = WifiScanReceiver(wifiManager, ::handleScanResults)
    private var scanScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isScanning = false
    private var latestFlightData: FlightSnapshot = FlightSnapshot(timestamp = Instant.now(), source = "bootstrap", payload = emptyMap())

    fun startScan() {
        if (isScanning) return
        isScanning = true
        context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        scanScope.launch { fetchFlightData() }
        requestScan()
    }

    suspend fun fetchFlightData() = withContext(Dispatchers.IO) {
        // Placeholder for real RF/telemetry ingestion.
        // Here we stamp a deterministic payload so downstream processing can
        // align Wi-Fi RSSI snapshots with the last known telemetry sample.
        latestFlightData = FlightSnapshot(
            timestamp = Instant.now(),
            source = "local_stub",
            payload = mapOf(
                "note" to "Replace with CSI/telemetry feed",
                "status" to "idle"
            )
        )
        Log.d(TAG, "Flight data refreshed at ${latestFlightData.timestamp}")
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        handler.removeCallbacksAndMessages(null)
        runCatching { context.unregisterReceiver(receiver) }
        scanScope.cancel()
        scanScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private fun requestScan() {
        val started = wifiManager.startScan()
        if (!started) {
            Log.w(TAG, "Wi-Fi scan request rejected by platform")
        }
        handler.postDelayed({
            if (isScanning) {
                requestScan()
            }
        }, SCAN_INTERVAL_MS)
    }

    private fun handleScanResults(results: List<ScanResult>) {
        if (!isScanning) return
        onResults(results, latestFlightData)
    }

    companion object {
        private const val TAG = "DroneSignalDetector"
        private const val SCAN_INTERVAL_MS = 10_000L
    }
}

data class FlightSnapshot(
    val timestamp: Instant,
    val source: String,
    val payload: Map<String, String>
)

private class WifiScanReceiver(
    private val wifiManager: WifiManager,
    private val onResults: (List<ScanResult>) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
        val results = wifiManager.scanResults.orEmpty()
        onResults(results)
    }
}
