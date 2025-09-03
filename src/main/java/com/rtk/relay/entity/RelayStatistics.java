package com.rtk.relay.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据转发统计信息
 * 记录服务运行期间的各项统计数据
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
public class RelayStatistics {
    
    /**
     * 服务启动时间
     */
    private LocalDateTime startTime;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
    
    /**
     * 基站连接数（当前）
     */
    private AtomicLong currentBaseStationConnections = new AtomicLong(0);
    
    /**
     * 移动站连接数（当前）
     */
    private AtomicLong currentMobileStationConnections = new AtomicLong(0);
    
    /**
     * 基站总连接次数（累计）
     */
    private AtomicLong totalBaseStationConnections = new AtomicLong(0);
    
    /**
     * 移动站总连接次数（累计）
     */
    private AtomicLong totalMobileStationConnections = new AtomicLong(0);
    
    /**
     * 总接收字节数
     */
    private AtomicLong totalReceivedBytes = new AtomicLong(0);
    
    /**
     * 总发送字节数
     */
    private AtomicLong totalSentBytes = new AtomicLong(0);
    
    /**
     * 总接收消息数
     */
    private AtomicLong totalReceivedMessages = new AtomicLong(0);
    
    /**
     * 总发送消息数
     */
    private AtomicLong totalSentMessages = new AtomicLong(0);
    
    /**
     * 连接错误次数
     */
    private AtomicLong connectionErrors = new AtomicLong(0);
    
    /**
     * 数据转发错误次数
     */
    private AtomicLong relayErrors = new AtomicLong(0);
    
    /**
     * 构造函数，初始化启动时间
     */
    public RelayStatistics() {
        this.startTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastUpdateTime = LocalDateTime.now();
    }
}
