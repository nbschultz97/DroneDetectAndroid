package com.example.dronedetect.hardware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.dronedetect.MainActivity
import com.example.dronedetect.R
import kotlinx.coroutines.*

/**
 * Foreground service for continuous RTL-SDR monitoring
 * Handles RF spectrum scanning and drone signature detection
 */
class RtlSdrService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isScanning = false

    // Callbacks for detection events
    var onDroneDetected: ((DroneDetection) -> Unit)? = null
    var onSpectrumUpdate: ((SpectrumData) -> Unit)? = null

    companion object {
        private const val TAG = "RtlSdrService"
        private const val CHANNEL_ID = "rtl_sdr_service"
        private const val NOTIFICATION_ID = 1001
    }

    inner class LocalBinder : Binder() {
        fun getService(): RtlSdrService = this@RtlSdrService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RTL-SDR Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RTL-SDR Service started")

        // Start foreground service
        val notification = createNotification("Initializing RF monitoring...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**
     * Start RTL-SDR scanning
     */
    fun startScanning() {
        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }

        isScanning = true
        updateNotification("Scanning RF spectrum...")

        serviceScope.launch {
            try {
                // TODO: Integrate with rtl_tcp_andro or native RTL-SDR library
                // For now, simulate scanning
                simulateRtlSdrScanning()
            } catch (e: Exception) {
                Log.e(TAG, "Error during RTL-SDR scanning", e)
                isScanning = false
            }
        }
    }

    /**
     * Stop RTL-SDR scanning
     */
    fun stopScanning() {
        if (!isScanning) {
            return
        }

        isScanning = false
        updateNotification("RF monitoring paused")
        Log.d(TAG, "RTL-SDR scanning stopped")
    }

    /**
     * Simulate RTL-SDR scanning (placeholder for actual implementation)
     *
     * In production, this would:
     * 1. Connect to rtl_tcp_andro via TCP socket (localhost:1234)
     * 2. Receive IQ samples
     * 3. Perform FFT analysis
     * 4. Detect frequency hopping patterns (FHSS drone controllers)
     */
    private suspend fun simulateRtlSdrScanning() {
        Log.d(TAG, "Starting simulated RTL-SDR scanning")

        while (isScanning) {
            delay(5000) // Scan every 5 seconds

            // Simulate spectrum data
            val spectrumData = SpectrumData(
                centerFrequency = 433.0e6, // 433 MHz
                sampleRate = 2048000,      // 2.048 MHz
                timestamp = System.currentTimeMillis(),
                powerSpectrum = FloatArray(1024) { -80f } // Dummy data
            )

            onSpectrumUpdate?.invoke(spectrumData)

            // Simulate drone detection (10% chance)
            if (Math.random() < 0.1) {
                val detection = DroneDetection(
                    frequency = 433.92e6,
                    signalStrength = -65.0f,
                    classification = "Unknown FHSS",
                    confidence = 0.7f,
                    timestamp = System.currentTimeMillis()
                )

                Log.d(TAG, "Simulated drone detection: ${detection.frequency / 1e6} MHz")
                onDroneDetected?.invoke(detection)
                updateNotification("⚠️ Drone detected at ${detection.frequency / 1e6} MHz")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RTL-SDR Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous RF spectrum monitoring for drone detection"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drone Detection Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.drone_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RTL-SDR Service destroyed")
        stopScanning()
        serviceScope.cancel()
    }

    /**
     * Data class representing RF spectrum data
     */
    data class SpectrumData(
        val centerFrequency: Double,
        val sampleRate: Int,
        val timestamp: Long,
        val powerSpectrum: FloatArray
    )

    /**
     * Data class representing a drone detection event
     */
    data class DroneDetection(
        val frequency: Double,
        val signalStrength: Float,
        val classification: String,
        val confidence: Float,
        val timestamp: Long
    )
}
