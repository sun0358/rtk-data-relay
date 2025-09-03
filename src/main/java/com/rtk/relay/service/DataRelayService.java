package com.rtk.relay.service;

import com.rtk.relay.entity.ConnectionInfo;
import com.rtk.relay.entity.RelayStatistics;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Collection;
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
     * 连接管理器
     */
    private final ConnectionManager connectionManager;

    private final ExecutorService relayExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r);
                t.setName("rtk-relay-" + t.getId());
                t.setDaemon(true);
                return t;
            }
    );

    /**
     * 心跳定时器
     */
    private ScheduledExecutorService heartbeatScheduler;

    /**
     * 构造函数
     *
     * @param connectionManager 连接管理器
     */
    public DataRelayService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
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

        // 每25秒发送一次心跳（小于frp的30秒间隔）
        heartbeatScheduler.scheduleWithFixedDelay(this::sendHeartbeat, 30, 25, TimeUnit.SECONDS);

        log.info("数据转发服务启动，心跳机制已启用（间隔25秒）");
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

            for (Channel channel : mobileStationChannels) {
                try {
                    if (channel.isActive()) {
                        // 发送心跳数据
                        channel.writeAndFlush(Unpooled.wrappedBuffer(HEARTBEAT_PACKET));

                        // 更新连接信息的最后活跃时间
                        ConnectionInfo connectionInfo = channel.attr(CONNECTION_INFO_KEY).get();
                        if (connectionInfo != null) {
                            connectionInfo.setLastActiveTime(LocalDateTime.now());
                            log.trace("发送心跳到移动站: {}", connectionInfo.getConnectionId());
                        }

                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.warn("发送心跳失败: {}", e.getMessage());
                    failureCount++;
                }
            }

            log.debug("心跳发送完成 - 成功: {}, 失败: {}", successCount, failureCount);

        } catch (Exception e) {
            log.error("心跳任务执行失败", e);
        }
    }

    /**
     * 将数据转发给所有移动站
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
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);

        // 异步转发数据到所有移动站
        CompletableFuture<Void>[] futures = mobileStationChannels.stream()
                .map(channel -> CompletableFuture.runAsync(() -> {
                    try {
                        if (channel.isActive()) {
                            // 发送数据到移动站
                            channel.writeAndFlush(Unpooled.wrappedBuffer(data));
                            successCount.incrementAndGet();

                            // 更新移动站的最后活跃时间和统计信息
                            ConnectionInfo connectionInfo = channel.attr(CONNECTION_INFO_KEY).get();
                            if (connectionInfo != null) {
                                connectionInfo.setLastActiveTime(LocalDateTime.now());
                                connectionInfo.setSentBytes(connectionInfo.getSentBytes() + data.length);
                                connectionInfo.setSentMessages(connectionInfo.getSentMessages() + 1);
                            }

                            // 更新全局统计
                            statistics.getTotalSentBytes().addAndGet(data.length);
                            statistics.getTotalSentMessages().incrementAndGet();

                            log.debug("数据转发成功 - 目标通道: {}, 数据长度: {} 字节",
                                    channel.remoteAddress(), data.length);
                        } else {
                            log.warn("目标通道未激活，跳过转发 - 通道: {}", channel.remoteAddress());
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.error("数据转发失败 - 目标通道: {}, 错误: {}",
                                channel.remoteAddress(), e.getMessage());
                        failureCount.incrementAndGet();
                        statistics.getRelayErrors().incrementAndGet();
                    }
                }, relayExecutor))
                .toArray(CompletableFuture[]::new);

        // 等待所有转发完成
        CompletableFuture.allOf(futures).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("数据转发过程中发生异常 - 源连接ID: {}", sourceConnectionId, throwable);
            }

            // 更新接收统计
            statistics.getTotalReceivedBytes().addAndGet(data.length);
            statistics.getTotalReceivedMessages().incrementAndGet();
            statistics.updateLastActiveTime();

            log.info("数据转发完成 - 源连接ID: {}, 数据长度: {} 字节, 成功: {}, 失败: {}",
                    sourceConnectionId, data.length, successCount.get(), failureCount.get());
        });
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
     * 服务销毁，清理资源
     */
    @PreDestroy
    public void destroy() {
        if (heartbeatScheduler != null) {
            log.info("停止心跳机制");
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}