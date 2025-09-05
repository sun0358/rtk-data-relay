package com.rtk.relay.controller;

import com.rtk.relay.dto.*;
import com.rtk.relay.entity.ConnectionInfo;
import com.rtk.relay.entity.RelayStatistics;
import com.rtk.relay.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RTK数据转发监控API控制器
 * 提供系统监控、统计信息查询等RESTful API
 * 符合RESTful设计规范，支持其他Web服务调用
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class MonitorController {

    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private TcpServerService tcpServerService;
    
    @Autowired
    private DataRelayService dataRelayService;
    
    @Autowired
    private DataPersistenceService dataPersistenceService;

    // ==================== 系统状态相关接口 ====================
    
    /**
     * 系统健康检查
     * GET /api/v1/health
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> healthCheck(HttpServletRequest request) {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now());
            health.put("service", "RTK Data Relay Service");
            health.put("version", "1.0.0");
            health.put("serverRunning", tcpServerService.isServerRunning());
            
            return ApiResponse.success(health, "服务运行正常").path(request.getRequestURI());
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ApiResponse.<Map<String, Object>>error("健康检查失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }
    
    /**
     * 获取系统状态概览
     * GET /api/v1/system/status
     */
    @GetMapping("/system/status")
    public ApiResponse<SystemStatusDTO> getSystemStatus(HttpServletRequest request) {
        try {
            SystemStatusDTO status = buildSystemStatus();
            return ApiResponse.success(status, "系统状态获取成功").path(request.getRequestURI());
        } catch (Exception e) {
            log.error("获取系统状态失败", e);
            return ApiResponse.<SystemStatusDTO>error("获取系统状态失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }
    
    /**
     * 获取系统性能监控信息
     * GET /api/v1/system/performance
     */
    @GetMapping("/system/performance")
    public ApiResponse<Map<String, Object>> getPerformanceMetrics(
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days) {
        try {
            Map<String, Object> perf = buildPerformanceMetrics(days);
            return ApiResponse.success(perf, "性能指标获取成功").path(request.getRequestURI());
        } catch (Exception e) {
            log.error("获取性能指标失败", e);
            return ApiResponse.<Map<String, Object>>error("获取性能指标失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }

    // ==================== 基站相关接口 ====================
    
    /**
     * 获取基站列表和状态
     * GET /api/v1/base-stations
     */
    @GetMapping("/base-stations")
    public ApiResponse<List<BaseStationDTO>> getBaseStations(
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "true") boolean includeQuality) {
        try {
            List<BaseStationDTO> baseStations = buildBaseStationList(days, includeQuality);
            return ApiResponse.success(baseStations, "基站信息获取成功").path(request.getRequestURI());
        } catch (Exception e) {
            log.error("获取基站信息失败", e);
            return ApiResponse.<List<BaseStationDTO>>error("获取基站信息失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }
    
    /**
     * 获取指定基站详细信息
     * GET /api/v1/base-stations/{baseStationId}
     */
    @GetMapping("/base-stations/{baseStationId}")
    public ApiResponse<BaseStationDTO> getBaseStation(
            @PathVariable String baseStationId,
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days) {
        try {
            BaseStationDTO baseStation = buildBaseStationDetail(baseStationId, days);
            if (baseStation != null) {
                return ApiResponse.success(baseStation, "基站信息获取成功").path(request.getRequestURI());
            } else {
                return ApiResponse.<BaseStationDTO>custom(404, "基站不存在", null).path(request.getRequestURI());
            }
        } catch (Exception e) {
            log.error("获取基站详细信息失败: {}", baseStationId, e);
            return ApiResponse.<BaseStationDTO>error("获取基站信息失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }

    // ==================== 移动站相关接口 ====================
    
    /**
     * 获取移动站连接信息
     * GET /api/v1/mobile-stations
     */
    @GetMapping("/mobile-stations")
    public ApiResponse<List<Map<String, Object>>> getMobileStations(HttpServletRequest request) {
        try {
            List<Map<String, Object>> mobileStations = buildMobileStationList();
            return ApiResponse.success(mobileStations, "移动站信息获取成功").path(request.getRequestURI());
        } catch (Exception e) {
            log.error("获取移动站信息失败", e);
            return ApiResponse.<List<Map<String, Object>>>error("获取移动站信息失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }

    // ==================== 转发性能相关接口 ====================
    
    /**
     * 获取转发性能统计
     * GET /api/v1/relay/performance
     */
    @GetMapping("/relay/performance")
    public ApiResponse<RelayPerformanceDTO> getRelayPerformance(
            HttpServletRequest request,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "true") boolean includeDetails) {
        try {
            RelayPerformanceDTO performance = buildRelayPerformance(hours, includeDetails);
            return ApiResponse.success(performance, "转发性能统计获取成功").path(request.getRequestURI());
        } catch (Exception e) {
            log.error("获取转发性能统计失败", e);
            return ApiResponse.<RelayPerformanceDTO>error("获取转发性能统计失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }

    // ==================== 数据库相关接口 ====================
    
    /**
     * 获取数据库状态信息
     * GET /api/v1/database/status
     */
    @GetMapping("/database/status")
    public ApiResponse<Map<String, Object>> getDatabaseStatus(
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days) {
        try {
            Map<String, Object> dbStats = buildDatabaseStatus(days);
            return ApiResponse.success(dbStats, "数据库状态获取成功").path(request.getRequestURI());
        } catch (Exception e) {
            log.error("获取数据库状态失败", e);
            return ApiResponse.<Map<String, Object>>error("获取数据库状态失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }

    // ==================== 兼容性接口（向后兼容） ====================
    
    /**
     * 获取原始统计数据（兼容旧接口）
     * GET /api/v1/statistics
     */
    @GetMapping("/statistics")
    @Deprecated
    public ApiResponse<RelayStatistics> getStatistics(HttpServletRequest request) {
        try {
            RelayStatistics stats = connectionManager.getStatistics();
            return ApiResponse.success(stats, "统计数据获取成功").path(request.getRequestURI());
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return ApiResponse.<RelayStatistics>error("获取统计数据失败: " + e.getMessage())
                    .path(request.getRequestURI());
        }
    }
    
    /**
     * 简单的ping接口（兼容旧接口）
     * GET /api/v1/ping
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping(HttpServletRequest request) {
        String message = "RTK Data Relay Service is running! Time: " + System.currentTimeMillis();
        return ApiResponse.success(message, "服务正常运行").path(request.getRequestURI());
    }

    // ==================== 私有辅助方法 ====================
    
    /**
     * 构建系统状态DTO
     */
    private SystemStatusDTO buildSystemStatus() {
        SystemStatusDTO status = new SystemStatusDTO();
        RelayStatistics stats = connectionManager.getStatistics();
        
        status.setServiceRunning(tcpServerService.isServerRunning());
        status.setStartTime(stats.getStartTime());
        status.setLastUpdateTime(stats.getLastUpdateTime());
        status.setCurrentBaseStationConnections(stats.getCurrentBaseStationConnections().get());
        status.setCurrentMobileStationConnections(stats.getCurrentMobileStationConnections().get());
        status.setTotalBaseStationConnections(stats.getTotalBaseStationConnections().get());
        status.setTotalMobileStationConnections(stats.getTotalMobileStationConnections().get());
        status.setTotalReceivedBytes(stats.getTotalReceivedBytes().get());
        status.setTotalSentBytes(stats.getTotalSentBytes().get());
        status.setTotalReceivedMessages(stats.getTotalReceivedMessages().get());
        status.setTotalSentMessages(stats.getTotalSentMessages().get());
        status.setConnectionErrors(stats.getConnectionErrors().get());
        status.setRelayErrors(stats.getRelayErrors().get());
        
        // 性能指标
        Map<String, Object> performance = new HashMap<>();
        performance.put("threadPoolStatus", dataRelayService.getThreadPoolStatus());
        performance.put("dataBufferStatus", dataRelayService.getDataBufferStatus());
        performance.put("memoryUsage", getMemoryUsage());
        status.setPerformance(performance);
        
        // 数据库状态
        if (dataPersistenceService != null) {
            status.setDatabase(dataRelayService.getDatabaseStatus());
        }
        
        return status;
    }
    
    /**
     * 构建性能指标
     */
    private Map<String, Object> buildPerformanceMetrics(int days) {
        Map<String, Object> perf = new HashMap<>();
        RelayStatistics stats = connectionManager.getStatistics();
        
        // 基础性能指标
        perf.put("timestamp", LocalDateTime.now());
        perf.put("serviceRunning", tcpServerService.isServerRunning());
        
        // 连接性能
        Map<String, Object> connectionMetrics = new HashMap<>();
        connectionMetrics.put("currentBaseStations", stats.getCurrentBaseStationConnections().get());
        connectionMetrics.put("currentMobileStations", stats.getCurrentMobileStationConnections().get());
        connectionMetrics.put("maxMobileStations", connectionManager.getMaxMobileStationConnections());
        connectionMetrics.put("connectionUtilization", calculateConnectionUtilization());
        perf.put("connectionMetrics", connectionMetrics);
        
        // 数据传输性能
        perf.put("throughputMetrics", calculateThroughputMetrics(stats));
        
        // 错误率分析
        perf.put("errorMetrics", calculateErrorMetrics(stats));
        
        // 系统资源使用
        Map<String, Object> resourceMetrics = new HashMap<>();
        resourceMetrics.put("threadPoolStatus", dataRelayService.getThreadPoolStatus());
        resourceMetrics.put("dataBufferStatus", dataRelayService.getDataBufferStatus());
        resourceMetrics.put("memoryUsage", getMemoryUsage());
        perf.put("resourceMetrics", resourceMetrics);
        
        // 数据库相关性能（如果启用）
        if (dataPersistenceService != null && dataPersistenceService.isDatabaseEnabled()) {
            perf.put("storageEfficiency", dataPersistenceService.getStorageEfficiencyStats(days));
            perf.put("dataQuality", dataPersistenceService.getDataQualitySummary(days));
            perf.put("relayPerformance", dataPersistenceService.getRelayPerformanceStats(24));
        }
        
        return perf;
    }
    
    /**
     * 构建数据库状态信息
     */
    private Map<String, Object> buildDatabaseStatus(int days) {
        Map<String, Object> dbStats = new HashMap<>();
        
        if (dataPersistenceService != null) {
            dbStats = dataRelayService.getDatabaseStatus();
            if (dataPersistenceService.isDatabaseEnabled()) {
                dbStats.put("storageEfficiency", dataPersistenceService.getStorageEfficiencyStats(days));
                dbStats.put("baseStationStatus", dataPersistenceService.getCurrentBaseStationStatus());
            }
        } else {
            dbStats.put("enabled", false);
            dbStats.put("message", "数据持久化服务未启用");
        }
        
        dbStats.put("timestamp", LocalDateTime.now());
        return dbStats;
    }
    
    /**
     * 构建基站列表
     */
    private List<BaseStationDTO> buildBaseStationList(int days, boolean includeQuality) {
        List<BaseStationDTO> baseStations = new ArrayList<>();
        
        // 获取当前连接的基站信息
        connectionManager.getAllConnectionInfo().stream()
            .filter(conn -> ConnectionInfo.ConnectionType.BASE_STATION.equals(conn.getType()))
            .forEach(conn -> {
                BaseStationDTO dto = new BaseStationDTO();
                dto.setBaseStationId(conn.getConnectionId());
                dto.setRemoteAddress(conn.getRemoteAddress());
                dto.setConnectTime(conn.getConnectTime());
                dto.setLastActiveTime(conn.getLastActiveTime());
                dto.setReceivedBytes(conn.getReceivedBytes());
                dto.setReceivedMessages(conn.getReceivedMessages());
                dto.setStatus(conn.getStatus().toString());
                
                // 计算非活跃时间
                if (conn.getLastActiveTime() != null) {
                    dto.setInactiveSeconds(java.time.Duration.between(conn.getLastActiveTime(), LocalDateTime.now()).getSeconds());
                }
                
                baseStations.add(dto);
            });
        
        return baseStations;
    }
    
    /**
     * 构建基站详细信息
     */
    private BaseStationDTO buildBaseStationDetail(String baseStationId, int days) {
        // 查找指定基站
        return connectionManager.getAllConnectionInfo().stream()
            .filter(conn -> ConnectionInfo.ConnectionType.BASE_STATION.equals(conn.getType()) && 
                          baseStationId.equals(conn.getConnectionId()))
            .findFirst()
            .map(conn -> {
                BaseStationDTO dto = new BaseStationDTO();
                dto.setBaseStationId(conn.getConnectionId());
                dto.setRemoteAddress(conn.getRemoteAddress());
                dto.setConnectTime(conn.getConnectTime());
                dto.setLastActiveTime(conn.getLastActiveTime());
                dto.setReceivedBytes(conn.getReceivedBytes());
                dto.setReceivedMessages(conn.getReceivedMessages());
                dto.setStatus(conn.getStatus().toString());
                
                if (conn.getLastActiveTime() != null) {
                    dto.setInactiveSeconds(java.time.Duration.between(conn.getLastActiveTime(), LocalDateTime.now()).getSeconds());
                }
                
                return dto;
            })
            .orElse(null);
    }
    
    /**
     * 构建转发性能DTO
     */
    private RelayPerformanceDTO buildRelayPerformance(int hours, boolean includeDetails) {
        RelayPerformanceDTO performance = new RelayPerformanceDTO();
        performance.setTimeRangeHours(hours);
        performance.setStartTime(LocalDateTime.now().minusHours(hours));
        performance.setEndTime(LocalDateTime.now());
        
        if (dataPersistenceService != null && dataPersistenceService.isDatabaseEnabled()) {
            Map<String, Object> stats = dataPersistenceService.getRelayPerformanceStats(hours);
            
            // 填充基础统计数据
            performance.setTotalRelayAttempts(getLongValue(stats, "total_relay_attempts"));
            performance.setTotalSuccess(getLongValue(stats, "total_success"));
            performance.setTotalFailed(getLongValue(stats, "total_failed"));
            performance.setOverallSuccessRate(getDoubleValue(stats, "overall_success_rate"));
            performance.setTotalSuccessBytes(getLongValue(stats, "total_success_bytes"));
            performance.setActiveBaseStations(getIntegerValue(stats, "active_base_stations"));
            performance.setActiveMobileStations(getIntegerValue(stats, "active_mobile_stations"));
            performance.setAvgSuccessDataSize(getDoubleValue(stats, "avg_success_data_size"));
            
            // 构建效率指标
            RelayPerformanceDTO.EfficiencyMetricsDTO efficiency = new RelayPerformanceDTO.EfficiencyMetricsDTO();
            // 这里可以添加更多的效率计算逻辑
            performance.setEfficiency(efficiency);
        }
        
        return performance;
    }
    
    /**
     * 构建移动站列表
     */
    private List<Map<String, Object>> buildMobileStationList() {
        return connectionManager.getAllConnectionInfo().stream()
            .filter(conn -> ConnectionInfo.ConnectionType.MOBILE_STATION.equals(conn.getType()))
            .map(conn -> {
                Map<String, Object> mobile = new HashMap<>();
                mobile.put("mobileStationId", conn.getConnectionId());
                mobile.put("remoteAddress", conn.getRemoteAddress());
                mobile.put("connectTime", conn.getConnectTime());
                mobile.put("lastActiveTime", conn.getLastActiveTime());
                mobile.put("receivedBytes", conn.getReceivedBytes());
                mobile.put("receivedMessages", conn.getReceivedMessages());
                mobile.put("status", conn.getStatus().toString());
                
                if (conn.getLastActiveTime() != null) {
                    mobile.put("inactiveSeconds", 
                        java.time.Duration.between(conn.getLastActiveTime(), LocalDateTime.now()).getSeconds());
                }
                
                return mobile;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 计算连接利用率
     */
    private double calculateConnectionUtilization() {
        int current = connectionManager.getMobileStationCount();
        int max = connectionManager.getMaxMobileStationConnections();
        return max > 0 ? (double) current / max * 100 : 0.0;
    }
    
    /**
     * 计算吞吐量指标
     */
    private Map<String, Object> calculateThroughputMetrics(RelayStatistics stats) {
        Map<String, Object> throughput = new HashMap<>();
        
        // 计算运行时长（秒）
        long runtimeSeconds = java.time.Duration.between(stats.getStartTime(), LocalDateTime.now()).getSeconds();
        if (runtimeSeconds > 0) {
            throughput.put("avgMessagesPerSecond", (double) stats.getTotalReceivedMessages().get() / runtimeSeconds);
            throughput.put("avgBytesPerSecond", (double) stats.getTotalReceivedBytes().get() / runtimeSeconds);
        } else {
            throughput.put("avgMessagesPerSecond", 0.0);
            throughput.put("avgBytesPerSecond", 0.0);
        }
        
        throughput.put("totalMessages", stats.getTotalReceivedMessages().get());
        throughput.put("totalBytes", stats.getTotalReceivedBytes().get());
        throughput.put("runtimeSeconds", runtimeSeconds);
        
        return throughput;
    }
    
    /**
     * 计算错误率指标
     */
    private Map<String, Object> calculateErrorMetrics(RelayStatistics stats) {
        Map<String, Object> errors = new HashMap<>();
        
        long totalConnections = stats.getTotalBaseStationConnections().get() + stats.getTotalMobileStationConnections().get();
        long totalErrors = stats.getConnectionErrors().get() + stats.getRelayErrors().get();
        
        errors.put("connectionErrors", stats.getConnectionErrors().get());
        errors.put("relayErrors", stats.getRelayErrors().get());
        errors.put("totalErrors", totalErrors);
        
        if (totalConnections > 0) {
            errors.put("errorRate", (double) totalErrors / totalConnections * 100);
        } else {
            errors.put("errorRate", 0.0);
        }
        
        return errors;
    }
    
    /**
     * 获取内存使用情况
     */
    private Map<String, Object> getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memory = new HashMap<>();
        
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        memory.put("maxMemory", maxMemory);
        memory.put("totalMemory", totalMemory);
        memory.put("usedMemory", usedMemory);
        memory.put("freeMemory", freeMemory);
        memory.put("usagePercent", (double) usedMemory / maxMemory * 100);
        
        return memory;
    }
    
    // 辅助方法
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }
    
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? Double.parseDouble(value.toString()) : 0.0;
    }
    
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }
}