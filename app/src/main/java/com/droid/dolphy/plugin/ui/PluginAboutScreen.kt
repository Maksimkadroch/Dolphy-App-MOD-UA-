package com.droid.dolphy.plugin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.plugin.PluginIcons

@Composable
fun PluginAboutScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = "О плагинах",
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
            ) {
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Руководство автора",
                        body = "Плагин - это файл .js. Импорт: Настройки -> Общие -> Плагины -> Выбрать файл.\n\n" +
                            "Можно собирать экраны, карточки в Другое, Wi-Fi, BLE, NFC, IR, HTTP, " +
                            "WebView, Root, Shizuku, shell и prefs.",
                    )
                }

                item { SectionTitle("1. Каркас файла") }
                item {
                    CodeBlock(
                        """
                        // @plugin id=my_tool name="My Tool" version=1.0
                        // @description Demo author=you

                        function onLoad(api) {
                          // карточка в Другое
                          api.other.add({ ... });
                        }

                        function screen_main(ui, api, state) {
                          return ui.scaffold({
                            topBar: { title: "My Tool", showBack: true },
                            content: ui.column({ padding: 16, spacing: 12 }, [
                              ui.text("Привет", { style: "headlineSmall" }),
                              ui.button({ text: "Жми", onClick: function () {
                                api.toast("OK");
                              }})
                            ])
                          });
                        }

                        // опционально:
                        function onUnload(api) { /* cleanup, stopScan */ }
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("2. Разделы экрана Другое") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Куда попадёт карточка",
                        body = "Четыре встроенных раздела:\n\n" +
                            "• INFRARED - инфракрасные инструменты\n" +
                            "• BLUETOOTH - Bluetooth, BLE, HID\n" +
                            "• ПРОЧЕЕ - NFC, QR, Wi-Fi, LAN\n" +
                            "• ПЛАГИНЫ - карточки плагинов\n\n" +
                            "Поле section в api.other.add выбирает раздел. " +
                            "Алиасы: infrared/ir, bluetooth/ble, other/прочее, plugins/плагины. " +
                            "Любая другая строка создаёт свою группу с этим названием.",
                    )
                }
                item {
                    CodeBlock(
                        """
                        // Встроенные (алиас = тот же раздел):
                        section: "infrared"   // или "ir", "INFRARED"
                        section: "bluetooth"  // или "bt", "ble", "BLUETOOTH"
                        section: "other"      // или "прочее", "ПРОЧЕЕ"
                        section: "plugins"    // или "плагины", "ПЛАГИНЫ" (по умолчанию)

                        // Своя группа (любая другая строка = новый заголовок):
                        section: "Root Tools"
                        section: "Мои утилиты"
                        section: "Home Lab"
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Extension,
                        title = "My Tool",
                        description = "Пример карточки в выбранном разделе",
                        code = """
                            api.other.add({
                              section: "bluetooth",
                              title: "My Tool",
                              description: "Короткое описание под заголовком",
                              icon: "bluetooth_searching",
                              screen: "main",
                              color: 0xFF7C4DFF,
                              order: 0
                            });

                            // Несколько карточек / экранов:
                            api.other.add({
                              section: "Root Tools",
                              title: "Shell Lab",
                              icon: "terminal",
                              screen: "shell"
                            });
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("3. Иконка карточки в Другое") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Поле icon",
                        body = "Строковое имя иконки. Неизвестный ключ даёт extension.\n" +
                            "Цвет: color как 0xAARRGGBB, например 0xFF4CAF50.",
                    )
                }
                item {
                    CodeBlock(
                        "Доступные имена icon:\n" +
                            PluginIcons.knownNames.joinToString(", "),
                    )
                }
                item {
                    CodeBlock(
                        """
                        api.other.add({
                          section: "plugins",
                          title: "Wi-Fi Tool",
                          icon: "wifi",           // имя
                          color: 0xFF4CAF50,      // зелёный
                          screen: "main"
                        });
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("4. UI-компоненты") }
                item {
                    CodeBlock(
                        """
                        ui.scaffold({ topBar: { title, showBack, actions }, content, fab })
                        ui.column / ui.row / ui.box / ui.lazyColumn
                        ui.text(text, { style, color, maxLines })
                        ui.button({ text, style, onClick, fillMaxWidth, enabled })
                          // style: filled | outlined | text | tonal | material
                        ui.iconButton / ui.textField / ui.switch / ui.slider / ui.chip
                        ui.linearProgress / ui.circularProgress
                        ui.spacer / ui.divider / ui.icon
                        ui.materialCard({ contentPadding, segmentedIndex, segmentedCount, onClick }, children)
                        ui.functionRow({ title, description, icon, iconTint, onClick })
                        ui.segmentedList / ui.settingsRow / ui.logPanel / ui.alertDialog
                        ui.webView
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("5. Диалоги (api.dialog)") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Модальные окна",
                        body = "Императивный API: api.dialog.show / api.dialog.dismiss.\n" +
                            "Не зависит от setState. Заголовок, текст и произвольный список кнопок.\n" +
                            "Стиль кнопки: filled | tonal | outlined | text | destructive.\n\n" +
                            "Альтернатива в дереве UI: ui.alertDialog({ show, title, message, buttons }).",
                    )
                }
                item {
                    CodeBlock(
                        """
                        // Рекомендуемый способ:
                        api.dialog.show({
                          title: "Подтверждение",
                          message: "Удалить запись?",
                          cancelable: true,
                          buttons: [
                            { text: "Удалить", style: "destructive", onClick: function () {
                              api.toast("Удалено");
                            }},
                            { text: "Отмена", style: "text", onClick: function () {} }
                          ]
                        });

                        // Короткий вариант (OK / Отмена):
                        api.dialog.show({
                          title: "Готово",
                          text: "Операция завершена",
                          confirmText: "OK",
                          onConfirm: function () { api.toast("OK"); },
                          dismissText: "Закрыть",
                          onDismiss: function () {}
                        });

                        api.dialog.dismiss();

                        // В UI-дереве:
                        ui.alertDialog({
                          show: state.dlg === true,
                          title: "Заголовок",
                          message: "Текст",
                          buttons: [
                            { text: "Да", style: "filled", onClick: function () {
                              api.setState({ dlg: false });
                            }},
                            { text: "Нет", style: "text", onClick: function () {
                              api.setState({ dlg: false });
                            }}
                          ]
                        })
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("6. WebView") }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Language,
                        title = "WebView",
                        description = "Встроенный браузерный виджет на экране плагина",
                        code = """
                            // По URL:
                            ui.webView({
                              url: "https://example.com",
                              height: 360,          // dp, если не на весь экран
                              fillMaxSize: false
                            })

                            // Или HTML-строка:
                            ui.webView({
                              html: "<html><body style='background:#111;color:#0f0'>" +
                                    "<h3>Hello from plugin</h3></body></html>",
                              height: 240
                            })

                            // Пример экрана:
                            function screen_main(ui, api, state) {
                              return ui.scaffold({
                                topBar: { title: "Browser", showBack: true },
                                content: ui.column({ padding: 12, spacing: 8, fillMaxSize: true }, [
                                  ui.textField({
                                    value: state.url || "https://example.com",
                                    label: "URL",
                                    onChange: function (v) { api.setState({ url: v }); }
                                  }),
                                  ui.button({ text: "Открыть", onClick: function () {
                                    api.setState({ open: true });
                                  }}),
                                  (state.open
                                    ? ui.webView({ url: state.url, fillMaxSize: true, height: 480 })
                                    : ui.text("Введите URL", { color: "muted" }))
                                ])
                              });
                            }
                        """.trimIndent(),
                    )
                }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Замечания по WebView",
                        body = "• В странице включён JavaScript.\n" +
                            "• Это не полный браузер, без системной адресной строки.\n" +
                            "• Локальный UI удобно отдавать через html.\n" +
                            "• Для URL нужен доступ в сеть.",
                    )
                }

                item { SectionTitle("7. Material-карточка на экране") }
                item {
                    MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Заголовок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Текст внутри MaterialCard Dolphy", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f))
                            Button(onClick = {}) { Text("Кнопка") }
                        }
                    }
                }
                item {
                    CodeBlock(
                        """
                        ui.materialCard({ contentPadding: 16 }, [
                          ui.text("Заголовок", { style: "titleMedium" }),
                          ui.text("Текст...", { style: "bodySmall", color: "muted" }),
                          ui.button({ text: "Кнопка", onClick: function () {} })
                        ])
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("8. Device / Android API (host)") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Покрытие Android API приложения",
                        body = "Плагины получают host-обёртки всех системных API, которые использует Dolphy: " +
                            "Wi-Fi, BLE/BT, IR, NFC, сеть, USB, location, sensors, audio, clipboard, " +
                            "vibrator, notifications, intents, файлы, root/shizuku, package features. " +
                            "Права = права приложения. Compose/UI-фреймворк не пробрасывается: UI через ui.*.",
                    )
                }
                item {
                    CodeBlock(
                        """
                        // Core
                        api.setState / api.getState / api.toast / api.log / api.navigate
                        api.dialog.show / dismiss
                        api.prefs.get / set
                        api.pluginId / api.pluginName
                        api.app.open("other/nfc_tools")  // встроенные экраны Dolphy
                        api.app.packageName

                        // Wi-Fi
                        api.wifi.isEnabled / startScan([force]) / scan(cb|opts)
                        api.wifi.getScanResults([max, minRssi]) / connectionInfo
                        api.wifi.openSettings / disconnect / reconnect
                        api.wifi.addSuggestion(ssid, passphrase?)  // API 29+

                        // BLE
                        api.ble.isEnabled / startScan(cb|opts) / stopScan
                        api.ble.advertise({ manufacturerId, payloadHex, connectable })
                        api.ble.stopAdvertise
                        api.ble.connect(addr, onEvent) / disconnect(addr)
                        api.ble.write(addr, serviceUuid, charUuid, hex)
                        api.ble.read(addr, serviceUuid, charUuid)

                        // Classic Bluetooth
                        api.bt.isEnabled / name / address / bondedDevices
                        api.bt.startDiscovery(cb) / stopDiscovery / openSettings

                        // NFC / IR
                        api.nfc.isAvailable / isEnabled / openSettings / openTools
                        api.ir.hasEmitter / carrierFrequencies / status
                        api.ir.transmit(freqHz, [mark,space,...])  // ConsumerIr
                        api.ir.toggleStorm / toggleJammer / openRemotes

                        // Network
                        api.net.active / detail / interfaces
                        api.net.http(method, url, body, headers, cb)
                        api.net.tcpReachable(host, port, timeout, cb)
                        api.net.nsdDiscover(type, cb) / nsdStop

                        // Shell
                        api.root.available / exec(cmd, cb)
                        api.shizuku.available / hasPermission / requestPermission / exec
                        api.shell.exec(cmd, cb)  // Shizuku -> root

                        // Platform
                        api.clipboard.get / set(text)
                        api.vibrator.vibrate(ms) / pattern([..], repeat) / cancel
                        api.notify.show(id, title, text) / cancel(id)
                        api.intent.openUrl / shareText / openSettings(kind) / start / dial
                        api.files.list / read / write / delete / exists / readBase64 / writeBase64
                        api.usb.devices
                        api.location.isEnabled / last / openSettings
                        api.sensors.list / start(type, cb) / stop(id) / stopAll
                        api.audio.playUrl / playFile / stop / tone(type, ms)
                        api.pm.hasPermission / hasFeature / features
                        api.device.info / battery / features
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("9. Пример: BLE") }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Bluetooth,
                        title = "BLE Scanner",
                        description = "section: bluetooth + scan",
                        code = """
                            api.other.add({
                              section: "bluetooth",
                              title: "BLE Scanner",
                              icon: "bluetooth_searching",
                              screen: "main"
                            });

                            api.ble.startScan(function (d) {
                              var list = (api.getState().devices) || [];
                              list = list.concat([d]); // d.name, d.address, d.rssi
                              api.setState({ devices: list });
                            });
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("10. Root и Shizuku") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Системный shell",
                        body = "При запуске Dolphy запрашивает Shizuku (если сервис запущен) и root (su -c id, диалог Magisk).\n\n" +
                            "В плагине: api.root, api.shizuku, api.shell.",
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Security,
                        title = "Root",
                        description = "uid 0, Magisk / SuperSU",
                        code = """
                            if (!api.root.available()) {
                              api.toast("Root недоступен");
                              return;
                            }
                            api.root.exec("id", function (r) {
                              // r.code, r.out, r.err
                              api.setState({ log: r.out || r.err || "" });
                            });
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Terminal,
                        title = "Shizuku",
                        description = "ADB shell без root",
                        code = """
                            if (!api.shizuku.available()) {
                              api.toast("Запустите Shizuku");
                              return;
                            }
                            if (!api.shizuku.hasPermission()) {
                              api.shizuku.requestPermission();
                              return;
                            }
                            api.shizuku.exec("cmd wifi status", function (r) {
                              api.setState({ log: r.out || r.err || "" });
                            });
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Extension,
                        title = "api.shell",
                        description = "Сначала Shizuku, иначе root",
                        code = """
                            api.shell.exec("getprop ro.product.model", function (r) {
                              if (r.via === "none" || r.code === -1) {
                                api.toast("Нужен Shizuku или root");
                                return;
                              }
                              api.toast(r.out);
                            });
                        """.trimIndent(),
                    )
                }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Безопасность",
                        body = "root/shizuku.exec работают с правами приложения. Ставь только свои или проверенные .js. " +
                            "Выключатель на экране Плагины отключает плагин и его карточки.",
                    )
                }

                item { SectionTitle("11. State, lifecycle, советы") }
                item {
                    CodeBlock(
                        """
                        // Перерисовка только через setState (debounce ~120ms):
                        api.setState({ count: (state.count || 0) + 1 });

                        // BLE: копите устройства, setState пакетом (не на каждый пакет OS)
                        api.ble.startScan(function (d) {
                          var list = state.devices || [];
                          // d.name, d.address, d.rssi
                          if (!list.some(function (x) { return x.address === d.address; })) {
                            list = list.concat([d]).slice(-60);
                            api.setState({ devices: list });
                          }
                        });

                        // Wi-Fi: список через lazyColumn
                        api.wifi.scan({ maxResults: 30, callback: function (arr) {
                          api.setState({ aps: arr });
                        }});

                        // Несколько экранов:
                        function screen_main(...) { ... }
                        function screen_settings(...) { ... }
                        api.navigate("settings");

                        // Остановка BLE при выгрузке:
                        function onUnload(api) {
                          try { api.ble.stopScan(); } catch (e) {}
                        }
                        """.trimIndent(),
                    )
                }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Чеклист",
                        body = "• Уникальный id в @plugin\n" +
                            "• return ui.scaffold({ topBar, content })\n" +
                            "• section: infrared | bluetooth | other | plugins | своя группа\n" +
                            "• icon: имя из списка выше\n" +
                            "• Диалоги: api.dialog.show({ title, message, buttons })\n" +
                            "• WebView: ui.webView({ url }) или ui.webView({ html })\n" +
                            "• Списки: ui.lazyColumn для scan-результатов\n" +
                            "• Shell: api.root / api.shizuku / api.shell\n" +
                            "• Отключение сохраняется, удаление снимает плагин до нового импорта",
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(0.65f),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun GuideHeader(accent: Color, title: String, body: String) {
    MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.75f))
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(0.4f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = code,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFFB2FF59),
        )
    }
}

@Composable
private fun VisualExample(
    accent: Color,
    icon: ImageVector,
    title: String,
    description: String,
    code: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 0.dp) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = accent)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
                }
            }
        }
        Text("API:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
        CodeBlock(code)
    }
}
