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
    
    // 센서 활성화 큐 관리 변수들 추가
    private var sensorActivationQueue = mutableListOf<SensorType>()
    private var currentActivatingSensor: SensorType? = null
    private var sensorTimeoutRunnable: Runnable? = null
    private val sensorTimeoutMs = 8000L // 8초 타임아웃
    
    // 각 센서별 마지막 데이터 수신 크기 추적
    private var lastEegDataSize = 0
    private var lastPpgDataSize = 0  
    private var lastAccDataSize = 0
    
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
                    // 연결 완료 후 최대 MTU 설정 (515바이트)
                    Log.d("BleManager", "Requesting maximum MTU: 515")
                    gatt.requestMtu(515)
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    // 기록 중이면 기록 중지 (연결 해제 시)
                    if (_isRecording.value) {
                        Log.d("BleManager", "Stopping recording due to disconnection")
                        stopRecording()
                    }
                    
                    _isConnected.value = false
                    _connectedDeviceName.value = null
                    // 연결 해제 시 모든 센서 상태 리셋
                    _isEegStarted.value = false
                    _isPpgStarted.value = false
                    _isAccStarted.value = false
                    // 수집 상태도 리셋
                    _isReceivingData.value = false
                    // 서비스 준비 상태도 리셋
                    servicesReady = false
                    Log.d("BleManager", "Connection disconnected - all sensor states, collection, and recording stopped")
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
            // MTU 설정 완료 후 서비스 발견 시작 (안정성을 위해 복원)
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
                
                // 서비스 발견 후 notification 설정 전 딜레이 (안정성 복원)
                handler.postDelayed({
                    startNotifications(gatt)
                }, 500)
                
                // 서비스 완전 준비 완료 플래그 설정 (안정성 복원)
                handler.postDelayed({
                    servicesReady = true
                    Log.d("BleManager", "All services are now ready for sensor operations")
                }, 2000)
            } else {
                Log.e("BleManager", "Service discovery failed with status: $status")
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            
            if (data != null && data.isNotEmpty()) {
                when (characteristic.uuid) {
                    EEG_NOTIFY_CHAR_UUID -> {
                        parseEegData(data)
                    }
                    PPG_CHAR_UUID -> {
                        parsePpgData(data)
                    }
                    ACCELEROMETER_CHAR_UUID -> {
                        parseAccData(data)
                    }
                    BATTERY_CHAR_UUID -> {
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
        
        // 기록 중이면 기록 중지
        if (_isRecording.value) {
            Log.d("BleManager", "Stopping recording due to disconnect")
            stopRecording()
        }
        
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isConnected.value = false
        // 수동 연결 해제 시에도 모든 센서 상태 리셋
        _isEegStarted.value = false
        _isPpgStarted.value = false
        _isAccStarted.value = false
        // 수집 상태도 리셋
        _isReceivingData.value = false
        // 서비스 준비 상태도 리셋
        servicesReady = false
        Log.d("BleManager", "Manual disconnect - all sensor states, collection, and recording stopped")
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
                Log.d("BleManager", "EEG: ${readings.size} samples")
                val currentData = _eegData.value.takeLast(1000).toMutableList()
                currentData.addAll(readings)
                _eegData.value = currentData
                
                // 데이터 수신 확인 (새로운 데이터가 들어왔는지 확인)
                if (_eegData.value.size > lastEegDataSize) {
                    onSensorDataReceived(SensorType.EEG)
                }
                
                // CSV 파일에 저장
                readings.forEach { data ->
                    writeEegToCsv(data)
                }
            }
        } catch (e: SensorDataParsingException) {
            Log.e("BleManager", "EEG parsing error: ${e.message}")
        }
    }
    
    private fun parsePpgData(data: ByteArray) {
        try {
            val readings = sensorDataParser.parsePpgData(data)
            
            if (readings.isNotEmpty()) {
                Log.d("BleManager", "PPG: ${readings.size} samples")
                val currentData = _ppgData.value.takeLast(500).toMutableList()
                currentData.addAll(readings)
                _ppgData.value = currentData
                
                // 데이터 수신 확인 (새로운 데이터가 들어왔는지 확인)
                if (_ppgData.value.size > lastPpgDataSize) {
                    onSensorDataReceived(SensorType.PPG)
                }
                
                // CSV 파일에 저장
                readings.forEach { data ->
                    writePpgToCsv(data)
                }
            }
        } catch (e: SensorDataParsingException) {
            Log.e("BleManager", "PPG parsing error: ${e.message}")
        }
    }
    
    private fun parseAccData(data: ByteArray) {
        try {
            val readings = sensorDataParser.parseAccelerometerData(data)
            
            if (readings.isNotEmpty()) {
                Log.d("BleManager", "ACC: ${readings.size} samples")
                val currentData = _accData.value.takeLast(300).toMutableList()
                currentData.addAll(readings)
                _accData.value = currentData
                
                // 데이터 수신 확인 (새로운 데이터가 들어왔는지 확인)
                if (_accData.value.size > lastAccDataSize) {
                    onSensorDataReceived(SensorType.ACC)
                }
                
                // CSV 파일에 저장
                readings.forEach { data ->
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
    
    private fun setupNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, sensorName: String) {
        Log.d("BleManager", "Setting up $sensorName notification")
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        descriptor?.let { desc ->
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(desc)
        } ?: Log.e("BleManager", "$sensorName descriptor not found")
    }
    
    // 모든 센서 notification 비활성화 헬퍼 함수 (스위프트 방식과 동일)
    private fun disableAllSensorNotifications() {
        bluetoothGatt?.let { gatt ->
            Log.d("BleManager", "Disabling all sensor notifications")
            
            // 모든 pending handler 작업 취소 (센서 재활성화 방지)
            handler.removeCallbacksAndMessages(null)
            Log.d("BleManager", "🛑 All pending handler callbacks cancelled in disable function")
            
            // 즉시 PPG 강력 비활성화 (가장 먼저 실행)
            emergencyDisablePpg(gatt)
            
            // 각 센서 타입에 대해 setNotifyValue(false) 실행 (스위프트와 동일한 방식)
            setNotifyValue(false, SensorType.EEG, gatt)
            setNotifyValue(false, SensorType.PPG, gatt)
            setNotifyValue(false, SensorType.ACC, gatt)
            
            // 모든 센서에 대해 추가적인 강력한 비활성화 시도
            handler.postDelayed({
                forceDisableEegSensor(gatt)
                forceDisablePpgSensor(gatt)
                forceDisableAccSensor(gatt)
            }, 500)
            
            // 추가로 PPG만 한번 더 강력하게 비활성화
            handler.postDelayed({
                emergencyDisablePpg(gatt)
                forceDisablePpgSensor(gatt)
            }, 1500)
            
            // 모든 센서 상태 비활성화
            _isEegStarted.value = false
            _isPpgStarted.value = false
            _isAccStarted.value = false
            _isReceivingData.value = false
            
            Log.d("BleManager", "All sensor notifications disabled")
        }
    }
    
    // PPG 긴급 비활성화 (즉시 실행)
    private fun emergencyDisablePpg(gatt: BluetoothGatt) {
        Log.d("BleManager", "🔴 Emergency PPG disable")
        try {
            val ppgService = gatt.getService(PPG_SERVICE_UUID)
            val ppgChar = ppgService?.getCharacteristic(PPG_CHAR_UUID)
            
            ppgChar?.let { char ->
                // 즉시 notification 비활성화
                gatt.setCharacteristicNotification(char, false)
                Log.d("BleManager", "🔴 PPG notification immediately disabled")
                
                // descriptor 즉시 비활성화
                val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.let { desc ->
                    desc.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                    Log.d("BleManager", "🔴 PPG descriptor immediately disabled")
                }
            } ?: Log.e("BleManager", "🔴 PPG characteristic not found for emergency disable")
        } catch (e: Exception) {
            Log.e("BleManager", "🔴 Emergency PPG disable failed: ${e.message}")
        }
    }
    
    // EEG 센서 강력한 비활성화
    private fun forceDisableEegSensor(gatt: BluetoothGatt) {
        val eegService = gatt.getService(EEG_NOTIFY_SERVICE_UUID)
        val eegChar = eegService?.getCharacteristic(EEG_NOTIFY_CHAR_UUID)
        
        eegChar?.let { char ->
            gatt.setCharacteristicNotification(char, false)
            val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.let { desc ->
                desc.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
                
                // 3차 시도: 추가 딜레이 후 다시 한번 비활성화
                handler.postDelayed({
                    gatt.setCharacteristicNotification(char, false)
                    desc.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }, 1000)
            }
        }
        
        // EEG stop 명령 전송
        val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
        eegWriteChar?.let {
            it.value = "stop".toByteArray()
            gatt.writeCharacteristic(it)
            
            // 추가로 한번 더 stop 명령 전송
            handler.postDelayed({
                it.value = "stop".toByteArray()
                gatt.writeCharacteristic(it)
            }, 800)
        }
    }
    
    // PPG 센서 강력한 비활성화
    private fun forceDisablePpgSensor(gatt: BluetoothGatt) {
        val ppgService = gatt.getService(PPG_SERVICE_UUID)
        val ppgChar = ppgService?.getCharacteristic(PPG_CHAR_UUID)
        
        ppgChar?.let { char ->
            gatt.setCharacteristicNotification(char, false)
            val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.let { desc ->
                desc.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
                
                // 3차 시도: 추가 딜레이 후 다시 한번 비활성화
                handler.postDelayed({
                    gatt.setCharacteristicNotification(char, false)
                    desc.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }, 1000)
            }
        }
    }
    
    // ACC 센서 강력한 비활성화
    private fun forceDisableAccSensor(gatt: BluetoothGatt) {
        val accService = gatt.getService(ACCELEROMETER_SERVICE_UUID)
        val accChar = accService?.getCharacteristic(ACCELEROMETER_CHAR_UUID)
        
        accChar?.let { char ->
            gatt.setCharacteristicNotification(char, false)
            val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.let { desc ->
                desc.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
                
                // 3차 시도: 추가 딜레이 후 다시 한번 비활성화
                handler.postDelayed({
                    gatt.setCharacteristicNotification(char, false)
                    desc.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }, 1000)
            }
        }
    }
    
    // 스위프트의 setNotifyValue(_:for:) 메서드와 동일한 기능
    private fun setNotifyValue(enabled: Boolean, sensorType: SensorType, gatt: BluetoothGatt) {
        val (serviceUUID, characteristicUUID) = when (sensorType) {
            SensorType.EEG -> Pair(EEG_NOTIFY_SERVICE_UUID, EEG_NOTIFY_CHAR_UUID)
            SensorType.PPG -> Pair(PPG_SERVICE_UUID, PPG_CHAR_UUID)
            SensorType.ACC -> Pair(ACCELEROMETER_SERVICE_UUID, ACCELEROMETER_CHAR_UUID)
        }
        
        // 서비스에서 해당 characteristic 찾기
        for (service in gatt.services ?: emptyList()) {
            for (characteristic in service.characteristics ?: emptyList()) {
                if (characteristic.uuid == characteristicUUID) {
                    gatt.setCharacteristicNotification(characteristic, enabled)
                    
                    // descriptor 설정하여 펌웨어에 notify 활성화/비활성화 명령 전송
                    val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor?.let { desc ->
                        desc.value = if (enabled) {
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        } else {
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        }
                        gatt.writeDescriptor(desc)
                    }
                    
                    // EEG의 경우 추가로 stop 명령 전송
                    if (sensorType == SensorType.EEG && !enabled) {
                        val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
                        eegWriteChar?.let {
                            it.value = "stop".toByteArray()
                            gatt.writeCharacteristic(it)
                        }
                    }
                    
                    return // characteristic 찾았으므로 종료
                }
            }
        }
        
        Log.w("BleManager", "Characteristic not found for ${sensorType.name}")
    }
    
    fun startSelectedSensors() {
        val selectedSensors = _selectedSensors.value
        if (selectedSensors.isEmpty()) {
            Log.w("BleManager", "No sensors selected")
            return
        }
        
        Log.d("BleManager", "=== 센서 큐 기반 활성화 시작: $selectedSensors ===")
        
        bluetoothGatt?.let { gatt ->
            
            // 1단계: 모든 센서 notification 비활성화 (펌웨어 데이터 전송 중단)
            Log.d("BleManager", "1단계: 모든 센서 notification 비활성화")
            disableAllSensorNotifications()
            
            // 2단계: 선택된 센서들을 큐에 추가하고 순차 활성화 시작
            handler.postDelayed({
                Log.d("BleManager", "2단계: 센서 큐 생성 및 순차 활성화 시작")
                
                // 큐 초기화
                sensorActivationQueue.clear()
                currentActivatingSensor = null
                
                // 디바이스 활성화를 위해 항상 EEG write 명령 전송 (선택되지 않아도)
                val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
                eegWriteChar?.let {
                    it.value = "start".toByteArray()
                    gatt.writeCharacteristic(it)
                    Log.d("BleManager", "Device activation command sent via EEG write")
                }
                
                // 선택된 센서들을 순서대로 큐에 추가 (EEG → ACC → PPG 순서)
                if (selectedSensors.contains(SensorType.EEG)) {
                    sensorActivationQueue.add(SensorType.EEG)
                }
                if (selectedSensors.contains(SensorType.ACC)) {
                    sensorActivationQueue.add(SensorType.ACC)
                }
                if (selectedSensors.contains(SensorType.PPG)) {
                    sensorActivationQueue.add(SensorType.PPG)
                }
                
                Log.d("BleManager", "센서 활성화 큐 생성됨: $sensorActivationQueue")
                
                // 서비스가 완전히 준비되지 않았으면 추가 딜레이
                val initialDelay = if (!servicesReady) {
                    Log.d("BleManager", "Services not fully ready, adding initial delay...")
                    1500L
                } else {
                    500L
                }
                
                // 큐 기반 순차 활성화 시작
                handler.postDelayed({
                    activateNextSensorInQueue()
                }, initialDelay)
                
            }, 1000) // 1단계 완료 후 1초 대기
        }
    }
    
    fun stopSelectedSensors() {
        Log.d("BleManager", "=== 수집 중지: 모든 센서 펌웨어 notify 중단 ===")
        
        // 모든 pending handler 작업 취소 (센서 활성화 큐 포함)
        handler.removeCallbacksAndMessages(null)
        Log.d("BleManager", "🛑 All pending handler callbacks cancelled")
        
        // 센서 활성화 큐 상태 초기화
        sensorActivationQueue.clear()
        currentActivatingSensor = null
        sensorTimeoutRunnable = null
        
        // 데이터 크기 추적 변수 초기화
        lastEegDataSize = 0
        lastPpgDataSize = 0
        lastAccDataSize = 0
        
        // 모든 센서 notification 비활성화 (펌웨어 데이터 전송 완전 중단)
        disableAllSensorNotifications()
        
        Log.d("BleManager", "🛑 All sensors stopped - firmware data transmission completely stopped, queue cleared")
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
                eegCsvWriter?.write("Timestamp_ms,Ch1_Raw,Ch2_Raw,Channel1_uV,Channel2_uV,LeadOff\n")
                createdFiles.add("EEG=${eegFile.name}")
                Log.d("BleManager", "EEG CSV file created: ${eegFile.name}")
            }
            
            if (selectedSensors.contains(SensorType.PPG)) {
                val ppgFile = File(linkBandDir, "LinkBand_PPG_${timestamp}.csv")
                ppgCsvWriter = FileWriter(ppgFile)
                ppgCsvWriter?.write("Timestamp_ms,Red,IR\n")
                createdFiles.add("PPG=${ppgFile.name}")
                Log.d("BleManager", "PPG CSV file created: ${ppgFile.name}")
            }
            
            if (selectedSensors.contains(SensorType.ACC)) {
                val accFile = File(linkBandDir, "LinkBand_ACC_${timestamp}.csv")
                accCsvWriter = FileWriter(accFile)
                accCsvWriter?.write("Timestamp_ms,X,Y,Z\n")
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
                // leadOff를 숫자로 변환: true -> 1, false -> 0
                val leadOffValue = if (data.leadOff) 1 else 0
                // 타임스탬프를 밀리초 단위로 저장 (더 읽기 쉽고 분석하기 좋음)
                eegCsvWriter?.write("${data.timestamp.time},${data.ch1Raw},${data.ch2Raw},${data.channel1},${data.channel2},$leadOffValue\n")
                eegCsvWriter?.flush()
            } catch (e: Exception) {
                Log.e("BleManager", "Error writing EEG to CSV", e)
            }
        }
    }
    
    private fun writePpgToCsv(data: PpgData) {
        if (_isRecording.value && ppgCsvWriter != null && _selectedSensors.value.contains(SensorType.PPG)) {
            try {
                // 타임스탬프를 밀리초 단위로 저장 (더 읽기 쉽고 분석하기 좋음)
                ppgCsvWriter?.write("${data.timestamp.time},${data.red},${data.ir}\n")
                ppgCsvWriter?.flush()
            } catch (e: Exception) {
                Log.e("BleManager", "Error writing PPG to CSV", e)
            }
        }
    }
    
    private fun writeAccToCsv(data: AccData) {
        if (_isRecording.value && accCsvWriter != null && _selectedSensors.value.contains(SensorType.ACC)) {
            try {
                // 타임스탬프를 밀리초 단위로 저장 (더 읽기 쉽고 분석하기 좋음)
                accCsvWriter?.write("${data.timestamp.time},${data.x},${data.y},${data.z}\n")
                accCsvWriter?.flush()
            } catch (e: Exception) {
                Log.e("BleManager", "Error writing ACC to CSV", e)
            }
        }
    }

    // 센서 활성화 큐 관리 함수들
    private fun activateNextSensorInQueue() {
        if (sensorActivationQueue.isEmpty()) {
            Log.d("BleManager", "🎉 All sensors in queue have been activated successfully!")
            _isReceivingData.value = true
            currentActivatingSensor = null
            return
        }
        
        val nextSensor = sensorActivationQueue.removeAt(0)
        currentActivatingSensor = nextSensor
        
        Log.d("BleManager", "🚀 Activating sensor: $nextSensor (${sensorActivationQueue.size} remaining in queue)")
        
        // 타임아웃 설정
        sensorTimeoutRunnable = Runnable {
            Log.w("BleManager", "⏰ Timeout waiting for $nextSensor data, proceeding to next sensor...")
            currentActivatingSensor = null
            activateNextSensorInQueue()
        }
        handler.postDelayed(sensorTimeoutRunnable!!, sensorTimeoutMs)
        
        // 센서별 활성화 실행
        when (nextSensor) {
            SensorType.EEG -> {
                lastEegDataSize = _eegData.value.size
                activateEegSensorInternal()
            }
            SensorType.ACC -> {
                lastAccDataSize = _accData.value.size
                activateAccSensorInternal()
            }
            SensorType.PPG -> {
                lastPpgDataSize = _ppgData.value.size
                activatePpgSensorInternal()
            }
        }
    }
    
    private fun onSensorDataReceived(sensorType: SensorType) {
        // 현재 활성화 대기 중인 센서와 일치하는지 확인
        if (currentActivatingSensor == sensorType) {
            Log.d("BleManager", "✅ $sensorType data confirmed - proceeding to next sensor")
            
            // 타임아웃 취소
            sensorTimeoutRunnable?.let { handler.removeCallbacks(it) }
            sensorTimeoutRunnable = null
            
            currentActivatingSensor = null
            
            // 500ms 대기 후 다음 센서 활성화 (안정성을 위해)
            handler.postDelayed({
                activateNextSensorInQueue()
            }, 500)
        }
    }
    
    private fun activateEegSensorInternal() {
        bluetoothGatt?.let { gatt ->
            val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
            eegWriteChar?.let {
                it.value = "start".toByteArray()
                gatt.writeCharacteristic(it)
                
                handler.postDelayed({
                    val eegNotifyChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_NOTIFY_CHAR_UUID)
                    eegNotifyChar?.let { notifyChar ->
                        setupNotification(gatt, notifyChar, "EEG")
                        _isEegStarted.value = true
                        Log.d("BleManager", "EEG sensor activated, waiting for data confirmation...")
                    }
                }, 200)
            }
        }
    }
    
    private fun activateAccSensorInternal() {
        bluetoothGatt?.let { gatt ->
            val accService = gatt.getService(ACCELEROMETER_SERVICE_UUID)
            val accChar = accService?.getCharacteristic(ACCELEROMETER_CHAR_UUID)
            accChar?.let { 
                handler.postDelayed({
                    setupNotification(gatt, it, "ACC")
                    _isAccStarted.value = true
                    Log.d("BleManager", "ACC sensor activated, waiting for data confirmation...")
                }, 600)
            }
        }
    }
    
    private fun activatePpgSensorInternal() {
        bluetoothGatt?.let { gatt ->
            val ppgService = gatt.getService(PPG_SERVICE_UUID)
            val ppgChar = ppgService?.getCharacteristic(PPG_CHAR_UUID)
            ppgChar?.let { 
                handler.postDelayed({
                    setupNotification(gatt, it, "PPG")
                    _isPpgStarted.value = true
                    Log.d("BleManager", "PPG sensor activated, waiting for data confirmation...")
                }, 500)
            }
        }
    }
} 