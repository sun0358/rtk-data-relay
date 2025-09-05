package com.rtk.relay.service;

import com.rtk.relay.entity.*;
import com.rtk.relay.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@ConditionalOnBean(DataSource.class)  // 只在数据源存在时启用
public class DataPersistenceService {

    @Autowired(required = false)
    private HourlyStatisticsMapper statisticsMapper;

    @Autowired(required = false)
    private ConnectionHistoryMapper historyMapper;
    
    @Autowired(required = false)
    private BaseStationRtcmDataMapper baseStationRtcmDataMapper;
    
    @Autowired(required = false)
    private DataRelayLogMapper dataRelayLogMapper;
    
    @Autowired(required = false)
    private DataQualityStatsMapper dataQualityStatsMapper;
    
    /**
     * 缓存每日消息计数，用于数据质量统计
     * Key: baseStationId:date, Value: messageCount
     */
    private final ConcurrentHashMap<String, Integer> dailyMessageCount = new ConcurrentHashMap<>();

    /**
     * 记录连接建立
     */
    @Transactional
    public void recordConnectionEstablished(String connectionId, String type,
                                            String address, int port) {
        if (historyMapper == null) {
            log.warn("数据持久化服务未启用，跳过连接记录");
            return;
        }

        ConnectionHistory history = new ConnectionHistory();
        history.setConnectionId(connectionId);
        history.setConnectionType(type);
        history.setRemoteAddress(address);
        history.setRemotePort(port);
        history.setConnectTime(LocalDateTime.now());
        history.setStatus("CONNECTED");

        historyMapper.insert(history);
        log.debug("记录连接建立: {}", connectionId);
    }

    /**
     * 更新连接断开
     */
    @Transactional
    public void recordConnectionClosed(String connectionId, long receivedBytes,
                                       long sentBytes, String status) {
        if (historyMapper == null) {
            log.warn("数据持久化服务未启用，跳过连接断开记录");
            return;
        }

        ConnectionHistory history = historyMapper.selectByConnectionId(connectionId);
        if (history != null) {
            LocalDateTime now = LocalDateTime.now();
            history.setDisconnectTime(now);
            history.setDurationSeconds(
                    ChronoUnit.SECONDS.between(history.getConnectTime(), now)
            );
            history.setReceivedBytes(receivedBytes);
            history.setSentBytes(sentBytes);
            history.setStatus(status);

            historyMapper.updateById(history);
            log.debug("记录连接断开: {}", connectionId);
        }
    }

    /**
     * 存储基站RTCM差分数据（核心优化功能）
     * 实现1小时内更新策略，大幅减少数据量
     * 基站发送RTCM差分修正数据，系统转发给移动站
     * 
     * @param baseStationId 基站ID
     * @param remoteAddress 基站IP地址
     * @param rtcmData RTCM差分修正数据
     */
    @Transactional
    public void storeBaseStationRtcmData(String baseStationId, String remoteAddress, byte[] rtcmData) {
        if (baseStationRtcmDataMapper == null) {
            log.debug("数据持久化服务未启用，跳过基站RTCM数据存储");
            return;
        }
        
        try {
            LocalDateTime currentHourSlot = BaseStationRtcmData.getCurrentHourSlot();
            
            // 生成数据校验和
            String checksum = calculateChecksum(rtcmData);
            
            // 查找当前小时的记录
            BaseStationRtcmData existingRecord = baseStationRtcmDataMapper
                    .selectByStationAndHour(baseStationId, currentHourSlot);
            
            if (existingRecord != null) {
                // 存在记录，更新数据
                existingRecord.updateRtcmData(rtcmData, checksum);
                
                baseStationRtcmDataMapper.updateRtcmData(
                    existingRecord.getId(),
                    rtcmData,
                    checksum,
                    existingRecord.getLastDataTime(),
                    existingRecord.getDataCount(),
                    existingRecord.getDataSize(),
                    existingRecord.getRtcmMessageTypes()
                );
                
                log.debug("更新基站RTCM数据 - 基站: {}, 更新次数: {}", 
                    baseStationId, existingRecord.getDataCount());
            } else {
                // 不存在记录，创建新记录
                BaseStationRtcmData newRecord = new BaseStationRtcmData();
                newRecord.initializeNewRecord(baseStationId, remoteAddress, rtcmData, checksum);
                
                baseStationRtcmDataMapper.insert(newRecord);
                
                log.info("创建新的基站RTCM记录 - 基站: {}, 时间槽: {}", 
                    baseStationId, currentHourSlot);
            }
            
            // 更新每日消息计数（用于数据质量统计）
            updateDailyMessageCount(baseStationId);
            
        } catch (Exception e) {
            log.error("存储基站RTCM数据失败 - 基站: {}", baseStationId, e);
        }
    }
    
    /**
     * 记录数据转发日志（批量处理，提高性能）
     * 
     * @param relayLogs 转发日志列表
     */
    @Transactional
    public void storeDataRelayLogs(List<DataRelayLog> relayLogs) {
        if (dataRelayLogMapper == null || relayLogs.isEmpty()) {
            log.debug("数据持久化服务未启用或无转发日志，跳过存储");
            return;
        }
        
        try {
            // 批量插入提高性能
            int inserted = dataRelayLogMapper.batchInsert(relayLogs);
            
            log.debug("批量存储转发日志 - 数量: {}", inserted);
                
        } catch (Exception e) {
            log.error("存储转发日志失败 - 数量: {}", relayLogs.size(), e);
        }
    }
    
    /**
     * 生成数据校验和
     */
    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("生成MD5校验和失败，使用数据长度作为校验和", e);
            return String.valueOf(data.length);
        }
    }
    
    /**
     * 更新每日消息计数
     */
    private void updateDailyMessageCount(String baseStationId) {
        String key = baseStationId + ":" + LocalDate.now().toString();
        dailyMessageCount.merge(key, 1, Integer::sum);
    }
    
    /**
     * 每小时统计一次
     */
    @Scheduled(cron = "0 0 * * * ?")  // 每小时执行
    public void generateHourlyStatistics() {
        if (statisticsMapper == null || historyMapper == null) {
            log.debug("数据持久化服务未启用，跳过小时统计");
            return;
        }

        try {
            LocalDateTime currentHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
            LocalDateTime lastHour = currentHour.minusHours(1);

            // 统计上一小时的数据
            HourlyStatistics stats = statisticsMapper.calculateHourlyStats(lastHour, currentHour);
            stats.setStatHour(lastHour);

            statisticsMapper.insert(stats);
            log.info("生成小时统计: {}", lastHour);

            // 清理旧数据
            cleanOldRecords();

        } catch (Exception e) {
            log.error("生成小时统计失败", e);
        }
    }
    
    /**
     * 每日生成数据质量统计
     */
    @Scheduled(cron = "0 30 0 * * ?")  // 每日午夜0:30执行
    public void generateDailyQualityStats() {
        if (dataQualityStatsMapper == null) {
            log.debug("数据持久化服务未启用，跳过数据质量统计");
            return;
        }
        
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            String yesterdayKey = yesterday.toString();
            
            // 遍历所有基站的每日消息计数
            for (Map.Entry<String, Integer> entry : dailyMessageCount.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith(":" + yesterdayKey)) {
                    String baseStationId = key.substring(0, key.lastIndexOf(":"));
                    Integer actualMessages = entry.getValue();
                    
                    // 计算平均数据大小（估算值）
                    double avgDataSize = 150.0; // RTK数据平均大小
                    
                    // 更新数据质量统计
                    dataQualityStatsMapper.upsertQualityStats(
                        yesterday, baseStationId, actualMessages, 0, avgDataSize
                    );
                    
                    // 移除已处理的记录
                    dailyMessageCount.remove(key);
                    
                    log.debug("生成数据质量统计 - 基站: {}, 日期: {}, 实际消息: {}", 
                        baseStationId, yesterday, actualMessages);
                }
            }
            
            log.info("数据质量统计生成完成 - 日期: {}", yesterday);
            
        } catch (Exception e) {
            log.error("生成数据质量统计失败", e);
        }
    }

    /**
     * 清理历史数据
     */
    private void cleanOldRecords() {
        try {
            // 清理连接历史记录（保留30天）
            if (historyMapper != null) {
                LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
                int deleted = historyMapper.deleteOldRecords(cutoffTime);
                if (deleted > 0) {
                    log.info("清理连接历史记录: {} 条", deleted);
                }
            }

            // 清理小时统计数据（保留90天）
            if (statisticsMapper != null) {
                LocalDateTime cutoffTime = LocalDateTime.now().minusDays(90);
                int deleted = statisticsMapper.deleteOldStatistics(cutoffTime);
                if (deleted > 0) {
                    log.info("清理小时统计数据: {} 条", deleted);
                }
            }
            
            // 清理基站RTCM数据（保留60天）
            if (baseStationRtcmDataMapper != null) {
                LocalDateTime cutoffTime = LocalDateTime.now().minusDays(60);
                int deleted = baseStationRtcmDataMapper.deleteOldRtcmData(cutoffTime);
                if (deleted > 0) {
                    log.info("清理基站RTCM数据: {} 条", deleted);
                }
            }
            
            // 清理转发日志（保留30天）
            if (dataRelayLogMapper != null) {
                LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
                int deleted = dataRelayLogMapper.deleteOldLogs(cutoffTime);
                if (deleted > 0) {
                    log.info("清理转发日志: {} 条", deleted);
                }
            }
            
            // 清理数据质量统计（保留180天）
            if (dataQualityStatsMapper != null) {
                LocalDate cutoffDate = LocalDate.now().minusDays(180);
                int deleted = dataQualityStatsMapper.deleteOldStats(cutoffDate);
                if (deleted > 0) {
                    log.info("清理数据质量统计: {} 条", deleted);
                }
            }
            
        } catch (Exception e) {
            log.error("清理历史数据失败", e);
        }
    }
    
    /**
     * 获取数据存储效率统计
     */
    public Map<String, Object> getStorageEfficiencyStats(int days) {
        if (baseStationRtcmDataMapper == null) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", "数据持久化服务未启用");
            return errorMap;
        }
        
        try {
            return baseStationRtcmDataMapper.selectStorageEfficiencyStats(days);
        } catch (Exception e) {
            log.error("获取存储效率统计失败", e);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }
    
    /**
     * 获取转发性能统计
     */
    public Map<String, Object> getRelayPerformanceStats(int hours) {
        if (dataRelayLogMapper == null) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", "数据持久化服务未启用");
            return errorMap;
        }
        
        try {
            return dataRelayLogMapper.selectSystemRelayPerformance(hours);
        } catch (Exception e) {
            log.error("获取转发性能统计失败", e);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }
    
    /**
     * 获取数据质量汇总统计
     */
    public Map<String, Object> getDataQualitySummary(int days) {
        if (dataQualityStatsMapper == null) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", "数据持久化服务未启用");
            return errorMap;
        }
        
        try {
            return dataQualityStatsMapper.selectQualitySummary(days);
        } catch (Exception e) {
            log.error("获取数据质量统计失败", e);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }
    
    /**
     * 检查数据库连接状态
     */
    public boolean isDatabaseEnabled() {
        return statisticsMapper != null && historyMapper != null 
            && baseStationRtcmDataMapper != null && dataRelayLogMapper != null;
    }
    
    /**
     * 获取基站实时状态
     */
    public List<Map<String, Object>> getCurrentBaseStationStatus() {
        if (baseStationRtcmDataMapper == null) {
            return new ArrayList<>();
        }
        
        try {
            return baseStationRtcmDataMapper.selectCurrentBaseStationStatus();
        } catch (Exception e) {
            log.error("获取基站实时状态失败", e);
            return new ArrayList<>();
        }
    }
}