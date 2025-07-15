package com.example.linkbandsdk

import android.content.Context

/**
 * LinkBand SDK 진입점 클래스
 * BLE 센서 제어, 데이터 수집, 파싱, 저장 등 핵심 기능을 제공
 * (구현은 내부 BleManager 등과 연결됨)
 */
class LinkBandSdk(private val context: Context) {
    // TODO: BleManager 등 내부 기능 연결 예정
    // 예시: private val bleManager = BleManager(context)
    
    // SDK에서 외부로 노출할 API 메서드들 (예시)
    // fun startScan() { ... }
    // fun selectSensor(...) { ... }
    // fun startCollection() { ... }
    // ...
} 