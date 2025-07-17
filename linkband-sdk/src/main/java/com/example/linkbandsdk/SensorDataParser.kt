package com.example.linkbandsdk

import android.util.Log
import java.util.Date
import kotlin.math.pow
import com.example.linkbandsdk.SensorType
import com.example.linkbandsdk.SensorConfiguration

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
    
    // 연속 EEG 타임스탬프 관리 변수
    private var lastEegSampleTimestampMillis: Long? = null
    // 연속 PPG 타임스탬프 관리 변수
    private var lastPpgSampleTimestampMillis: Long? = null
    // 연속 ACC 타임스탬프 관리 변수
    private var lastAccSampleTimestampMillis: Long? = null

    /**
     * BLE 연결 해제/센서 중지 시 타임스탬프 리셋
     */
    fun resetEegTimestamp() {
        lastEegSampleTimestampMillis = null
    }
    fun resetPpgTimestamp() {
        lastPpgSampleTimestampMillis = null
    }
    fun resetAccTimestamp() {
        lastAccSampleTimestampMillis = null
    }

    /**
     * 원시 EEG 데이터 패킷을 구조화된 읽기값으로 파싱 (Swift 스타일)
     * BLE 패킷 헤더가 아닌 내부 lastEegSampleTimestampMillis를 기준으로 연속 타임스탬프를 생성
     */
    @Throws(SensorDataParsingException::class)
    fun parseEegData(data: ByteArray): List<EegData> {
        val headerSize = 4
        if (data.size < headerSize + configuration.eegSampleSize) {
            throw SensorDataParsingException(
                "EEG packet too short: ${data.size} bytes (minimum: ${headerSize + configuration.eegSampleSize})"
            )
        }
        val dataWithoutHeader = data.size - headerSize
        val actualSampleCount = dataWithoutHeader / configuration.eegSampleSize
        val expectedSampleCount = (configuration.eegPacketSize - headerSize) / configuration.eegSampleSize
        if (data.size != configuration.eegPacketSize) {
            Log.w(TAG, "⚠️ EEG packet size: ${data.size} bytes (expected: ${configuration.eegPacketSize}), processing $actualSampleCount samples (expected: $expectedSampleCount)")
        }
        // 헤더에서 timestamp 추출 (Little Endian)
        val timeRaw = ((data[3].toLong() and 0xFF) shl 24) or ((data[2].toLong() and 0xFF) shl 16) or ((data[1].toLong() and 0xFF) shl 8) or (data[0].toLong() and 0xFF)
        val packetTimestampSec = timeRaw / configuration.timestampDivisor / configuration.millisecondsToSeconds
        val packetTimestampMillis = (packetTimestampSec * 1000).toLong()
        val readings = mutableListOf<EegData>()
        var sampleTimestampMillis = lastEegSampleTimestampMillis?.let { it + (1000.0 / configuration.eegSampleRate).toLong() } ?: packetTimestampMillis
        for (sampleIndex in 0 until actualSampleCount) {
            val i = headerSize + (sampleIndex * configuration.eegSampleSize)
            if (i + configuration.eegSampleSize > data.size) {
                Log.w(TAG, "⚠️ EEG sample ${sampleIndex + 1} incomplete, skipping remaining samples")
                break
            }
            val leadOffRaw = data[i].toInt() and 0xFF
            val leadOffNormalized = leadOffRaw > 0
            var ch1Raw = ((data[i+1].toInt() and 0xFF) shl 16) or ((data[i+2].toInt() and 0xFF) shl 8) or (data[i+3].toInt() and 0xFF)
            var ch2Raw = ((data[i+4].toInt() and 0xFF) shl 16) or ((data[i+5].toInt() and 0xFF) shl 8) or (data[i+6].toInt() and 0xFF)
            if ((ch1Raw and 0x800000) != 0) ch1Raw -= 0x1000000
            if ((ch2Raw and 0x800000) != 0) ch2Raw -= 0x1000000
            val ch1uV = ch1Raw * configuration.eegVoltageReference / configuration.eegGain / configuration.eegResolution * configuration.microVoltMultiplier
            val ch2uV = ch2Raw * configuration.eegVoltageReference / configuration.eegGain / configuration.eegResolution * configuration.microVoltMultiplier
            val reading = EegData(
                channel1 = ch1uV,
                channel2 = ch2uV,
                ch1Raw = ch1Raw,
                ch2Raw = ch2Raw,
                leadOff = leadOffNormalized,
                timestamp = Date(sampleTimestampMillis)
            )
            readings.add(reading)
            sampleTimestampMillis += (1000.0 / configuration.eegSampleRate).toLong()
        }
        // 마지막 샘플 타임스탬프 저장
        if (readings.isNotEmpty()) {
            lastEegSampleTimestampMillis = readings.last().timestamp.time
        }
        return readings
    }

    /**
     * 원시 PPG 데이터 패킷을 구조화된 읽기값으로 파싱 (Swift 스타일)
     * BLE 패킷 헤더가 아닌 내부 lastPpgSampleTimestampMillis를 기준으로 연속 타임스탬프를 생성
     */
    @Throws(SensorDataParsingException::class)
    fun parsePpgData(data: ByteArray): List<PpgData> {
        val headerSize = 4
        if (data.size < headerSize + configuration.ppgSampleSize) {
            throw SensorDataParsingException(
                "PPG packet too short: ${data.size} bytes (minimum: ${headerSize + configuration.ppgSampleSize})"
            )
        }
        val dataWithoutHeader = data.size - headerSize
        val actualSampleCount = dataWithoutHeader / configuration.ppgSampleSize
        val expectedSampleCount = (configuration.ppgPacketSize - headerSize) / configuration.ppgSampleSize
        if (data.size != configuration.ppgPacketSize) {
            Log.w(TAG, "⚠️ PPG packet size: ${data.size} bytes (expected: ${configuration.ppgPacketSize}), processing $actualSampleCount samples (expected: $expectedSampleCount)")
        }
        val timeRaw = ((data[3].toLong() and 0xFF) shl 24) or ((data[2].toLong() and 0xFF) shl 16) or ((data[1].toLong() and 0xFF) shl 8) or (data[0].toLong() and 0xFF)
        val packetTimestampSec = timeRaw / configuration.timestampDivisor / configuration.millisecondsToSeconds
        val packetTimestampMillis = (packetTimestampSec * 1000).toLong()
        val readings = mutableListOf<PpgData>()
        var sampleTimestampMillis = lastPpgSampleTimestampMillis?.let { it + (1000.0 / configuration.ppgSampleRate).toLong() } ?: packetTimestampMillis
        for (sampleIndex in 0 until actualSampleCount) {
            val i = headerSize + (sampleIndex * configuration.ppgSampleSize)
            if (i + configuration.ppgSampleSize > data.size) {
                Log.w(TAG, "⚠️ PPG sample ${sampleIndex + 1} incomplete, skipping remaining samples")
                break
            }
            val red = (data[i].toInt() shl 16) or (data[i+1].toInt() shl 8) or (data[i+2].toInt())
            val ir = (data[i+3].toInt() shl 16) or (data[i+4].toInt() shl 8) or (data[i+5].toInt())
            val reading = PpgData(
                red = red,
                ir = ir,
                timestamp = Date(sampleTimestampMillis)
            )
            readings.add(reading)
            sampleTimestampMillis += (1000.0 / configuration.ppgSampleRate).toLong()
        }
        if (readings.isNotEmpty()) {
            lastPpgSampleTimestampMillis = readings.last().timestamp.time
        }
        return readings
    }

    /**
     * 원시 가속도계 데이터 패킷을 구조화된 읽기값으로 파싱 (Swift 스타일)
     * BLE 패킷 헤더가 아닌 내부 lastAccSampleTimestampMillis를 기준으로 연속 타임스탬프를 생성
     */
    @Throws(SensorDataParsingException::class)
    fun parseAccelerometerData(data: ByteArray): List<AccData> {
        val headerSize = 4
        val sampleSize = 6
        if (data.size < headerSize + sampleSize) {
            throw SensorDataParsingException(
                "ACCEL packet too short: ${data.size} bytes (minimum: ${headerSize + sampleSize})"
            )
        }
        val dataWithoutHeaderCount = data.size - headerSize
        if (dataWithoutHeaderCount < sampleSize) {
            throw SensorDataParsingException(
                "ACCEL packet has header but not enough data for one sample"
            )
        }
        val sampleCount = dataWithoutHeaderCount / sampleSize
        val timeRaw = ((data[3].toLong() and 0xFF) shl 24) or ((data[2].toLong() and 0xFF) shl 16) or ((data[1].toLong() and 0xFF) shl 8) or (data[0].toLong() and 0xFF)
        val packetTimestampSec = timeRaw / configuration.timestampDivisor / configuration.millisecondsToSeconds
        val packetTimestampMillis = (packetTimestampSec * 1000).toLong()
        val readings = mutableListOf<AccData>()
        var sampleTimestampMillis = lastAccSampleTimestampMillis?.let { it + (1000.0 / configuration.accelerometerSampleRate).toLong() } ?: packetTimestampMillis
        for (i in 0 until sampleCount) {
            val baseInFullPacket = headerSize + (i * sampleSize)
            val x = (data[baseInFullPacket + 1].toInt()).toShort()
            val y = (data[baseInFullPacket + 3].toInt()).toShort()
            val z = (data[baseInFullPacket + 5].toInt()).toShort()
            val reading = AccData(
                x = x,
                y = y,
                z = z,
                timestamp = Date(sampleTimestampMillis)
            )
            readings.add(reading)
            sampleTimestampMillis += (1000.0 / configuration.accelerometerSampleRate).toLong()
        }
        if (readings.isNotEmpty()) {
            lastAccSampleTimestampMillis = readings.last().timestamp.time
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