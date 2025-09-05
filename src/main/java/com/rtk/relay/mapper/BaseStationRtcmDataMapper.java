package com.rtk.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rtk.relay.entity.BaseStationRtcmData;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 基站RTCM差分数据Mapper
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Mapper
public interface BaseStationRtcmDataMapper extends BaseMapper<BaseStationRtcmData> {

    /**
     * 查找指定基站在当前小时的记录
     * 
     * @param baseStationId 基站ID
     * @param hourSlot 小时时间槽
     * @return 基站RTCM数据记录
     */
    @Select("SELECT * FROM base_station_rtcm_data " +
            "WHERE base_station_id = #{baseStationId} AND hour_slot = #{hourSlot}")
    BaseStationRtcmData selectByStationAndHour(@Param("baseStationId") String baseStationId,
                                               @Param("hourSlot") LocalDateTime hourSlot);

    /**
     * 更新基站RTCM数据（仅更新数据相关字段）
     * 
     * @param id 记录ID
     * @param rtcmData RTCM数据
     * @param checksum 校验和
     * @param lastDataTime 最后数据时间
     * @param dataCount 数据次数
     * @param dataSize 数据大小
     * @param rtcmMessageTypes RTCM消息类型
     * @return 更新行数
     */
    @Update("UPDATE base_station_rtcm_data SET " +
            "rtcm_data = #{rtcmData}, " +
            "checksum = #{checksum}, " +
            "last_data_time = #{lastDataTime}, " +
            "data_count = #{dataCount}, " +
            "data_size = #{dataSize}, " +
            "rtcm_message_types = #{rtcmMessageTypes}, " +
            "updated_at = NOW() " +
            "WHERE id = #{id}")
    int updateRtcmData(@Param("id") Long id,
                       @Param("rtcmData") byte[] rtcmData,
                       @Param("checksum") String checksum,
                       @Param("lastDataTime") LocalDateTime lastDataTime,
                       @Param("dataCount") Integer dataCount,
                       @Param("dataSize") Long dataSize,
                       @Param("rtcmMessageTypes") String rtcmMessageTypes);

    /**
     * 查询指定时间范围内的基站RTCM数据记录
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 基站RTCM数据记录列表
     */
    @Select("SELECT * FROM base_station_rtcm_data " +
            "WHERE hour_slot >= #{startTime} AND hour_slot <= #{endTime} " +
            "ORDER BY base_station_id, hour_slot")
    List<BaseStationRtcmData> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定基站的历史记录
     * 
     * @param baseStationId 基站ID
     * @param days 查询天数
     * @return 基站RTCM数据记录列表
     */
    @Select("SELECT * FROM base_station_rtcm_data " +
            "WHERE base_station_id = #{baseStationId} " +
            "AND hour_slot >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "ORDER BY hour_slot DESC")
    List<BaseStationRtcmData> selectByStationHistory(@Param("baseStationId") String baseStationId,
                                                     @Param("days") int days);

    /**
     * 统计基站数据更新情况
     * 
     * @param date 统计日期
     * @return 统计结果
     */
    @Select("SELECT " +
            "base_station_id, " +
            "COUNT(*) as hourly_records, " +
            "SUM(data_count) as total_data_count, " +
            "AVG(data_count) as avg_data_per_hour, " +
            "SUM(data_size) as total_data_size, " +
            "MIN(first_data_time) as first_data_time, " +
            "MAX(last_data_time) as last_data_time, " +
            "GROUP_CONCAT(DISTINCT rtcm_message_types) as all_message_types " +
            "FROM base_station_rtcm_data " +
            "WHERE DATE(hour_slot) = #{date} " +
            "GROUP BY base_station_id " +
            "ORDER BY total_data_count DESC")
    List<Map<String, Object>> selectDailyDataStats(@Param("date") String date);

    /**
     * 清理旧的RTCM数据
     * 
     * @param cutoffTime 截止时间
     * @return 删除的记录数
     */
    @Delete("DELETE FROM base_station_rtcm_data WHERE hour_slot < #{cutoffTime}")
    int deleteOldRtcmData(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 获取基站实时状态
     * 
     * @return 基站状态列表
     */
    @Select("SELECT " +
            "brd.base_station_id, " +
            "brd.remote_address, " +
            "brd.last_data_time, " +
            "brd.data_count as hourly_data_count, " +
            "brd.data_size, " +
            "brd.rtcm_message_types, " +
            "TIMESTAMPDIFF(SECOND, brd.last_data_time, NOW()) as inactive_seconds " +
            "FROM base_station_rtcm_data brd " +
            "WHERE brd.hour_slot = DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00') " +
            "ORDER BY brd.last_data_time DESC")
    List<Map<String, Object>> selectCurrentBaseStationStatus();

    /**
     * 统计数据存储效率（对比传统方式）
     * 
     * @param days 统计天数
     * @return 效率统计
     */
    @Select("SELECT " +
            "COUNT(*) as optimized_records, " +
            "SUM(data_count) as total_original_records, " +
            "ROUND((1 - COUNT(*) / SUM(data_count)) * 100, 2) as storage_efficiency_percent, " +
            "SUM(data_size) as total_data_size_bytes, " +
            "AVG(data_count) as avg_data_per_hour, " +
            "COUNT(DISTINCT base_station_id) as active_base_stations " +
            "FROM base_station_rtcm_data " +
            "WHERE hour_slot >= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    Map<String, Object> selectStorageEfficiencyStats(@Param("days") int days);

    /**
     * 查询基站数据完整性统计
     * 
     * @param baseStationId 基站ID
     * @param date 统计日期
     * @return 完整性统计
     */
    @Select("SELECT " +
            "base_station_id, " +
            "COUNT(*) as hourly_records, " +
            "SUM(data_count) as total_data_count, " +
            "CASE WHEN COUNT(*) = 24 THEN '完整' ELSE '不完整' END as data_integrity, " +
            "ROUND((COUNT(*) / 24.0) * 100, 2) as coverage_percent " +
            "FROM base_station_rtcm_data " +
            "WHERE base_station_id = #{baseStationId} AND DATE(hour_slot) = #{date} " +
            "GROUP BY base_station_id")
    Map<String, Object> selectBaseStationIntegrityStats(@Param("baseStationId") String baseStationId,
                                                        @Param("date") String date);
}
