package com.aip.platform.workflow.engine;

import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.SecurityUtils;
import com.aip.platform.workflow.dto.GraphDef;
import com.aip.platform.workflow.dto.GraphEdge;
import com.aip.platform.workflow.dto.GraphNode;
import com.aip.platform.workflow.entity.WfDefinition;
import com.aip.platform.workflow.entity.WfInstance;
import com.aip.platform.workflow.entity.WfNodeRun;
import com.aip.platform.workflow.mapper.WfInstanceMapper;
import com.aip.platform.workflow.mapper.WfNodeRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * DAG 工作流执行引擎
 *
 * <p>执行流程：解析 graph_json → 校验 → 落库 RUNNING 实例 → 从 start 节点出发逐个执行
 * → 每步写 wf_node_run → 成功/失败统一回写 wf_instance。</p>
 *
 * <p>环路保护：最大步数 = 节点数 * 3 + 20；除条件节点外每个节点最多执行一次。</p>
 *
 * <p>注意：本类不做事务包装。流程执行可能耗时数分钟（等待大模型），
 * 若放在事务里会长时间占用数据库连接；同时失败实例也要落库保留现场，
 * 因此所有异常都在 run() 内部消化，由调用方决定要不要抛。</p>
 */
@Slf4j
@Component
public class WorkflowEngine {

    /** error_msg 为 VARCHAR(2000)，留一点余量 */
    private static final int MAX_ERROR_LENGTH = 1800;

    private final ObjectMapper objectMapper;

    private final WfInstanceMapper instanceMapper;

    private final WfNodeRunMapper nodeRunMapper;

    private final Executor taskExecutor;

    /** 节点类型 -> 执行器 */
    private final Map<String, NodeExecutor> executors = new LinkedHashMap<>();

    public WorkflowEngine(ObjectMapper objectMapper,
                          List<NodeExecutor> executorList,
                          WfInstanceMapper instanceMapper,
                          WfNodeRunMapper nodeRunMapper,
                          @Qualifier("taskExecutor") Executor taskExecutor) {
        this.objectMapper = objectMapper;
        this.instanceMapper = instanceMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.taskExecutor = taskExecutor;
        if (executorList != null) {
            executorList.forEach(executor -> executors.put(executor.getType(), executor));
        }
        log.info("工作流引擎就绪，已注册节点执行器：{}", executors.keySet());
    }

    // ============================ 对外入口 ============================

    /**
     * 同步执行一次流程
     *
     * @param definition  流程定义（需含 graph_json）
     * @param params      入参
     * @param triggerType 触发方式：MANUAL / API / SCHEDULE
     * @return 执行完成的实例（状态为 SUCCESS / FAILED）
     */
    public WfInstance run(WfDefinition definition, Map<String, Object> params, String triggerType) {
        return run(definition, params, triggerType, SecurityUtils.getUserId(), SecurityUtils.getUsername());
    }

    /**
     * 异步执行一次流程（定时任务 / API 触发预留）
     *
     * <p>SecurityContext 是线程私有的，异步线程里取不到当前登录人，
     * 因此这里先在调用线程取好 userId / username 再传入。</p>
     *
     * @param definition 流程定义
     * @param params     入参
     * @return 异步结果
     */
    public CompletableFuture<WfInstance> runAsync(WfDefinition definition, Map<String, Object> params) {
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();
        return CompletableFuture.supplyAsync(
                () -> run(definition, params, WfInstance.TRIGGER_MANUAL, userId, username), taskExecutor);
    }

    // ============================ 核心执行 ============================

    private WfInstance run(WfDefinition definition, Map<String, Object> params, String triggerType,
                           Long userId, String username) {
        if (definition == null || definition.getDefId() == null) {
            throw new BusinessException("流程定义不存在，无法执行");
        }
        GraphDef graph = parseGraph(definition);
        validate(graph);

        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        for (GraphNode node : graph.getNodes()) {
            if (node != null && StrUtil.isNotBlank(node.getId())) {
                nodeMap.put(node.getId(), node);
            }
        }
        Map<String, List<GraphEdge>> outEdges = new HashMap<>();
        if (graph.getEdges() != null) {
            for (GraphEdge edge : graph.getEdges()) {
                if (edge != null && StrUtil.isNotBlank(edge.getSource())) {
                    outEdges.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge);
                }
            }
        }
        Map<String, Object> safeParams = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);

        // 1. 落库实例（RUNNING）
        WfInstance instance = new WfInstance();
        instance.setDefId(definition.getDefId());
        instance.setDefCode(definition.getDefCode());
        instance.setStatus(WfInstance.STATUS_RUNNING);
        instance.setTriggerType(StrUtil.isBlank(triggerType) ? WfInstance.TRIGGER_MANUAL : triggerType);
        instance.setUserId(userId);
        instance.setUsername(username);
        instance.setStartTime(LocalDateTime.now());
        instance.setInputData(writeJson(safeParams));
        instance.setTokenTotal(0);
        instance.setCostMs(0);
        if (StrUtil.isNotBlank(username)) {
            instance.setCreateBy(username);
            instance.setUpdateBy(username);
        }
        instanceMapper.insert(instance);

        WorkflowContext context = new WorkflowContext(instance, safeParams, objectMapper);
        long flowStart = System.currentTimeMillis();
        int maxSteps = nodeMap.size() * 3 + 20;
        Set<String> visited = new HashSet<>();
        int steps = 0;
        int seqNo = 0;

        try {
            String currentId = findStartId(nodeMap);
            while (StrUtil.isNotBlank(currentId)) {
                if (++steps > maxSteps) {
                    throw new BusinessException("执行步数超过上限(" + maxSteps + ")，疑似存在环路，已中断");
                }
                GraphNode node = nodeMap.get(currentId);
                if (node == null) {
                    throw new BusinessException("画布节点[" + currentId + "]不存在，流程已中断");
                }
                NodeType nodeType = NodeType.of(node.getType());
                if (nodeType == null) {
                    throw new BusinessException("节点[" + node.getName() + "]配置了未知类型：" + node.getType());
                }
                // 环路保护：条件节点允许多次经过，其余节点只允许执行一次
                if (nodeType != NodeType.CONDITION && !visited.add(currentId)) {
                    throw new BusinessException("节点[" + node.getName() + "]被重复执行，疑似环路，已中断");
                }
                context.getNodePath().push(currentId);
                seqNo++;

                WfNodeRun nodeRun = newRun(instance, node, nodeType, seqNo);
                nodeRun.setInputData(writeValue(node.getData()));
                nodeRunMapper.insert(nodeRun);

                long nodeStart = System.currentTimeMillis();
                int tokensBefore = context.getTokenTotal();
                // 每个节点重新记录，避免非大模型节点沿用上一个节点的 model_id
                context.setLastModelId(null);
                try {
                    NodeExecutor executor = executors.get(nodeType.getCode());
                    if (executor == null) {
                        throw new BusinessException("节点类型[" + nodeType.getCode() + "]没有对应的执行器");
                    }
                    executor.execute(node, context);
                    nodeRun.setStatus(WfNodeRun.STATUS_SUCCESS);
                    nodeRun.setOutputData(writeValue(context.getLastOutput()));
                    nodeRun.setTokens(Math.max(context.getTokenTotal() - tokensBefore, 0));
                    nodeRun.setModelId(context.getLastModelId());
                    if (nodeType == NodeType.CONDITION) {
                        nodeRun.setBranchTaken(context.getNextNodeId());
                    }
                    nodeRun.setCostMs(costOf(nodeStart));
                    nodeRun.setEndTime(LocalDateTime.now());
                    nodeRunMapper.updateById(nodeRun);
                } catch (Exception e) {
                    nodeRun.setStatus(WfNodeRun.STATUS_FAILED);
                    nodeRun.setErrorMsg(truncate(e.getMessage()));
                    nodeRun.setCostMs(costOf(nodeStart));
                    nodeRun.setEndTime(LocalDateTime.now());
                    nodeRunMapper.updateById(nodeRun);
                    throw e;
                }

                String next = context.takeNextNodeId();
                String nextHandle = context.takeNextHandle();
                if (StrUtil.isBlank(next)) {
                    // 条件节点：先看「命中的分支出口」有没有拖出连线，再退到第一条出边
                    if (StrUtil.isNotBlank(nextHandle)) {
                        next = edgeOfSourceHandle(outEdges.get(currentId), nextHandle);
                    }
                    if (StrUtil.isBlank(next) && nodeType == NodeType.CONDITION) {
                        next = conditionTarget(node, outEdges, currentId);
                    }
                    if (StrUtil.isBlank(next)) {
                        next = firstTarget(outEdges, currentId);
                    }
                }
                currentId = next;
            }

            instance.setStatus(WfInstance.STATUS_SUCCESS);
            String finalOutput = context.getFinalOutput();
            if (StrUtil.isBlank(finalOutput)) {
                finalOutput = writeValue(context.getLastOutput());
            }
            instance.setOutputData(buildOutputData(finalOutput));
            log.info("工作流执行成功 defId={} instanceId={} costMs={}",
                    definition.getDefId(), instance.getInstanceId(), costOf(flowStart));
        } catch (Exception e) {
            log.error("工作流执行失败 defId={} instanceId={}：{}",
                    definition.getDefId(), instance.getInstanceId(), e.getMessage());
            instance.setStatus(WfInstance.STATUS_FAILED);
            instance.setErrorMsg(truncate(e.getMessage()));
        } finally {
            instance.setEndTime(LocalDateTime.now());
            instance.setCostMs(costOf(flowStart));
            instance.setTokenTotal(context.getTokenTotal());
            instance.setVariables(writeJson(context.getVariables()));
            if (StrUtil.isNotBlank(username)) {
                instance.setUpdateBy(username);
            }
            try {
                instanceMapper.updateById(instance);
            } catch (Exception e) {
                log.error("回写运行实例失败 instanceId={}：{}", instance.getInstanceId(), e.getMessage());
            }
        }
        return instance;
    }

    // ============================ 校验与解析 ============================

    /** 解析画布：优先用内存中的 graph 对象，其次反序列化 graph_json */
    private GraphDef parseGraph(WfDefinition definition) {
        if (definition.getGraph() != null && definition.getGraph().getNodes() != null
                && !definition.getGraph().getNodes().isEmpty()) {
            return definition.getGraph();
        }
        if (StrUtil.isBlank(definition.getGraphJson())) {
            throw new BusinessException("流程画布为空，请先在设计器中编排节点");
        }
        try {
            return objectMapper.readValue(definition.getGraphJson(), GraphDef.class);
        } catch (Exception e) {
            throw new BusinessException("流程画布 JSON 解析失败：" + e.getMessage());
        }
    }

    /** 画布校验：必须有 start 与 end；大模型 / 智能体节点必须有 userPrompt */
    private void validate(GraphDef graph) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            throw new BusinessException("流程画布为空，请先在设计器中编排节点");
        }
        boolean hasStart = false;
        boolean hasEnd = false;
        for (GraphNode node : graph.getNodes()) {
            if (node == null || StrUtil.isBlank(node.getId())) {
                continue;
            }
            NodeType nodeType = NodeType.of(node.getType());
            if (nodeType == null) {
                throw new BusinessException("节点[" + node.getName() + "]配置了未知类型：" + node.getType());
            }
            if (nodeType == NodeType.START) {
                hasStart = true;
            }
            if (nodeType == NodeType.END) {
                hasEnd = true;
            }
            if (nodeType == NodeType.LLM || nodeType == NodeType.AGENT) {
                Object userPrompt = node.getData() == null ? null : node.getData().get("userPrompt");
                if (userPrompt == null || StrUtil.isBlank(String.valueOf(userPrompt))) {
                    throw new BusinessException("节点[" + node.getName() + "]未配置 userPrompt（用户提示词），无法执行");
                }
            }
        }
        if (!hasStart) {
            throw new BusinessException("流程缺少【开始】节点");
        }
        if (!hasEnd) {
            throw new BusinessException("流程缺少【结束】节点");
        }
    }

    /** 找到开始节点ID */
    private String findStartId(Map<String, GraphNode> nodeMap) {
        for (Map.Entry<String, GraphNode> entry : nodeMap.entrySet()) {
            if (NodeType.of(entry.getValue().getType()) == NodeType.START) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 取第一条出边的目标节点 */
    private String firstTarget(Map<String, List<GraphEdge>> outEdges, String nodeId) {
        List<GraphEdge> edges = outEdges.get(nodeId);
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        return edges.get(0).getTarget();
    }

    /**
     * 条件节点的兜底走向：优先用节点里配的 branch.target，没配就看画布上该分支拖出的连线。
     *
     * <p>设计器里从一个分支端点拖线出去时，会把 target 写回 node.data，理论上两者一致；
     * 这里再兜一层是为了兼容「只拉了线、target 是空的老数据」。</p>
     */
    private String conditionTarget(GraphNode node, Map<String, List<GraphEdge>> outEdges, String nodeId) {
        List<GraphEdge> edges = outEdges.get(nodeId);
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        Map<String, Object> data = node.getData() == null ? Map.of() : node.getData();
        // 1) 分支表里显式配了 target 的
        Object raw = data.get("branches");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String target = StrUtil.trimToNull(String.valueOf(map.get("target")));
                    if (target != null) {
                        String hit = targetOfHandle(edges, target);
                        if (hit != null) {
                            return hit;
                        }
                    }
                }
            }
        }
        // 2) 按 branch_0 → branch_1 顺序找第一条画出来的连线，最后再考虑默认分支
        for (int i = 0; i < 20; i++) {
            String hit = edgeOfSourceHandle(edges, "branch_" + i);
            if (hit != null) {
                return hit;
            }
        }
        return edgeOfSourceHandle(edges, "branch_default");
    }

    /** 在出边里找指向某节点的连线（确认该 target 确实画了线，避免指向已删节点） */
    private String targetOfHandle(List<GraphEdge> edges, String targetId) {
        for (GraphEdge edge : edges) {
            if (edge != null && targetId.equals(edge.getTarget())) {
                return targetId;
            }
        }
        return null;
    }

    /** 取指定出口端点的连线目标 */
    private String edgeOfSourceHandle(List<GraphEdge> edges, String sourceHandle) {
        for (GraphEdge edge : edges) {
            if (edge != null && sourceHandle.equals(edge.getSourceHandle())) {
                return StrUtil.trimToNull(edge.getTarget());
            }
        }
        return null;
    }

    /** 构造节点运行记录（RUNNING） */
    private WfNodeRun newRun(WfInstance instance, GraphNode node, NodeType nodeType, int seqNo) {
        WfNodeRun nodeRun = new WfNodeRun();
        nodeRun.setInstanceId(instance.getInstanceId());
        nodeRun.setNodeId(node.getId());
        nodeRun.setNodeName(node.getName());
        nodeRun.setNodeType(nodeType.getCode());
        nodeRun.setStatus(WfNodeRun.STATUS_RUNNING);
        nodeRun.setSeqNo(seqNo);
        nodeRun.setTokens(0);
        nodeRun.setCostMs(0);
        nodeRun.setStartTime(LocalDateTime.now());
        return nodeRun;
    }

    // ============================ 工具方法 ============================

    private int costOf(long startMillis) {
        long cost = System.currentTimeMillis() - startMillis;
        return (int) Math.min(cost, Integer.MAX_VALUE);
    }

    private String truncate(String text) {
        if (text == null) {
            return "未知错误";
        }
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH) + "...";
    }

    /** 始终序列化为 JSON 字符串 */
    private String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("变量快照序列化失败：{}", e.getMessage());
            return "{}";
        }
    }

    /** 字符串原样输出，其它对象转 JSON（便于前端时间线直接展示） */
    private String writeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        return writeJson(value);
    }

    /** 产出 output_data：能解析成 JSON 就存原样，否则存 JSON 字符串字面量 */
    private String buildOutputData(String finalOutput) {
        if (StrUtil.isBlank(finalOutput)) {
            return "{}";
        }
        String text = finalOutput.trim();
        boolean looksJson = (text.startsWith("{") && text.endsWith("}"))
                || (text.startsWith("[") && text.endsWith("]"));
        if (looksJson) {
            try {
                objectMapper.readTree(text);
                return text;
            } catch (Exception ignored) {
                // 不是合法 JSON，走字符串字面量
            }
        }
        return writeJson(finalOutput);
    }
}
