package com.rtk.relay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("connection_history")
public class ConnectionHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String connectionId;

    private String connectionType;

    private String remoteAddress;

    private Integer remotePort;

    private LocalDateTime connectTime;

    private LocalDateTime disconnectTime;

    private Long durationSeconds;

    private Long receivedBytes;

    private Long sentBytes;

    private Long receivedMessages;

    private Long sentMessages;

    private String status;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}