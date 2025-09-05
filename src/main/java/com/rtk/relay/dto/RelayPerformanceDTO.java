package com.rtk.relay.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 转发性能统计DTO
 * 用于RESTful API响应
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
public class RelayPerformanceDTO {
    
    /**
     * 统计时间范围（小时）
     */
    private Integer timeRangeHours;
    
    /**
     * 统计开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 统计结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 总转发尝试次数
     */
    private Long totalRelayAttempts;
    
    /**
     * 总成功次数
     */
    private Long totalSuccess;
    
    /**
     * 总失败次数
     */
    private Long totalFailed;
    
    /**
     * 总体成功率（百分比）
     */
    private Double overallSuccessRate;
    
    /**
     * 总成功传输字节数
     */
    private Long totalSuccessBytes;
    
    /**
     * 活跃基站数
     */
    private Integer activeBaseStations;
    
    /**
     * 活跃移动站数
     */
    private Integer activeMobileStations;
    
    /**
     * 平均成功数据大小
     */
    private Double avgSuccessDataSize;
    
    /**
     * 转发效率指标
     */
    private EfficiencyMetricsDTO efficiency;
    
    /**
     * 每小时转发统计
     */
    private List<HourlyStatsDTO> hourlyStats;
    
    /**
     * 基站转发统计
     */
    private List<BaseStationRelayDTO> baseStationStats;
    
    /**
     * 转发效率指标内部类
     */
    @Data
    public static class EfficiencyMetricsDTO {
        /**
         * 消息转发率（消息/秒）
         */
        private Double messagesPerSecond;
        
        /**
         * 数据吞吐量（字节/秒）
         */
        private Double bytesPerSecond;
        
        /**
         * 平均转发延迟（毫秒）
         */
        private Double avgRelayLatency;
        
        /**
         * 系统负载百分比
         */
        private Double systemLoadPercent;
    }
    
    /**
     * 每小时统计内部类
     */
    @Data
    public static class HourlyStatsDTO {
        /**
         * 小时时间槽
         */
        private String hourSlot;
        
        /**
         * 转发次数
         */
        private Long totalRelays;
        
        /**
         * 成功次数
         */
        private Long successRelays;
        
        /**
         * 成功字节数
         */
        private Long successBytes;
        
        /**
         * 成功率
         */
        private Double successRate;
    }
    
    /**
     * 基站转发统计内部类
     */
    @Data
    public static class BaseStationRelayDTO {
        /**
         * 基站ID
         */
        private String baseStationId;
        
        /**
         * 转发次数
         */
        private Long relayCount;
        
        /**
         * 成功次数
         */
        private Long successCount;
        
        /**
         * 成功字节数
         */
        private Long successBytes;
        
        /**
         * 平均数据大小
         */
        private Double avgDataSize;
        
        /**
         * 最后转发时间
         */
        private LocalDateTime lastRelayTime;
        
        /**
         * 目标移动站数量
         */
        private Integer targetMobileStations;
    }
}
