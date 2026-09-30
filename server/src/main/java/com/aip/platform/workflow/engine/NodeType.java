package com.aip.platform.workflow.engine;

import java.util.Locale;

/**
 * 工作流节点类型
 */
public enum NodeType {

    /** 开始节点：写入流程入参 */
    START("start", "开始节点"),

    /** 大模型节点：调用 LLM 生成内容 */
    LLM("llm", "大模型节点"),

    /** 数据变换节点：模板渲染 / 取值 / 拼接（不执行任意脚本） */
    CODE("code", "数据变换节点"),

    /** 条件分支节点：按表达式决定走向 */
    CONDITION("condition", "条件分支节点"),

    /** HTTP 请求节点：调用外部接口 */
    HTTP("http", "HTTP 请求节点"),

    /** 智能体节点：MVP 阶段复用大模型节点逻辑 */
    AGENT("agent", "智能体节点"),

    /** 知识库节点：静态知识片段注入 */
    KNOWLEDGE("knowledge", "知识库节点"),

    /** 结束节点：产出最终结果 */
    END("end", "结束节点");

    private final String code;

    private final String desc;

    NodeType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按画布节点 type 字符串解析，忽略大小写与前后空格
     *
     * @param code 节点类型字符串
     * @return 节点类型，未知返回 null
     */
    public static NodeType of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String target = code.trim().toLowerCase(Locale.ROOT);
        for (NodeType type : values()) {
            if (type.code.equals(target)) {
                return type;
            }
        }
        return null;
    }
}
