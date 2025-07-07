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
import android.util.Log
import com.example.test.data.AccData
import com.example.test.data.BatteryData
import com.example.test.data.EegData
import com.example.test.data.PpgData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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
    
    private var bluetoothGatt: BluetoothGatt? = null
    
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
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
    private val _isAutoReconnectEnabled = MutableStateFlow(false)
    val isAutoReconnectEnabled: StateFlow<Boolean> = _isAutoReconnectEnabled.asStateFlow()
    
    // 마지막 연결된 디바이스 정보 저장
    private var lastConnectedDevice: BluetoothDevice? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private var reconnectRunnable: Runnable? = null
    
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
                    // 연결 해제 시 모든 센서 상태 리셋
                    _isEegStarted.value = false
                    _isPpgStarted.value = false
                    _isAccStarted.value = false
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
                // 서비스 발견 후 notification 설정 전 딜레이
                handler.postDelayed({
                    startNotifications(gatt)
                }, 500)
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
    
    // 모든 센서 동시 시작 함수 추가
    fun startAllSensors() {
        Log.d("BleManager", "=== 모든 센서 동시 시작 ===")
        bluetoothGatt?.let { gatt ->
            // 1. EEG 시작
            val eegWriteChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_WRITE_CHAR_UUID)
            eegWriteChar?.let {
                Log.d("BleManager", "Starting EEG...")
                it.value = byteArrayOf(0x01)
                gatt.writeCharacteristic(it)
            }
            
            // 2. 모든 notification 설정 (순차적으로)
            handler.postDelayed({
                // EEG notification
                val eegNotifyChar = gatt.getService(EEG_NOTIFY_SERVICE_UUID)?.getCharacteristic(EEG_NOTIFY_CHAR_UUID)
                eegNotifyChar?.let { notifyChar ->
                    Log.d("BleManager", "Setting up EEG notification")
                    gatt.setCharacteristicNotification(notifyChar, true)
                    val descriptor = notifyChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor?.let { desc ->
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(desc)
                        _isEegStarted.value = true
                    }
                }
                
                // PPG notification (300ms 후)
                handler.postDelayed({
                    val ppgChar = gatt.getService(PPG_SERVICE_UUID)?.getCharacteristic(PPG_CHAR_UUID)
                    ppgChar?.let {
                        Log.d("BleManager", "Setting up PPG notification")
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.let { desc ->
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(desc)
                            _isPpgStarted.value = true
                        }
                    }
                }, 300)
                
                // ACC notification (600ms 후)
                handler.postDelayed({
                    val accChar = gatt.getService(ACCELEROMETER_SERVICE_UUID)?.getCharacteristic(ACCELEROMETER_CHAR_UUID)
                    accChar?.let {
                        Log.d("BleManager", "Setting up ACC notification")
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.let { desc ->
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(desc)
                            _isAccStarted.value = true
                        }
                    }
                }, 600)
                
                // 데이터 수신 확인 (10초 후)
                handler.postDelayed({
                    val eegCount = _eegData.value.size
                    val ppgCount = _ppgData.value.size
                    val accCount = _accData.value.size
                    
                    Log.d("BleManager", "=== 센서 데이터 수신 결과 ===")
                    Log.d("BleManager", "EEG: ${eegCount}개 샘플")
                    Log.d("BleManager", "PPG: ${ppgCount}개 샘플")
                    Log.d("BleManager", "ACC: ${accCount}개 샘플")
                    
                    if (eegCount == 0 && ppgCount == 0 && accCount == 0) {
                        Log.w("BleManager", "⚠️ 모든 센서에서 데이터 수신 실패")
                        Log.w("BleManager", "디바이스 착용 상태와 피부 접촉을 확인하세요")
                    }
                }, 10000)
                
            }, 500)
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
        Log.d("BleManager", "parseEegData called with ${data.size} bytes")
        // 최소 헤더 4바이트 + 최소 1개 샘플 7바이트 = 11바이트
        if (data.size < 11) {
            Log.w("BleManager", "EEG data too small: ${data.size} bytes, need at least 11")
            return
        }
        
        Log.d("BleManager", "Parsing EEG data...")
        val timeRaw = (data[3].toInt() and 0xFF shl 24) or 
                     (data[2].toInt() and 0xFF shl 16) or 
                     (data[1].toInt() and 0xFF shl 8) or 
                     (data[0].toInt() and 0xFF)
        var timestamp = timeRaw / 32.768 / 1000.0
        
        val newEegData = mutableListOf<EegData>()
        
        // 실제 데이터 크기에 따라 샘플 수 계산
        val availableDataSize = data.size - 4
        val sampleCount = availableDataSize / 7
        Log.d("BleManager", "EEG sample count: $sampleCount")
        
        // 샘플 파싱 (7바이트씩)
        for (i in 0 until sampleCount) {
            val offset = 4 + i * 7
            if (offset + 6 >= data.size) break
            
            val leadOff = data[offset].toInt() and 0xFF
            
            // 24bit signed 처리
            var ch1Raw = (data[offset+1].toInt() and 0xFF shl 16) or 
                        (data[offset+2].toInt() and 0xFF shl 8) or 
                        (data[offset+3].toInt() and 0xFF)
            var ch2Raw = (data[offset+4].toInt() and 0xFF shl 16) or 
                        (data[offset+5].toInt() and 0xFF shl 8) or 
                        (data[offset+6].toInt() and 0xFF)
            
            // 24bit signed 보정
            if (ch1Raw and 0x800000 != 0) ch1Raw -= 0x1000000
            if (ch2Raw and 0x800000 != 0) ch2Raw -= 0x1000000
            
            // 전압값(uV)로 변환
            val ch1Uv = ch1Raw * 4.033 / 12 / (Math.pow(2.0, 23.0) - 1) * 1e6
            val ch2Uv = ch2Raw * 4.033 / 12 / (Math.pow(2.0, 23.0) - 1) * 1e6
            
            newEegData.add(EegData(timestamp.toLong(), leadOff, ch1Uv, ch2Uv))
            timestamp += 1.0 / EEG_SAMPLE_RATE
        }
        
        if (newEegData.isNotEmpty()) {
            Log.d("BleManager", "EEG parsed successfully: ${newEegData.size} samples")
            val currentData = _eegData.value.takeLast(1000).toMutableList()
            currentData.addAll(newEegData)
            _eegData.value = currentData
            Log.d("BleManager", "EEG data updated, total samples: ${_eegData.value.size}")
        } else {
            Log.w("BleManager", "No EEG samples parsed")
        }
    }
    
    private fun parsePpgData(data: ByteArray) {
        Log.d("BleManager", "parsePpgData called with ${data.size} bytes")
        // 최소 헤더 4바이트 + 최소 1개 샘플 6바이트 = 10바이트
        if (data.size < 10) {
            Log.w("BleManager", "PPG data too small: ${data.size} bytes, need at least 10")
            return
        }
        
        Log.d("BleManager", "Parsing PPG data...")
        val timeRaw = (data[3].toInt() and 0xFF shl 24) or 
                     (data[2].toInt() and 0xFF shl 16) or 
                     (data[1].toInt() and 0xFF shl 8) or 
                     (data[0].toInt() and 0xFF)
        var timestamp = timeRaw / 32.768 / 1000.0
        
        val newPpgData = mutableListOf<PpgData>()
        
        // 실제 데이터 크기에 따라 샘플 수 계산
        val availableDataSize = data.size - 4
        val sampleCount = availableDataSize / 6
        Log.d("BleManager", "PPG sample count: $sampleCount")
        
        // 샘플 파싱 (6바이트씩)
        for (i in 0 until sampleCount) {
            val offset = 4 + i * 6
            if (offset + 5 >= data.size) break
            
            val ppgRed = (data[offset].toInt() and 0xFF shl 16) or 
                        (data[offset+1].toInt() and 0xFF shl 8) or 
                        (data[offset+2].toInt() and 0xFF)
            val ppgIr = (data[offset+3].toInt() and 0xFF shl 16) or 
                       (data[offset+4].toInt() and 0xFF shl 8) or 
                       (data[offset+5].toInt() and 0xFF)
            
            newPpgData.add(PpgData(timestamp.toLong(), ppgRed, ppgIr))
            timestamp += 1.0 / PPG_SAMPLE_RATE
        }
        
        if (newPpgData.isNotEmpty()) {
            Log.d("BleManager", "PPG parsed successfully: ${newPpgData.size} samples")
            val currentData = _ppgData.value.takeLast(500).toMutableList()
            currentData.addAll(newPpgData)
            _ppgData.value = currentData
            Log.d("BleManager", "PPG data updated, total samples: ${_ppgData.value.size}")
        } else {
            Log.w("BleManager", "No PPG samples parsed")
        }
    }
    
    private fun parseAccData(data: ByteArray) {
        // 최소 헤더 4바이트 + 최소 1개 샘플 6바이트 = 10바이트
        if (data.size < 10) return
        
        val timeRaw = (data[3].toInt() and 0xFF shl 24) or 
                     (data[2].toInt() and 0xFF shl 16) or 
                     (data[1].toInt() and 0xFF shl 8) or 
                     (data[0].toInt() and 0xFF)
        var timestamp = timeRaw / 32.768 / 1000.0
        
        val newAccData = mutableListOf<AccData>()
        
        // 실제 데이터 크기에 따라 샘플 수 계산
        val availableDataSize = data.size - 4
        val sampleCount = availableDataSize / 6
        
        // 샘플 파싱 (6바이트씩)
        for (i in 0 until sampleCount) {
            val offset = 4 + i * 6
            if (offset + 5 >= data.size) break
            
            val accX = data[offset+1].toInt() and 0xFF
            val accY = data[offset+3].toInt() and 0xFF
            val accZ = data[offset+5].toInt() and 0xFF
            
            newAccData.add(AccData(timestamp.toLong(), accX, accY, accZ))
            timestamp += 1.0 / ACC_SAMPLE_RATE
        }
        
        if (newAccData.isNotEmpty()) {
            val currentData = _accData.value.takeLast(300).toMutableList()
            currentData.addAll(newAccData)
            _accData.value = currentData
        }
    }
    
    private fun parseBatteryData(data: ByteArray) {
        if (data.isNotEmpty()) {
            val batteryLevel = data[0].toInt() and 0xFF
            _batteryData.value = BatteryData(batteryLevel)
        }
    }
} 