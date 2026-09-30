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
 * 结束节点执行器：产出流程最终结果
 *
 * <p>data 约定：outputTemplate（支持变量渲染）。渲染结果写入 ctx.setFinalOutput()，
 * 引擎会把它落到 wf_instance.output_data；同时也会写入变量池，便于多结束节点场景回溯。</p>
 */
@Slf4j
@Component
public class EndExecutor implements NodeExecutor {

    @Override
    public String getType() {
        return NodeType.END.getCode();
    }

    @Override
    public void execute(GraphNode node, WorkflowContext context) {
        Map<String, Object> data = NodeData.of(node);
        String output = context.render(NodeData.str(data.get("outputTemplate")));
        context.setFinalOutput(output);
        context.setNodeOutput(node, output);
        log.debug("结束节点[{}]产出最终输出，长度={}", node.getName(), output == null ? 0 : output.length());
    }
}
