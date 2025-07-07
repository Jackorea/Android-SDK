package com.example.test.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.test.ble.SensorType
import com.example.test.data.AccData
import com.example.test.data.BatteryData
import com.example.test.data.EegData
import com.example.test.data.PpgData
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Locale

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
    onDisconnect: () -> Unit,
    onNavigateToScan: () -> Unit,
    onSelectSensor: (SensorType) -> Unit,
    onDeselectSensor: (SensorType) -> Unit,
    onStartSelectedSensors: () -> Unit,
    onStopSelectedSensors: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onShowFileList: () -> Unit,
    onToggleAutoReconnect: () -> Unit
) {
    // 경고 다이얼로그 상태
    var showStopCollectionDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    
    // 연결이 끊어지면 자동으로 스캔 화면으로 이동
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            onNavigateToScan()
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
                                text = "ACC 센서",
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
                        onClick = {
                            if (isReceivingData) {
                                // 수집 중지 시 기록 중이면 경고 다이얼로그 표시
                                if (isRecording) {
                                    showStopCollectionDialog = true
                                } else {
                                    onStopSelectedSensors()
                                }
                            } else {
                                onStartSelectedSensors()
                            }
                        },
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
                                "🛑 수집 중지" 
                            else 
                                "▶️ 수집 시작 (${selectedSensors.size}개)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // CSV 기록 버튼 (센서가 수신 중일 때만 표시)
                    if (isReceivingData) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = if (isRecording) onStopRecording else onStartRecording,
                            enabled = isConnected && isReceivingData,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(
                                text = if (isRecording) 
                                    "⏹️ 기록 중지" 
                                else 
                                    "📝 기록 시작",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (isRecording) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📁 CSV 파일로 데이터 기록 중...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // EEG 데이터 (같은 레벨에 표시)
        item {
            SensorDataCard(
                title = "EEG 데이터",
                content = {
                    if (eegData.isNotEmpty()) {
                        val latest = eegData.takeLast(3)
                        latest.forEach { data ->
                            val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                            Text(
                                text = "${timeFormat.format(data.timestamp)} - CH1: ${data.channel1.roundToInt()}µV, CH2: ${data.channel2.roundToInt()}µV, Lead: ${if (data.leadOff) "OFF" else "ON"}",
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
        
        // PPG 데이터 (같은 레벨에 표시)
        item {
            SensorDataCard(
                title = "PPG 데이터",
                content = {
                    if (ppgData.isNotEmpty()) {
                        val latest = ppgData.takeLast(3)
                        latest.forEach { data ->
                            val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                            Text(
                                text = "${timeFormat.format(data.timestamp)} - Red: ${data.red}, IR: ${data.ir}",
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
        
        // ACC 데이터 (같은 레벨에 표시)
        item {
            SensorDataCard(
                title = "ACC 데이터",
                content = {
                    if (accData.isNotEmpty()) {
                        val latest = accData.takeLast(3)
                        latest.forEach { data ->
                            val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                            Text(
                                text = "${timeFormat.format(data.timestamp)} - X: ${data.x}, Y: ${data.y}, Z: ${data.z}",
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
                    text = "🔌 연결 해제",
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