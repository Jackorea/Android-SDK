package com.example.test.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.linkbandsdk.SensorType
import com.example.linkbandsdk.AccData
import com.example.linkbandsdk.BatteryData
import com.example.linkbandsdk.EegData
import com.example.linkbandsdk.PpgData
import com.example.linkbandsdk.AccelerometerMode
import com.example.linkbandsdk.ProcessedAccData
import com.example.linkbandsdk.CollectionMode
import com.example.linkbandsdk.SensorBatchConfiguration
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ArrowForward
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

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
    isRecording: Boolean,
    isAutoReconnectEnabled: Boolean,
    connectedDeviceName: String?,
    accelerometerMode: AccelerometerMode,
    processedAccData: List<ProcessedAccData>,
    // 배치 수집 관련 매개변수들 추가
    selectedCollectionMode: CollectionMode,
    getSensorConfiguration: (SensorType) -> SensorBatchConfiguration?,
    onDisconnect: () -> Unit,
    onNavigateToScan: () -> Unit,
    onSelectSensor: (SensorType) -> Unit,
    onDeselectSensor: (SensorType) -> Unit,
    onStartSelectedSensors: () -> Unit,
    onStopSelectedSensors: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onShowFileList: () -> Unit,
    onToggleAutoReconnect: () -> Unit,
    onSetAccelerometerMode: (AccelerometerMode) -> Unit,
    // 배치 수집 콜백 함수들 추가
    onCollectionModeChange: (CollectionMode) -> Unit,
    onSampleCountChange: (SensorType, Int, String) -> Unit,
    onSecondsChange: (SensorType, Int, String) -> Unit,
    onMinutesChange: (SensorType, Int, String) -> Unit,
    navController: NavController
) {
    // 경고 다이얼로그 상태
    var showStopCollectionDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    
    // 연결이 끊어지면 자동으로 스캔 화면으로 이동
    var hasNavigatedToScan by remember { mutableStateOf(false) }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(isConnected, currentRoute) {
        if (!isConnected && currentRoute == "data") {
            onNavigateToScan()
        }
    }
    
    // 수집 시작 시점의 선택된 센서 스냅샷
    var startedSensors by remember { mutableStateOf<Set<SensorType>>(emptySet()) }
    
    // 수집 시작/중지 시점에 스냅샷 갱신
    LaunchedEffect(isReceivingData) {
        if (isReceivingData) {
            startedSensors = selectedSensors.toSet()
        } else {
            startedSensors = emptySet()
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 헤더
        item {
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
                
                Row {
                    // 저장된 파일 보기 아이콘만 유지
                IconButton(
                    onClick = onShowFileList
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "저장된 파일 보기",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    }
                }
            }
        }
        
        // 연결 상태
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            text = if (isConnected) {
                                connectedDeviceName?.let { "연결됨: $it" } ?: "연결됨"
                            } else "연결 해제됨",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (isConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "샘플링 레이트 \n EEG 250Hz \n PPG 50Hz \n ACC 25Hz",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 배터리 정보
        batteryData?.let { battery ->
            item {
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
        }
        
        // 수동 서비스 제어 패널
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 헤더
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "데이터 수집 설정",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "데이터 수집 설정",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // 모니터링 상태 표시
                        if (isReceivingData) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "활성",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                            )
                            Text(
                                    text = "수집 중",
                                    style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                            )
                            }
                        }
                    }
                    
                    // 수집 모드 선택
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "수집 모드",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // 세그먼트 컨트롤 스타일
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val modes = listOf(
                                CollectionMode.SAMPLE_COUNT to "샘플 수",
                                CollectionMode.SECONDS to "시간(초)",
                                CollectionMode.MINUTES to "시간(분)"
                            )
                            
                            modes.forEachIndexed { index, (mode, displayName) ->
                                val isSelected = selectedCollectionMode == mode
                                
                                Button(
                                    onClick = { 
                                        if (!isReceivingData && selectedCollectionMode != mode) {
                                            onCollectionModeChange(mode) 
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isReceivingData,
                                    colors = if (isSelected) {
                                        ButtonDefaults.buttonColors()
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                                    shape = when (index) {
                                        0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                                        modes.size - 1 -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                                        else -> RoundedCornerShape(0.dp)
                                    }
                                ) {
                            Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    
                    // 센서별 설정
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val title = when (selectedCollectionMode) {
                            CollectionMode.SAMPLE_COUNT -> "센서별 샘플 수 설정"
                            CollectionMode.SECONDS -> "센서별 수집 시간 (초)"
                            CollectionMode.MINUTES -> "센서별 수집 시간 (분)"
                        }
                        
                            Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val sensors = listOf(SensorType.EEG, SensorType.PPG, SensorType.ACC)
                        
                        sensors.forEach { sensorType ->
                            val config = getSensorConfiguration(sensorType)
                            if (config != null) {
                                SensorConfigRow(
                                    sensorType = sensorType,
                                    config = config,
                                    selectedCollectionMode = selectedCollectionMode,
                                    isSelected = selectedSensors.contains(sensorType),
                                    isEnabled = !isReceivingData,
                                    onSampleCountChange = onSampleCountChange,
                                    onSecondsChange = onSecondsChange,
                                    onMinutesChange = onMinutesChange
                                )
                            }
                        }
                    }
                    
                    // 센서 선택
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "센서 선택",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val sensors = listOf(
                            SensorType.EEG to "EEG",
                            SensorType.PPG to "PPG",
                            SensorType.ACC to "ACC"
                        )
                        
                        sensors.forEach { (sensorType, displayName) ->
                            SensorCheckboxItem(
                                sensorType = sensorType,
                                displayName = displayName,
                                checked = selectedSensors.contains(sensorType),
                                isStarted = when (sensorType) {
                                    SensorType.EEG -> isEegStarted
                                    SensorType.PPG -> isPpgStarted
                                    SensorType.ACC -> isAccStarted
                                },
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onSelectSensor(sensorType)
                                    } else {
                                        onDeselectSensor(sensorType)
                                    }
                                },
                                enabled = isConnected && !isReceivingData
                            )
                        }
                    }
                    
                    // ACC 모드 선택 (ACC가 선택된 경우)
                    if (selectedSensors.contains(SensorType.ACC)) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ACC 표시 모드",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { onSetAccelerometerMode(AccelerometerMode.RAW) },
                                    enabled = isConnected && !isReceivingData,
                                    modifier = Modifier.weight(1f),
                                    colors = if (accelerometerMode == AccelerometerMode.RAW) {
                                        ButtonDefaults.buttonColors()
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                                    shape = RoundedCornerShape(
                                        topStart = 8.dp, 
                                        bottomStart = 8.dp, 
                                        topEnd = 0.dp, 
                                        bottomEnd = 0.dp
                                    )
                                ) {
                                    Text(
                                        text = "원시값",
                                        style = MaterialTheme.typography.bodySmall
                            )
                        }
                                
                                Button(
                                    onClick = { onSetAccelerometerMode(AccelerometerMode.MOTION) },
                                    enabled = isConnected && !isReceivingData,
                                    modifier = Modifier.weight(1f),
                                    colors = if (accelerometerMode == AccelerometerMode.MOTION) {
                                        ButtonDefaults.buttonColors()
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                                    shape = RoundedCornerShape(
                                        topStart = 0.dp, 
                                        bottomStart = 0.dp, 
                                        topEnd = 8.dp, 
                                        bottomEnd = 8.dp
                                    )
                                ) {
                            Text(
                                        text = "움직임",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            
                            Text(
                                text = accelerometerMode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 컨트롤 버튼
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 데이터 수집 컨트롤
                        if (isReceivingData) {
                    Button(
                        onClick = {
                                if (isRecording) {
                                    showStopCollectionDialog = true
                                } else {
                                    onStopSelectedSensors()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("수집 중지")
                            }
                        } else {
                            Button(
                                onClick = { onStartSelectedSensors() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isConnected && selectedSensors.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("수집 시작 (${selectedSensors.size}개)")
                            }
                        }
                    
                        // 기록 컨트롤 (수집 시작 후 표시)
                    if (isReceivingData) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "데이터 기록",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    if (isRecording) {
                        Button(
                                            onClick = { onStopRecording() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("기록 중지")
                                        }
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "기록 중",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(12.dp)
                                            )
                            Text(
                                                text = "데이터를 기록하고 있습니다...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { onStartRecording() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("기록 시작")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // EEG 데이터 (같은 레벨에 표시)
        if (startedSensors.contains(SensorType.EEG)) {
            item {
                SensorDataCard(
                    title = "EEG 데이터",
                    content = {
                        if (eegData.isNotEmpty()) {
                            val latest = eegData.takeLast(3)
                            latest.forEach { data ->
                                Text(
                                    text = "timestamp: ${data.timestamp.time}, ch1Raw: ${data.ch1Raw}, ch2Raw: ${data.ch2Raw}, ch1uV: ${data.channel1.roundToInt()}µV, ch2uV: ${data.channel2.roundToInt()}µV, leadOff: ${if (data.leadOff) "1" else "0"}",
                                    fontSize = 12.sp
                                )
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
        }
        
        // PPG 데이터 (같은 레벨에 표시)
        if (startedSensors.contains(SensorType.PPG)) {
            item {
                SensorDataCard(
                    title = "PPG 데이터",
                    content = {
                        if (ppgData.isNotEmpty()) {
                            val latest = ppgData.takeLast(3)
                            latest.forEach { data ->
                                Text(
                                    text = "timestamp: ${data.timestamp.time}, red: ${data.red}, ir: ${data.ir}",
                                    fontSize = 12.sp
                                )
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
        }
        
        // ACC 데이터 (같은 레벨에 표시)
        if (startedSensors.contains(SensorType.ACC)) {
            item {
                SensorDataCard(
                    title = "ACC 데이터 [${if (accelerometerMode == AccelerometerMode.RAW) "원시값" else "움직임"}]",
                    content = {
                        if (processedAccData.isNotEmpty()) {
                            val latest = processedAccData.takeLast(3)
                            latest.forEach { data ->
                                Text(
                                    text = "timestamp: ${data.timestamp.time}, x: ${data.x}, y: ${data.y}, z: ${data.z}",
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Text(
                                text = "ACC 데이터를 수신하지 못했습니다",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
        
        // 자동연결 토글 (ACC 데이터 카드 아래로 이동)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                        text = "자동연결",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Switch(
                        checked = isAutoReconnectEnabled,
                        onCheckedChange = { onToggleAutoReconnect() }
                    )
                }
            }
        }
        
        // 연결해제 버튼 (맨 아래)
        item {
            Button(
                onClick = { showDisconnectDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "연결 해제",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
    
    // 수집 중지 경고 다이얼로그 (기록 중일 때)
    if (showStopCollectionDialog) {
        AlertDialog(
            onDismissRequest = { showStopCollectionDialog = false },
            title = { Text("수집 중지 경고") },
            text = { Text("현재 데이터 기록 중입니다. 수집을 중지하면 기록도 함께 중지됩니다. 계속하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopCollectionDialog = false
                        onStopSelectedSensors()
                        // 기록도 함께 중지
                        if (isRecording) {
                            onStopRecording()
                        }
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStopCollectionDialog = false }
                ) {
                    Text("취소")
                }
            }
        )
    }
    
    // 연결 해제 경고 다이얼로그
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("연결 해제 경고") },
            text = { Text("디바이스와의 연결을 해제하시겠습니까? 수집 중인 데이터와 기록이 중지됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        onDisconnect()
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectDialog = false }
                ) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun SensorDataCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileListScreen(
    onBack: () -> Unit,
    onFileClick: (java.io.File) -> Unit
) {
    var csvFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // 파일 목록 로드
    LaunchedEffect(Unit) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val linkBandDir = java.io.File(downloadsDir, "LinkBand")
        if (linkBandDir.exists()) {
            csvFiles = linkBandDir.listFiles { file ->
                file.name.endsWith(".csv") && file.name.startsWith("LinkBand_")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "저장된 CSV 파일",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Share 아이콘을 제목 오른쪽으로 이동
                IconButton(
                    onClick = {
                        // "내 파일" 앱으로 직접 Downloads/LinkBand 폴더 열기
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val linkBandDir = java.io.File(downloadsDir, "LinkBand")
                        
                        // LinkBand 폴더가 없으면 생성
                        if (!linkBandDir.exists()) {
                            linkBandDir.mkdirs()
                        }
                        
                        try {
                            // 방법 1: 삼성 "내 파일" 앱 직접 실행
                            val intent = Intent()
                            intent.setClassName("com.sec.android.app.myfiles", "com.sec.android.app.myfiles.external.ui.MainActivity")
                            intent.action = Intent.ACTION_VIEW
                            intent.setDataAndType(Uri.fromFile(downloadsDir), "resource/folder")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e1: Exception) {
                            try {
                                // 방법 2: 구글 "Files" 앱 직접 실행
                                val intent = Intent()
                                intent.setClassName("com.google.android.apps.nbu.files", "com.google.android.apps.nbu.files.home.HomeActivity")
                                intent.action = Intent.ACTION_VIEW
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e2: Exception) {
                                try {
                                    // 방법 3: 일반적인 파일 관리자 (선택 없이)
                                    val intent = Intent(Intent.ACTION_MAIN)
                                    intent.addCategory(Intent.CATEGORY_APP_FILES)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e3: Exception) {
                                    try {
                                        // 방법 4: Downloads 관리자 직접 실행
                                        val intent = Intent("android.intent.action.VIEW_DOWNLOADS")
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e4: Exception) {
                                        try {
                                            // 방법 5: 시스템 문서 UI로 Downloads 폴더 열기
                                            val intent = Intent(Intent.ACTION_VIEW)
                                            intent.setDataAndType(
                                                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"),
                                                "vnd.android.document/directory"
                                            )
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (e5: Exception) {
                                            // 모든 방법 실패 - 사용자에게 안내
                                            android.widget.Toast.makeText(
                                                context,
                                                "내 파일 앱을 열 수 없습니다.\n수동으로 이동하세요:\n내 파일 → Download → LinkBand",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            // 일반 터치: 파일 관리자 열기 (위의 onClick과 동일)
                        },
                        onLongClick = {
                            // 길게 누르면 파일 경로를 클립보드에 복사
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val linkBandDir = java.io.File(downloadsDir, "LinkBand")
                            clipboardManager.setText(AnnotatedString(linkBandDir.absolutePath))
                            android.widget.Toast.makeText(
                                context,
                                "파일 경로가 클립보드에 복사되었습니다:\n${linkBandDir.absolutePath}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "내 파일 앱으로 Downloads/LinkBand 폴더 열기",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            TextButton(onClick = onBack) {
                Text("← 뒤로")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (csvFiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📁",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "저장된 CSV 파일이 없습니다",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "센서 데이터를 기록하면 여기에 표시됩니다",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(csvFiles) { file ->
                    CsvFileItem(file = file, onFileClick = onFileClick)
                }
            }
        }
    }
}

@Composable
fun CsvFileItem(file: java.io.File, onFileClick: (java.io.File) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = file.name,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val fileSize = when {
                        file.length() < 1024 -> "${file.length()} B"
                        file.length() < 1024 * 1024 -> "${file.length() / 1024} KB"
                        else -> "${"%.1f".format(file.length() / (1024.0 * 1024.0))} MB"
                    }
                    
                    val lastModified = java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", 
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(file.lastModified()))
                    
                    Text(
                        text = "크기: $fileSize",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "수정: $lastModified",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 센서 타입 표시
                val sensorType = when {
                    file.name.contains("EEG") -> "📊 EEG"
                    file.name.contains("PPG") -> "🔴 PPG"
                    file.name.contains("ACC") -> "🚀 ACC"
                    else -> "📄"
                }
                
                Text(
                    text = sensorType,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Button(
                onClick = { onFileClick(file) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("미리보기")
            }
        }
    }
} 

@Composable
private fun SensorConfigRow(
    sensorType: SensorType,
    config: SensorBatchConfiguration,
    selectedCollectionMode: CollectionMode,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSampleCountChange: (SensorType, Int, String) -> Unit,
    onSecondsChange: (SensorType, Int, String) -> Unit,
    onMinutesChange: (SensorType, Int, String) -> Unit
) {
    var currentText by remember(sensorType, selectedCollectionMode) {
        mutableStateOf(
            when (selectedCollectionMode) {
                CollectionMode.SAMPLE_COUNT -> config.sampleCountText
                CollectionMode.SECONDS -> config.secondsText
                CollectionMode.MINUTES -> config.minutesText
            }
        )
    }
    
    // 디폴트값 설정
    val defaultValue = when (selectedCollectionMode) {
        CollectionMode.SAMPLE_COUNT -> when (sensorType) {
            SensorType.EEG -> "250"
            SensorType.PPG -> "50"
            SensorType.ACC -> "25"
        }
        CollectionMode.SECONDS -> "1"
        CollectionMode.MINUTES -> "1"
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when (sensorType) {
                    SensorType.EEG -> Icons.Default.Share
                    SensorType.PPG -> Icons.Default.Favorite
                    SensorType.ACC -> Icons.Default.ArrowForward
                },
                contentDescription = null,
                tint = if (isSelected) {
                    when (sensorType) {
                        SensorType.EEG -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
                        SensorType.PPG -> androidx.compose.ui.graphics.Color.Red
                        SensorType.ACC -> androidx.compose.ui.graphics.Color.Blue
                    }
                } else androidx.compose.ui.graphics.Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = when (sensorType) {
                    SensorType.EEG -> "EEG"
                    SensorType.PPG -> "PPG"
                    SensorType.ACC -> "ACC"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Gray
            )
        }
        
        OutlinedTextField(
            value = currentText,
            onValueChange = { newValue ->
                currentText = newValue
                when (selectedCollectionMode) {
                    CollectionMode.SAMPLE_COUNT -> {
                        val count = newValue.toIntOrNull() ?: 0
                        onSampleCountChange(sensorType, count, newValue)
                    }
                    CollectionMode.SECONDS -> {
                        val seconds = newValue.toIntOrNull() ?: 0
                        onSecondsChange(sensorType, seconds, newValue)
                    }
                    CollectionMode.MINUTES -> {
                        val minutes = newValue.toIntOrNull() ?: 0
                        onMinutesChange(sensorType, minutes, newValue)
                    }
                }
            },
            modifier = Modifier.width(80.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodySmall,
            enabled = isSelected && isEnabled,
            singleLine = true,
            placeholder = {
                Text(
                    text = defaultValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        )
    }
}

@Composable
private fun SensorCheckboxItem(
    sensorType: SensorType,
    displayName: String,
    checked: Boolean,
    isStarted: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    val sensorIcon = when (sensorType) {
        SensorType.EEG -> Icons.Default.Share
        SensorType.PPG -> Icons.Default.Favorite
        SensorType.ACC -> Icons.Default.ArrowForward
    }
    
    val sensorColor = when (sensorType) {
        SensorType.EEG -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
        SensorType.PPG -> androidx.compose.ui.graphics.Color.Red
        SensorType.ACC -> androidx.compose.ui.graphics.Color.Blue
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = sensorColor
                ),
                enabled = enabled
            )
            
            Icon(
                imageVector = sensorIcon,
                contentDescription = null,
                tint = if (checked) sensorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isStarted) {
            Text(
                text = "수집 중",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
} 