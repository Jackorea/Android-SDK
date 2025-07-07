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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.test.ui.DataScreen
import com.example.test.ui.ScanScreen
import com.example.test.ui.theme.TestTheme
import com.example.test.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

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
                
                LaunchedEffect(Unit) {
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
                                val scannedDevices by viewModel.scannedDevices.collectAsState()
                                val isScanning by viewModel.isScanning.collectAsState()
                                val isConnected by viewModel.isConnected.collectAsState()
                                val isAutoReconnectEnabled by viewModel.isAutoReconnectEnabled.collectAsState()
                                
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
                                    onDisableAutoReconnect = { viewModel.disableAutoReconnect() }
                                )
                            }
                            
                            composable("data") {
                                val eegData by viewModel.eegData.collectAsState()
                                val ppgData by viewModel.ppgData.collectAsState()
                                val accData by viewModel.accData.collectAsState()
                                val batteryData by viewModel.batteryData.collectAsState()
                                val isConnected by viewModel.isConnected.collectAsState()
                                val isEegStarted by viewModel.isEegStarted.collectAsState()
                                val isPpgStarted by viewModel.isPpgStarted.collectAsState()
                                val isAccStarted by viewModel.isAccStarted.collectAsState()
                                
                                DataScreen(
                                    eegData = eegData,
                                    ppgData = ppgData,
                                    accData = accData,
                                    batteryData = batteryData,
                                    isConnected = isConnected,
                                    isEegStarted = isEegStarted,
                                    isPpgStarted = isPpgStarted,
                                    isAccStarted = isAccStarted,
                                    onDisconnect = { viewModel.disconnect() },
                                    onNavigateToScan = { 
                                        navController.navigate("scan") {
                                            popUpTo("data") { inclusive = true }
                                        }
                                    },
                                    onStartEeg = { viewModel.startEegService() },
                                    onStopEeg = { viewModel.stopEegService() },
                                    onStartPpg = { viewModel.startPpgService() },
                                    onStopPpg = { viewModel.stopPpgService() },
                                    onStartAcc = { viewModel.startAccService() },
                                    onStopAcc = { viewModel.stopAccService() },
                                    onStartAllSensors = { viewModel.startAllSensors() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}