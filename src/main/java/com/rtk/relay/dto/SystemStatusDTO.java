package com.rtk.relay.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统状态DTO
 * 用于RESTful API响应
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
public class SystemStatusDTO {
    
    /**
     * 服务运行状态
     */
    private Boolean serviceRunning;
    
    /**
     * 服务启动时间
     */
    private LocalDateTime startTime;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
    
    /**
     * 当前基站连接数
     */
    private Long currentBaseStationConnections;
    
    /**
     * 当前移动站连接数
     */
    private Long currentMobileStationConnections;
    
    /**
     * 总基站连接数
     */
    private Long totalBaseStationConnections;
    
    /**
     * 总移动站连接数
     */
    private Long totalMobileStationConnections;
    
    /**
     * 总接收字节数
     */
    private Long totalReceivedBytes;
    
    /**
     * 总发送字节数
     */
    private Long totalSentBytes;
    
    /**
     * 总接收消息数
     */
    private Long totalReceivedMessages;
    
    /**
     * 总发送消息数
     */
    private Long totalSentMessages;
    
    /**
     * 连接错误数
     */
    private Long connectionErrors;
    
    /**
     * 转发错误数
     */
    private Long relayErrors;
    
    /**
     * 系统性能指标
     */
    private Map<String, Object> performance;
    
    /**
     * 数据库状态
     */
    private Map<String, Object> database;
}
