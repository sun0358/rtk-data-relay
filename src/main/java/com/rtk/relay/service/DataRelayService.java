package com.rtk.relay.service;

import com.rtk.relay.config.RtkDataBuffer;
import com.rtk.relay.entity.ConnectionInfo;
import com.rtk.relay.entity.RelayStatistics;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据转发服务
 * 负责将基站数据转发给所有连接的移动站
 * 包含心跳机制，确保长连接稳定
 *
 * @author RTK Team
 * @version 1.0.0
 */
@Service
@Slf4j
public class DataRelayService {

    /**
     * 连接信息属性键
     */
    private static final AttributeKey<ConnectionInfo> CONNECTION_INFO_KEY = AttributeKey.valueOf("connectionInfo");

    /**
     * 心跳数据包 - 发送最小的数据包保持连接活跃
     * 使用NMEA-0183格式的注释行，不会影响正常数据解析
     */
    private static final byte[] HEARTBEAT_PACKET = "$RTKH,HEARTBEAT*00\r\n".getBytes();
    
    /**
     * 同步转发的移动站数量阈值
     * 小于等于此数量时使用同步转发，确保数据可靠性
     * 大于此数量时使用异步转发，提高性能
     */
    private static final int SYNC_RELAY_THRESHOLD = 5;
    
    /**
     * 数据发送超时时间（毫秒）
     */
    private static final long SEND_TIMEOUT_MS = 200;

    /**
     * 连接管理器
     */
    private final ConnectionManager connectionManager;
    
    /**
     * 数据缓冲区
     */
    private final RtkDataBuffer dataBuffer;
    
    /**
     * 数据持久化服务（可选依赖）
     */
    @Autowired
    private DataPersistenceService dataPersistenceService;

    /**
     * 数据转发线程池 - 使用有界队列防止内存溢出
     */
    private final ThreadPoolExecutor relayExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(), // 核心线程数
            Runtime.getRuntime().availableProcessors() * 2, // 最大线程数
            60L, TimeUnit.SECONDS, // 线程空闲时间
            new ArrayBlockingQueue<>(1000), // 有界队列，防止内存溢出
            r -> {
                Thread t = new Thread(r);
                t.setName("rtk-relay-" + t.getId());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用线程执行
    );

    /**
     * 心跳定时器
     */
    private ScheduledExecutorService heartbeatScheduler;

    /**
     * 构造函数
     *
     * @param connectionManager 连接管理器
     * @param dataBuffer 数据缓冲区
     */
    public DataRelayService(ConnectionManager connectionManager, RtkDataBuffer dataBuffer) {
        this.connectionManager = connectionManager;
        this.dataBuffer = dataBuffer;
    }

    /**
     * 初始化服务，启动心跳机制
     */
    @PostConstruct
    public void init() {
        // 创建心跳定时器
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("rtk-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        // 每20秒发送一次心跳（小于frp的30秒间隔，留有足够缓冲）
        heartbeatScheduler.scheduleWithFixedDelay(this::sendHeartbeat, 30, 20, TimeUnit.SECONDS);

        log.info("数据转发服务启动，心跳机制已启用（间隔20秒）");
        log.info("转发策略：移动站数量 <= {} 使用同步转发，> {} 使用异步转发", SYNC_RELAY_THRESHOLD, SYNC_RELAY_THRESHOLD);
    }

    /**
     * 发送心跳包到所有移动站
     * 保持TCP连接活跃，防止被frp或其他中间设备断开
     */
    private void sendHeartbeat() {
        try {
            Collection<Channel> mobileStationChannels = connectionManager.getAllMobileStationChannels();

            if (mobileStationChannels.isEmpty()) {
                log.debug("没有移动站连接，跳过心跳");
                return;
            }

            int successCount = 0;
            int failureCount = 0;
            List<Channel> deadChannels = new ArrayList<>();

            for (Channel channel : mobileStationChannels) {
                try {
                    if (channel.isActive() && channel.isWritable()) {
                        // 发送心跳数据并检查结果
                        ChannelFuture future = channel.writeAndFlush(Unpooled.wrappedBuffer(HEARTBEAT_PACKET));
                        
                        // 异步检查发送结果，避免阻塞心跳线程
                        future.addListener(channelFuture -> {
                            if (!channelFuture.isSuccess()) {
                                log.warn("心跳发送失败: {}, 原因: {}", 
                                    channel.remoteAddress(), channelFuture.cause().getMessage());
                            }
                        });

                        // 更新连接信息的最后活跃时间
                        ConnectionInfo connectionInfo = channel.attr(CONNECTION_INFO_KEY).get();
                        if (connectionInfo != null) {
                            connectionInfo.setLastActiveTime(LocalDateTime.now());
                            log.trace("发送心跳到移动站: {}", connectionInfo.getConnectionId());
                        }

                        successCount++;
                    } else {
                        // 标记死连接，稍后清理
                        deadChannels.add(channel);
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.warn("发送心跳失败: {}, 通道: {}", e.getMessage(), channel.remoteAddress());
                    deadChannels.add(channel);
                    failureCount++;
                }
            }
            
            // 清理死连接
            for (Channel deadChannel : deadChannels) {
                try {
                    ConnectionInfo connectionInfo = deadChannel.attr(CONNECTION_INFO_KEY).get();
                    if (connectionInfo != null) {
                        log.info("清理死连接: {}", connectionInfo.getConnectionId());
                        connectionManager.unregisterMobileStation(connectionInfo.getConnectionId());
                    }
                } catch (Exception e) {
                    log.warn("清理死连接时出错: {}", e.getMessage());
                }
            }

            if (successCount > 0 || failureCount > 0) {
                log.debug("心跳发送完成 - 成功: {}, 失败: {}, 清理死连接: {}", 
                    successCount, failureCount, deadChannels.size());
            }

        } catch (Exception e) {
            log.error("心跳任务执行失败", e);
        }
    }

    /**
     * 将数据转发给所有移动站
     * 使用混合策略：少量移动站同步转发，大量移动站异步转发
     *
     * @param data 要转发的数据
     * @param sourceConnectionId 数据源连接ID（基站连接ID）
     */
    public void relayDataToMobileStations(byte[] data, String sourceConnectionId) {
        if (data == null || data.length == 0) {
            log.warn("数据为空，跳过转发 - 源连接ID: {}", sourceConnectionId);
            return;
        }

        Collection<Channel> mobileStationChannels = connectionManager.getAllMobileStationChannels();
        if (mobileStationChannels.isEmpty()) {
            log.debug("没有移动站连接，跳过数据转发 - 源连接ID: {}, 数据长度: {} 字节",
                    sourceConnectionId, data.length);
            return;
        }

        RelayStatistics statistics = connectionManager.getStatistics();
        
        // 更新接收统计（在转发前更新，确保统计准确）
        statistics.getTotalReceivedBytes().addAndGet(data.length);
        statistics.getTotalReceivedMessages().incrementAndGet();
        statistics.updateLastActiveTime();
        
        // 将数据添加到缓冲区（供新连接的移动站使用）
        try {
            dataBuffer.addData(data);
        } catch (Exception e) {
            log.warn("添加数据到缓冲区失败: {}", e.getMessage());
        }
        
        // 存储基站RTCM差分数据（优化存储策略）
        if (dataPersistenceService.isDatabaseEnabled()) {
            try {
                // 获取基站IP地址（从连接管理器获取）
                String remoteAddress = getBaseStationAddress(sourceConnectionId);
                dataPersistenceService.storeBaseStationRtcmData(sourceConnectionId, remoteAddress, data);
            } catch (Exception e) {
                log.warn("存储基站RTCM数据失败: {}", e.getMessage());
            }
        }

        // 根据移动站数量选择转发策略
        if (mobileStationChannels.size() <= SYNC_RELAY_THRESHOLD) {
            // 少量移动站：同步转发，保证数据可靠性
            relaySynchronously(data, mobileStationChannels, sourceConnectionId, statistics);
        } else {
            // 大量移动站：异步转发，提高性能
            relayAsynchronously(data, mobileStationChannels, sourceConnectionId, statistics);
        }
    }
    
    /**
     * 同步转发数据到移动站
     * 确保每条数据都成功发送，适用于少量移动站
     */
    private void relaySynchronously(byte[] data, Collection<Channel> channels, 
                                   String sourceConnectionId, RelayStatistics statistics) {
        int successCount = 0;
        int failureCount = 0;
        List<Channel> deadChannels = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        for (Channel channel : channels) {
            try {
                if (channel.isActive() && channel.isWritable()) {
                    // 同步发送数据
                    ChannelFuture future = channel.writeAndFlush(Unpooled.wrappedBuffer(data));
                    
                    // 等待发送完成，设置超时时间
                    if (future.await(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        if (future.isSuccess()) {
                            // 发送成功，更新统计信息
                            updateConnectionStats(channel, data, statistics);
                            successCount++;
                            
                            log.debug("同步转发成功 - 目标: {}, 数据长度: {} 字节",
                                    channel.remoteAddress(), data.length);
                        } else {
                            log.warn("同步转发失败 - 目标: {}, 原因: {}",
                                    channel.remoteAddress(), future.cause().getMessage());
                            deadChannels.add(channel);
                            failureCount++;
                            statistics.getRelayErrors().incrementAndGet();
                        }
                    } else {
                        log.warn("同步转发超时 - 目标: {}, 超时时间: {}ms",
                                channel.remoteAddress(), SEND_TIMEOUT_MS);
                        deadChannels.add(channel);
                        failureCount++;
                        statistics.getRelayErrors().incrementAndGet();
                    }
                } else {
                    log.warn("通道不可用，跳过转发 - 目标: {}, isActive: {}, isWritable: {}",
                            channel.remoteAddress(), channel.isActive(), channel.isWritable());
                    deadChannels.add(channel);
                    failureCount++;
                }
            } catch (Exception e) {
                log.error("同步转发异常 - 目标: {}, 错误: {}",
                        channel.remoteAddress(), e.getMessage());
                deadChannels.add(channel);
                failureCount++;
                statistics.getRelayErrors().incrementAndGet();
            }
        }
        
        // 清理死连接
        cleanupDeadChannels(deadChannels);
        
        long endTime = System.currentTimeMillis();
        log.info("同步转发完成 - 源: {}, 数据: {}字节, 成功: {}, 失败: {}, 耗时: {}ms",
                sourceConnectionId, data.length, successCount, failureCount, (endTime - startTime));
    }
    
    /**
     * 异步转发数据到移动站
     * 提高并发性能，适用于大量移动站
     */
    private void relayAsynchronously(byte[] data, Collection<Channel> channels, 
                                    String sourceConnectionId, RelayStatistics statistics) {
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        List<Channel> deadChannels = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        // 创建异步任务
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = channels.stream()
                .map(channel -> CompletableFuture.runAsync(() -> {
                    try {
                        if (channel.isActive() && channel.isWritable()) {
                            // 异步发送数据
                            ChannelFuture future = channel.writeAndFlush(Unpooled.wrappedBuffer(data));
                            
                            // 添加监听器处理发送结果
                            future.addListener(channelFuture -> {
                                if (channelFuture.isSuccess()) {
                                    updateConnectionStats(channel, data, statistics);
                                    successCount.incrementAndGet();
                                    
                                    log.debug("异步转发成功 - 目标: {}, 数据长度: {} 字节",
                                            channel.remoteAddress(), data.length);
                                } else {
                                    log.warn("异步转发失败 - 目标: {}, 原因: {}",
                                            channel.remoteAddress(), channelFuture.cause().getMessage());
                                    synchronized (deadChannels) {
                                        deadChannels.add(channel);
                                    }
                                    failureCount.incrementAndGet();
                                    statistics.getRelayErrors().incrementAndGet();
                                }
                            });
                        } else {
                            log.warn("通道不可用，跳过转发 - 目标: {}, isActive: {}, isWritable: {}",
                                    channel.remoteAddress(), channel.isActive(), channel.isWritable());
                            synchronized (deadChannels) {
                                deadChannels.add(channel);
                            }
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.error("异步转发异常 - 目标: {}, 错误: {}",
                                channel.remoteAddress(), e.getMessage());
                        synchronized (deadChannels) {
                            deadChannels.add(channel);
                        }
                        failureCount.incrementAndGet();
                        statistics.getRelayErrors().incrementAndGet();
                    }
                }, relayExecutor))
                .toArray(CompletableFuture[]::new);

        // 等待所有异步任务完成
        CompletableFuture.allOf(futures).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("异步转发过程中发生异常 - 源连接ID: {}", sourceConnectionId, throwable);
            }
            
            // 清理死连接
            cleanupDeadChannels(deadChannels);
            
            long endTime = System.currentTimeMillis();
            log.info("异步转发完成 - 源: {}, 数据: {}字节, 成功: {}, 失败: {}, 耗时: {}ms",
                    sourceConnectionId, data.length, successCount.get(), failureCount.get(), (endTime - startTime));
        });
    }
    
    /**
     * 更新连接统计信息
     */
    private void updateConnectionStats(Channel channel, byte[] data, RelayStatistics statistics) {
        // 更新移动站连接信息
        ConnectionInfo connectionInfo = channel.attr(CONNECTION_INFO_KEY).get();
        if (connectionInfo != null) {
            connectionInfo.setLastActiveTime(LocalDateTime.now());
            connectionInfo.setSentBytes(connectionInfo.getSentBytes() + data.length);
            connectionInfo.setSentMessages(connectionInfo.getSentMessages() + 1);
        }
        
        // 更新全局统计
        statistics.getTotalSentBytes().addAndGet(data.length);
        statistics.getTotalSentMessages().incrementAndGet();
    }
    
    /**
     * 清理死连接
     */
    private void cleanupDeadChannels(List<Channel> deadChannels) {
        for (Channel deadChannel : deadChannels) {
            try {
                ConnectionInfo connectionInfo = deadChannel.attr(CONNECTION_INFO_KEY).get();
                if (connectionInfo != null) {
                    log.info("清理死连接: {}", connectionInfo.getConnectionId());
                    connectionManager.unregisterMobileStation(connectionInfo.getConnectionId());
                } else {
                    // 强制关闭没有连接信息的通道
                    deadChannel.close();
                }
            } catch (Exception e) {
                log.warn("清理死连接时出错: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取当前活跃的移动站连接数
     *
     * @return 移动站连接数
     */
    public int getActiveMobileStationCount() {
        return connectionManager.getMobileStationCount();
    }

    /**
     * 获取当前活跃的基站连接数
     *
     * @return 基站连接数
     */
    public int getActiveBaseStationCount() {
        return connectionManager.getBaseStationCount();
    }

    /**
     * 为新连接的移动站发送缓存数据
     * @param channel 移动站通道
     * @param connectionId 连接ID
     */
    public void sendBufferedDataToNewMobileStation(Channel channel, String connectionId) {
        try {
            List<byte[]> recentData = dataBuffer.getRecentData();
            if (recentData.isEmpty()) {
                log.debug("无缓存数据可发送给新移动站: {}", connectionId);
                return;
            }
            
            int successCount = 0;
            int failureCount = 0;
            
            for (byte[] data : recentData) {
                try {
                    if (channel.isActive() && channel.isWritable()) {
                        ChannelFuture future = channel.writeAndFlush(Unpooled.wrappedBuffer(data));
                        
                        // 等待发送完成，但不阻塞太久
                        if (future.await(100, TimeUnit.MILLISECONDS)) {
                            if (future.isSuccess()) {
                                successCount++;
                            } else {
                                failureCount++;
                                break; // 发送失败就停止
                            }
                        } else {
                            failureCount++;
                            break; // 超时就停止
                        }
                    } else {
                        log.warn("移动站通道不可用，停止发送缓存数据: {}", connectionId);
                        break;
                    }
                } catch (Exception e) {
                    log.warn("发送缓存数据失败: {}", e.getMessage());
                    failureCount++;
                    break;
                }
            }
            
            log.info("缓存数据发送完成 - 移动站: {}, 成功: {}, 失败: {}, 总数: {}",
                    connectionId, successCount, failureCount, recentData.size());
                    
        } catch (Exception e) {
            log.error("发送缓存数据到新移动站时发生错误: {}", e.getMessage());
        }
    }
    
    /**
     * 获取线程池状态信息（用于监控）
     */
    public String getThreadPoolStatus() {
        return String.format("ThreadPool[Active: %d, Pool: %d, Queue: %d, Completed: %d]",
                relayExecutor.getActiveCount(),
                relayExecutor.getPoolSize(),
                relayExecutor.getQueue().size(),
                relayExecutor.getCompletedTaskCount());
    }
    
    /**
     * 获取数据缓冲区状态信息（用于监控）
     */
    public String getDataBufferStatus() {
        return dataBuffer.getBufferStats();
    }
    
    /**
     * 获取基站IP地址
     */
    private String getBaseStationAddress(String baseStationId) {
        try {
            // 从连接管理器获取基站连接信息
            ConnectionInfo connectionInfo = connectionManager.getConnectionInfo(baseStationId);
            if (connectionInfo != null) {
                return connectionInfo.getRemoteAddress();
            }
        } catch (Exception e) {
            log.debug("获取基站IP地址失败: {}", e.getMessage());
        }
        return "unknown";
    }
    
    /**
     * 获取数据库状态信息（用于监控）
     */
    public Map<String, Object> getDatabaseStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("enabled", dataPersistenceService.isDatabaseEnabled());
        
        if (dataPersistenceService.isDatabaseEnabled()) {
            // 获取存储效率统计
            status.put("storageEfficiency", dataPersistenceService.getStorageEfficiencyStats(7));
            status.put("dataQuality", dataPersistenceService.getDataQualitySummary(7));
            status.put("relayPerformance", dataPersistenceService.getRelayPerformanceStats(24));
            status.put("baseStationStatus", dataPersistenceService.getCurrentBaseStationStatus());
        } else {
            status.put("enabled", false);
            status.put("reason", "Database connection not available");
        }
        
        return status;
    }
    
    /**
     * 服务销毁，清理资源
     */
    @PreDestroy
    public void destroy() {
        log.info("正在关闭数据转发服务...");
        
        // 停止心跳机制
        if (heartbeatScheduler != null) {
            log.info("停止心跳机制");
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("心跳调度器未能正常关闭，强制关闭");
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("等待心跳调度器关闭时被中断");
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭转发线程池
        if (relayExecutor != null) {
            log.info("停止数据转发线程池");
            relayExecutor.shutdown();
            try {
                if (!relayExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("转发线程池未能正常关闭，强制关闭");
                    relayExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("等待转发线程池关闭时被中断");
                relayExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("数据转发服务已关闭");
    }
}