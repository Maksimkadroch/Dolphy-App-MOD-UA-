package com.droid.dolphy.plugin.bridge

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger








class PluginAndroidApis(
    private val context: Context,
    private val pluginId: String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val appCtx = context.applicationContext

    private val filesDir by lazy {
        File(appCtx.filesDir, "dolphy_plugins_data/$pluginId").apply { mkdirs() }
    }


    private var advertiseCallback: AdvertiseCallback? = null


    private var discoveryReceiver: BroadcastReceiver? = null
    private var discoveryCallback: ((String) -> Unit)? = null


    private val gattMap = ConcurrentHashMap<String, BluetoothGatt>()


    @Volatile private var mediaPlayer: MediaPlayer? = null


    private var nsdListener: NsdManager.DiscoveryListener? = null
    private val nsdFound = ConcurrentHashMap<String, NsdServiceInfo>()


    private val sensorListeners = ConcurrentHashMap<String, SensorEventListener>()
    private val sensorSeq = AtomicInteger(0)

    private val btAdapter: BluetoothAdapter?
        get() = (appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val wifiManager: WifiManager?
        get() = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager


    fun clipboardGet(): String {
        return try {
            val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(appCtx)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun clipboardSet(text: String): Boolean = try {
        val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dolphy", text))
        true
    } catch (_: Exception) {
        false
    }


    @Suppress("DEPRECATION")
    fun vibrate(ms: Long): Boolean = try {
        val v = vibrator()
        if (Build.VERSION.SDK_INT >= 26) {
            v?.vibrate(VibrationEffect.createOneShot(ms.coerceIn(1, 5000), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v?.vibrate(ms.coerceIn(1, 5000))
        }
        true
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun vibratePattern(patternMs: LongArray, repeat: Int = -1): Boolean = try {
        val v = vibrator()
        if (Build.VERSION.SDK_INT >= 26) {
            v?.vibrate(VibrationEffect.createWaveform(patternMs, repeat))
        } else {
            v?.vibrate(patternMs, repeat)
        }
        true
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun vibrateCancel() {
        try {
            vibrator()?.cancel()
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= 31) {
            (appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }


    fun notifyShow(id: Int, title: String, text: String, channelId: String = "dolphy_plugins"): Boolean {
        return try {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(channelId, "Dolphy Plugins", NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(ch)
            }
            val n = NotificationCompat.Builder(appCtx, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify("plugin_$pluginId", id, n)
            true
        } catch (e: Exception) {
            Log.w(TAG, "notifyShow", e)
            false
        }
    }

    fun notifyCancel(id: Int) {
        try {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel("plugin_$pluginId", id)
        } catch (_: Exception) {
        }
    }


    fun openUrl(url: String): Boolean = try {
        val u = if (url.startsWith("http")) url else "https://$url"
        appCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun shareText(text: String, title: String = "Share"): Boolean = try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appCtx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun openSettings(action: String?): Boolean = try {
        val act = when (action?.lowercase()) {
            null, "", "app" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bt", "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "wireless" -> Settings.ACTION_WIRELESS_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "nfc_payment" -> Settings.ACTION_NFC_PAYMENT_SETTINGS
            else -> action
        }
        val intent = if (act == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
            Intent(act, Uri.parse("package:${appCtx.packageName}"))
        } else {
            Intent(act)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appCtx.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    fun startActivity(action: String, dataUri: String? = null, extrasJson: String? = null): Boolean = try {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!dataUri.isNullOrBlank()) intent.data = Uri.parse(dataUri)
        if (!extrasJson.isNullOrBlank()) {
            val o = JSONObject(extrasJson)
            o.keys().forEach { k ->
                when (val v = o.get(k)) {
                    is Boolean -> intent.putExtra(k, v)
                    is Int -> intent.putExtra(k, v)
                    is Long -> intent.putExtra(k, v)
                    is Double -> intent.putExtra(k, v)
                    else -> intent.putExtra(k, v.toString())
                }
            }
        }
        appCtx.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "startActivity", e)
        false
    }

    fun dial(number: String): Boolean = try {
        appCtx.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        false
    }


    fun filesList(sub: String = ""): String {
        val dir = resolvePath(sub, dirOnly = true) ?: return "[]"
        val arr = JSONArray()
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            arr.put(
                JSONObject()
                    .put("name", f.name)
                    .put("path", relativePath(f))
                    .put("isDir", f.isDirectory)
                    .put("size", if (f.isFile) f.length() else 0)
                    .put("modified", f.lastModified())
            )
        }
        return arr.toString()
    }

    fun filesRead(path: String): String? {
        val f = resolvePath(path) ?: return null
        if (!f.isFile) return null
        return try {
            f.readText(Charsets.UTF_8).take(2_000_000)
        } catch (_: Exception) {
            null
        }
    }

    fun filesWrite(path: String, content: String, append: Boolean = false): Boolean {
        val f = resolvePath(path) ?: return false
        return try {
            f.parentFile?.mkdirs()
            if (append) f.appendText(content, Charsets.UTF_8) else f.writeText(content, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun filesDelete(path: String): Boolean {
        val f = resolvePath(path) ?: return false
        return try {
            f.deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }

    fun filesExists(path: String): Boolean = resolvePath(path)?.exists() == true

    fun filesWriteBase64(path: String, b64: String): Boolean {
        val f = resolvePath(path) ?: return false
        return try {
            f.parentFile?.mkdirs()
            f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun filesReadBase64(path: String): String? {
        val f = resolvePath(path) ?: return null
        if (!f.isFile || f.length() > 5_000_000) return null
        return try {
            Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolvePath(path: String, dirOnly: Boolean = false): File? {
        val clean = path.trim().removePrefix("/").replace("..", "")
        val f = if (clean.isEmpty()) filesDir else File(filesDir, clean)
        val canon = try {
            f.canonicalFile
        } catch (_: Exception) {
            f
        }
        if (!canon.path.startsWith(filesDir.canonicalFile.path)) return null
        if (dirOnly && !canon.exists()) canon.mkdirs()
        return canon
    }

    private fun relativePath(f: File): String {
        val base = filesDir.canonicalFile.path
        val p = f.canonicalFile.path
        return if (p.startsWith(base)) p.removePrefix(base).removePrefix("/") else f.name
    }


    fun irHasEmitter(): Boolean {
        val cm = appCtx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        return cm?.hasIrEmitter() == true
    }

    fun irCarrierFrequenciesJson(): String {
        val cm = appCtx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            ?: return "[]"
        return try {
            val arr = JSONArray()
            cm.carrierFrequencies?.forEach { r ->
                arr.put(JSONObject().put("min", r.minFrequency).put("max", r.maxFrequency))
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    fun irTransmit(freqHz: Int, pattern: IntArray): Boolean {
        val cm = appCtx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager ?: return false
        return try {
            if (!cm.hasIrEmitter()) return false
            cm.transmit(freqHz, pattern)
            true
        } catch (e: Exception) {
            Log.w(TAG, "irTransmit", e)
            false
        }
    }


    @SuppressLint("MissingPermission")
    fun btBondedDevicesJson(): String {
        val arr = JSONArray()
        return try {
            btAdapter?.bondedDevices?.forEach { d ->
                arr.put(deviceJson(d))
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    @SuppressLint("MissingPermission")
    fun btStartDiscovery(onDevice: (String) -> Unit): Boolean {
        btStopDiscovery()
        val adapter = btAdapter ?: return false
        discoveryCallback = onDevice
        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (d != null) {
                            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                            val json = deviceJson(d).put("rssi", rssi).toString()
                            mainHandler.post { discoveryCallback?.invoke(json) }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {

                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            ContextCompat.registerReceiver(appCtx, discoveryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            @Suppress("DEPRECATION")
            return adapter.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "btStartDiscovery", e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun btStopDiscovery() {
        try {
            @Suppress("DEPRECATION")
            btAdapter?.cancelDiscovery()
        } catch (_: Exception) {
        }
        try {
            discoveryReceiver?.let { appCtx.unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        discoveryReceiver = null
        discoveryCallback = null
    }

    fun btOpenSettings(): Boolean = openSettings("bluetooth")

    fun btAddress(): String = try {
        @Suppress("DEPRECATION", "MissingPermission")
        btAdapter?.address ?: ""
    } catch (_: Exception) {
        ""
    }

    fun btName(): String = try {
        @Suppress("MissingPermission")
        btAdapter?.name ?: ""
    } catch (_: Exception) {
        ""
    }

    @SuppressLint("MissingPermission")
    private fun deviceJson(d: BluetoothDevice): JSONObject {
        return JSONObject()
            .put("name", try {
                d.name ?: ""
            } catch (_: Exception) {
                ""
            })
            .put("address", d.address ?: "")
            .put("bondState", d.bondState)
            .put("type", d.type)
            .put("deviceClass", try {
                d.bluetoothClass?.deviceClass ?: -1
            } catch (_: Exception) {
                -1
            })
    }


    @SuppressLint("MissingPermission")
    fun bleAdvertiseStart(
        manufacturerId: Int,
        payloadHex: String,
        connectable: Boolean = false,
        includeName: Boolean = false,
    ): Boolean {
        bleAdvertiseStop()
        val advertiser = btAdapter?.bluetoothLeAdvertiser ?: return false
        val payload = hexToBytes(payloadHex) ?: return false
        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(includeName)
            .addManufacturerData(manufacturerId, payload)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(connectable)
            .setTimeout(0)
            .build()
        val cb = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "advertise fail $errorCode")
            }
        }
        advertiseCallback = cb
        return try {
            advertiser.startAdvertising(settings, dataBuilder.build(), cb)
            true
        } catch (e: Exception) {
            Log.w(TAG, "bleAdvertiseStart", e)
            advertiseCallback = null
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun bleAdvertiseStop() {
        val cb = advertiseCallback ?: return
        try {
            btAdapter?.bluetoothLeAdvertiser?.stopAdvertising(cb)
        } catch (_: Exception) {
        }
        advertiseCallback = null
    }


    @SuppressLint("MissingPermission")
    fun gattConnect(address: String, onEvent: (String) -> Unit): Boolean {
        val device = try {
            btAdapter?.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        } ?: return false
        gattDisconnect(address)
        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val state = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> "connected"
                    BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
                    else -> "state_$newState"
                }
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "connection")
                            .put("state", state)
                            .put("status", status)
                            .put("address", address)
                            .toString()
                    )
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        gatt.discoverServices()
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val arr = JSONArray()
                gatt.services?.forEach { s ->
                    val chars = JSONArray()
                    s.characteristics?.forEach { c ->
                        chars.put(
                            JSONObject()
                                .put("uuid", c.uuid.toString())
                                .put("props", c.properties)
                        )
                    }
                    arr.put(JSONObject().put("uuid", s.uuid.toString()).put("characteristics", chars))
                }
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "services")
                            .put("status", status)
                            .put("services", arr)
                            .put("address", address)
                            .toString()
                    )
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: ByteArray(0)
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "read")
                            .put("uuid", characteristic.uuid.toString())
                            .put("status", status)
                            .put("hex", bytesToHex(value))
                            .put("address", address)
                            .toString()
                    )
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: ByteArray(0)
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "notify")
                            .put("uuid", characteristic.uuid.toString())
                            .put("hex", bytesToHex(value))
                            .put("address", address)
                            .toString()
                    )
                }
            }
        }
        return try {
            val gatt = if (Build.VERSION.SDK_INT >= 23) {
                device.connectGatt(appCtx, false, cb, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appCtx, false, cb)
            }
            if (gatt != null) {
                gattMap[address.uppercase()] = gatt
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "gattConnect", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun gattDisconnect(address: String) {
        val g = gattMap.remove(address.uppercase()) ?: return
        try {
            g.disconnect()
            g.close()
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun gattWrite(address: String, serviceUuid: String, charUuid: String, hex: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        val bytes = hexToBytes(hex) ?: return false
        return try {
            val svc = gatt.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        } catch (e: Exception) {
            Log.w(TAG, "gattWrite", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun gattRead(address: String, serviceUuid: String, charUuid: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        return try {
            val svc = gatt.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            gatt.readCharacteristic(ch)
        } catch (_: Exception) {
            false
        }
    }


    @SuppressLint("MissingPermission")
    fun wifiDisconnect(): Boolean = try {
        @Suppress("DEPRECATION")
        wifiManager?.disconnect() == true
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun wifiReconnect(): Boolean = try {
        @Suppress("DEPRECATION")
        wifiManager?.reconnect() == true
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun wifiAddSuggestion(ssid: String, passphrase: String?): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
            if (!passphrase.isNullOrBlank()) builder.setWpa2Passphrase(passphrase)
            val status = wifiManager?.addNetworkSuggestions(listOf(builder.build()))
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "wifiAddSuggestion", e)
            false
        }
    }


    fun netInterfacesJson(): String {
        val arr = JSONArray()
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp) return@forEach
                val addrs = JSONArray()
                ni.inetAddresses.toList().forEach { a ->
                    addrs.put(
                        JSONObject()
                            .put("host", a.hostAddress)
                            .put("ipv4", a is Inet4Address)
                            .put("loopback", a.isLoopbackAddress)
                    )
                }
                arr.put(
                    JSONObject()
                        .put("name", ni.name)
                        .put("display", ni.displayName)
                        .put("mtu", ni.mtu)
                        .put("addresses", addrs)
                )
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    fun nsdDiscover(serviceType: String, onEvent: (String) -> Unit): Boolean {
        nsdStop()
        val nsd = appCtx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return false
        val type = if (serviceType.endsWith(".")) serviceType else "$serviceType."
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                mainHandler.post {
                    onEvent(JSONObject().put("event", "started").put("type", regType).toString())
                }
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                nsdFound[service.serviceName] = service
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "found")
                            .put("name", service.serviceName)
                            .put("type", service.serviceType)
                            .toString()
                    )
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                nsdFound.remove(service.serviceName)
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "lost")
                            .put("name", service.serviceName)
                            .toString()
                    )
                }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                mainHandler.post {
                    onEvent(JSONObject().put("event", "stopped").toString())
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                mainHandler.post {
                    onEvent(JSONObject().put("event", "error").put("code", errorCode).toString())
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }
        nsdListener = listener
        return try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        } catch (e: Exception) {
            Log.w(TAG, "nsdDiscover", e)
            false
        }
    }

    fun nsdStop() {
        val nsd = appCtx.getSystemService(Context.NSD_SERVICE) as? NsdManager
        try {
            nsdListener?.let { nsd?.stopServiceDiscovery(it) }
        } catch (_: Exception) {
        }
        nsdListener = null
        nsdFound.clear()
    }


    fun usbDevicesJson(): String {
        val usb = appCtx.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return "[]"
        val arr = JSONArray()
        return try {
            usb.deviceList?.values?.forEach { d ->
                arr.put(
                    JSONObject()
                        .put("deviceName", d.deviceName)
                        .put("vendorId", d.vendorId)
                        .put("productId", d.productId)
                        .put("class", d.deviceClass)
                        .put("subclass", d.deviceSubclass)
                        .put("protocol", d.deviceProtocol)
                        .put("interfaceCount", d.interfaceCount)
                )
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }


    @SuppressLint("MissingPermission")
    fun locationLastJson(): String {
        return try {
            val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var best: android.location.Location? = null
            for (p in providers) {
                try {
                    val loc = lm.getLastKnownLocation(p) ?: continue
                    if (best == null || loc.time > best!!.time) best = loc
                } catch (_: Exception) {
                }
            }
            if (best == null) return JSONObject().put("ok", false).toString()
            JSONObject()
                .put("ok", true)
                .put("lat", best.latitude)
                .put("lon", best.longitude)
                .put("accuracy", best.accuracy)
                .put("provider", best.provider)
                .put("time", best.time)
                .put("altitude", best.altitude)
                .toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message).toString()
        }
    }

    fun locationIsEnabled(): Boolean = try {
        val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    } catch (_: Exception) {
        false
    }


    fun sensorsListJson(): String {
        val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return "[]"
        val arr = JSONArray()
        sm.getSensorList(Sensor.TYPE_ALL).forEach { s ->
            arr.put(
                JSONObject()
                    .put("name", s.name)
                    .put("type", s.type)
                    .put("vendor", s.vendor)
                    .put("maxRange", s.maximumRange)
                    .put("resolution", s.resolution)
            )
        }
        return arr.toString()
    }

    fun sensorStart(type: Int, onEvent: (String) -> Unit): String? {
        val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val sensor = sm.getDefaultSensor(type) ?: return null
        val id = "s${sensorSeq.incrementAndGet()}"
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val vals = JSONArray()
                event.values.forEach { vals.put(it.toDouble()) }
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("id", id)
                            .put("type", event.sensor.type)
                            .put("values", vals)
                            .put("accuracy", event.accuracy)
                            .put("timestamp", event.timestamp)
                            .toString()
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorListeners[id] = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        return id
    }

    fun sensorStop(id: String) {
        val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val l = sensorListeners.remove(id) ?: return
        try {
            sm?.unregisterListener(l)
        } catch (_: Exception) {
        }
    }

    fun sensorStopAll() {
        sensorListeners.keys.toList().forEach { sensorStop(it) }
    }


    fun audioPlayUrl(url: String, onDone: ((String) -> Unit)? = null): Boolean {
        audioStop()
        return try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(url)
            mp.setOnCompletionListener {
                mainHandler.post { onDone?.invoke(JSONObject().put("ok", true).toString()) }
                audioStop()
            }
            mp.setOnErrorListener { _, what, extra ->
                mainHandler.post {
                    onDone?.invoke(JSONObject().put("ok", false).put("what", what).put("extra", extra).toString())
                }
                audioStop()
                true
            }
            mp.prepareAsync()
            mp.setOnPreparedListener { it.start() }
            true
        } catch (e: Exception) {
            Log.w(TAG, "audioPlayUrl", e)
            false
        }
    }

    fun audioPlayFile(path: String): Boolean {
        val f = resolvePath(path) ?: return false
        if (!f.isFile) return false
        return audioPlayUrl(f.absolutePath)
    }

    fun audioStop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun audioTone(toneType: Int = ToneGenerator.TONE_PROP_BEEP, durationMs: Int = 200): Boolean = try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        tg.startTone(toneType, durationMs.coerceIn(50, 3000))
        mainHandler.postDelayed({
            try {
                tg.release()
            } catch (_: Exception) {
            }
        }, (durationMs + 100).toLong())
        true
    } catch (_: Exception) {
        false
    }


    fun batteryJson(): String {
        return try {
            val bm = appCtx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            JSONObject()
                .put("level", level)
                .put("charging", charging)
                .put("status", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS))
                .toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    fun hasPermission(permission: String): Boolean = try {
        ContextCompat.checkSelfPermission(appCtx, permission) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun hasFeature(feature: String): Boolean = try {
        appCtx.packageManager.hasSystemFeature(feature)
    } catch (_: Exception) {
        false
    }

    fun featuresJson(): String {
        val features = listOf(
            PackageManager.FEATURE_BLUETOOTH,
            PackageManager.FEATURE_BLUETOOTH_LE,
            PackageManager.FEATURE_WIFI,
            PackageManager.FEATURE_NFC,
            PackageManager.FEATURE_CONSUMER_IR,
            PackageManager.FEATURE_LOCATION,
            PackageManager.FEATURE_LOCATION_GPS,
            PackageManager.FEATURE_USB_HOST,
            PackageManager.FEATURE_CAMERA,
            PackageManager.FEATURE_MICROPHONE,
            PackageManager.FEATURE_TELEPHONY,
            PackageManager.FEATURE_SENSOR_ACCELEROMETER,
            PackageManager.FEATURE_SENSOR_GYROSCOPE,
            "android.hardware.vibrator",
        )
        val o = JSONObject()
        features.forEach { f ->
            o.put(f.removePrefix("android.hardware.").removePrefix("android.software."), hasFeature(f))
        }
        return o.toString()
    }

    fun deviceInfoExtendedJson(): String {
        return try {
            val pi = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("product", Build.PRODUCT)
                .put("board", Build.BOARD)
                .put("hardware", Build.HARDWARE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("release", Build.VERSION.RELEASE)
                .put("securityPatch", if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "")
                .put("package", appCtx.packageName)
                .put("versionName", pi.versionName)
                .put("versionCode", if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong())
                .put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                .put("btName", btName())
                .put("btAddress", btAddress())
                .toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message).toString()
        }
    }

    fun connectivityDetailJson(): String {
        return try {
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            JSONObject()
                .put("hasInternet", caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                .put("validated", caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                .put("wifi", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
                .put("cellular", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                .put("vpn", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true)
                .put("ethernet", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true)
                .put("bluetooth", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH) == true)
                .put("downKbps", caps?.linkDownstreamBandwidthKbps ?: 0)
                .put("upKbps", caps?.linkUpstreamBandwidthKbps ?: 0)
                .toString()
        } catch (_: Exception) {
            "{}"
        }
    }


    fun release() {
        btStopDiscovery()
        bleAdvertiseStop()
        gattMap.keys.toList().forEach { gattDisconnect(it) }
        audioStop()
        nsdStop()
        sensorStopAll()
        try {
            io.shutdownNow()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "PluginAndroidApis"

        fun hexToBytes(hex: String): ByteArray? {
            val clean = hex.trim().removePrefix("0x").replace(" ", "").replace(":", "")
            if (clean.isEmpty() || clean.length % 2 != 0) return null
            return try {
                ByteArray(clean.length / 2) { i ->
                    clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } catch (_: Exception) {
                null
            }
        }

        fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02X".format(it) }
    }
}
