package com.droid.dolphy.plugin.bridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.droid.dolphy.RootUtils
import com.droid.dolphy.ShizukuHelper
import com.droid.dolphy.plugin.PluginManager
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors




class PluginDeviceApis(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val bleCallbacks = ConcurrentHashMap<String, ScanCallback>()
    private val bleBatchers = ConcurrentHashMap<String, BleBatcher>()
    @Volatile private var lastWifiScanAt = 0L

    private val wifiManager: WifiManager?
        get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val btAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter


    fun wifiIsEnabled(): Boolean = try {
        wifiManager?.isWifiEnabled == true
    } catch (_: Exception) {
        false
    }





    @SuppressLint("MissingPermission")
    fun wifiScanResultsJson(maxResults: Int = DEFAULT_WIFI_MAX, minRssi: Int? = null): String {
        val wm = wifiManager ?: return "[]"
        return try {
            val cap = when {
                maxResults <= 0 -> DEFAULT_WIFI_MAX
                else -> maxResults.coerceAtMost(200)
            }
            @Suppress("DEPRECATION")
            val sorted = wm.scanResults.orEmpty()
                .asSequence()
                .filter { minRssi == null || it.level >= minRssi }
                .sortedByDescending { it.level }
                .take(cap)
            val arr = JSONArray()
            sorted.forEach { r ->
                arr.put(
                    JSONObject()
                        .put("ssid", r.SSID ?: "")
                        .put("bssid", r.BSSID ?: "")
                        .put("rssi", r.level)
                        .put("frequency", r.frequency)
                        .put("capabilities", r.capabilities ?: "")
                        .put("channelWidth", if (Build.VERSION.SDK_INT >= 23) r.channelWidth else -1)
                )
            }
            arr.toString()
        } catch (e: Exception) {
            Log.w(TAG, "wifiScanResults", e)
            "[]"
        }
    }





    @SuppressLint("MissingPermission")
    fun wifiStartScan(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force && now - lastWifiScanAt < WIFI_SCAN_MIN_INTERVAL_MS) {
            return false
        }
        return try {
            @Suppress("DEPRECATION")
            val ok = wifiManager?.startScan() == true
            if (ok) lastWifiScanAt = now
            ok
        } catch (e: Exception) {
            Log.w(TAG, "wifiStartScan", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun wifiConnectionInfoJson(): String {
        val wm = wifiManager ?: return "{}"
        return try {
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            val dhcp = wm.dhcpInfo
            JSONObject()
                .put("ssid", info?.ssid?.trim('"') ?: "")
                .put("bssid", info?.bssid ?: "")
                .put("rssi", info?.rssi ?: 0)
                .put("linkSpeed", info?.linkSpeed ?: 0)
                .put("ip", intToIp(info?.ipAddress ?: 0))
                .put("gateway", intToIp(dhcp?.gateway ?: 0))
                .put("netmask", intToIp(dhcp?.netmask ?: 0))
                .put("dns1", intToIp(dhcp?.dns1 ?: 0))
                .put("frequency", if (Build.VERSION.SDK_INT >= 21) info?.frequency ?: 0 else 0)
                .toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    fun wifiOpenSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }


    fun btIsEnabled(): Boolean = try {
        btAdapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }











    @SuppressLint("MissingPermission")
    fun bleStartScan(
        scanId: String,
        onDeviceJs: (String) -> Unit,
        batchMs: Long = BLE_BATCH_MS,
        maxDevices: Int = BLE_MAX_DEVICES,
    ): Boolean {
        val scanner = btAdapter?.bluetoothLeScanner ?: return false
        stopBleScan(scanId)
        val batcher = BleBatcher(
            batchMs = batchMs.coerceIn(150L, 2000L),
            maxDevices = maxDevices.coerceIn(16, 256),
            mainHandler = mainHandler,
            onFlush = { list ->

                list.forEach { onDeviceJs(it) }
            },
        )
        bleBatchers[scanId] = batcher
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val d = result.device
                    val address = d?.address ?: return
                    val json = JSONObject()
                        .put("name", d.name ?: result.scanRecord?.deviceName ?: "")
                        .put("address", address)
                        .put("rssi", result.rssi)
                        .put("connectable", if (Build.VERSION.SDK_INT >= 26) result.isConnectable else true)
                        .toString()
                    batcher.offer(address, json)
                } catch (e: Exception) {
                    Log.w(TAG, "ble result", e)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "ble scan failed $errorCode")
            }
        }
        bleCallbacks[scanId] = cb
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(0L)
                .build()
            scanner.startScan(null, settings, cb)
            true
        } catch (e: Exception) {
            Log.w(TAG, "bleStartScan", e)
            bleCallbacks.remove(scanId)
            bleBatchers.remove(scanId)?.cancel()
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan(scanId: String) {
        bleBatchers.remove(scanId)?.cancel()
        val cb = bleCallbacks.remove(scanId) ?: return
        try {
            btAdapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: Exception) {
        }
    }

    fun stopAllBleScans() {
        bleCallbacks.keys.toList().forEach { stopBleScan(it) }
    }




    private class BleBatcher(
        private val batchMs: Long,
        private val maxDevices: Int,
        private val mainHandler: Handler,
        private val onFlush: (List<String>) -> Unit,
    ) {
        private val pending = LinkedHashMap<String, String>()
        private val known = LinkedHashMap<String, String>()
        private var flushPosted = false
        private val flushRunnable = Runnable { flush() }

        @Synchronized
        fun offer(address: String, json: String) {
            val prev = known[address]

            if (prev == json) return
            known[address] = json
            while (known.size > maxDevices) {
                val first = known.entries.firstOrNull()?.key ?: break
                known.remove(first)
            }
            pending[address] = json
            if (!flushPosted) {
                flushPosted = true
                mainHandler.postDelayed(flushRunnable, batchMs)
            }
        }

        @Synchronized
        private fun flush() {
            flushPosted = false
            if (pending.isEmpty()) return
            val list = pending.values.toList()
            pending.clear()
            onFlush(list)
        }

        fun cancel() {
            mainHandler.removeCallbacks(flushRunnable)
            synchronized(this) {
                pending.clear()
                known.clear()
                flushPosted = false
            }
        }
    }


    fun nfcIsAvailable(): Boolean = NfcAdapter.getDefaultAdapter(context) != null

    fun nfcIsEnabled(): Boolean = try {
        NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    fun nfcOpenSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }


    fun irStatusJson(): String {
        return try {
            JSONObject()
                .put("stormRunning", try { com.droid.dolphy.IrWidgetRuntime.isStormRunning() } catch (_: Throwable) { false })
                .put("jammerRunning", try { com.droid.dolphy.IrWidgetRuntime.isJammerRunning() } catch (_: Throwable) { false })
                .put("hasConsumerIr", context.packageManager.hasSystemFeature("android.hardware.consumerir"))
                .toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    fun irToggleStorm(): Boolean = try {
        try { com.droid.dolphy.IrWidgetRuntime.toggleStorm() } catch (t: Throwable) {
            Log.w(TAG, "toggleStorm", t)
        }
        true
    } catch (_: Exception) {
        false
    }

    fun irToggleJammer(): Boolean = try {
        try { com.droid.dolphy.IrWidgetRuntime.toggleJammer() } catch (t: Throwable) {
            Log.w(TAG, "toggleJammer", t)
        }
        true
    } catch (_: Exception) {
        false
    }


    fun networkActiveJson(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            JSONObject()
                .put("hasInternet", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                .put("wifi", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                .put("cellular", caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                .put("vpn", caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
                .toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    fun httpRequest(
        method: String,
        url: String,
        body: String?,
        headersJson: String?,
        callback: (String) -> Unit,
    ) {
        io.execute {
            val result = try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method.uppercase()
                    connectTimeout = 15000
                    readTimeout = 20000
                    doInput = true
                    if (!headersJson.isNullOrBlank()) {
                        val obj = JSONObject(headersJson)
                        obj.keys().forEach { k -> setRequestProperty(k, obj.getString(k)) }
                    }
                    if (body != null && method.uppercase() != "GET" && method.uppercase() != "HEAD") {
                        doOutput = true
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                JSONObject()
                    .put("ok", code in 200..299)
                    .put("code", code)
                    .put("body", text.take(500_000))
                    .toString()
            } catch (e: Exception) {
                JSONObject()
                    .put("ok", false)
                    .put("code", -1)
                    .put("body", e.message ?: "error")
                    .toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun tcpReachable(host: String, port: Int, timeoutMs: Int, callback: (Boolean) -> Unit) {
        io.execute {
            val ok = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), timeoutMs.coerceIn(200, 10000))
                    true
                }
            } catch (_: Exception) {
                false
            }
            mainHandler.post { callback(ok) }
        }
    }


    fun rootAvailable(): Boolean = try {
        RootUtils.isRooted()
    } catch (t: Throwable) {
        Log.w(TAG, "rootAvailable", t)
        false
    }

    fun rootExec(command: String, callback: (String) -> Unit) {
        io.execute {
            val json = try {
                val (code, out) = RootUtils.executeRootCommand(command)
                JSONObject()
                    .put("code", code)
                    .put("out", out)
                    .put("err", "")
                    .toString()
            } catch (t: Throwable) {
                Log.w(TAG, "rootExec", t)
                JSONObject()
                    .put("code", -1)
                    .put("out", "")
                    .put("err", t.message ?: t.javaClass.simpleName)
                    .toString()
            }
            mainHandler.post { callback(json) }
        }
    }


    fun shizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    fun shizukuHasPermission(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun shizukuRequestPermission() {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(PluginManager.SHIZUKU_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "shizukuRequestPermission", e)
        }
    }

    fun shizukuExec(command: String, callback: (String) -> Unit) {
        io.execute {
            val result = try {
                ShizukuHelper.runShellCommandWithOutput(command)
            } catch (t: Throwable) {
                Log.w(TAG, "shizukuExec", t)
                JSONObject()
                    .put("code", -1)
                    .put("out", "")
                    .put("err", t.message ?: t.javaClass.simpleName)
                    .toString()
            }
            mainHandler.post { callback(result) }
        }
    }


    fun shellExecSmart(command: String, callback: (String) -> Unit) {
        when {
            shizukuHasPermission() -> shizukuExec(command, callback)
            rootAvailable() -> rootExec(command, callback)
            else -> mainHandler.post {
                callback(
                    JSONObject()
                        .put("code", -1)
                        .put("out", "")
                        .put("err", "No Shizuku permission and no root")
                        .put("via", "none")
                        .toString()
                )
            }
        }
    }

    fun deviceInfoJson(): String {
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("package", context.packageName)
            .toString()
    }

    private fun intToIp(value: Int): String {
        if (value == 0) return ""
        return "${value and 0xff}.${value shr 8 and 0xff}.${value shr 16 and 0xff}.${value shr 24 and 0xff}"
    }

    companion object {
        private const val TAG = "PluginDeviceApis"
        private const val DEFAULT_WIFI_MAX = 40
        private const val WIFI_SCAN_MIN_INTERVAL_MS = 8_000L
        private const val BLE_BATCH_MS = 400L
        private const val BLE_MAX_DEVICES = 80
    }
}
