package com.aip.platform.workflow.engine.executor;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.engine.NodeData;
import com.aip.platform.workflow.engine.NodeExecutor;
import com.aip.platform.workflow.engine.NodeType;
import com.aip.platform.workflow.engine.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据变换节点执行器
 *
 * <p><b>安全约束：暂不支持任意脚本执行</b>（不执行用户传入的动态代码，避免 RCE 风险）。
 * 当前只提供三种受控变换：template（模板渲染）、jsonPath（按 a.b.c 取值）、join（数组拼接）。
 * 后续如需真正跑脚本，可在此处接入 GraalVM 多语言沙箱并做资源隔离。</p>
 *
 * <p>data 约定：transform、template、source、jsonPath、separator、outputVar。</p>
 */
@Slf4j
@Component
public class CodeExecutor implements NodeExecutor {

    @Override
    public String getType() {
        return NodeType.CODE.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);
        String transform = NodeData.str(data.get("transform"));
        String source = NodeData.str(data.get("source"));
        Object value;

        if ("jsonPath".equalsIgnoreCase(transform)) {
            String path = NodeData.str(data.get("jsonPath"));
            if (StrUtil.isBlank(path)) {
                throw new BusinessException("数据变换节点[" + node.getName() + "]使用 jsonPath 但未配置 jsonPath");
            }
            Object root = source.isEmpty() ? context.getVariables() : context.getVariable(source);
            value = resolvePath(root, path);
        } else if ("join".equalsIgnoreCase(transform)) {
            String separator = NodeData.str(data.get("separator"));
            if (separator.isEmpty()) {
                separator = "\n";
            }
            value = join(context.getVariable(source), separator);
        } else {
            // 默认按模板渲染处理：优先取 template，兼容直接把文本放在 value 上
            String template = NodeData.str(data.get("template"));
            if (template.isEmpty()) {
                template = NodeData.str(data.get("value"));
            }
            if (StrUtil.isBlank(template)) {
                throw new BusinessException("数据变换节点[" + node.getName() + "]未配置 template/value，无法执行变换");
            }
            value = context.render(template);
        }
        context.setNodeOutput(node, value);
        log.debug("数据变换节点[{}]执行完成，transform={}", node.getName(), transform);
    }

    /** 按 a.b.c 路径从对象里取值，支持 Map 与 List 下标 */
    private Object resolvePath(Object root, String path) {
        Object current = root;
        for (String segment : path.split("\\.")) {
            String key = segment.trim();
            if (key.isEmpty()) {
                continue;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(key);
            } else if (current instanceof List<?> list) {
                try {
                    int index = Integer.parseInt(key);
                    current = (index >= 0 && index < list.size()) ? list.get(index) : null;
                } catch (NumberFormatException e) {
                    current = null;
                }
            } else {
                current = null;
            }
            if (current == null) {
                break;
            }
        }
        return current;
    }

    /** 数组拼接：非数组则退化为其字符串形式 */
    private String join(Object source, String separator) {
        if (source == null) {
            return "";
        }
        if (source instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    builder.append(separator);
                }
                builder.append(list.get(i) == null ? "" : String.valueOf(list.get(i)));
            }
            return builder.toString();
        }
        return String.valueOf(source);
    }
}
