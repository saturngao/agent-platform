package com.aip.platform.agent.mapper;

import com.aip.platform.agent.entity.AgentMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 智能体消息 Mapper
 */
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    /**
     * 分页查询消息列表（按 msg_id 正序）
     */
    @Select("<script>"
            + "SELECT * FROM agent_message "
            + "<where>"
            + "  <if test='q.convId != null'>AND conv_id = #{q.convId}</if>"
            + "  <if test='q.role != null and q.role != \"\"'>AND role = #{q.role}</if>"
            + "  <if test='q.status != null'>AND status = #{q.status}</if>"
            + "</where>"
            + " ORDER BY msg_id ASC"
            + "</script>")
    IPage<AgentMessage> selectMessagePage(IPage<AgentMessage> page, @Param("q") AgentMessage q);

    /**
     * 取会话最近 N 条消息，并按 msg_id 正序返回（上下文长度保护）
     */
    @Select("SELECT * FROM ("
            + " SELECT * FROM agent_message WHERE conv_id = #{convId} AND status = 0 "
            + " ORDER BY msg_id DESC LIMIT #{limit}"
            + ") t ORDER BY t.msg_id ASC")
    List<AgentMessage> selectLatestMessages(@Param("convId") Long convId, @Param("limit") int limit);

    /**
     * 按会话ID批量删除消息（级联清理）
     */
    @Delete("<script>"
            + "DELETE FROM agent_message WHERE conv_id IN "
            + "<foreach collection='convIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deleteByConvIds(@Param("convIds") List<Long> convIds);
}
