package com.droid.dolphy.plugin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.model.PluginDialogSpec
import com.droid.dolphy.plugin.model.UiNode
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun PluginHostScreen(
    pluginId: String,
    screenId: String,
    navController: NavController,
) {
    val accent = MaterialTheme.colorScheme.primary
    val session = remember(pluginId) { PluginManager.getSession(pluginId) }

    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(pluginId, session) {
        session?.requestUiRefresh = {
            tick++
        }
        session?.navigateToScreen = { target ->
            if (target.startsWith("__app__:")) {
                navController.navigate(target.removePrefix("__app__:"))
            } else {
                navController.navigate("plugin/$pluginId/$target")
            }
        }
    }

    val tree = remember(pluginId, screenId, tick, session?.getStateVersion()) {
        PluginManager.renderScreen(pluginId, screenId)
    }

    val emptyDialogFlow = remember { MutableStateFlow<PluginDialogSpec?>(null) }
    val dialog by (session?.dialog ?: emptyDialogFlow).collectAsState()

    MaterialBackground(accentColor = accent) {
        when {
            session == null -> {
                Column(Modifier.fillMaxSize()) {
                    SectionTopBar(
                        title = "Плагин",
                        onBack = { navController.popBackStack() },
                        accentColor = accent,
                    )
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Плагин «$pluginId» не установлен.\nИмпортируйте .js через Управление плагинами.",
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            else -> {
                val node = when (tree) {
                    is UiNode.Scaffold -> tree
                    is UiNode.Empty -> UiNode.Scaffold(
                        topBar = UiNode.TopBar(session.manifest.name, true),
                        content = UiNode.Column(
                            listOf(
                                UiNode.Text("Пустой экран", "titleMedium"),
                                UiNode.Text(
                                    session.lastError ?: "screen_$screenId вернул пустой UI",
                                    "bodyMedium",
                                    color = "error",
                                ),
                            ),
                            padding = 16f,
                            spacing = 8f,
                        ),
                    )
                    else -> UiNode.Scaffold(
                        topBar = UiNode.TopBar(
                            title = session.manifest.name,
                            showBack = true,
                        ),
                        content = tree,
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    PluginUiRenderer(
                        node = node,
                        accent = accent,
                        onBack = { navController.popBackStack() },
                        onCallback = { id, value -> session.onCallback(id, value) },
                    )

                    val dlg = dialog
                    if (dlg != null) {
                        PluginAlertDialogContent(
                            title = dlg.title,
                            message = dlg.message,
                            buttons = dlg.buttons.ifEmpty {
                                listOf(UiNode.DialogButton("OK", "filled", null))
                            },
                            cancelable = dlg.cancelable,
                            onDismiss = { session.dismissDialog() },
                            onButton = { id -> session.onDialogButton(id) },
                        )
                    }
                }
            }
        }
    }
}
