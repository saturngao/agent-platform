package com.aip.platform.agent.service;

import com.aip.platform.agent.dto.AgentReplyDTO;
import com.aip.platform.agent.entity.AgentConversation;
import com.aip.platform.agent.entity.AgentDefinition;
import com.aip.platform.agent.entity.AgentMessage;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能体模块服务接口
 */
public interface IAgentService {

    /**
     * 分页查询智能体列表
     */
    PageResult<AgentDefinition> selectPage(PageQuery query, AgentDefinition q);

    /**
     * 查询智能体详情
     */
    AgentDefinition selectDetail(Long agentId);

    /**
     * 新增智能体
     */
    int insertAgent(AgentDefinition agent);

    /**
     * 修改智能体
     */
    int updateAgent(AgentDefinition agent);

    /**
     * 批量删除智能体（物理删除）
     */
    int deleteAgents(List<Long> ids);

    /**
     * 修改状态：0草稿 1发布 2停用
     */
    int changeStatus(Long agentId, Integer status);

    /**
     * 发布智能体
     */
    int publish(Long agentId);

    /**
     * 同步对话：落库用户消息 -&gt; 组装上下文 -&gt; 调用大模型 -&gt; 落库回复
     */
    AgentReplyDTO chat(Long agentId, Long conversationId, String content);

    /**
     * 流式对话：返回增量文本片段，结束后统一落库完整回复
     */
    Flux<String> chatStream(Long agentId, Long conversationId, String content);

    /**
     * 分页查询会话列表
     */
    PageResult<AgentConversation> selectConversations(PageQuery query, AgentConversation q);

    /**
     * 分页查询消息列表（按 msg_id 正序）
     */
    PageResult<AgentMessage> selectMessages(PageQuery query, AgentMessage q);

    /**
     * 批量删除会话（级联删除其消息）
     */
    int deleteConversations(List<Long> ids);
}
