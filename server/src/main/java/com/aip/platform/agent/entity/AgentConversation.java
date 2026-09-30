package com.aip.platform.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体会话（对应表 agent_conversation）
 *
 * <p>该表既没有 del_flag 也没有审计字段，因此不继承 BaseEntity。</p>
 */
@Data
@TableName("agent_conversation")
public class AgentConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 0;
    /** 状态：归档 */
    public static final int STATUS_ARCHIVED = 1;

    @TableId(value = "conv_id", type = IdType.AUTO)
    private Long convId;

    /** 所属智能体 */
    private Long agentId;

    /** 发起人 */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 上下文摘要 */
    private String summary;

    /** 消息条数 */
    private Integer messageCount;

    /** 状态 0正常 1归档 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
