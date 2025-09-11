package com.vantagescanner

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.util.Locale

/**
 * Detects drone Wi-Fi signals by comparing MAC prefixes against a
 * locally maintained list of Organizationally Unique Identifiers.
 *
 * OUI prefixes are loaded from the `drone_ouis.json` asset at runtime
 * to avoid hard-coding vendor data.
 */
class DroneSignalDetector(private val context: Context) {
    private val droneOuis: Set<String> by lazy { loadOuis() }

    private fun loadOuis(): Set<String> {
        context.assets.open("drone_ouis.json").use { input ->
            val text = input.bufferedReader().use(BufferedReader::readText)
            val arr = JSONArray(text)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set += arr.getString(i).uppercase(Locale.US)
            }
            return set
        }
    }

    /**
     * Returns true if the MAC address belongs to a known drone vendor.
     */
    fun isDrone(macAddress: String): Boolean {
        val prefix = macAddress.uppercase(Locale.US).replace(":", "").take(6)
        return prefix in droneOuis
    }
}
