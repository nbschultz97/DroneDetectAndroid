package com.example.dronedetect.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Manages USB device detection and permission handling for RTL-SDR dongles
 */
class UsbDeviceManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var permissionCallback: ((Boolean, UsbDevice?) -> Unit)? = null
    private var receiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "UsbDeviceManager"
        private const val ACTION_USB_PERMISSION = "com.example.dronedetect.USB_PERMISSION"

        // RTL-SDR device vendor/product IDs
        private const val RTL_VENDOR_ID = 0x0bda  // Realtek
        private const val RTL_PRODUCT_ID_2838 = 0x2838  // RTL2832U
        private const val RTL_PRODUCT_ID_2832 = 0x2832  // RTL-SDR Blog V3/V4
    }

    /**
     * Detect all connected RTL-SDR devices
     */
    fun detectRtlSdrDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.filter { device ->
            isRtlSdrDevice(device)
        }.also { devices ->
            Log.d(TAG, "Found ${devices.size} RTL-SDR device(s)")
            devices.forEach { device ->
                Log.d(TAG, "Device: ${device.deviceName}, Vendor: ${device.vendorId}, Product: ${device.productId}")
            }
        }
    }

    /**
     * Check if device is an RTL-SDR dongle
     */
    private fun isRtlSdrDevice(device: UsbDevice): Boolean {
        return device.vendorId == RTL_VENDOR_ID &&
               (device.productId == RTL_PRODUCT_ID_2838 || device.productId == RTL_PRODUCT_ID_2832)
    }

    /**
     * Check if we already have permission for a device
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * Request permission to access a USB device
     *
     * @param device The USB device to request permission for
     * @param callback Called when permission is granted or denied (granted: Boolean, device: UsbDevice?)
     */
    fun requestPermission(device: UsbDevice, callback: (Boolean, UsbDevice?) -> Unit) {
        this.permissionCallback = callback

        // Create permission intent
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Register broadcast receiver for permission response
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    synchronized(this) {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                        Log.d(TAG, "USB permission ${if (granted) "granted" else "denied"} for ${device?.deviceName}")

                        permissionCallback?.invoke(granted, device)

                        // Unregister receiver
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error unregistering receiver: ${e.message}")
                        }
                    }
                }
            }
        }

        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Request permission
        usbManager.requestPermission(device, permissionIntent)
        Log.d(TAG, "Requesting USB permission for ${device.deviceName}")
    }

    /**
     * Get device information as a readable string
     */
    fun getDeviceInfo(device: UsbDevice): String {
        return buildString {
            append("Device: ${device.deviceName}\n")
            append("Vendor ID: 0x${device.vendorId.toString(16)}\n")
            append("Product ID: 0x${device.productId.toString(16)}\n")
            append("Device Class: ${device.deviceClass}\n")
            append("Interface Count: ${device.interfaceCount}\n")
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup: ${e.message}")
        }
        permissionCallback = null
        receiver = null
    }
}
