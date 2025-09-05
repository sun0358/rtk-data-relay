package com.rtk.relay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据转发日志实体类
 * 记录基站数据转发给移动站的统计信息
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
@TableName("data_relay_logs")
public class DataRelayLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数据源基站ID
     */
    private String baseStationId;

    /**
     * 目标移动站ID
     */
    private String mobileStationId;

    /**
     * 转发时间
     */
    private LocalDateTime relayTime;

    /**
     * 转发数据大小（字节）
     */
    private Long dataSize;

    /**
     * 转发状态：SUCCESS/FAILED
     */
    private String relayStatus;

    /**
     * 错误信息（如果转发失败）
     */
    private String errorMessage;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 转发状态枚举
     */
    public enum RelayStatus {
        SUCCESS("SUCCESS"),
        FAILED("FAILED"),
        TIMEOUT("TIMEOUT"),
        CHANNEL_INACTIVE("CHANNEL_INACTIVE");

        private final String value;

        RelayStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 创建成功的转发日志
     */
    public static DataRelayLog createSuccessLog(String baseStationId, String mobileStationId, Long dataSize) {
        DataRelayLog log = new DataRelayLog();
        log.setBaseStationId(baseStationId);
        log.setMobileStationId(mobileStationId);
        log.setRelayTime(LocalDateTime.now());
        log.setDataSize(dataSize);
        log.setRelayStatus(RelayStatus.SUCCESS.getValue());
        return log;
    }

    /**
     * 创建失败的转发日志
     */
    public static DataRelayLog createFailedLog(String baseStationId, String mobileStationId, 
                                              Long dataSize, String errorMessage) {
        DataRelayLog log = new DataRelayLog();
        log.setBaseStationId(baseStationId);
        log.setMobileStationId(mobileStationId);
        log.setRelayTime(LocalDateTime.now());
        log.setDataSize(dataSize);
        log.setRelayStatus(RelayStatus.FAILED.getValue());
        log.setErrorMessage(errorMessage);
        return log;
    }

    /**
     * 检查是否为成功转发
     */
    public boolean isSuccess() {
        return RelayStatus.SUCCESS.getValue().equals(this.relayStatus);
    }
}
