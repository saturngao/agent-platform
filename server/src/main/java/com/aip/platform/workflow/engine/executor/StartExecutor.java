package com.aip.platform.workflow.engine.executor;

import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.engine.NodeData;
import com.aip.platform.workflow.engine.NodeExecutor;
import com.aip.platform.workflow.engine.NodeType;
import com.aip.platform.workflow.engine.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开始节点执行器：把流程入参写入运行上下文
 *
 * <p>data 约定：</p>
 * <ul>
 *   <li>variables：额外默认变量（Map），入参中已存在的同名变量不会被覆盖</li>
 *   <li>outputs：声明的输出变量名数组，仅在有配置时按需收集，否则整体返回入参</li>
 * </ul>
 */
@Slf4j
@Component
public class StartExecutor implements NodeExecutor {

    @Override
    public String getType() {
        return NodeType.START.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);
        // 入参整体进入变量池
        context.putAllVariables(context.getParams());

        // 节点上配置的默认变量（优先级低于入参）
        Object defaults = data.get("variables");
        if (defaults instanceof Map<?, ?> map) {
            Map<String, Object> extra = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                extra.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            context.putAllVariables(extra);
        }

        Object output = context.getParams();
        Object declaredOutputs = data.get("outputs");
        if (declaredOutputs instanceof List<?> names && !names.isEmpty()) {
            Map<String, Object> picked = new LinkedHashMap<>();
            for (Object name : names) {
                if (name == null) {
                    continue;
                }
                picked.put(String.valueOf(name), context.getVariable(String.valueOf(name)));
            }
            output = picked;
        }
        context.setNodeOutput(node, output);
        log.debug("开始节点[{}]执行完成，入参keys={}", node.getName(), context.getParams().keySet());
    }
}
