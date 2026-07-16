package com.droid.dolphy.nfc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.TextGray

@Composable
fun NfcToolsScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val bottomScrollPadding = 220.dp

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = bottomScrollPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionTopBar(
                    transparent = true,
                    title = stringResource(R.string.nfc_tools),
                    onBack = { navController.popBackStack() },
                )

                Spacer(modifier = Modifier.height(8.dp))

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    cornerRadius = 12.dp,
                ) {
                    ToolRow(
                        title = stringResource(R.string.nfc_read),
                        description = stringResource(R.string.nfc_read_description),
                        icon = Icons.Outlined.Nfc,
                        accent = accent,
                onClick = { navController.navigate("other/nfc_wait") },
                    )
                }

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    cornerRadius = 12.dp,
                ) {
                    ToolRow(
                        title = stringResource(R.string.nfc_tool_erase_title),
                        description = stringResource(R.string.nfc_tool_erase_desc),
                        icon = Icons.Outlined.DeleteOutline,
                        accent = accent,
                onClick = { navController.navigate("other/nfc_erase") },
                    )
                }

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    cornerRadius = 12.dp,
                ) {
                    ToolRow(
                        title = stringResource(R.string.nfc_write_menu_title),
                        description = stringResource(R.string.nfc_write_pick_type),
                        icon = Icons.Outlined.EditNote,
                        accent = accent,
                onClick = { navController.navigate("other/nfc_write_menu") },
                    )
                }

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    cornerRadius = 12.dp,
                ) {
                    ToolRow(
                        title = stringResource(R.string.nfc_master_key),
                        description = stringResource(R.string.nfc_master_key_description),
                        icon = Icons.Outlined.Nfc,
                        accent = accent,
                onClick = { navController.navigate("other/nfc_master_key") },
                    )
                }

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    cornerRadius = 12.dp,
                ) {
                    ToolRow(
                        title = stringResource(R.string.nfc_audio_spoofer),
                        description = stringResource(R.string.nfc_audio_spoofer_desc),
                        icon = Icons.Outlined.Nfc,
                        accent = accent,
                onClick = { navController.navigate("other/nfc_audio_spoofer") },
                    )
                }

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    cornerRadius = 12.dp,
                ) {
                    ToolRow(
                        title = stringResource(R.string.nfc_trolls),
                        description = stringResource(R.string.nfc_troll_description),
                        icon = Icons.Outlined.Nfc,
                        accent = accent,
                        onClick = { navController.navigate("other/nfc_trolls") },
                    )
                }

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    cornerRadius = 12.dp,
                ) {
                    ToolRow(
                        title = stringResource(R.string.nfc_emulate),
                        description = stringResource(R.string.nfc_emulate_description),
                        icon = Icons.Outlined.Nfc,
                        accent = accent,
                onClick = { navController.navigate("other/nfc_emulator_list") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    title: String,
    description: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
            )
        }
    }
}
