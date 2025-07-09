package com.example.test.data

import java.util.Date

/**
 * 시간 기반 배치 관리를 위한 제네릭 클래스
 * 
 * 지정된 시간 간격마다 샘플들을 배치로 그룹화하여 전달합니다.
 * 
 * @param T 관리할 데이터 타입 (EegData, PpgData, AccData 등)
 * @param targetIntervalMs 목표 시간 간격 (밀리초)
 * @param timestampExtractor 데이터에서 타임스탬프를 추출하는 함수
 */
class TimeBatchManager<T>(
    private val targetIntervalMs: Long,
    private val timestampExtractor: (T) -> Date
) {
    private val buffer = mutableListOf<T>()
    private var batchStartTime: Date? = null
    
    /**
     * 샘플을 추가하고 배치가 완성되면 반환
     * 
     * @param sample 추가할 샘플
     * @return 배치가 완성되면 배치 리스트, 아니면 null
     */
    fun addSample(sample: T): List<T>? {
        val sampleTime = timestampExtractor(sample)
        
        // 첫 번째 샘플이면 배치 시작 시간 설정
        if (batchStartTime == null) {
            batchStartTime = sampleTime
        }
        
        buffer.add(sample)
        
        // 시간 간격 확인
        val elapsed = sampleTime.time - batchStartTime!!.time
        
        return if (elapsed >= targetIntervalMs) {
            val batch = buffer.toList()
            buffer.clear()
            batchStartTime = sampleTime // 새로운 배치 시작
            batch
        } else {
            null
        }
    }
    
    /**
     * 현재 버퍼에 있는 모든 샘플을 강제로 배치로 반환
     * (수집 중지 시 마지막 배치를 얻기 위해 사용)
     */
    fun flushBuffer(): List<T>? {
        return if (buffer.isNotEmpty()) {
            val batch = buffer.toList()
            buffer.clear()
            batchStartTime = null
            batch
        } else {
            null
        }
    }
    
    /**
     * 버퍼를 비우고 상태 초기화
     */
    fun clearBuffer() {
        buffer.clear()
        batchStartTime = null
    }
    
    /**
     * 현재 버퍼에 있는 샘플 개수
     */
    val bufferSize: Int
        get() = buffer.size
} 