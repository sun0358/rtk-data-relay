package com.rtk.relay.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 基站信息DTO
 * 用于RESTful API响应
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
public class BaseStationDTO {
    
    /**
     * 基站ID
     */
    private String baseStationId;
    
    /**
     * 基站IP地址
     */
    private String remoteAddress;
    
    /**
     * 连接时间
     */
    private LocalDateTime connectTime;
    
    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;
    
    /**
     * 最后数据时间
     */
    private LocalDateTime lastDataTime;
    
    /**
     * 当前小时数据计数
     */
    private Integer hourlyDataCount;
    
    /**
     * 接收字节数
     */
    private Long receivedBytes;
    
    /**
     * 接收消息数
     */
    private Long receivedMessages;
    
    /**
     * 连接状态
     */
    private String status;
    
    /**
     * 非活跃秒数
     */
    private Long inactiveSeconds;
    
    /**
     * RTCM消息类型
     */
    private String rtcmMessageTypes;
    
    /**
     * 数据质量指标
     */
    private DataQualityDTO dataQuality;
    
    /**
     * 数据质量指标内部类
     */
    @Data
    public static class DataQualityDTO {
        /**
         * 数据完整性百分比
         */
        private Double integrityPercent;
        
        /**
         * 平均数据大小
         */
        private Double avgDataSize;
        
        /**
         * 数据更新频率
         */
        private Double updateFrequency;
        
        /**
         * 质量评级
         */
        private String qualityRating;
    }
}
