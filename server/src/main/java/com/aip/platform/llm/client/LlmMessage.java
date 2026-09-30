package com.aip.platform.llm.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 对话消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** system / user / assistant */
    private String role;

    private String content;

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }
}
