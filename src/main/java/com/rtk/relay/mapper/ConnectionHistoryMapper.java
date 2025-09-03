package com.rtk.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rtk.relay.entity.ConnectionHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ConnectionHistoryMapper extends BaseMapper<ConnectionHistory> {

    /**
     * 根据连接ID查询连接历史
     */
    @Select("SELECT * FROM connection_history WHERE connection_id = #{connectionId} ORDER BY connect_time DESC LIMIT 1")
    ConnectionHistory selectByConnectionId(@Param("connectionId") String connectionId);

    /**
     * 删除指定时间之前的旧记录
     */
    @Delete("DELETE FROM connection_history WHERE connect_time < #{cutoffTime}")
    int deleteOldRecords(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 查询最近的连接记录
     */
    @Select("SELECT * FROM connection_history WHERE connect_time >= #{since} ORDER BY connect_time DESC")
    List<ConnectionHistory> selectRecentConnections(@Param("since") LocalDateTime since);

    /**
     * 获取连接数量排行榜（按远程地址统计）
     */
    @Select("SELECT remote_address, COUNT(*) as connection_count, " +
            "SUM(received_bytes) as total_received, SUM(sent_bytes) as total_sent, " +
            "AVG(duration_seconds) as avg_duration " +
            "FROM connection_history " +
            "WHERE disconnect_time IS NOT NULL " +
            "GROUP BY remote_address " +
            "ORDER BY connection_count DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectTopConnections(@Param("limit") int limit);
}
