CREATE DATABASE IF NOT EXISTS rtk_relay DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE rtk_relay;

-- 连接历史记录表
CREATE TABLE IF NOT EXISTS connection_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id VARCHAR(100) NOT NULL COMMENT '连接ID',
    connection_type VARCHAR(20) NOT NULL COMMENT '连接类型: BASE_STATION/MOBILE_STATION',
    remote_address VARCHAR(50) NOT NULL COMMENT '远程IP地址',
    remote_port INT NOT NULL COMMENT '远程端口',
    connect_time DATETIME NOT NULL COMMENT '连接时间',
    disconnect_time DATETIME COMMENT '断开时间',
    duration_seconds BIGINT COMMENT '连接持续时间(秒)',
    received_bytes BIGINT DEFAULT 0 COMMENT '接收字节数',
    sent_bytes BIGINT DEFAULT 0 COMMENT '发送字节数',
    received_messages BIGINT DEFAULT 0 COMMENT '接收消息数',
    sent_messages BIGINT DEFAULT 0 COMMENT '发送消息数',
    status VARCHAR(20) COMMENT '最终状态',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_connection_id (connection_id),
    INDEX idx_connect_time (connect_time),
    INDEX idx_type (connection_type),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='连接历史记录表';

-- 小时级统计表（用于快速查询）
CREATE TABLE IF NOT EXISTS hourly_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_hour DATETIME NOT NULL COMMENT '统计小时',
    base_station_count INT DEFAULT 0 COMMENT '基站连接数',
    mobile_station_count INT DEFAULT 0 COMMENT '移动站连接数',
    total_received_bytes BIGINT DEFAULT 0 COMMENT '总接收字节数',
    total_sent_bytes BIGINT DEFAULT 0 COMMENT '总发送字节数',
    total_received_messages BIGINT DEFAULT 0 COMMENT '总接收消息数',
    total_sent_messages BIGINT DEFAULT 0 COMMENT '总发送消息数',
    connection_errors INT DEFAULT 0 COMMENT '连接错误数',
    relay_errors INT DEFAULT 0 COMMENT '转发错误数',
    avg_latency_ms DECIMAL(10,2) COMMENT '平均延迟(毫秒)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_stat_hour (stat_hour)
) ENGINE=InnoDB COMMENT='小时级统计表';

-- 实时状态表（只保留当前状态）
CREATE TABLE IF NOT EXISTS current_connections (
    connection_id VARCHAR(100) PRIMARY KEY,
    connection_type VARCHAR(20) NOT NULL,
    remote_address VARCHAR(50) NOT NULL,
    remote_port INT NOT NULL,
    connect_time DATETIME NOT NULL,
    last_active_time DATETIME NOT NULL,
    received_bytes BIGINT DEFAULT 0,
    sent_bytes BIGINT DEFAULT 0,
    received_messages BIGINT DEFAULT 0,
    sent_messages BIGINT DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (connection_type),
    INDEX idx_status (status),
    INDEX idx_last_active (last_active_time)
) ENGINE=InnoDB COMMENT='当前连接状态表';

-- 基站差分数据表（核心优化表）
-- 实现1小时内更新策略，减少数据量
-- 基站发送RTCM差分修正数据，系统转发给移动站
CREATE TABLE IF NOT EXISTS base_station_rtcm_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_station_id VARCHAR(100) NOT NULL COMMENT '基站ID（连接ID）',
    remote_address VARCHAR(50) NOT NULL COMMENT '基站IP地址',
    hour_slot DATETIME NOT NULL COMMENT '小时时间槽（精确到小时）',
    first_data_time DATETIME NOT NULL COMMENT '本小时内第一次接收数据时间',
    last_data_time DATETIME NOT NULL COMMENT '本小时内最后一次接收数据时间',
    rtcm_data LONGBLOB COMMENT '最新的RTCM差分修正数据',
    data_count INT DEFAULT 1 COMMENT '本小时内接收数据次数',
    data_size BIGINT DEFAULT 0 COMMENT '数据大小（字节）',
    checksum VARCHAR(32) COMMENT '数据校验和（用于检测变化）',
    rtcm_message_types VARCHAR(200) COMMENT 'RTCM消息类型列表（如：1074,1084,1094）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    UNIQUE INDEX idx_station_hour (base_station_id, hour_slot),
    INDEX idx_base_station_id (base_station_id),
    INDEX idx_hour_slot (hour_slot),
    INDEX idx_last_data_time (last_data_time)
) ENGINE=InnoDB COMMENT='基站RTCM差分数据表（1小时聚合策略）';

-- 数据转发记录表（记录转发统计信息，不存储实际数据）
-- 移动站只接收数据，不发送数据
CREATE TABLE IF NOT EXISTS data_relay_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_station_id VARCHAR(100) NOT NULL COMMENT '数据源基站ID',
    mobile_station_id VARCHAR(100) NOT NULL COMMENT '目标移动站ID',
    relay_time DATETIME NOT NULL COMMENT '转发时间',
    data_size BIGINT DEFAULT 0 COMMENT '转发数据大小（字节）',
    relay_status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '转发状态：SUCCESS/FAILED',
    error_message TEXT COMMENT '错误信息（如果转发失败）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_base_station_id (base_station_id),
    INDEX idx_mobile_station_id (mobile_station_id),
    INDEX idx_relay_time (relay_time),
    INDEX idx_relay_status (relay_status)
) ENGINE=InnoDB COMMENT='数据转发记录表';

-- 告警记录表
CREATE TABLE IF NOT EXISTS alert_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL COMMENT '告警类型',
    alert_level VARCHAR(20) NOT NULL COMMENT '告警级别: INFO/WARN/ERROR/CRITICAL',
    alert_message TEXT NOT NULL COMMENT '告警消息',
    details JSON COMMENT '详细信息(JSON格式)',
    connection_id VARCHAR(100) COMMENT '相关连接ID',
    occurred_at DATETIME NOT NULL COMMENT '发生时间',
    resolved_at DATETIME COMMENT '解决时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alert_type (alert_type),
    INDEX idx_occurred_at (occurred_at),
    INDEX idx_level (alert_level),
    INDEX idx_connection_id (connection_id)
) ENGINE=InnoDB COMMENT='告警记录表';

-- 数据质量统计表
CREATE TABLE IF NOT EXISTS data_quality_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '统计日期',
    base_station_id VARCHAR(100) NOT NULL COMMENT '基站ID',
    expected_messages INT DEFAULT 86400 COMMENT '预期消息数（1秒1条=86400条/天）',
    actual_messages INT DEFAULT 0 COMMENT '实际消息数',
    missing_messages INT DEFAULT 0 COMMENT '丢失消息数',
    duplicate_messages INT DEFAULT 0 COMMENT '重复消息数',
    data_integrity_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '数据完整性率（%）',
    avg_data_size DECIMAL(10,2) DEFAULT 0.00 COMMENT '平均数据大小（字节）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_date_station (stat_date, base_station_id),
    INDEX idx_stat_date (stat_date),
    INDEX idx_base_station_id (base_station_id)
) ENGINE=InnoDB COMMENT='数据质量统计表';

-- 系统性能监控表
CREATE TABLE IF NOT EXISTS system_performance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    monitor_time DATETIME NOT NULL COMMENT '监控时间',
    cpu_usage DECIMAL(5,2) COMMENT 'CPU使用率（%）',
    memory_usage DECIMAL(5,2) COMMENT '内存使用率（%）',
    disk_usage DECIMAL(5,2) COMMENT '磁盘使用率（%）',
    network_in_bytes BIGINT DEFAULT 0 COMMENT '网络入流量（字节）',
    network_out_bytes BIGINT DEFAULT 0 COMMENT '网络出流量（字节）',
    active_connections INT DEFAULT 0 COMMENT '活跃连接数',
    thread_pool_active INT DEFAULT 0 COMMENT '活跃线程数',
    thread_pool_queue_size INT DEFAULT 0 COMMENT '线程池队列大小',
    gc_count INT DEFAULT 0 COMMENT 'GC次数',
    gc_time_ms BIGINT DEFAULT 0 COMMENT 'GC耗时（毫秒）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_monitor_time (monitor_time)
) ENGINE=InnoDB COMMENT='系统性能监控表';

-- 创建视图：基站实时状态
CREATE OR REPLACE VIEW v_base_station_status AS
SELECT 
    brd.base_station_id,
    brd.remote_address,
    cc.connect_time,
    cc.last_active_time,
    brd.last_data_time,
    brd.data_count as hourly_data_count,
    cc.received_bytes,
    cc.received_messages,
    cc.status,
    TIMESTAMPDIFF(SECOND, cc.last_active_time, NOW()) as inactive_seconds,
    brd.rtcm_message_types
FROM current_connections cc
LEFT JOIN base_station_rtcm_data brd ON cc.connection_id = brd.base_station_id 
    AND brd.hour_slot = DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00')
WHERE cc.connection_type = 'BASE_STATION'
ORDER BY cc.last_active_time DESC;

-- 创建视图：数据转发统计
CREATE OR REPLACE VIEW v_data_relay_stats AS
SELECT 
    DATE(stat_hour) as stat_date,
    SUM(base_station_count) as total_base_stations,
    SUM(mobile_station_count) as total_mobile_stations,
    SUM(total_received_bytes) as daily_received_bytes,
    SUM(total_sent_bytes) as daily_sent_bytes,
    SUM(total_received_messages) as daily_received_messages,
    SUM(total_sent_messages) as daily_sent_messages,
    SUM(connection_errors) as daily_connection_errors,
    SUM(relay_errors) as daily_relay_errors,
    ROUND(SUM(total_sent_messages) / SUM(total_received_messages) * 100, 2) as relay_efficiency_percent
FROM hourly_statistics 
WHERE total_received_messages > 0
GROUP BY DATE(stat_hour)
ORDER BY stat_date DESC;

-- 插入初始化数据（如果需要）
-- INSERT INTO system_performance (monitor_time, cpu_usage, memory_usage, disk_usage, active_connections) 
-- VALUES (NOW(), 0.0, 0.0, 0.0, 0);

-- 创建索引优化查询性能
CREATE INDEX idx_base_station_rtcm_data_composite ON base_station_rtcm_data(base_station_id, hour_slot, last_data_time);
CREATE INDEX idx_data_relay_logs_composite ON data_relay_logs(base_station_id, relay_time, relay_status);
CREATE INDEX idx_hourly_statistics_composite ON hourly_statistics(stat_hour, base_station_count, mobile_station_count);
