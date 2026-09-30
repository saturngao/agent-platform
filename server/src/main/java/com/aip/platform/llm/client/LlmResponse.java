package com.aip.platform.llm.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 大模型调用结果（非流式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;

    /** 生成的文本 */
    private String content;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer costMs;

    private String errorMsg;

    /** 原始响应（排查问题用，可能很长） */
    private String rawResponse;

    private String modelName;

    public static LlmResponse fail(String errorMsg) {
        return LlmResponse.builder().success(false).errorMsg(errorMsg).content("").build();
    }

    public static LlmResponse ok(String content) {
        return LlmResponse.builder().success(true).content(content).build();
    }
}
