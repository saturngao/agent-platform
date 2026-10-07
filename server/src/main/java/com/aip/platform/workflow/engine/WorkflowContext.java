package com.aip.platform.workflow.engine;

import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.entity.WfInstance;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流运行上下文
 *
 * <p>承载一次实例运行期间的全部可变状态：入参、各节点输出变量、token 累计、执行路径、
 * 条件节点决定的下一个节点ID，以及结束节点的最终输出。</p>
 */
@Slf4j
public class WorkflowContext {

    /** 变量占位符：{{varName}} 或 {{obj.field}} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*\\}\\}");

    private final ObjectMapper objectMapper;

    /** 当前运行实例 */
    @Getter
    private final WfInstance instance;

    /** 流程入参（原始值，不再修改） */
    @Getter
    private final Map<String, Object> params;

    /** 运行期变量：入参 + 各节点输出 */
    @Getter
    private final Map<String, Object> variables = new LinkedHashMap<>();

    /** 执行路径（栈） */
    @Getter
    private final Deque<String> nodePath = new ArrayDeque<>();

    /** token 累计 */
    @Getter
    private int tokenTotal;

    /** 条件节点决定的下一个节点ID */
    @Getter
    @Setter
    private String nextNodeId;

    /**
     * 命中的分支出口（形如 branch_0 / branch_default）
     *
     * <p>只在「分支没手写 target、靠画布连线决定走向」时使用：
     * ConditionExecutor 判断出命中哪个分支后把出口编号放在这里，
     * 引擎再据此找到该出口拖出的那条连线。</p>
     */
    @Getter
    @Setter
    private String nextHandle;

    /** 结束节点产出的最终输出 */
    @Getter
    @Setter
    private String finalOutput;

    /** 最近一次节点输出（用于回写 wf_node_run.output_data） */
    @Getter
    @Setter
    private Object lastOutput;

    /** 最近一次调用使用的大模型ID（用于回写 wf_node_run.model_id） */
    @Getter
    @Setter
    private Long lastModelId;

    public WorkflowContext(WfInstance instance, Map<String, Object> params, ObjectMapper objectMapper) {
        this.instance = instance;
        this.objectMapper = objectMapper;
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    // ============================ 变量操作 ============================

    /** 放入变量 */
    public void putVariable(String name, Object value) {
        if (name == null) {
            return;
        }
        variables.put(name, value);
    }

    /** 批量放入变量（已存在的不覆盖） */
    public void putAllVariables(Map<String, Object> source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!variables.containsKey(entry.getKey())) {
                variables.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 读取变量，支持 a.b.c 形式的路径 */
    public Object getVariable(String name) {
        return resolve(name);
    }

    /** 累计 token */
    public void addTokens(Integer tokens) {
        if (tokens != null && tokens > 0) {
            tokenTotal += tokens;
        }
    }

    /**
     * 写入节点输出：同时写入 variables，key 优先取节点 data.outputVar，其次节点ID
     *
     * @param node  画布节点
     * @param value 输出值
     */
    public void setNodeOutput(GraphNode node, Object value) {
        String key = null;
        if (node != null && node.getData() != null) {
            Object outputVar = node.getData().get("outputVar");
            if (outputVar != null) {
                String text = String.valueOf(outputVar).trim();
                if (!text.isEmpty()) {
                    key = text;
                }
            }
        }
        if (key == null || key.isEmpty()) {
            key = node == null ? "output" : node.getId();
        }
        setNodeOutput(key, value);
    }

    /**
     * 写入节点输出（按变量名直接写入）
     *
     * @param nodeId 变量名（一般为节点ID 或 outputVar）
     * @param value  输出值
     */
    public void setNodeOutput(String nodeId, Object value) {
        if (nodeId == null) {
            return;
        }
        variables.put(nodeId, value);
        lastOutput = value;
    }

    /** 取出并清空条件节点设置的下一个节点ID */
    public String takeNextNodeId() {
        String id = nextNodeId;
        nextNodeId = null;
        return id;
    }

    /** 取出并清空「命中的分支出口」，语义同 takeNextNodeId */
    public String takeNextHandle() {
        String handle = nextHandle;
        nextHandle = null;
        return handle;
    }

    // ============================ 模板渲染 ============================

    /**
     * 渲染模板：把 {{varName}} 替换为变量值字符串
     *
     * <p>Map / List 等复杂对象转为 JSON 字符串；找不到变量时保留原样并输出 warn 日志（不抛异常）。</p>
     *
     * @param template 模板
     * @return 渲染结果，模板为空返回原值
     */
    public String render(String template) {
        if (template == null) {
            return null;
        }
        if (template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String expr = matcher.group(1);
            Object value = resolve(expr);
            if (value == null) {
                log.warn("工作流变量[{}]不存在，占位符保持原样", expr);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(stringify(value)));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /** 变量解析：优先按完整 key 命中，其次按 a.b.c 逐层取值 */
    private Object resolve(String expr) {
        if (expr == null || expr.isEmpty()) {
            return null;
        }
        if (variables.containsKey(expr)) {
            return variables.get(expr);
        }
        String[] parts = expr.split("\\.");
        Object current = variables.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            current = readProperty(current, parts[i]);
        }
        return current;
    }

    /** 从 Map / List 中按 key 或下标取值 */
    private Object readProperty(Object source, String key) {
        if (source instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if (source instanceof List<?> list) {
            try {
                int index = Integer.parseInt(key.trim());
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                }
            } catch (NumberFormatException ignored) {
                // 非数字下标，返回 null
            }
            return null;
        }
        return null;
    }

    /** 对象转字符串：字符串原样，复杂对象转 JSON */
    public String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("变量序列化失败，降级为 toString: {}", e.getMessage());
            return String.valueOf(value);
        }
    }
}
