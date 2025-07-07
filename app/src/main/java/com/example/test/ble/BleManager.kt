package com.example.test.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.util.Log
import com.example.test.data.AccData
import com.example.test.data.BatteryData
import com.example.test.data.EegData
import com.example.test.data.PpgData
import com.example.test.data.SensorDataParser
import com.example.test.data.SensorDataParsingException
import com.example.test.data.SensorConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.roundToInt
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 센서 타입 enum 추가
enum class SensorType {
    EEG, PPG, ACC
}

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    
    // UUID 상수들 (파이썬 코드에서 추출)
    companion object {
        val ACCELEROMETER_SERVICE_UUID = UUID.fromString("75c276c3-8f97-20bc-a143-b354244886d4")
        val ACCELEROMETER_CHAR_UUID = UUID.fromString("d3d46a35-4394-e9aa-5a43-e7921120aaed")
        
        val EEG_NOTIFY_SERVICE_UUID = UUID.fromString("df7b5d95-3afe-00a1-084c-b50895ef4f95")
        val EEG_NOTIFY_CHAR_UUID = UUID.fromString("00ab4d15-66b4-0d8a-824f-8d6f8966c6e5")
        val EEG_WRITE_CHAR_UUID = UUID.fromString("0065cacb-9e52-21bf-a849-99a80d83830e")
        
        val PPG_SERVICE_UUID = UUID.fromString("1cc50ec0-6967-9d84-a243-c2267f924d1f")
        val PPG_CHAR_UUID = UUID.fromString("6c739642-23ba-818b-2045-bfe8970263f6")
        
        val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        
        const val EEG_SAMPLE_RATE = 250
        const val PPG_SAMPLE_RATE = 50
        const val ACC_SAMPLE_RATE = 30
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    
    // 센서 데이터 파서 추가
    private val sensorDataParser = SensorDataParser(SensorConfiguration.default)
    
    private var bluetoothGatt: BluetoothGatt? = null
    
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()
    
    private val _eegData = MutableStateFlow<List<EegData>>(emptyList())
    val eegData: StateFlow<List<EegData>> = _eegData.asStateFlow()
    
    private val _ppgData = MutableStateFlow<List<PpgData>>(emptyList())
    val ppgData: StateFlow<List<PpgData>> = _ppgData.asStateFlow()
    
    private val _accData = MutableStateFlow<List<AccData>>(emptyList())
    val accData: StateFlow<List<AccData>> = _accData.asStateFlow()
    
    private val _batteryData = MutableStateFlow<BatteryData?>(null)
    val batteryData: StateFlow<BatteryData?> = _batteryData.asStateFlow()
    
    private val _isEegStarted = MutableStateFlow(false)
    val isEegStarted: StateFlow<Boolean> = _isEegStarted.asStateFlow()
    
    private val _isPpgStarted = MutableStateFlow(false) 
    val isPpgStarted: StateFlow<Boolean> = _isPpgStarted.asStateFlow()
    
    private val _isAccStarted = MutableStateFlow(false)
    val isAccStarted: StateFlow<Boolean> = _isAccStarted.asStateFlow()
    
    // 자동연결 관련 StateFlow 추가
    private val _isAutoReconnectEnabled = MutableStateFlow(true) // 디폴트로 활성화
    val isAutoReconnectEnabled: StateFlow<Boolean> = _isAutoReconnectEnabled.asStateFlow()
    
    // 마지막 연결된 디바이스 정보 저장
    private var lastConnectedDevice: BluetoothDevice? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private var reconnectRunnable: Runnable? = null
    
    // 서비스 준비 상태 플래그 추가
    private var servicesReady = false
    
    // 센서 선택 상태 관리
    private val _selectedSensors = MutableStateFlow<Set<SensorType>>(emptySet())
    val selectedSensors: StateFlow<Set<SensorType>> = _selectedSensors.asStateFlow()
    
    private val _isReceivingData = MutableStateFlow(false)
    val isReceivingData: StateFlow<Boolean> = _isReceivingData.asStateFlow()
    
    // CSV 기록 상태 관리
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    // CSV 파일 관련 변수들
    private var eegCsvWriter: FileWriter? = null
    private var ppgCsvWriter: FileWriter? = null
    private var accCsvWriter: FileWriter? = null
    private var recordingStartTime: Long = 0
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: return
            
            // "LXB-"로 시작하는 디바이스만 필터링
            if (deviceName.startsWith("LXB-")) {
                val currentDevices = _scannedDevices.value.toMutableList()
                if (!currentDevices.any { it.address == device.address }) {
                    currentDevices.add(device)
                    _scannedDevices.value = currentDevices
                }
            }
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    _isConnected.value = true
                    _connectedDeviceName.value = gatt.device.name
                    // 연결 성공 시 재연결 시도 횟수 리셋
                    reconnectAttempts = 0
                    // 현재 연결된 디바이스를 마지막 연결 디바이스로 저장
                    lastConnectedDevice = gatt.device
                    Log.d("BleManager", "Connected to device: ${gatt.device.name}")
                    // 연결 완료 후 MTU 설정
                    Log.d("BleManager", "Requesting MTU: 247")
                    gatt.requestMtu(247)
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    _isConnected.value = false
                    _connectedDeviceName.value = null
                    // 연결 해제 시 모든 센서 상태 리셋
                    _isEegStarted.value = false
                    _isPpgStarted.value = false
                    _isAccStarted.value = false
                    // 서비스 준비 상태도 리셋
                    servicesReady = false
                    Log.d("BleManager", "Connection disconnected - all sensor states reset")
                    bluetoothGatt = null
                    
                    // 자동연결이 활성화되어 있고 마지막 연결 디바이스가 있으면 재연결 시도
                    if (_isAutoReconnectEnabled.value && lastConnectedDevice != null) {
                        attemptAutoReconnect()
                    }
                }
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("BleManager", "MTU changed to: $mtu, status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleManager", "MTU successfully set to: $mtu")
            } else {
                Log.w("BleManager", "MTU change failed with status: $status")
            }
            // MTU 설정 완료 후 서비스 발견 시작
            handler.postDelayed({
                gatt.discoverServices()
            }, 1000)
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BleManager", "Services discovered, status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services
                Log.d("BleManager", "Found ${services.size} services")
                for (service in services) {
                    Log.d("BleManager", "Service UUID: ${service.uuid}")
                }
                
                // 디바이스 연결 시 모든 센서를 디폴트로 선택
                val allSensors = setOf(SensorType.EEG, SensorType.PPG, SensorType.ACC)
                _selectedSensors.value = allSensors
                Log.d("BleManager", "All sensors selected by default: $allSensors")
                
                // 서비스 발견 후 notification 설정 전 딜레이
                handler.postDelayed({
                    startNotifications(gatt)
                }, 500)
                
                // 서비스 완전 준비 완료 플래그 설정 (2초 후)
                handler.postDelayed({
                    servicesReady = true
                    Log.d("BleManager", "All services are now ready for sensor operations")
                }, 2000)
            } else {
                Log.e("BleManager", "Service discovery failed with status: $status")
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value //데이터 수신 감지
            Log.d("BleManager", "=== Data received from ${characteristic.uuid}, size: ${data?.size ?: 0} ===")
            
            // PPG 데이터인지 특별히 확인
            if (characteristic.uuid == PPG_CHAR_UUID) {
                Log.d("BleManager", "🔴 PPG 데이터 수신! 크기: ${data?.size ?: 0}")
            }
            
            if (data != null && data.isNotEmpty()) {
                // 데이터를 hex로 출력
                val hexString = data.joinToString(" ") { "%02x".format(it) }
                Log.d("BleManager", "Raw data: $hexString")
                
                when (characteristic.uuid) {
                    EEG_NOTIFY_CHAR_UUID -> {
                        Log.d("BleManager", "Processing EEG data: ${data.size} bytes")
                        parseEegData(data)
                    }
                    PPG_CHAR_UUID -> {
                        Log.d("BleManager", "🔴 Processing PPG data: ${data.size} bytes")
                        parsePpgData(data)
                    }
                    ACCELEROMETER_CHAR_UUID -> {
                        Log.d("BleManager", "Processing ACC data: ${data.size} bytes")
                        parseAccData(data)
                    }
                    BATTERY_CHAR_UUID -> {
                        Log.d("BleManager", "Processing Battery data: ${data.size} bytes")
                        parseBatteryData(data)
                    }
                    else -> {
                        Log.w("BleManager", "Unknown characteristic UUID: ${characteristic.uuid}")
                    }
                }
            } else {
                Log.w("BleManager", "Received null or empty data from ${characteristic.uuid}")
            }
        }
        
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d("BleManager", "Characteristic read: ${characteristic.uuid}, status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic.value
                if (data != null && characteristic.uuid == BATTERY_CHAR_UUID) {
                    parseBatteryData(data)
                }
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d("BleManager", "Characteristic write: ${characteristic.uuid}, status: $status")
            // EEG write 명령 완료 로그만 남기고 자동 notification 설정 제거
            if (characteristic.uuid == EEG_WRITE_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleManager", "EEG start/stop command sent successfully")
            }
        }
        
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d("BleManager", "Descriptor write: ${descriptor.uuid}, status: $status")
            // Descriptor 쓰기 완료 처리
        }
    }
    
    fun startScan() {
        if (!_isScanning.value) {
            _scannedDevices.value = emptyList()
            _isScanning.value = true
            bluetoothLeScanner.startScan(scanCallback)
        }
    }
    
    fun stopScan() {
        if (_isScanning.value) {
            _isScanning.value = false
            bluetoothLeScanner.stopScan(scanCallback)
        }
    }
    
    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    
    // 자동연결 제어 함수들
    fun enableAutoReconnect() {
        _isAutoReconnectEnabled.value = true
        Log.d("BleManager", "Auto-reconnect enabled")
    }
    
    fun disableAutoReconnect() {
        _isAutoReconnectEnabled.value = false
        // 진행 중인 재연결 시도 취소
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectAttempts = 0
        Log.d("BleManager", "Auto-reconnect disabled")
    }
    
    private fun attemptAutoReconnect() {
        if (!_isAutoReconnectEnabled.value || lastConnectedDevice == null) {
            return
        }
        
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.w("BleManager", "Max reconnect attempts reached. Auto-reconnect stopped.")
            return
        }
        
        reconnectAttempts++
        // 재연결 딜레이 계산 (3초, 5초, 10초, 20초, 30초)
        val delays = arrayOf(3000L, 5000L, 10000L, 20000L, 30000L)
        val delay = delays.getOrElse(reconnectAttempts - 1) { 30000L }
        
        Log.d("BleManager", "Attempting auto-reconnect ${reconnectAttempts}/${maxReconnectAttempts} in ${delay/1000}s...")
        
        reconnectRunnable = Runnable {
            lastConnectedDevice?.let { device ->
                Log.d("BleManager", "Auto-reconnecting to ${device.name}...")
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            }
        }
        
        reconnectRunnable?.let { handler.postDelayed(it, delay) }
    }
    
    fun disconnect() {
        // 수동 연결 해제 시 자동연결 시도 취소
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectAttempts = 0
        
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isConnected.value = false
        // 수동 연결 해제 시에도 모든 센서 상태 리셋
        _isEegStarted.value = false
        _isPpgStarted.value = false
        _isAccStarted.value = false
        // 서비스 준비 상태도 리셋
        servicesReady = false
        Log.d("BleManager", "Manual disconnect - all sensor states reset")
    }
    
    private fun startNotifications(gatt: BluetoothGatt) {
        Log.d("BleManager", "Connection established - ready for manual service control")
        // 배터리만 즉시 읽기 (파이썬과 동일)
        val batteryChar = gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_CHAR_UUID)
        batteryChar?.let {
            Log.d("BleManager", "Reading battery characteristic")
            gatt.readCharacteristic(it)
        } ?: Log.e("BleManager", "Battery characteristic not found")
        
        // 나머지는 수동으로 시작하도록 변경
        // setupNotifications 자동 호출 제거
    }
    
    // EEG 수동 시작 함수
    fun startEegService() {
        bluetoothGatt?.let { gatt ->
            Log.d("BleManager", "Starting EEG service manually")
            
            // 1. EEG 시작 명령 전송 (바이너리 명령 시도)
            val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
            eegWriteChar?.let {
                Log.d("BleManager", "Sending EEG start command (binary)")
                // 바이너리 명령 시도: 0x01 = start, 0x00 = stop
                it.value = byteArrayOf(0x01)
                gatt.writeCharacteristic(it)
                
                // 2. EEG notification 설정 (파이썬의 toggle_eeg_notify와 동일)
                handler.postDelayed({
                    val eegNotifyChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_NOTIFY_CHAR_UUID)
                    eegNotifyChar?.let { notifyChar ->
                        Log.d("BleManager", "Setting up EEG notification")
                        gatt.setCharacteristicNotification(notifyChar, true)
                        val descriptor = notifyChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.let { desc ->
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(desc)
                            _isEegStarted.value = true
                        } ?: Log.e("BleManager", "EEG descriptor not found")
                    } ?: Log.e("BleManager", "EEG notification characteristic not found")
                }, 200)
            } ?: Log.e("BleManager", "EEG write characteristic not found")
        }
    }
    
    // PPG 수동 시작 함수
    fun startPpgService() {
        Log.d("BleManager", "=== PPG 서비스 시작 요청 ===")
        bluetoothGatt?.let { gatt ->
            Log.d("BleManager", "Starting PPG service manually")
            Log.d("BleManager", "Available services: ${gatt.services.map { it.uuid }}")
            
            // 서비스가 발견되지 않았다면 다시 발견 시도
            if (gatt.services.isEmpty()) {
                Log.w("BleManager", "No services found, trying to discover services again")
                gatt.discoverServices()
                return
            }
            
            val ppgService = gatt.getService(PPG_SERVICE_UUID)
            if (ppgService == null) {
                Log.e("BleManager", "PPG service not found!")
                Log.e("BleManager", "Expected UUID: $PPG_SERVICE_UUID")
                return
            }
            
            val ppgChar = ppgService.getCharacteristic(PPG_CHAR_UUID)
            ppgChar?.let {
                Log.d("BleManager", "Found PPG characteristic: ${it.uuid}")
                Log.d("BleManager", "PPG characteristic properties: ${it.properties}")
                Log.d("BleManager", "Setting up PPG notification")
                
                val notifyResult = gatt.setCharacteristicNotification(it, true)
                Log.d("BleManager", "Set PPG notification result: $notifyResult")
                
                val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.let { desc ->
                    Log.d("BleManager", "Found PPG descriptor: ${desc.uuid}")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val writeResult = gatt.writeDescriptor(desc)
                    Log.d("BleManager", "Write PPG descriptor result: $writeResult")
                    
                    // PPG 데이터 수신 대기 로그 추가
                    handler.postDelayed({
                        Log.d("BleManager", "PPG notification 설정 완료 - 데이터 수신 대기 중...")
                        Log.d("BleManager", "PPG 센서가 피부에 닿아 있는지 확인하세요")
                        
                        // 5초 후 데이터 수신 여부 확인
                        handler.postDelayed({
                            val ppgDataCount = _ppgData.value.size
                            if (ppgDataCount == 0) {
                                Log.w("BleManager", "⚠️ PPG 데이터가 수신되지 않았습니다")
                                Log.w("BleManager", "가능한 원인:")
                                Log.w("BleManager", "1. PPG 센서가 피부에 닿지 않음")
                                Log.w("BleManager", "2. 다른 센서와 함께 시작해야 함")
                                Log.w("BleManager", "3. 디바이스 펌웨어 차이")
                            } else {
                                Log.d("BleManager", "✅ PPG 데이터 수신 성공: ${ppgDataCount}개 샘플")
                            }
                        }, 5000)
                    }, 1000)
                    
                    _isPpgStarted.value = true
                    Log.d("BleManager", "PPG service started successfully")
                } ?: Log.e("BleManager", "PPG descriptor not found")
            } ?: Log.e("BleManager", "PPG characteristic not found")
        } ?: Log.e("BleManager", "BluetoothGatt is null - 연결되지 않음")
    }
    
    // ACC 수동 시작 함수  
    fun startAccService() {
        bluetoothGatt?.let { gatt ->
            Log.d("BleManager", "Starting ACC service manually")
            val accChar = gatt.getService(ACCELEROMETER_SERVICE_UUID)?.getCharacteristic(ACCELEROMETER_CHAR_UUID)
            accChar?.let {
                Log.d("BleManager", "Setting up ACC notification")
                gatt.setCharacteristicNotification(it, true)
                val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                    _isAccStarted.value = true
                } ?: Log.e("BleManager", "ACC descriptor not found")
            } ?: Log.e("BleManager", "ACC characteristic not found")
        }
    }
    
    // 서비스 중지 함수들
    fun stopEegService() {
        bluetoothGatt?.let { gatt ->
            Log.d("BleManager", "Stopping EEG service")
            val eegNotifyChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_NOTIFY_CHAR_UUID)
            eegNotifyChar?.let {
                gatt.setCharacteristicNotification(it, false)
                _isEegStarted.value = false
            }
            
            val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
            eegWriteChar?.let {
                it.value = "stop".toByteArray()
                gatt.writeCharacteristic(it)
            }
        }
    }
    
    fun stopPpgService() {
        bluetoothGatt?.let { gatt ->
            Log.d("BleManager", "Stopping PPG service")
            val ppgChar = gatt.getService(PPG_SERVICE_UUID)?.getCharacteristic(PPG_CHAR_UUID)
            ppgChar?.let {
                gatt.setCharacteristicNotification(it, false)
                _isPpgStarted.value = false
            }
        }
    }
    
    fun stopAccService() {
        bluetoothGatt?.let { gatt ->
            Log.d("BleManager", "Stopping ACC service")
            val accChar = gatt.getService(ACCELEROMETER_SERVICE_UUID)?.getCharacteristic(ACCELEROMETER_CHAR_UUID)
            accChar?.let {
                gatt.setCharacteristicNotification(it, false)
                _isAccStarted.value = false
            }
        }
    }
    
    private fun parseEegData(data: ByteArray) {
        try {
            val readings = sensorDataParser.parseEegData(data)
            
            if (readings.isNotEmpty()) {
                Log.d("BleManager", "EEG parsed successfully: ${readings.size} samples")
                val currentData = _eegData.value.takeLast(1000).toMutableList()
                currentData.addAll(readings)
                _eegData.value = currentData
                Log.d("BleManager", "EEG data updated, total samples: ${_eegData.value.size}")
                
                // 콘솔에 모든 EEG 데이터 샘플 실시간 출력
                readings.forEach { data ->
                    Log.d("EEG_DATA", "📊 EEG | CH1: ${data.channel1.roundToInt()}µV | CH2: ${data.channel2.roundToInt()}µV | Lead: ${data.leadOff}")
                    // CSV 파일에 저장
                    writeEegToCsv(data)
                }
            } else {
                Log.w("BleManager", "No EEG samples parsed")
            }
        } catch (e: SensorDataParsingException) {
            Log.e("BleManager", "EEG parsing error: ${e.message}")
        }
    }
    
    private fun parsePpgData(data: ByteArray) {
        try {
            val readings = sensorDataParser.parsePpgData(data)
            
            if (readings.isNotEmpty()) {
                Log.d("BleManager", "PPG parsed successfully: ${readings.size} samples")
                val currentData = _ppgData.value.takeLast(500).toMutableList()
                currentData.addAll(readings)
                _ppgData.value = currentData
                Log.d("BleManager", "PPG data updated, total samples: ${_ppgData.value.size}")
                
                // 콘솔에 모든 PPG 데이터 샘플 실시간 출력
                readings.forEach { data ->
                    Log.d("PPG_DATA", "🔴 PPG | Red: ${data.red} | IR: ${data.ir}")
                    // CSV 파일에 저장
                    writePpgToCsv(data)
                }
            } else {
                Log.w("BleManager", "No PPG samples parsed")
            }
        } catch (e: SensorDataParsingException) {
            Log.e("BleManager", "PPG parsing error: ${e.message}")
        }
    }
    
    private fun parseAccData(data: ByteArray) {
        try {
            val readings = sensorDataParser.parseAccelerometerData(data)
            
            if (readings.isNotEmpty()) {
                Log.d("BleManager", "ACC parsed successfully: ${readings.size} samples")
                val currentData = _accData.value.takeLast(300).toMutableList()
                currentData.addAll(readings)
                _accData.value = currentData
                
                // 콘솔에 모든 ACC 데이터 샘플 실시간 출력
                readings.forEach { data ->
                    Log.d("ACC_DATA", "🚀 ACC | X: ${data.x} | Y: ${data.y} | Z: ${data.z}")
                    // CSV 파일에 저장
                    writeAccToCsv(data)
                }
            }
        } catch (e: SensorDataParsingException) {
            Log.e("BleManager", "ACC parsing error: ${e.message}")
        }
    }
    
    private fun parseBatteryData(data: ByteArray) {
        try {
            val batteryReading = sensorDataParser.parseBatteryData(data)
            _batteryData.value = batteryReading
            
            // 콘솔에 배터리 데이터 출력
            Log.d("BATTERY_DATA", "🔋 Battery Level: ${batteryReading.level}%")
        } catch (e: SensorDataParsingException) {
            Log.e("BleManager", "Battery parsing error: ${e.message}")
        }
    }
    
    // 센서 선택 제어 함수들
    fun selectSensor(sensor: SensorType) {
        val currentSelected = _selectedSensors.value.toMutableSet()
        currentSelected.add(sensor)
        _selectedSensors.value = currentSelected
        Log.d("BleManager", "Sensor selected: $sensor, current selection: $currentSelected")
    }
    
    fun deselectSensor(sensor: SensorType) {
        val currentSelected = _selectedSensors.value.toMutableSet()
        currentSelected.remove(sensor)
        _selectedSensors.value = currentSelected
        Log.d("BleManager", "Sensor deselected: $sensor, current selection: $currentSelected")
    }
    
    fun startSelectedSensors() {
        val selectedSensors = _selectedSensors.value
        if (selectedSensors.isEmpty()) {
            Log.w("BleManager", "No sensors selected")
            return
        }
        
        Log.d("BleManager", "=== 선택된 센서들 시작: $selectedSensors ===")
        Log.d("BleManager", "Services ready: $servicesReady")
        
        bluetoothGatt?.let { gatt ->
            
            // 서비스가 완전히 준비되지 않았으면 추가 딜레이
            val initialDelay = if (!servicesReady) {
                Log.d("BleManager", "Services not fully ready, adding initial delay...")
                1500L // 1.5초 추가 딜레이
            } else {
                0L
            }
            
            handler.postDelayed({
                Log.d("BleManager", "Starting sensor initialization sequence...")
                
                // 파이썬과 동일한 방식: 각 센서별 독립적 활성화
                var currentDelay = 0L
                
                // 1. EEG 센서 활성화 (선택된 경우)
                if (selectedSensors.contains(SensorType.EEG)) {
                    handler.postDelayed({
                        Log.d("BleManager", "🧠 Starting EEG sensor...")
                        val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
                        eegWriteChar?.let {
                            Log.d("BleManager", "Sending EEG start command...")
                            it.value = "start".toByteArray()
                            gatt.writeCharacteristic(it)
                            
                            // EEG notification 설정 (200ms 후)
                            handler.postDelayed({
                                val eegNotifyChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_NOTIFY_CHAR_UUID)
                                eegNotifyChar?.let { 
                                    setupNotification(gatt, it, "EEG")
                                    _isEegStarted.value = true
                                    Log.d("BleManager", "✅ EEG activated and notification enabled")
                                }
                            }, 200)
                        } ?: Log.e("BleManager", "EEG write characteristic not found")
                    }, currentDelay)
                    currentDelay += 2000 // EEG 완전 안정화를 위해 2초 대기
                } else {
                    // EEG가 선택되지 않았어도 디바이스 활성화를 위해 EEG write 전송
                    handler.postDelayed({
                        Log.d("BleManager", "🔧 Activating device (EEG write for system activation)...")
                        val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
                        eegWriteChar?.let {
                            it.value = "start".toByteArray()
                            gatt.writeCharacteristic(it)
                        }
                    }, currentDelay)
                    currentDelay += 2000
                }
                
                // 2. ACC 센서 활성화 (선택된 경우) - 2번째 센서 특별 처리
                if (selectedSensors.contains(SensorType.ACC)) {
                    handler.postDelayed({
                        Log.d("BleManager", "🚀 Starting ACC sensor (2nd sensor - special handling)...")
                        
                        // ACC 서비스와 특성 재확인
                        val accService = gatt.getService(ACCELEROMETER_SERVICE_UUID)
                        if (accService == null) {
                            Log.e("BleManager", "❌ ACC service not found during activation!")
                            return@postDelayed
                        }
                        
                        val accChar = accService.getCharacteristic(ACCELEROMETER_CHAR_UUID)
                        accChar?.let { 
                            Log.d("BleManager", "ACC characteristic found, properties: ${it.properties}")
                            
                            // ACC notification 설정 전 추가 대기 (2번째 센서 안정화)
                            handler.postDelayed({
                                setupNotification(gatt, it, "ACC")
                                _isAccStarted.value = true
                                Log.d("BleManager", "✅ ACC notification enabled (2nd sensor with extra delay)")
                                
                                // ACC 데이터 수신 확인 (5초 후)
                                handler.postDelayed({
                                    val accCount = _accData.value.size
                                    if (accCount == 0) {
                                        Log.w("BleManager", "⚠️ ACC 데이터 수신되지 않음 (2번째 센서 문제)")
                                        Log.w("BleManager", "💡 디바이스를 움직여서 가속도 변화를 만들어보세요")
                                    } else {
                                        Log.d("BleManager", "🎉 ACC 데이터 수신 성공: ${accCount}개 샘플")
                                    }
                                }, 5000)
                                
                            }, 800) // ACC notification 설정 전 800ms 추가 대기
                            
                        } ?: Log.e("BleManager", "❌ ACC characteristic not found during activation!")
                    }, currentDelay)
                    currentDelay += 2500 // ACC는 더 긴 대기 시간 (2.5초)
                }
                
                // 3. PPG 센서 활성화 (가장 마지막, 선택된 경우)
                if (selectedSensors.contains(SensorType.PPG)) {
                    handler.postDelayed({
                        Log.d("BleManager", "❤️ Starting PPG sensor (final step)...")
                        
                        // PPG 서비스와 특성 다시 확인
                        val ppgService = gatt.getService(PPG_SERVICE_UUID)
                        if (ppgService == null) {
                            Log.e("BleManager", "❌ PPG service not found during activation!")
                            return@postDelayed
                        }
                        
                        val ppgChar = ppgService.getCharacteristic(PPG_CHAR_UUID)
                        ppgChar?.let { 
                            Log.d("BleManager", "PPG characteristic found, properties: ${it.properties}")
                            
                            // PPG notification 설정 전 추가 대기
                            handler.postDelayed({
                                setupNotification(gatt, it, "PPG")
                                _isPpgStarted.value = true
                                Log.d("BleManager", "✅ PPG notification enabled (with extra delay)")
                                
                                // PPG 데이터 수신 확인 (5초 후)
                                handler.postDelayed({
                                    val ppgCount = _ppgData.value.size
                                    if (ppgCount == 0) {
                                        Log.w("BleManager", "⚠️ PPG 데이터 여전히 수신되지 않음")
                                        Log.w("BleManager", "💡 PPG 센서가 피부에 직접 닿아있는지 확인하세요")
                                    } else {
                                        Log.d("BleManager", "🎉 PPG 데이터 수신 성공: ${ppgCount}개 샘플")
                                    }
                                }, 5000)
                                
                            }, 500) // notification 설정 전 추가 500ms 대기
                            
                        } ?: Log.e("BleManager", "❌ PPG characteristic not found during activation!")
                    }, currentDelay)
                    currentDelay += 2000 // PPG 대기 시간
                }
                
                // 모든 센서 활성화 완료 표시
                handler.postDelayed({
                    _isReceivingData.value = true
                    Log.d("BleManager", "🚀 All selected sensors activation sequence completed!")
                }, currentDelay)
                
                // 데이터 수신 확인 (모든 센서 활성화 후 10초 후)
                handler.postDelayed({
                    val eegCount = _eegData.value.size
                    val ppgCount = _ppgData.value.size
                    val accCount = _accData.value.size
                    
                    Log.d("BleManager", "=== 선택된 센서 데이터 수신 결과 ===")
                    if (selectedSensors.contains(SensorType.EEG)) Log.d("BleManager", "EEG: ${eegCount}개 샘플")
                    if (selectedSensors.contains(SensorType.PPG)) Log.d("BleManager", "PPG: ${ppgCount}개 샘플")
                    if (selectedSensors.contains(SensorType.ACC)) Log.d("BleManager", "ACC: ${accCount}개 샘플")
                    
                    val activeCount = listOf(
                        if (selectedSensors.contains(SensorType.EEG)) eegCount else -1,
                        if (selectedSensors.contains(SensorType.PPG)) ppgCount else -1,
                        if (selectedSensors.contains(SensorType.ACC)) accCount else -1
                    ).count { it > 0 }
                    
                    if (activeCount == 0) {
                        Log.w("BleManager", "⚠️ 선택된 센서에서 데이터 수신 실패")
                        Log.w("BleManager", "디바이스 착용 상태와 피부 접촉을 확인하세요")
                    } else {
                        Log.d("BleManager", "✅ ${activeCount}개 센서에서 데이터 수신 성공!")
                    }
                }, currentDelay + 10000)
                
            }, initialDelay)
        }
    }
    
    fun stopSelectedSensors() {
        val selectedSensors = _selectedSensors.value
        Log.d("BleManager", "=== 선택된 센서들 중지: $selectedSensors ===")
        
        bluetoothGatt?.let { gatt ->
            selectedSensors.forEach { sensor ->
                when (sensor) {
                    SensorType.EEG -> {
                        val eegNotifyChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_NOTIFY_CHAR_UUID)
                        eegNotifyChar?.let { gatt.setCharacteristicNotification(it, false) }
                        val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
                        eegWriteChar?.let {
                            // 파이썬과 동일하게 'stop' 문자열 전송
                            it.value = "stop".toByteArray()
                            gatt.writeCharacteristic(it)
                            Log.d("BleManager", "EEG stop command sent")
                        }
                        _isEegStarted.value = false
                    }
                    SensorType.PPG -> {
                        val ppgChar = gatt.getService(PPG_SERVICE_UUID)?.getCharacteristic(PPG_CHAR_UUID)
                        ppgChar?.let { 
                            gatt.setCharacteristicNotification(it, false)
                            Log.d("BleManager", "PPG notification disabled")
                        }
                        _isPpgStarted.value = false
                    }
                    SensorType.ACC -> {
                        val accChar = gatt.getService(ACCELEROMETER_SERVICE_UUID)?.getCharacteristic(ACCELEROMETER_CHAR_UUID)
                        accChar?.let { 
                            gatt.setCharacteristicNotification(it, false)
                            Log.d("BleManager", "ACC notification disabled")
                        }
                        _isAccStarted.value = false
                    }
                }
            }
        }
        
        _isReceivingData.value = false
        Log.d("BleManager", "🛑 All selected sensors stopped")
    }
    
    private fun setupNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, sensorName: String) {
        Log.d("BleManager", "Setting up $sensorName notification")
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        descriptor?.let { desc ->
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(desc)
        } ?: Log.e("BleManager", "$sensorName descriptor not found")
    }
    
    // CSV 기록 제어 함수들
    fun startRecording() {
        if (_isRecording.value) {
            Log.w("BleManager", "Recording already in progress")
            return
        }
        
        val selectedSensors = _selectedSensors.value
        if (selectedSensors.isEmpty()) {
            Log.w("BleManager", "No sensors selected for recording")
            return
        }
        
        try {
            recordingStartTime = System.currentTimeMillis()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            
            // 내장 저장공간의 Downloads 폴더에 CSV 파일 생성
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            // LinkBand 전용 폴더 생성
            val linkBandDir = File(downloadsDir, "LinkBand")
            if (!linkBandDir.exists()) {
                linkBandDir.mkdirs()
            }
            
            // 선택된 센서에 대해서만 CSV 파일 생성
            val createdFiles = mutableListOf<String>()
            
            if (selectedSensors.contains(SensorType.EEG)) {
                val eegFile = File(linkBandDir, "LinkBand_EEG_${timestamp}.csv")
                eegCsvWriter = FileWriter(eegFile)
                eegCsvWriter?.write("Timestamp,Channel1_uV,Channel2_uV,LeadOff\n")
                createdFiles.add("EEG=${eegFile.name}")
                Log.d("BleManager", "EEG CSV file created: ${eegFile.name}")
            }
            
            if (selectedSensors.contains(SensorType.PPG)) {
                val ppgFile = File(linkBandDir, "LinkBand_PPG_${timestamp}.csv")
                ppgCsvWriter = FileWriter(ppgFile)
                ppgCsvWriter?.write("Timestamp,Red,IR\n")
                createdFiles.add("PPG=${ppgFile.name}")
                Log.d("BleManager", "PPG CSV file created: ${ppgFile.name}")
            }
            
            if (selectedSensors.contains(SensorType.ACC)) {
                val accFile = File(linkBandDir, "LinkBand_ACC_${timestamp}.csv")
                accCsvWriter = FileWriter(accFile)
                accCsvWriter?.write("Timestamp,X,Y,Z\n")
                createdFiles.add("ACC=${accFile.name}")
                Log.d("BleManager", "ACC CSV file created: ${accFile.name}")
            }
            
            _isRecording.value = true
            Log.d("BleManager", "CSV recording started at: ${linkBandDir.absolutePath}")
            Log.d("BleManager", "Created files for selected sensors: ${createdFiles.joinToString(", ")}")
            
        } catch (e: Exception) {
            Log.e("BleManager", "Failed to start CSV recording", e)
            stopRecording()
        }
    }
    
    fun stopRecording() {
        if (!_isRecording.value) {
            Log.w("BleManager", "No recording in progress")
            return
        }
        
        try {
            // 생성된 파일들만 닫기
            eegCsvWriter?.close()
            ppgCsvWriter?.close()
            accCsvWriter?.close()
            
            eegCsvWriter = null
            ppgCsvWriter = null
            accCsvWriter = null
            
            _isRecording.value = false
            
            val recordingDuration = (System.currentTimeMillis() - recordingStartTime) / 1000.0
            Log.d("BleManager", "CSV recording stopped. Duration: ${recordingDuration}s")
            
        } catch (e: Exception) {
            Log.e("BleManager", "Error stopping CSV recording", e)
        }
    }
    
    private fun writeEegToCsv(data: EegData) {
        if (_isRecording.value && eegCsvWriter != null && _selectedSensors.value.contains(SensorType.EEG)) {
            try {
                eegCsvWriter?.write("${data.timestamp},${data.channel1},${data.channel2},${data.leadOff}\n")
                eegCsvWriter?.flush()
            } catch (e: Exception) {
                Log.e("BleManager", "Error writing EEG to CSV", e)
            }
        }
    }
    
    private fun writePpgToCsv(data: PpgData) {
        if (_isRecording.value && ppgCsvWriter != null && _selectedSensors.value.contains(SensorType.PPG)) {
            try {
                ppgCsvWriter?.write("${data.timestamp},${data.red},${data.ir}\n")
                ppgCsvWriter?.flush()
            } catch (e: Exception) {
                Log.e("BleManager", "Error writing PPG to CSV", e)
            }
        }
    }
    
    private fun writeAccToCsv(data: AccData) {
        if (_isRecording.value && accCsvWriter != null && _selectedSensors.value.contains(SensorType.ACC)) {
            try {
                accCsvWriter?.write("${data.timestamp},${data.x},${data.y},${data.z}\n")
                accCsvWriter?.flush()
            } catch (e: Exception) {
                Log.e("BleManager", "Error writing ACC to CSV", e)
            }
        }
    }
} 