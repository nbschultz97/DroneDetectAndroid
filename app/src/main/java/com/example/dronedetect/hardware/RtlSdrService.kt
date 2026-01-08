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
    private var rtlSdrScanner: RtlSdrScanner? = null

    // Callbacks for detection events (forward from scanner)
    var onDroneDetected: ((RtlSdrScanner.DroneDetection) -> Unit)? = null
    var onSpectrumUpdate: ((RtlSdrScanner.SpectrumData) -> Unit)? = null

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
    fun startScanning(host: String = "127.0.0.1", port: Int = 1234) {
        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }

        isScanning = true
        updateNotification("Connecting to RTL-SDR...")

        serviceScope.launch {
            try {
                // Create scanner if not exists
                if (rtlSdrScanner == null) {
                    rtlSdrScanner = RtlSdrScanner().apply {
                        // Forward callbacks
                        onDroneDetected = { detection ->
                            this@RtlSdrService.onDroneDetected?.invoke(detection)
                            updateNotification("⚠️ ${detection.classification} detected!")
                        }
                        onSpectrumUpdate = { spectrum ->
                            this@RtlSdrService.onSpectrumUpdate?.invoke(spectrum)
                        }
                        onError = { error ->
                            Log.e(TAG, "Scanner error: $error")
                            updateNotification("Error: $error")
                        }
                    }
                }

                // Connect to rtl_tcp_andro
                val connected = rtlSdrScanner?.connect(host, port) ?: false

                if (connected) {
                    updateNotification("Scanning 433 MHz / 915 MHz bands...")
                    rtlSdrScanner?.startScanning()
                } else {
                    updateNotification("Failed to connect to RTL-SDR")
                    isScanning = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during RTL-SDR scanning", e)
                updateNotification("Scanning error: ${e.message}")
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
        rtlSdrScanner?.stopScanning()
        updateNotification("RF monitoring paused")
        Log.d(TAG, "RTL-SDR scanning stopped")
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
        rtlSdrScanner = null
        serviceScope.cancel()
    }
}
