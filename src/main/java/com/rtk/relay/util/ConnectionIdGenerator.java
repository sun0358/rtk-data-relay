package com.rtk.relay.util;

import cn.hutool.core.util.IdUtil;

/**
 * 连接ID生成器工具类
 * 为每个TCP连接生成唯一标识符
 * 
 * @author RTK Team
 * @version 1.0.0
 */
public class ConnectionIdGenerator {
    
    /**
     * 生成基站连接ID
     * 
     * @param remoteAddress 远程地址
     * @param remotePort 远程端口
     * @return 连接ID
     */
    public static String generateBaseStationId(String remoteAddress, int remotePort) {
        return "BASE_" + remoteAddress.replace(".", "_") + "_" + remotePort + "_" + IdUtil.fastSimpleUUID().substring(0, 8);
    }
    
    /**
     * 生成移动站连接ID
     * 
     * @param remoteAddress 远程地址
     * @param remotePort 远程端口
     * @return 连接ID
     */
    public static String generateMobileStationId(String remoteAddress, int remotePort) {
        return "MOBILE_" + remoteAddress.replace(".", "_") + "_" + remotePort + "_" + IdUtil.fastSimpleUUID().substring(0, 8);
    }
    
    /**
     * 生成通用连接ID
     * 
     * @param prefix 前缀
     * @param remoteAddress 远程地址
     * @param remotePort 远程端口
     * @return 连接ID
     */
    public static String generateConnectionId(String prefix, String remoteAddress, int remotePort) {
        return prefix + "_" + remoteAddress.replace(".", "_") + "_" + remotePort + "_" + IdUtil.fastSimpleUUID().substring(0, 8);
    }
}
