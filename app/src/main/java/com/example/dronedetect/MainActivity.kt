package com.example.dronedetect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var detector: DroneSignalDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detector = DroneSignalDetector(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.stopScan()
    }
}
