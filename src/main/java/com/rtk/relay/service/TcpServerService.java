package com.rtk.relay.service;

import com.rtk.relay.config.RtkProperties;
import com.rtk.relay.exception.RtkRelayException;
import com.rtk.relay.netty.BaseStationHandler;
import com.rtk.relay.netty.MobileStationHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;

/**
 * TCP服务器服务
 * 管理Server1（基站接入）和Server2（移动站接入）两个TCP服务器
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Service
@Slf4j
public class TcpServerService {
    
    /**
     * RTK配置
     */
    private final RtkProperties rtkProperties;
    
    /**
     * 连接管理器
     */
    private final ConnectionManager connectionManager;

    /**
     * 数据转发服务
     */
    private final DataRelayService dataRelayService;

    /**
     * Server1的Boss事件循环组
     */
    private EventLoopGroup server1BossGroup;
    
    /**
     * Server1的Worker事件循环组
     */
    private EventLoopGroup server1WorkerGroup;
    
    /**
     * Server2的Boss事件循环组
     */
    private EventLoopGroup server2BossGroup;
    
    /**
     * Server2的Worker事件循环组
     */
    private EventLoopGroup server2WorkerGroup;
    
    /**
     * Server1通道
     */
    private Channel server1Channel;
    
    /**
     * Server2通道
     */
    private Channel server2Channel;
    
    /**
     * 构造函数
     * 
     * @param rtkProperties RTK配置
     * @param connectionManager 连接管理器
     * @param dataRelayService 数据转发服务
     */
    public TcpServerService(RtkProperties rtkProperties, 
                           ConnectionManager connectionManager,
                           DataRelayService dataRelayService) {
        this.rtkProperties = rtkProperties;
        this.connectionManager = connectionManager;
        this.dataRelayService = dataRelayService;
    }
    
    /**
     * 启动TCP服务器
     * 同时启动Server1和Server2
     */
    @PostConstruct
    public void startServers() {
        log.info("正在启动RTK TCP服务器...");
        
        try {
            // 并行启动两个服务器
            CompletableFuture<Void> server1Future = CompletableFuture.runAsync(this::startServer1);
            CompletableFuture<Void> server2Future = CompletableFuture.runAsync(this::startServer2);
            
            // 等待两个服务器都启动完成
            CompletableFuture.allOf(server1Future, server2Future).get();
            
            log.info("RTK TCP服务器启动成功 - Server1端口: {}, Server2端口: {}",
                    rtkProperties.getServer1().getPort(),
                    rtkProperties.getServer2().getPort());
            
        } catch (Exception e) {
            log.error("RTK TCP服务器启动失败", e);
            throw new RtkRelayException("TCP_SERVER_START_FAILED", "TCP服务器启动失败", e);
        }
    }
    
    /**
     * 启动Server1（基站接入服务器）
     */
    private void startServer1() {
        server1BossGroup = new NioEventLoopGroup(1);
        server1WorkerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(server1BossGroup, server1WorkerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 移除空闲状态检测 - 保持连接常开
                            // pipeline.addLast(new IdleStateHandler(...)); // 已移除

                            // 移除空闲状态处理器 - 不再自动断开连接
                            // pipeline.addLast(new ChannelInboundHandlerAdapter() {...}); // 已移除

                            // 添加基站数据处理器
                            pipeline.addLast(new BaseStationHandler(connectionManager, dataRelayService));
                        }
                    });
            
            // 绑定端口并启动服务器
            ChannelFuture future = bootstrap.bind(rtkProperties.getServer1().getPort()).sync();
            server1Channel = future.channel();
            
            log.info("Server1（基站接入）启动成功 - 端口: {}", rtkProperties.getServer1().getPort());
            
        } catch (Exception e) {
            log.error("Server1启动失败", e);
            throw new RtkRelayException("SERVER1_START_FAILED", "Server1启动失败", e);
        }
    }
    
    /**
     * 启动Server2（移动站接入服务器）
     */
    private void startServer2() {
        server2BossGroup = new NioEventLoopGroup(1);
        server2WorkerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(server2BossGroup, server2WorkerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 移除空闲状态检测 - 保持连接常开
                            // pipeline.addLast(new IdleStateHandler(...)); // 已移除

                            // 移除空闲状态处理器 - 不再自动断开连接
                            // pipeline.addLast(new ChannelInboundHandlerAdapter() {...}); // 已移除

                            // 添加移动站数据处理器
                            pipeline.addLast(new MobileStationHandler(connectionManager));
                        }
                    });
            
            // 绑定端口并启动服务器
            ChannelFuture future = bootstrap.bind(rtkProperties.getServer2().getPort()).sync();
            server2Channel = future.channel();
            
            log.info("Server2（移动站接入）启动成功 - 端口: {}", rtkProperties.getServer2().getPort());
            
        } catch (Exception e) {
            log.error("Server2启动失败", e);
            throw new RtkRelayException("SERVER2_START_FAILED", "Server2启动失败", e);
        }
    }
    
    /**
     * 关闭TCP服务器
     */
    @PreDestroy
    public void shutdownServers() {
        log.info("正在关闭RTK TCP服务器...");
        
        try {
            // 关闭服务器通道
            if (server1Channel != null) {
                server1Channel.close().sync();
            }
            if (server2Channel != null) {
                server2Channel.close().sync();
            }
            
            // 关闭事件循环组
            if (server1BossGroup != null) {
                server1BossGroup.shutdownGracefully();
            }
            if (server1WorkerGroup != null) {
                server1WorkerGroup.shutdownGracefully();
            }
            if (server2BossGroup != null) {
                server2BossGroup.shutdownGracefully();
            }
            if (server2WorkerGroup != null) {
                server2WorkerGroup.shutdownGracefully();
            }
            
            log.info("RTK TCP服务器已关闭");
            
        } catch (Exception e) {
            log.error("关闭TCP服务器时发生错误", e);
        }
    }
    
    /**
     * 检查服务器状态
     * 
     * @return 服务器是否正常运行
     */
    public boolean isServerRunning() {
        return server1Channel != null && server1Channel.isActive() && 
               server2Channel != null && server2Channel.isActive();
    }
}
