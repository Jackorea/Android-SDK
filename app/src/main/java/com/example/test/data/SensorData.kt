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

data class SensorDataState(
    val eegData: List<EegData> = emptyList(),
    val ppgData: List<PpgData> = emptyList(),
    val accData: List<AccData> = emptyList(),
    val batteryData: BatteryData? = null
)

// 파싱 에러를 위한 커스텀 예외 클래스
class SensorDataParsingException(message: String) : Exception(message) 