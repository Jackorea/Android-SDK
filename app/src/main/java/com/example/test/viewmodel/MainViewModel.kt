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
import com.example.test.data.AccelerometerMode
import com.example.test.data.ProcessedAccData
import com.example.test.data.CollectionMode
import com.example.test.data.SensorBatchConfiguration
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
    
    // 가속도계 모드 관련 StateFlow 추가
    val accelerometerMode: StateFlow<AccelerometerMode> = bleManager.accelerometerMode
    val processedAccData: StateFlow<List<ProcessedAccData>> = bleManager.processedAccData
    
    // 배치 수집 관련 StateFlow 추가
    val selectedCollectionMode: StateFlow<CollectionMode> = bleManager.selectedCollectionMode
    val eegBatchData: StateFlow<List<EegData>> = bleManager.eegBatchData
    val ppgBatchData: StateFlow<List<PpgData>> = bleManager.ppgBatchData
    val accBatchData: StateFlow<List<AccData>> = bleManager.accBatchData
    
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
    
    // 가속도계 모드 제어 함수
    fun setAccelerometerMode(mode: AccelerometerMode) {
        Log.d("MainViewModel", "가속도계 모드 변경: ${mode.description}")
        viewModelScope.launch {
            bleManager.setAccelerometerMode(mode)
        }
    }
    
    // ============ 배치 수집 관련 메서드들 ============
    
    /**
     * 수집 모드를 설정합니다
     */
    fun setCollectionMode(mode: CollectionMode) {
        Log.d("MainViewModel", "수집 모드 변경: ${mode.description}")
        viewModelScope.launch {
            bleManager.setCollectionMode(mode)
        }
    }
    
    /**
     * 특정 센서의 샘플 수 설정을 업데이트합니다
     */
    fun updateSensorSampleCount(sensorType: SensorType, sampleCount: Int, sampleCountText: String) {
        viewModelScope.launch {
            bleManager.updateSensorSampleCount(sensorType, sampleCount, sampleCountText)
        }
    }
    
    /**
     * 특정 센서의 초 단위 설정을 업데이트합니다
     */
    fun updateSensorSeconds(sensorType: SensorType, seconds: Int, secondsText: String) {
        viewModelScope.launch {
            bleManager.updateSensorSeconds(sensorType, seconds, secondsText)
        }
    }
    
    /**
     * 특정 센서의 분 단위 설정을 업데이트합니다
     */
    fun updateSensorMinutes(sensorType: SensorType, minutes: Int, minutesText: String) {
        viewModelScope.launch {
            bleManager.updateSensorMinutes(sensorType, minutes, minutesText)
        }
    }
    
    /**
     * 특정 센서의 현재 설정을 가져옵니다
     */
    fun getSensorConfiguration(sensorType: SensorType): SensorBatchConfiguration? {
        return bleManager.getSensorConfiguration(sensorType)
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