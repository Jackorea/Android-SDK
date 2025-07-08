package com.example.test.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.ble.BleManager
import com.example.test.ble.SensorType
import com.example.test.data.AccData
import com.example.test.data.BatteryData
import com.example.test.data.EegData
import com.example.test.data.PpgData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bleManager = BleManager(application)
    
    val scannedDevices: StateFlow<List<BluetoothDevice>> = bleManager.scannedDevices
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val isConnected: StateFlow<Boolean> = bleManager.isConnected
    val connectedDeviceName: StateFlow<String?> = bleManager.connectedDeviceName
    val eegData: StateFlow<List<EegData>> = bleManager.eegData
    val ppgData: StateFlow<List<PpgData>> = bleManager.ppgData
    val accData: StateFlow<List<AccData>> = bleManager.accData
    val batteryData: StateFlow<BatteryData?> = bleManager.batteryData
    
    // 수동 서비스 제어 상태
    val isEegStarted: StateFlow<Boolean> = bleManager.isEegStarted
    val isPpgStarted: StateFlow<Boolean> = bleManager.isPpgStarted  
    val isAccStarted: StateFlow<Boolean> = bleManager.isAccStarted
    
    // 자동연결 상태
    val isAutoReconnectEnabled: StateFlow<Boolean> = bleManager.isAutoReconnectEnabled
    
    // 센서 선택 상태
    val selectedSensors: StateFlow<Set<SensorType>> = bleManager.selectedSensors
    val isReceivingData: StateFlow<Boolean> = bleManager.isReceivingData
    
    // CSV 기록 상태
    val isRecording: StateFlow<Boolean> = bleManager.isRecording
    
    init {
        // PPG 상태 변경 로그 확인
        viewModelScope.launch {
            isPpgStarted.collect { started ->
                Log.d("MainViewModel", "PPG 상태 변경: $started")
            }
        }
    }
    
    fun startScan() {
        viewModelScope.launch {
            bleManager.startScan()
        }
    }
    
    fun stopScan() {
        viewModelScope.launch {
            bleManager.stopScan()
        }
    }
    
    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            bleManager.connectToDevice(device)
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            bleManager.disconnect()
        }
    }
    
    // 수동 서비스 제어 함수들
    fun startEegService() {
        viewModelScope.launch {
            bleManager.startEegService()
        }
    }
    
    fun stopEegService() {
        viewModelScope.launch {
            bleManager.stopEegService()
        }
    }
    
    fun startPpgService() {
        Log.d("MainViewModel", "PPG 시작 버튼이 클릭되었습니다!")
        viewModelScope.launch {
            bleManager.startPpgService()
        }
    }
    
    fun stopPpgService() {
        viewModelScope.launch {
            bleManager.stopPpgService()
        }
    }
    
    fun startAccService() {
        viewModelScope.launch {
            bleManager.startAccService()
        }
    }
    
    fun stopAccService() {
        viewModelScope.launch {
            bleManager.stopAccService()
        }
    }
    
    // 자동연결 제어 함수들
    fun enableAutoReconnect() {
        viewModelScope.launch {
            bleManager.enableAutoReconnect()
        }
    }
    
    fun disableAutoReconnect() {
        viewModelScope.launch {
            bleManager.disableAutoReconnect()
        }
    }
    
    // 센서 선택 제어 함수들
    fun selectSensor(sensor: SensorType) {
        viewModelScope.launch {
            bleManager.selectSensor(sensor)
        }
    }
    
    fun deselectSensor(sensor: SensorType) {
        viewModelScope.launch {
            bleManager.deselectSensor(sensor)
        }
    }
    
    fun startSelectedSensors() {
        Log.d("MainViewModel", "선택된 센서들 시작 요청")
        viewModelScope.launch {
            bleManager.startSelectedSensors()
        }
    }
    
    fun stopSelectedSensors() {
        Log.d("MainViewModel", "선택된 센서들 중지 요청")
        viewModelScope.launch {
            bleManager.stopSelectedSensors()
        }
    }
    
    // CSV 기록 제어 함수들
    fun startRecording() {
        Log.d("MainViewModel", "CSV 기록 시작 요청")
        viewModelScope.launch {
            bleManager.startRecording()
        }
    }
    
    fun stopRecording() {
        Log.d("MainViewModel", "CSV 기록 중지 요청")
        viewModelScope.launch {
            bleManager.stopRecording()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
} 