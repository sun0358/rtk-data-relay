package com.rtk.relay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RTK服务配置属性类
 * 从application.yml中读取rtk配置
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "rtk")
public class RtkProperties {
    
    /**
     * Server1配置（接收基站数据）
     */
    private Server1Config server1 = new Server1Config();
    
    /**
     * Server2配置（转发数据给移动站）
     */
    private Server2Config server2 = new Server2Config();
    
    /**
     * 数据转发配置
     */
    private RelayConfig relay = new RelayConfig();
    
    /**
     * Server1配置类
     */
    @Data
    public static class Server1Config {
        /**
         * 监听端口
         */
        private int port = 9001;
        
        /**
         * 连接超时时间（秒）
         */
        private int timeout = 30;
        
        /**
         * 心跳检测间隔（秒）
         */
        private int heartbeatInterval = 10;
    }
    
    /**
     * Server2配置类
     */
    @Data
    public static class Server2Config {
        /**
         * 监听端口
         */
        private int port = 9002;
        
        /**
         * 最大移动站连接数
         */
        private int maxConnections = 10;
        
        /**
         * 连接超时时间（秒）
         */
        private int timeout = 30;
        
        /**
         * 心跳检测间隔（秒）
         */
        private int heartbeatInterval = 10;
    }
    
    /**
     * 数据转发配置类
     */
    @Data
    public static class RelayConfig {
        /**
         * 数据缓冲区大小（字节）
         */
        private int bufferSize = 8192;
        
        /**
         * 统计数据保留时间（小时）
         */
        private int statisticsRetentionHours = 24;
        
        /**
         * 自动重连间隔（秒）
         */
        private int reconnectInterval = 5;
        
        /**
         * 最大重连次数
         */
        private int maxReconnectAttempts = 10;
    }
}
