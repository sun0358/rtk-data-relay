package com.rtk.relay.controller;

import com.rtk.relay.entity.ConnectionHistory;
import com.rtk.relay.entity.ConnectionInfo;
import com.rtk.relay.entity.HourlyStatistics;
import com.rtk.relay.entity.RelayStatistics;
import com.rtk.relay.mapper.ConnectionHistoryMapper;
import com.rtk.relay.mapper.HourlyStatisticsMapper;
import com.rtk.relay.service.ConnectionManager;
import com.rtk.relay.service.DataRelayService;
import com.rtk.relay.service.TcpServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控控制器
 * 提供RTK数据转发服务的监控和统计接口
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/monitor")
@Slf4j
public class MonitorController {
    
    /**
     * 简单的ping接口，用于测试服务是否正常
     * 
     * @return 简单的响应信息
     */
    @GetMapping("/ping")
    public String ping() {
        return "RTK Data Relay Service is running! Time: " + System.currentTimeMillis();
    }
    
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

    @Autowired(required = false)  // 设置为非必需，避免启动失败
    private ConnectionHistoryMapper historyMapper;

    @Autowired(required = false)  // 设置为非必需，避免启动失败
    private HourlyStatisticsMapper statisticsMapper;
    
    /**
     * 构造函数
     * 
     * @param connectionManager 连接管理器
     * @param dataRelayService 数据转发服务
     * @param tcpServerService TCP服务器服务
     */
    public MonitorController(ConnectionManager connectionManager, 
                           DataRelayService dataRelayService,
                           TcpServerService tcpServerService) {
        this.connectionManager = connectionManager;
        this.dataRelayService = dataRelayService;
        this.tcpServerService = tcpServerService;
    }
    
    /**
     * 获取服务状态信息
     * 
     * @return 服务状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 服务器运行状态
        status.put("serverRunning", tcpServerService.isServerRunning());
        
        // 连接统计
        status.put("activeBaseStations", dataRelayService.getActiveBaseStationCount());
        status.put("activeMobileStations", dataRelayService.getActiveMobileStationCount());
        
        // 基础信息
        status.put("timestamp", System.currentTimeMillis());
        status.put("message", "RTK数据转发服务运行正常");
        
        return status;
    }
    
    /**
     * 获取详细统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/statistics")
    public RelayStatistics getStatistics() {
        return connectionManager.getStatistics();
    }
    
    /**
     * 获取所有连接信息
     * 
     * @return 连接信息列表
     */
    @GetMapping("/connections")
    public Collection<ConnectionInfo> getConnections() {
        return connectionManager.getAllConnectionInfo();
    }
    
    /**
     * 获取基站连接信息
     * 
     * @return 基站连接信息
     */
    @GetMapping("/connections/base-stations")
    public Map<String, Object> getBaseStationConnections() {
        Collection<ConnectionInfo> allConnections = connectionManager.getAllConnectionInfo();
        
        Map<String, Object> result = new HashMap<>();
        result.put("count", dataRelayService.getActiveBaseStationCount());
        result.put("connections", allConnections.stream()
                .filter(conn -> conn.getType() == ConnectionInfo.ConnectionType.BASE_STATION)
                .filter(conn -> conn.getStatus() == ConnectionInfo.ConnectionStatus.CONNECTED)
                .toArray());
        
        return result;
    }
    
    /**
     * 获取移动站连接信息
     * 
     * @return 移动站连接信息
     */
    @GetMapping("/connections/mobile-stations")
    public Map<String, Object> getMobileStationConnections() {
        Collection<ConnectionInfo> allConnections = connectionManager.getAllConnectionInfo();
        
        Map<String, Object> result = new HashMap<>();
        result.put("count", dataRelayService.getActiveMobileStationCount());
        result.put("connections", allConnections.stream()
                .filter(conn -> conn.getType() == ConnectionInfo.ConnectionType.MOBILE_STATION)
                .filter(conn -> conn.getStatus() == ConnectionInfo.ConnectionStatus.CONNECTED)
                .toArray());
        
        return result;
    }
    
    /**
     * 获取服务健康状态��用于健康检查）
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        boolean isHealthy = tcpServerService.isServerRunning();
        health.put("status", isHealthy ? "UP" : "DOWN");
        health.put("serverRunning", isHealthy);
        health.put("baseStationConnections", dataRelayService.getActiveBaseStationCount());
        health.put("mobileStationConnections", dataRelayService.getActiveMobileStationCount());
        
        return health;
    }

    /**
     * 获取历史连接记录
     */
    @GetMapping("/history")
    public List<ConnectionHistory> getConnectionHistory(
            @RequestParam(defaultValue = "24") int hours) {
        if (historyMapper == null) {
            log.warn("数据库未配置，无法查询历史连接记录");
            return java.util.Collections.emptyList();
        }
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return historyMapper.selectRecentConnections(since);
    }

    /**
     * 获取统计趋势
     */
    @GetMapping("/trends")
    public List<HourlyStatistics> getStatisticsTrends(
            @RequestParam(defaultValue = "7") int days) {
        if (statisticsMapper == null) {
            log.warn("数据库未配置，无法查询统计趋势");
            return java.util.Collections.emptyList();
        }
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return statisticsMapper.selectByTimeRange(since, LocalDateTime.now());
    }

    /**
     * 获取连接排行
     */
    @GetMapping("/top-connections")
    public List<Map<String, Object>> getTopConnections() {
        if (historyMapper == null) {
            log.warn("数据库未配置，无法查询连接排行");
            return java.util.Collections.emptyList();
        }
        return historyMapper.selectTopConnections(10);
    }

    /**
     * 导出报表（CSV格式）
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        // 生成CSV报表
        String csv = generateCSVReport(startDate, endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition",
                "attachment; filename=rtk-report.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成CSV报表
     */
    private String generateCSVReport(String startDate, String endDate) {
        StringBuilder csv = new StringBuilder();

        // CSV头部
        csv.append("连接ID,连接类型,远程地址,远程端口,连接时间,断开时间,持续时长(秒),接收字节,发送字节,状态\n");

        if (historyMapper == null) {
            csv.append("数据库未配置，无法生成报表\n");
            return csv.toString();
        }

        try {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");

            // 查询指定时间范围内的连接历史
            List<ConnectionHistory> histories = historyMapper.selectRecentConnections(start);

            for (ConnectionHistory history : histories) {
                // 过滤时��范围
                if (history.getConnectTime().isBefore(start) || history.getConnectTime().isAfter(end)) {
                    continue;
                }

                csv.append(history.getConnectionId()).append(",")
                   .append(history.getConnectionType()).append(",")
                   .append(history.getRemoteAddress()).append(",")
                   .append(history.getRemotePort()).append(",")
                   .append(history.getConnectTime()).append(",")
                   .append(history.getDisconnectTime() != null ? history.getDisconnectTime() : "").append(",")
                   .append(history.getDurationSeconds() != null ? history.getDurationSeconds() : 0).append(",")
                   .append(history.getReceivedBytes() != null ? history.getReceivedBytes() : 0).append(",")
                   .append(history.getSentBytes() != null ? history.getSentBytes() : 0).append(",")
                   .append(history.getStatus()).append("\n");
            }

        } catch (Exception e) {
            log.error("生成CSV报表失败", e);
            csv.append("报表生成失败: ").append(e.getMessage()).append("\n");
        }

        return csv.toString();
    }
}
