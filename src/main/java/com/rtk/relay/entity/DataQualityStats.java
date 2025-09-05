package com.rtk.relay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据质量统计实体类
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
@TableName("data_quality_stats")
public class DataQualityStats {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 基站ID
     */
    private String baseStationId;

    /**
     * 预期消息数（1秒1条=86400条/天）
     */
    private Integer expectedMessages;

    /**
     * 实际消息数
     */
    private Integer actualMessages;

    /**
     * 丢失消息数
     */
    private Integer missingMessages;

    /**
     * 重复消息数
     */
    private Integer duplicateMessages;

    /**
     * 数据完整性率（%）
     */
    private BigDecimal dataIntegrityRate;

    /**
     * 平均数据大小（字节）
     */
    private BigDecimal avgDataSize;

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
     * 计算数据完整性率
     */
    public void calculateIntegrityRate() {
        if (expectedMessages != null && expectedMessages > 0) {
            double rate = (double) actualMessages / expectedMessages * 100;
            this.dataIntegrityRate = BigDecimal.valueOf(rate).setScale(2, BigDecimal.ROUND_HALF_UP);
        } else {
            this.dataIntegrityRate = BigDecimal.ZERO;
        }
        
        this.missingMessages = expectedMessages - actualMessages;
    }
}
