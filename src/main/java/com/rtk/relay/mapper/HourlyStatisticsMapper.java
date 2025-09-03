package com.rtk.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rtk.relay.entity.HourlyStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface HourlyStatisticsMapper extends BaseMapper<HourlyStatistics> {

    /**
     * 计算指定时间段的小时统计数据
     */
    @Select("SELECT " +
            "COUNT(CASE WHEN connection_type = 'BASE_STATION' THEN 1 END) as baseStationConnections, " +
            "COUNT(CASE WHEN connection_type = 'MOBILE_STATION' THEN 1 END) as mobileStationConnections, " +
            "COALESCE(SUM(received_bytes), 0) as totalReceivedBytes, " +
            "COALESCE(SUM(sent_bytes), 0) as totalSentBytes, " +
            "COUNT(*) as totalReceivedMessages, " +
            "COUNT(*) as totalSentMessages, " +
            "0 as connectionErrors, " +
            "0 as relayErrors, " +
            "COALESCE(AVG(duration_seconds), 0) as avgConnectionDuration, " +
            "NOW() as createTime " +
            "FROM connection_history " +
            "WHERE connect_time >= #{startTime} AND connect_time < #{endTime}")
    HourlyStatistics calculateHourlyStats(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 删除指定时间之前的旧统计数据
     */
    @Delete("DELETE FROM hourly_statistics WHERE stat_hour < #{cutoffTime}")
    int deleteOldStatistics(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 查询指定时间范围内的统计数��
     */
    @Select("SELECT * FROM hourly_statistics " +
            "WHERE stat_hour >= #{startTime} AND stat_hour <= #{endTime} " +
            "ORDER BY stat_hour ASC")
    List<HourlyStatistics> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);
}
