package com.rtk.relay.service;

import com.rtk.relay.entity.RelayStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 健康检查服务
 * 定期检查服务运行状态，记录关键指标
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Service
@Slf4j
public class HealthCheckService {
    
    /**
     * 连接管理器
     */
    private final ConnectionManager connectionManager;
    
    /**
     * 数据转发服务
     */
    private final DataRelayService dataRelayService;
    
    /**
     * TCP服务器服务
     */
    private final TcpServerService tcpServerService;
    
    /**
     * 构造函数
     * 
     * @param connectionManager 连接管理器
     * @param dataRelayService 数据转发服务
     * @param tcpServerService TCP服务器服务
     */
    public HealthCheckService(ConnectionManager connectionManager, 
                            DataRelayService dataRelayService,
                            TcpServerService tcpServerService) {
        this.connectionManager = connectionManager;
        this.dataRelayService = dataRelayService;
        this.tcpServerService = tcpServerService;
    }
    
    /**
     * 定期健康检查
     * 每分钟执行一次，记录服务运行状态
     */
    @Scheduled(fixedRate = 60000) // 每60秒执行一次
    public void performHealthCheck() {
        try {
            RelayStatistics statistics = connectionManager.getStatistics();
            
            boolean serverRunning = tcpServerService.isServerRunning();
            int baseStationCount = dataRelayService.getActiveBaseStationCount();
            int mobileStationCount = dataRelayService.getActiveMobileStationCount();
            
            // 记录健康状态日志
            log.info("健康检查 - 时间: {}, 服务状态: {}, 基站连接: {}, 移动站连接: {}, " +
                    "总接收: {} 字节, 总发送: {} 字节, 转发错误: {}", 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    serverRunning ? "正常" : "异常",
                    baseStationCount,
                    mobileStationCount,
                    statistics.getTotalReceivedBytes().get(),
                    statistics.getTotalSentBytes().get(),
                    statistics.getRelayErrors().get());
            
            // 检查异常情况
            if (!serverRunning) {
                log.error("健康检查发现服务器未运行，需要人工介入");
            }
            
            if (baseStationCount == 0) {
                log.warn("健康检查发现没有基站连接");
            }
            
            if (statistics.getRelayErrors().get() > 100) {
                log.warn("健康检查发现转发错误次数过多: {}", statistics.getRelayErrors().get());
            }
            
        } catch (Exception e) {
            log.error("健康检查执行失败", e);
        }
    }
    
    /**
     * 定期统计报告
     * 每小时执行一次，生成详细的统计报告
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void generateStatisticsReport() {
        try {
            RelayStatistics statistics = connectionManager.getStatistics();
            
            log.info("=== RTK数据转发服务统计报告 ===");
            log.info("服务启动时间: {}", 
                    statistics.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            log.info("当前基站连接数: {}", statistics.getCurrentBaseStationConnections().get());
            log.info("当前移动站连接数: {}", statistics.getCurrentMobileStationConnections().get());
            log.info("累计基站连接次数: {}", statistics.getTotalBaseStationConnections().get());
            log.info("累计移动站连接次数: {}", statistics.getTotalMobileStationConnections().get());
            log.info("总接收数据量: {} 字节 ({} MB)", 
                    statistics.getTotalReceivedBytes().get(),
                    statistics.getTotalReceivedBytes().get() / 1024 / 1024);
            log.info("总发送数据量: {} 字节 ({} MB)", 
                    statistics.getTotalSentBytes().get(),
                    statistics.getTotalSentBytes().get() / 1024 / 1024);
            log.info("总接收消息数: {}", statistics.getTotalReceivedMessages().get());
            log.info("总发送消息数: {}", statistics.getTotalSentMessages().get());
            log.info("连接错误次数: {}", statistics.getConnectionErrors().get());
            log.info("转发错误次数: {}", statistics.getRelayErrors().get());
            log.info("================================");
            
        } catch (Exception e) {
            log.error("生成统计报告失败", e);
        }
    }
}
