CREATE DATABASE rtk_relay DEFAULT CHARACTER SET utf8mb4;

USE rtk_relay;

-- 连接历史记录表
CREATE TABLE connection_history (
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
                                    INDEX idx_connection_id (connection_id),
                                    INDEX idx_connect_time (connect_time),
                                    INDEX idx_type (connection_type)
) ENGINE=InnoDB COMMENT='连接历史记录表';

-- 小时级统计表（用于快速查询）
CREATE TABLE hourly_statistics (
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
                                   UNIQUE INDEX idx_stat_hour (stat_hour)
) ENGINE=InnoDB COMMENT='小时级统计表';

-- 实时状态表（只保留当前状态）
CREATE TABLE current_connections (
                                     connection_id VARCHAR(100) PRIMARY KEY,
                                     connection_type VARCHAR(20) NOT NULL,
                                     remote_address VARCHAR(50) NOT NULL,
                                     remote_port INT NOT NULL,
                                     connect_time DATETIME NOT NULL,
                                     last_active_time DATETIME NOT NULL,
                                     received_bytes BIGINT DEFAULT 0,
                                     sent_bytes BIGINT DEFAULT 0,
                                     status VARCHAR(20) NOT NULL,
                                     updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     INDEX idx_type (connection_type),
                                     INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='当前连接状态表';

-- 告警记录表
CREATE TABLE alert_history (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               alert_type VARCHAR(50) NOT NULL COMMENT '告警类型',
                               alert_level VARCHAR(20) NOT NULL COMMENT '告警级别: INFO/WARN/ERROR/CRITICAL',
                               alert_message TEXT NOT NULL COMMENT '告警消息',
                               details JSON COMMENT '详细信息(JSON格式)',
                               occurred_at DATETIME NOT NULL COMMENT '发生时间',
                               resolved_at DATETIME COMMENT '解决时间',
                               created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                               INDEX idx_alert_type (alert_type),
                               INDEX idx_occurred_at (occurred_at),
                               INDEX idx_level (alert_level)
) ENGINE=InnoDB COMMENT='告警记录表';