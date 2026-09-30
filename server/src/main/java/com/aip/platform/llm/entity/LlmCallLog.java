package com.aip.platform.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 大模型调用日志
 */
@Data
@TableName("llm_call_log")
public class LlmCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "call_id", type = IdType.AUTO)
    private Long callId;

    /** workflow / agent / summary / test */
    private String bizType;

    private String bizId;

    private Long modelId;

    private String modelName;

    private String requestBody;

    private String responseBody;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer costMs;

    /** 0 成功 1 失败 */
    private Integer status;

    private String errorMsg;

    private Long userId;

    private LocalDateTime callTime;
}
