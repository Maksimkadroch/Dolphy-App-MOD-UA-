package com.droid.dolphy.plugin.js

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.droid.dolphy.plugin.PluginRegistry
import com.droid.dolphy.plugin.bridge.PluginAndroidApis
import com.droid.dolphy.plugin.bridge.PluginDeviceApis
import com.droid.dolphy.plugin.model.OtherCardContribution
import com.droid.dolphy.plugin.model.OtherSections
import com.droid.dolphy.plugin.model.PluginDialogSpec
import com.droid.dolphy.plugin.model.PluginManifest
import com.droid.dolphy.plugin.model.SettingsItemContribution
import com.droid.dolphy.plugin.model.SettingsSectionContribution
import com.droid.dolphy.plugin.model.UiNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentHashMap





class JsPluginSession(
    private val appContext: Context,
    val manifest: PluginManifest,
    private val sourceCode: String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val device = PluginDeviceApis(appContext)
    private val androidApis = PluginAndroidApis(appContext, manifest.id)
    private val uiBuilder = JsUiBuilder()
    private val prefs = appContext.getSharedPreferences("plugin_prefs_${manifest.id}", Context.MODE_PRIVATE)
    private val state = ConcurrentHashMap<String, Any?>()
    private val _dialog = MutableStateFlow<PluginDialogSpec?>(null)
    val dialog: StateFlow<PluginDialogSpec?> = _dialog.asStateFlow()

    @Volatile private var scope: Scriptable? = null
    @Volatile private var started = false
    @Volatile private var stateVersion: Int = 0
    @Volatile var lastError: String? = null
        private set

    var navigateToScreen: ((String) -> Unit)? = null
    var requestUiRefresh: (() -> Unit)? = null


    private val refreshDebounceMs = 80L
    private val refreshRunnable = Runnable { requestUiRefresh?.invoke() }

    private fun scheduleUiRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, refreshDebounceMs)
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun onDialogButton(callbackId: String?) {
        _dialog.value = null
        if (callbackId != null) onCallback(callbackId, null)
    }

    fun start() {
        synchronized(this) {
            stopInternal(clearRegistry = false)
            try {
                withRhino { cx, _ ->
                    val sc = cx.initStandardObjects()
                    scope = sc
                    uiBuilder.cx = cx
                    uiBuilder.scope = sc
                    val api = try {
                        buildApi(cx, sc)
                    } catch (t: Throwable) {

                        Log.e(TAG, "buildApi partial failure for ${manifest.id}", t)
                        lastError = "api: ${t.javaClass.simpleName}: ${t.message}"
                        cx.newObject(sc)
                    }
                    ScriptableObject.putProperty(sc, "api", api)
                    ScriptableObject.putProperty(sc, "console", buildConsole(cx, sc))
                    try {
                        cx.evaluateString(sc, sourceCode, manifest.id, 1, null)
                        callIfExists(cx, sc, "onLoad", api)
                        if (lastError == null) lastError = null
                        started = true
                    } catch (e: Exception) {
                        lastError = e.message
                        Log.e(TAG, "Plugin ${manifest.id} load failed", e)
                        started = true
                    } catch (t: Throwable) {

                        lastError = "${t.javaClass.simpleName}: ${t.message}"
                        Log.e(TAG, "Plugin ${manifest.id} fatal load error", t)
                        started = true
                        scope = sc
                    }
                }
            } catch (t: Throwable) {
                lastError = "${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "Plugin ${manifest.id} Rhino start failed", t)
                scope = null
                started = false

                throw t
            }
        }
    }

    fun stop() {
        synchronized(this) {
            try {
                withRhino { cx, sc ->
                    if (sc != null) callIfExists(cx, sc, "onUnload")
                }
            } catch (_: Exception) {
            }
            stopInternal(clearRegistry = true)
            device.stopAllBleScans()
            androidApis.release()
            mainHandler.removeCallbacks(refreshRunnable)
            _dialog.value = null
        }
    }

    private fun stopInternal(clearRegistry: Boolean) {
        scope = null
        started = false
        uiBuilder.clearAllCallbacks()
        if (clearRegistry) {
            PluginRegistry.clearPlugin(manifest.id)
        }
    }

    fun renderScreen(screenId: String): UiNode {
        synchronized(this) {
            val sc = scope
            if (!started || sc == null) {
                return errorNode(lastError ?: "Плагин не загружен")
            }
            return try {
                withRhino { cx, _ ->
                    uiBuilder.clearCallbacks()
                    uiBuilder.cx = cx
                    uiBuilder.scope = sc
                    val ui = uiBuilder.createUiObject(cx, sc)
                    val api = ScriptableObject.getProperty(sc, "api")
                    val stateObj = stateToJs(cx, sc)
                    val fnName = "screen_$screenId"
                    val fn = ScriptableObject.getProperty(sc, fnName)
                    val result = when {
                        fn is org.mozilla.javascript.Function ->
                            fn.call(cx, sc, sc, arrayOf(ui, api, stateObj))
                        else -> {
                            val main = ScriptableObject.getProperty(sc, "screen_main")
                            if (main is org.mozilla.javascript.Function) {
                                main.call(cx, sc, sc, arrayOf(ui, api, stateObj))
                            } else {
                                return@withRhino errorNode("Функция $fnName / screen_main не найдена")
                            }
                        }
                    }
                    val node = uiBuilder.unwrap(result)
                    if (node is UiNode.Empty || (node is UiNode.Scaffold && node.content is UiNode.Empty)) {
                        Log.w(TAG, "render produced empty tree for ${manifest.id}/$screenId")
                        if (lastError != null) errorNode(lastError!!)
                        else errorNode("Пустой UI (проверьте return ui.scaffold({ content: ... }))")
                    } else {
                        lastError = null
                        node
                    }
                }
            } catch (e: Exception) {
                lastError = e.message
                Log.e(TAG, "renderScreen $screenId", e)
                errorNode(e.message ?: "render error")
            }
        }
    }

    fun onCallback(id: String?, value: Any? = Undefined) {
        if (id == null) return



        val run = Runnable {
            synchronized(this) {
                try {
                    withRhino { cx, sc ->
                        if (sc == null) return@withRhino
                        uiBuilder.cx = cx
                        uiBuilder.scope = sc
                        if (value === Undefined) {
                            uiBuilder.invokeCallback(id)
                        } else {
                            uiBuilder.invokeCallback(id, value)
                        }
                    }

                    scheduleUiRefresh()
                } catch (e: Exception) {
                    Log.w(TAG, "onCallback", e)
                    lastError = e.message
                    scheduleUiRefresh()
                }
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            run.run()
        } else {
            mainHandler.post(run)
        }
    }

    fun getStateVersion(): Int = stateVersion

    private fun <T> withRhino(block: (RhinoContext, Scriptable?) -> T): T {
        val cx = RhinoContext.enter()
        try {
            cx.optimizationLevel = -1
            try {
                cx.languageVersion = RhinoContext.VERSION_ES6
            } catch (_: Exception) {
            }
            return block(cx, scope)
        } finally {
            try {
                RhinoContext.exit()
            } catch (_: Exception) {
            }
        }
    }

    private fun errorNode(msg: String): UiNode {
        return UiNode.Scaffold(
            topBar = UiNode.TopBar(manifest.name, showBack = true),
            content = UiNode.Column(
                listOf(
                    UiNode.Text(manifest.name, "headlineSmall"),
                    UiNode.Spacer(8f),
                    UiNode.Text(msg, "bodyMedium", color = "error"),
                    UiNode.Spacer(12f),
                    UiNode.Text("id=${manifest.id} v${manifest.version}", "labelSmall", color = "muted"),
                ),
                padding = 16f,
                spacing = 8f,
                fillMaxSize = true,
            ),
        )
    }

    private fun callIfExists(cx: RhinoContext, sc: Scriptable, name: String, vararg args: Any?) {
        val fn = ScriptableObject.getProperty(sc, name)
        if (fn is org.mozilla.javascript.Function) {
            fn.call(cx, sc, sc, args)
        }
    }

    private fun buildApi(cx: RhinoContext, sc: Scriptable): Scriptable {
        val api = cx.newObject(sc)

        fun fn(name: String, body: (Array<Any?>) -> Any?) {
            ScriptableObject.putProperty(api, name, object : BaseFunction() {
                override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                    return body(args)
                }
            })
        }

        fn("setState") { args ->
            val obj = args.getOrNull(0)
            if (obj is Scriptable) {
                for (id in ScriptableObject.getPropertyIds(obj)) {
                    val k = id.toString()
                    state[k] = jsToJvm(ScriptableObject.getProperty(obj, k))
                }
                stateVersion++

                scheduleUiRefresh()
            }
            null
        }
        fn("getState") { _ ->



            val c = RhinoContext.getCurrentContext() ?: return@fn null
            val s = scope ?: return@fn null
            stateToJs(c, s)
        }
        fn("toast") { args ->
            val msg = args.getOrNull(0)?.toString() ?: return@fn null
            mainHandler.post {
                Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
            }
            null
        }
        fn("log") { args ->
            Log.i("Plugin:${manifest.id}", args.joinToString(" ") { it?.toString() ?: "null" })
            null
        }
        fn("navigate") { args ->
            val screen = args.getOrNull(0)?.toString() ?: return@fn null
            mainHandler.post { navigateToScreen?.invoke(screen) }
            null
        }

        val prefsObj = cx.newObject(sc)
        ScriptableObject.putProperty(prefsObj, "get", object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                val key = args.getOrNull(0)?.toString() ?: return null
                val def = args.getOrNull(1)
                return when (def) {
                    is Boolean -> prefs.getBoolean(key, def)
                    is Number -> prefs.getFloat(key, def.toFloat()).toDouble()
                    else -> prefs.getString(key, def?.toString())
                }
            }
        })
        ScriptableObject.putProperty(prefsObj, "set", object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                val key = args.getOrNull(0)?.toString() ?: return null
                when (val v = args.getOrNull(1)) {
                    is Boolean -> prefs.edit().putBoolean(key, v).apply()
                    is Number -> prefs.edit().putFloat(key, v.toFloat()).apply()
                    else -> prefs.edit().putString(key, v?.toString() ?: "").apply()
                }
                return null
            }
        })
        ScriptableObject.putProperty(api, "prefs", prefsObj)

        val other = cx.newObject(sc)
        ScriptableObject.putProperty(other, "add", object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                val p = asMap(args.getOrNull(0))
                PluginRegistry.addOtherCard(
                    OtherCardContribution(
                        pluginId = manifest.id,
                        section = OtherSections.normalize(p["section"]?.toString()),
                        title = p["title"]?.toString() ?: manifest.name,
                        description = p["description"]?.toString() ?: "",
                        icon = p["icon"]?.toString() ?: "extension",
                        iconTintArgb = (p["color"] as? Number)?.toLong(),
                        screenId = p["screen"]?.toString() ?: p["screenId"]?.toString() ?: "main",
                        order = (p["order"] as? Number)?.toInt() ?: 0,
                    )
                )
                return null
            }
        })
        ScriptableObject.putProperty(api, "other", other)

        val settings = cx.newObject(sc)
        ScriptableObject.putProperty(settings, "addSection", object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                val p = asMap(args.getOrNull(0))
                val title = p["title"]?.toString() ?: manifest.name
                val itemsRaw = p["items"]
                val items = mutableListOf<SettingsItemContribution>()
                if (itemsRaw is org.mozilla.javascript.NativeArray) {
                    for (i in 0 until itemsRaw.length.toInt()) {
                        val m = asMap(itemsRaw.get(i, itemsRaw))
                        when (m["type"]?.toString()?.lowercase()) {
                            "switch" -> items += SettingsItemContribution.SwitchItem(
                                manifest.id,
                                m["key"]?.toString() ?: "sw_$i",
                                m["title"]?.toString() ?: "",
                                m["subtitle"]?.toString() ?: "",
                                m["value"] as? Boolean ?: m["default"] as? Boolean ?: false,
                            )
                            "slider" -> items += SettingsItemContribution.SliderItem(
                                manifest.id,
                                m["key"]?.toString() ?: "sl_$i",
                                m["title"]?.toString() ?: "",
                                m["subtitle"]?.toString() ?: "",
                                (m["min"] as? Number)?.toFloat() ?: 0f,
                                (m["max"] as? Number)?.toFloat() ?: 100f,
                                (m["value"] as? Number)?.toFloat()
                                    ?: (m["default"] as? Number)?.toFloat()
                                    ?: 50f,
                                (m["steps"] as? Number)?.toInt() ?: 0,
                            )
                            "nav" -> items += SettingsItemContribution.NavItem(
                                manifest.id,
                                m["title"]?.toString() ?: "",
                                m["subtitle"]?.toString() ?: "",
                                m["icon"]?.toString() ?: "extension",
                                m["screen"]?.toString() ?: "main",
                            )
                            "card" -> items += SettingsItemContribution.CardItem(
                                manifest.id,
                                m["title"]?.toString() ?: "",
                                m["subtitle"]?.toString() ?: "",
                                m["icon"]?.toString() ?: "extension",
                                m["screen"]?.toString(),
                            )
                            "header" -> items += SettingsItemContribution.Header(
                                manifest.id,
                                m["title"]?.toString() ?: "",
                            )
                            else -> items += SettingsItemContribution.CardItem(
                                manifest.id,
                                m["title"]?.toString() ?: "Item",
                                m["subtitle"]?.toString() ?: "",
                                m["icon"]?.toString() ?: "extension",
                                m["screen"]?.toString(),
                            )
                        }
                    }
                }
                PluginRegistry.addSettingsSection(
                    SettingsSectionContribution(manifest.id, title, items, (p["order"] as? Number)?.toInt() ?: 0)
                )
                return null
            }
        })
        ScriptableObject.putProperty(api, "settings", settings)


        ScriptableObject.putProperty(api, "dialog", module(cx, sc) { put ->
            put("show") { args ->
                val raw = args.getOrNull(0)
                val title: String
                val message: String
                val cancelable: Boolean
                val buttons = mutableListOf<UiNode.DialogButton>()

                if (raw is Scriptable) {
                    title = ScriptableObject.getProperty(raw, "title")?.toString()?.takeIf {
                        it != "undefined" && it != "null"
                    } ?: ""
                    val msgProp = ScriptableObject.getProperty(raw, "message")
                        ?: ScriptableObject.getProperty(raw, "text")
                        ?: ScriptableObject.getProperty(raw, "body")
                    message = msgProp?.toString()?.takeIf { it != "undefined" && it != "null" } ?: ""
                    cancelable = when (val c = ScriptableObject.getProperty(raw, "cancelable")) {
                        is Boolean -> c
                        else -> true
                    }
                    val buttonsRaw = ScriptableObject.getProperty(raw, "buttons")
                    if (buttonsRaw is Scriptable) {
                        val len = (ScriptableObject.getProperty(buttonsRaw, "length") as? Number)?.toInt() ?: 0
                        for (i in 0 until len) {
                            val item = ScriptableObject.getProperty(buttonsRaw, i)
                            if (item is Scriptable) {
                                val text = ScriptableObject.getProperty(item, "text")?.toString()
                                    ?: ScriptableObject.getProperty(item, "label")?.toString()
                                    ?: "OK"
                                val style = ScriptableObject.getProperty(item, "style")?.toString()
                                    ?: if (i == 0) "filled" else "text"
                                val onClick = ScriptableObject.getProperty(item, "onClick")
                                buttons += UiNode.DialogButton(
                                    text,
                                    style,
                                    uiBuilder.registerPersistentCallback(onClick),
                                )
                            }
                        }
                    }
                    if (buttons.isEmpty()) {
                        val confirm = ScriptableObject.getProperty(raw, "confirmText")?.toString()
                            ?: ScriptableObject.getProperty(raw, "ok")?.toString()
                            ?: "OK"
                        val dismissRaw = ScriptableObject.getProperty(raw, "dismissText")
                            ?: ScriptableObject.getProperty(raw, "cancel")
                        val dismiss = dismissRaw?.toString()?.takeIf {
                            it != "undefined" && it != "null"
                        }
                        val onConfirm = ScriptableObject.getProperty(raw, "onConfirm")
                            ?: ScriptableObject.getProperty(raw, "onOk")
                        val onDismiss = ScriptableObject.getProperty(raw, "onDismiss")
                            ?: ScriptableObject.getProperty(raw, "onCancel")
                        buttons += UiNode.DialogButton(
                            confirm,
                            "filled",
                            uiBuilder.registerPersistentCallback(onConfirm),
                        )
                        if (dismiss != null) {
                            buttons += UiNode.DialogButton(
                                dismiss,
                                "text",
                                uiBuilder.registerPersistentCallback(onDismiss),
                            )
                        }
                    }
                } else {
                    title = ""
                    message = raw?.toString() ?: ""
                    cancelable = true
                    buttons += UiNode.DialogButton("OK", "filled", null)
                }

                mainHandler.post {
                    _dialog.value = PluginDialogSpec(title, message, buttons, cancelable)
                }
                true
            }
            put("dismiss") {
                mainHandler.post { _dialog.value = null }
                null
            }
        })

        ScriptableObject.putProperty(api, "wifi", module(cx, sc) { put ->
            put("isEnabled") { device.wifiIsEnabled() }
            put("startScan") { args ->
                val force = args.getOrNull(0) as? Boolean ?: false
                device.wifiStartScan(force)
            }
            put("getScanResults") { args ->
                val max = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                val minRssi = (args.getOrNull(1) as? Number)?.toInt()
                device.wifiScanResultsJson(max, minRssi)
            }
            put("connectionInfo") { device.wifiConnectionInfoJson() }
            put("openSettings") { device.wifiOpenSettings(); null }
            put("disconnect") { androidApis.wifiDisconnect() }
            put("reconnect") { androidApis.wifiReconnect() }
            put("addSuggestion") { args ->
                val ssid = args.getOrNull(0)?.toString() ?: return@put false
                val pass = args.getOrNull(1)?.toString()
                androidApis.wifiAddSuggestion(ssid, pass)
            }
            put("scan") { args ->
                val first = args.getOrNull(0)
                val opts = if (first is Scriptable && first !is org.mozilla.javascript.Function) asMap(first) else emptyMap()
                val cb = when {
                    first is org.mozilla.javascript.Function -> first
                    else -> opts["callback"] ?: opts["onResult"] ?: args.getOrNull(1)
                }
                val max = (opts["maxResults"] as? Number)?.toInt() ?: 40
                val minRssi = (opts["minRssi"] as? Number)?.toInt()
                val force = opts["force"] as? Boolean ?: false
                device.wifiStartScan(force)
                mainHandler.postDelayed({
                    val json = device.wifiScanResultsJson(max, minRssi)
                    invokeJs(cb, json, refreshUi = false)
                }, 2800)
                true
            }
        })

        ScriptableObject.putProperty(api, "ble", module(cx, sc) { put ->
            put("isEnabled") { device.btIsEnabled() }
            put("startScan") { args ->
                val first = args.getOrNull(0)
                val opts = if (first is Scriptable && first !is org.mozilla.javascript.Function) asMap(first) else emptyMap()
                val cb = when {
                    first is org.mozilla.javascript.Function -> first
                    else -> opts["onDevice"] ?: opts["callback"] ?: args.getOrNull(1)
                }
                val batchMs = (opts["batchMs"] as? Number)?.toLong() ?: 400L
                val maxDevices = (opts["maxDevices"] as? Number)?.toInt() ?: 80
                device.bleStartScan(manifest.id, { json ->
                    invokeJs(cb, json, refreshUi = false)
                }, batchMs, maxDevices)
            }
            put("stopScan") { device.stopBleScan(manifest.id); null }
            put("advertise") { args ->


                val first = args.getOrNull(0)
                if (first is Scriptable && first !is org.mozilla.javascript.Function) {
                    val o = asMap(first)
                    androidApis.bleAdvertiseStart(
                        (o["manufacturerId"] as? Number)?.toInt() ?: (o["id"] as? Number)?.toInt() ?: 0xFFFF,
                        o["payloadHex"]?.toString() ?: o["hex"]?.toString() ?: o["payload"]?.toString() ?: "",
                        o["connectable"] as? Boolean ?: false,
                        o["includeName"] as? Boolean ?: false,
                    )
                } else {
                    val id = (args.getOrNull(0) as? Number)?.toInt() ?: 0xFFFF
                    val hex = args.getOrNull(1)?.toString() ?: return@put false
                    androidApis.bleAdvertiseStart(id, hex)
                }
            }
            put("stopAdvertise") { androidApis.bleAdvertiseStop(); null }
            put("connect") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val cb = args.getOrNull(1)
                androidApis.gattConnect(addr) { invokeJs(cb, it, refreshUi = false) }
            }
            put("disconnect") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put null
                androidApis.gattDisconnect(addr)
                null
            }
            put("write") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                val hex = args.getOrNull(3)?.toString() ?: return@put false
                androidApis.gattWrite(addr, svc, ch, hex)
            }
            put("read") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                androidApis.gattRead(addr, svc, ch)
            }
        })


        ScriptableObject.putProperty(api, "bt", module(cx, sc) { put ->
            put("isEnabled") { device.btIsEnabled() }
            put("name") { androidApis.btName() }
            put("address") { androidApis.btAddress() }
            put("bondedDevices") { androidApis.btBondedDevicesJson() }
            put("startDiscovery") { args ->
                val cb = args.getOrNull(0)
                androidApis.btStartDiscovery { invokeJs(cb, it, refreshUi = false) }
            }
            put("stopDiscovery") { androidApis.btStopDiscovery(); null }
            put("openSettings") { androidApis.btOpenSettings() }
        })

        ScriptableObject.putProperty(api, "nfc", module(cx, sc) { put ->
            put("isAvailable") { device.nfcIsAvailable() }
            put("isEnabled") { device.nfcIsEnabled() }
            put("openSettings") { device.nfcOpenSettings(); null }
            put("openTools") {
                mainHandler.post { navigateToScreen?.invoke("__app__:other/nfc_tools") }
                null
            }
        })

        ScriptableObject.putProperty(api, "ir", module(cx, sc) { put ->
            put("status") { device.irStatusJson() }
            put("hasEmitter") { androidApis.irHasEmitter() }
            put("carrierFrequencies") { androidApis.irCarrierFrequenciesJson() }
            put("transmit") { args ->

                val first = args.getOrNull(0)
                if (first is Scriptable && first !is org.mozilla.javascript.NativeArray &&
                    first !is org.mozilla.javascript.Function
                ) {
                    val o = asMap(first)
                    val freq = (o["freq"] as? Number)?.toInt()
                        ?: (o["frequency"] as? Number)?.toInt()
                        ?: 38000
                    val pattern = toIntArray(o["pattern"] ?: o["pulses"])
                    androidApis.irTransmit(freq, pattern)
                } else {
                    val freq = (args.getOrNull(0) as? Number)?.toInt() ?: 38000
                    val pattern = toIntArray(args.getOrNull(1))
                    androidApis.irTransmit(freq, pattern)
                }
            }
            put("toggleStorm") { device.irToggleStorm() }
            put("toggleJammer") { device.irToggleJammer() }
            put("openRemotes") {
                mainHandler.post { navigateToScreen?.invoke("__app__:other/ir_flipper_home") }
                null
            }
        })

        ScriptableObject.putProperty(api, "net", module(cx, sc) { put ->
            put("active") { device.networkActiveJson() }
            put("detail") { androidApis.connectivityDetailJson() }
            put("interfaces") { androidApis.netInterfacesJson() }
            put("http") { args ->
                val method = args.getOrNull(0)?.toString() ?: "GET"
                val url = args.getOrNull(1)?.toString() ?: return@put null
                val body = args.getOrNull(2)?.toString()
                val headers = args.getOrNull(3)?.toString()
                val cb = args.getOrNull(4)
                device.httpRequest(method, url, body, headers) { invokeJs(cb, it) }
                null
            }
            put("tcpReachable") { args ->
                val host = args.getOrNull(0)?.toString() ?: return@put null
                val port = (args.getOrNull(1) as? Number)?.toInt() ?: 80
                val timeout = (args.getOrNull(2) as? Number)?.toInt() ?: 1500
                val cb = args.getOrNull(3)
                device.tcpReachable(host, port, timeout) { invokeJs(cb, it) }
                null
            }
            put("nsdDiscover") { args ->
                val type = args.getOrNull(0)?.toString() ?: "_http._tcp"
                val cb = args.getOrNull(1)
                androidApis.nsdDiscover(type) { invokeJs(cb, it, refreshUi = false) }
            }
            put("nsdStop") { androidApis.nsdStop(); null }
        })

        ScriptableObject.putProperty(api, "root", module(cx, sc) { put ->
            put("available") { device.rootAvailable() }
            put("exec") { args ->
                val cmd = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootExec(cmd) { invokeJs(cb, it) }
                null
            }
        })

        ScriptableObject.putProperty(api, "shizuku", module(cx, sc) { put ->
            put("available") { device.shizukuAvailable() }
            put("hasPermission") { device.shizukuHasPermission() }
            put("requestPermission") { device.shizukuRequestPermission(); null }
            put("exec") { args ->
                val cmd = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.shizukuExec(cmd) { invokeJs(cb, it) }
                null
            }
        })

        ScriptableObject.putProperty(api, "shell", module(cx, sc) { put ->
            put("exec") { args ->
                val cmd = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.shellExecSmart(cmd) { invokeJs(cb, it) }
                null
            }
        })

        ScriptableObject.putProperty(api, "clipboard", module(cx, sc) { put ->
            put("get") { androidApis.clipboardGet() }
            put("set") { args ->
                androidApis.clipboardSet(args.getOrNull(0)?.toString() ?: "")
            }
        })

        ScriptableObject.putProperty(api, "vibrator", module(cx, sc) { put ->
            put("vibrate") { args ->
                val ms = (args.getOrNull(0) as? Number)?.toLong() ?: 50L
                androidApis.vibrate(ms)
            }
            put("pattern") { args ->
                val arr = toLongArray(args.getOrNull(0))
                val repeat = (args.getOrNull(1) as? Number)?.toInt() ?: -1
                androidApis.vibratePattern(arr, repeat)
            }
            put("cancel") { androidApis.vibrateCancel(); null }
        })

        ScriptableObject.putProperty(api, "notify", module(cx, sc) { put ->
            put("show") { args ->

                val first = args.getOrNull(0)
                if (first is Scriptable && first !is org.mozilla.javascript.Function) {
                    val o = asMap(first)
                    androidApis.notifyShow(
                        (o["id"] as? Number)?.toInt() ?: 1,
                        o["title"]?.toString() ?: "",
                        o["text"]?.toString() ?: o["message"]?.toString() ?: "",
                        o["channel"]?.toString() ?: "dolphy_plugins",
                    )
                } else {
                    val id = (args.getOrNull(0) as? Number)?.toInt() ?: 1
                    val title = args.getOrNull(1)?.toString() ?: ""
                    val text = args.getOrNull(2)?.toString() ?: ""
                    androidApis.notifyShow(id, title, text)
                }
            }
            put("cancel") { args ->
                androidApis.notifyCancel((args.getOrNull(0) as? Number)?.toInt() ?: 1)
                null
            }
        })

        ScriptableObject.putProperty(api, "intent", module(cx, sc) { put ->
            put("openUrl") { args -> androidApis.openUrl(args.getOrNull(0)?.toString() ?: "") }
            put("shareText") { args ->
                androidApis.shareText(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "Share",
                )
            }
            put("openSettings") { args ->
                androidApis.openSettings(args.getOrNull(0)?.toString())
            }
            put("start") { args ->
                val action = args.getOrNull(0)?.toString() ?: return@put false
                val data = args.getOrNull(1)?.toString()
                val extras = args.getOrNull(2)?.toString()
                androidApis.startActivity(action, data, extras)
            }
            put("dial") { args -> androidApis.dial(args.getOrNull(0)?.toString() ?: "") }
        })

        ScriptableObject.putProperty(api, "files", module(cx, sc) { put ->
            put("list") { args -> androidApis.filesList(args.getOrNull(0)?.toString() ?: "") }
            put("read") { args -> androidApis.filesRead(args.getOrNull(0)?.toString() ?: "") }
            put("write") { args ->
                androidApis.filesWrite(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "",
                    args.getOrNull(2) as? Boolean ?: false,
                )
            }
            put("delete") { args -> androidApis.filesDelete(args.getOrNull(0)?.toString() ?: "") }
            put("exists") { args -> androidApis.filesExists(args.getOrNull(0)?.toString() ?: "") }
            put("writeBase64") { args ->
                androidApis.filesWriteBase64(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "",
                )
            }
            put("readBase64") { args -> androidApis.filesReadBase64(args.getOrNull(0)?.toString() ?: "") }
        })

        ScriptableObject.putProperty(api, "usb", module(cx, sc) { put ->
            put("devices") { androidApis.usbDevicesJson() }
        })

        ScriptableObject.putProperty(api, "location", module(cx, sc) { put ->
            put("isEnabled") { androidApis.locationIsEnabled() }
            put("last") { androidApis.locationLastJson() }
            put("openSettings") { androidApis.openSettings("location") }
        })

        ScriptableObject.putProperty(api, "sensors", module(cx, sc) { put ->
            put("list") { androidApis.sensorsListJson() }
            put("start") { args ->
                val type = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val cb = args.getOrNull(1)
                androidApis.sensorStart(type) { invokeJs(cb, it, refreshUi = false) }
            }
            put("stop") { args ->
                androidApis.sensorStop(args.getOrNull(0)?.toString() ?: "")
                null
            }
            put("stopAll") { androidApis.sensorStopAll(); null }
        })

        ScriptableObject.putProperty(api, "audio", module(cx, sc) { put ->
            put("playUrl") { args ->
                val url = args.getOrNull(0)?.toString() ?: return@put false
                val cb = args.getOrNull(1)
                androidApis.audioPlayUrl(url) { invokeJs(cb, it, refreshUi = false) }
            }
            put("playFile") { args ->
                androidApis.audioPlayFile(args.getOrNull(0)?.toString() ?: "")
            }
            put("stop") { androidApis.audioStop(); null }
            put("tone") { args ->
                val tone = (args.getOrNull(0) as? Number)?.toInt() ?: 24
                val dur = (args.getOrNull(1) as? Number)?.toInt() ?: 200
                androidApis.audioTone(tone, dur)
            }
        })

        ScriptableObject.putProperty(api, "pm", module(cx, sc) { put ->
            put("hasPermission") { args ->
                androidApis.hasPermission(args.getOrNull(0)?.toString() ?: "")
            }
            put("hasFeature") { args ->
                androidApis.hasFeature(args.getOrNull(0)?.toString() ?: "")
            }
            put("features") { androidApis.featuresJson() }
        })

        ScriptableObject.putProperty(api, "device", module(cx, sc) { put ->
            put("info") { androidApis.deviceInfoExtendedJson() }
            put("infoBasic") { device.deviceInfoJson() }
            put("battery") { androidApis.batteryJson() }
            put("features") { androidApis.featuresJson() }
        })


        ScriptableObject.putProperty(api, "app", module(cx, sc) { put ->
            put("open") { args ->
                val route = args.getOrNull(0)?.toString() ?: return@put false
                mainHandler.post {
                    navigateToScreen?.invoke(
                        if (route.startsWith("__app__:")) route else "__app__:$route"
                    )
                }
                true
            }
            put("packageName") { appContext.packageName }
        })

        ScriptableObject.putProperty(api, "pluginId", manifest.id)
        ScriptableObject.putProperty(api, "pluginName", manifest.name)

        return api
    }

    private fun toIntArray(v: Any?): IntArray {
        return when (v) {
            is IntArray -> v
            is List<*> -> v.mapNotNull { (it as? Number)?.toInt() }.toIntArray()
            is org.mozilla.javascript.NativeArray -> {
                IntArray(v.length.toInt()) { i ->
                    (v.get(i, v) as? Number)?.toInt() ?: 0
                }
            }
            is Scriptable -> {
                val len = (ScriptableObject.getProperty(v, "length") as? Number)?.toInt() ?: 0
                IntArray(len) { i ->
                    (ScriptableObject.getProperty(v, i) as? Number)?.toInt() ?: 0
                }
            }
            is String -> v.split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            else -> intArrayOf()
        }
    }

    private fun toLongArray(v: Any?): LongArray {
        return when (v) {
            is LongArray -> v
            is IntArray -> LongArray(v.size) { v[it].toLong() }
            is List<*> -> v.mapNotNull { (it as? Number)?.toLong() }.toLongArray()
            is org.mozilla.javascript.NativeArray -> {
                LongArray(v.length.toInt()) { i ->
                    (v.get(i, v) as? Number)?.toLong() ?: 0L
                }
            }
            is Scriptable -> {
                val len = (ScriptableObject.getProperty(v, "length") as? Number)?.toInt() ?: 0
                LongArray(len) { i ->
                    (ScriptableObject.getProperty(v, i) as? Number)?.toLong() ?: 0L
                }
            }
            else -> longArrayOf(0, 50, 50, 50)
        }
    }

    private fun module(cx: RhinoContext, sc: Scriptable, build: (((String, (Array<Any?>) -> Any?) -> Unit) -> Unit)): Scriptable {
        val obj = cx.newObject(sc)
        build { name, body ->
            ScriptableObject.putProperty(obj, name, object : BaseFunction() {
                override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                    return body(args)
                }
            })
        }
        return obj
    }

    private fun buildConsole(cx: RhinoContext, sc: Scriptable): Scriptable {
        val console = cx.newObject(sc)
        val logFn = object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                Log.d("Plugin:${manifest.id}", args.joinToString(" ") { it?.toString() ?: "null" })
                return null
            }
        }
        ScriptableObject.putProperty(console, "log", logFn)
        ScriptableObject.putProperty(console, "warn", logFn)
        ScriptableObject.putProperty(console, "error", logFn)
        return console
    }

    private fun invokeJs(fn: Any?, arg: Any?, refreshUi: Boolean = true) {
        mainHandler.post {
            synchronized(this) {
                try {
                    withRhino { cx, sc ->
                        if (sc == null || fn !is org.mozilla.javascript.Function) return@withRhino
                        uiBuilder.cx = cx
                        uiBuilder.scope = sc
                        val jsArg = when (arg) {
                            is String -> {
                                try {
                                    if (arg.trimStart().startsWith("{") || arg.trimStart().startsWith("[")) {
                                        cx.evaluateString(sc, "($arg)", "json", 1, null)
                                    } else arg
                                } catch (_: Exception) {
                                    arg
                                }
                            }
                            is Boolean, is Number -> arg
                            null -> null
                            else -> arg.toString()
                        }
                        fn.call(cx, sc, sc, arrayOf(jsArg))
                    }

                    if (refreshUi) scheduleUiRefresh()
                } catch (e: Exception) {
                    Log.w(TAG, "invokeJs", e)
                }
            }
        }
    }

    private fun stateToJs(cx: RhinoContext, sc: Scriptable): Scriptable {
        val obj = cx.newObject(sc)
        for ((k, v) in state) {
            ScriptableObject.putProperty(obj, k, jvmToJs(cx, sc, v))
        }
        return obj
    }

    private fun jvmToJs(cx: RhinoContext, sc: Scriptable, v: Any?): Any? {
        return when (v) {
            null -> null
            is Boolean, is String, is Number -> v
            is Map<*, *> -> {
                val o = cx.newObject(sc)
                v.forEach { (kk, vv) ->
                    ScriptableObject.putProperty(o, kk.toString(), jvmToJs(cx, sc, vv))
                }
                o
            }
            is List<*> -> cx.newArray(sc, v.map { jvmToJs(cx, sc, it) }.toTypedArray())
            else -> v.toString()
        }
    }

    private fun jsToJvm(v: Any?): Any? {
        return when (v) {
            null, RhinoContext.getUndefinedValue() -> null
            is Boolean, is String, is Number -> v
            is Scriptable -> {
                if (v is org.mozilla.javascript.NativeArray) {
                    (0 until v.length.toInt()).map { jsToJvm(v.get(it, v)) }
                } else {
                    asMap(v)
                }
            }
            else -> v.toString()
        }
    }

    private fun asMap(v: Any?): Map<String, Any?> {
        if (v !is Scriptable) return emptyMap()
        val map = mutableMapOf<String, Any?>()
        for (id in ScriptableObject.getPropertyIds(v)) {
            map[id.toString()] = jsToJvm(ScriptableObject.getProperty(v, id.toString()))
        }
        return map
    }

    private object Undefined

    companion object {
        private const val TAG = "JsPluginSession"

        fun parseManifest(source: String, fallbackId: String, fallbackName: String): PluginManifest {
            val line = source.lineSequence().firstOrNull { it.contains("@plugin", ignoreCase = true) } ?: ""
            fun fromLine(key: String): String? =
                Regex("""\b$key\s*=\s*["']([^"']+)["']""").find(line)?.groupValues?.get(1)
                    ?: Regex("""\b$key\s*=\s*([A-Za-z0-9_.-]+)""").find(line)?.groupValues?.get(1)

            fun metaQuoted(key: String): String? {
                val re2 = Regex("""//\s*@$key\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                return re2.find(source)?.groupValues?.getOrNull(1)
            }

            val id = fromLine("id") ?: metaQuoted("id") ?: fallbackId
            val name = fromLine("name") ?: metaQuoted("name") ?: fallbackName
            val version = fromLine("version") ?: metaQuoted("version") ?: "1.0"
            val description = fromLine("description") ?: metaQuoted("description") ?: ""
            val author = fromLine("author") ?: metaQuoted("author") ?: ""
            return PluginManifest(id, name, version, description, author)
        }
    }
}
