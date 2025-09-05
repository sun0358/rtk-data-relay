package com.rtk.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rtk.relay.entity.DataQualityStats;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据质量统计Mapper
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Mapper
public interface DataQualityStatsMapper extends BaseMapper<DataQualityStats> {

    /**
     * 查询指定日期和基站的数据质量统计
     * 
     * @param statDate 统计日期
     * @param baseStationId 基站ID
     * @return 数据质量统计
     */
    @Select("SELECT * FROM data_quality_stats " +
            "WHERE stat_date = #{statDate} AND base_station_id = #{baseStationId}")
    DataQualityStats selectByDateAndStation(@Param("statDate") LocalDate statDate,
                                           @Param("baseStationId") String baseStationId);

    /**
     * 查询指定时间范围的数据质量统计
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 数据质量统计列表
     */
    @Select("SELECT * FROM data_quality_stats " +
            "WHERE stat_date >= #{startDate} AND stat_date <= #{endDate} " +
            "ORDER BY stat_date DESC, data_integrity_rate ASC")
    List<DataQualityStats> selectByDateRange(@Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /**
     * 查询数据质量汇总统计
     * 
     * @param days 统计天数
     * @return 汇总统计
     */
    @Select("SELECT " +
            "COUNT(DISTINCT base_station_id) as base_station_count, " +
            "AVG(data_integrity_rate) as avg_integrity_rate, " +
            "MIN(data_integrity_rate) as min_integrity_rate, " +
            "MAX(data_integrity_rate) as max_integrity_rate, " +
            "SUM(expected_messages) as total_expected_messages, " +
            "SUM(actual_messages) as total_actual_messages, " +
            "SUM(missing_messages) as total_missing_messages, " +
            "SUM(duplicate_messages) as total_duplicate_messages, " +
            "AVG(avg_data_size) as avg_message_size " +
            "FROM data_quality_stats " +
            "WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)")
    Map<String, Object> selectQualitySummary(@Param("days") int days);

    /**
     * 查询数据质量最差的基站
     * 
     * @param days 统计天数
     * @param limit 返回数量限制
     * @return 基站质量统计
     */
    @Select("SELECT " +
            "base_station_id, " +
            "AVG(data_integrity_rate) as avg_integrity_rate, " +
            "SUM(missing_messages) as total_missing_messages, " +
            "COUNT(*) as stat_days " +
            "FROM data_quality_stats " +
            "WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY base_station_id " +
            "HAVING avg_integrity_rate < 95.0 " +
            "ORDER BY avg_integrity_rate ASC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectPoorQualityStations(@Param("days") int days,
                                                        @Param("limit") int limit);

    /**
     * 更新或插入数据质量统计
     * 
     * @param statDate 统计日期
     * @param baseStationId 基站ID
     * @param actualMessages 实际消息数
     * @param duplicateMessages 重复消息数
     * @param avgDataSize 平均数据大小
     * @return 影响行数
     */
    @Insert("INSERT INTO data_quality_stats " +
            "(stat_date, base_station_id, expected_messages, actual_messages, " +
            "duplicate_messages, avg_data_size, created_at, updated_at) " +
            "VALUES (#{statDate}, #{baseStationId}, 86400, #{actualMessages}, " +
            "#{duplicateMessages}, #{avgDataSize}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "actual_messages = #{actualMessages}, " +
            "duplicate_messages = #{duplicateMessages}, " +
            "missing_messages = 86400 - #{actualMessages}, " +
            "data_integrity_rate = ROUND((#{actualMessages} / 86400.0) * 100, 2), " +
            "avg_data_size = #{avgDataSize}, " +
            "updated_at = NOW()")
    int upsertQualityStats(@Param("statDate") LocalDate statDate,
                          @Param("baseStationId") String baseStationId,
                          @Param("actualMessages") Integer actualMessages,
                          @Param("duplicateMessages") Integer duplicateMessages,
                          @Param("avgDataSize") Double avgDataSize);

    /**
     * 清理旧的数据质量统计
     * 
     * @param cutoffDate 截止日期
     * @return 删除的记录数
     */
    @Delete("DELETE FROM data_quality_stats WHERE stat_date < #{cutoffDate}")
    int deleteOldStats(@Param("cutoffDate") LocalDate cutoffDate);
}
