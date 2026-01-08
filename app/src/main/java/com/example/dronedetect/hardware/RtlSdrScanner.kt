package com.example.dronedetect.hardware

import android.util.Log
import kotlinx.coroutines.*
import org.jtransforms.fft.DoubleFFT_1D
import java.io.DataInputStream
import java.net.Socket
import kotlin.math.*

/**
 * RTL-SDR RF Scanner for detecting drone controller signals
 *
 * Connects to rtl_tcp_andro or native RTL-SDR library to receive IQ samples,
 * performs FFT analysis, and detects frequency hopping patterns characteristic
 * of drone RC controllers.
 */
class RtlSdrScanner {

    private var socket: Socket? = null
    private var isScanning = false
    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callbacks
    var onDroneDetected: ((DroneDetection) -> Unit)? = null
    var onSpectrumUpdate: ((SpectrumData) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "RtlSdrScanner"

        // RTL-SDR configuration
        private const val DEFAULT_CENTER_FREQ = 433.92e6  // 433.92 MHz (ISM band)
        private const val DEFAULT_SAMPLE_RATE = 2048000   // 2.048 MHz
        private const val FFT_SIZE = 1024
        private const val HOP_SIZE = 256  // 75% overlap for STFT

        // Detection thresholds
        private const val POWER_THRESHOLD_DBM = -80.0
        private const val MIN_HOPS_FOR_DETECTION = 10
        private const val HOP_RATE_MIN = 40.0  // Hz
        private const val HOP_RATE_MAX = 100.0 // Hz
    }

    /**
     * Connect to rtl_tcp_andro server
     *
     * @param host Server host (usually "127.0.0.1" for local rtl_tcp_andro)
     * @param port Server port (set when launching rtl_tcp_andro)
     * @param centerFreq Center frequency in Hz
     * @param sampleRate Sample rate in Hz
     */
    suspend fun connect(
        host: String = "127.0.0.1",
        port: Int = 1234,
        centerFreq: Double = DEFAULT_CENTER_FREQ,
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to rtl_tcp at $host:$port")
            socket = Socket(host, port)

            // Configure RTL-SDR via TCP commands
            sendCommand(RtlCommand.SET_FREQUENCY, centerFreq.toLong())
            sendCommand(RtlCommand.SET_SAMPLE_RATE, sampleRate.toLong())
            sendCommand(RtlCommand.SET_GAIN_MODE, 0) // Auto gain
            sendCommand(RtlCommand.SET_AGC_MODE, 1)  // Enable AGC

            Log.d(TAG, "Connected to RTL-SDR: ${centerFreq / 1e6} MHz @ ${sampleRate / 1e6} Msps")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to rtl_tcp", e)
            onError?.invoke("RTL-SDR connection failed: ${e.message}")
            false
        }
    }

    /**
     * Start scanning for drone signals
     */
    fun startScanning() {
        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }

        isScanning = true
        scanScope.launch {
            processIqStream()
        }
    }

    /**
     * Stop scanning
     */
    fun stopScanning() {
        isScanning = false
        scanScope.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing socket", e)
        }
        socket = null
    }

    /**
     * Process IQ sample stream from RTL-SDR
     */
    private suspend fun processIqStream() = withContext(Dispatchers.IO) {
        val socket = socket ?: run {
            onError?.invoke("Socket not connected")
            return@withContext
        }

        try {
            val inputStream = DataInputStream(socket.getInputStream())
            val buffer = ByteArray(FFT_SIZE * 2) // I and Q samples (8-bit each)
            val fftInput = DoubleArray(FFT_SIZE * 2)
            val fft = DoubleFFT_1D(FFT_SIZE.toLong())

            val frequencyHops = mutableListOf<FrequencyHop>()

            while (isScanning && !socket.isClosed) {
                // Read IQ samples
                val bytesRead = inputStream.read(buffer)
                if (bytesRead != buffer.size) {
                    Log.w(TAG, "Incomplete read: $bytesRead bytes")
                    continue
                }

                // Convert unsigned bytes to IQ samples (center around 0)
                for (i in 0 until FFT_SIZE) {
                    fftInput[i * 2] = (buffer[i * 2].toInt() and 0xFF - 127.5) / 128.0       // I
                    fftInput[i * 2 + 1] = (buffer[i * 2 + 1].toInt() and 0xFF - 127.5) / 128.0 // Q
                }

                // Apply Hann window
                applyHannWindow(fftInput, FFT_SIZE)

                // Compute FFT
                fft.complexForward(fftInput)

                // Calculate power spectrum
                val powerSpectrum = calculatePowerSpectrum(fftInput, FFT_SIZE)

                // Emit spectrum update
                onSpectrumUpdate?.invoke(
                    SpectrumData(
                        centerFrequency = DEFAULT_CENTER_FREQ,
                        sampleRate = DEFAULT_SAMPLE_RATE,
                        timestamp = System.currentTimeMillis(),
                        powerSpectrum = powerSpectrum
                    )
                )

                // Detect peaks (potential frequency hops)
                val peaks = detectPeaks(powerSpectrum, POWER_THRESHOLD_DBM)

                for (binIndex in peaks) {
                    val frequency = DEFAULT_CENTER_FREQ +
                                  (binIndex - FFT_SIZE / 2) * DEFAULT_SAMPLE_RATE / FFT_SIZE
                    val power = powerSpectrum[binIndex]

                    frequencyHops.add(
                        FrequencyHop(
                            frequency = frequency,
                            timestamp = System.currentTimeMillis(),
                            power = power
                        )
                    )
                }

                // Analyze hopping pattern every 100 hops
                if (frequencyHops.size >= 100) {
                    analyzeHoppingPattern(frequencyHops)
                    frequencyHops.clear()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing IQ stream", e)
            onError?.invoke("Stream processing error: ${e.message}")
        }
    }

    /**
     * Analyze frequency hopping pattern to detect drones
     */
    private fun analyzeHoppingPattern(hops: List<FrequencyHop>) {
        if (hops.size < MIN_HOPS_FOR_DETECTION) {
            return
        }

        // Calculate hop rate
        val timeDiff = hops.last().timestamp - hops.first().timestamp
        if (timeDiff == 0L) return

        val hopRate = (hops.size * 1000.0) / timeDiff.toDouble()

        // Calculate bandwidth usage
        val minFreq = hops.minOf { it.frequency }
        val maxFreq = hops.maxOf { it.frequency }
        val bandwidth = maxFreq - minFreq

        // Count unique frequencies (rounded to MHz)
        val uniqueFrequencies = hops.map { (it.frequency / 1e6).toInt() }.toSet()

        // Check for FHSS pattern
        val isFhss = uniqueFrequencies.size >= 3 && hopRate in HOP_RATE_MIN..HOP_RATE_MAX

        if (isFhss) {
            val avgPower = hops.map { it.power }.average().toFloat()

            val detection = DroneDetection(
                frequency = (minFreq + maxFreq) / 2,
                signalStrength = avgPower,
                classification = classifyDrone(hopRate, bandwidth),
                confidence = calculateConfidence(hopRate, bandwidth, uniqueFrequencies.size),
                timestamp = System.currentTimeMillis(),
                hopRate = hopRate,
                bandwidth = bandwidth
            )

            Log.d(TAG, "Drone detected: ${detection.classification} @ ${detection.frequency / 1e6} MHz")
            onDroneDetected?.invoke(detection)
        }
    }

    /**
     * Classify drone type based on hopping characteristics
     */
    private fun classifyDrone(hopRate: Double, bandwidth: Double): String {
        return when {
            hopRate in 40.0..60.0 && bandwidth in 20e6..40e6 -> "Futaba FHSS (433 MHz RC)"
            hopRate in 80.0..100.0 && bandwidth in 60e6..80e6 -> "FrSky FHSS (915 MHz RC)"
            bandwidth < 5e6 -> "Fixed frequency (GPS/Telemetry)"
            else -> "Unknown FHSS"
        }
    }

    /**
     * Calculate detection confidence (0.0 to 1.0)
     */
    private fun calculateConfidence(hopRate: Double, bandwidth: Double, uniqueFreqs: Int): Float {
        var confidence = 0.0f

        // Hop rate confidence
        confidence += when {
            hopRate in 50.0..90.0 -> 0.4f
            hopRate in 40.0..100.0 -> 0.3f
            else -> 0.1f
        }

        // Bandwidth confidence
        confidence += when {
            bandwidth in 20e6..80e6 -> 0.3f
            bandwidth in 10e6..100e6 -> 0.2f
            else -> 0.1f
        }

        // Frequency diversity confidence
        confidence += when {
            uniqueFreqs >= 5 -> 0.3f
            uniqueFreqs >= 3 -> 0.2f
            else -> 0.1f
        }

        return confidence.coerceIn(0f, 1f)
    }

    /**
     * Apply Hann window to reduce spectral leakage
     */
    private fun applyHannWindow(data: DoubleArray, fftSize: Int) {
        for (i in 0 until fftSize) {
            val window = 0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
            data[i * 2] *= window      // I component
            data[i * 2 + 1] *= window  // Q component
        }
    }

    /**
     * Calculate power spectrum in dBm
     */
    private fun calculatePowerSpectrum(fftData: DoubleArray, fftSize: Int): FloatArray {
        val power = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            val real = fftData[i * 2]
            val imag = fftData[i * 2 + 1]
            power[i] = (10.0 * log10(real * real + imag * imag + 1e-10)).toFloat()
        }
        return power
    }

    /**
     * Detect peaks in power spectrum above threshold
     */
    private fun detectPeaks(spectrum: FloatArray, threshold: Double): List<Int> {
        val peaks = mutableListOf<Int>()
        for (i in 1 until spectrum.size - 1) {
            if (spectrum[i] > threshold &&
                spectrum[i] > spectrum[i - 1] &&
                spectrum[i] > spectrum[i + 1]) {
                peaks.add(i)
            }
        }
        return peaks
    }

    /**
     * Send command to RTL-SDR via TCP
     */
    private fun sendCommand(command: RtlCommand, value: Long) {
        try {
            val socket = socket ?: return
            val output = socket.getOutputStream()

            // rtl_tcp command format: [command:1byte][param:4bytes]
            output.write(command.value)
            output.write((value shr 24).toInt() and 0xFF)
            output.write((value shr 16).toInt() and 0xFF)
            output.write((value shr 8).toInt() and 0xFF)
            output.write(value.toInt() and 0xFF)
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command", e)
        }
    }

    /**
     * RTL-TCP command codes
     */
    private enum class RtlCommand(val value: Int) {
        SET_FREQUENCY(0x01),
        SET_SAMPLE_RATE(0x02),
        SET_GAIN_MODE(0x03),
        SET_GAIN(0x04),
        SET_AGC_MODE(0x08)
    }

    /**
     * Frequency hop data
     */
    data class FrequencyHop(
        val frequency: Double,
        val timestamp: Long,
        val power: Double
    )

    /**
     * Spectrum data for visualization
     */
    data class SpectrumData(
        val centerFrequency: Double,
        val sampleRate: Int,
        val timestamp: Long,
        val powerSpectrum: FloatArray
    )

    /**
     * Drone detection event
     */
    data class DroneDetection(
        val frequency: Double,
        val signalStrength: Float,
        val classification: String,
        val confidence: Float,
        val timestamp: Long,
        val hopRate: Double = 0.0,
        val bandwidth: Double = 0.0
    )
}
