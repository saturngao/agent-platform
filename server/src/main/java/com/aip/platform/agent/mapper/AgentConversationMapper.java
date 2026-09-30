package com.aip.platform.agent.mapper;

import com.aip.platform.agent.entity.AgentConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 智能体会话 Mapper
 */
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {

    /**
     * 分页查询会话列表，支持 agentId / userId / title 模糊
     */
    @Select("<script>"
            + "SELECT * FROM agent_conversation "
            + "<where>"
            + "  <if test='q.agentId != null'>AND agent_id = #{q.agentId}</if>"
            + "  <if test='q.userId != null'>AND user_id = #{q.userId}</if>"
            + "  <if test='q.status != null'>AND status = #{q.status}</if>"
            + "  <if test='q.title != null and q.title != \"\"'>AND title LIKE concat('%', #{q.title}, '%')</if>"
            + "</where>"
            + " ORDER BY update_time DESC, conv_id DESC"
            + "</script>")
    IPage<AgentConversation> selectConversationPage(IPage<AgentConversation> page,
                                                    @Param("q") AgentConversation q);

    /**
     * 查询指定智能体下的会话ID集合（删除智能体时级联清理用）
     */
    @Select("SELECT conv_id FROM agent_conversation WHERE agent_id = #{agentId}")
    List<Long> selectConvIdsByAgentId(@Param("agentId") Long agentId);
}
