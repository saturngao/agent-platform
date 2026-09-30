package com.aip.platform.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体消息（对应表 agent_message）
 *
 * <p>该表既没有 del_flag 也没有审计字段，因此不继承 BaseEntity。</p>
 */
@Data
@TableName("agent_message")
public class AgentMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 0;
    /** 状态：失败 */
    public static final int STATUS_FAIL = 1;

    /** 角色：用户 */
    public static final String ROLE_USER = "user";
    /** 角色：助手 */
    public static final String ROLE_ASSISTANT = "assistant";
    /** 角色：系统 */
    public static final String ROLE_SYSTEM = "system";

    @TableId(value = "msg_id", type = IdType.AUTO)
    private Long msgId;

    /** 所属会话 */
    private Long convId;

    /** user / assistant / system */
    private String role;

    /** 消息正文 */
    private String content;

    /** 消耗 token */
    private Integer tokens;

    /** 实际使用的模型名称 */
    private String modelName;

    /** 耗时（毫秒） */
    private Integer costMs;

    /** 状态 0正常 1失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    private LocalDateTime createTime;

    // ---------------- 非数据库字段 ----------------

    /** 前端兼容别名：conversationId -&gt; convId */
    @TableField(exist = false)
    private Long conversationId;
}
