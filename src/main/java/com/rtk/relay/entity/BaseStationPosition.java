package com.rtk.relay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 基站位置数据实体类
 * 实现1小时内更新策略，大大减少数据量
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
@TableName("base_station_positions")
public class BaseStationPosition {

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
     * 本小时内第一次定位时间
     */
    private LocalDateTime firstPositionTime;

    /**
     * 本小时内最后一次定位时间
     */
    private LocalDateTime lastPositionTime;

    /**
     * 最新的位置数据（原始RTK数据）
     */
    private byte[] positionData;

    /**
     * 本小时内更新次数
     */
    private Integer positionCount;

    /**
     * 数据大小（字节）
     */
    private Long dataSize;

    /**
     * 数据校验和（用于检测变化）
     */
    private String checksum;

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
     * 更新位置数据
     */
    public void updatePosition(byte[] newData, String newChecksum) {
        this.positionData = newData;
        this.checksum = newChecksum;
        this.lastPositionTime = LocalDateTime.now();
        this.dataSize = (long) newData.length;
        
        if (this.positionCount == null) {
            this.positionCount = 1;
        } else {
            this.positionCount++;
        }
    }

    /**
     * 初始化新记录
     */
    public void initializeNewRecord(String baseStationId, String remoteAddress, 
                                   byte[] positionData, String checksum) {
        this.baseStationId = baseStationId;
        this.remoteAddress = remoteAddress;
        this.hourSlot = getCurrentHourSlot();
        this.firstPositionTime = LocalDateTime.now();
        this.lastPositionTime = LocalDateTime.now();
        this.positionData = positionData;
        this.checksum = checksum;
        this.positionCount = 1;
        this.dataSize = (long) positionData.length;
    }
}
