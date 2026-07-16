package com.droid.dolphy.nrf

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import kotlinx.coroutines.launch

@Composable
fun NrfScannerScreen(
    context: Context,
    navController: NavController,
    viewModel: NrfScannerViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        viewModel.initialize(context, bluetoothAdapter)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = colorScheme.primary) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTopBar(
                    title = stringResource(R.string.nrf_scanner_title),
                    onBack = { navController.popBackStack() }
                )


        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isScanning) {
                        viewModel.stopScanning()
                    } else {
                        viewModel.startScanning()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                )
                Text(
                    text = if (isScanning) stringResource(R.string.nrf_scanner_stop) else stringResource(R.string.nrf_scanner_start),
                    fontWeight = FontWeight.Bold
                )
            }


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text(stringResource(R.string.nrf_scanner_all)) },
                    leadingIcon = if (selectedFilter == null) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )

                FilterChip(
                    selected = selectedFilter == DeviceType.BLE_DEVICE,
                    onClick = { viewModel.setFilter(DeviceType.BLE_DEVICE) },
                    label = { Text(stringResource(R.string.nrf_scanner_ble)) }
                )

                FilterChip(
                    selected = selectedFilter == DeviceType.CLASSIC_DEVICE,
                    onClick = { viewModel.setFilter(DeviceType.CLASSIC_DEVICE) },
                    label = { Text(stringResource(R.string.nrf_scanner_classic)) }
                )

                FilterChip(
                    selected = selectedFilter == DeviceType.BEACON,
                    onClick = { viewModel.setFilter(DeviceType.BEACON) },
                    label = { Text(stringResource(R.string.nrf_scanner_beacon)) }
                )

                FilterChip(
                    selected = selectedFilter == DeviceType.FAST_PAIR,
                    onClick = { viewModel.setFilter(DeviceType.FAST_PAIR) },
                    label = { Text(stringResource(R.string.nrf_scanner_fast_pair)) }
                )
            }
        }

        Divider(thickness = 0.5.dp, color = colorScheme.outlineVariant)


        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (isScanning) stringResource(R.string.nrf_scanner_scanning) else stringResource(R.string.nrf_scanner_no_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (!isScanning) {
                        Text(
                            text = stringResource(R.string.nrf_scanner_tap_to_start),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
            ) {
                items(devices, key = { it.address }) { device ->
                    NrfDeviceCard(
                        device = device,
                        onConnect = { connectedDevice ->
                            scope.launch {
                            }
                        },
                        onProfile = { profileDevice ->
                            scope.launch {
                                viewModel.profileDevice(profileDevice)
                            }
                        }
                    )
                }
            }
        }
    }
}
}}
