package com.aip.platform.workflow.engine.executor;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.Constants;
import com.aip.platform.llm.client.LlmClient;
import com.aip.platform.llm.client.LlmResponse;
import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.engine.NodeData;
import com.aip.platform.workflow.engine.NodeExecutor;
import com.aip.platform.workflow.engine.NodeType;
import com.aip.platform.workflow.engine.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 大模型节点执行器
 *
 * <p>data 约定：modelId（为空时使用默认模型）、systemPrompt、userPrompt（必填）、
 * temperature、maxTokens、outputVar。提示词与 modelId 都先做变量渲染再使用。</p>
 */
@Slf4j
@Component
public class LlmExecutor implements NodeExecutor {

    private final LlmClient llmClient;

    /** 技能服务：把工作流挂载的技能注入系统提示词 */
    private final com.aip.platform.skill.service.ISkillService skillService;

    public LlmExecutor(LlmClient llmClient, com.aip.platform.skill.service.ISkillService skillService) {
        this.llmClient = llmClient;
        this.skillService = skillService;
    }

    @Override
    public String getType() {
        return NodeType.LLM.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);

        String userPrompt = context.render(NodeData.str(data.get("userPrompt")));
        if (StrUtil.isBlank(userPrompt)) {
            throw new BusinessException("大模型节点[" + node.getName() + "]未配置 userPrompt（用户提示词）");
        }
        String systemPrompt = context.render(NodeData.str(data.get("systemPrompt")));
        // 把该工作流挂载的技能正文追加进系统提示词，让节点具备对应领域的做事方法
        try {
            Long defId = context.getInstance() == null ? null : context.getInstance().getDefId();
            if (defId != null) {
                String skillPrompt = skillService.buildSkillPrompt(
                        com.aip.platform.skill.entity.SkillBinding.BizType.WORKFLOW, defId);
                if (StrUtil.isNotBlank(skillPrompt)) {
                    systemPrompt = (StrUtil.isBlank(systemPrompt) ? "" : systemPrompt) + skillPrompt;
                }
            }
        } catch (Exception e) {
            // 技能加载失败不应中断流程执行
            log.warn("加载工作流技能失败: {}", e.getMessage());
        }
        Long modelId = NodeData.toLong(data.get("modelId"));
        Double temperature = NodeData.toDouble(data.get("temperature"));
        Integer maxTokens = NodeData.toInteger(data.get("maxTokens"));

        Long instanceId = context.getInstance().getInstanceId();
        String bizId = instanceId == null ? null : String.valueOf(instanceId);

        LlmResponse response = llmClient.chat(modelId, systemPrompt, userPrompt, temperature, maxTokens,
                Constants.BizType.WORKFLOW, bizId);
        if (response == null || !response.isSuccess()) {
            String reason = response == null ? "无响应" : response.getErrorMsg();
            throw new BusinessException("节点[" + node.getName() + "]调用大模型失败：" + reason);
        }

        context.addTokens(response.getTotalTokens());
        // 回写 wf_node_run.model_id：未显式配置模型时补一个默认模型ID，失败不影响主流程
        if (modelId != null) {
            context.setLastModelId(modelId);
        } else {
            try {
                context.setLastModelId(llmClient.getOrLoadModel(null).getModelId());
            } catch (Exception ignored) {
                log.debug("无法确定默认模型ID，wf_node_run.model_id 留空");
            }
        }
        context.setNodeOutput(node, response.getContent());
        log.debug("大模型节点[{}]执行完成，tokens={}", node.getName(), response.getTotalTokens());
    }
}
