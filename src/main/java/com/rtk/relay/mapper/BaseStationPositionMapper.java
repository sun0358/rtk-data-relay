package com.rtk.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rtk.relay.entity.BaseStationPosition;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 基站位置数据Mapper
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@Mapper
public interface BaseStationPositionMapper extends BaseMapper<BaseStationPosition> {

    /**
     * 查找指定基站在当前小时的记录
     * 
     * @param baseStationId 基站ID
     * @param hourSlot 小时时间槽
     * @return 基站位置记录
     */
    @Select("SELECT * FROM base_station_positions " +
            "WHERE base_station_id = #{baseStationId} AND hour_slot = #{hourSlot}")
    BaseStationPosition selectByStationAndHour(@Param("baseStationId") String baseStationId,
                                              @Param("hourSlot") LocalDateTime hourSlot);

    /**
     * 更新基站位置数据（仅更新数据相关字段）
     * 
     * @param id 记录ID
     * @param positionData 位置数据
     * @param checksum 校验和
     * @param lastPositionTime 最后定位时间
     * @param positionCount 更新次数
     * @param dataSize 数据大小
     * @return 更新行数
     */
    @Update("UPDATE base_station_positions SET " +
            "position_data = #{positionData}, " +
            "checksum = #{checksum}, " +
            "last_position_time = #{lastPositionTime}, " +
            "position_count = #{positionCount}, " +
            "data_size = #{dataSize}, " +
            "updated_at = NOW() " +
            "WHERE id = #{id}")
    int updatePositionData(@Param("id") Long id,
                          @Param("positionData") byte[] positionData,
                          @Param("checksum") String checksum,
                          @Param("lastPositionTime") LocalDateTime lastPositionTime,
                          @Param("positionCount") Integer positionCount,
                          @Param("dataSize") Long dataSize);

    /**
     * 查询指定时间范围内的基站位置记录
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 基站位置记录列表
     */
    @Select("SELECT * FROM base_station_positions " +
            "WHERE hour_slot >= #{startTime} AND hour_slot <= #{endTime} " +
            "ORDER BY base_station_id, hour_slot")
    List<BaseStationPosition> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定基站的历史记录
     * 
     * @param baseStationId 基站ID
     * @param days 查询天数
     * @return 基站位置记录列表
     */
    @Select("SELECT * FROM base_station_positions " +
            "WHERE base_station_id = #{baseStationId} " +
            "AND hour_slot >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "ORDER BY hour_slot DESC")
    List<BaseStationPosition> selectByStationHistory(@Param("baseStationId") String baseStationId,
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
            "SUM(position_count) as total_updates, " +
            "AVG(position_count) as avg_updates_per_hour, " +
            "MIN(first_position_time) as first_update, " +
            "MAX(last_position_time) as last_update " +
            "FROM base_station_positions " +
            "WHERE DATE(hour_slot) = #{date} " +
            "GROUP BY base_station_id " +
            "ORDER BY total_updates DESC")
    List<Map<String, Object>> selectDailyUpdateStats(@Param("date") String date);

    /**
     * 清理旧的位置数据
     * 
     * @param cutoffTime 截止时间
     * @return 删除的记录数
     */
    @Delete("DELETE FROM base_station_positions WHERE hour_slot < #{cutoffTime}")
    int deleteOldPositions(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 获取基站实时状态
     * 
     * @return 基站状态列表
     */
    @Select("SELECT " +
            "bsp.base_station_id, " +
            "bsp.remote_address, " +
            "bsp.last_position_time, " +
            "bsp.position_count as hourly_updates, " +
            "bsp.data_size, " +
            "TIMESTAMPDIFF(SECOND, bsp.last_position_time, NOW()) as inactive_seconds " +
            "FROM base_station_positions bsp " +
            "WHERE bsp.hour_slot = DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00') " +
            "ORDER BY bsp.last_position_time DESC")
    List<Map<String, Object>> selectCurrentBaseStationStatus();

    /**
     * 统计数据存储效率（对比传统方式）
     * 
     * @param days 统计天数
     * @return 效率统计
     */
    @Select("SELECT " +
            "COUNT(*) as optimized_records, " +
            "SUM(position_count) as total_original_records, " +
            "ROUND((1 - COUNT(*) / SUM(position_count)) * 100, 2) as storage_efficiency_percent, " +
            "SUM(data_size) as total_data_size_bytes, " +
            "AVG(position_count) as avg_updates_per_hour " +
            "FROM base_station_positions " +
            "WHERE hour_slot >= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    Map<String, Object> selectStorageEfficiencyStats(@Param("days") int days);
}
