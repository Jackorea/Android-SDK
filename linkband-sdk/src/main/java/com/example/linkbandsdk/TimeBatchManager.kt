package com.example.linkbandsdk

import java.util.Date
import com.example.linkbandsdk.SensorType

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
    private val targetIntervalMs: Long
) {
    private val buffer = mutableListOf<T>()
    private var batchStartWallClock: Long? = null
    
    /**
     * 샘플을 추가하고 배치가 완성되면 반환
     * 
     * @param sample 추가할 샘플
     * @return 배치가 완성되면 배치 리스트, 아니면 null
     */
    fun addSample(sample: T): List<T>? {
        val now = System.currentTimeMillis()
        if (batchStartWallClock == null) {
            batchStartWallClock = now
        }
        buffer.add(sample)
        val elapsed = now - batchStartWallClock!!
        
        return if (elapsed >= targetIntervalMs) {
            val batch = buffer.toList()
            buffer.clear()
            batchStartWallClock = now
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
            batchStartWallClock = null
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
        batchStartWallClock = null
    }
    
    /**
     * 현재 버퍼에 있는 샘플 개수
     */
    val bufferSize: Int
        get() = buffer.size
} 