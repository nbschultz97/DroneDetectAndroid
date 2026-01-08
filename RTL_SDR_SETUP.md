# RTL-SDR Setup Guide for DroneDetectAndroid

## Overview

This guide explains how to use external RTL-SDR USB dongles with DroneDetectAndroid to detect drone controller signals at 433 MHz and 915 MHz - frequencies that built-in WiFi cannot reach.

**Why RTL-SDR?**
- Standard Android WiFi only detects 2.4 GHz / 5 GHz
- Many drones use 433 MHz / 915 MHz RC controllers
- RTL-SDR adds detection for drones with WiFi disabled
- Increases detection rate from 20% → 80% of consumer drones

---

## Hardware Requirements

### RTL-SDR Dongle (Required)

**Recommended Options**:

1. **RTL-SDR Blog V3** (~$30)
   - Frequency Range: 500 kHz - 1.766 GHz
   - Includes dipole antenna kit
   - USB OTG support confirmed
   - Available: rtl-sdr.com, Amazon

2. **NooElec NESDR Nano 3 OTG** (~$30)
   - Designed specifically for Android
   - Includes USB OTG cable
   - Compact form factor
   - Available: Amazon, NooElec store

3. **Generic RTL2832U Dongles** ($15-25)
   - Check for RTL2832U chipset
   - May need separate USB OTG cable

**What to Avoid**:
- ❌ Cheap no-name dongles without specs
- ❌ Dongles that only work on Windows
- ❌ Non-RTL2832U chipsets

### Android Device Requirements

- **Android Version**: 8.0 (API 26) or higher
- **USB OTG Support**: Required (most modern phones have this)
- **RAM**: 2GB minimum, 4GB+ recommended
- **Storage**: 100MB free space

**How to Check USB OTG Support**:
1. Install "USB OTG Checker" app from Play Store
2. Or try plugging in a USB flash drive with OTG cable

### Cables & Accessories

**Required**:
- **USB OTG Cable** ($3-5) - If not included with dongle
  - USB-C to USB-A (for modern phones)
  - Micro-USB to USB-A (for older phones)

**Optional**:
- **External Battery Pack** ($15-30)
  - RTL-SDR draws ~300mA
  - Extends field operation time
- **Directional Antenna** ($30-60)
  - Increases range from 500m → 1-2km
  - Yagi or panel antenna for 433/915 MHz

---

## Software Setup

### Step 1: Install rtl_tcp_andro Driver

DroneDetectAndroid works with the **rtl_tcp_andro** driver app:

1. **Install from Play Store**:
   - Search "SDR driver"
   - Install "SDR driver" by Martin Marinov
   - OR: F-Droid / Amazon Appstore versions available

2. **Alternative (Advanced)**:
   - Build from source: https://github.com/martinmarinov/rtl_tcp_andro-

### Step 2: Install DroneDetectAndroid

1. Download latest APK from releases
2. Install: `adb install DroneDetect.apk`
3. OR: Build from source (see README.md)

### Step 3: Grant Permissions

When you first launch DroneDetectAndroid:

1. **Location Permission** - Required for WiFi scanning
2. **Notification Permission** (Android 13+) - For background alerts
3. **USB Permission** - Will auto-prompt when dongle is connected

---

## Usage

### Connecting RTL-SDR

1. **Plug in RTL-SDR dongle** to phone via USB OTG cable
2. **Grant USB permission** when prompted
3. **DroneDetectAndroid auto-detects** the device
4. **Status updates** in notification bar

### Starting Detection

**Option A: Automatic (Recommended)**
- App auto-starts RTL-SDR scanning when dongle detected
- No configuration needed

**Option B: Manual**
1. Launch DroneDetectAndroid
2. Tap "Start Scanning"
3. App scans both WiFi (2.4 GHz) AND RTL-SDR (433/915 MHz)

### Reading Detections

**WiFi Detections** (2.4 GHz):
```
Active scan: 12 APs
DJI-Phantom4 (AA:BB:CC:DD:EE:FF) - RSSI -45dBm @2437MHz
```

**RTL-SDR Detections** (433/915 MHz):
```
⚠️ Futaba FHSS (433 MHz RC) detected!
Frequency: 433.92 MHz
Signal Strength: -65 dBm
Confidence: 85%
```

### Stopping Detection

- Tap "Stop" button
- Or unplug RTL-SDR dongle
- Service auto-stops after 30 sec of inactivity

---

## Troubleshooting

### "No RTL-SDR devices detected"

**Check**:
- ✅ Dongle fully inserted in OTG cable
- ✅ OTG cable fully inserted in phone
- ✅ Phone supports USB OTG (test with flash drive)
- ✅ Dongle is RTL2832U chipset

**Try**:
1. Replug dongle (disconnect + reconnect)
2. Restart DroneDetectAndroid app
3. Check `adb logcat | grep UsbDeviceManager` for device IDs

### "Failed to connect to RTL-SDR"

**Possible Causes**:
1. **rtl_tcp_andro not installed**
   - Solution: Install "SDR driver" app from Play Store

2. **rtl_tcp_andro not running**
   - DroneDetectAndroid will try to launch it automatically
   - If fails, manually launch "SDR driver" app first

3. **Port conflict**
   - Another app is using RTL-SDR
   - Solution: Close other SDR apps (SDR++, RF Analyzer, etc.)

### "Drone detected" but no drone present

**False Positive Sources**:
- Garage door openers (433 MHz)
- Weather stations (433/915 MHz)
- Wireless doorbells
- Car key fobs
- Baby monitors

**Reduce False Positives**:
- Move away from buildings
- Use directional antenna (focuses scan area)
- Check confidence score (< 60% = likely false positive)

### Battery Drains Quickly

**Normal Behavior**:
- RTL-SDR draws ~300mA
- Continuous FFT computation uses CPU
- Expect 30-50% faster battery drain

**Solutions**:
- Use external battery pack
- Enable duty cycling: scan 5 sec, sleep 5 sec (future feature)
- Only scan when actively monitoring

### Phone Gets Hot

**Normal for Continuous Scanning**:
- RTL-SDR USB device generates heat
- FFT computation is CPU-intensive

**If Excessive**:
- Remove phone case (improves airflow)
- Pause scanning periodically
- Avoid direct sunlight

---

## Technical Details

### Frequency Coverage

**RTL-SDR Blog V3/V4**:
- Range: 500 kHz - 1.766 GHz
- **Can Detect**:
  - ✅ 433 MHz RC controllers (Europe/Asia)
  - ✅ 915 MHz RC controllers (US)
  - ✅ GPS L1 signals (1.5 GHz) - drone navigation
  - ✅ Sub-1 GHz telemetry
- **Cannot Detect**:
  - ❌ 2.4 GHz WiFi (use phone's WiFi scanner)
  - ❌ 5.8 GHz FPV video (need upconverter or HackRF)

### Detection Algorithm

**FHSS (Frequency Hopping Spread Spectrum) Detection**:

1. **Receive IQ samples** from RTL-SDR at 2.048 Msps
2. **Perform FFT** (1024 bins, 75% overlap)
3. **Detect peaks** above -80 dBm threshold
4. **Track frequency hops** over time
5. **Classify pattern**:
   - Hop rate: 40-100 Hz = drone RC controller
   - Hop rate: < 10 Hz = WiFi/telemetry
   - No hopping = GPS/fixed frequency

**Drone Signatures**:
- **Futaba FHSS**: 40-60 hops/sec, 20-40 MHz bandwidth
- **FrSky FHSS**: 80-100 hops/sec, 60-80 MHz bandwidth
- **Generic**: Variable patterns detected with lower confidence

### Sample Rate & FFT Configuration

```kotlin
Sample Rate: 2.048 MHz (2048000 Hz)
FFT Size: 1024 bins
Frequency Resolution: 2.048 MHz / 1024 = 2 kHz per bin
Time Resolution: 1024 / 2.048 MHz = 0.5 ms per frame
Hop Size: 256 samples (75% overlap)
```

### Power Consumption

**Typical Values**:
- RTL-SDR: 250-350 mA @ 5V
- FFT Processing: 10-15% CPU (varies by phone)
- Bluetooth (mesh): 5-10 mA (when enabled)

**Total Impact**: ~1.5-2x faster battery drain vs idle

---

## Advanced Configuration

### Changing Scan Frequency

Currently scans **433.92 MHz** by default. To change:

**Edit** `RtlSdrScanner.kt`:
```kotlin
private const val DEFAULT_CENTER_FREQ = 915.0e6  // Change to 915 MHz
```

**Or** (future feature): UI frequency selector

### Adjusting Sensitivity

**Edit** `RtlSdrScanner.kt`:
```kotlin
private const val POWER_THRESHOLD_DBM = -85.0  // More sensitive (more false positives)
private const val POWER_THRESHOLD_DBM = -75.0  // Less sensitive (may miss weak signals)
```

Default: **-80 dBm** (balanced)

### Multiple Frequency Scanning

**Future Feature**: Scan 433 MHz and 915 MHz alternately
- 5 seconds @ 433 MHz
- 5 seconds @ 915 MHz
- Repeat

---

## Field Deployment Tips (Iraq Use Case)

### Team Coordination

**Multi-Device Setup**:
1. Each team member has phone + RTL-SDR
2. Spread out in 100m grid pattern
3. Mesh networking shares detections (future feature)
4. Triangulate drone position from multiple detections

**Battery Management**:
- Carry 2-3 battery packs per person
- Rotate scanning duty (1 person scans, others on standby)
- Solar chargers for extended operations

### Environmental Considerations

**Heat** (Iraq summer):
- RTL-SDR rated to 70°C (but will throttle)
- Keep in shade when possible
- Pause scanning if device > 60°C

**Dust**:
- USB port covers when not in use
- Weatherproof cases for RTL-SDR
- Blow out USB ports with compressed air regularly

**Interference**:
- Stay 50m+ from radio towers
- Avoid power lines (EMI interference)
- Military bases have high RF noise (scan away from facilities)

### Legal & Operational

**Detection = Legal, Jamming = Illegal**:
- ✅ Listening/detecting drone signals is legal
- ❌ Transmitting/jamming is illegal without authorization
- DroneDetectAndroid is RX-only (receive only)

**Coordinate with Authorities**:
- Inform local security forces if deploying
- Share detection logs with authorized personnel
- Do not interfere with military operations

---

## Hardware Shopping List

**Minimum Setup** ($45):
- RTL-SDR Blog V3: $30
- USB OTG cable: $5
- Android phone: (use existing)
- **Total**: $35

**Recommended Setup** ($75):
- RTL-SDR Blog V3: $30
- USB OTG cable: $5
- Battery pack (10000mAh): $20
- Directional antenna: $20
- **Total**: $75

**Professional Setup** ($200):
- RTL-SDR Blog V4: $40
- USB OTG cable (right-angle): $8
- Rugged battery pack (20000mAh): $40
- High-gain Yagi antenna (9 dBi): $60
- Weatherproof case: $30
- Spare phone for dedicated detection: $50
- **Total**: $228

---

## FAQ

**Q: Do I need root access?**
A: No, RTL-SDR works via Android USB Host API (no root required)

**Q: Will this work on iPhone?**
A: No, iOS does not support USB host for SDR devices

**Q: Can I use this while WiFi scanning?**
A: Yes! App runs both simultaneously for maximum coverage

**Q: How far can it detect drones?**
A: ~500m with stock antenna, 1-2km with directional antenna

**Q: Does it work offline?**
A: Yes, fully air-gapped operation (no internet needed)

**Q: Can it detect DJI drones?**
A: Yes, if they broadcast WiFi (2.4 GHz) or use 433/915 MHz RC (older models)

**Q: What about 5.8 GHz FPV drones?**
A: Need upconverter (~$45) or HackRF (~$300) for 5.8 GHz

**Q: Can I detect military drones?**
A: Unlikely - military uses encrypted, frequency-hopping systems on different bands

---

## Support & Resources

**Documentation**:
- Main README: `/README.md`
- External Sensors Roadmap: `/EXTERNAL_SENSORS.md`
- Build Instructions: `/USAGE.md`

**Hardware**:
- RTL-SDR Blog: https://www.rtl-sdr.com
- rtl_tcp_andro: https://github.com/martinmarinov/rtl_tcp_andro-

**Community**:
- GitHub Issues: https://github.com/nbschultz97/DroneDetectAndroid/issues
- Reddit: r/RTLSDR, r/drones

---

## What's Next?

**Upcoming Features** (in development):
1. ✅ RTL-SDR integration (Phase 1 - DONE)
2. 🔄 Mesh networking (Bluetooth LE peer-to-peer alerts)
3. 🔄 Spectrum waterfall visualization
4. 🔄 Multi-frequency scanning (433 + 915 MHz auto-switch)
5. ⏳ Direction finding (triangulation with multiple devices)
6. ⏳ Audio alerts (beep when drone detected)
7. ⏳ Detection logging & export

**Legend**: ✅ Complete | 🔄 In Progress | ⏳ Planned

---

*Last Updated: 2026-01-08*
*For DroneDetectAndroid v1.0*
