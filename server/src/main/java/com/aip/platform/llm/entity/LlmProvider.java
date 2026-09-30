package com.aip.platform.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 大模型供应商
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("llm_provider")
public class LlmProvider extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "provider_id", type = IdType.AUTO)
    private Long providerId;

    private String providerCode;

    private String providerName;

    private String baseUrl;

    private String protocol;

    private String apiKeyHint;

    private String logo;

    private Integer sortNum;

    private Integer status;
}
