package com.example.test.data

import android.util.Log
import java.util.Date
import kotlin.math.pow

/**
 * 센서 데이터 패킷을 구조화된 읽기값으로 파싱하는 순수 비즈니스 로직 클래스
 *
 * 이 클래스는 UI 프레임워크와 완전히 독립적으로 작동하며, Bluetooth 센서로부터 수신된
 * 바이너리 데이터를 구조화된 Kotlin 타입으로 변환합니다. 모든 파싱 매개변수는
 * SensorConfiguration을 통해 설정 가능하여 다양한 센서 하드웨어를 지원합니다.
 * 
 * 주요 특징:
 * - UI 프레임워크 의존성 없음 (순수 비즈니스 로직)
 * - 바이너리 데이터 파싱 전문화
 * - 설정 가능한 센서 매개변수 지원
 * - 엄격한 데이터 검증 및 오류 처리
 * - 타임스탬프 처리 및 샘플링 레이트 계산
 * - 멀티 샘플 패킷 지원
 *
 * 지원 센서 타입:
 * - EEG (뇌전도): 2채널, 24비트 해상도, lead-off 감지
 * - PPG (광전 용적 맥파): Red/IR LED, 심박수 모니터링용
 * - 가속도계: 3축, 모션 감지용
 * - 배터리: 배터리 레벨 모니터링
 */
class SensorDataParser(
    private val configuration: SensorConfiguration = SensorConfiguration.default
) {
    
    companion object {
        private const val TAG = "SensorDataParser"
        private const val HEADER_SIZE = 4
    }
    
    /**
     * 원시 EEG 데이터 패킷을 구조화된 읽기값으로 파싱
     *
     * @param data EEG 특성에서 수신된 원시 바이너리 데이터
     * @return 패킷에서 추출된 EEG 읽기값 배열
     * @throws SensorDataParsingException 패킷 형식이 유효하지 않은 경우
     */
    @Throws(SensorDataParsingException::class)
    fun parseEegData(data: ByteArray): List<EegData> {
        // 최소 패킷 크기 검증 (헤더 + 최소 1개 샘플)
        val minPacketSize = HEADER_SIZE + configuration.eegSampleSize
        if (data.size < minPacketSize) {
            throw SensorDataParsingException(
                "EEG packet too short: ${data.size} bytes (minimum: $minPacketSize)"
            )
        }
        
        // 사용 가능한 실제 샘플 수 계산
        val dataWithoutHeader = data.size - HEADER_SIZE
        val actualSampleCount = dataWithoutHeader / configuration.eegSampleSize
        val expectedSampleCount = (configuration.eegPacketSize - HEADER_SIZE) / configuration.eegSampleSize
        
        // 버퍼링 분석을 위한 시간 계산
        val actualDurationMs = (actualSampleCount / configuration.eegSampleRate * 1000).toInt()
        val expectedDurationMs = (expectedSampleCount / configuration.eegSampleRate * 1000).toInt()
        
        // 패킷 크기가 예상과 다른 경우 로깅
        if (data.size != configuration.eegPacketSize) {
            Log.w(TAG, "⚠️ EEG packet size: ${data.size} bytes (expected: ${configuration.eegPacketSize}), " +
                     "processing $actualSampleCount samples (expected: $expectedSampleCount)")
            Log.i(TAG, "📊 EEG buffering: ${actualDurationMs}ms worth of data (expected: ${expectedDurationMs}ms)")
        }
        
        // 패킷 헤더에서 타임스탬프 추출
        val timeRaw = extractTimestamp(data)
        var timestamp = timeRaw / configuration.timestampDivisor / configuration.millisecondsToSeconds
        
        val readings = mutableListOf<EegData>()
        
        // 사용 가능한 샘플만 파싱
        for (sampleIndex in 0 until actualSampleCount) {
            val i = HEADER_SIZE + (sampleIndex * configuration.eegSampleSize)
            
            // 배열 경계 초과 방지
            if (i + configuration.eegSampleSize > data.size) {
                Log.w(TAG, "⚠️ EEG sample ${sampleIndex + 1} incomplete, skipping remaining samples")
                break
            }
            
            // lead-off (1바이트) - 센서 연결 상태
            val leadOffRaw = data[i].toInt() and 0xFF
            val leadOffNormalized = leadOffRaw > 0  // 리드가 연결 해제된 경우 true
            
            // CH1: 3바이트 (Big Endian)
            var ch1Raw = ((data[i+1].toInt() and 0xFF) shl 16) or
                        ((data[i+2].toInt() and 0xFF) shl 8) or
                        (data[i+3].toInt() and 0xFF)
            
            // CH2: 3바이트 (Big Endian)
            var ch2Raw = ((data[i+4].toInt() and 0xFF) shl 16) or
                        ((data[i+5].toInt() and 0xFF) shl 8) or
                        (data[i+6].toInt() and 0xFF)
            
            // 24비트 부호 있는 값 처리 (MSB 부호 확장)
            if ((ch1Raw and 0x800000) != 0) {
                ch1Raw -= 0x1000000
            }
            if ((ch2Raw and 0x800000) != 0) {
                ch2Raw -= 0x1000000
            }
            
            // 설정 매개변수를 사용하여 전압으로 변환
            val ch1uV = ch1Raw * configuration.eegVoltageReference / configuration.eegGain / 
                       configuration.eegResolution * configuration.microVoltMultiplier
            val ch2uV = ch2Raw * configuration.eegVoltageReference / configuration.eegGain / 
                       configuration.eegResolution * configuration.microVoltMultiplier
            
            val reading = EegData(
                timestamp = Date((timestamp * 1000).toLong()),
                leadOff = leadOffNormalized,
                channel1 = ch1uV,
                channel2 = ch2uV,
                ch1Raw = ch1Raw,
                ch2Raw = ch2Raw
            )
            
            readings.add(reading)
            
            // 다음 샘플을 위한 타임스탬프 증가
            timestamp += 1.0 / configuration.eegSampleRate
        }
        
        return readings
    }
    
    /**
     * 원시 PPG 데이터 패킷을 구조화된 읽기값으로 파싱
     *
     * @param data PPG 특성에서 수신된 원시 바이너리 데이터
     * @return 패킷에서 추출된 PPG 읽기값 배열
     * @throws SensorDataParsingException 패킷 형식이 유효하지 않은 경우
     */
    @Throws(SensorDataParsingException::class)
    fun parsePpgData(data: ByteArray): List<PpgData> {
        // 최소 패킷 크기 검증 (헤더 + 최소 1개 샘플)
        val minPacketSize = HEADER_SIZE + configuration.ppgSampleSize
        if (data.size < minPacketSize) {
            throw SensorDataParsingException(
                "PPG packet too short: ${data.size} bytes (minimum: $minPacketSize)"
            )
        }
        
        // 사용 가능한 실제 샘플 수 계산
        val dataWithoutHeader = data.size - HEADER_SIZE
        val actualSampleCount = dataWithoutHeader / configuration.ppgSampleSize
        val expectedSampleCount = (configuration.ppgPacketSize - HEADER_SIZE) / configuration.ppgSampleSize
        
        // 패킷 크기가 예상과 다른 경우 로깅
        if (data.size != configuration.ppgPacketSize) {
            Log.w(TAG, "⚠️ PPG packet size: ${data.size} bytes (expected: ${configuration.ppgPacketSize}), " +
                     "processing $actualSampleCount samples (expected: $expectedSampleCount)")
        }
        
        // 패킷 헤더에서 타임스탬프 추출
        val timeRaw = extractTimestamp(data)
        var timestamp = timeRaw / configuration.timestampDivisor / configuration.millisecondsToSeconds
        
        val readings = mutableListOf<PpgData>()
        
        // 사용 가능한 샘플만 파싱
        for (sampleIndex in 0 until actualSampleCount) {
            val i = HEADER_SIZE + (sampleIndex * configuration.ppgSampleSize)
            
            // 배열 경계 초과 방지
            if (i + configuration.ppgSampleSize > data.size) {
                Log.w(TAG, "⚠️ PPG sample ${sampleIndex + 1} incomplete, skipping remaining samples")
                break
            }
            
            val red = ((data[i].toInt() and 0xFF) shl 16) or
                     ((data[i+1].toInt() and 0xFF) shl 8) or
                     (data[i+2].toInt() and 0xFF)
            val ir = ((data[i+3].toInt() and 0xFF) shl 16) or
                    ((data[i+4].toInt() and 0xFF) shl 8) or
                    (data[i+5].toInt() and 0xFF)
            
            val reading = PpgData(
                timestamp = Date((timestamp * 1000).toLong()),
                red = red,
                ir = ir
            )
            
            readings.add(reading)
            
            // 다음 샘플을 위한 타임스탬프 증가
            timestamp += 1.0 / configuration.ppgSampleRate
        }
        
        return readings
    }
    
    /**
     * 원시 가속도계 데이터 패킷을 구조화된 읽기값으로 파싱
     *
     * @param data 가속도계 특성에서 수신된 원시 바이너리 데이터
     * @return 패킷에서 추출된 가속도계 읽기값 배열
     * @throws SensorDataParsingException 패킷 형식이 유효하지 않은 경우
     */
    @Throws(SensorDataParsingException::class)
    fun parseAccelerometerData(data: ByteArray): List<AccData> {
        val sampleSize = 6
        val minPacketSize = HEADER_SIZE + sampleSize
        
        if (data.size < minPacketSize) {
            throw SensorDataParsingException(
                "ACCEL packet too short: ${data.size} bytes (minimum: $minPacketSize)"
            )
        }
        
        // 패킷 헤더에서 타임스탬프 추출
        val timeRaw = extractTimestamp(data)
        var timestamp = timeRaw / configuration.timestampDivisor / configuration.millisecondsToSeconds
        
        val dataWithoutHeaderCount = data.size - HEADER_SIZE
        if (dataWithoutHeaderCount < sampleSize) {
            throw SensorDataParsingException(
                "ACCEL packet has header but not enough data for one sample"
            )
        }
        
        val sampleCount = dataWithoutHeaderCount / sampleSize
        val readings = mutableListOf<AccData>()
        
        for (i in 0 until sampleCount) {
            val baseInFullPacket = HEADER_SIZE + (i * sampleSize)
            // 하드웨어 사양에 따라 홀수 번째 바이트 사용
            val x = (data[baseInFullPacket + 1].toInt() and 0xFF).toShort()  // data[i+1]
            val y = (data[baseInFullPacket + 3].toInt() and 0xFF).toShort()  // data[i+3]
            val z = (data[baseInFullPacket + 5].toInt() and 0xFF).toShort()  // data[i+5]
            
            val reading = AccData(
                timestamp = Date((timestamp * 1000).toLong()),
                x = x,
                y = y,
                z = z
            )
            
            readings.add(reading)
            
            // 다음 샘플을 위한 타임스탬프 증가
            timestamp += 1.0 / configuration.accelerometerSampleRate
        }
        
        return readings
    }
    
    /**
     * 원시 배터리 데이터를 구조화된 읽기값으로 파싱
     *
     * @param data 배터리 특성에서 수신된 원시 바이너리 데이터
     * @return 현재 레벨을 포함한 배터리 읽기값
     * @throws SensorDataParsingException 데이터가 유효하지 않은 경우
     */
    @Throws(SensorDataParsingException::class)
    fun parseBatteryData(data: ByteArray): BatteryData {
        if (data.isEmpty()) {
            throw SensorDataParsingException("Battery data is empty")
        }
        
        val level = data[0].toInt() and 0xFF
        return BatteryData(level = level)
    }
    
    /**
     * 패킷 헤더에서 타임스탬프 추출 (Little Endian)
     */
    private fun extractTimestamp(data: ByteArray): Long {
        return ((data[3].toLong() and 0xFF) shl 24) or
               ((data[2].toLong() and 0xFF) shl 16) or
               ((data[1].toLong() and 0xFF) shl 8) or
               (data[0].toLong() and 0xFF)
    }
} 