package com.example.test.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    scannedDevices: List<BluetoothDevice>,
    isScanning: Boolean,
    isConnected: Boolean,
    isAutoReconnectEnabled: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onNavigateToData: () -> Unit,
    onEnableAutoReconnect: () -> Unit,
    onDisableAutoReconnect: () -> Unit
) {
    // 연결되면 자동으로 데이터 화면으로 이동
    LaunchedEffect(isConnected) {
        if (isConnected) {
            onNavigateToData()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 제목
        Text(
            text = "LinkBand 블루투스 스캐너",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        // 자동연결 토글
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "자동 재연결",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isAutoReconnectEnabled) "연결이 끊어지면 자동으로 재연결됩니다" else "수동으로 재연결해야 합니다",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAutoReconnectEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onEnableAutoReconnect()
                        } else {
                            onDisableAutoReconnect()
                        }
                    }
                )
            }
        }
        
        // 스캔 버튼
        Button(
            onClick = if (isScanning) onStopScan else onStartScan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "스캔 중지" else "스캔 시작")
        }
        
        // 스캔 상태
        if (isScanning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text("LXB- 디바이스를 검색 중...")
            }
        }
        
        // 디바이스 목록
        Card(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "발견된 디바이스 (${scannedDevices.size}개)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (scannedDevices.isEmpty()) {
                    Text(
                        text = "디바이스를 찾을 수 없습니다. 스캔을 시작하여 LXB- 디바이스를 찾아보세요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scannedDevices) { device ->
                            DeviceItem(
                                device = device,
                                isScanning = isScanning,
                                onConnect = { onConnect(device) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(
    device: BluetoothDevice,
    isScanning: Boolean,
    onConnect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.name ?: "알 수 없는 디바이스",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = device.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            
            Button(
                onClick = onConnect,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("연결")
            }
        }
    }
} 