package com.rtk.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rtk.relay.entity.DataRelayLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据转发日志Mapper
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Mapper
public interface DataRelayLogMapper extends BaseMapper<DataRelayLog> {

    /**
     * 批量插入转发日志（提高性能）
     * 
     * @param logs 转发日志列表
     * @return 插入数量
     */
    @Insert("<script>" +
            "INSERT INTO data_relay_logs (base_station_id, mobile_station_id, relay_time, data_size, relay_status, error_message, created_at) VALUES " +
            "<foreach collection='logs' item='log' separator=','>" +
            "(#{log.baseStationId}, #{log.mobileStationId}, #{log.relayTime}, #{log.dataSize}, #{log.relayStatus}, #{log.errorMessage}, NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("logs") List<DataRelayLog> logs);

    /**
     * 查询指定时间范围内的转发统计
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 转发统计
     */
    @Select("SELECT " +
            "base_station_id, " +
            "COUNT(*) as total_relays, " +
            "COUNT(CASE WHEN relay_status = 'SUCCESS' THEN 1 END) as success_relays, " +
            "COUNT(CASE WHEN relay_status = 'FAILED' THEN 1 END) as failed_relays, " +
            "ROUND((COUNT(CASE WHEN relay_status = 'SUCCESS' THEN 1 END) / COUNT(*)) * 100, 2) as success_rate, " +
            "SUM(data_size) as total_data_size, " +
            "AVG(data_size) as avg_data_size, " +
            "COUNT(DISTINCT mobile_station_id) as target_mobile_stations " +
            "FROM data_relay_logs " +
            "WHERE relay_time >= #{startTime} AND relay_time <= #{endTime} " +
            "GROUP BY base_station_id " +
            "ORDER BY total_relays DESC")
    List<Map<String, Object>> selectRelayStatsByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 查询移动站接收统计
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 接收统计
     */
    @Select("SELECT " +
            "mobile_station_id, " +
            "COUNT(*) as total_received, " +
            "COUNT(CASE WHEN relay_status = 'SUCCESS' THEN 1 END) as success_received, " +
            "SUM(CASE WHEN relay_status = 'SUCCESS' THEN data_size ELSE 0 END) as total_received_bytes, " +
            "COUNT(DISTINCT base_station_id) as source_base_stations, " +
            "MIN(relay_time) as first_received_time, " +
            "MAX(relay_time) as last_received_time " +
            "FROM data_relay_logs " +
            "WHERE relay_time >= #{startTime} AND relay_time <= #{endTime} " +
            "GROUP BY mobile_station_id " +
            "ORDER BY total_received DESC")
    List<Map<String, Object>> selectMobileStationReceiveStats(@Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);

    /**
     * 查询转发失败的详细信息
     * 
     * @param hours 查询小时数
     * @param limit 限制数量
     * @return 失败详情
     */
    @Select("SELECT " +
            "base_station_id, " +
            "mobile_station_id, " +
            "relay_time, " +
            "data_size, " +
            "error_message " +
            "FROM data_relay_logs " +
            "WHERE relay_status = 'FAILED' " +
            "AND relay_time >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR) " +
            "ORDER BY relay_time DESC " +
            "LIMIT #{limit}")
    List<DataRelayLog> selectRecentFailedRelays(@Param("hours") int hours, @Param("limit") int limit);

    /**
     * 查询转发链路分析
     * 
     * @param hours 统计小时数
     * @return 链路分析
     */
    @Select("SELECT " +
            "base_station_id, " +
            "mobile_station_id, " +
            "COUNT(*) as relay_count, " +
            "COUNT(CASE WHEN relay_status = 'SUCCESS' THEN 1 END) as success_count, " +
            "SUM(CASE WHEN relay_status = 'SUCCESS' THEN data_size ELSE 0 END) as success_bytes, " +
            "ROUND(AVG(data_size), 2) as avg_data_size, " +
            "MAX(relay_time) as last_relay_time " +
            "FROM data_relay_logs " +
            "WHERE relay_time >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR) " +
            "GROUP BY base_station_id, mobile_station_id " +
            "ORDER BY relay_count DESC")
    List<Map<String, Object>> selectRelayLinkAnalysis(@Param("hours") int hours);

    /**
     * 查询系统转发性能统计
     * 
     * @param hours 统计小时数
     * @return 性能统计
     */
    @Select("SELECT " +
            "COUNT(*) as total_relay_attempts, " +
            "COUNT(CASE WHEN relay_status = 'SUCCESS' THEN 1 END) as total_success, " +
            "COUNT(CASE WHEN relay_status = 'FAILED' THEN 1 END) as total_failed, " +
            "ROUND((COUNT(CASE WHEN relay_status = 'SUCCESS' THEN 1 END) / COUNT(*)) * 100, 2) as overall_success_rate, " +
            "SUM(CASE WHEN relay_status = 'SUCCESS' THEN data_size ELSE 0 END) as total_success_bytes, " +
            "COUNT(DISTINCT base_station_id) as active_base_stations, " +
            "COUNT(DISTINCT mobile_station_id) as active_mobile_stations, " +
            "ROUND(AVG(CASE WHEN relay_status = 'SUCCESS' THEN data_size END), 2) as avg_success_data_size " +
            "FROM data_relay_logs " +
            "WHERE relay_time >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR)")
    Map<String, Object> selectSystemRelayPerformance(@Param("hours") int hours);

    /**
     * 清理旧的转发日志
     * 
     * @param cutoffTime 截止时间
     * @return 删除的记录数
     */
    @Delete("DELETE FROM data_relay_logs WHERE relay_time < #{cutoffTime}")
    int deleteOldLogs(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 查询每小时转发量统计（用于生成图表）
     * 
     * @param hours 统计小时数
     * @return 每小时统计
     */
    @Select("SELECT " +
            "DATE_FORMAT(relay_time, '%Y-%m-%d %H:00:00') as hour_slot, " +
            "COUNT(*) as total_relays, " +
            "COUNT(CASE WHEN relay_status = 'SUCCESS' THEN 1 END) as success_relays, " +
            "SUM(CASE WHEN relay_status = 'SUCCESS' THEN data_size ELSE 0 END) as success_bytes " +
            "FROM data_relay_logs " +
            "WHERE relay_time >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR) " +
            "GROUP BY DATE_FORMAT(relay_time, '%Y-%m-%d %H:00:00') " +
            "ORDER BY hour_slot")
    List<Map<String, Object>> selectHourlyRelayStats(@Param("hours") int hours);
}
