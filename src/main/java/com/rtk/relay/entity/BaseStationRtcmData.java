package com.rtk.relay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 基站RTCM差分数据实体类
 * 实现1小时内更新策略，大大减少数据量
 * 基站发送RTCM差分修正数据，系统转发给移动站
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
@TableName("base_station_rtcm_data")
public class BaseStationRtcmData {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 基站ID（连接ID）
     */
    private String baseStationId;

    /**
     * 基站IP地址
     */
    private String remoteAddress;

    /**
     * 小时时间槽（精确到小时）
     * 例如：2025-09-04 15:00:00
     */
    private LocalDateTime hourSlot;

    /**
     * 本小时内第一次接收数据时间
     */
    private LocalDateTime firstDataTime;

    /**
     * 本小时内最后一次接收数据时间
     */
    private LocalDateTime lastDataTime;

    /**
     * 最新的RTCM差分修正数据
     */
    private byte[] rtcmData;

    /**
     * 本小时内接收数据次数
     */
    private Integer dataCount;

    /**
     * 数据大小（字节）
     */
    private Long dataSize;

    /**
     * 数据校验和（用于检测变化）
     */
    private String checksum;

    /**
     * RTCM消息类型列表（如：1074,1084,1094）
     */
    private String rtcmMessageTypes;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 判断是否为新的小时时间槽
     */
    public boolean isNewHourSlot(LocalDateTime currentTime) {
        if (hourSlot == null) return true;
        
        LocalDateTime currentHourSlot = currentTime
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        
        return !hourSlot.equals(currentHourSlot);
    }

    /**
     * 创建当前小时的时间槽
     */
    public static LocalDateTime getCurrentHourSlot() {
        return LocalDateTime.now()
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    /**
     * 更新RTCM数据
     */
    public void updateRtcmData(byte[] newData, String newChecksum) {
        this.rtcmData = newData;
        this.checksum = newChecksum;
        this.lastDataTime = LocalDateTime.now();
        this.dataSize = (long) newData.length;
        
        if (this.dataCount == null) {
            this.dataCount = 1;
        } else {
            this.dataCount++;
        }
        
        // 解析RTCM消息类型（简单实现）
        this.rtcmMessageTypes = parseRtcmMessageTypes(newData);
    }

    /**
     * 初始化新记录
     */
    public void initializeNewRecord(String baseStationId, String remoteAddress, 
                                   byte[] rtcmData, String checksum) {
        this.baseStationId = baseStationId;
        this.remoteAddress = remoteAddress;
        this.hourSlot = getCurrentHourSlot();
        this.firstDataTime = LocalDateTime.now();
        this.lastDataTime = LocalDateTime.now();
        this.rtcmData = rtcmData;
        this.checksum = checksum;
        this.dataCount = 1;
        this.dataSize = (long) rtcmData.length;
        this.rtcmMessageTypes = parseRtcmMessageTypes(rtcmData);
    }

    /**
     * 解析RTCM消息类型（简单实现）
     * 实际应用中可能需要更复杂的RTCM解析逻辑
     */
    private String parseRtcmMessageTypes(byte[] data) {
        // 这里只是示例实现，实际需要根据RTCM协议解析
        if (data == null || data.length < 3) {
            return "unknown";
        }
        
        // RTCM消息通常以0xD3开头
        if ((data[0] & 0xFF) == 0xD3) {
            // 简单的消息类型识别，实际应用中需要完整的RTCM解析器
            return "RTCM3";
        }
        
        return "unknown";
    }

    /**
     * 获取数据统计信息
     */
    public String getDataSummary() {
        return String.format("BaseStation[%s] Hour[%s] Count[%d] Size[%dB] Types[%s]",
                baseStationId, 
                hourSlot != null ? hourSlot.toString() : "N/A",
                dataCount != null ? dataCount : 0,
                dataSize != null ? dataSize : 0,
                rtcmMessageTypes != null ? rtcmMessageTypes : "unknown");
    }
}
