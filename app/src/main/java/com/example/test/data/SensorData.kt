package com.example.test.data

data class EegData(
    val timestamp: Long,
    val leadOff: Int,
    val channel1: Double,
    val channel2: Double
)

data class PpgData(
    val timestamp: Long,
    val red: Int,
    val ir: Int
)

data class AccData(
    val timestamp: Long,
    val x: Int,
    val y: Int,
    val z: Int
)

data class BatteryData(
    val level: Int
)

data class SensorDataState(
    val eegData: List<EegData> = emptyList(),
    val ppgData: List<PpgData> = emptyList(),
    val accData: List<AccData> = emptyList(),
    val batteryData: BatteryData? = null
) 