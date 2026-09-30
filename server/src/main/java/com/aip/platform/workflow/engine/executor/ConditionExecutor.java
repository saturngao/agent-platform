package com.aip.platform.workflow.engine.executor;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.engine.NodeData;
import com.aip.platform.workflow.engine.NodeExecutor;
import com.aip.platform.workflow.engine.NodeType;
import com.aip.platform.workflow.engine.SimpleConditionEvaluator;
import com.aip.platform.workflow.engine.WorkflowContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件分支节点执行器
 *
 * <p>data 约定：branches=[{condition, target, label}]、defaultTarget。</p>
 * <p>求值使用 {@link SimpleConditionEvaluator}，任何异常都按「不匹配」处理；
 * 全部分支都不命中且未配置 defaultTarget 时不抛异常，流程在该节点后自然结束。</p>
 */
@Slf4j
@Component
public class ConditionExecutor implements NodeExecutor {

    private final ObjectMapper objectMapper;

    public ConditionExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return NodeType.CONDITION.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);
        List<Map<String, Object>> branches = readBranches(data.get("branches"));

        for (Map<String, Object> branch : branches) {
            String condition = NodeData.str(branch.get("condition"));
            String target = NodeData.str(branch.get("target"));
            if (target.isEmpty()) {
                continue;
            }
            if (SimpleConditionEvaluator.matches(condition, context)) {
                String label = NodeData.str(branch.get("label"));
                context.setNextNodeId(target);
                context.setNodeOutput(node, (label.isEmpty() ? condition : label) + " -> " + target);
                log.info("条件节点[{}]命中分支[{}]，走向节点[{}]", node.getName(), condition, target);
                return;
            }
        }

        String defaultTarget = NodeData.str(data.get("defaultTarget"));
        if (!defaultTarget.isEmpty()) {
            context.setNextNodeId(defaultTarget);
            context.setNodeOutput(node, "default -> " + defaultTarget);
            log.info("条件节点[{}]未命中任何分支，走默认分支[{}]", node.getName(), defaultTarget);
            return;
        }

        // 注意：这里刻意不抛异常——判空失败属于业务分支，不应让整个流程崩掉
        log.warn("条件节点[{}]未命中任何分支且未配置 defaultTarget，流程将在此节点后结束", node.getName());
        context.setNodeOutput(node, "");
    }

    /** 分支配置兼容 List 与 JSON 字符串两种形态 */
    private List<Map<String, Object>> readBranches(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        row.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(row);
                }
            }
            return result;
        }
        if (raw instanceof String text && StrUtil.isNotBlank(text)) {
            try {
                List<Map<String, Object>> parsed =
                        objectMapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {
                        });
                return parsed == null ? Collections.emptyList() : parsed;
            } catch (Exception e) {
                log.warn("条件节点 branches 解析失败，按无分支处理：{}", e.getMessage());
            }
        }
        return Collections.emptyList();
    }
}
