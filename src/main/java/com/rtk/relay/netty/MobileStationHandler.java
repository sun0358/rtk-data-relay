package com.rtk.relay.netty;

import com.rtk.relay.entity.ConnectionInfo;
import com.rtk.relay.service.ConnectionManager;
import com.rtk.relay.service.DataRelayService;
import com.rtk.relay.util.ConnectionIdGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 移动站数据处理器
 * 处理来自移动站的TCP连接，主要用于接收连接和发送转发数据
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Slf4j
public class MobileStationHandler extends ChannelInboundHandlerAdapter {
    
    /**
     * 连接信息属性键
     */
    private static final AttributeKey<ConnectionInfo> CONNECTION_INFO_KEY = AttributeKey.valueOf("connectionInfo");
    
    /**
     * 连接管理器
     */
    private final ConnectionManager connectionManager;
    
    /**
     * 数据转发服务
     */
    private final DataRelayService dataRelayService;
    
    /**
     * 构造函数
     * 
     * @param connectionManager 连接管理器
     * @param dataRelayService 数据转发服务
     */
    public MobileStationHandler(ConnectionManager connectionManager, DataRelayService dataRelayService) {
        this.connectionManager = connectionManager;
        this.dataRelayService = dataRelayService;
    }
    
    /**
     * 连接建立时的处理
     * 
     * @param ctx 通道上下文
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        String connectionId = ConnectionIdGenerator.generateMobileStationId(
            remoteAddress.getAddress().getHostAddress(), 
            remoteAddress.getPort()
        );
        
        // 检查连接数限制
        if (connectionManager.getMobileStationCount() >= connectionManager.getMaxMobileStationConnections()) {
            log.warn("移动站连接数已达上限，拒绝新连接 - 远程地址: {}:{}", 
                    remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort());
            ctx.close();
            return;
        }
        
        // 创建连接信息
        ConnectionInfo connectionInfo = new ConnectionInfo();
        connectionInfo.setConnectionId(connectionId);
        connectionInfo.setType(ConnectionInfo.ConnectionType.MOBILE_STATION);
        connectionInfo.setRemoteAddress(remoteAddress.getAddress().getHostAddress());
        connectionInfo.setRemotePort(remoteAddress.getPort());
        connectionInfo.setConnectTime(LocalDateTime.now());
        connectionInfo.setLastActiveTime(LocalDateTime.now());
        connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.CONNECTED);
        
        // 将连接信息绑定到通道
        ctx.channel().attr(CONNECTION_INFO_KEY).set(connectionInfo);
        
        // 注册移动站连接
        connectionManager.registerMobileStation(connectionId, ctx.channel(), connectionInfo);
        
        log.info("移动站连接建立成功 - 连接ID: {}, 远程地址: {}:{}", 
                connectionId, connectionInfo.getRemoteAddress(), connectionInfo.getRemotePort());
        
        // 为新连接的移动站发送缓存数据（异步执行，不阻塞连接建立）
        if (dataRelayService != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    // 稍微延迟，让连接完全建立
                    Thread.sleep(100);
                    dataRelayService.sendBufferedDataToNewMobileStation(ctx.channel(), connectionId);
                } catch (Exception e) {
                    log.warn("发送缓存数据失败: {}", e.getMessage());
                }
            });
        }
    }
    
    /**
     * 接收数据时的处理（移动站一般不发送数据，但需要处理心跳等）
     * 
     * @param ctx 通道上下文
     * @param msg 接收到的消息
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf)) {
            return;
        }
        
        ConnectionInfo connectionInfo = ctx.channel().attr(CONNECTION_INFO_KEY).get();
        if (connectionInfo == null) {
            log.warn("连接信息为空，忽略数据");
            return;
        }
        
        ByteBuf byteBuf = (ByteBuf) msg;
        try {
            // 读取数据（通常是心跳包或确认包）
            byte[] data = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(data);
            
            // 更新连接统计信息
            connectionInfo.setLastActiveTime(LocalDateTime.now());
            connectionInfo.setReceivedBytes(connectionInfo.getReceivedBytes() + data.length);
            connectionInfo.setReceivedMessages(connectionInfo.getReceivedMessages() + 1);
            
            log.debug("接收到移动站数据 - 连接ID: {}, 数据长度: {} 字节", 
                    connectionInfo.getConnectionId(), data.length);
            
        } catch (Exception e) {
            log.error("处理移动站数据时发生错误 - 连接ID: {}", connectionInfo.getConnectionId(), e);
        } finally {
            byteBuf.release();
        }
    }
    
    /**
     * 连接断开时的处理
     * 
     * @param ctx 通道上下文
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ConnectionInfo connectionInfo = ctx.channel().attr(CONNECTION_INFO_KEY).get();
        if (connectionInfo != null) {
            connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.DISCONNECTED);
            connectionManager.unregisterMobileStation(connectionInfo.getConnectionId());
            
            log.info("移动站连接断开 - 连接ID: {}, 远程地址: {}:{}", 
                    connectionInfo.getConnectionId(), 
                    connectionInfo.getRemoteAddress(), 
                    connectionInfo.getRemotePort());
        }
    }
    
    /**
     * 异常处理
     * 
     * @param ctx 通道上下文
     * @param cause 异常原因
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ConnectionInfo connectionInfo = ctx.channel().attr(CONNECTION_INFO_KEY).get();
        String connectionId = connectionInfo != null ? connectionInfo.getConnectionId() : "UNKNOWN";
        
        log.error("移动站连接发生异常 - 连接ID: {}", connectionId, cause);
        
        if (connectionInfo != null) {
            connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.ERROR);
        }
        
        ctx.close();
    }
}
