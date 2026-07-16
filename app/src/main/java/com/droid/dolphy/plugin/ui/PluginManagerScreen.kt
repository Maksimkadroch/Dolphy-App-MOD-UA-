package com.droid.dolphy.plugin.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.DolphySwitch
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.plugin.PluginManager

@Composable
fun PluginManagerScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val plugins by PluginManager.plugins.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = PluginManager.installFromUri(uri)
        result.onSuccess {
            message = "Установлен: ${it.name}"
            Toast.makeText(context, "Плагин ${it.name} установлен", Toast.LENGTH_SHORT).show()
        }.onFailure {
            message = "Ошибка: ${it.message}"
            Toast.makeText(context, "Ошибка: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = "Плагины",
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
            ) {
                item {
                    MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "JS-плагины Dolphy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Файл .js: экраны, Другое, Wi-Fi, BLE, NFC, IR, WebView, root, Shizuku.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.7f),
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        picker.launch(
                                            arrayOf("*/*", "text/*", "application/javascript", "text/javascript"),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Выбрать файл")
                                }
                                OutlinedButton(
                                    onClick = { navController.navigate("plugin_about") },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Подробнее")
                                }
                            }
                            if (message != null) {
                                Text(message!!, style = MaterialTheme.typography.bodySmall, color = accent)
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Установленные",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(0.6f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    )
                }

                if (plugins.isEmpty()) {
                    item {
                        Text(
                            "Пока нет плагинов. Выберите .js файл выше.",
                            color = Color.White.copy(0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                items(plugins, key = { it.manifest.id }) { p ->
                    MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 0.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Extension, null, tint = if (p.enabled) accent else Color.White.copy(0.35f))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.manifest.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (p.enabled) Color.Unspecified else Color.White.copy(0.5f),
                                )
                                Text(
                                    "${p.manifest.id} · v${p.manifest.version}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(0.55f),
                                )
                                if (p.manifest.description.isNotBlank()) {
                                    Text(
                                        p.manifest.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(0.45f),
                                    )
                                }
                            }
                            DolphySwitch(
                                checked = p.enabled,
                                onCheckedChange = { enabled ->
                                    PluginManager.setEnabled(p.manifest.id, enabled)
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { PluginManager.deletePlugin(p.manifest.id) }) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }
        }
    }
}
