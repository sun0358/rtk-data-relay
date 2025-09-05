package com.rtk.relay.service;

import com.rtk.relay.config.RtkProperties;
import com.rtk.relay.entity.ConnectionInfo;
import com.rtk.relay.entity.RelayStatistics;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 连接管理服务
 * 管理基站和移动站的TCP连接，提供连接注册、注销、查询等功能
 *
 * @author RTK Team
 * @version 1.0.0
 */
@Service
@Slf4j
public class ConnectionManager {

    /**
     * 基站连接映射表：连接ID -> Channel
     */
    private final ConcurrentHashMap<String, Channel> baseStationChannels = new ConcurrentHashMap<>();

    /**
     * 移动站连接映射表：连接ID -> Channel
     */
    private final ConcurrentHashMap<String, Channel> mobileStationChannels = new ConcurrentHashMap<>();

    /**
     * 连接信息映射表：连接ID -> ConnectionInfo
     */
    private final ConcurrentHashMap<String, ConnectionInfo> connectionInfoMap = new ConcurrentHashMap<>();

    /**
     * 统计信息
     */
    private final RelayStatistics statistics = new RelayStatistics();

    /**
     * RTK配置
     */
    private final RtkProperties rtkProperties;
    
    /**
     * 数据持久化服务
     */
    @Autowired
    private DataPersistenceService dataPersistenceService;

    /**
     * 定时任务执行器（用于连接检查和清理）
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 构造函数
     *
     * @param rtkProperties RTK配置
     */
    public ConnectionManager(RtkProperties rtkProperties) {
        this.rtkProperties = rtkProperties;
        // 启动连接检查任务
        startConnectionCheckTask();
    }

    /**
     * 注册基站连接
     *
     * @param connectionId 连接ID
     * @param channel 通道
     * @param connectionInfo 连接信息
     */
    public void registerBaseStation(String connectionId, Channel channel, ConnectionInfo connectionInfo) {
        baseStationChannels.put(connectionId, channel);
        connectionInfoMap.put(connectionId, connectionInfo);
        statistics.getCurrentBaseStationConnections().incrementAndGet();
        statistics.getTotalBaseStationConnections().incrementAndGet();
        statistics.updateLastActiveTime();

        // 记录连接建立到数据库
        try {
            dataPersistenceService.recordConnectionEstablished(
                connectionId, 
                "BASE_STATION", 
                connectionInfo.getRemoteAddress(),
                connectionInfo.getRemotePort()
            );
        } catch (Exception e) {
            log.warn("记录基站连接建立失败: {}", e.getMessage());
        }

        log.info("基站连接已注册 - 连接ID: {}, 当前基站连接数: {}",
                connectionId, statistics.getCurrentBaseStationConnections().get());
    }

    /**
     * 注册移动站连接
     *
     * @param connectionId 连接ID
     * @param channel 通道
     * @param connectionInfo 连接信息
     */
    public void registerMobileStation(String connectionId, Channel channel, ConnectionInfo connectionInfo) {
        mobileStationChannels.put(connectionId, channel);
        connectionInfoMap.put(connectionId, connectionInfo);
        statistics.getCurrentMobileStationConnections().incrementAndGet();
        statistics.getTotalMobileStationConnections().incrementAndGet();
        statistics.updateLastActiveTime();

        // 记录连接建立到数据库
        try {
            dataPersistenceService.recordConnectionEstablished(
                connectionId, 
                "MOBILE_STATION", 
                connectionInfo.getRemoteAddress(),
                connectionInfo.getRemotePort()
            );
        } catch (Exception e) {
            log.warn("记录移动站连接建立失败: {}", e.getMessage());
        }

        log.info("移动站连接已注册 - 连接ID: {}, 当前移动站连接数: {}",
                connectionId, statistics.getCurrentMobileStationConnections().get());
    }

    /**
     * 注销基站连接
     *
     * @param connectionId 连接ID
     */
    public void unregisterBaseStation(String connectionId) {
        ConnectionInfo connectionInfo = connectionInfoMap.get(connectionId);
        
        baseStationChannels.remove(connectionId);
        connectionInfoMap.remove(connectionId);
        statistics.getCurrentBaseStationConnections().decrementAndGet();
        statistics.updateLastActiveTime();

        // 记录连接断开到数据库
        if (connectionInfo != null) {
            try {
                dataPersistenceService.recordConnectionClosed(
                    connectionId, 
                    connectionInfo.getReceivedBytes(),
                    connectionInfo.getSentBytes(),
                    "DISCONNECTED"
                );
            } catch (Exception e) {
                log.warn("记录基站连接断开失败: {}", e.getMessage());
            }
        }

        log.info("基站连接已注销 - 连接ID: {}, 当前基站连接数: {}",
                connectionId, statistics.getCurrentBaseStationConnections().get());
    }

    /**
     * 注销移动站连接
     *
     * @param connectionId 连接ID
     */
    public void unregisterMobileStation(String connectionId) {
        ConnectionInfo connectionInfo = connectionInfoMap.get(connectionId);
        
        mobileStationChannels.remove(connectionId);
        connectionInfoMap.remove(connectionId);
        statistics.getCurrentMobileStationConnections().decrementAndGet();
        statistics.updateLastActiveTime();

        // 记录连接断开到数据库
        if (connectionInfo != null) {
            try {
                dataPersistenceService.recordConnectionClosed(
                    connectionId,
                    connectionInfo.getReceivedBytes(),
                    connectionInfo.getSentBytes(),
                    "DISCONNECTED"
                );
            } catch (Exception e) {
                log.warn("记录移动站连接断开失败: {}", e.getMessage());
            }
        }

        log.info("移动站连接已注销 - 连接ID: {}, 当前移动站连接数: {}",
                connectionId, statistics.getCurrentMobileStationConnections().get());
    }

    /**
     * 获取所有移动站通道
     *
     * @return 移动站通道集合
     */
    public Collection<Channel> getAllMobileStationChannels() {
        return mobileStationChannels.values();
    }

    /**
     * 获取移动站连接数
     *
     * @return 移动站连接数
     */
    public int getMobileStationCount() {
        return mobileStationChannels.size();
    }

    /**
     * 获取基站连接数
     *
     * @return 基站连接数
     */
    public int getBaseStationCount() {
        return baseStationChannels.size();
    }

    /**
     * 获取最大移动站连接数
     *
     * @return 最大移动站连接数
     */
    public int getMaxMobileStationConnections() {
        return rtkProperties.getServer2().getMaxConnections();
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public RelayStatistics getStatistics() {
        return statistics;
    }

    /**
     * 获取所有连接信息
     *
     * @return 连接信息集合
     */
    public Collection<ConnectionInfo> getAllConnectionInfo() {
        return connectionInfoMap.values();
    }
    
    /**
     * 获取指定连接的信息
     *
     * @param connectionId 连接ID
     * @return 连接信息，如果不存在则返回null
     */
    public ConnectionInfo getConnectionInfo(String connectionId) {
        return connectionInfoMap.get(connectionId);
    }

    /**
     * 启动连接检查任务
     * 定期检查连接状态，清理无效连接
     */
    private void startConnectionCheckTask() {
        // 每30秒检查一次连接状态
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                checkAndCleanupConnections();
            } catch (Exception e) {
                log.error("连接检查任务执行失败", e);
            }
        }, 30, 30, TimeUnit.SECONDS);

        log.info("连接检查任务已启动，检查间隔: 30秒");
    }

    /**
     * 检查并清理无效连接
     * 【修复】：避免重复扣减计数器，只清理Map中的连接，不重复扣减统计计数
     */
    private void checkAndCleanupConnections() {
        log.debug("开始检查连接状态...");

        // 基站连接：检查channel状态和超时
        baseStationChannels.entrySet().removeIf(entry -> {
            String connectionId = entry.getKey();
            Channel channel = entry.getValue();
            ConnectionInfo connectionInfo = connectionInfoMap.get(connectionId);

            // 只检查channel是否还活跃
            if (!channel.isActive()) {
                log.warn("清理无效基站连接 - 连接ID: {} (channel已断开)", connectionId);
                connectionInfoMap.remove(connectionId);
                // 【关键修复】不在这里扣减计数器，避免与channelInactive重复扣减
                // statistics.getCurrentBaseStationConnections().decrementAndGet();
                try {
                    channel.close();
                } catch (Exception e) {
                    log.error("关闭基站连接失败", e);
                }
                return true;
            }

            // 基站可以检查超时（如果需要）
            if (connectionInfo != null) {
                LocalDateTime now = LocalDateTime.now();
                int timeoutSeconds = rtkProperties.getServer1().getTimeout();
                if (connectionInfo.getLastActiveTime().plusSeconds(timeoutSeconds).isBefore(now)) {
                    log.warn("基站连接超时 - 连接ID: {}, 最后活跃: {}",
                            connectionId, connectionInfo.getLastActiveTime());
                    connectionInfoMap.remove(connectionId);
                    // 【关键修复】超时的情况下才扣减，因为channelInactive可能不会被调用
                    statistics.getCurrentBaseStationConnections().decrementAndGet();
                    try {
                        channel.close();
                    } catch (Exception e) {
                        log.error("关闭超时基站连接失败", e);
                    }
                    return true;
                }
            }

            return false;
        });

        // 【关键修复】移动站连接：只检查channel状态，不检查超时
        mobileStationChannels.entrySet().removeIf(entry -> {
            String connectionId = entry.getKey();
            Channel channel = entry.getValue();

            // 只检查channel是否还活跃，不检查lastActiveTime
            if (!channel.isActive()) {
                log.warn("清理无效移动站连接 - 连接ID: {} (channel已断开)", connectionId);
                connectionInfoMap.remove(connectionId);
                // 【关键修复】不在这里扣减计数器，避免与channelInactive重复扣减
                // statistics.getCurrentMobileStationConnections().decrementAndGet();
                try {
                    channel.close();
                } catch (Exception e) {
                    log.error("关闭移动站连接失败", e);
                }
                return true;
            }

            // 【重要】移动站不检查超时，因为移动站只接收数据不发送数据
            // 保持连接直到客户端主动断开或网络异常

            return false;
        });

        // 【新增】修正计数器，确保与实际Map大小一致
        int actualBaseStations = baseStationChannels.size();
        int actualMobileStations = mobileStationChannels.size();
        long currentBaseStations = statistics.getCurrentBaseStationConnections().get();
        long currentMobileStations = statistics.getCurrentMobileStationConnections().get();
        
        if (actualBaseStations != currentBaseStations) {
            log.warn("修正基站连接计数器 - 实际: {}, 计数器: {} -> {}", 
                    actualBaseStations, currentBaseStations, actualBaseStations);
            statistics.getCurrentBaseStationConnections().set(actualBaseStations);
        }
        
        if (actualMobileStations != currentMobileStations) {
            log.warn("修正移动站连接计数器 - 实际: {}, 计数器: {} -> {}", 
                    actualMobileStations, currentMobileStations, actualMobileStations);
            statistics.getCurrentMobileStationConnections().set(actualMobileStations);
        }

        log.debug("连接检查完成 - 基站: {}, 移动站: {}",
                baseStationChannels.size(), mobileStationChannels.size());
    }

    /**
     * 关闭所有连接和资源
     */
    public void shutdown() {
        log.info("正在关闭连接管理器...");

        // 关闭所有基站连接
        baseStationChannels.values().forEach(Channel::close);
        baseStationChannels.clear();

        // 关闭所有移动站连接
        mobileStationChannels.values().forEach(Channel::close);
        mobileStationChannels.clear();

        // 清理连接信息
        connectionInfoMap.clear();

        // 关闭定时任务
        scheduler.shutdown();

        log.info("连接管理器已关闭");
    }
}