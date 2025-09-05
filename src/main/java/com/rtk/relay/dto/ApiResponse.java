package com.rtk.relay.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 统一API响应格式
 * 符合RESTful API设计规范
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Data
public class ApiResponse<T> {
    
    /**
     * 响应状态码
     * 200: 成功
     * 400: 客户端错误
     * 500: 服务器错误
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 请求路径（可选）
     */
    private String path;
    
    /**
     * 私有构造函数
     */
    private ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Success");
        response.setData(data);
        return response;
    }
    
    /**
     * 成功响应（带数据和自定义消息）
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
    
    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Success");
        return response;
    }
    
    /**
     * 客户端错误响应
     */
    public static <T> ApiResponse<T> badRequest(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(400);
        response.setMessage(message);
        return response;
    }
    
    /**
     * 服务器错误响应
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }
    
    /**
     * 自定义响应
     */
    public static <T> ApiResponse<T> custom(Integer code, String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
    
    /**
     * 设置请求路径
     */
    public ApiResponse<T> path(String path) {
        this.setPath(path);
        return this;
    }
}
