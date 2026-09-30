package com.aip.platform.workflow.engine.executor;

import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.engine.NodeData;
import com.aip.platform.workflow.engine.NodeExecutor;
import com.aip.platform.workflow.engine.NodeType;
import com.aip.platform.workflow.engine.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识库节点执行器
 *
 * <p><b>MVP 阶段</b>：实现为「静态知识片段注入器」——直接取节点 data.content（支持 {{变量}} 渲染），
 * 渲染后写入 outputVar 指定的变量，供后续大模型节点作为上下文引用。</p>
 *
 * <p>后续接入向量检索（embedding + 召回 + 重排）时，
 * 只需把 content 换成「按 query 检索出的片段拼接结果」即可，节点契约保持不变。</p>
 *
 * <p>data 约定：content、outputVar。</p>
 */
@Slf4j
@Component
public class KnowledgeExecutor implements NodeExecutor {

    @Override
    public String getType() {
        return NodeType.KNOWLEDGE.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);
        String content = context.render(NodeData.str(data.get("content")));
        context.setNodeOutput(node, content);
        log.debug("知识库节点[{}]注入片段长度={}", node.getName(), content == null ? 0 : content.length());
    }
}
