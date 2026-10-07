package com.aip.platform.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 画布连线
 *
 * <p>sourceHandle 用于条件分支：其内容一般与 condition 分支的 label 一致，
 * 引擎实际走向由 ConditionExecutor 通过 ctx.setNextNodeId(target) 决定。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /** 起点节点ID */
    private String source;

    /** 终点节点ID */
    private String target;

    /** 起点出口标识：条件节点形如 branch_0 / branch_default，其余节点为 out */
    private String sourceHandle;

    /** 终点入口标识（目前所有节点只有一个入口，保留字段便于扩展） */
    private String targetHandle;

    /** 连线标签 */
    private String label;
}
