package com.rtk.relay.service;

import com.rtk.relay.entity.ConnectionHistory;
import com.rtk.relay.entity.HourlyStatistics;
import com.rtk.relay.mapper.ConnectionHistoryMapper;
import com.rtk.relay.mapper.HourlyStatisticsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
@ConditionalOnBean(DataSource.class)  // 只在数据源存在时启用
public class DataPersistenceService {

    @Autowired(required = false)  // 设置为非必需，避免启动失败
    private HourlyStatisticsMapper statisticsMapper;

    @Autowired(required = false)  // 设置为非必需，避免启动失败
    private ConnectionHistoryMapper historyMapper;

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

            // 清理30天前的详细记录
            cleanOldRecords();

        } catch (Exception e) {
            log.error("生成小时统计失败", e);
        }
    }

    /**
     * 清理历史数据
     */
    private void cleanOldRecords() {
        if (historyMapper == null || statisticsMapper == null) {
            return;
        }

        // 保留30天的详细记录
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
        int deleted = historyMapper.deleteOldRecords(cutoffTime);
        if (deleted > 0) {
            log.info("清理历史记录: {} 条", deleted);
        }

        // 保留90天的统计数据
        cutoffTime = LocalDateTime.now().minusDays(90);
        deleted = statisticsMapper.deleteOldStatistics(cutoffTime);
        if (deleted > 0) {
            log.info("清理统计数据: {} 条", deleted);
        }
    }
}