package com.aip.platform.workflow.engine.executor;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.engine.NodeData;
import com.aip.platform.workflow.engine.NodeExecutor;
import com.aip.platform.workflow.engine.NodeType;
import com.aip.platform.workflow.engine.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 智能体节点执行器
 *
 * <p><b>MVP 阶段说明</b>：本版本尚未实现「按 agentId 加载智能体定义并装配工具/知识库」的能力。
 * 若节点配置了 agentId，仅在上下文里记录一条提示「当前版本由 LlmExecutor 承担智能处理」，
 * 实际仍按大模型节点逻辑执行，即复用同一个 llmClient，读取节点 data 里的 systemPrompt / userPrompt。</p>
 *
 * <p>data 约定：agentId（预留）、systemPrompt、userPrompt、modelId、temperature、maxTokens、outputVar。</p>
 * <p>待智能体模块（agent_definition / tools / knowledge）就绪后，
 * 只需在此处按 agentId 加载配置并覆盖提示词与工具列表即可，无需改动引擎。</p>
 */
@Slf4j
@Component
public class AgentExecutor implements NodeExecutor {

    private final LlmExecutor llmExecutor;

    public AgentExecutor(LlmExecutor llmExecutor) {
        this.llmExecutor = llmExecutor;
    }

    @Override
    public String getType() {
        return NodeType.AGENT.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);
        String agentId = NodeData.str(data.get("agentId"));
        if (StrUtil.isNotBlank(agentId)) {
            // 预留：记录一次提示，便于在节点执行明细里看到「本该由哪个智能体处理」
            context.putVariable(node.getId() + ".agentId", agentId);
            log.info("agent节点[{}]配置了 agentId={}，当前版本由 LlmExecutor 承担智能处理", node.getName(), agentId);
        }
        llmExecutor.execute(node, context);
    }
}
