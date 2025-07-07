package com.example.test.ui

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
import com.example.test.ble.SensorType
import com.example.test.data.AccData
import com.example.test.data.BatteryData
import com.example.test.data.EegData
import com.example.test.data.PpgData
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    eegData: List<EegData>,
    ppgData: List<PpgData>,
    accData: List<AccData>,
    batteryData: BatteryData?,
    isConnected: Boolean,
    isEegStarted: Boolean,
    isPpgStarted: Boolean, 
    isAccStarted: Boolean,
    selectedSensors: Set<SensorType>,
    isReceivingData: Boolean,
    onDisconnect: () -> Unit,
    onNavigateToScan: () -> Unit,
    onSelectSensor: (SensorType) -> Unit,
    onDeselectSensor: (SensorType) -> Unit,
    onStartSelectedSensors: () -> Unit,
    onStopSelectedSensors: () -> Unit,
    onStartAllSensors: () -> Unit
) {
    // 연결이 끊어지면 자동으로 스캔 화면으로 이동
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            onNavigateToScan()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LinkBand 데이터",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("연결 해제")
            }
        }
        
        // 연결 상태
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .padding(end = 8.dp)
                ) {
                    // 연결 상태 인디케이터
                }
                Text(
                    text = if (isConnected) "연결됨" else "연결 해제됨",
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // 수동 서비스 제어 패널
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "센서 제어",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                // 센서 선택 섹션
                Text(
                    text = "수신할 센서 선택:",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                // EEG 센서 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedSensors.contains(SensorType.EEG),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onSelectSensor(SensorType.EEG)
                                } else {
                                    onDeselectSensor(SensorType.EEG)
                                }
                            },
                            enabled = isConnected && !isReceivingData
                        )
                        Text(
                            text = "EEG 센서",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (isEegStarted) {
                        Text(
                            text = "수신 중",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // PPG 센서 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedSensors.contains(SensorType.PPG),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onSelectSensor(SensorType.PPG)
                                } else {
                                    onDeselectSensor(SensorType.PPG)
                                }
                            },
                            enabled = isConnected && !isReceivingData
                        )
                        Text(
                            text = "PPG 센서",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (isPpgStarted) {
                        Text(
                            text = "수신 중",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // ACC 센서 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedSensors.contains(SensorType.ACC),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onSelectSensor(SensorType.ACC)
                                } else {
                                    onDeselectSensor(SensorType.ACC)
                                }
                            },
                            enabled = isConnected && !isReceivingData
                        )
                        Text(
                            text = "가속도 센서",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (isAccStarted) {
                        Text(
                            text = "수신 중",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 선택된 센서 시작/중지 버튼
                Button(
                    onClick = if (isReceivingData) onStopSelectedSensors else onStartSelectedSensors,
                    enabled = isConnected && selectedSensors.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReceivingData) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isReceivingData) 
                            "🛑 선택된 센서 중지" 
                        else 
                            "▶️ 선택된 센서 시작 (${selectedSensors.size}개)",
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 모든 센서 동시 시작 버튼 (기존 유지)
                Button(
                    onClick = onStartAllSensors,
                    enabled = isConnected && !isReceivingData,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🚀 모든 센서 동시 시작", fontWeight = FontWeight.Bold)
                }
            }
        }
        
        // 배터리 정보
        batteryData?.let { battery ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        battery.level > 50 -> MaterialTheme.colorScheme.primaryContainer
                        battery.level > 20 -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "배터리",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${battery.level}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
        
        // 센서 데이터
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SensorDataCard(
                    title = "EEG 데이터",
                    subtitle = "${eegData.size}개 샘플",
                    content = {
                        if (eegData.isNotEmpty()) {
                            val latest = eegData.takeLast(5)
                            latest.forEach { data ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "채널1: ${data.channel1.roundToInt()}µV",
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "채널2: ${data.channel2.roundToInt()}µV",
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Lead: ${data.leadOff}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "EEG 데이터를 수신하지 못했습니다",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
            
            item {
                SensorDataCard(
                    title = "PPG 데이터",
                    subtitle = "${ppgData.size}개 샘플",
                    content = {
                        if (ppgData.isNotEmpty()) {
                            val latest = ppgData.takeLast(5)
                            latest.forEach { data ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Red: ${data.red}",
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "IR: ${data.ir}",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "PPG 데이터를 수신하지 못했습니다",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
            
            item {
                SensorDataCard(
                    title = "가속도계 데이터",
                    subtitle = "${accData.size}개 샘플",
                    content = {
                        if (accData.isNotEmpty()) {
                            val latest = accData.takeLast(5)
                            latest.forEach { data ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "X: ${data.x}",
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Y: ${data.y}",
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Z: ${data.z}",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "가속도계 데이터를 수신하지 못했습니다",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SensorDataCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            content()
        }
    }
} 