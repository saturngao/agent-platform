package com.aip.platform.workflow.engine;

import com.aip.platform.workflow.dto.GraphNode;

/**
 * 节点执行器
 *
 * <p>每种节点类型一个实现，由 Spring 收集后按 getType() 注册到 WorkflowEngine 的执行器映射中。</p>
 * <p>实现约定：执行失败请直接抛出 {@link com.aip.platform.common.BusinessException}，
 * 引擎会捕获并把错误信息写入 wf_node_run 与 wf_instance。</p>
 */
public interface NodeExecutor {

    /**
     * 节点类型编码，与 {@link NodeType#getCode()} 一致
     *
     * @return 类型编码，如 llm / condition
     */
    String getType();

    /**
     * 执行节点
     *
     * @param node    画布节点
     * @param context 运行上下文
     */
    void execute(GraphNode node, WorkflowContext context);
}
