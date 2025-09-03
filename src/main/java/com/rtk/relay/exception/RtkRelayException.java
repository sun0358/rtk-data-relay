package com.rtk.relay.exception;

import lombok.Getter;

/**
 * RTK数据转发服务自定义异常类
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Getter
public class RtkRelayException extends RuntimeException {
    
    /**
     * 错误码
     * -- GETTER --
     *  获取错误码
     *
     * @return 错误码

     */
    private final String errorCode;
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public RtkRelayException(String message) {
        super(message);
        this.errorCode = "RTK_ERROR";
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public RtkRelayException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原因异常
     */
    public RtkRelayException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "RTK_ERROR";
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public RtkRelayException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
