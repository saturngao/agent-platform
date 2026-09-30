package com.aip.platform.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 大模型实例配置：工作流 / 智能体 / 总结模块统一引用的对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("llm_model")
public class LlmModel extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "model_id", type = IdType.AUTO)
    private Long modelId;

    private Long providerId;

    /** 模型唯一编码，业务内引用 */
    private String modelCode;

    private String modelName;

    /** chat / embedding / rerank */
    private String modelType;

    /** 完整请求地址，如 https://api.openai.com/v1/chat/completions */
    private String endpoint;

    /** 密钥（明文存储，生产建议加密或接入 KMS） */
    private String apiKey;

    /** 上游真实模型标识，如 gpt-4o / qwen-plus */
    private String modelNameId;

    private String systemPrompt;

    private Double temperature;

    private Double topP;

    private Integer maxTokens;

    private Integer timeoutMs;

    private Integer rateLimit;

    private Integer contextWindow;

    private Integer supportStream;

    private Integer supportJson;

    private Integer isDefault;

    private Integer status;

    private String lastTestAt;

    private String lastTestMsg;

    private Long deptId;

    // ---------- 非数据库字段 ----------
    @TableField(exist = false)
    private String providerName;

    /** 前端兼容别名：modelKey -> modelCode */
    @TableField(exist = false)
    private String modelKey;

    /** 前端兼容别名：provider -> providerId */
    @TableField(exist = false)
    private Long provider;

    /** 前端兼容别名：支持 camelCase 前端直接提交 */
    @TableField(exist = false)
    private Integer topPPercent;
}
