package com.aip.platform.agent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 智能体回复结果
 */
@Data
public class AgentReplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID（新建会话时回传给前端，便于继续追问） */
    private Long conversationId;

    /** 本次回复消息的ID */
    private Long messageId;

    /** 回复内容 */
    private String content;

    /** 实际使用的模型名称 */
    private String modelName;

    /** 消耗 token 数 */
    private Integer tokens;

    /** 耗时（毫秒） */
    private Integer costMs;

    /** 是否调用成功 */
    private Boolean success;
}
