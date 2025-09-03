package com.rtk.relay.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 连接信息实体类
 * 记录TCP连接的基本信息和统计数据
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
public class ConnectionInfo {
    
    /**
     * 连接ID，唯一标识
     */
    private String connectionId;
    
    /**
     * 连接类型：BASE_STATION（基站）、MOBILE_STATION（移动站）
     */
    private ConnectionType type;
    
    /**
     * 远程地址
     */
    private String remoteAddress;
    
    /**
     * 远程端口
     */
    private int remotePort;
    
    /**
     * 连接建立时间
     */
    private LocalDateTime connectTime;
    
    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;
    
    /**
     * 连接状态
     */
    private ConnectionStatus status;
    
    /**
     * 接收字节数
     */
    private long receivedBytes;
    
    /**
     * 发送字节数
     */
    private long sentBytes;
    
    /**
     * 接收消息数
     */
    private long receivedMessages;
    
    /**
     * 发送消息数
     */
    private long sentMessages;
    
    /**
     * 连接类型枚举
     */
    public enum ConnectionType {
        /**
         * 基站连接
         */
        BASE_STATION,
        
        /**
         * 移动站连接
         */
        MOBILE_STATION
    }
    
    /**
     * 连接状态枚举
     */
    public enum ConnectionStatus {
        /**
         * 已连接
         */
        CONNECTED,
        
        /**
         * 已断开
         */
        DISCONNECTED,
        
        /**
         * 连接异常
         */
        ERROR
    }
}
