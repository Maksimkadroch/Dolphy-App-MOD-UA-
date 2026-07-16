package com.droid.dolphy.plugin.model




sealed class UiNode {
    data class Scaffold(
        val topBar: TopBar? = null,
        val content: UiNode,
        val fab: UiNode? = null,
    ) : UiNode()

    data class TopBar(
        val title: String,
        val showBack: Boolean = true,
        val actions: List<TopBarAction> = emptyList(),
    )

    data class TopBarAction(
        val icon: String,
        val onClickId: String? = null,
    )

    data class Column(
        val children: List<UiNode>,
        val padding: Float = 0f,
        val spacing: Float = 8f,
        val fillMaxSize: Boolean = false,
    ) : UiNode()

    data class Row(
        val children: List<UiNode>,
        val padding: Float = 0f,
        val spacing: Float = 8f,
        val fillMaxWidth: Boolean = true,
    ) : UiNode()

    data class Box(
        val children: List<UiNode>,
        val padding: Float = 0f,
        val fillMaxSize: Boolean = false,
    ) : UiNode()

    data class LazyColumn(
        val children: List<UiNode>,
        val padding: Float = 0f,
        val spacing: Float = 4f,
        val fillMaxSize: Boolean = true,
    ) : UiNode()

    data class Text(
        val text: String,
        val style: String = "bodyMedium",
        val color: String? = null,
        val maxLines: Int = Int.MAX_VALUE,
    ) : UiNode()

    data class Button(
        val text: String,
        val onClickId: String? = null,
        val style: String = "filled",
        val enabled: Boolean = true,
        val fillMaxWidth: Boolean = false,
    ) : UiNode()

    data class IconButton(
        val icon: String,
        val onClickId: String? = null,
        val enabled: Boolean = true,
    ) : UiNode()

    data class TextField(
        val value: String,
        val onChangeId: String? = null,
        val label: String = "",
        val singleLine: Boolean = true,
        val fillMaxWidth: Boolean = true,
    ) : UiNode()

    data class Switch(
        val checked: Boolean,
        val onChangeId: String? = null,
        val title: String = "",
        val subtitle: String = "",
        val enabled: Boolean = true,
    ) : UiNode()

    data class Slider(
        val value: Float,
        val onChangeId: String? = null,
        val min: Float = 0f,
        val max: Float = 100f,
        val title: String = "",
        val steps: Int = 0,
    ) : UiNode()

    data class LinearProgress(
        val progress: Float? = null,
        val fillMaxWidth: Boolean = true,
    ) : UiNode()

    data class CircularProgress(
        val progress: Float? = null,
    ) : UiNode()

    data class Divider(
        val vertical: Boolean = false,
    ) : UiNode()

    data class Spacer(
        val height: Float = 8f,
        val width: Float = 0f,
    ) : UiNode()

    data class Icon(
        val name: String,
        val size: Float = 24f,
        val tint: String? = null,
    ) : UiNode()

    data class Chip(
        val text: String,
        val selected: Boolean = false,
        val onClickId: String? = null,
    ) : UiNode()

    data class MaterialCard(
        val children: List<UiNode>,
        val contentPadding: Float = 16f,
        val segmentedIndex: Int? = null,
        val segmentedCount: Int? = null,
        val onClickId: String? = null,
    ) : UiNode()

    data class FunctionRow(
        val title: String,
        val description: String = "",
        val icon: String = "extension",
        val iconTint: String? = null,
        val onClickId: String? = null,
    ) : UiNode()

    data class SegmentedList(
        val children: List<UiNode>,
        val spacing: Float = 4f,
    ) : UiNode()

    data class SettingsRow(
        val title: String,
        val subtitle: String = "",
        val icon: String? = null,
        val onClickId: String? = null,
        val trailing: UiNode? = null,
    ) : UiNode()

    data class WebView(
        val url: String = "",
        val html: String = "",
        val fillMaxSize: Boolean = true,
        val height: Float = 320f,
    ) : UiNode()

    data class LogPanel(
        val text: String,
        val maxHeight: Float = 200f,
    ) : UiNode()

    data class AlertDialog(
        val show: Boolean,
        val title: String,
        val message: String,
        val confirmText: String = "OK",
        val dismissText: String = "Отмена",
        val onConfirmId: String? = null,
        val onDismissId: String? = null,

        val buttons: List<DialogButton> = emptyList(),
        val cancelable: Boolean = true,
    ) : UiNode()

    data class DialogButton(
        val text: String,
        val style: String = "text",
        val onClickId: String? = null,
    )

    data object Empty : UiNode()
}




data class PluginDialogSpec(
    val title: String,
    val message: String,
    val buttons: List<UiNode.DialogButton>,
    val cancelable: Boolean = true,
)
