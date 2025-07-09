package com.example.test.data

import java.util.Date

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