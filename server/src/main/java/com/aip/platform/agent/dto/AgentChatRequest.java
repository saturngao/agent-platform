package com.aip.platform.agent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 智能体对话请求体（POST /api/agent/message/send）
 */
@Data
public class AgentChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 智能体ID */
    private Long agentId;

    /** 会话ID，为空时自动新建会话 */
    private Long conversationId;

    /** 本次发送的内容 */
    private String content;

    /** 是否需要流式返回 */
    private Boolean stream;
}
