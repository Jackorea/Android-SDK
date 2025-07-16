package com.example.test

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
//import androidx.compose.runtime.*

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.test.ui.CsvViewerScreen
import com.example.test.ui.DataScreen
import com.example.test.ui.FileListScreen
import com.example.test.ui.ScanScreen
import com.example.test.ui.theme.TestTheme
import com.example.test.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.linkbandsdk.EegData
import com.example.linkbandsdk.PpgData
import com.example.linkbandsdk.AccData
import com.example.linkbandsdk.BatteryData
import com.example.linkbandsdk.AccelerometerMode
import com.example.linkbandsdk.CollectionMode

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TestTheme {
                val navController = rememberNavController()
                
                // BLE 권한 요청
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                } else {
                    listOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
                
                val permissionState = rememberMultiplePermissionsState(permissions)
                
                LaunchedEffect(permissionState.allPermissionsGranted) {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                }
                
                if (permissionState.allPermissionsGranted) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "scan",
                        modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("scan") {
                                val scannedDevices by viewModel.scannedDevices.collectAsState(initial = emptyList())
                                val isScanning by viewModel.isScanning.collectAsState(initial = false)
                                val isConnected by viewModel.isConnected.collectAsState(initial = false)
                                val isAutoReconnectEnabled by viewModel.isAutoReconnectEnabled.collectAsState(initial = false)
                                
                                ScanScreen(
                                    scannedDevices = scannedDevices,
                                    isScanning = isScanning,
                                    isConnected = isConnected,
                                    isAutoReconnectEnabled = isAutoReconnectEnabled,
                                    onStartScan = { viewModel.startScan() },
                                    onStopScan = { viewModel.stopScan() },
                                    onConnect = { device -> viewModel.connectToDevice(device) },
                                    onNavigateToData = { 
                                        navController.navigate("data") {
                                            popUpTo("scan") { inclusive = true }
                                        }
                                    },
                                    onEnableAutoReconnect = { viewModel.enableAutoReconnect() },
                                    onDisableAutoReconnect = { viewModel.disableAutoReconnect() },
                                    navController = navController
                                )
                            }
                            
                            composable("data") {
                                val eegData by viewModel.eegData.collectAsState(initial = emptyList())
                                val ppgData by viewModel.ppgData.collectAsState(initial = emptyList())
                                val accData by viewModel.accData.collectAsState(initial = emptyList())
                                val batteryData by viewModel.batteryData.collectAsState(initial = null)
                                val isConnected by viewModel.isConnected.collectAsState(initial = false)
                                val isEegStarted by viewModel.isEegStarted.collectAsState(initial = false)
                                val isPpgStarted by viewModel.isPpgStarted.collectAsState(initial = false)
                                val isAccStarted by viewModel.isAccStarted.collectAsState(initial = false)
                                val selectedSensors by viewModel.selectedSensors.collectAsState(initial = emptySet())
                                val isReceivingData by viewModel.isReceivingData.collectAsState(initial = false)
                                val isRecording by viewModel.isRecording.collectAsState(initial = false)
                                val isAutoReconnectEnabled by viewModel.isAutoReconnectEnabled.collectAsState(initial = false)
                                val connectedDeviceName by viewModel.connectedDeviceName.collectAsState(initial = null)
                                val accelerometerMode by viewModel.accelerometerMode.collectAsState(initial = com.example.linkbandsdk.AccelerometerMode.RAW)
                                val processedAccData by viewModel.processedAccData.collectAsState(initial = emptyList())
                                // 배치 수집 관련 상태들 추가
                                val selectedCollectionMode by viewModel.selectedCollectionMode.collectAsState(initial = com.example.linkbandsdk.CollectionMode.SAMPLE_COUNT)
                                
                                DataScreen(
                                    eegData = eegData,
                                    ppgData = ppgData,
                                    accData = accData,
                                    batteryData = batteryData,
                                    isConnected = isConnected,
                                    isEegStarted = isEegStarted,
                                    isPpgStarted = isPpgStarted,
                                    isAccStarted = isAccStarted,
                                    selectedSensors = selectedSensors,
                                    isReceivingData = isReceivingData,
                                    isRecording = isRecording,
                                    isAutoReconnectEnabled = isAutoReconnectEnabled,
                                    connectedDeviceName = connectedDeviceName,
                                    accelerometerMode = accelerometerMode,
                                    processedAccData = processedAccData,
                                    // 배치 수집 관련 매개변수들 추가
                                    selectedCollectionMode = selectedCollectionMode,
                                    getSensorConfiguration = { sensorType ->
                                        viewModel.getSensorConfiguration(sensorType)
                                    },
                                    onDisconnect = { viewModel.disconnect() },
                                    onNavigateToScan = { 
                                        navController.navigate("scan") {
                                            popUpTo("data") { inclusive = true }
                                        }
                                    },
                                    onSelectSensor = { sensor -> viewModel.selectSensor(sensor) },
                                    onDeselectSensor = { sensor -> viewModel.deselectSensor(sensor) },
                                    onStartSelectedSensors = { viewModel.startSelectedSensors() },
                                    onStopSelectedSensors = { viewModel.stopSelectedSensors() },
                                    onStartRecording = { viewModel.startRecording() },
                                    onStopRecording = { viewModel.stopRecording() },
                                    onShowFileList = { navController.navigate("files") },
                                    onToggleAutoReconnect = { 
                                        if (isAutoReconnectEnabled) {
                                            viewModel.disableAutoReconnect()
                                        } else {
                                            viewModel.enableAutoReconnect()
                                        }
                                    },
                                    onSetAccelerometerMode = { mode -> viewModel.setAccelerometerMode(mode) },
                                    // 배치 수집 콜백 함수들 추가
                                    onCollectionModeChange = { mode ->
                                        viewModel.setCollectionMode(mode)
                                    },
                                    onSampleCountChange = { sensorType, count, text ->
                                        viewModel.updateSensorSampleCount(sensorType, count, text)
                                    },
                                    onSecondsChange = { sensorType, seconds, text ->
                                        viewModel.updateSensorSeconds(sensorType, seconds, text)
                                    },
                                    onMinutesChange = { sensorType, minutes, text ->
                                        viewModel.updateSensorMinutes(sensorType, minutes, text)
                                    },
                                    navController = navController
                                )
                            }
                            
                            composable("files") {
                                FileListScreen(
                                    onBack = {
                                        navController.popBackStack()
                                    },
                                    onFileClick = { file ->
                                        // 파일 경로를 URL 인코딩하여 네비게이션에 전달
                                        val encodedPath = java.net.URLEncoder.encode(file.absolutePath, "UTF-8")
                                        navController.navigate("csvViewer/$encodedPath")
                                    }
                                )
                            }
                            
                            composable("csvViewer/{filePath}") { backStackEntry ->
                                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                                val filePath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                                val file = java.io.File(filePath)
                                
                                CsvViewerScreen(
                                    file = file,
                                    onBackClick = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}