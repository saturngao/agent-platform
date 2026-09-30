package com.aip.platform.agent.mapper;

import com.aip.platform.agent.entity.AgentDefinition;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 智能体定义 Mapper
 *
 * <p>查询统一使用注解 SQL（@Select + &lt;script&gt;），不额外维护 xml；
 * 列表联查 llm_model 只为取模型名称，绝不返回 apiKey 等敏感字段。</p>
 */
public interface AgentDefinitionMapper extends BaseMapper<AgentDefinition> {

    /**
     * 分页查询智能体列表（带模型名称）
     */
    @Select("<script>"
            + "SELECT a.*, m.model_name AS modelName "
            + "FROM agent_definition a "
            + "LEFT JOIN llm_model m ON a.model_id = m.model_id "
            + "<where>"
            + "  <if test='q.agentName != null and q.agentName != \"\"'>"
            + "    AND (a.agent_name LIKE concat('%', #{q.agentName}, '%') OR a.agent_code LIKE concat('%', #{q.agentName}, '%'))"
            + "  </if>"
            + "  <if test='q.agentCode != null and q.agentCode != \"\"'>AND a.agent_code = #{q.agentCode}</if>"
            + "  <if test='q.category != null and q.category != \"\"'>AND a.category = #{q.category}</if>"
            + "  <if test='q.status != null'>AND a.status = #{q.status}</if>"
            + "  <if test='q.deptId != null'>AND a.dept_id = #{q.deptId}</if>"
            + "</where>"
            + " ORDER BY a.agent_id DESC"
            + "</script>")
    IPage<AgentDefinition> selectAgentPage(IPage<AgentDefinition> page, @Param("q") AgentDefinition q);

    /**
     * 查询智能体详情（带模型名称）
     */
    @Select("SELECT a.*, m.model_name AS modelName "
            + "FROM agent_definition a "
            + "LEFT JOIN llm_model m ON a.model_id = m.model_id "
            + "WHERE a.agent_id = #{agentId}")
    AgentDefinition selectDetail(@Param("agentId") Long agentId);
}
