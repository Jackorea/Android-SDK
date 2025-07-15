package com.example.linkbandsdk

import com.example.linkbandsdk.SensorType

/**
 * 센서 데이터 파싱을 위한 설정 클래스
 * 
 * 이 클래스는 다양한 센서 하드웨어를 지원하기 위해 모든 파싱 매개변수를 
 * 설정 가능하도록 합니다. Swift 버전의 SensorConfiguration을 참고하여 구현되었습니다.
 */
data class SensorConfiguration(
    // 타임스탬프 관련 설정
    val timestampDivisor: Double = 32.768,
    val millisecondsToSeconds: Double = 1000.0,
    
    // EEG 센서 설정
    val eegSampleRate: Double = 250.0,
    val eegSampleSize: Int = 7, // leadOff(1) + CH1(3) + CH2(3)
    val eegPacketSize: Int = 39, // 헤더(4) + 샘플 5개(35)
    val eegVoltageReference: Double = 4.033,
    val eegGain: Double = 12.0,
    val eegResolution: Double = 8388607.0, // 2^23 - 1
    val microVoltMultiplier: Double = 1e6,
    
    // PPG 센서 설정
    val ppgSampleRate: Double = 50.0,
    val ppgSampleSize: Int = 6, // Red(3) + IR(3)
    val ppgPacketSize: Int = 172, // 헤더(4) + 샘플 28개(168)
    
    // 가속도계 센서 설정
    val accelerometerSampleRate: Double = 30.0,
    val accelerometerSampleSize: Int = 6, // X(2) + Y(2) + Z(2)
    val accelerometerPacketSize: Int = 10 // 헤더(4) + 샘플 1개(6)
) {
    companion object {
        val default = SensorConfiguration()
    }
} 