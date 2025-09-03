package com.rtk.relay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小时统计数据实体类
 *
 * @author RTK Team
 * @version 1.0.0
 */
@Data
@TableName("hourly_statistics")
public class HourlyStatistics {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计小时
     */
    private LocalDateTime statHour;

    /**
     * 基站连接总数
     */
    private Integer baseStationConnections;

    /**
     * 移动站连接总数
     */
    private Integer mobileStationConnections;

    /**
     * 总接收字节数
     */
    private Long totalReceivedBytes;

    /**
     * 总发送字节数
     */
    private Long totalSentBytes;

    /**
     * 总接收消息数
     */
    private Long totalReceivedMessages;

    /**
     * 总发送消息数
     */
    private Long totalSentMessages;

    /**
     * 连接错误次数
     */
    private Integer connectionErrors;

    /**
     * 数据转发错误次数
     */
    private Integer relayErrors;

    /**
     * 平均连接时长（秒）
     */
    private Long avgConnectionDuration;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
