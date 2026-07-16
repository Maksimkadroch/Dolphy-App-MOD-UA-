package com.droid.dolphy.plugin.js

import com.droid.dolphy.plugin.model.UiNode
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger





class JsUiBuilder {
    private val callbacks = ConcurrentHashMap<String, org.mozilla.javascript.Function>()

    private val dialogCallbacks = ConcurrentHashMap<String, org.mozilla.javascript.Function>()
    private val seq = AtomicInteger(0)
    @Volatile var cx: Context? = null
    @Volatile var scope: Scriptable? = null

    fun clearCallbacks() {
        callbacks.clear()
        nodeBag.clear()

    }

    fun clearAllCallbacks() {
        callbacks.clear()
        dialogCallbacks.clear()
        nodeBag.clear()
    }


    fun registerPersistentCallback(fn: Any?): String? {
        if (fn !is org.mozilla.javascript.Function) return null
        val id = "dlg_${seq.incrementAndGet()}"
        dialogCallbacks[id] = fn
        return id
    }

    fun invokeCallback(id: String?, arg: Any? = null) {
        if (id.isNullOrBlank()) return
        val fn = callbacks[id] ?: dialogCallbacks[id] ?: return
        val c = cx ?: return
        val s = scope ?: return
        try {
            val args = if (arg == null) arrayOfNulls<Any>(0) else arrayOf(arg)
            fn.call(c, s, s, args)
        } catch (e: Exception) {
            android.util.Log.w("JsUiBuilder", "callback $id failed", e)
        }
    }

    fun createUiObject(cx: Context, scope: Scriptable): Scriptable {
        this.cx = cx
        this.scope = scope

        val ui = cx.newObject(scope)
        fun put(name: String, fn: (Context, Scriptable, Array<Any?>) -> Any?) {
            ScriptableObject.putProperty(ui, name, object : BaseFunction() {
                override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                    return fn(cx, scope, args)
                }
            })
        }

        put("scaffold") { _, _, args ->



            val props = anyObjectMap(args, 0)
            val nestedTop = props["topBar"]
            val topBar = when {
                nestedTop is Scriptable -> topBarFrom(scriptableToMap(nestedTop))
                nestedTop is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    topBarFrom(nestedTop as Map<String, Any?>)
                }
                props.containsKey("title") -> topBarFrom(props)
                else -> null
            }
            val content = nodeProp(props, "content")
                ?: nodeAt(args, 1)
                ?: UiNode.Empty
            val fab = nodeProp(props, "fab")
            wrap(UiNode.Scaffold(topBar, content, fab))
        }
        put("column") { _, _, args -> wrap(UiNode.Column(childrenOf(args), f(args, "padding", 0f), f(args, "spacing", 8f), b(args, "fillMaxSize", false))) }
        put("Column") { _, _, args -> wrap(UiNode.Column(childrenOf(args), f(args, "padding", 0f), f(args, "spacing", 8f), b(args, "fillMaxSize", false))) }
        put("row") { _, _, args -> wrap(UiNode.Row(childrenOf(args), f(args, "padding", 0f), f(args, "spacing", 8f), b(args, "fillMaxWidth", true))) }
        put("Row") { _, _, args -> wrap(UiNode.Row(childrenOf(args), f(args, "padding", 0f), f(args, "spacing", 8f), b(args, "fillMaxWidth", true))) }
        put("box") { _, _, args -> wrap(UiNode.Box(childrenOf(args), f(args, "padding", 0f), b(args, "fillMaxSize", false))) }
        put("lazyColumn") { _, _, args -> wrap(UiNode.LazyColumn(childrenOf(args), f(args, "padding", 0f), f(args, "spacing", 4f), b(args, "fillMaxSize", true))) }
        put("LazyColumn") { _, _, args -> wrap(UiNode.LazyColumn(childrenOf(args), f(args, "padding", 0f), f(args, "spacing", 4f), b(args, "fillMaxSize", true))) }
        put("text") { _, _, args ->
            val t = str(args, 0, "")
            val props = props(args, 1)
            wrap(UiNode.Text(t, strProp(props, "style", "bodyMedium"), strPropOrNull(props, "color", null), intProp(props, "maxLines", Int.MAX_VALUE)))
        }
        put("Text") { _, _, args ->
            val t = str(args, 0, "")
            val props = props(args, 1)
            wrap(UiNode.Text(t, strProp(props, "style", "bodyMedium"), strPropOrNull(props, "color", null), intProp(props, "maxLines", Int.MAX_VALUE)))
        }
        put("button") { _, _, args ->
            val props = props(args, 0)
            val text = strProp(props, "text", str(args, 0, "Button"))
            wrap(
                UiNode.Button(
                    text = text,
                    onClickId = reg(props["onClick"]),
                    style = strProp(props, "style", "filled"),
                    enabled = boolProp(props, "enabled", true),
                    fillMaxWidth = boolProp(props, "fillMaxWidth", false),
                )
            )
        }
        put("Button") { _, _, args ->
            val props = props(args, 0)
            val text = if (args.isNotEmpty() && args[0] is CharSequence) str(args, 0, "Button") else strProp(props, "text", "Button")
            val p = if (args.isNotEmpty() && args[0] is CharSequence) props(args, 1) else props
            wrap(
                UiNode.Button(
                    text = text,
                    onClickId = reg(p["onClick"]),
                    style = strProp(p, "style", "filled"),
                    enabled = boolProp(p, "enabled", true),
                    fillMaxWidth = boolProp(p, "fillMaxWidth", false),
                )
            )
        }
        put("iconButton") { _, _, args ->
            val props = props(args, 0)
            wrap(UiNode.IconButton(strProp(props, "icon", "more"), reg(props["onClick"]), boolProp(props, "enabled", true)))
        }
        put("textField") { _, _, args ->
            val props = props(args, 0)

            wrap(
                UiNode.TextField(
                    strProp(props, "value", ""),
                    registerPersistentCallback(props["onChange"] ?: props["onValueChange"]),
                    strProp(props, "label", ""),
                    boolProp(props, "singleLine", true),
                    boolProp(props, "fillMaxWidth", true),
                )
            )
        }
        put("switch") { _, _, args ->
            val props = props(args, 0)
            wrap(
                UiNode.Switch(
                    boolProp(props, "checked", false),
                    reg(props["onChange"] ?: props["onCheckedChange"]),
                    strProp(props, "title", ""),
                    strProp(props, "subtitle", ""),
                    boolProp(props, "enabled", true),
                )
            )
        }
        put("slider") { _, _, args ->
            val props = props(args, 0)
            wrap(
                UiNode.Slider(
                    floatProp(props, "value", 0f),
                    reg(props["onChange"] ?: props["onValueChange"]),
                    floatProp(props, "min", 0f),
                    floatProp(props, "max", 100f),
                    strProp(props, "title", ""),
                    intProp(props, "steps", 0),
                )
            )
        }
        put("linearProgress") { _, _, args ->
            val props = props(args, 0)
            val p = props["progress"]
            wrap(UiNode.LinearProgress(if (p == null || p == Context.getUndefinedValue()) null else toFloat(p), boolProp(props, "fillMaxWidth", true)))
        }
        put("circularProgress") { _, _, args ->
            val props = props(args, 0)
            val p = props["progress"]
            wrap(UiNode.CircularProgress(if (p == null || p == Context.getUndefinedValue()) null else toFloat(p)))
        }
        put("divider") { _, _, args -> wrap(UiNode.Divider(b(args, "vertical", false))) }
        put("spacer") { _, _, args ->
            val props = props(args, 0)
            wrap(UiNode.Spacer(floatProp(props, "height", 8f), floatProp(props, "width", 0f)))
        }
        put("icon") { _, _, args ->
            val props = props(args, 0)
            val name = if (args.isNotEmpty() && args[0] is CharSequence) str(args, 0, "extension") else strProp(props, "name", "extension")
            wrap(UiNode.Icon(name, floatProp(props, "size", 24f), strPropOrNull(props, "tint", null)))
        }
        put("chip") { _, _, args ->
            val props = props(args, 0)
            wrap(UiNode.Chip(strProp(props, "text", ""), boolProp(props, "selected", false), reg(props["onClick"])))
        }
        put("materialCard") { _, _, args ->
            val props = props(args, 0)
            val kids = childrenList(props["children"] ?: args.getOrNull(1))
            val idx = props["segmentedIndex"]
            val cnt = props["segmentedCount"]
            wrap(
                UiNode.MaterialCard(
                    kids,
                    floatProp(props, "contentPadding", 16f),
                    if (idx == null) null else toInt(idx),
                    if (cnt == null) null else toInt(cnt),
                    reg(props["onClick"]),
                )
            )
        }
        put("MaterialCard") { c, s, a -> (ScriptableObject.getProperty(ui, "materialCard") as BaseFunction).call(c, s, ui, a) }
        put("functionRow") { _, _, args ->
            val props = props(args, 0)
            wrap(
                UiNode.FunctionRow(
                    strProp(props, "title", ""),
                    strProp(props, "description", ""),
                    strProp(props, "icon", "extension"),
                    strPropOrNull(props, "iconTint", null),
                    reg(props["onClick"]),
                )
            )
        }
        put("segmentedList") { _, _, args ->
            wrap(UiNode.SegmentedList(childrenOf(args), f(args, "spacing", 4f)))
        }
        put("settingsRow") { _, _, args ->
            val props = props(args, 0)
            wrap(
                UiNode.SettingsRow(
                    strProp(props, "title", ""),
                    strProp(props, "subtitle", ""),
                    strPropOrNull(props, "icon", null),
                    reg(props["onClick"]),
                    nodeProp(props, "trailing"),
                )
            )
        }
        put("webView") { _, _, args ->
            val props = props(args, 0)
            wrap(
                UiNode.WebView(
                    strProp(props, "url", ""),
                    strProp(props, "html", ""),
                    boolProp(props, "fillMaxSize", true),
                    floatProp(props, "height", 320f),
                )
            )
        }
        put("WebView") { c, s, a -> (ScriptableObject.getProperty(ui, "webView") as BaseFunction).call(c, s, ui, a) }
        put("logPanel") { _, _, args ->
            val props = props(args, 0)
            val text = if (args.isNotEmpty() && args[0] is CharSequence) str(args, 0, "") else strProp(props, "text", "")
            wrap(UiNode.LogPanel(text, floatProp(props, "maxHeight", 200f)))
        }
        put("alertDialog") { _, _, args ->
            val props = props(args, 0)
            val buttons = mutableListOf<UiNode.DialogButton>()
            val rawButtons = props["buttons"]
            if (rawButtons is Scriptable) {
                val len = try {
                    (ScriptableObject.getProperty(rawButtons, "length") as? Number)?.toInt() ?: 0
                } catch (_: Exception) {
                    0
                }
                for (i in 0 until len) {
                    val item = ScriptableObject.getProperty(rawButtons, i)
                    if (item is Scriptable) {
                        val text = ScriptableObject.getProperty(item, "text")?.toString()
                            ?: ScriptableObject.getProperty(item, "label")?.toString()
                            ?: "OK"
                        val style = ScriptableObject.getProperty(item, "style")?.toString() ?: "text"
                        val onClick = ScriptableObject.getProperty(item, "onClick")
                        buttons += UiNode.DialogButton(text, style, reg(onClick))
                    }
                }
            }
            wrap(
                UiNode.AlertDialog(
                    show = boolProp(props, "show", false),
                    title = strProp(props, "title", ""),
                    message = strProp(props, "message", strProp(props, "text", "")),
                    confirmText = strProp(props, "confirmText", "OK"),
                    dismissText = strProp(props, "dismissText", "Отмена"),
                    onConfirmId = reg(props["onConfirm"]),
                    onDismissId = reg(props["onDismiss"]),
                    buttons = buttons,
                    cancelable = boolProp(props, "cancelable", true),
                )
            )
        }
        return ui
    }

    private val nodeBag = ConcurrentHashMap<String, UiNode>()

    fun unwrap(value: Any?): UiNode {
        when (value) {
            is UiNode -> return value
            is UiNodeHolder -> return value.node
            is Scriptable -> {
                val id = ScriptableObject.getProperty(value, "__nodeId")
                if (id is String) {
                    nodeBag[id]?.let { return it }
                }
                val n = ScriptableObject.getProperty(value, "__uiNode")
                if (n is UiNode) return n

                if (n != null && n != Scriptable.NOT_FOUND && n !is org.mozilla.javascript.UniqueTag) {
                    try {
                        if (n.javaClass.name.contains("UiNode") || n is UiNode) {
                            @Suppress("UNCHECKED_CAST")
                            return n as UiNode
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return UiNode.Empty
    }

    private fun wrap(node: UiNode): Any {
        val c = cx
        val s = scope
        if (c == null || s == null) return UiNodeHolder(node)
        val id = "n_${seq.incrementAndGet()}"
        nodeBag[id] = node
        val obj = c.newObject(s)
        ScriptableObject.putProperty(obj, "__nodeId", id)

        ScriptableObject.putProperty(obj, "__uiNode", node)
        return obj
    }

    private fun reg(fn: Any?): String? {
        if (fn is org.mozilla.javascript.Function) {
            val id = "cb_${seq.incrementAndGet()}"
            callbacks[id] = fn
            return id
        }
        return null
    }

    private fun topBarFrom(props: Map<String, Any?>): UiNode.TopBar? {
        if (props.isEmpty()) return null
        val title = strPropOrNull(props, "title", null) ?: return null
        val actionsRaw = props["actions"]
        val actions = mutableListOf<UiNode.TopBarAction>()
        if (actionsRaw is NativeArray) {
            for (i in 0 until actionsRaw.length.toInt()) {
                val item = actionsRaw.get(i, actionsRaw)
                if (item is Scriptable) {
                    actions += UiNode.TopBarAction(
                        strProp(scriptableToMap(item), "icon", "more"),
                        reg(ScriptableObject.getProperty(item, "onClick")),
                    )
                }
            }
        }
        return UiNode.TopBar(title, boolProp(props, "showBack", true), actions)
    }

    private fun childrenOf(args: Array<Any?>): List<UiNode> {
        if (args.isEmpty()) return emptyList()

        val first = args[0]
        if (first is NativeArray) return childrenList(first)
        if (first is Scriptable && isPlainObject(first) && args.size >= 2) {

            val second = args[1]
            return when {
                second is NativeArray -> childrenList(second)
                args.size > 2 -> args.drop(1).mapNotNull { nodeFrom(it) }
                else -> listOfNotNull(nodeFrom(second))
            }
        }

        if (args.size == 1 && first is Scriptable && isPlainObject(first)) {

            val map = scriptableToMap(first)
            if (map.containsKey("children")) return childrenList(map["children"])
        }
        return args.mapNotNull { nodeFrom(it) }
    }

    private fun childrenList(raw: Any?): List<UiNode> {
        return when (raw) {
            is NativeArray -> (0 until raw.length.toInt()).mapNotNull { nodeFrom(raw.get(it, raw)) }
            is Array<*> -> raw.mapNotNull { nodeFrom(it) }
            is List<*> -> raw.mapNotNull { nodeFrom(it) }
            else -> listOfNotNull(nodeFrom(raw))
        }
    }

    private fun nodeFrom(v: Any?): UiNode? {
        if (v == null || v == Context.getUndefinedValue()) return null
        return unwrap(v).takeIf { it !is UiNode.Empty }
    }

    private fun nodeAt(args: Array<Any?>, index: Int): UiNode? =
        if (index < args.size) nodeFrom(args[index]) else null

    private fun nodeProp(props: Map<String, Any?>, key: String): UiNode? = nodeFrom(props[key])

    private fun props(args: Array<Any?>, index: Int): Map<String, Any?> = anyObjectMap(args, index)


    private fun anyObjectMap(args: Array<Any?>, index: Int): Map<String, Any?> {
        if (index >= args.size) return emptyMap()
        val v = args[index]
        if (v is Scriptable && isPlainObject(v)) return scriptableToMap(v)
        return emptyMap()
    }





    private fun isPlainObject(s: Scriptable): Boolean {
        if (s is org.mozilla.javascript.Function) return false
        if (s is NativeArray) return false

        val nodeId = ScriptableObject.getProperty(s, "__nodeId")
        if (nodeId != null && nodeId != Scriptable.NOT_FOUND && nodeId != Context.getUndefinedValue()) {
            return false
        }
        val uiNode = ScriptableObject.getProperty(s, "__uiNode")
        if (uiNode is UiNode) return false

        return ScriptableObject.getPropertyIds(s).isNotEmpty()
    }

    private fun isProps(s: Scriptable): Boolean = isPlainObject(s)

    private fun scriptableToMap(s: Scriptable): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (id in ScriptableObject.getPropertyIds(s)) {
            val key = id.toString()
            map[key] = ScriptableObject.getProperty(s, key)
        }
        return map
    }

    private fun f(args: Array<Any?>, key: String, def: Float): Float {
        val p = props(args, 0)
        return floatProp(p, key, def)
    }

    private fun b(args: Array<Any?>, key: String, def: Boolean): Boolean {
        val p = props(args, 0)
        return boolProp(p, key, def)
    }

    private fun str(args: Array<Any?>, i: Int, def: String): String {
        if (i >= args.size || args[i] == null || args[i] == Context.getUndefinedValue()) return def
        return Context.toString(args[i])
    }

    private fun strPropOrNull(p: Map<String, Any?>, k: String, def: String?): String? {
        val v = p[k] ?: return def
        if (v == Context.getUndefinedValue()) return def
        return Context.toString(v)
    }

    private fun strProp(p: Map<String, Any?>, k: String, def: String): String = strPropOrNull(p, k, def) ?: def

    private fun boolProp(p: Map<String, Any?>, k: String, def: Boolean): Boolean {
        val v = p[k] ?: return def
        if (v == Context.getUndefinedValue()) return def
        return Context.toBoolean(v)
    }

    private fun floatProp(p: Map<String, Any?>, k: String, def: Float): Float {
        val v = p[k] ?: return def
        if (v == Context.getUndefinedValue()) return def
        return toFloat(v)
    }

    private fun intProp(p: Map<String, Any?>, k: String, def: Int): Int {
        val v = p[k] ?: return def
        if (v == Context.getUndefinedValue()) return def
        return toInt(v)
    }

    private fun toFloat(v: Any): Float = when (v) {
        is Number -> v.toFloat()
        else -> Context.toNumber(v).toFloat()
    }

    private fun toInt(v: Any): Int = when (v) {
        is Number -> v.toInt()
        else -> Context.toNumber(v).toInt()
    }

    private class UiNodeHolder(val node: UiNode)
}
