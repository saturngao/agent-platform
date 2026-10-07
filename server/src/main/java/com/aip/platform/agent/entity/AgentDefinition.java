package com.aip.platform.agent.entity;

import com.aip.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 智能体定义（对应表 agent_definition）
 *
 * <p>该表没有 del_flag，删除走物理删除，因此不启用逻辑删除注解。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_definition")
public class AgentDefinition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已发布 */
    public static final int STATUS_PUBLISHED = 1;
    /** 状态：已停用 */
    public static final int STATUS_DISABLED = 2;

    @TableId(value = "agent_id", type = IdType.AUTO)
    private Long agentId;

    /** 唯一编码 */
    private String agentCode;

    /** 智能体名称 */
    private String agentName;

    /** 头像/图标 */
    private String avatar;

    /** 分类 */
    private String category;

    /** 绑定的模型配置ID，为空时走平台默认模型 */
    private Long modelId;

    /** 系统提示词 */
    private String systemPrompt;

    /** 开场白 */
    private String prologue;

    /** 覆盖模型的温度 */
    private Double temperature;

    /** 覆盖模型的最大输出 token */
    private Integer maxTokens;

    /** 启用的工具 JSON 数组字符串 */
    private String toolsJson;

    /** 关联知识/工作流ID集合（逗号分隔） */
    private String knowledgeIds;

    /** 变量定义 JSON 数组字符串 */
    private String variables;

    /** 状态 0草稿 1发布 2停用 */
    private Integer status;

    private Long deptId;

    // ---------------- 非数据库字段 ----------------

    /** 前端兼容别名：agentKey -&gt; agentCode */
    @TableField(exist = false)
    private String agentKey;

    /** 前端提交的工具数组 -&gt; toolsJson */
    @TableField(exist = false)
    private List<String> tools;

    /** 联查展示用模型名称 */
    @TableField(exist = false)
    private String modelName;

    /** 挂载的技能ID列表（非表字段，读写 skill_binding） */
    @TableField(exist = false)
    private List<Long> skillIds;

    /** 挂载的技能名称，列表展示用 */
    @TableField(exist = false)
    private String skillNames;

    /** 绑定的知识库ID数组（前端友好字段 -&gt; knowledgeIds 逗号分隔字符串） */
    @TableField(exist = false)
    private List<Long> knowledgeBaseIds;

    /** 绑定的知识库名称，列表/详情展示用 */
    @TableField(exist = false)
    private String knowledgeNames;
}
