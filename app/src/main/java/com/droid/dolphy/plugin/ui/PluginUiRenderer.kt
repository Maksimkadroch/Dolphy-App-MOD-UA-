package com.droid.dolphy.plugin.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.droid.dolphy.DolphySlider
import com.droid.dolphy.DolphySwitch
import com.droid.dolphy.MaterialButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.getSegmentedShape
import com.droid.dolphy.plugin.PluginIcons
import com.droid.dolphy.plugin.model.UiNode

@Composable
fun PluginUiRenderer(
    node: UiNode,
    accent: Color,
    onBack: () -> Unit,
    onCallback: (String?, Any?) -> Unit,
) {
    when (node) {
        is UiNode.Scaffold -> {
            Column(Modifier.fillMaxSize()) {
                val bar = node.topBar
                if (bar != null) {
                    SectionTopBar(
                        title = bar.title,
                        onBack = if (bar.showBack) onBack else null,
                        accentColor = accent,
                        actions = {
                            bar.actions.forEach { action ->
                                IconButton(onClick = { onCallback(action.onClickId, null) }) {
                                    Icon(
                                        PluginIcons.resolve(action.icon),
                                        contentDescription = null,
                                        tint = accent,
                                    )
                                }
                            }
                        },
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (val c = node.content) {
                        is UiNode.LazyColumn -> {

                            LazyColumn(
                                Modifier
                                    .fillMaxSize()
                                    .padding(c.padding.dp),
                                verticalArrangement = Arrangement.spacedBy(c.spacing.dp),
                                contentPadding = PaddingValues(bottom = 120.dp),
                            ) {
                                itemsIndexed(
                                    items = c.children,
                                    key = { index, _ -> index },
                                ) { _, child ->
                                    PluginUiRenderer(child, accent, onBack, onCallback)
                                }
                            }
                        }
                        is UiNode.Column -> {
                            val scroll = rememberScrollState()
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scroll)
                                    .padding(bottom = 120.dp)
                                    .padding(c.padding.dp),
                                verticalArrangement = Arrangement.spacedBy(c.spacing.dp),
                            ) {
                                c.children.forEach { child ->
                                    PluginUiRenderer(child, accent, onBack, onCallback)
                                }
                            }
                        }
                        else -> {
                            val scroll = rememberScrollState()
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scroll)
                                    .padding(bottom = 120.dp),
                            ) {
                                PluginUiRenderer(c, accent, onBack, onCallback)
                            }
                        }
                    }
                }
                node.fab?.let { fab ->
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                        PluginUiRenderer(fab, accent, onBack, onCallback)
                    }
                }
            }
        }

        is UiNode.Column -> {
            val scroll = rememberScrollState()
            val mod = Modifier
                .then(
                    if (node.fillMaxSize) {
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(bottom = 120.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .padding(node.padding.dp)
            Column(mod, verticalArrangement = Arrangement.spacedBy(node.spacing.dp)) {
                node.children.forEach { PluginUiRenderer(it, accent, onBack, onCallback) }
            }
        }

        is UiNode.Row -> {
            Row(
                Modifier
                    .then(if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                    .padding(node.padding.dp),
                horizontalArrangement = Arrangement.spacedBy(node.spacing.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                node.children.forEach { PluginUiRenderer(it, accent, onBack, onCallback) }
            }
        }

        is UiNode.Box -> {
            Box(
                Modifier
                    .then(if (node.fillMaxSize) Modifier.fillMaxSize() else Modifier)
                    .padding(node.padding.dp)
            ) {
                node.children.forEach { PluginUiRenderer(it, accent, onBack, onCallback) }
            }
        }

        is UiNode.LazyColumn -> {
            LazyColumn(
                Modifier
                    .then(if (node.fillMaxSize) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .padding(node.padding.dp),
                verticalArrangement = Arrangement.spacedBy(node.spacing.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                itemsIndexed(node.children) { _, child ->
                    PluginUiRenderer(child, accent, onBack, onCallback)
                }
            }
        }

        is UiNode.Text -> {
            Text(
                text = node.text,
                style = textStyle(node.style),
                color = parseColor(node.color) ?: textColor(node.style),
                maxLines = node.maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }

        is UiNode.Button -> {
            val mod = if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier
            when (node.style.lowercase()) {
                "outlined" -> OutlinedButton(
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                ) { Text(node.text) }
                "text" -> TextButton(
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                ) { Text(node.text) }
                "tonal" -> FilledTonalButton(
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                ) { Text(node.text) }
                "material" -> MaterialButton(
                    text = node.text,
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                    accentColor = accent,
                )
                else -> Button(
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                ) { Text(node.text) }
            }
        }

        is UiNode.IconButton -> {
            IconButton(onClick = { onCallback(node.onClickId, null) }, enabled = node.enabled) {
                Icon(PluginIcons.resolve(node.icon), null, tint = accent)
            }
        }

        is UiNode.TextField -> {



            var text by remember(node.label, node.singleLine) { mutableStateOf(node.value) }
            LaunchedEffect(node.value) {

                if (node.value != text) {
                    text = node.value
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new
                    onCallback(node.onChangeId, new)
                },
                label = if (node.label.isNotBlank()) ({ Text(node.label) }) else null,
                singleLine = node.singleLine,
                modifier = if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            )
        }

        is UiNode.Switch -> {
            if (node.title.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(node.title, style = MaterialTheme.typography.bodyLarge)
                        if (node.subtitle.isNotBlank()) {
                            Text(node.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
                        }
                    }
                    DolphySwitch(
                        checked = node.checked,
                        onCheckedChange = { onCallback(node.onChangeId, it) },
                        enabled = node.enabled,
                    )
                }
            } else {
                DolphySwitch(
                    checked = node.checked,
                    onCheckedChange = { onCallback(node.onChangeId, it) },
                    enabled = node.enabled,
                )
            }
        }

        is UiNode.Slider -> {
            Column(Modifier.fillMaxWidth()) {
                if (node.title.isNotBlank()) {
                    Text(node.title, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                }
                DolphySlider(
                    value = node.value.coerceIn(node.min, node.max),
                    onValueChange = { onCallback(node.onChangeId, it) },
                    valueRange = node.min..node.max,
                    steps = node.steps,
                )
            }
        }

        is UiNode.LinearProgress -> {
            if (node.progress == null) {
                LinearProgressIndicator(Modifier.then(if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier))
            } else {
                LinearProgressIndicator(
                    progress = { node.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.then(if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier),
                )
            }
        }

        is UiNode.CircularProgress -> {
            if (node.progress == null) CircularProgressIndicator()
            else CircularProgressIndicator(progress = { node.progress.coerceIn(0f, 1f) })
        }

        is UiNode.Divider -> HorizontalDivider(color = Color.White.copy(0.12f))
        is UiNode.Spacer -> Spacer(Modifier.height(node.height.dp).width(node.width.dp))
        is UiNode.Icon -> Icon(
            PluginIcons.resolve(node.name),
            null,
            modifier = Modifier.size(node.size.dp),
            tint = parseColor(node.tint) ?: accent,
        )

        is UiNode.Chip -> {
            FilterChip(
                selected = node.selected,
                onClick = { onCallback(node.onClickId, null) },
                label = { Text(node.text) },
            )
        }

        is UiNode.MaterialCard -> {
            val shape = if (node.segmentedIndex != null && node.segmentedCount != null) {
                getSegmentedShape(node.segmentedIndex, node.segmentedCount)
            } else {
                RoundedCornerShape(28.dp)
            }
            MaterialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (node.onClickId != null) Modifier.clickable { onCallback(node.onClickId, null) }
                        else Modifier
                    ),
                accentColor = accent,
                shape = shape,
                contentPadding = node.contentPadding.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    node.children.forEach { PluginUiRenderer(it, accent, onBack, onCallback) }
                }
            }
        }

        is UiNode.FunctionRow -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = node.onClickId != null) { onCallback(node.onClickId, null) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = parseColor(node.iconTint) ?: accent
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PluginIcons.resolve(node.icon), null, tint = tint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(node.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (node.description.isNotBlank()) {
                        Text(node.description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
                    }
                }
            }
        }

        is UiNode.SegmentedList -> {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(node.spacing.dp)) {
                val count = node.children.size
                node.children.forEachIndexed { index, child ->

                    if (child is UiNode.MaterialCard && child.segmentedIndex == null) {
                        PluginUiRenderer(
                            child.copy(segmentedIndex = index, segmentedCount = count),
                            accent, onBack, onCallback,
                        )
                    } else {
                        PluginUiRenderer(child, accent, onBack, onCallback)
                    }
                }
            }
        }

        is UiNode.SettingsRow -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = node.onClickId != null) { onCallback(node.onClickId, null) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (node.icon != null) {
                    Icon(PluginIcons.resolve(node.icon), null, tint = accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(node.title, style = MaterialTheme.typography.bodyLarge)
                    if (node.subtitle.isNotBlank()) {
                        Text(node.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
                    }
                }
                node.trailing?.let { PluginUiRenderer(it, accent, onBack, onCallback) }
            }
        }

        is UiNode.WebView -> PluginWebView(node)

        is UiNode.LogPanel -> {
            val scroll = rememberScrollState()

            val panelHeight = node.maxHeight.coerceAtLeast(280f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(panelHeight.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(0.35f))
                    .border(1.dp, accent.copy(0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .verticalScroll(scroll),
            ) {
                Text(
                    node.text.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFFB2FF59),
                )
            }
            LaunchedEffect(node.text) {

                scroll.scrollTo(scroll.maxValue)
            }
        }

        is UiNode.AlertDialog -> {
            if (node.show) {
                PluginAlertDialogContent(
                    title = node.title,
                    message = node.message,
                    buttons = node.buttons.ifEmpty {
                        listOf(
                            UiNode.DialogButton(node.confirmText, "filled", node.onConfirmId),
                            UiNode.DialogButton(node.dismissText, "text", node.onDismissId),
                        )
                    },
                    cancelable = node.cancelable,
                    onDismiss = { onCallback(node.onDismissId, null) },
                    onButton = { id -> onCallback(id, null) },
                )
            }
        }

        is UiNode.Empty -> {}
        is UiNode.TopBar -> {

            SectionTopBar(title = node.title, onBack = if (node.showBack) onBack else null, accentColor = accent)
        }
    }
}

@Composable
fun PluginAlertDialogContent(
    title: String,
    message: String,
    buttons: List<UiNode.DialogButton>,
    cancelable: Boolean,
    onDismiss: () -> Unit,
    onButton: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (cancelable) onDismiss()
        },
        title = if (title.isNotBlank()) {
            { Text(title, fontWeight = FontWeight.SemiBold) }
        } else null,
        text = if (message.isNotBlank()) {
            { Text(message) }
        } else null,
        confirmButton = {
            val primary = buttons.firstOrNull()
            if (primary != null) {
                DialogActionButton(primary) { onButton(primary.onClickId) }
            }
        },
        dismissButton = {
            if (buttons.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    buttons.drop(1).forEach { btn ->
                        DialogActionButton(btn) { onButton(btn.onClickId) }
                    }
                }
            }
        },
    )
}

@Composable
private fun DialogActionButton(btn: UiNode.DialogButton, onClick: () -> Unit) {
    when (btn.style.lowercase()) {
        "filled", "material" -> {
            Button(onClick = onClick) { Text(btn.text) }
        }
        "tonal" -> {
            FilledTonalButton(onClick = onClick) { Text(btn.text) }
        }
        "outlined" -> {
            OutlinedButton(onClick = onClick) { Text(btn.text) }
        }
        "destructive" -> {
            TextButton(onClick = onClick) {
                Text(btn.text, color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            TextButton(onClick = onClick) { Text(btn.text) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PluginWebView(node: UiNode.WebView) {
    val mod = if (node.fillMaxSize) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(node.height.dp)
    AndroidView(
        modifier = mod.clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                if (node.html.isNotBlank()) {
                    loadDataWithBaseURL(null, node.html, "text/html", "utf-8", null)
                } else if (node.url.isNotBlank()) {
                    loadUrl(node.url)
                }
            }
        },
        update = { web ->
            if (node.html.isNotBlank()) {
                web.loadDataWithBaseURL(null, node.html, "text/html", "utf-8", null)
            } else if (node.url.isNotBlank() && web.url != node.url) {
                web.loadUrl(node.url)
            }
        },
    )
}

@Composable
private fun textStyle(style: String) = when (style.lowercase()) {
    "headlinelarge", "headline_large" -> MaterialTheme.typography.headlineLarge
    "headlinemedium", "headline_medium" -> MaterialTheme.typography.headlineMedium
    "headlinesmall", "headline_small" -> MaterialTheme.typography.headlineSmall
    "titlelarge", "title_large" -> MaterialTheme.typography.titleLarge
    "titlemedium", "title_medium" -> MaterialTheme.typography.titleMedium
    "titlesmall", "title_small" -> MaterialTheme.typography.titleSmall
    "bodylarge", "body_large" -> MaterialTheme.typography.bodyLarge
    "bodysmall", "body_small" -> MaterialTheme.typography.bodySmall
    "labelsmall", "label_small" -> MaterialTheme.typography.labelSmall
    "labelmedium", "label_medium" -> MaterialTheme.typography.labelMedium
    "labellarge", "label_large" -> MaterialTheme.typography.labelLarge
    else -> MaterialTheme.typography.bodyMedium
}

@Composable
private fun textColor(style: String): Color {
    return when {
        style.contains("label", true) -> Color.White.copy(0.6f)
        else -> MaterialTheme.colorScheme.onBackground
    }
}

private fun parseColor(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    return try {
        when (raw.lowercase()) {
            "error", "red" -> Color(0xFFFF5252)
            "primary", "accent" -> null
            "muted", "secondary" -> Color.White.copy(0.6f)
            "success", "green" -> Color(0xFF69F0AE)
            "warning", "orange" -> Color(0xFFFFAB40)
            else -> Color(AndroidColor.parseColor(if (raw.startsWith("#")) raw else "#$raw"))
        }
    } catch (_: Exception) {
        null
    }
}
