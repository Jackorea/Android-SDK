package com.example.linkbandsdk

import java.util.Date
import com.example.linkbandsdk.SensorType

data class EegData(
    val timestamp: Date,
    val leadOff: Boolean, // true if any lead is disconnected
    val channel1: Double, // µV
    val channel2: Double, // µV
    val ch1Raw: Int, // raw 24-bit value
    val ch2Raw: Int  // raw 24-bit value
)

data class PpgData(
    val timestamp: Date,
    val red: Int,
    val ir: Int
)

data class AccData(
    val timestamp: Date,
    val x: Short, // 16-bit signed value
    val y: Short, // 16-bit signed value
    val z: Short  // 16-bit signed value
)

data class BatteryData(
    val level: Int // 0-100%
)

// 가속도계 표시 모드를 정의하는 enum
enum class AccelerometerMode {
    RAW,    // 원시 가속도 값 표시 (중력 포함)
    MOTION; // 선형 가속도 값 표시 (중력 제거)
    
    val description: String
        get() = when (this) {
            RAW -> "중력을 포함한 원시 가속도 값"
            MOTION -> "중력을 제거한 움직임만 표시"
        }
}

// 처리된 가속도계 데이터 (UI 표시용)
data class ProcessedAccData(
    val timestamp: Date,
    val x: Short,
    val y: Short,
    val z: Short,
    val mode: AccelerometerMode
)

data class SensorDataState(
    val eegData: List<EegData> = emptyList(),
    val ppgData: List<PpgData> = emptyList(),
    val accData: List<AccData> = emptyList(),
    val batteryData: BatteryData? = null
)

// 파싱 에러를 위한 커스텀 예외 클래스
class SensorDataParsingException(message: String) : Exception(message) 

// 데이터 수집 모드를 정의하는 enum
enum class CollectionMode(val description: String) {
    SAMPLE_COUNT("샘플 수"),
    SECONDS("초단위"),
    MINUTES("분단위");
    
    companion object {
        val allCases = values().toList()
    }
}

// 각 센서별 수집 설정을 관리하는 데이터 클래스
data class SensorBatchConfiguration(
    // 샘플 수 기반 수집 시 배치 크기
    var sampleCount: Int,
    // 초 단위 시간 기반 수집 시 간격
    var seconds: Int,
    // 분 단위 시간 기반 수집 시 간격 (기본값: 1분)
    var minutes: Int,
    
    // UI 텍스트 필드용 문자열들 (입력 중 임시 값 포함)
    var sampleCountText: String = sampleCount.toString(),
    var secondsText: String = seconds.toString(),
    var minutesText: String = minutes.toString()
) {
    companion object {
        // 센서 타입별 최적화된 기본 설정값을 제공
        fun defaultConfiguration(sensorType: SensorType): SensorBatchConfiguration {
            return when (sensorType) {
                SensorType.EEG -> SensorBatchConfiguration(
                    sampleCount = 250, 
                    seconds = 1, 
                    minutes = 1
                )
                SensorType.PPG -> SensorBatchConfiguration(
                    sampleCount = 50, 
                    seconds = 1, 
                    minutes = 1
                )
                SensorType.ACC -> SensorBatchConfiguration(
                    sampleCount = 25, 
                    seconds = 1, 
                    minutes = 1
                )
            }
        }
    }
}

// 배치 데이터 수집 설정
data class DataCollectionConfig(
    val sensorType: SensorType,
    val mode: DataCollectionMode
) {
    sealed class DataCollectionMode {
        data class SampleCount(val count: Int) : DataCollectionMode()
        data class TimeInterval(val intervalSeconds: Long) : DataCollectionMode()
    }
} 

// 센서 타입 enum (SDK 전체에서 사용)
enum class SensorType {
    EEG, PPG, ACC
} 